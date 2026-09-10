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

/** Original DICOM geometry; pixel spacing is in DICOM row, column order, in millimeters. */
public record ImageGeometry(
    int rows,
    int columns,
    List<Double> imageOrientationPatient,
    List<Double> imagePositionPatient,
    List<Double> pixelSpacing,
    String frameOfReferenceUid) {
  public ImageGeometry {
    rows = Math.max(0, rows);
    columns = Math.max(0, columns);
    imageOrientationPatient = finiteVector(imageOrientationPatient, 6);
    imagePositionPatient = finiteVector(imagePositionPatient, 3);
    pixelSpacing = finiteVector(pixelSpacing, 2);
    frameOfReferenceUid = ComposerText.clean(frameOfReferenceUid);
  }

  public static ImageGeometry empty() {
    return new ImageGeometry(0, 0, List.of(), List.of(), List.of(), "");
  }

  public boolean contains(double column, double row) {
    return column >= 0 && row >= 0 && column < columns && row < rows;
  }

  /** Returns patient LPS millimeters for a zero-based source column/row, or an empty list. */
  public List<Double> patientPosition(double column, double row) {
    if (!Double.isFinite(column)
        || !Double.isFinite(row)
        || imageOrientationPatient.isEmpty()
        || imagePositionPatient.isEmpty()
        || pixelSpacing.isEmpty()
        || pixelSpacing.get(0) <= 0
        || pixelSpacing.get(1) <= 0) {
      return List.of();
    }
    Double[] result = new Double[3];
    for (int axis = 0; axis < 3; axis++) {
      result[axis] =
          imagePositionPatient.get(axis)
              + column * pixelSpacing.get(1) * imageOrientationPatient.get(axis)
              + row * pixelSpacing.get(0) * imageOrientationPatient.get(axis + 3);
    }
    return List.of(result);
  }

  private static List<Double> finiteVector(List<Double> values, int length) {
    return values == null
            || values.size() != length
            || values.stream().anyMatch(value -> value == null || !Double.isFinite(value))
        ? List.of()
        : List.copyOf(values);
  }
}
