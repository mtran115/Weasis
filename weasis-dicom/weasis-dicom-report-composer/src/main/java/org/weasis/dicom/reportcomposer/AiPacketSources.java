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

import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.lut.PresetWindowLevel;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.display.MrWindowLevelRange;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.ExamTemplate;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageConversion;
import org.weasis.opencv.op.lut.DefaultWlPresentation;
import org.weasis.opencv.op.lut.LutShape;

/** Reads a loaded study into AI packet sources; pixels are decoded later, by each renderer. */
final class AiPacketSources {
  private static final Pattern DICOM_AGE = Pattern.compile("(\\d{1,3})([DWMY])");
  private static final int OLDEST_EXACT_AGE = 89;

  record Collected(AiPacketPlan.Study study, List<AiPacketPlan.SourceSeries> series) {}

  private AiPacketSources() {}

  static Optional<Collected> collect(
      MediaSeries<?> anchor, ExamTemplate template, String examTitle) {
    if (anchor == null || !(anchor.getTagValue(TagW.ExplorerModel) instanceof DicomModel model)) {
      return Optional.empty();
    }
    MediaSeriesGroup study = model.getParent(anchor, DicomModel.study);
    MediaSeriesGroup patient = model.getParent(anchor, DicomModel.patient);
    if (study == null) {
      return Optional.empty();
    }
    List<AiPacketPlan.SourceSeries> series = new ArrayList<>();
    String modality = "";
    for (MediaSeriesGroup child : List.copyOf(model.getChildren(study))) {
      if (child instanceof MediaSeries<?> mediaSeries) {
        Optional<AiPacketPlan.SourceSeries> source = series(mediaSeries);
        source.ifPresent(series::add);
        if (modality.isEmpty() && source.isPresent()) {
          modality = text(Tag.Modality, mediaSeries);
        }
      }
    }
    Object ageTag = value(Tag.PatientAge, study, patient, anchor);
    Object birthDate = value(Tag.PatientBirthDate, patient, study, anchor);
    Object studyDate = value(Tag.StudyDate, study, anchor);
    return Optional.of(
        new Collected(
            new AiPacketPlan.Study(
                template,
                examTitle,
                modality,
                age(ageTag, date(birthDate), date(studyDate)),
                sex(text(Tag.PatientSex, patient, study, anchor))),
            List.copyOf(series)));
  }

  /** Whole years, capped as "90 or older" so the age cannot identify a patient. */
  static String age(Object ageTag, LocalDate birthDate, LocalDate studyDate) {
    if (ageTag instanceof String value) {
      Matcher matcher = DICOM_AGE.matcher(value.strip().toUpperCase(Locale.ROOT));
      if (matcher.matches()) {
        int amount = Integer.parseInt(matcher.group(1));
        return switch (matcher.group(2)) {
          case "Y" -> years(amount);
          case "M" -> amount >= 24 ? years(amount / 12) : amount + " months";
          case "W" -> amount + " weeks";
          default -> amount + " days";
        };
      }
    }
    if (birthDate != null && studyDate != null && !birthDate.isAfter(studyDate)) {
      return years(Period.between(birthDate, studyDate).getYears());
    }
    return "unknown";
  }

  static String sex(String value) {
    return switch (value.strip().toUpperCase(Locale.ROOT)) {
      case "M" -> "male";
      case "F" -> "female";
      default -> "unknown";
    };
  }

  private static String years(int years) {
    return years > OLDEST_EXACT_AGE ? "90 or older" : years + " years";
  }

  private static Optional<AiPacketPlan.SourceSeries> series(MediaSeries<?> series) {
    List<AiPacketPlan.SourceSlice> slices = new ArrayList<>();
    List<?> media = series.copyOfMedias(null, null);
    DicomImageElement first = null;
    for (int index = 0; index < media.size(); index++) {
      if (media.get(index) instanceof DicomImageElement image) {
        first = first == null ? image : first;
        slices.add(
            new AiPacketPlan.SourceSlice(
                SourceDicomMetadata.reference(image, series, index + 1, media.size()),
                () -> render(image)));
      }
    }
    if (first == null) {
      return Optional.empty();
    }
    return Optional.of(
        new AiPacketPlan.SourceSeries(
            series.getSeriesNumber(),
            text(Tag.SeriesDescription, first, series),
            text(Tag.SOPClassUID, first, series),
            text(Tag.ImageType, first, series),
            text(Tag.BurnedInAnnotation, first, series),
            parameters(first, series),
            List.copyOf(slices)));
  }

