# Local lumbar level-mapping pilot

This pilot proposes disc locations/numbering with pretrained
[SPINEPS](https://github.com/Hendrik-code/spineps). It does **not** predict report findings,
change the report, or train a model automatically. Reader feedback is collected separately
from the existing report annotation archive.

## Reading workflow

- Open a lumbar study as usual. A debounced background request snapshots loaded source metadata;
  DICOM decoding and inference run in a separate local Python process. There is no network API.
- Continue reading. Results do not open dialogs, move focus, change tabs, or scroll the composer.
  The pilot's panel has a stable height while the result arrives.
- **Review…** opens the sagittal image with proposed disc landmarks. Drag dots, change labels,
  Shift-click to add a missing disc, or remove a selected disc. **Lowest disc → Number upward**
  corrects the numbering sequence in one action, based on your selected convention, including
  **T11–T12** when seven standard disc landmarks are present.
- Unexpected model counts, incomplete lumbar coverage, or inconsistent numbering show
  **Numbering uncertain—review required**. The entire sequence is displayed as **Disc 1 ?**, etc.,
  with unassigned level selectors. Assign the sequence yourself or mark it uncertain/skip it.
  Raw research categories such as T12–T13 are retained in the audit data only; they are not
  offered as clinical level choices. Unassigned discs cannot be confirmed or used for navigation.
- Choose the numbering basis: reporting convention, whole-spine count, prior correlation,
  or uncertain. **Confirm map**, **Mark uncertain**, **Skip**, and **Cancel** are distinct.
- **Show levels** displays a transient overlay on nearby sagittal slices in the same study and
  Frame of Reference. Unreviewed labels have a `?`; confirmed labels are green. The small
  **Confirm** button accepts an ordinary displayed map using the reporting convention. Flagged
  variants/incomplete maps require the Review dialog. Nothing is automatically accepted.
- After confirmation, click a level button to jump in an **already open axial series**. Matching
  uses patient LPS coordinates, filtered/sorted image order, matching Frame of Reference, image
  coverage, and a maximum 10 mm plane distance. It does not replace a viewport's series.
- Uncheck **Background mapping** to stop the worker and pause automatic requests. **Retry** is
  an explicit request, including after more source images have loaded. New studies clear the old
  map immediately; delayed responses cannot populate a different study.

Neither provisional nor confirmed pilot overlays enter key-image, clipboard, print, transcription,
or report-training exports. These are transient viewport UI, not DICOM graphics. Map feedback
does not change the report's completed/draft state. Skips, uncertainty, and unreviewed proposals
are **not** confirmed labels or negative findings.

## Setup and runtime

Tested on an Apple M5 Pro / 64 GB Mac with Python 3.13. This first runtime requires Apple Silicon
MPS; CPU-only inference was too slow for reading-time use. Setup downloads public packages and
weights, without opening clinical files:

```bash
./scripts/setup-lumbar-ai.sh
```

The default root is `~/.weasis/ai/lumbar`. Override with `WEASIS_LUMBAR_AI_ROOT` for setup and the
development launcher; other launchers can pass `-Dweasis.lumbar.ai.root=/absolute/path`.
Restart Report Composer after building its modules to load the new Java UI. Installing/updating
the worker does not restart Weasis.

The environment is isolated in `venv/`, with a complete Python dependency lock in this directory.
SPINEPS is pinned to commit `a240cd5890e12ad33ad816f449671c1cd0cd4a78`, with T2w semantic 1.0.9,
instance 1.2.0, and labeling 1.4.0 weights. Setup records installed versions and model SHA-256
checksums locally. Weights are not included in Git.

Inference uses semantic fold 0 without test-time mirroring, batch size 1 for instances, MPS for
the two segmentation networks, and CPU for the small numbering classifier. CPU work is limited
to two threads; MPS allocation is capped at 40% of its recommended working-set memory. Requests
are serial with stale queued requests discarded, a ten-minute timeout, and process cleanup on
shutdown. This configuration has its own model ID; it is not the published ensemble evaluation.

Two compatibility adaptations are confined to the worker: TPTBox 0.8.2's CUDA-only resource
probes receive conservative values for MPS, and the temporary centroid-output directory is
created because SPINEPS still writes centroids in its in-memory mode. No installed package is
modified. Inference resolves explicit local model paths, without automatic model downloads.

Supported input is a locally available, single-frame MR sagittal T2 series with 8–160 slices,
consistent orientations/spacings/Frame of Reference, and regular slice positions. Localizers,
T1, STIR, FLAIR, enhanced multiframe objects, missing slices, and incomplete geometry are not
supported in this pilot. Selection prefers routine non-fat-suppressed T2. Unusable input or
failed geometry checks leave the panel unavailable and the reading workflow intact.

## Local data and reproducibility

The runtime root contains identifiable local clinical material. Hashing directories does not
de-identify the contents:

- `proposals/<study-hash>/<proposal-id>/proposal.json` and `sagittal.png`: model landmarks, the
  source DICOM reference/geometry, review flags, model ID, and timing. Cache identity includes
  model configuration, Study UID, selected SOP UIDs, and actual DICOM file checksums, independent
  of file relocation. Source DICOMs are read only; temporary NIfTI/segmentation files are removed.
  Proposal schema 2 separates `modelPoints`/`modelVertebraLabels` (unaltered model output) from
  display `points` and `numberingUncertain`. The worker v5 cache identity invalidates earlier
  proposals so missing T11–T12 landmarks can be recomputed. Older reader decisions stay in the
  audit history; they are not automatically applied to a changed proposal or silently renumbered.
- `feedback/<study-hash>/events/<event-id>.json`: immutable reader decisions with timestamp,
  local reader account, numbering basis, original proposal, and final landmarks.
- `feedback/<study-hash>/latest.json`: latest review, restored only for the exact study and
  proposal. Atomic private writes run independently of inference; the UI confirms saving only
  after the write completes. An unreadable review is preserved, not silently overwritten.
- `dataset-split-v1.json`: stable patient-group development/holdout assignment.
- `pilot/index.json`: currently eligible completed studies, source revision fingerprints, patient
  groups, partitions, and local request paths. Consumers must use this index, not glob old files.

Prepare or refresh the local evaluation inventory:

```bash
~/.weasis/ai/lumbar/venv/bin/python scripts/lumbar-ai/prepare_pilot.py \
  --archive ~/.weasis/data/report-composer/training-v1 \
  --root ~/.weasis/ai/lumbar
```

Only current completed lumbar records with archived sources and a patient ID are eligible.
All studies sharing a patient ID stay in one partition; old assignments persist. A newer draft
excludes that case rather than falling back to an older completed annotation. The first split
reserves approximately 20% of patients. New patient groups receive a stable hash-based assignment.
The holdout must remain excluded from tuning and training. The current implementation does not
run inference on it or include it in an automatic training job.

Model proposals are weak, unverified supervision. Confirmed reporting-convention numbering is
different from numbering established by whole-spine counting. Lumbar-only coverage cannot
resolve every transitional variant. Keep original model guesses, reader corrections, uncertainty,
and the numbering basis distinct when building a future training dataset. Validate numbering
and localization on a fixed patient-level holdout before adding finding suggestion cards.

## Verification

```bash
~/.weasis/ai/lumbar/venv/bin/python -m pytest scripts/lumbar-ai/tests -q
mvn -pl weasis-dicom/weasis-dicom-report-composer -am \
  -Dweasis.arch=macosx-aarch64 '-Dtest=org.weasis.dicom.reportcomposer.*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false package
```

Tests cover DICOM identity, source-content cache invalidation, oblique/non-square pixel geometry,
irregular series, variant disc codes, durable feedback, exclusion of uncertainty from confirmed
labels, patient-level split stability, stale study responses, the worker protocol, and export
isolation. Automated geometry checks and a few development-case smoke runs do not establish
clinical numbering accuracy; reader review is the next evaluation step.
