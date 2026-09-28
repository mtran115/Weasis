import hashlib
import json
from pathlib import Path
import sys

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import link_key_images as linker

FRAME = "1.2.3.frame"
# LPS: +x patient left, +y posterior, +z superior.
DISCS = [
    {"level": "L3-L4", "lps": [0.0, -50.0, 70.0]},
    {"level": "L4-L5", "lps": [0.0, -50.0, 40.0]},
    {"level": "L5-S1", "lps": [0.0, -50.0, 10.0]},
]
LABELS = [
    {"level": "L4-L5", "task": "spinal_canal_stenosis", "laterality": "central", "value": "moderate", "certainty": "asserted"},
    {"level": "L5-S1", "task": "neural_foraminal_stenosis", "laterality": "left", "value": "mild", "certainty": "asserted"},
    {"level": "L3-L4", "task": "spinal_canal_stenosis", "laterality": "central", "value": "no_reportable_finding", "certainty": "inferred"},
]


def sagittal(x):
    # Rows run posterior (+y), columns run inferior (-z); pixel (0, 0) at y=-100, z=100.
    return {"imageOrientationPatient": [0, 1, 0, 0, 0, -1], "imagePositionPatient": [x, -100, 100],
            "pixelSpacing": [1, 1], "rows": 200, "columns": 200, "frameOfReferenceUid": FRAME}


def axial(z):
    return {"imageOrientationPatient": [1, 0, 0, 0, 1, 0], "imagePositionPatient": [-100, -150, z],
            "pixelSpacing": [1, 1], "rows": 200, "columns": 200, "frameOfReferenceUid": FRAME}


def key_image(geometry, arrows=(), source="last_finding_suggestion"):
    return {"id": "ki-1", "reference": {"sopInstanceUid": "1.2.3.4", "geometry": geometry},
            "sourceArrows": list(arrows), "findingId": "f-1", "findingLinkSource": source}


def link(image, discs=DISCS):
    return linker.link_key_image(image, discs, FRAME, linker.positive_labels(LABELS))


def test_pixels_map_to_patient_coordinates_and_planes_are_classified():
    assert linker.pixel_to_lps(sagittal(3.0), 10, 20) == [3.0, -90.0, 80.0]
    assert linker.plane_of(sagittal(0)) == "sagittal"
    assert linker.plane_of(axial(0)) == "axial"
    assert (linker.side_of(2), linker.side_of(12), linker.side_of(-12)) == ("central", "left", "right")


def test_only_asserted_findings_are_linkable_because_omitted_levels_are_normal():
    reported = linker.positive_labels(LABELS)
    assert sorted(reported) == ["L4-L5", "L5-S1"]


def test_an_arrow_links_to_the_nearest_disc_and_its_findings_on_that_side():
    # Pixel (50, 60) on the midline sagittal is y=-50, z=40: the L4-L5 disc.
    [result] = link(key_image(sagittal(1.0), [{"tipX": 50, "tipY": 60, "tipInsideImage": True}]))
    assert (result["method"], result["confidence"], result["level"], result["side"]) == ("arrow", "high", "L4-L5", "central")
    assert result["labels"] == [{"task": "spinal_canal_stenosis", "laterality": "central", "value": "moderate"}]
    assert result["storedLink"] == {"findingId": "f-1", "findingLinkSource": "last_finding_suggestion", "trusted": False}


def test_a_lateral_posterior_foraminal_arrow_links_by_position_along_the_spine():
    # Left parasagittal slice; tip 20 mm posterior and 5 mm above L4-L5: 3D distance ~29 mm.
    [result] = link(key_image(sagittal(20.0), [{"tipX": 70, "tipY": 55, "tipInsideImage": True}]))
    assert (result["method"], result["level"], result["side"]) == ("arrow", "L4-L5", "left")


def test_an_arrow_between_two_discs_is_left_for_review():
    [result] = link(key_image(sagittal(0), [{"tipX": 50, "tipY": 45, "tipInsideImage": True}]))
    assert (result["method"], result["candidateLevels"]) == ("arrow_between_levels", ["L3-L4", "L4-L5"])


def test_arrows_far_from_the_spine_and_outside_the_image_do_not_link():
    far = link(key_image(sagittal(0), [{"tipX": 5, "tipY": 5, "tipInsideImage": True}]))
    assert far[0]["method"] == "arrow_not_near_spine"
    outside = link(key_image(axial(40.5), [{"tipX": 5, "tipY": 5, "tipInsideImage": False}]))
    assert outside[0]["method"] == "axial_plane"


def test_an_axial_slice_links_to_the_disc_it_crosses():
    [result] = link(key_image(axial(41.0)))
    assert (result["method"], result["level"], result["distanceMm"]) == ("axial_plane", "L4-L5", 1.0)
    assert link(key_image(axial(25.0)))[0]["method"] == "axial_between_discs"


