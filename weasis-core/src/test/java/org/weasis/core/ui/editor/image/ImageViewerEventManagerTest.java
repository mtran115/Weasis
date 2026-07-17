/*
 * Copyright (c) 2009-2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.geom.Point2D;
import org.junit.jupiter.api.Test;

class ImageViewerEventManagerTest {

  @Test
  void calculatesPanNeededToRestoreCursorAnchor() {
    Point2D adjustment =
        ImageViewerEventManager.calculateZoomAnchorPanAdjustment(
            new Point2D.Double(300.0, 200.0), new Point2D.Double(330.0, 185.0), 2.0);

    assertEquals(15.0, adjustment.getX(), 0.000001);
    assertEquals(-7.5, adjustment.getY(), 0.000001);
  }

  @Test
  void skipsCursorAnchoringWithoutAValidViewportScale() {
    Point2D cursor = new Point2D.Double(300.0, 200.0);
    Point2D anchor = new Point2D.Double(330.0, 185.0);

    assertNull(ImageViewerEventManager.calculateZoomAnchorPanAdjustment(null, anchor, 2.0));
    assertNull(ImageViewerEventManager.calculateZoomAnchorPanAdjustment(cursor, null, 2.0));
    assertNull(ImageViewerEventManager.calculateZoomAnchorPanAdjustment(cursor, anchor, 0.0));
    assertNull(
        ImageViewerEventManager.calculateZoomAnchorPanAdjustment(cursor, anchor, Double.NaN));
  }
}
