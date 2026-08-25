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

import java.awt.FlowLayout;
import java.awt.Insets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

final class DirectChoiceControl<T> {
  private final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 0));
  private final Map<T, JToggleButton> buttons = new LinkedHashMap<>();

  DirectChoiceControl(List<T> choices) {
    for (T choice : choices) {
      JToggleButton button = new JToggleButton(choice.toString());
      button.setMargin(new Insets(2, 7, 2, 7));
      button.addActionListener(
          event -> {
            if (button.isSelected()) {
              buttons.values().stream()
                  .filter(other -> other != button)
                  .forEach(other -> other.setSelected(false));
            }
          });
      buttons.put(choice, button);
      panel.add(button);
    }
  }

  JPanel panel() {
    return panel;
  }

  T selectionOr(T fallback) {
    return buttons.entrySet().stream()
        .filter(entry -> entry.getValue().isSelected())
        .map(Map.Entry::getKey)
        .findFirst()
        .orElse(fallback);
  }

  void clear() {
    buttons.values().forEach(button -> button.setSelected(false));
  }
}
