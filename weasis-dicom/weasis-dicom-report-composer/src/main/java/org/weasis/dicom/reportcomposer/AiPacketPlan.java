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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.weasis.dicom.reportcomposer.AiPacketGeometry.Plane;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.ExamTemplate;

/**
 * Which series and slices an AI packet contains, in what order, and how each image is labelled.
 * Renders no pixels; each slice carries a renderer the exporter calls off the Swing thread.
 */
record AiPacketPlan(
    Study study,
    List<Series> series,
    List<Localizer> localizers,
    List<Exclusion> excluded,
    String levelSource,
    List<PlacedMark> marks,
    List<Integer> unplacedMarks) {

  private static final String SECONDARY_CAPTURE = "1.2.840.10008.5.1.4.1.1.7";
  private static final Pattern LOCALIZER_WORDS =
      Pattern.compile(
          "\\b(loc|locali[sz]er|scout|survey|3[- ]?pl(ane)?|tri[- ]?pilot|calibration|smartexam)\\b",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern T2 = Pattern.compile("\\bt2", Pattern.CASE_INSENSITIVE);
  private static final double DISC_HINT_MM = 20;

  record SourceSlice(ImageReference reference, Supplier<BufferedImage> renderer) {}

  record SourceSeries(
      String seriesNumber,
      String description,
      String sopClassUid,
      String imageType,
      String burnedInAnnotation,
      List<String> parameters,
      List<SourceSlice> slices) {}

  /** Only what the model needs; never names, IDs, dates, or the institution. */
  record Study(ExamTemplate template, String examTitle, String modality, String age, String sex) {}

  record Disc(String level, List<Double> lps) {}

  /** Disc landmarks from the lumbar level map; confirmed when the radiologist checked them. */
  record DiscLevels(String frameOfReferenceUid, List<Disc> discs, boolean confirmed) {}

  record Slice(
      String id,
      int number,
      ImageReference reference,
      Supplier<BufferedImage> renderer,
      String label) {}

  record Series(
      String id,
      String seriesNumber,
      String description,
      Plane plane,
      List<Slice> slices,
      String summary) {}

  /** A numbered line per axial slice, in source pixels of the localizer slice. */
  record Line(int number, double[] coordinates) {}

  record LevelMark(String level, double column, double row) {}

  record Localizer(
      String id,
      Series axial,
      Slice slice,
      List<Line> lines,
      List<LevelMark> levels,
      String label) {}

  record Exclusion(String series, String reason) {}

  /** A ring the radiologist drew, on the packet slice it belongs to, in source pixels. */
  record PlacedMark(int number, Slice slice, double column, double row) {}

  static AiPacketPlan build(
      Study study, List<SourceSeries> sources, Optional<DiscLevels> discLevels) {
    List<Exclusion> excluded = new ArrayList<>();
    List<Accepted> accepted = new ArrayList<>();
    for (SourceSeries source : sortedBySeriesNumber(sources)) {
      Optional<String> reason = exclusionReason(source);
      if (reason.isPresent()) {
        excluded.add(new Exclusion(seriesName(source), reason.get()));
      } else {
        Plane plane =
            AiPacketGeometry.plane(source.slices().getFirst().reference().geometry()).orElseThrow();
        accepted.add(new Accepted(source, plane));
      }
    }

    List<Series> series = new ArrayList<>();
    for (int index = 0; index < accepted.size(); index++) {
      series.add(series("S" + (index + 1), accepted.get(index), discLevels));
    }
    List<Localizer> localizers = localizers(series, discLevels);
    String levelSource =
        discLevels
            .map(
                d -> d.confirmed() ? "confirmed by the radiologist" : "model estimate, unconfirmed")
            .orElse("none");
    return new AiPacketPlan(study, series, localizers, excluded, levelSource, List.of(), List.of());
  }

  /** Places rings on their slices; rings on images left out of the packet are listed apart. */
  AiPacketPlan withMarks(List<AiMarks.Mark> rings) {
    List<PlacedMark> placed = new ArrayList<>();
    List<Integer> unplaced = new ArrayList<>();
    for (AiMarks.Mark ring : rings) {
      series.stream()
          .flatMap(s -> s.slices().stream())
          .filter(
              slice ->
                  slice.reference().sopInstanceUid().equals(ring.sopInstanceUid())
                      && java.util.Objects.equals(
                          slice.reference().sourceFrameIndex(), ring.frame()))
          .findFirst()
          .ifPresentOrElse(
              slice -> placed.add(new PlacedMark(ring.number(), slice, ring.column(), ring.row())),
              () -> unplaced.add(ring.number()));
    }
    return new AiPacketPlan(
        study,
        series,
        localizers,
        excluded,
        levelSource,
        List.copyOf(placed),
        List.copyOf(unplaced));
  }

  int imageCount() {
    return series.stream().mapToInt(s -> s.slices().size()).sum() + localizers.size();
  }

  private record Accepted(SourceSeries source, Plane plane) {}

  private static Optional<String> exclusionReason(SourceSeries source) {
    String imageType = text(source.imageType()).toUpperCase(Locale.ROOT);
    if (source.slices().isEmpty()) {
      return Optional.of("no images");
    }
    if (text(source.sopClassUid()).startsWith(SECONDARY_CAPTURE)) {
      return Optional.of("secondary capture, which can carry burned-in text");
    }
    if ("YES".equalsIgnoreCase(text(source.burnedInAnnotation()))) {
      return Optional.of("marked as having burned-in annotation");
    }
    if (imageType.contains("SCREEN")) {
      return Optional.of("screen capture");
    }
    if (imageType.contains("LOCALIZER")
        || LOCALIZER_WORDS.matcher(text(source.description())).find()) {
      return Optional.of("localizer or scout");
    }
    List<ImageGeometry> geometries =
        source.slices().stream().map(slice -> slice.reference().geometry()).toList();
    Plane plane = AiPacketGeometry.plane(geometries.getFirst()).orElseThrow();
    if (geometries.stream().anyMatch(g -> AiPacketGeometry.plane(g).orElseThrow() != plane)) {
      return Optional.of("slices in different planes, like a localizer");
    }
    if (source.slices().size() < 3) {
      return Optional.of("fewer than 3 images");
    }
    return Optional.empty();
  }

  private static Series series(String id, Accepted accepted, Optional<DiscLevels> discLevels) {
    SourceSeries source = accepted.source();
    Plane plane = accepted.plane();
    List<SourceSlice> ordered =
        source.slices().stream()
            .sorted(
                Comparator.comparingDouble(
                    (SourceSlice s) -> AiPacketGeometry.stackKey(s.reference().geometry(), plane)))
            .toList();
    int width = Math.max(2, Integer.toString(ordered.size()).length());
    String name =
        plane.label()
            + " "
            + quotedDescription(source)
            + "(series "
            + text(source.seriesNumber())
            + ")";
    List<Slice> slices = new ArrayList<>();
    for (int index = 0; index < ordered.size(); index++) {
      SourceSlice slice = ordered.get(index);
      ImageGeometry geometry = slice.reference().geometry();
      String sliceId = id + "-" + String.format(Locale.ROOT, "%0" + width + "d", index + 1);
      String label =
          "["
              + sliceId
              + "] "
              + name
              + ", slice "
              + (index + 1)
              + " of "
              + ordered.size()
              + ", "
              + AiPacketGeometry.positionText(geometry, plane)
              + (plane == Plane.AXIAL ? discHint(geometry, discLevels) : "");
      slices.add(new Slice(sliceId, index + 1, slice.reference(), slice.renderer(), label));
    }
    ImageGeometry first = ordered.getFirst().reference().geometry();
    List<String> details = new ArrayList<>();
    details.add(ordered.size() + " slices ordered " + plane.stackOrder());
    details.add(AiPacketGeometry.orientationText(first));
    details.addAll(source.parameters());
    details.add(
        String.format(
            Locale.ROOT,
            "pixel spacing %.2f x %.2f mm, matrix %d x %d",
            first.pixelSpacing().get(0),
            first.pixelSpacing().get(1),
            first.columns(),
            first.rows()));
    String summary = id + " = " + name + ": " + String.join("; ", details);
    return new Series(
        id, text(source.seriesNumber()), text(source.description()), plane, slices, summary);
  }

  private static String discHint(ImageGeometry geometry, Optional<DiscLevels> discLevels) {
    if (discLevels.isEmpty()
        || !discLevels.get().frameOfReferenceUid().equals(geometry.frameOfReferenceUid())) {
      return "";
    }
    Disc nearest = null;
    double distance = Double.POSITIVE_INFINITY;
    for (Disc disc : discLevels.get().discs()) {
      double d = LumbarLevelMap.planeDistance(geometry, disc.lps());
      if (d < distance) {
        distance = d;
        nearest = disc;
      }
    }
    if (nearest == null || distance > DISC_HINT_MM) {
      return "";
    }
    return String.format(
        Locale.ROOT, ", nearest disc %s (%.0f mm away)", nearest.level(), distance);
  }

  private static List<Localizer> localizers(List<Series> series, Optional<DiscLevels> discLevels) {
    Optional<Series> sagittal =
        series.stream()
            .filter(s -> s.plane() == Plane.SAGITTAL)
            .max(
                Comparator.comparing((Series s) -> T2.matcher(s.description()).find())
                    .thenComparingInt(s -> s.slices().size()));
    if (sagittal.isEmpty()) {
      return List.of();
    }
    Slice middle = sagittal.get().slices().get(sagittal.get().slices().size() / 2);
    ImageGeometry target = middle.reference().geometry();
    List<LevelMark> levels = levelMarks(target, discLevels);
    List<Localizer> localizers = new ArrayList<>();
    for (Series axial : series.stream().filter(s -> s.plane() == Plane.AXIAL).toList()) {
      List<Line> lines = new ArrayList<>();
      for (Slice slice : axial.slices()) {
        if (target
            .frameOfReferenceUid()
            .equals(slice.reference().geometry().frameOfReferenceUid())) {
          AiPacketGeometry.crossLine(target, slice.reference().geometry())
              .ifPresent(coordinates -> lines.add(new Line(slice.number(), coordinates)));
        }
      }
      if (lines.isEmpty()) {
        continue;
      }
      String id = "LOC-" + axial.id();
      String label =
          "["
              + id
              + "] Localizer for "
              + axial.id()
              + ": sagittal slice "
              + middle.id()
              + " with a numbered line where each "
              + axial.id()
              + " slice cuts it (the number is the "
              + axial.id()
              + " slice number)"
              + (levels.isEmpty() ? "" : "; disc levels are marked from the lumbar level map");
      localizers.add(new Localizer(id, axial, middle, lines, levels, label));
    }
    return localizers;
  }

  private static List<LevelMark> levelMarks(ImageGeometry target, Optional<DiscLevels> discLevels) {
    if (discLevels.isEmpty()
        || !discLevels.get().frameOfReferenceUid().equals(target.frameOfReferenceUid())) {
      return List.of();
    }
    List<LevelMark> marks = new ArrayList<>();
    for (Disc disc : discLevels.get().discs()) {
      var pixel = LumbarLevelMap.pixel(target, disc.lps());
      if (pixel != null && target.contains(pixel.getX(), pixel.getY())) {
        marks.add(new LevelMark(disc.level(), pixel.getX(), pixel.getY()));
      }
    }
    return marks;
  }

  private static List<SourceSeries> sortedBySeriesNumber(List<SourceSeries> sources) {
    return sources.stream()
        .sorted(
            Comparator.comparingLong((SourceSeries s) -> seriesNumber(s.seriesNumber()))
                .thenComparing(s -> text(s.description())))
        .toList();
  }

  private static long seriesNumber(String value) {
    try {
      return Long.parseLong(text(value).strip());
    } catch (NumberFormatException e) {
      return Long.MAX_VALUE;
    }
  }

  private static String seriesName(SourceSeries source) {
    return "Series " + text(source.seriesNumber()) + " " + quotedDescription(source).strip();
  }

  private static String quotedDescription(SourceSeries source) {
    String description = text(source.description()).strip();
    return description.isEmpty() ? "" : "\"" + description + "\" ";
  }

  private static String text(String value) {
    return value == null ? "" : value;
  }
}
