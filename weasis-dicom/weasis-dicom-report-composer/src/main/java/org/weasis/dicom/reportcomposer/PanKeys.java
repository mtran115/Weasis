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
import java.awt.KeyboardFocusManager;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.util.Optional;
import javax.swing.text.JTextComponent;
import org.weasis.core.api.gui.util.ShortcutManager;
import org.weasis.core.ui.model.utils.bean.PanPoint;
import org.weasis.dicom.viewer2d.View2d;

/**
 * Pans the viewport under the mouse with keys, for a controller's D-pad: each press moves the image
 * a tenth of the viewport in the key's direction, as dragging it with the mouse would, so a press
 * feels the same at any zoom and matrix size.
 */
final class PanKeys {
  /** The share of the viewport each press moves the image. */
  static final double STEP = 0.1;

  private static boolean installed;

  enum Direction {
    LEFT(-1, 0, ShortcutManager.ID_REPORT_COMPOSER_PAN_LEFT),
    RIGHT(1, 0, ShortcutManager.ID_REPORT_COMPOSER_PAN_RIGHT),
    UP(0, -1, ShortcutManager.ID_REPORT_COMPOSER_PAN_UP),
    DOWN(0, 1, ShortcutManager.ID_REPORT_COMPOSER_PAN_DOWN);

    final int dx;
    final int dy;
    final String shortcut;

    Direction(int dx, int dy, String shortcut) {
      this.dx = dx;
      this.dy = dy;
      this.shortcut = shortcut;
    }
  }

  private PanKeys() {}

  static synchronized void install() {
    if (installed) return;
    installed = true;
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(PanKeys::dispatch);
  }

  /**
   * The change of the view's model offset that moves the image {@link #STEP} of a viewport of this
   * size, in screen pixels, the way {@code direction} points. A lower offset moves the image right
   * or down, as when dragging it.
   */
  static Point2D offsetChange(Direction direction, double width, double height, double viewScale) {
    return new Point2D.Double(
        -direction.dx * STEP * width / viewScale, -direction.dy * STEP * height / viewScale);
  }

  private static boolean dispatch(KeyEvent event) {
    if (event.getID() != KeyEvent.KEY_PRESSED || event.isConsumed()) return false;
    ShortcutManager shortcuts = ShortcutManager.getInstance();
    for (Direction direction : Direction.values()) {
      if (!shortcuts.matches(direction.shortcut, event.getKeyCode(), event.getModifiers())) {
        continue;
      }
      Optional<View2d> view = SequenceStepper.targetView(event);
      if (typesIntoText(event) || view.isEmpty()) return false;
      event.consume();
      pan(view.get(), direction);
      return true;
    }
    return false;
  }

  // A pan key rebound to a plain key would also move a text box's caret; leave that to the box.
  private static boolean typesIntoText(KeyEvent event) {
    Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    int commandKeys =
        InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK | InputEvent.META_DOWN_MASK;
    return focusOwner instanceof JTextComponent text
        && text.isEditable()
        && (event.getModifiersEx() & commandKeys) == 0;
  }

  private static void pan(View2d view, Direction direction) {
    double viewScale = view.getViewModel().getViewScale();
    if (!(viewScale > 0)) return;
    Point2D change = offsetChange(direction, view.getWidth(), view.getHeight(), viewScale);
    view.moveOrigin(new PanPoint(PanPoint.State.MOVE, change.getX(), change.getY()));
  }
}
