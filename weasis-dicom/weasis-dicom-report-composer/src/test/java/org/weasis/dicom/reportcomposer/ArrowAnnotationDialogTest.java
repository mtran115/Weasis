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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Point;
import org.junit.jupiter.api.Test;

class ArrowAnnotationDialogTest {
  @Test
  void initialGesturePointBecomesArrowHead() {
    ArrowPlacement placement =
        ArrowAnnotationDialog.placementFromHeadFirstGesture(
            new Point(80, 20), new Point(20, 80), 101, 101);

    assertEquals(0.2, placement.tailX());
    assertEquals(0.8, placement.tailY());
    assertEquals(0.8, placement.tipX());
    assertEquals(0.2, placement.tipY());
  }

  @Test
  void automaticArrowUsesACompactTailInsideTheImage() {
    Point head = new Point(500, 500);
    Point tail = ArrowAnnotationDialog.automaticTailFor(head, 1000, 1000);

    assertTrue(tail.x >= 0 && tail.x < 1000);
    assertTrue(tail.y >= 0 && tail.y < 1000);
    assertTrue(head.distance(tail) < 120.0);
    assertTrue(head.distance(tail) > 30.0);
  }
}
