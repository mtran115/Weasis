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

import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/**
 * Swing delivers the wheel to the innermost scroll pane under the pointer, even when it cannot
 * scroll further, so a text box that slides under the pointer stalls the page around it. This
 * passes the wheel to the enclosing scroll pane whenever the inner one has nothing left to scroll
 * in that direction; otherwise the inner pane scrolls as usual.
 */
final class NestedWheelScrolling implements MouseWheelListener {
  private final JScrollPane inner;

  private NestedWheelScrolling(JScrollPane inner) {
    this.inner = inner;
  }

  /** Installs forwarding on every scroll pane nested inside {@code root}. */
  static void install(Container root) {
    for (Component child : root.getComponents()) {
      if (child instanceof JScrollPane pane
          && SwingUtilities.getAncestorOfClass(JScrollPane.class, pane) != null) {
        pane.addMouseWheelListener(new NestedWheelScrolling(pane));
      }
      if (child instanceof Container container) {
        install(container);
      }
    }
  }

  static boolean canScroll(JScrollBar bar, double rotation) {
    if (bar == null || !bar.isVisible() || rotation == 0) return false;
    return rotation < 0
        ? bar.getValue() > bar.getMinimum()
        : bar.getValue() + bar.getVisibleAmount() < bar.getMaximum();
  }

  @Override
  public void mouseWheelMoved(MouseWheelEvent event) {
    double rotation = event.getPreciseWheelRotation();
    JScrollBar bar =
        event.isShiftDown() ? inner.getHorizontalScrollBar() : inner.getVerticalScrollBar();
    if (canScroll(bar, rotation)) return;
    if (!(SwingUtilities.getAncestorOfClass(JScrollPane.class, inner)
        instanceof JScrollPane outer)) {
      return;
    }
    outer.dispatchEvent(SwingUtilities.convertMouseEvent(inner, event, outer));
    event.consume();
  }
}
