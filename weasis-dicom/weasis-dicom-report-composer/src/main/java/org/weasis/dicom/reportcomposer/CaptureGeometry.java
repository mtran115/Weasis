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

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.List;

/**
 * Frozen screenshot-pixel to original DICOM column/row transform (zero-based continuous pixels).
 */
public record CaptureGeometry(
    int screenshotWidth, int screenshotHeight, List<Double> screenshotToSource) {
  public CaptureGeometry {
    screenshotWidth = Math.max(0, screenshotWidth);
    screenshotHeight = Math.max(0, screenshotHeight);
    screenshotToSource = screenshotToSource == null ? List.of() : List.copyOf(screenshotToSource);
    if (!screenshotToSource.isEmpty()
        && (screenshotToSource.size() != 6
            || screenshotToSource.stream().anyMatch(value -> !Double.isFinite(value)))) {
      throw new IllegalArgumentException("A finite six-element affine transform is required.");
    }
  }

  public static CaptureGeometry unavailable(int width, int height) {
    return new CaptureGeometry(width, height, List.of());
  }

  static CaptureGeometry freeze(
      int width,
      int height,
      AffineTransform inverseView,
      Point2D clipOffset,
      double rescaleX,
      double rescaleY) {
    if (width <= 0
        || height <= 0
        || inverseView == null
        || clipOffset == null
        || !Double.isFinite(rescaleX)
        || !Double.isFinite(rescaleY)
        || rescaleX <= 0
        || rescaleY <= 0) {
      return unavailable(width, height);
    }
    AffineTransform transform = AffineTransform.getScaleInstance(1.0 / rescaleX, 1.0 / rescaleY);
    transform.concatenate(inverseView);
    transform.translate(-clipOffset.getX(), -clipOffset.getY());
    double[] matrix = new double[6];
    transform.getMatrix(matrix);
    if (Arrays.stream(matrix).anyMatch(value -> !Double.isFinite(value))) {
      return unavailable(width, height);
    }
    return new CaptureGeometry(width, height, Arrays.stream(matrix).boxed().toList());
  }

  public List<SourceArrowPlacement> sourceArrows(List<ArrowPlacement> arrows, ImageGeometry image) {
    if (screenshotToSource.isEmpty() || screenshotWidth <= 0 || screenshotHeight <= 0) {
      return List.of();
    }
    AffineTransform transform =
        new AffineTransform(screenshotToSource.stream().mapToDouble(Double::doubleValue).toArray());
    return arrows.stream()
        .map(
            arrow -> {
              Point2D tail =
                  transform.transform(
                      new Point2D.Double(
                          arrow.tailX() * screenshotWidth, arrow.tailY() * screenshotHeight),
                      null);
              Point2D tip =
                  transform.transform(
                      new Point2D.Double(
                          arrow.tipX() * screenshotWidth, arrow.tipY() * screenshotHeight),
                      null);
              return new SourceArrowPlacement(
                  tail.getX(),
                  tail.getY(),
                  tip.getX(),
                  tip.getY(),
                  image.contains(tip.getX(), tip.getY()));
            })
        .toList();
  }
}
