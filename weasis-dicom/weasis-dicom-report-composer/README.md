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
   the study draft. Selections and unfinished text also save locally in the background; a manual
   exam selection is restored with that study after restarting Weasis.
3. In **Key Images**, choose the viewport by its layout number, series, and current image, then
   capture it. Press on the finding to place the arrowhead, then drag outward to place its tail, or
   click the finding for a short automatic arrow. Press **Enter** to save the key image with its
   current arrows, or without arrows if none were drawn. **Escape** cancels the capture.
   The series and image reference are recorded automatically. Assign the **Capture key image from
   viewport 1** and **Capture key image from viewport 2** actions in Keyboard Shortcuts to capture
   either viewport directly.
4. Edit the key-image caption when the arrow instruction needs clarification; edits save locally
   automatically. The **Finding** selector shows its association and lets you change or clear it.
5. In **Preview**, export the packet. For locally imported studies the default destination is
   `notes` inside the folder selected for import. It is created on the first export if needed.
   **Choose Transcription Folder** lets you select another destination, including a synced folder.

The default follows the active study's import folder, including recursive imports of nested
study/series folders. Individual file imports use the file's parent; DICOMDIR uses its containing
folder, and local ZIP imports retain the original import folder rather than an extraction cache.
Existing notes and earlier packets are preserved. Word instruction documents are ignored when
reimporting the folder, so their ZIP format is not mistaken for a DICOM archive.

A manual destination applies to studies from that same imported folder for the current session.
Studies from other folders get their own `notes` default. For unknown, temporary/downloaded, or
ambiguous sources (the same study loaded from multiple roots), export asks for a destination.
If `notes` cannot be created or written, the draft is retained and another folder can be selected.
The last manually chosen folder is remembered as a chooser starting location, not reused as an
automatic destination for an unrelated study. Folder creation occurs in the export worker;
viewing a study never creates folders or checks drive permissions.

Each study has a separate durable local draft.
Compose starts at the top when a study is first viewed and remembers that
study's scroll position when switching tabs or returning to the study during the same session.

After a successful export, nonblank text from the shared **Report text / instructions** field is
also appended to `.weasis/data/report-composer/instruction-history.jsonl`. This local learning
history contains only a timestamp, normalized exam category, and the raw instruction text. It does
not copy patient or study metadata and is not included in the exported packet. Because the text is
stored verbatim, anything manually typed into that field is retained.

### Spine form keyboard shortcuts

Hover over a cervical, thoracic, or lumbar level to target it without clicking. A blue outline
marks that level, and the footer shows the available keys or the pending choice. These shortcuts
operate the existing form controls and use the same draft saving and training capture.

| Keys, pressed in sequence | Action at the hovered level |
| --- | --- |
| B / P / E / A | Toggle bulge / protrusion / extrusion / annular fissure |
| V | Toggle ventral epidural lipomatosis |
| C, then numpad 1 / 2 / 3 | Mild / moderate / severe canal stenosis, where available |
| L or R, then numpad 1 / 2 / 3 | Left or right foraminal stenosis severity |
| F or H, then L / R / B | Facet arthrosis or posterior-element hypertrophy: left / right / bilateral |
| C / L / R / F / H, then numpad 0 | Clear that choice |
| D, then numpad location numbers, then Enter | Choose protrusion/extrusion locations |
| T | Focus the level's free-text field |
| Escape | Cancel a pending choice, or leave a composer text field |
| Cmd+Z (Ctrl+Z on other platforms) | Undo a keyboard field edit, unless intervening edits changed that section |
| Cmd+Enter (Ctrl+Enter on other platforms) | Add Selected Findings |

**Only numpad digits select numeric choices.** Top-row numbers retain the viewer's shortcuts,
including when focus is on a spine-form button or panel. Text fields retain normal typing and
text-editing shortcuts. Left/right key-image shortcuts are not reassigned. Holding a finding key
does not repeatedly toggle it.

P and E retain the current disc location selection, initially Central, and remain mutually
exclusive. In the D picker: numpad 1 Central, 2 Broad-based, 3 Left central, 4 Right central,
5 Left subarticular, 6 Right subarticular, 7 Left foraminal, 8 Right foraminal. The first number
replaces the prior locations; subsequent numbers toggle additional locations. Enter applies the
selection; Escape cancels. An empty selection leaves the prior locations unchanged.

Moving to another level, leaving the form, or switching studies cancels pending choices. Starting
another letter command also cancels the pending choice. Adding findings or clearing the form
starts a new field-undo history. In a detached composer, hover commands also work while its image
viewer is the active window; T explicitly activates the composer to enter text.

### Shoulder form keyboard shortcuts

Hover over a tendon, a specific bursitis/AC joint row, the biceps section, or the labrum.
The blue outline identifies the target; the footer shows its shortcuts. Numeric selections use
**only the numpad**, including when Num Lock is off. Top-row digits and key-image arrows retain
their viewer shortcuts. Text fields retain normal typing and text-editing shortcuts.

