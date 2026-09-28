#!/usr/bin/env python3
"""Offline: link archived lumbar key images to reported levels by geometry.

Composer key-image links are not trusted: most were auto-suggested at capture time
("last_finding_suggestion"), before the findings were written. This script re-derives links
from where each key image is in the patient (arrow tip, axial plane, or sagittal side) and the
study's disc-level map. It reads the report archive and writes link files under
<root>/keyimage-links; it never modifies the archive. Run it outside reading hours: studies
without a cached level map run the local segmentation model.
"""
import argparse
import hashlib
import json
import math
import sys
from pathlib import Path

from prepare_pilot import build_request
from worker import UNASSIGNED, digest, process, proposal_location, write_json

SCHEMA_VERSION = 1
# Only a link the reader explicitly chose is kept; suggestions and removed findings are not.
TRUSTED_STORED_SOURCES = {"selected_finding"}
# Arrows point at findings beside and behind the disc (foramina sit ~15-25 mm lateral and
# posterior), so levels are chosen by position along the spine, not straight-line distance.
ARROW_MAX_OFF_AXIS_MM = 45.0
# The nearest disc along the spine must be clearly closer than the next; otherwise the arrow
# sits between levels and is left for review.
LEVEL_MARGIN_RATIO = 0.75
AXIAL_MAX_MM = 10.0
# Sagittal slices within this distance of the disc midline show the canal, not a foramen.
MIDLINE_MM = 8.0
SIDE_TASKS = {
    "central": {"spinal_canal_stenosis"},
    "left": {"neural_foraminal_stenosis", "subarticular_stenosis"},
    "right": {"neural_foraminal_stenosis", "subarticular_stenosis"},
}
CONFIDENCE = {
    "arrow": "high",
    "axial_plane": "high",
    "sagittal_side_single_level": "medium",
}


def _vector(values):
    return [float(v) for v in values]


def _dot(a, b):
    return sum(x * y for x, y in zip(a, b))


def _sub(a, b):
    return [x - y for x, y in zip(a, b)]


def normal_of(geometry):
    r, c = _vector(geometry["imageOrientationPatient"][:3]), _vector(geometry["imageOrientationPatient"][3:6])
    return [r[1] * c[2] - r[2] * c[1], r[2] * c[0] - r[0] * c[2], r[0] * c[1] - r[1] * c[0]]


def plane_of(geometry):
    normal = normal_of(geometry)
    return ("sagittal", "coronal", "axial")[max(range(3), key=lambda i: abs(normal[i]))]


def pixel_to_lps(geometry, x, y):
    """Source pixel (x = column, y = row) to patient LPS millimetres."""
    row_dir = _vector(geometry["imageOrientationPatient"][:3])
    col_dir = _vector(geometry["imageOrientationPatient"][3:6])
    row_spacing, col_spacing = _vector(geometry["pixelSpacing"])
    origin = _vector(geometry["imagePositionPatient"])
    return [origin[i] + x * col_spacing * row_dir[i] + y * row_spacing * col_dir[i] for i in range(3)]


def positive_labels(labels):
    """Reported (asserted) findings by level; omitted levels are normal and never linked."""
    by_level = {}
    for label in labels:
        if label.get("certainty") == "asserted":
            by_level.setdefault(label["level"], []).append(
                {key: label[key] for key in ("task", "laterality", "value")})
    return by_level


def side_of(offset_mm):
    """LPS +x is patient left."""
    if abs(offset_mm) <= MIDLINE_MM:
        return "central"
    return "left" if offset_mm > 0 else "right"


def _matching(labels, side):
    if side is None:
        return list(labels)
    return [l for l in labels if l["task"] in SIDE_TASKS[side]
            and (side == "central" or l["laterality"] == side)]


