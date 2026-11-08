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
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import javax.swing.SwingUtilities;
import org.weasis.core.api.gui.util.ShortcutManager;
import org.weasis.core.ui.editor.image.DefaultView2d;

/** A form section pinned from the viewport HUD; its keys apply while the pointer is on an image. */
final class HudPin<T> {
  private T target;
  private Component pointer;
  private Runnable listener = () -> {};

  T target() {
    return target;
  }

  /** Returns whether the pinned section changed. */
  boolean set(T next) {
    if (target == next) return false;
    target = next;
    return true;
  }

  void setListener(Runnable listener) {
    this.listener = listener;
  }

  void notifyListener() {
    listener.run();
  }

  void observe(MouseEvent mouse) {
    if (mouse.getID() != MouseEvent.MOUSE_EXITED) pointer = mouse.getComponent();
  }

  /** The pinned section while the pointer is over an image viewport, otherwise null. */
  T onImage() {
    return target != null && pointerOnImage() ? target : null;
  }

  /** Whether the key belongs to the image under the pointer rather than the pinned section. */
  boolean yieldsToViewer(KeyEvent event) {
    return isViewerZoom(event) && onImage() != null && !pointerIn(FormHud.class);
  }

  static boolean isViewerZoom(KeyEvent event) {
    ShortcutManager shortcuts = ShortcutManager.getInstance();
    return shortcuts.matches(ShortcutManager.ID_VIEWER_ZOOM_IN, event)
        || shortcuts.matches(ShortcutManager.ID_VIEWER_ZOOM_OUT, event);
  }

  private boolean pointerOnImage() {
    return pointerIn(DefaultView2d.class);
  }

  private boolean pointerIn(Class<? extends Component> type) {
    Component component = pointer;
    if (component == null || !component.isShowing()) return false;
    Component view =
        type.isInstance(component) ? component : SwingUtilities.getAncestorOfClass(type, component);
    PointerInfo info = MouseInfo.getPointerInfo();
    if (view == null || info == null) return false;
    Point point = new Point(info.getLocation());
    SwingUtilities.convertPointFromScreen(point, view);
    return view.contains(point);
  }
}
