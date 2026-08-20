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
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JDialog;
import javax.swing.JOptionPane;

final class ComposerDialogSupport {
  private ComposerDialogSupport() {}

  static void showMessage(Component parent, Object message, String title, int messageType) {
    JOptionPane optionPane = new JOptionPane(message, messageType);
    JDialog dialog = optionPane.createDialog(parent, title);
    keepInFront(dialog);
    dialog.setVisible(true);
    dialog.dispose();
  }

  static void keepInFront(Window window) {
    window.setAutoRequestFocus(true);
    window.addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowOpened(WindowEvent event) {
            window.toFront();
            window.requestFocus();
          }
        });
  }
}