def link_key_image(key_image, discs, frame_of_reference, reported):
    """One or more links for a key image. discs: [{"level", "lps"}] with assigned levels."""
    stored = {"findingId": key_image.get("findingId", ""),
              "findingLinkSource": key_image.get("findingLinkSource", ""),
              "trusted": key_image.get("findingLinkSource") in TRUSTED_STORED_SOURCES}
    base = {"keyImageId": key_image["id"],
            "sopInstanceUid": key_image["reference"].get("sopInstanceUid", ""),
            "storedLink": stored}
    geometry = key_image["reference"].get("geometry") or {}
    if not geometry.get("imageOrientationPatient") or not discs:
        return [{**base, "method": "no_level_map", "confidence": "none"}]
    if geometry.get("frameOfReferenceUid") != frame_of_reference:
        return [{**base, "method": "different_frame_of_reference", "confidence": "none"}]
    plane = plane_of(geometry)
    base["plane"] = plane
    midline_x = sum(d["lps"][0] for d in discs) / len(discs)

    arrows = [a for a in key_image.get("sourceArrows") or [] if a.get("tipInsideImage")]
    if arrows:
        links = []
        for arrow in arrows:
            tip = pixel_to_lps(geometry, arrow["tipX"], arrow["tipY"])
            ranked, off_axis = along_spine(tip, discs)
            if off_axis > ARROW_MAX_OFF_AXIS_MM:
                links.append({**base, "method": "arrow_not_near_spine", "confidence": "none",
                              "distanceMm": round(off_axis, 1)})
                continue
            (disc, nearest), *rest = ranked
            if rest and nearest > LEVEL_MARGIN_RATIO * rest[0][1]:
                links.append({**base, "method": "arrow_between_levels", "confidence": "none",
                              "candidateLevels": sorted([disc["level"], rest[0][0]["level"]])})
                continue
            side = side_of(tip[0] - disc["lps"][0])
            links.append(_level_link(base, "arrow", disc["level"], side, nearest, reported))
        return links

    if plane == "axial":
        normal = normal_of(geometry)
        origin = _vector(geometry["imagePositionPatient"])
        disc = min(discs, key=lambda d: abs(_dot(_sub(d["lps"], origin), normal)))
        distance = abs(_dot(_sub(disc["lps"], origin), normal))
        if distance > AXIAL_MAX_MM:
            return [{**base, "method": "axial_between_discs", "confidence": "none",
                     "distanceMm": round(distance, 1)}]
        return [_level_link(base, "axial_plane", disc["level"], None, distance, reported)]

    if plane == "sagittal":
        side = side_of(_vector(geometry["imagePositionPatient"])[0] - midline_x)
        candidates = sorted(level for level, labels in reported.items()
                            if level in {d["level"] for d in discs} and _matching(labels, side))
        if len(candidates) == 1:
            return [_level_link(base, "sagittal_side_single_level", candidates[0], side, None, reported)]
        return [{**base, "method": "ambiguous" if candidates else "no_matching_finding",
                 "confidence": "none", "side": side, "candidateLevels": candidates}]

    return [{**base, "method": "ambiguous", "confidence": "none",
             "candidateLevels": sorted(reported)}]


def along_spine(point, discs):
    """Discs ranked by distance along the spine's disc polyline, and the point's distance from it."""
    ordered = sorted(discs, key=lambda d: -d["lps"][2])
    arc = [0.0]
    for a, b in zip(ordered, ordered[1:]):
        arc.append(arc[-1] + math.dist(a["lps"], b["lps"]))
    best = (math.inf, 0.0)
    for index, (a, b) in enumerate(zip(ordered, ordered[1:])):
        segment = _sub(b["lps"], a["lps"])
        length_sq = _dot(segment, segment)
        t = max(0.0, min(1.0, _dot(_sub(point, a["lps"]), segment) / length_sq)) if length_sq else 0.0
        foot = [a["lps"][i] + t * segment[i] for i in range(3)]
        off_axis = math.dist(point, foot)
        if off_axis < best[0]:
            best = (off_axis, arc[index] + t * math.sqrt(length_sq))
    if len(ordered) == 1:
        best = (math.dist(point, ordered[0]["lps"]), 0.0)
    off_axis, position = best
    ranked = sorted(((d, abs(position - arc[i])) for i, d in enumerate(ordered)), key=lambda x: x[1])
    return ranked, off_axis


def _level_link(base, method, level, side, distance, reported):
    link = {**base, "method": method, "confidence": CONFIDENCE[method], "level": level,
            "labels": _matching(reported.get(level, []), side)}
    if side is not None:
        link["side"] = side
    if distance is not None:
        link["distanceMm"] = round(distance, 1)
    return link


