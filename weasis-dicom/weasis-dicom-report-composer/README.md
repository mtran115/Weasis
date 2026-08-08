# Weasis Report Composer V1

Report Composer is an experimental, local-only reporting tool for the custom Weasis build. It
creates a concise instruction packet for a transcriptionist; it does not create or modify the
final formatted radiology report.

## Workflow

1. Open a DICOM study and show the **Report Composer** tool on the right side of the 2D viewer.
2. In **Compose**, choose a wrist category, structure, and finding. Edit the generated finding or
   impression when needed, then add it to the study draft.
3. In **Key Images**, capture the active viewer. Click or drag on the preview to place an arrow,
   or keep the key image without an arrow. The series and image reference are recorded
   automatically.
4. Edit the key-image caption when the arrow instruction needs clarification.
5. In **Preview**, choose the approved Google Drive transcription folder and export the packet.

The selected destination is remembered locally. Each study has a separate in-memory draft while
Weasis remains open.

## Exported Packet

Each export creates a new patient/exam/date folder without overwriting an existing packet:

```text
PATIENT - EXAM - DATE/
  TRANSCRIPTION_INSTRUCTIONS.docx
  REPORT_TEXT.txt
  KEY_IMAGES/
    KI-01 - SERIES 8 IMAGE 23.png
```

`TRANSCRIPTION_INSTRUCTIONS.docx` contains patient and exam identifiers, selectable findings and
impression text, key-image references, and embedded annotated images. `REPORT_TEXT.txt` is a plain
text fallback. The transcriptionist copies the instructions into the separately maintained,
specially formatted report template.

## Version 1 Boundaries

- The structured phrase catalog currently covers wrist MRI findings.
- Drafts are not persisted after Weasis exits.
- A capture supports one optional arrow. Capture the view again for another arrow or image.
- Export is local filesystem output. A Google Drive-synced folder handles delivery; the module does
  not call Google APIs or any AI service.
- The packet includes patient information, so its destination must remain within the approved
  clinical workflow.

## Development

This module lives on the `report-composer-v1` branch in the isolated
`~/Coding/Weasis_Report_Composer` worktree. It is separate from the `daily-reading` worktree.

Run the focused tests:

```bash
mvn -pl weasis-dicom/weasis-dicom-report-composer -am test
```

Install and launch the isolated development build:

```bash
mvn -Dweasis.arch=macosx-aarch64 -DskipTests install
./scripts/run-report-composer-dev.sh
```

The dedicated launcher uses `~/.weasis-report-composer-dev` for its singleton lock and boot log. A
distinct `report-composer-v1` profile and source ID give it separate preferences and an OSGi cache
from the daily-reading build, even though Weasis keeps those profile directories under
`~/.weasis`.
