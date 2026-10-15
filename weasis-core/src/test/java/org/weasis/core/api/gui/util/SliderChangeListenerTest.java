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

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BoundedRangeModel;
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

  @Test
  void aClickWithoutMovingLeavesTheValueAlone() {
    List<String> fired = new ArrayList<>();
    SliderChangeListener slider = mouseSlider(fired);

    slider.mousePressed(mouseEvent(MouseEvent.MOUSE_PRESSED, 50, InputEvent.BUTTON1_DOWN_MASK));
    slider.mouseDragged(mouseEvent(MouseEvent.MOUSE_DRAGGED, 50, InputEvent.BUTTON1_DOWN_MASK));
    slider.mouseReleased(mouseEvent(MouseEvent.MOUSE_RELEASED, 50, 0));

    assertEquals(List.of(), fired);
    assertEquals(400, slider.getSliderValue());
  }

  @Test
  void aDragAdjustsAndSettlesWhenReleased() {
    List<String> fired = new ArrayList<>();
    SliderChangeListener slider = mouseSlider(fired);

    slider.mousePressed(mouseEvent(MouseEvent.MOUSE_PRESSED, 50, InputEvent.BUTTON1_DOWN_MASK));
    slider.mouseDragged(mouseEvent(MouseEvent.MOUSE_DRAGGED, 60, InputEvent.BUTTON1_DOWN_MASK));
    slider.mouseReleased(mouseEvent(MouseEvent.MOUSE_RELEASED, 60, 0));

    assertEquals(List.of("adjusting 400", "adjusting 410", "settled 410"), fired);
  }

  private static SliderChangeListener mouseSlider(List<String> fired) {
    SliderChangeListener slider =
        new SliderChangeListener(ActionW.WINDOW, 0, 1000, 400, true) {
          @Override
          public void stateChanged(BoundedRangeModel model) {
            fired.add((model.getValueIsAdjusting() ? "adjusting " : "settled ") + model.getValue());
          }
        };
    slider.enableAction(true);
    slider.setButtonMaskEx(InputEvent.BUTTON1_DOWN_MASK);
    return slider;
  }

  private static MouseEvent mouseEvent(int id, int y, int modifiersEx) {
    return new MouseEvent(
        new JPanel(),
        id,
        System.currentTimeMillis(),
        modifiersEx,
        10,
        y,
        1,
        false,
        MouseEvent.BUTTON1);
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