def feedback_map(root, study_key):
    """Reader-reviewed disc levels, when the reader accepted or corrected the map."""
    path = root / "feedback" / hashlib.sha256(study_key.encode()).hexdigest() / "latest.json"
    if not path.is_file():
        return None
    saved = json.loads(path.read_text())
    points = saved.get("points") or []
    if saved.get("decision") not in {"accepted", "corrected"} or any(
            p.get("level") == UNASSIGNED for p in points):
        return None
    frame = (saved.get("proposal") or {}).get("frameOfReferenceUid", "")
    return {"source": "reader_reviewed", "frameOfReferenceUid": frame, "points": points}


def level_map(root, request, run_model):
    feedback = feedback_map(root, request["studyKey"])
    if feedback:
        return feedback
    _, _, _, output = proposal_location(request, root)
    if output.is_file():
        proposal = json.loads(output.read_text())
    elif run_model:
        proposal = process(request, root)
    else:
        return {"source": "not_computed", "points": []}
    if proposal.get("numberingUncertain"):
        return {"source": "model_numbering_uncertain", "points": []}
    return {"source": "model_provisional",
            "frameOfReferenceUid": proposal.get("frameOfReferenceUid", ""),
            "points": proposal.get("points") or []}


def link_study(directory, manifest, root, run_model):
    content = manifest["content"]
    try:
        levels = level_map(root, build_request(directory, manifest), run_model)
    except ValueError as error:
        # Worker and request errors are fixed messages without paths or patient data.
        levels = {"source": "level_map_unavailable", "detail": str(error), "points": []}
    except Exception as error:
        levels = {"source": "level_map_unavailable", "detail": type(error).__name__, "points": []}
    discs = [p for p in levels["points"] if p.get("level") not in (None, UNASSIGNED)]
    reported = positive_labels((manifest.get("trainingLabels") or {}).get("labels", []))
    links = [link for key_image in content.get("keyImages", [])
             for link in link_key_image(key_image, discs, levels.get("frameOfReferenceUid", ""), reported)]
    result = {"schemaVersion": SCHEMA_VERSION, "studyKey": content["studyKey"],
              "sourceRevision": digest(content), "levelMapSource": levels["source"], "links": links}
    if "detail" in levels:
        result["levelMapDetail"] = levels["detail"]
    return result


def eligible(manifest):
    return (manifest.get("content", {}).get("exam") == "LUMBAR_SPINE"
            and manifest.get("annotationComplete")
            and manifest.get("sourceArchive", {}).get("availableSourcesArchived"))


def run(archive, root, run_model=True, limit=None, progress=None):
    output_dir = root / "keyimage-links"
    summary = {"studies": 0, "keyImages": 0, "byMethod": {}, "byLevelMap": {}}
    index = []
    for file in sorted(archive.glob("*/latest.json")):
        manifest = json.loads(file.read_text())
        if not eligible(manifest):
            continue
        if limit is not None and summary["studies"] >= limit:
            break
        result = link_study(file.parent, manifest, root, run_model)
        name = digest(result["studyKey"]) + ".json"
        write_json(output_dir / name, result)
        index.append({"linkPath": name, "sourceRevision": result["sourceRevision"],
                      "levelMapSource": result["levelMapSource"]})
        summary["studies"] += 1
        source = result["levelMapSource"] + (f" ({result['levelMapDetail']})" if "levelMapDetail" in result else "")
        summary["byLevelMap"][source] = summary["byLevelMap"].get(source, 0) + 1
        for link in result["links"]:
            summary["keyImages"] += 1
            summary["byMethod"][link["method"]] = summary["byMethod"].get(link["method"], 0) + 1
        if progress:
            progress(summary["studies"])
    # Consumers use this index, not a directory glob, so outdated link files never count.
    write_json(output_dir / "index.json", {"schemaVersion": SCHEMA_VERSION, "studies": index})
    return summary


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--archive", type=Path, required=True)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--cached-only", action="store_true",
                        help="Use existing level maps only; never run the segmentation model")
    parser.add_argument("--limit", type=int)
    args = parser.parse_args()
    result = run(args.archive, args.root.resolve(), run_model=not args.cached_only, limit=args.limit,
                 progress=lambda n: print(f"linked {n} studies", file=sys.stderr, flush=True))
    print(json.dumps(result, indent=2))
