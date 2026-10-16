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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.swing.Timer;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.ui.recorder.InteractionRecorder;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.ExamTemplate;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.View2d;

/**
 * The reader's viewer macros, from a JSON file they can edit; a change applies to the next series
 * opened or stepped to. "series_open" holds display steps run when a viewport opens a series, such
 * as the zoom and Auto W/L the reader would otherwise set by hand; "sequence_order" holds how each
 * exam is hung by the next-study keys and the order the next-sequence keys follow (see {@link
 * SequenceStepper}). The reader edits the orders in {@link HangingProtocolsDialog}.
 */
final class ViewerMacros {
  private static final Logger LOGGER = LoggerFactory.getLogger(ViewerMacros.class);

  static final String DEFAULTS =
      """
      {
        "series_open": [
          {
            "name": "Every MR series",
            "modality": "MR",
            "auto_window_level": true
          }
        ],
        "sequence_order": [
          {
            "name": "Spine MR",
            "exams": ["CERVICAL_SPINE", "THORACIC_SPINE", "LUMBAR_SPINE"],
            "layout": "1x2",
            "order": ["SAG T2", "COR", "SAG STIR", "SAG T1", "AX T2", "OBL"]
          },
          {
            "name": "Joint MR",
            "exams": ["SHOULDER", "ELBOW", "WRIST", "HAND", "HIP", "KNEE", "ANKLE", "FOOT"],
            "layout": "1x2",
            "order": ["COR STIR", "COR", "SAG STIR", "SAG", "AX STIR", "AX"]
          }
        ]
      }
      """;

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
          .setSerializationInclusion(JsonInclude.Include.NON_NULL);
  // A series waits for its viewport to show an image, e.g. until its viewer tab is opened.
  private static final Duration SHOW_WAIT = Duration.ofMinutes(10);
  private static final int POLL_MILLIS = 100;
  private static ViewerMacros installed;

  /**
   * What to do when a matching series opens; a missing criterion matches any series.
   *
   * @param exam an {@link ExamTemplate} name, inferred from the study description
   * @param series a pattern found in the series description, ignoring case
   * @param zoom a multiple of the zoom that fits the image in the viewport
   */
  record Rule(
      String name,
      String exam,
      String modality,
      String series,
      Double zoom,
      boolean autoWindowLevel) {
    Rule {
      // A broken pattern fails when the file is read rather than when a series opens.
      if (series != null) Pattern.compile(series);
      if (zoom != null && !(zoom > 0.0)) throw new IllegalArgumentException("zoom must be above 0");
    }

    boolean matches(Optional<ExamTemplate> studyExam, String seriesModality, String description) {
      return (exam == null
              || studyExam.filter(template -> template.name().equalsIgnoreCase(exam)).isPresent())
          && (modality == null || modality.equalsIgnoreCase(seriesModality))
          && (series == null
              || Pattern.compile(series, Pattern.CASE_INSENSITIVE).matcher(description).find());
    }
  }

  /**
   * How some exams are hung and stepped through: the viewer layout, and the order sequences fill
   * the viewports and the next-sequence keys follow. Other studies go by series number in 1x2.
   *
   * @param exams the {@link ExamTemplate} names it is for
   * @param layout a viewer layout id such as "1x2"
   * @param order steps such as "SAG T2", "COR" or "AX T2", each a plane and/or a contrast
   */
  record SequenceOrder(String name, List<String> exams, String layout, List<String> order) {
    static final String DEFAULT_LAYOUT = "1x2";
    static final SequenceOrder BY_SERIES_NUMBER = new SequenceOrder("", List.of(), null, List.of());

    SequenceOrder {
      name = name == null ? "" : name.strip();
      exams = exams == null ? List.of() : List.copyOf(exams);
      layout = layout == null || layout.isBlank() ? DEFAULT_LAYOUT : layout.strip();
      order = order == null ? List.of() : List.copyOf(order);
      // A misspelled step fails when the file is read rather than when a key is pressed.
      order.forEach(SequenceStepper::checkStep);
    }

    boolean isFor(ExamTemplate exam) {
      return exams.stream().anyMatch(exam.name()::equalsIgnoreCase);
    }
  }

  record Macros(List<Rule> seriesOpen, List<SequenceOrder> sequenceOrder) {
    static final Macros NONE = new Macros(List.of(), List.of());

    Macros {
      seriesOpen = seriesOpen == null ? List.of() : List.copyOf(seriesOpen);
      sequenceOrder = sequenceOrder == null ? List.of() : List.copyOf(sequenceOrder);
    }
  }

  private final Path file;
  private FileTime loadedVersion;
  private Macros macros = Macros.NONE;

  ViewerMacros(Path file) {
    this.file = file;
  }

