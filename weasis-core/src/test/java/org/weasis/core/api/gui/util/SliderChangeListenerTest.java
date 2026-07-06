/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.event.MouseWheelEvent;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

class SliderChangeListenerTest {

  @Test
  void getWheelRotationDirection_clampsAcceleratedWheelRotationToSingleStep() {
    assertEquals(1, SliderChangeListener.getWheelRotationDirection(wheelEvent(7, 7.0)));
    assertEquals(-1, SliderChangeListener.getWheelRotationDirection(wheelEvent(-5, -5.0)));
  }

  @Test
  void getWheelRotationDirection_usesPreciseRotationWhenRoundedRotationIsZero() {
    assertEquals(1, SliderChangeListener.getWheelRotationDirection(wheelEvent(0, 0.35)));
    assertEquals(-1, SliderChangeListener.getWheelRotationDirection(wheelEvent(0, -0.35)));
  }

  @Test
  void getWheelRotationDirection_returnsZeroWhenThereIsNoWheelMovement() {
    assertEquals(0, SliderChangeListener.getWheelRotationDirection(wheelEvent(0, 0.0)));
  }

  private static MouseWheelEvent wheelEvent(int wheelRotation, double preciseWheelRotation) {
    return new MouseWheelEvent(
        new JPanel(),
        MouseWheelEvent.MOUSE_WHEEL,
        System.currentTimeMillis(),
        0,
        0,
        0,
        0,
        0,
        1,
        false,
        MouseWheelEvent.WHEEL_UNIT_SCROLL,
        3,
        wheelRotation,
        preciseWheelRotation);
  }
}
