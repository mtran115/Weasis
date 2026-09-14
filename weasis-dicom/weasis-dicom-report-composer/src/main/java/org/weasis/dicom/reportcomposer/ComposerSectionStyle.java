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

import java.awt.Color;
import java.awt.Component;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.border.TitledBorder;

/** Subtle section colors without changing the component paths used by saved study drafts. */
final class ComposerSectionStyle {
  private static final String SECTION = "reportComposer.section";
  private static final String COLOR_INDEX = "reportComposer.sectionColorIndex";
  private static final Color[] ACCENTS = {
    new Color(79, 154, 199), // Blue
    new Color(84, 170, 150), // Teal
    new Color(155, 134, 200), // Lavender
    new Color(188, 153, 96), // Sand
    new Color(187, 131, 158) // Rose
  };

  private ComposerSectionStyle() {}

  static void markSection(JPanel panel) {
    panel.putClientProperty(SECTION, Boolean.TRUE);
  }

  static void apply(JPanel root) {
    apply(root, 0, false);
  }

  private static int apply(JPanel panel, int nextColor, boolean insideSection) {
    boolean section =
        panel.getBorder() instanceof TitledBorder
            || Boolean.TRUE.equals(panel.getClientProperty(SECTION));
    if (section) {
      boolean installed = panel.getClientProperty(COLOR_INDEX) != null;
      panel.putClientProperty(COLOR_INDEX, nextColor++ % ACCENTS.length);
      panel.setOpaque(true);
      updateBackground(panel);
      if (!installed) {
        panel.addPropertyChangeListener("UI", event -> updateBackground(panel));
      }
    } else if (insideSection) {
      // Layout rows inherit their section's tint; text fields and finding buttons keep their UI.
      panel.setOpaque(false);
    }

    for (Component child : panel.getComponents()) {
      if (child instanceof JPanel childPanel) {
        nextColor = apply(childPanel, nextColor, insideSection || section);
      } else if (child instanceof JCheckBox checkBox && (insideSection || section)) {
        checkBox.setOpaque(false);
      }
    }
    return nextColor;
  }

  private static void updateBackground(JPanel panel) {
    Color base = UIManager.getColor("Panel.background");
    if (base == null) base = panel.getBackground();
    if (base == null) return;
    Color accent = ACCENTS[(Integer) panel.getClientProperty(COLOR_INDEX)];
    double brightness = base.getRed() * 0.2126 + base.getGreen() * 0.7152 + base.getBlue() * 0.0722;
    double amount = brightness < 128 ? 0.20 : 0.12;
    panel.setBackground(
        new Color(
            blend(base.getRed(), accent.getRed(), amount),
            blend(base.getGreen(), accent.getGreen(), amount),
            blend(base.getBlue(), accent.getBlue(), amount)));
  }

  private static int blend(int base, int accent, double amount) {
    return (int) Math.round(base + (accent - base) * amount);
  }
}
