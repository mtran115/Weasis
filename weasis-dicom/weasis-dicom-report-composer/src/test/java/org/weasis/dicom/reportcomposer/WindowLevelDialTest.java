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

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.reportcomposer.WindowLevelDial.Action;
import org.weasis.dicom.viewer2d.EventManager.WindowLevel;

class WindowLevelDialTest {
  // Window 800 and level 500: the black point is 100 and the white point 900.
  private static final WindowLevel START = new WindowLevel(800, 500);

  private static double black(WindowLevel wl) {
    return wl.level() - wl.window() / 2;
  }

  @Test
  void theWhitePointMovesAShareOfTheWindowAndTheBlackPointStays() {
    WindowLevel up = WindowLevelDial.apply(Action.WHITE_POINT_UP, START);
    assertEquals(840, up.window(), 1e-9);
    assertEquals(100, black(up), 1e-9);

    WindowLevel down = WindowLevelDial.apply(Action.WHITE_POINT_DOWN, START);
    assertEquals(800 / 1.05, down.window(), 1e-9);
    assertEquals(100, black(down), 1e-9);
  }

  @Test
  void upAndDownUndoEachOtherHoweverManyClicks() {
    WindowLevel wl = START;
    for (int i = 0; i < 20; i++) wl = WindowLevelDial.apply(Action.WHITE_POINT_UP, wl);
    for (int i = 0; i < 20; i++) wl = WindowLevelDial.apply(Action.WHITE_POINT_DOWN, wl);
    assertEquals(800, wl.window(), 1e-6);
    assertEquals(500, wl.level(), 1e-6);
    // Lowering never collapses the window.
    for (int i = 0; i < 500; i++) wl = WindowLevelDial.apply(Action.WHITE_POINT_DOWN, wl);
    assertTrue(wl.window() > 0);
  }

  @Test
  void brightnessShiftsBothPointsAndKeepsTheContrast() {
    WindowLevel brighter = WindowLevelDial.apply(Action.BRIGHTER, START);
    assertEquals(800, brighter.window());
    assertEquals(476, brighter.level(), 1e-9);
    assertEquals(524, WindowLevelDial.apply(Action.DARKER, START).level(), 1e-9);
  }

  @Test
  void aTurnStartsAfterAPauseOrInAnotherViewport() {
    Instant first = Instant.parse("2026-10-16T20:00:00Z");
    assertFalse(WindowLevelDial.startsTurn(true, first, first.plusMillis(1500)));
    assertTrue(WindowLevelDial.startsTurn(true, first, first.plusMillis(1501)));
    assertTrue(WindowLevelDial.startsTurn(false, first, first.plusMillis(100)));
    assertTrue(WindowLevelDial.startsTurn(true, Instant.MIN, first));
  }
}
