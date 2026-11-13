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

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

/** Layout helpers shared by the structured forms built from labelled rows of buttons. */
final class FormRows {
  private FormRows() {}

  static JPanel rowsPanel(String title) {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(BorderFactory.createTitledBorder(title));
    return panel;
  }

  /** A label line with its controls indented beneath it. */
  static void addRow(JPanel panel, int row, String label, JComponent controls) {
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = row * 2;
    constraints.anchor = GridBagConstraints.LINE_START;
    constraints.insets = new Insets(2, 3, 0, 3);
    panel.add(new JLabel(label), constraints);
    constraints.gridy++;
    constraints.weightx = 1.0;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.insets = new Insets(0, 15, 3, 3);
    panel.add(controls, constraints);
  }

  /** A short label with its controls beside it. */
  static void addInlineRow(JPanel panel, int row, String label, JComponent controls) {
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridy = row;
    constraints.anchor = GridBagConstraints.LINE_START;
    constraints.insets = new Insets(1, 3, 1, 6);
    panel.add(new JLabel(label), constraints);
    constraints.weightx = 1.0;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.insets = new Insets(1, 0, 1, 3);
    panel.add(controls, constraints);
  }

  static <T> JPanel toggleRow(
      List<T> values, Map<T, JToggleButton> buttons, java.util.function.Function<T, String> label) {
    JPanel panel = new JPanel(new GridLayout(1, 0, 4, 2));
    for (T value : values) {
      JToggleButton button = new FindingToggleButton(label.apply(value));
      button.setMargin(new Insets(2, 7, 2, 7));
      buttons.put(value, button);
      panel.add(button);
    }
    return panel;
  }

  static <T> List<T> selected(Map<T, JToggleButton> buttons) {
    return buttons.entrySet().stream()
        .filter(entry -> entry.getValue().isSelected())
        .map(Map.Entry::getKey)
        .toList();
  }

  static <T extends JComponent> T stretch(T component) {
    component.setAlignmentX(JComponent.LEFT_ALIGNMENT);
    component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
    return component;
  }
}
