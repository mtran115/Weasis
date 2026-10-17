/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.reportcomposer;

import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.MouseInfo;
import java.awt.PointerInfo;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import javax.swing.text.JTextComponent;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.ShortcutManager;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.recorder.InteractionRecorder;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.main.DicomExplorer;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.ExamTemplate;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.InfoLayer;
import org.weasis.dicom.viewer2d.View2d;

/**
 * Steps the viewport under the mouse through its study's sequences in the reader's order, such as
 * sagittal T2, coronal, sagittal STIR, sagittal T1 and axial T2 on a spine MR, skipping sequences
 * other viewports show. The study is the one last opened in the viewer, so after a new study's
 * first sequence goes into one viewport, the next key brings that study into another. Studies
 * without an order in the macros file go by series number. The next-study keys go to {@link
 * StudyStepper}.
 */
final class SequenceStepper {
  private static final Pattern NOT_A_WORD = Pattern.compile("[^A-Z0-9]+");
  private static final Map<String, String> PLANE_WORDS =
      Map.of(
          "SAG",
          "SAG",
          "SAGITTAL",
          "SAG",
          "COR",
          "COR",
          "CORONAL",
          "COR",
          "AX",
          "AX",
          "AXIAL",
          "AX",
          "TRA",
          "AX",
          "TRS",
          "AX",
          "TRANSVERSE",
          "AX");
  static final List<String> PLANES = List.of("SAG", "COR", "AX", "OBL");
  static final List<String> CONTRASTS = List.of("T1", "T2", "STIR", "PD");
  private static final Set<String> INVERSION_RECOVERY = Set.of("STIR", "TIRM", "IR", "FIR");
  private static final Set<String> FAT_SUPPRESSED =
      Set.of("FS", "FATSAT", "SPAIR", "SPIR", "DIXON");
  private static final Set<String> LOCALIZER =
      Set.of(
          "LOC",
          "LOCALIZER",
          "LOCALISER",
          "SCOUT",
          "SURVEY",
          "SCANO",
          "PILOT",
          "CAL",
          "CALIBRATION",
          "PLANE");
  private static SequenceStepper installed;

  private final ViewerMacros macros;
  private final StudyStepper studies;
  // When each viewport last opened a series; the newest names the study being read.
  private final Map<View2d, Long> openedAt = new WeakHashMap<>();
  private long opened;

  private SequenceStepper(ViewerMacros macros) {
    this.macros = macros;
    this.studies = new StudyStepper(macros);
  }

  static synchronized void install(ViewerMacros macros) {
    if (installed != null) return;
    SequenceStepper stepper = new SequenceStepper(macros);
    installed = stepper;
    EventManager.getInstance()
        .addSeriesOpenedListener((view, restored) -> stepper.openedAt.put(view, ++stepper.opened));
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(stepper::dispatch);
  }

  /** The plane and contrast of a sequence, read from its description; either may be "". */
  record Kind(String plane, String contrast) {
    static Kind of(String description) {
      List<String> words = words(description);
      String plane =
          words.contains("OBL") || words.contains("OBLIQUE")
              ? "OBL"
              : words.stream()
                  .map(PLANE_WORDS::get)
                  .filter(Objects::nonNull)
                  .findFirst()
                  .orElse("");
      boolean t2 = words.contains("T2");
      // Fat-suppressed T2 and PD are fluid-sensitive like STIR.
      boolean fluidSensitive =
          (t2 || words.contains("PD")) && words.stream().anyMatch(FAT_SUPPRESSED::contains);
      String contrast;
      if (fluidSensitive || words.stream().anyMatch(INVERSION_RECOVERY::contains)) {
        contrast = "STIR";
      } else if (words.contains("T1")) {
        contrast = "T1";
      } else if (t2) {
        contrast = "T2";
      } else {
        contrast = words.contains("PD") ? "PD" : "";
      }
      return new Kind(plane, contrast);
    }

    boolean fits(String step) {
      return stepWords(step).stream().allMatch(word -> word.equals(plane) || word.equals(contrast));
    }
  }

  /** Rejects a step that is not a plane, a contrast, or one of each, such as "SAG T2". */
  static void checkStep(String step) {
    List<String> words = stepWords(step);
    long planes = words.stream().filter(PLANES::contains).count();
    long contrasts = words.stream().filter(CONTRASTS::contains).count();
    if (words.isEmpty() || planes > 1 || contrasts > 1 || planes + contrasts != words.size()) {
      throw new IllegalArgumentException(
          "Sequence step \""
              + step
              + "\" must be a plane "
              + PLANES
              + ", a contrast "
              + CONTRASTS
              + ", or one of each");
    }
  }

