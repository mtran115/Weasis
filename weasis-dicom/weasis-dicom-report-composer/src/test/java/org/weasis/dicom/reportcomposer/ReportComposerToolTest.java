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

import java.awt.Rectangle;
import org.junit.jupiter.api.Test;

class ReportComposerToolTest {
  @Test
  void popOutWindowFitsOnASecondaryDisplayWithNegativeCoordinates() {
    Rectangle display = new Rectangle(-1440, 0, 1440, 900);

    Rectangle window = ReportComposerTool.preferredPopOutBounds(display);

    assertEquals(new Rectangle(-1416, 24, 600, 852), window);
    assertTrue(display.contains(window));
  }

  @Test
  void popOutWindowFitsOnASmallDisplay() {
    Rectangle display = new Rectangle(0, 0, 400, 300);

    Rectangle window = ReportComposerTool.preferredPopOutBounds(display);

    assertEquals(new Rectangle(15, 15, 370, 270), window);
    assertTrue(display.contains(window));
  }
}
