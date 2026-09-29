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

import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class NestedWheelScrollingTest {
  @Test
  void aBarCanScrollOnlyAwayFromItsCurrentEnd() {
    JScrollBar bar = new JScrollBar(JScrollBar.VERTICAL, 0, 100, 0, 300);
    assertFalse(NestedWheelScrolling.canScroll(bar, -1));
    assertTrue(NestedWheelScrolling.canScroll(bar, 1));
    bar.setValue(200);
    assertTrue(NestedWheelScrolling.canScroll(bar, -1));
    assertFalse(NestedWheelScrolling.canScroll(bar, 1));
    bar.setVisible(false);
    assertFalse(NestedWheelScrolling.canScroll(bar, -1));
  }

  @Test
  void aNestedBoxWithNothingToScrollPassesTheWheelToThePage() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          List<MouseWheelEvent> received = new ArrayList<>();
          JScrollPane inner = new JScrollPane(new JTextArea("short"));
          inner.getVerticalScrollBar().setVisible(false);
          JPanel page = new JPanel();
          page.add(inner);
          JScrollPane outer =
              new JScrollPane(page) {
                @Override
                protected void processMouseWheelEvent(MouseWheelEvent event) {
                  received.add(event);
                }
              };
          NestedWheelScrolling.install(page);
          MouseWheelEvent wheel =
              new MouseWheelEvent(
                  inner,
                  MouseWheelEvent.MOUSE_WHEEL,
                  0,
                  0,
                  5,
                  5,
                  0,
                  false,
                  MouseWheelEvent.WHEEL_UNIT_SCROLL,
                  3,
                  1);
          for (var listener : inner.getMouseWheelListeners()) listener.mouseWheelMoved(wheel);

          assertEquals(1, received.size());
          assertEquals(outer, received.getFirst().getComponent());
          assertEquals(1, received.getFirst().getWheelRotation());
          assertTrue(wheel.isConsumed());
        });
  }
}