  private static List<String> stepWords(String step) {
    String text = step == null ? "" : step.toUpperCase(Locale.ROOT).strip();
    return text.isEmpty() ? List.of() : List.of(text.split("\\s+"));
  }

  private static List<String> words(String description) {
    return Arrays.asList(NOT_A_WORD.split(description.toUpperCase(Locale.ROOT).strip()));
  }

  static boolean isLocalizer(String description) {
    return words(description).stream().anyMatch(LOCALIZER::contains);
  }

  /**
   * The series in reading order: those fitting each step in turn, then the rest as given with
   * localizers last.
   */
  static <T> List<T> ordered(List<T> series, Function<T, String> description, List<String> order) {
    List<T> result = new ArrayList<>();
    for (String step : order) {
      for (T item : series) {
        if (!result.contains(item) && Kind.of(description.apply(item)).fits(step)) {
          result.add(item);
        }
      }
    }
    List<T> rest = series.stream().filter(item -> !result.contains(item)).toList();
    rest.stream().filter(item -> !isLocalizer(description.apply(item))).forEach(result::add);
    rest.stream().filter(item -> isLocalizer(description.apply(item))).forEach(result::add);
    return result;
  }

  /**
   * The series after {@code current} in {@code ordered}, or before it going backwards, skipping
   * those in {@code skip}. A series not in the list starts from the first one, or the last.
   */
  static <T> Optional<T> step(List<T> ordered, T current, Set<T> skip, boolean backwards) {
    int direction = backwards ? -1 : 1;
    // An empty viewport has no current series.
    int index = current == null ? -1 : ordered.indexOf(current);
    if (index < 0) index = backwards ? ordered.size() : -1;
    for (int i = index + direction; i >= 0 && i < ordered.size(); i += direction) {
      if (!skip.contains(ordered.get(i))) return Optional.of(ordered.get(i));
    }
    return Optional.empty();
  }

  private boolean dispatch(KeyEvent event) {
    if (event.getID() != KeyEvent.KEY_PRESSED || event.isConsumed() || isEditingText()) {
      return false;
    }
    ShortcutManager shortcuts = ShortcutManager.getInstance();
    int keyCode = shortcutKeyCode(event);
    int modifiers = event.getModifiers();
    boolean nextStudy =
        shortcuts.matches(ShortcutManager.ID_REPORT_COMPOSER_NEXT_STUDY, keyCode, modifiers);
    if (nextStudy
        || shortcuts.matches(
            ShortcutManager.ID_REPORT_COMPOSER_PREVIOUS_STUDY, keyCode, modifiers)) {
      event.consume();
      InteractionRecorder.record("study_key", Map.of("direction", nextStudy ? "next" : "previous"));
      studies.step(studyBeingRead().orElse(null), !nextStudy);
      return true;
    }
    boolean next =
        shortcuts.matches(ShortcutManager.ID_REPORT_COMPOSER_NEXT_SEQUENCE, keyCode, modifiers);
    boolean previous =
        shortcuts.matches(ShortcutManager.ID_REPORT_COMPOSER_PREVIOUS_SEQUENCE, keyCode, modifiers);
    if (!next && !previous) return false;
    Optional<View2d> view = targetView(event);
    if (view.isEmpty()) return false;
    event.consume();
    stepView(view.get(), previous);
    return true;
  }

  // Num Lock changes the key code of keypad digits on some keyboards; the character does not.
  private static int shortcutKeyCode(KeyEvent event) {
    char key = event.getKeyChar();
    return event.getKeyLocation() == KeyEvent.KEY_LOCATION_NUMPAD && key >= '0' && key <= '9'
        ? KeyEvent.VK_NUMPAD0 + (key - '0')
        : event.getKeyCode();
  }

  private static boolean isEditingText() {
    Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    return focusOwner instanceof JTextComponent text && text.isEditable();
  }

  /**
   * Whether a key would type into the focused text box: one without Control, Option or Command.
   * Keys made for a controller carry those, so they work while the reader types a report.
   */
  static boolean typesIntoText(KeyEvent event) {
    int commandKeys =
        InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK | InputEvent.META_DOWN_MASK;
    return isEditingText() && (event.getModifiersEx() & commandKeys) == 0;
  }

