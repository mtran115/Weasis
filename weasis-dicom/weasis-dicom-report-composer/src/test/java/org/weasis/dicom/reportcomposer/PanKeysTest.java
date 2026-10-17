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

import java.awt.geom.Point2D;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.reportcomposer.PanKeys.Direction;

class PanKeysTest {
  @Test
  void aPressMovesTheImageATenthOfTheViewportTheWayADragWould() {
    // An 800 x 600 viewport at twice the image's size: a tenth is 80 x 60 screen pixels, 40 x 30
    // image pixels. Dragging right lowers the offset, so moving the image right does too.
    assertEquals(new Point2D.Double(-40, 0), PanKeys.offsetChange(Direction.RIGHT, 800, 600, 2));
    assertEquals(new Point2D.Double(40, 0), PanKeys.offsetChange(Direction.LEFT, 800, 600, 2));
    assertEquals(new Point2D.Double(0, 30), PanKeys.offsetChange(Direction.UP, 800, 600, 2));
    assertEquals(new Point2D.Double(0, -30), PanKeys.offsetChange(Direction.DOWN, 800, 600, 2));
  }

  @Test
  void theStepFollowsTheZoomSoAPressLooksTheSameOnScreen() {
    double zoomedOut = PanKeys.offsetChange(Direction.RIGHT, 800, 600, 0.5).getX();
    double zoomedIn = PanKeys.offsetChange(Direction.RIGHT, 800, 600, 4).getX();

    assertEquals(-160, zoomedOut);
    assertEquals(-20, zoomedIn);
  }
}
