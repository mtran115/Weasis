# Weasis Report Composer V1

Report Composer is an experimental, local-only reporting tool for the custom Weasis build. It
creates a concise instruction packet for a transcriptionist; it does not create or modify the
final formatted radiology report.

## Workflow

1. Open a DICOM study and show the **Report Composer** tool on the right side of the 2D viewer.
2. In **Compose**, confirm the automatically selected MRI exam. Cervical, thoracic, and lumbar
   spine MRI each open a region-specific structured form for alignment, per-level listhesis with
   optional displacement, level-selectable degenerative changes, and per-level disc, facet,
   posterior-element, foraminal, and free-text findings. Brain, knee, shoulder, and wrist MRI open
   direct-click forms for their commonly repeated findings; other exams use the phrase catalog.
   The shared **Report text / instructions** box accepts any additional text for every exam and
   saves it automatically in the current study draft.
   Press **Add Selected Findings** to finalize and clear the form, or move directly to **Key
   Images** or **Preview** to update the draft while preserving the current checkboxes. Reopening
   Preview does not duplicate unchanged selections. Every generated finding remains editable in
   the study draft. A manual exam selection is remembered for that study while Weasis remains
   open.
3. In **Key Images**, choose the viewport by its layout number, series, and current image, then
   capture it. Press on the finding to place the arrowhead, then drag outward to place its tail, or
   click the finding for a short automatic arrow. You can also keep the key image without an arrow.
   The series and image reference are recorded automatically. Assign the **Capture key image from
   viewport 1** and **Capture key image from viewport 2** actions in Keyboard Shortcuts to capture
   either viewport directly.
4. Edit the key-image caption when the arrow instruction needs clarification.
5. In **Preview**, choose the approved Google Drive transcription folder and export the packet.

The selected destination is remembered locally. Each study has a separate in-memory draft while
Weasis remains open. Compose starts at the top when a study is first viewed and remembers that
study's scroll position when switching tabs or returning to the study during the same session.

After a successful export, nonblank text from the shared **Report text / instructions** field is
also appended to `.weasis/data/report-composer/instruction-history.jsonl`. This local learning
history contains only a timestamp, normalized exam category, and the raw instruction text. It does
not copy patient or study metadata and is not included in the exported packet. Because the text is
stored verbatim, anything manually typed into that field is retained.

Use the minus button in the Report Composer title bar to collapse the panel to its right-edge tab.
Reopening the tab preserves the current in-memory study draft.

Use **Pop Out** in the case header to move the same live composer to another connected monitor when
one is available. The detached window keeps every study draft, finding, and key image in place.
Use **Dock Back** to return it to the Weasis window.

## Exported Packet

Each export creates a new patient/exam/date folder without overwriting an existing packet:

```text
PATIENT - EXAM - DATE/
  TRANSCRIPTION_INSTRUCTIONS.docx
  REPORT_TEXT.txt
  KEY_IMAGES/
    KI-01 - SERIES 8 IMAGE 23.png
```

`TRANSCRIPTION_INSTRUCTIONS.docx` contains patient and exam identifiers, one continuous text block
combining the findings, selected impression text, and additional free text, plus key-image
references and embedded annotated images. `REPORT_TEXT.txt` is a plain text fallback. The
transcriptionist copies the instructions into the separately maintained, specially formatted
report template.

## Version 1 Boundaries

- The starter phrase catalog covers brain, cervical spine, thoracic spine, lumbar spine, shoulder,
  elbow, wrist, hand, hip, knee, ankle, and foot MRI, with a General MRI fallback.
- The cervical, thoracic, and lumbar spine forms can add several findings at once. Use **Other
  Finding** for anything not represented by the brain, spine, knee, shoulder, or wrist structured
  controls; both paths feed the same findings and key-image lists.
- Catalog phrases are editable workflow aids rather than diagnostic decision support. The reading
  radiologist remains responsible for reviewing the generated findings and impression.
- Drafts are not persisted after Weasis exits.
- A capture supports multiple optional arrows.
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

The dedicated launcher uses Homebrew OpenJDK 26 to avoid the native macOS Swing accessibility
crash tracked as OpenJDK JDK-8372757. Override its runtime only when necessary with
`WEASIS_REPORT_COMPOSER_JAVA_HOME`.

The dedicated launcher uses `~/.weasis-report-composer-dev` for its singleton lock and boot log. A
distinct `report-composer-v1` profile and source ID give it separate preferences and an OSGi cache
from the daily-reading build, even though Weasis keeps those profile directories under
`~/.weasis`.
