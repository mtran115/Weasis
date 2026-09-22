#!/usr/bin/env python3
"""Reserve a fixed patient-level holdout; prepare local source requests without exposing IDs."""
import argparse
import json
from pathlib import Path
from worker import digest, write_json

def prepare(archive, root):
    cases = []
    for file in sorted(archive.glob("*/latest.json")):
        d = json.loads(file.read_text())
        if d.get("content", {}).get("exam") != "LUMBAR_SPINE" or not d.get("annotationComplete"):
            continue  # Never silently use an older completed revision over a newer unfinished edit.
        if not d.get("sourceArchive", {}).get("availableSourcesArchived"):
            continue
        c = d["content"]
        context = c["context"]
        patient = context.get("patientId", "").strip()
        if not patient:
            continue
        group = digest(["lumbar-pilot-patient-v1", patient])
        cases.append((file.parent, d, group))
    split_file = root / "dataset-split-v1.json"
    split = json.loads(split_file.read_text()) if split_file.exists() else {"schemaVersion": 1, "groups": {}}
    if not split["groups"]:
        groups = sorted({group for _, _, group in cases})
        holdout = set(groups[:max(1, round(len(groups)*.2))])
        split["groups"] = {g: "holdout" if g in holdout else "development" for g in groups}
    counts = {"development": 0, "holdout": 0}
    active = []
    for directory, d, group in cases:
        label = split["groups"].setdefault(group, "holdout" if int(group[:8], 16) % 5 == 0 else "development")
        c = d["content"]
        images = []
        for source in d["sourceArchive"]["instances"]:
            file = directory / source["archivedPath"]
            if not file.is_file():
                raise ValueError("An archived source is unavailable")
            reference = dict(source["reference"])
            reference["sourceUri"] = file.resolve().as_uri()
            images.append({"studyInstanceUid": source["studyInstanceUid"], "reference": reference})
        request = {"schemaVersion": 1, "studyKey": c["studyKey"],
                   "studyInstanceUid": c["context"]["studyInstanceUid"], "images": images}
        request_path = Path(label) / (digest(c["studyKey"])+".json")
        write_json(root / "pilot" / request_path, request)
        active.append({"patientGroup": group, "partition": label,
                       "requestPath": str(request_path), "sourceRevision": digest(c)})
        counts[label] += 1
    write_json(split_file, split)
    # Consumers use this index, not a directory glob: old generated files may represent a
    # subsequently unfinished study and must never silently remain eligible for training.
    write_json(root / "pilot" / "index.json", {"schemaVersion": 1, "cases": active})
    return counts

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--archive", type=Path, required=True)
    parser.add_argument("--root", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(prepare(args.archive, args.root)))