  /** The viewport under the mouse, else the one with the keyboard focus. */
  static Optional<View2d> targetView(KeyEvent event) {
    PointerInfo pointer = MouseInfo.getPointerInfo();
    return DicomContextReader.canvasAt(pointer == null ? null : pointer.getLocation())
        .or(
            () ->
                event.getComponent() == null
                    ? Optional.empty()
                    : DicomContextReader.canvasFor(event.getComponent()))
        .filter(canvas -> canvas.getClass() == View2d.class)
        .map(View2d.class::cast);
  }

  private void stepView(View2d view, boolean backwards) {
    ImageViewerPlugin<DicomImageElement> viewer =
        EventManager.getInstance().getSelectedView2dContainer();
    DataExplorerView explorer = GuiUtils.getUICore().getExplorerPlugin(DicomExplorer.NAME);
    if (viewer == null
        || !(explorer instanceof DicomExplorer dicomExplorer)
        || !(explorer.getDataExplorerModel() instanceof DicomModel model)) {
      return;
    }
    List<ViewCanvas<DicomImageElement>> views = viewer.getImagePanels(false);
    MediaSeriesGroup study = studyOf(seriesBeingRead(views)).orElse(null);
    int viewIndex = views.indexOf(view);
    if (study == null || viewIndex < 0) return;

    List<DicomSeries> ordered =
        ordered(
            imageSeries(model, study),
            SequenceStepper::description,
            macros.sequenceOrderFor(examOf(study)));
    Set<DicomSeries> elsewhere = new HashSet<>();
    for (ViewCanvas<DicomImageElement> other : views) {
      if (other != view && other.getSeries() instanceof DicomSeries shown) elsewhere.add(shown);
    }
    DicomSeries current = view.getSeries() instanceof DicomSeries shown ? shown : null;
    InteractionRecorder.recordInView(
        view, "sequence_key", Map.of("direction", backwards ? "previous" : "next"));
    step(ordered, current, elsewhere, backwards)
        .ifPresent(series -> dicomExplorer.displaySeriesInViewSlot(series, null, viewIndex));
  }

  /** The study being read in the selected viewer, if any. */
  static synchronized Optional<MediaSeriesGroup> studyBeingRead() {
    ImageViewerPlugin<DicomImageElement> viewer =
        EventManager.getInstance().getSelectedView2dContainer();
    if (installed == null || viewer == null) return Optional.empty();
    return studyOf(installed.seriesBeingRead(viewer.getImagePanels(false)));
  }

  private static Optional<MediaSeriesGroup> studyOf(
      Optional<MediaSeries<DicomImageElement>> series) {
    return series.map(shown -> InfoLayer.getParent(shown, DicomModel.study));
  }

  // The series a viewport opened last; its study is the one being read in this viewer.
  private Optional<MediaSeries<DicomImageElement>> seriesBeingRead(
      List<ViewCanvas<DicomImageElement>> views) {
    return views.stream()
        .filter(candidate -> candidate instanceof View2d && candidate.getSeries() != null)
        .max(Comparator.comparingLong(candidate -> openedAt.getOrDefault((View2d) candidate, 0L)))
        .map(ViewCanvas::getSeries);
  }

  static Optional<ExamTemplate> examOf(MediaSeriesGroup study) {
    return MriFindingCatalog.inferExam(
        Objects.toString(TagD.getTagValue(study, Tag.StudyDescription), ""));
  }

  /** The study's image series by series number, without key objects, reports and the like. */
  static List<DicomSeries> imageSeries(DicomModel model, MediaSeriesGroup study) {
    Collection<MediaSeriesGroup> children;
    synchronized (model) {
      children = List.copyOf(model.getChildren(study));
    }
    return children.stream()
        .filter(DicomSeries.class::isInstance)
        .map(DicomSeries.class::cast)
        .filter(
            series ->
                series.getMedia(MediaSeries.MEDIA_POSITION.FIRST, null, null)
                    instanceof DicomImageElement)
        .sorted(Comparator.comparingInt(SequenceStepper::seriesNumber))
        .toList();
  }

  private static int seriesNumber(DicomSeries series) {
    Integer number = TagD.getTagValue(series, Tag.SeriesNumber, Integer.class);
    return number == null ? Integer.MAX_VALUE : number;
  }

  static String description(DicomSeries series) {
    Object value = TagD.getTagValue(series, Tag.SeriesDescription);
    return value == null ? "" : value.toString();
  }
}