  /** Starts the macros once per session, writing the starting file if there is none. */
  static synchronized void install() {
    if (installed != null) return;
    ViewerMacros macros =
        new ViewerMacros(AppProperties.WEASIS_PATH.resolve("data/report-composer/macros.json"));
    installed = macros;
    macros.writeDefaultsIfMissing();
    EventManager.getInstance().addSeriesOpenedListener(macros::seriesOpened);
    SequenceStepper.install(macros);
  }

  /** The session's macros, once installed. */
  static synchronized Optional<ViewerMacros> current() {
    return Optional.ofNullable(installed);
  }

  void writeDefaultsIfMissing() {
    try {
      if (Files.notExists(file)) {
        Files.createDirectories(file.getParent());
        Files.writeString(file, DEFAULTS);
      }
    } catch (IOException e) {
      LOGGER.warn("Cannot write the starting macros to {}", file, e);
    }
  }

  /** The first rule matching the series, reading the file again when it has changed. */
  Optional<Rule> ruleFor(Optional<ExamTemplate> exam, String modality, String seriesDescription) {
    return macros().seriesOpen().stream()
        .filter(rule -> rule.matches(exam, modality, seriesDescription))
        .findFirst();
  }

  /** The first sequence order for the exam, or series-number order in 1x2. */
  SequenceOrder hangingFor(Optional<ExamTemplate> exam) {
    return exam.flatMap(
            template ->
                macros().sequenceOrder().stream()
                    .filter(order -> order.isFor(template))
                    .findFirst())
        .orElse(SequenceOrder.BY_SERIES_NUMBER);
  }

  /** The order the next-sequence keys follow for the exam; empty to go by series number. */
  List<String> sequenceOrderFor(Optional<ExamTemplate> exam) {
    return hangingFor(exam).order();
  }

  List<SequenceOrder> sequenceOrders() {
    return macros().sequenceOrder();
  }

  /** Replaces the sequence orders in the file and keeps its series-open rules. */
  void saveSequenceOrders(List<SequenceOrder> orders) throws IOException {
    Macros updated = new Macros(macros().seriesOpen(), orders);
    Files.createDirectories(file.getParent());
    Path draft = file.resolveSibling(file.getFileName() + ".saving");
    Files.writeString(draft, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(updated));
    Files.move(draft, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
  }

  private Macros macros() {
    FileTime version;
    try {
      version = Files.getLastModifiedTime(file);
    } catch (IOException e) {
      loadedVersion = null;
      macros = Macros.NONE;
      return macros;
    }
    if (!version.equals(loadedVersion)) {
      loadedVersion = version;
      try {
        macros = MAPPER.readValue(Files.readString(file), Macros.class);
      } catch (IOException e) {
        LOGGER.warn("Cannot read {}; the macros read before still apply", file, e);
      }
    }
    return macros;
  }

  private void seriesOpened(View2d view, boolean windowLevelRestored) {
    // MPR, MIP and other derived views keep their own display.
    if (view.getClass() != View2d.class) return;
    MediaSeries<?> series = view.getSeries();
    long deadline = System.nanoTime() + SHOW_WAIT.toNanos();
    Timer timer = new Timer(POLL_MILLIS, null);
    timer.setInitialDelay(0);
    timer.addActionListener(
        event -> {
          if (view.getSeries() != series || System.nanoTime() > deadline) {
            timer.stop();
          } else if (view.isShowing()
              && view.getWidth() > 0
              && view.getHeight() > 0
              && view.getImage() != null) {
            timer.stop();
            ruleFor(view).ifPresent(rule -> apply(view, rule, windowLevelRestored));
          }
        });
    timer.start();
  }

  private Optional<Rule> ruleFor(View2d view) {
    Optional<ExamTemplate> exam =
        DicomContextReader.from(view)
            .flatMap(
                selection -> MriFindingCatalog.inferExam(selection.context().studyDescription()));
    return ruleFor(
        exam, tag(view.getSeries(), Tag.Modality), tag(view.getSeries(), Tag.SeriesDescription));
  }

  private static String tag(MediaSeries<?> series, int tag) {
    Object value = TagD.getTagValue(series, tag);
    return value == null ? "" : value.toString();
  }

  private static void apply(View2d view, Rule rule, boolean windowLevelRestored) {
    EventManager manager = EventManager.getInstance();
    Map<String, Object> settings = new LinkedHashMap<>();
    settings.put("macro", rule.name());
    if (rule.zoom() != null) {
      double scale = view.getBestFitViewScale() * rule.zoom();
      view.zoom(scale);
      if (manager.getSelectedViewPane() == view) {
        manager.getAction(ActionW.ZOOM).ifPresent(action -> action.setRealValue(scale, false));
      }
      settings.put(ActionW.ZOOM.cmd(), scale);
    }
    // A W/L the series had earlier in the session is the reader's own; Auto would replace it.
    if (rule.autoWindowLevel() && !windowLevelRestored) {
      manager.applyAutoWindowLevelOnOpen(view);
      settings.put("auto_window_level", true);
    }
    InteractionRecorder.recordInView(view, "macro", settings);
  }
}