def test_a_sagittal_slice_without_arrow_links_only_when_its_side_has_one_candidate_level():
    left = link(key_image(sagittal(18.0)))[0]
    assert (left["method"], left["level"], left["side"]) == ("sagittal_side_single_level", "L5-S1", "left")
    right = link(key_image(sagittal(-18.0)))[0]
    assert (right["method"], right["candidateLevels"]) == ("no_matching_finding", [])
    extra = LABELS + [{"level": "L3-L4", "task": "spinal_canal_stenosis", "laterality": "central", "value": "mild", "certainty": "asserted"}]
    midline = linker.link_key_image(key_image(sagittal(0)), DISCS, FRAME, linker.positive_labels(extra))[0]
    assert (midline["method"], midline["candidateLevels"]) == ("ambiguous", ["L3-L4", "L4-L5"])


def test_no_level_map_or_a_different_frame_never_guesses():
    assert link(key_image(sagittal(0)), discs=[])[0]["method"] == "no_level_map"
    other = key_image({**sagittal(0), "frameOfReferenceUid": "other"})
    assert link(other)[0]["method"] == "different_frame_of_reference"
    assert link(key_image(sagittal(0), source="selected_finding"))[0]["storedLink"]["trusted"] is True


def test_reader_reviewed_maps_are_used_only_when_accepted_or_corrected(tmp_path):
    key = "1.2.840.study"
    folder = tmp_path / "feedback" / hashlib.sha256(key.encode()).hexdigest()
    folder.mkdir(parents=True)
    saved = {"decision": "corrected", "points": DISCS, "proposal": {"frameOfReferenceUid": FRAME}}
    (folder / "latest.json").write_text(json.dumps(saved))
    assert linker.feedback_map(tmp_path, key)["source"] == "reader_reviewed"
    (folder / "latest.json").write_text(json.dumps({**saved, "decision": "skipped"}))
    assert linker.feedback_map(tmp_path, key) is None
    unassigned = [{**DISCS[0], "level": "Unassigned"}]
    (folder / "latest.json").write_text(json.dumps({**saved, "points": unassigned}))
    assert linker.feedback_map(tmp_path, key) is None


def test_cooldown_follows_only_studies_that_ran_the_model(tmp_path, monkeypatch):
    archive = tmp_path / "archive"
    for name in ("a", "b"):
        folder = archive / name
        folder.mkdir(parents=True)
        (folder / "latest.json").write_text(json.dumps({
            "annotationComplete": True, "sourceArchive": {"availableSourcesArchived": True},
            "content": {"exam": "LUMBAR_SPINE", "studyKey": name, "keyImages": []}}))
    computed = {"a": True, "b": False}
    monkeypatch.setattr(linker, "link_study", lambda directory, manifest, root, run_model: (
        {"studyKey": manifest["content"]["studyKey"], "sourceRevision": "r",
         "levelMapSource": "model_provisional", "links": []}, computed[manifest["content"]["studyKey"]]))
    pauses = []
    monkeypatch.setattr(linker.time, "sleep", pauses.append)
    summary = linker.run(archive, tmp_path / "root", pause_seconds=60)
    assert summary["studies"] == 2
    assert pauses == [60]


def test_uncertain_model_numbering_is_renumbered_upward_from_l5_s1_when_safe():
    # Six lumbar levels: the model's lowest disc is L6-S1, so every level shifts up by one.
    model = [{"id": f"d{i}", "level": level, "lps": [0.0, -50.0, 100.0 - 30 * i]}
             for i, level in enumerate(["T12-L1", "L1-L2", "L2-L3", "L3-L4", "L4-L5", "L5-L6", "L6-S1"])]
    renumbered = linker.number_by_reporting_convention(model)
    assert [p["level"] for p in renumbered] == list(linker.CONVENTION_SEQUENCE)
    assert [p["id"] for p in renumbered] == [p["id"] for p in model]
    missing_lumbosacral = [{**p} for p in model[:-1]]
    assert linker.number_by_reporting_convention(missing_lumbosacral) is None
    assert linker.number_by_reporting_convention(model[-4:]) is None


def test_an_explicit_reader_uncertain_is_never_renumbered(tmp_path):
    key = "1.2.840.study"
    folder = tmp_path / "feedback" / hashlib.sha256(key.encode()).hexdigest()
    folder.mkdir(parents=True)
    (folder / "latest.json").write_text(json.dumps({"decision": "uncertain", "points": DISCS}))
    assert linker.feedback_map(tmp_path, key) == {"source": "reader_marked_uncertain", "points": []}
