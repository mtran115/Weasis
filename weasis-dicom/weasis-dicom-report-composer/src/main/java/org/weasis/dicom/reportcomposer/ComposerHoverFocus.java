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
import java.awt.event.MouseEvent;
import javax.swing.JComboBox;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/** Leaves composer text entry only when the pointer moves onto finding controls. */
final class ComposerHoverFocus {
  private ComposerHoverFocus() {}

  static boolean isHoverMotion(MouseEvent event) {
    return (event.getID() == MouseEvent.MOUSE_ENTERED || event.getID() == MouseEvent.MOUSE_MOVED)
        && event.getModifiersEx() == 0;
  }

  static void activate(Component form, Component textRoot, Component pointer, Component focus) {
    if (!(focus instanceof JTextComponent)
        || !SwingUtilities.isDescendingFrom(focus, textRoot)
        || !SwingUtilities.isDescendingFrom(pointer, form)) return;

    // Moving within a text editor (including its scrollbars) must keep typing/selection intact.
    for (Component current = pointer;
        current != null && current != form;
        current = current.getParent()) {
      if (current instanceof JTextComponent
          || current instanceof JSpinner
          || (current instanceof JComboBox<?> combo && combo.isEditable())
          || (current instanceof JScrollPane scroll
              && scroll.getViewport().getView() instanceof JTextComponent)) return;
    }
    form.requestFocusInWindow();
  }
}
