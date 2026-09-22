#!/usr/bin/env python3
"""Local-only, serial lumbar localization worker. JSON over stdio; no listening socket.

Original DICOMs are read only. Predictions are provisional, never report findings or labels.
"""
import os
os.environ.setdefault("OMP_NUM_THREADS", "2")
os.environ.setdefault("OPENBLAS_NUM_THREADS", "2")
os.environ.setdefault("ITK_GLOBAL_DEFAULT_NUMBER_OF_THREADS", "2")
os.environ.setdefault("SPINEPS_NO_CITATION_REMINDER", "1")
import contextlib
import hashlib
import json
import math
from pathlib import Path
import sys
import tempfile
import time
from urllib.parse import urlparse, unquote

MODEL_ID = "spineps-a240cd5890-t2w1.0.9-instance1.2.0-labeling1.4.0-mps-fold0-no-tta-v5"
PIPELINE = None
UNASSIGNED = "Unassigned"
STANDARD_LEVELS = ("T11-T12", "T12-L1", "L1-L2", "L2-L3", "L3-L4", "L4-L5", "L5-S1")
REQUIRED_LUMBAR_LEVELS = set(STANDARD_LEVELS[2:])


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def write_json(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    fd, name = tempfile.mkstemp(dir=path.parent, prefix=".pending-")
    try:
        with os.fdopen(fd, "w") as stream:
            json.dump(value, stream, allow_nan=False)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(name, path)
    finally:
        if os.path.exists(name):
            os.unlink(name)


def local_path(uri):
    parsed = urlparse(uri)
    if parsed.scheme != "file" or parsed.netloc or parsed.query or parsed.fragment:
        raise ValueError("A local DICOM source is required")
    return Path(unquote(parsed.path))


def select_series(request):
    """Select an actual sagittal T2 series using geometry and DICOM sequence metadata."""
    import numpy as np
    import pydicom
    groups = {}
    seen = set()
    for source in request["images"]:
        if source.get("studyInstanceUid") != request["studyInstanceUid"]:
            continue
        reference = source["reference"]
        try:
            file = local_path(reference["sourceUri"])
            header = pydicom.dcmread(file, stop_before_pixels=True)
            if (str(header.get("StudyInstanceUID", "")) != request["studyInstanceUid"]
                or str(header.get("SOPInstanceUID", "")) != reference.get("sopInstanceUid")
                or str(header.get("SeriesInstanceUID", "")) != reference.get("seriesInstanceUid")
                or str(header.get("Modality", "")) != "MR"
                or int(header.get("NumberOfFrames", 1)) != 1):
                continue
            orient = np.asarray(header.ImageOrientationPatient, dtype=float)
            normal = np.cross(orient[:3], orient[3:])
            if abs(normal[0]) < .90:
                continue
            desc = str(header.get("SeriesDescription", "")).lower()
            if any(word in desc for word in ("local", "scout", "survey", "stir", "t1", "flair")):
                continue
            te = float(header.get("EchoTime", 0))
            if te < 60 and "t2" not in desc:
                continue
            if not str(header.get("FrameOfReferenceUID", "")):
                continue
            identity = (str(header.SeriesInstanceUID), str(header.SOPInstanceUID))
            if identity not in seen:
                groups.setdefault(identity[0], []).append((file, header, reference))
                seen.add(identity)
        except (OSError, ValueError, AttributeError, KeyError):
            continue
    valid = [items for items in groups.values() if 8 <= len(items) <= 160]
    if not valid:
        raise ValueError("No supported sagittal T2 series is available")
    # Prefer non-fat-suppressed routine T2, then the series with more sagittal slices.
    valid.sort(key=lambda items: (any(w in str(items[0][1].get("SeriesDescription", "")).lower()
                                    for w in ("fat", "fs", "dixon")), -len(items)))
    return valid[0]


def make_volume(items):
    import numpy as np
    import nibabel as nib
    import pydicom
    first = items[0][1]
    orient = np.asarray(first.ImageOrientationPatient, dtype=float)
    spacing = np.asarray(first.PixelSpacing, dtype=float)
    if (orient.shape != (6,) or spacing.shape != (2,)
        or not np.all(np.isfinite(orient)) or not np.all(np.isfinite(spacing))
        or np.any(spacing <= 0) or abs(np.linalg.norm(orient[:3])-1) > .01
        or abs(np.linalg.norm(orient[3:])-1) > .01 or abs(np.dot(orient[:3], orient[3:])) > .01):
        raise ValueError("Invalid DICOM geometry")
    normal = np.cross(orient[:3], orient[3:])
    items = sorted(items, key=lambda item: float(np.dot(item[1].ImagePositionPatient, normal)))
    positions = np.asarray([item[1].ImagePositionPatient for item in items], dtype=float)
    if not np.all(np.isfinite(positions)):
        raise ValueError("Invalid slice positions")
    differences = np.diff(positions, axis=0)
    step = np.median(differences, axis=0)
    if np.linalg.norm(step) < .2 or np.max(np.linalg.norm(differences-step, axis=1)) > .3:
        raise ValueError("Sagittal series has missing or irregular slices")
    arrays = []
    for file, header, _ in items:
        if (not np.allclose(header.ImageOrientationPatient, orient, atol=1e-4)
            or not np.allclose(header.PixelSpacing, spacing, atol=1e-4)
            or str(header.FrameOfReferenceUID) != str(first.FrameOfReferenceUID)
            or int(header.Rows) != int(first.Rows) or int(header.Columns) != int(first.Columns)):
            raise ValueError("Sagittal series geometry is inconsistent")
        ds = pydicom.dcmread(file)
        pixels = ds.pixel_array.astype(np.float32)
        if pixels.ndim != 2 or not np.all(np.isfinite(pixels)):
            raise ValueError("Unsupported pixel data")
        pixels = pixels * float(ds.get("RescaleSlope", 1)) + float(ds.get("RescaleIntercept", 0))
        arrays.append(pixels)
    # NIfTI array axes: original DICOM column, row, slice. Convert LPS to RAS explicitly.
    lps = np.eye(4)
    lps[:3, 0] = orient[:3] * spacing[1]
    lps[:3, 1] = orient[3:] * spacing[0]
    lps[:3, 2] = step
    lps[:3, 3] = positions[0]
    ras = np.diag([-1, -1, 1, 1]) @ lps
    data = np.stack(arrays, axis=2).transpose(1, 0, 2)
    volume = nib.Nifti1Image(data, ras)
    volume.header.set_xyzt_units("mm", "sec")
    return volume, items, arrays


def disc_points(mask, affine):
    import numpy as np
    present = set(int(x) for x in np.unique(mask))
    points = []
    for label in [*range(118, 126), 128]:
        locations = np.argwhere(mask == label)
        if len(locations) < 20:
            continue
        number = label - 119  # 120 is disc below L1, 124 below L5.
        if label == 118:
            level = "T11-T12"
        elif label == 128:
            level = "T13-L1"
        elif label == 119:
            level = "T12-T13" if 28 in present or 128 in present else "T12-L1"
        elif number == 6:
            level = "L6-S1"
        elif number == 5 and 125 not in present and 25 not in present:
            level = "L5-S1"
        else:
            level = f"L{number}-L{number+1}"
        ras = np.asarray(affine) @ np.r_[locations.mean(axis=0), 1]
        points.append({"id": f"disc-{label}", "level": level,
                       "lps": [-float(ras[0]), -float(ras[1]), float(ras[2])]})
    points.sort(key=lambda p: -p["lps"][2])
    return points


def assess_points(points):
    import numpy as np
    reasons = ["Absolute numbering requires reader review; lumbar-only images may not establish the vertebral count."]
    if not 4 <= len(points) <= 10:
        raise ValueError("The model did not produce a usable set of disc landmarks")
    if len({p["level"] for p in points}) != len(points):
        raise ValueError("The model returned duplicate level labels")
    for p in points:
        if len(p["lps"]) != 3 or not all(math.isfinite(x) for x in p["lps"]):
            raise ValueError("Invalid model coordinates")
    for a, b in zip(points, points[1:]):
        if not 8 <= np.linalg.norm(np.array(a["lps"])-b["lps"]) <= 75:
            raise ValueError("Disc spacing failed the geometry check")
    if numbering_uncertain(points):
        reasons.append("Numbering uncertain—review required. Confirm the entire sequence before using level labels.")
    return reasons


def numbering_uncertain(points):
    """An unexpected count/order can shift every level, not just the anomalous label."""
    levels = [p["level"] for p in points]
    if any(level not in STANDARD_LEVELS for level in levels):
        return True
    if not REQUIRED_LUMBAR_LEVELS.issubset(levels):
        return True
    order = [STANDARD_LEVELS.index(level) for level in levels]
    return any(b != a + 1 for a, b in zip(order, order[1:]))


def reviewed_numbering_proposal(model_points, unexpected_vertebrae=False):
    reasons = assess_points(model_points)
    uncertain = unexpected_vertebrae or numbering_uncertain(model_points)
    if uncertain and len(reasons) == 1:
        reasons.append("Numbering uncertain—review required. Confirm the entire sequence before using level labels.")
    points = [{**p, "level": UNASSIGNED} for p in model_points] if uncertain else [dict(p) for p in model_points]
    return points, reasons, uncertain


def process(request, root):
    global PIPELINE
    import numpy as np
    from PIL import Image
    if request.get("schemaVersion") != 1 or not request.get("studyInstanceUid") or not request.get("studyKey"):
        raise ValueError("Invalid study request")
    if not 1 <= len(request.get("images", [])) <= 20000:
        raise ValueError("Unsupported study size")
    items = select_series(request)
    # Stable across file relocation, but not changed source pixels/headers or model versions.
    identities = []
    for file, header, _ in items:
        with file.open("rb") as stream:
            identities.append((str(header.SOPInstanceUID), hashlib.file_digest(stream, "sha256").hexdigest()))
    key = digest([MODEL_ID, request["studyInstanceUid"], sorted(identities)])
    directory = root / "proposals" / digest(request["studyKey"]) / key
    output = directory / "proposal.json"
    if output.is_file():
        saved = json.loads(output.read_text())
        if Path(saved.get("previewPath", "")).is_file():
            return saved
    started = time.monotonic()
    volume, items, arrays = make_volume(items)
    directory.mkdir(parents=True, exist_ok=True, mode=0o700)
    import nibabel as nib
    import torch
    torch.set_num_threads(2)
    from spineps import SpinepsPipeline, InstanceConfig
    if PIPELINE is None:
        if not torch.backends.mps.is_available():
            raise ValueError("This pilot runtime requires Apple Silicon MPS")
        torch.mps.set_per_process_memory_fraction(0.4)
        # TPTBox 0.8.2 unconditionally queries CUDA memory, including its MPS path.
        # Adapt only those resource probes; model mathematics and weights are unchanged.
        from TPTBox.segmentation.nnUnet_utils import predictor
        predictor.get_gpu_util = lambda device: 0.0
        predictor.get_gpu_memory_MB = lambda device: 2048.0
        from spineps.get_models import get_actual_model
        paths = [root / "models" / name for name in
                 ("T2w_semantic_v1.0.9", "instance_sagittal_v1.2.0", "T2w_labeling_v1.4.0")]
        if not all(path.is_dir() and any(path.rglob("inference_config.json")) for path in paths):
            raise ValueError("Run the local model setup first")
        # Explicit local paths prevent the registry's automatic download fallback during reading.
        semantic = get_actual_model(paths[0], use_cpu=True).load(folds=("0",))
        semantic.predictor.use_mirroring = False
        PIPELINE = SpinepsPipeline(model_semantic=semantic, model_instance=paths[1],
                                   model_labeling=paths[2], use_cpu=True)
        # PyTorch MPS keeps inference on this Mac. The small labeling classifier stays on CPU.
        PIPELINE.model_semantic.predictor.device = torch.device("mps")
        PIPELINE.model_instance.device = torch.device("mps")
        PIPELINE.model_instance.predictor.to(PIPELINE.model_instance.device)
    # The model loader resets thread count; apply the foreground-friendly limit afterwards.
    torch.set_num_threads(2)
    # Temporary NIfTI has no copied patient/header metadata. Model output stays local.
    with tempfile.TemporaryDirectory(prefix="inference-", dir=directory) as work:
        path = Path(work) / "sub-local_T2w.nii.gz"
        nib.save(volume, path)
        # SPINEPS writes centroid JSON even in in-memory mode; its parent is not created.
        (Path(work) / "derivatives_seg").mkdir(mode=0o700)
        result = PIPELINE.segment(path, output_in_memory=True, instance=InstanceConfig(batch_size=1))
        if not result.success or result.vertebra is None:
            raise ValueError("The localization model could not process this series")
        mask = result.vertebra.get_seg_array()
        model_points = disc_points(mask, result.vertebra.affine)
        model_vertebra_labels = [int(x) for x in np.unique(mask) if 0 < x < 100]
    points, reasons, uncertain = reviewed_numbering_proposal(
        model_points, unexpected_vertebrae=any(x in model_vertebra_labels for x in (25, 28)))
    center = np.mean([p["lps"] for p in points], axis=0)
    orient = np.asarray(items[0][1].ImageOrientationPatient, dtype=float)
    normal = np.cross(orient[:3], orient[3:])
    middle = min(range(len(items)), key=lambda i: abs(np.dot(center-np.asarray(items[i][1].ImagePositionPatient), normal)))
    pixels = arrays[middle]
    lo, hi = np.percentile(pixels, [1, 99.5])
    preview = np.clip((pixels-lo)/max(hi-lo, 1)*255, 0, 255).astype(np.uint8)
    preview_path = directory / "sagittal.png"
    Image.fromarray(preview).save(preview_path)
    os.chmod(preview_path, 0o600)
    reference = dict(items[middle][2])
    header = items[middle][1]
    reference["geometry"] = {"rows": int(header.Rows), "columns": int(header.Columns),
        "imageOrientationPatient": [float(x) for x in header.ImageOrientationPatient],
        "imagePositionPatient": [float(x) for x in header.ImagePositionPatient],
        "pixelSpacing": [float(x) for x in header.PixelSpacing],
        "frameOfReferenceUid": str(header.FrameOfReferenceUID)}
    proposal = {"schemaVersion": 2, "studyKey": request["studyKey"],
                "studyInstanceUid": request["studyInstanceUid"], "proposalId": key,
                "modelId": MODEL_ID, "createdAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
                "seriesInstanceUid": str(items[0][1].SeriesInstanceUID),
                "frameOfReferenceUid": str(items[0][1].FrameOfReferenceUID),
                "reference": reference, "points": points, "reviewReasons": reasons,
                "modelPoints": model_points, "modelVertebraLabels": model_vertebra_labels,
                "numberingUncertain": uncertain,
                "previewPath": str(preview_path), "elapsedSeconds": round(time.monotonic()-started, 2),
                "status": "provisional", "numberingBasis": "model_prediction_not_reader_verified"}
    write_json(output, proposal)
    return proposal


def main():
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--request", type=Path)
    args = parser.parse_args()
    root = args.root.resolve()
    root.mkdir(parents=True, exist_ok=True, mode=0o700)
    os.environ["SPINEPS_SEGMENTOR_MODELS"] = str(root / "models")
    # All third-party logging stays on stderr, outside the JSON protocol and normal Weasis logs.
    output = sys.stdout
    lines = [args.request.read_text()] if args.request else sys.stdin
    for line in lines:
        if not line.strip():
            continue
        request = {}
        try:
            request = json.loads(line)
            with contextlib.redirect_stdout(sys.stderr):
                response = process(request, root)
        except Exception:
            # Do not echo file paths, raw headers, or model tracebacks into UI/logs.
            response = {"schemaVersion": 1, "studyKey": request.get("studyKey", ""),
                        "studyInstanceUid": request.get("studyInstanceUid", ""),
                        "status": "unavailable", "message": "No usable map. Check series availability or continue reading normally."}
        output.write(json.dumps(response, allow_nan=False) + "\n")
        output.flush()

if __name__ == "__main__":
    main()
