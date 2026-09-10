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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.List;
import org.junit.jupiter.api.Test;

class CaptureGeometryTest {
  @Test
  void recoversSourceCoordinatesAfterZoomPanRotationFlipAndNonsquarePixelDisplay()
      throws Exception {
    AffineTransform imageToView = new AffineTransform();
    imageToView.translate(29, 17);
    imageToView.rotate(Math.PI / 2);
    imageToView.scale(-2, 2);
    Point2D offset = new Point2D.Double(500, 500);
    Point2D tail = screenPoint(imageToView, offset, 70, 60);
    Point2D tip = screenPoint(imageToView, offset, 100, 70);
    AffineTransform inverse = imageToView.createInverse();
    CaptureGeometry frozen = CaptureGeometry.freeze(2000, 1600, inverse, offset, 2, 1);

    // Simulate the viewport being scrolled/resized while the modal annotation editor is open.
    inverse.setToIdentity();
    offset.setLocation(0, 0);
    ArrowPlacement arrow =
        new ArrowPlacement(
            tail.getX() / 2000, tail.getY() / 1600, tip.getX() / 2000, tip.getY() / 1600);
    SourceArrowPlacement source = frozen.sourceArrows(List.of(arrow), geometry()).getFirst();

    assertEquals(70, source.tailX(), 1e-9);
    assertEquals(60, source.tailY(), 1e-9);
    assertEquals(100, source.tipX(), 1e-9);
    assertEquals(70, source.tipY(), 1e-9);
    assertTrue(source.tipInsideImage());
  }

  @Test
  void marksArrowsOutsideTheOriginalImageInsteadOfClampingThemOntoAnEdge() {
    CaptureGeometry frozen =
        CaptureGeometry.freeze(1024, 1024, new AffineTransform(), new Point2D.Double(), 1, 1);
    SourceArrowPlacement arrow =
        frozen.sourceArrows(List.of(new ArrowPlacement(0.1, 0.1, 0.9, 0.9)), geometry()).getFirst();

    assertEquals(921.6, arrow.tipX(), 1e-9);
    assertFalse(arrow.tipInsideImage());
  }

  @Test
  void unavailableTransformDoesNotFabricateSourceCoordinates() {
    assertTrue(
        CaptureGeometry.unavailable(1024, 1024)
            .sourceArrows(List.of(new ArrowPlacement(0.1, 0.1, 0.2, 0.2)), geometry())
            .isEmpty());
    assertTrue(
        CaptureGeometry.freeze(1024, 1024, new AffineTransform(), new Point2D.Double(), 0, 1)
            .screenshotToSource()
            .isEmpty());
  }

  private static Point2D screenPoint(
      AffineTransform transform, Point2D offset, double x, double y) {
    Point2D point = transform.transform(new Point2D.Double(x * 2, y), null);
    return new Point2D.Double(point.getX() + offset.getX(), point.getY() + offset.getY());
  }

  private static ImageGeometry geometry() {
    return new ImageGeometry(256, 512, List.of(), List.of(), List.of(), "");
  }
}