| Keys | At a hovered rotator cuff tendon |
| --- | --- |
| Numpad 1 / 2 / 3 | Mild / moderate / severe tendinosis |
| Numpad decimal / 0 | Minimal tendinosis / clear tendinosis (preserves tears) |
| A / B / I / F | Toggle articular-surface / bursal-surface / interstitial / full-thickness tear |
| H / P / G | Toggle high-grade / at footprint / background tendinosis |
| T | Focus that tendon's Details field |

Multiple tear types can be selected. Modifiers and details require a selected tear; high-grade
requires a partial-thickness or interstitial tear. T shows a hint if tear details are unavailable.
For example, hover over supraspinatus and press A, H, P for a high-grade articular-surface tear at
the footprint; T then lets you enter measurements or other details.

Over a **bursitis row, AC joint, or long head of biceps**, numpad 1/2/3 sets mild/moderate/severe,
numpad decimal sets minimal, and numpad 0 clears that finding. Over the **labrum**, A/S/P/I toggles
anterior/superior/posterior/inferior involvement and C toggles an adjacent paralabral cyst. T in
these sections focuses the shared shoulder free text.

Escape leaves a composer text field. Cmd+Z (or Ctrl+Z) outside text undoes the last keyboard field
edit unless subsequent mouse/text edits changed that section. Cmd+Enter (or Ctrl+Enter) outside
text invokes Add Selected Findings. These commands require a hovered section. Holding a key does
not repeatedly toggle it. Adding findings, clearing the form, or switching studies resets undo.
Shortcuts also work in a detached composer while its viewer is active; T activates the composer
for text entry. Keyboard edits use the existing draft saving and finding capture.

Use the minus button in the Report Composer title bar to collapse the panel to its right-edge tab.
Reopening the tab preserves the current study draft.

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
- Drafts, unfinished form input, and key images restore when the same study is reopened.
- A capture supports multiple optional arrows.
- Export is local filesystem output. A Google Drive-synced folder handles delivery; the module does
  not call Google APIs or any AI service.
- The packet includes patient information, so its destination must remain within the approved
  clinical workflow.

## Phase 1: local lumbar training capture

Read and mark positive findings as usual. No per-level negative checklist is required. Capture is
local and does not call an AI service, rent GPUs, or upload scans. The status line shows background
save progress and failures. Generic free-text findings still require **Add** or **Update** to enter
the report; unfinished text is saved as editor input and must be resolved before completion.

A successful instruction-packet export also saves an immutable **completed annotation pass**.
For a lumbar case with no findings, images, or report text, the same button becomes **Finish Normal
Case** and records completion locally without creating an empty transcription packet. This marker
is a dataset workflow state, not a signed clinical report or validation of AI output. Later report
edits return the latest case to draft status; earlier completed revisions remain available.

Records live in `~/.weasis/data/report-composer/training-v1/`, independently of the chosen clinical
export folder. Each hashed study directory contains:

- `latest.json`: context, exam, findings, original shorthand, structured selection evidence, pending
  editor state, key-image associations, parser version, and annotation status.
- `revisions/<revision-id>.json`: immutable completed annotation revisions.
- `images/<sha256>.png`: captured display pixels without Report Composer arrows. Screenshot-space
  arrows and original-image coordinates are separate manifest fields.
- `dicom/<sha256>.dcm`: checksum-deduplicated local source files available from the loaded lumbar
  study when completion was requested. DICOM archiving has its own worker so it does not hold up
  draft saves or study restoration.

These are identifiable local clinical records. Patient metadata and original source files are
retained. They are not de-identified or automatically removed by this feature. The archive records
missing files and whether inventory covered the loaded study or only the active series; it never
claims that the complete original exam was available. DICOM frame indices are distinct from the
viewer's sorted slice index. Unavailable image geometry remains explicitly unavailable.

Structured selections retain their original values before report prose is generated. A small,
versioned local parser also preserves shorthand such as `T2 hyperint lesion L4 likely intraoss
hemang`: it records the T2 lesion observation separately from the uncertain hemangioma
interpretation. Unsupported text stays available for later review instead of being discarded.

For completed lumbar passes, the initial reporting convention covers canal stenosis and left/right
foraminal and subarticular stenosis at L1-L2 through L5-S1. Unmentioned scoped items may become
`no_reportable_finding` with `inferred_negative` provenance. These are reporting-convention labels,
not claims of verified anatomical normality. Drafts never infer negatives. Uncertain, limited,
conflicting, unsupported, or manually changed structured evidence blocks negative inference for
that case until reviewed. Other exams receive local draft persistence but no lumbar labels or
whole-study archive.

Key images record their finding ID and how that link was made. A fallback to the last finding is
marked as a suggestion; use the **Finding** selector to confirm or correct it. Deleting a finding
clears its key-image links. Report edits and structured-form restoration never silently retarget a
key image to a different finding.

This phase collects data only. Dataset review, de-identification, patient-level train/test splits,
model training, and evaluated prelabels are later work.

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

Development launch configurations load Weasis application bundles directly from this worktree's
module `target` directories. Third-party dependencies still come from the local Maven repository.
This prevents an install from another worktree from replacing Report Composer's core library and
disabling its capture shortcuts. Build the modules after source changes, then restart the app to
load the updated bundles. When upgrading from a build that predates local capture, export current
drafts before restarting; those older builds hold drafts only in memory.
