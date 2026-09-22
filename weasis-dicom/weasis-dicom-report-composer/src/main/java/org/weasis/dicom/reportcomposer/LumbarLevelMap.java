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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.awt.geom.Point2D;
import java.util.HashSet;
import java.util.List;

/** A model proposal is never a report finding or a reader-confirmed training label. */
@JsonIgnoreProperties(ignoreUnknown = true)
record LumbarLevelMap(
    int schemaVersion,
    String studyKey,
    String studyInstanceUid,
    String proposalId,
    String modelId,
    String frameOfReferenceUid,
    ImageReference reference,
    List<Landmark> points,
    List<String> reviewReasons,
    String previewPath,
    String status,
    List<Landmark> modelPoints,
    List<Integer> modelVertebraLabels,
    boolean numberingUncertain) {
  static final String UNASSIGNED = "Unassigned";
  static final int MAX_POINTS = 10;
  // T13 exists only in the raw model taxonomy/legacy audit records, never in review choices.
  private static final List<String> RAW_ONLY_LEVELS = List.of("T12-T13", "T13-L1");
  static final List<String> LEVELS =
      List.of("T11-T12", "T12-L1", "L1-L2", "L2-L3", "L3-L4", "L4-L5", "L5-S1", "L5-L6", "L6-S1");

  LumbarLevelMap(
      int schemaVersion,
      String studyKey,
      String studyInstanceUid,
      String proposalId,
      String modelId,
      String frameOfReferenceUid,
      ImageReference reference,
      List<Landmark> points,
      List<String> reviewReasons,
      String previewPath,
      String status) {
    this(
        schemaVersion,
        studyKey,
        studyInstanceUid,
        proposalId,
        modelId,
        frameOfReferenceUid,
        reference,
        points,
        reviewReasons,
        previewPath,
        status,
        null,
        List.of(),
        false);
  }

  record Landmark(String id, String level, List<Double> lps) {
    Landmark {
      if (id == null
          || id.isBlank()
          || !(LEVELS.contains(level)
              || RAW_ONLY_LEVELS.contains(level)
              || UNASSIGNED.equals(level))
          || lps == null
          || lps.size() != 3
          || lps.stream().anyMatch(x -> x == null || !Double.isFinite(x))) {
        throw new IllegalArgumentException("Invalid level landmark");
      }
      lps = List.copyOf(lps);
    }

    String displayLabel(int index) {
      return LEVELS.contains(level) ? level : "Disc " + (index + 1) + " ?";
    }
  }

  LumbarLevelMap {
    if ((schemaVersion != 1 && schemaVersion != 2)
        || studyKey == null
        || studyKey.isBlank()
        || studyInstanceUid == null
        || studyInstanceUid.isBlank()
        || proposalId == null
        || !proposalId.matches("[a-f0-9]{64}")
        || modelId == null
        || modelId.isBlank()
        || frameOfReferenceUid == null
        || frameOfReferenceUid.isBlank()
        || reference == null
        || !frameOfReferenceUid.equals(reference.geometry().frameOfReferenceUid())) {
      throw new IllegalArgumentException("Invalid level map");
    }
    modelPoints = modelPoints == null ? List.copyOf(points) : List.copyOf(modelPoints);
    modelVertebraLabels =
        modelVertebraLabels == null ? List.of() : List.copyOf(modelVertebraLabels);
    validateGeometry(modelPoints, reference.geometry());
    validateGeometry(points, reference.geometry());
    numberingUncertain |=
        modelPoints.stream()
                .anyMatch(
                    p ->
                        RAW_ONLY_LEVELS.contains(p.level())
                            || p.level().contains("L6")
                            || UNASSIGNED.equals(p.level()))
            || modelVertebraLabels.contains(25)
            || modelVertebraLabels.contains(28)
            || points.stream().anyMatch(p -> UNASSIGNED.equals(p.level()));
    if (numberingUncertain) {
      points = points.stream().map(p -> new Landmark(p.id(), UNASSIGNED, p.lps())).toList();
    } else {
      validatePoints(points, reference.geometry());
    }
    points = List.copyOf(points);
    reviewReasons = reviewReasons == null ? List.of() : List.copyOf(reviewReasons);
  }

  static void validatePoints(List<Landmark> points, ImageGeometry geometry) {
    validateGeometry(points, geometry);
    var names = new HashSet<String>();
    int previous = -1;
    for (Landmark point : points) {
      int order = LEVELS.indexOf(point.level());
      if (order < 0)
        throw new IllegalArgumentException("Assign every disc a level before confirming the map.");
      if (!names.add(point.level()) || order <= previous) {
        throw new IllegalArgumentException(
            "Check disc order, duplicate labels, and image positions");
      }
      previous = order;
    }
    if (names.contains("L5-S1") && (names.contains("L5-L6") || names.contains("L6-S1"))) {
      throw new IllegalArgumentException("Choose one numbering convention");
    }
  }

  static void validateGeometry(List<Landmark> points, ImageGeometry geometry) {
    if (points == null || points.size() < 2 || points.size() > MAX_POINTS) {
      throw new IllegalArgumentException("A map needs 2–10 disc landmarks");
    }
    var ids = new HashSet<String>();
    double previousZ = Double.POSITIVE_INFINITY;
    for (Landmark point : points) {
      Point2D pixel = pixel(geometry, point.lps());
      if (!ids.add(point.id())
          || point.lps().get(2) >= previousZ
          || pixel == null
          || !geometry.contains(pixel.getX(), pixel.getY())) {
        throw new IllegalArgumentException("Check disc order and image positions");
      }
      previousZ = point.lps().get(2);
    }
  }

  static Point2D pixel(ImageGeometry geometry, List<Double> lps) {
    if (!validGeometry(geometry) || lps == null || lps.size() != 3) return null;
    double column = 0, row = 0;
    for (int a = 0; a < 3; a++) {
      double delta = lps.get(a) - geometry.imagePositionPatient().get(a);
      column += delta * geometry.imageOrientationPatient().get(a);
      row += delta * geometry.imageOrientationPatient().get(a + 3);
    }
    return new Point2D.Double(
        column / geometry.pixelSpacing().get(1), row / geometry.pixelSpacing().get(0));
  }

  static boolean validGeometry(ImageGeometry g) {
    if (g == null
        || g.rows() <= 0
        || g.columns() <= 0
        || g.imageOrientationPatient().size() != 6
        || g.imagePositionPatient().size() != 3
        || g.pixelSpacing().size() != 2
        || g.pixelSpacing().stream().anyMatch(x -> x <= 0)) return false;
    double a = 0, b = 0, dot = 0;
    for (int i = 0; i < 3; i++) {
      double x = g.imageOrientationPatient().get(i), y = g.imageOrientationPatient().get(i + 3);
      a += x * x;
      b += y * y;
      dot += x * y;
    }
    return Math.abs(a - 1) < .01 && Math.abs(b - 1) < .01 && Math.abs(dot) < .01;
  }

  static double[] normal(ImageGeometry g) {
    var v = g.imageOrientationPatient();
    return new double[] {
      v.get(1) * v.get(5) - v.get(2) * v.get(4),
      v.get(2) * v.get(3) - v.get(0) * v.get(5),
      v.get(0) * v.get(4) - v.get(1) * v.get(3)
    };
  }

  static double planeDistance(ImageGeometry geometry, List<Double> lps) {
    if (!validGeometry(geometry)) return Double.POSITIVE_INFINITY;
    double[] n = normal(geometry);
    double result = 0;
    for (int a = 0; a < 3; a++)
      result += (lps.get(a) - geometry.imagePositionPatient().get(a)) * n[a];
    return Math.abs(result);
  }
}