  private static List<String> parameters(DicomImageElement image, MediaSeries<?> series) {
    List<String> parameters = new ArrayList<>();
    addNumber(parameters, "slice thickness", value(Tag.SliceThickness, image, series), "mm");
    addNumber(
        parameters, "spacing between slices", value(Tag.SpacingBetweenSlices, image, series), "mm");
    addNumber(parameters, "TR", value(Tag.RepetitionTime, image, series), "ms");
    addNumber(parameters, "TE", value(Tag.EchoTime, image, series), "ms");
    addNumber(parameters, "TI", value(Tag.InversionTime, image, series), "ms");
    addNumber(parameters, "flip angle", value(Tag.FlipAngle, image, series), "degrees");
    addNumber(parameters, "field strength", value(Tag.MagneticFieldStrength, image, series), "T");
    String contrast = text(Tag.ContrastBolusAgent, image, series).strip();
    if (!contrast.isEmpty()) {
      parameters.add("contrast agent recorded");
    }
    return List.copyOf(parameters);
  }

  private static void addNumber(List<String> parameters, String name, Object value, String unit) {
    Double number =
        switch (value) {
          case Number n -> n.doubleValue();
          case double[] array when array.length > 0 -> array[0];
          case String s -> parseDouble(s);
          case null, default -> null;
        };
    if (number != null && Double.isFinite(number) && number > 0) {
      String formatted = String.format(Locale.ROOT, "%.2f", number).replaceAll("\\.?0+$", "");
      parameters.add(name + " " + formatted + " " + unit);
    }
  }

  /** Per-slice auto window/level for MR (the numpad + estimate), else the image's default. */
  static BufferedImage render(DicomImageElement image) {
    PlanarImage source = image.getImage();
    if (source == null) {
      throw new IllegalStateException("The image could not be decoded.");
    }
    DefaultWlPresentation presentation = new DefaultWlPresentation(null, true);
    double window;
    double level;
    MrWindowLevelRange range =
        "MR".equalsIgnoreCase(text(Tag.Modality, image)) ? image.getMrAutoWindowLevelRange() : null;
    PresetWindowLevel preset = image.getDefaultPreset(presentation);
    if (range != null && range.max() > range.min()) {
      window = range.max() - range.min();
      level = range.min() + window / 2;
    } else if (preset != null) {
      window = preset.getWindow();
      level = preset.getLevel();
    } else {
      window = image.getMaxValue(presentation) - image.getMinValue(presentation);
      level = image.getMinValue(presentation) + window / 2;
    }
    Map<String, Object> params = new HashMap<>();
    params.put(ActionW.WINDOW.cmd(), window);
    params.put(ActionW.LEVEL.cmd(), level);
    params.put(
        ActionW.LEVEL_MIN.cmd(), Math.min(level - window / 2, image.getMinValue(presentation)));
    params.put(
        ActionW.LEVEL_MAX.cmd(), Math.max(level + window / 2, image.getMaxValue(presentation)));
    params.put(ActionW.LUT_SHAPE.cmd(), LutShape.LINEAR);
    params.put(ActionW.IMAGE_PIX_PADDING.cmd(), Boolean.TRUE);
    BufferedImage rendered =
        ImageConversion.toBufferedImage(image.getRenderedImage(source, params));
    if (rendered == null) {
      throw new IllegalStateException("The image could not be rendered.");
    }
    return rendered;
  }

  private static LocalDate date(Object value) {
    return switch (value) {
      case LocalDate date -> date;
      case String text -> parseDate(text.strip());
      case null, default -> null;
    };
  }

  private static LocalDate parseDate(String text) {
    for (DateTimeFormatter format :
        List.of(DateTimeFormatter.BASIC_ISO_DATE, DateTimeFormatter.ISO_LOCAL_DATE)) {
      try {
        return LocalDate.parse(text, format);
      } catch (DateTimeParseException ignored) {
        // Try the next DICOM date spelling.
      }
    }
    return null;
  }

  private static Double parseDouble(String value) {
    try {
      return Double.parseDouble(value.strip());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static String text(int tag, TagReadable... sources) {
    Object value = value(tag, sources);
    return switch (value) {
      case String[] values -> String.join("\\", values);
      case null -> "";
      default -> value.toString();
    };
  }

  private static Object value(int tag, TagReadable... sources) {
    return Arrays.stream(sources)
        .filter(java.util.Objects::nonNull)
        .map(source -> TagD.getTagValue(source, tag))
        .filter(java.util.Objects::nonNull)
        .findFirst()
        .orElse(null);
  }
}
