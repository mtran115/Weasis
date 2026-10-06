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

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Patient-space helpers for labelling and cross-referencing slices, in DICOM LPS millimeters. */
final class AiPacketGeometry {
  private static final double EPSILON = 1e-9;

  /**
   * Planes are named by the patient axis the slice normal points along; stacks run R→L, A→P, S→I.
   */
  enum Plane {
    SAGITTAL("sagittal", 0, 1, "right to left"),
    CORONAL("coronal", 1, 1, "anterior to posterior"),
    AXIAL("axial", 2, -1, "superior to inferior");

    private final String label;
    private final int axis;
    private final int stackDirection;
    private final String stackOrder;

    Plane(String label, int axis, int stackDirection, String stackOrder) {
      this.label = label;
      this.axis = axis;
      this.stackDirection = stackDirection;
      this.stackOrder = stackOrder;
    }

    String label() {
      return label;
    }

    String stackOrder() {
      return stackOrder;
    }
  }

  private AiPacketGeometry() {}

  static Optional<Plane> plane(ImageGeometry geometry) {
    if (!LumbarLevelMap.validGeometry(geometry)) {
      return Optional.empty();
    }
    double[] n = LumbarLevelMap.normal(geometry);
    int axis = 0;
    for (int a = 1; a < 3; a++) {
      if (Math.abs(n[a]) > Math.abs(n[axis])) {
        axis = a;
      }
    }
    int dominant = axis;
    return java.util.Arrays.stream(Plane.values()).filter(p -> p.axis == dominant).findFirst();
  }

  /**
   * Where the image center sits along the plane's patient axis (positive = left, posterior, up).
   * The center, not the plane offset, keeps angled blocks such as disc-parallel axials in order.
   */
  static double axisOffset(ImageGeometry geometry, Plane plane) {
    List<Double> center = geometry.patientPosition(geometry.columns() / 2.0, geometry.rows() / 2.0);
    return center.isEmpty() ? 0 : center.get(plane.axis);
  }

  /** Sort key that runs each stack in its {@link Plane#stackOrder()}. */
  static double stackKey(ImageGeometry geometry, Plane plane) {
    return plane.stackDirection * axisOffset(geometry, plane);
  }

  /** Scanner-style slice position such as "L 4.0 mm" or "S 12.5 mm". */
  static String positionText(ImageGeometry geometry, Plane plane) {
    double offset = axisOffset(geometry, plane);
    String side =
        switch (plane) {
          case SAGITTAL -> offset < 0 ? "R" : "L";
          case CORONAL -> offset < 0 ? "A" : "P";
          case AXIAL -> offset < 0 ? "I" : "S";
        };
    return String.format(Locale.ROOT, "%s %.1f mm", side, Math.abs(offset));
  }

  /** Which patient directions the image's right and down edges face, e.g. "posterior, inferior". */
  static String orientationText(ImageGeometry geometry) {
    List<Double> iop = geometry.imageOrientationPatient();
    return "image right = "
        + direction(iop.get(0), iop.get(1), iop.get(2))
        + ", image down = "
        + direction(iop.get(3), iop.get(4), iop.get(5));
  }

  /**
   * Where {@code slice} cuts {@code target}, as {column1, row1, column2, row2} in target pixels,
   * limited to the part of the line that lies inside both images.
   */
  static Optional<double[]> crossLine(ImageGeometry target, ImageGeometry slice) {
    if (!LumbarLevelMap.validGeometry(target) || !LumbarLevelMap.validGeometry(slice)) {
      return Optional.empty();
    }
    double[] nt = LumbarLevelMap.normal(target);
    double[] ns = LumbarLevelMap.normal(slice);
    double[] direction = cross(nt, ns);
    double length = Math.sqrt(dot(direction, direction));
    if (length < 0.05) {
      return Optional.empty();
    }
    for (int a = 0; a < 3; a++) {
      direction[a] /= length;
    }
    double ht = dot(target.imagePositionPatient(), nt);
    double hs = dot(slice.imagePositionPatient(), ns);
    double c = dot(nt, ns);
    double denominator = 1 - c * c;
    double[] origin = new double[3];
    for (int a = 0; a < 3; a++) {
      origin[a] = ((ht - hs * c) * nt[a] + (hs - ht * c) * ns[a]) / denominator;
    }
    double[] range = {Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY};
    if (!clipToImage(slice, origin, direction, range)
        || !clipToImage(target, origin, direction, range)
        || range[1] - range[0] < EPSILON) {
      return Optional.empty();
    }
    double[] start = pixel(target, origin, direction, range[0]);
    double[] end = pixel(target, origin, direction, range[1]);
    return Optional.of(new double[] {start[0], start[1], end[0], end[1]});
  }

  private static boolean clipToImage(
      ImageGeometry image, double[] origin, double[] direction, double[] range) {
    List<Double> iop = image.imageOrientationPatient();
    double[] columnAxis = {iop.get(0), iop.get(1), iop.get(2)};
    double[] rowAxis = {iop.get(3), iop.get(4), iop.get(5)};
    double[] offset = subtract(origin, image.imagePositionPatient());
    double columnSpacing = image.pixelSpacing().get(1);
    double rowSpacing = image.pixelSpacing().get(0);
    return clip(
            dot(offset, columnAxis) / columnSpacing,
            dot(direction, columnAxis) / columnSpacing,
            image.columns(),
            range)
        && clip(
            dot(offset, rowAxis) / rowSpacing,
            dot(direction, rowAxis) / rowSpacing,
            image.rows(),
            range);
  }

  // Narrows range so that start + t * step stays within [0, size].
  private static boolean clip(double start, double step, double size, double[] range) {
    if (Math.abs(step) < EPSILON) {
      return start >= 0 && start <= size;
    }
    double t1 = -start / step;
    double t2 = (size - start) / step;
    range[0] = Math.max(range[0], Math.min(t1, t2));
    range[1] = Math.min(range[1], Math.max(t1, t2));
    return range[0] < range[1];
  }

  private static double[] pixel(
      ImageGeometry image, double[] origin, double[] direction, double t) {
    double[] point = new double[3];
    for (int a = 0; a < 3; a++) {
      point[a] = origin[a] + t * direction[a];
    }
    List<Double> iop = image.imageOrientationPatient();
    double[] offset = subtract(point, image.imagePositionPatient());
    return new double[] {
      (offset[0] * iop.get(0) + offset[1] * iop.get(1) + offset[2] * iop.get(2))
          / image.pixelSpacing().get(1),
      (offset[0] * iop.get(3) + offset[1] * iop.get(4) + offset[2] * iop.get(5))
          / image.pixelSpacing().get(0)
    };
  }

  private static String direction(double x, double y, double z) {
    double ax = Math.abs(x);
    double ay = Math.abs(y);
    double az = Math.abs(z);
    if (ax >= ay && ax >= az) {
      return x < 0 ? "patient right" : "patient left";
    }
    if (ay >= az) {
      return y < 0 ? "anterior" : "posterior";
    }
    return z < 0 ? "inferior" : "superior";
  }

  private static double dot(List<Double> a, double[] b) {
    return a.get(0) * b[0] + a.get(1) * b[1] + a.get(2) * b[2];
  }

  private static double dot(double[] a, double[] b) {
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
  }

  private static double[] cross(double[] a, double[] b) {
    return new double[] {
      a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]
    };
  }

  private static double[] subtract(double[] a, List<Double> b) {
    return new double[] {a[0] - b.get(0), a[1] - b.get(1), a[2] - b.get(2)};
  }
}
