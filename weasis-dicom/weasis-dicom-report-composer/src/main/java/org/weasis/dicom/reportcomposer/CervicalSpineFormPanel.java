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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.dicom.reportcomposer.CervicalSpineFindingBuilder.Curvature;
import org.weasis.dicom.reportcomposer.CervicalSpineFindingBuilder.Laterality;
import org.weasis.dicom.reportcomposer.CervicalSpineFindingBuilder.LevelSelection;
import org.weasis.dicom.reportcomposer.CervicalSpineFindingBuilder.OverviewFinding;
import org.weasis.dicom.reportcomposer.CervicalSpineFindingBuilder.Selection;
import org.weasis.dicom.reportcomposer.CervicalSpineFindingBuilder.Severity;

final class CervicalSpineFormPanel extends JPanel {
  private static final Laterality[] LATERALITIES = {
    Laterality.LEFT, Laterality.RIGHT, Laterality.BILATERAL
  };
  private static final Severity[] SEVERITIES = {Severity.MILD, Severity.MODERATE, Severity.SEVERE};

  private final JCheckBox straightening = new JCheckBox("Straightening");
  private final JCheckBox reversal = new JCheckBox("Reversal");
  private final Map<OverviewFinding, OverviewControls> overviewControls =
      new EnumMap<>(OverviewFinding.class);
  private final Map<String, LevelControls> levelControls = new LinkedHashMap<>();
  private final JButton resetButton = iconButton(ActionIcon.RESET, "Clear C-spine form");
  private final JButton otherFindingButton = new JButton("Other Finding");
  private final JButton addButton = new JButton("Add Selected Findings");

  CervicalSpineFormPanel() {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(BorderFactory.createTitledBorder("Structured C-spine findings"));
    add(stretch(buildAlignmentPanel()));
    add(stretch(buildOverviewPanel()));
    add(stretch(buildLevelPanel()));
    add(stretch(buildActions()));
    setAlignmentX(LEFT_ALIGNMENT);
    Dimension preferred = getPreferredSize();
    setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height));

    straightening.addActionListener(
        event -> {
          if (straightening.isSelected()) {
            reversal.setSelected(false);
          }
        });
    reversal.addActionListener(
        event -> {
          if (reversal.isSelected()) {
            straightening.setSelected(false);
          }
        });
    resetButton.addActionListener(event -> clearSelections());
  }

  void addSubmitListener(ActionListener listener) {
    addButton.addActionListener(listener);
  }

  void addOtherFindingListener(ActionListener listener) {
    otherFindingButton.addActionListener(listener);
  }

  Selection selection() {
    Curvature curvature =
        reversal.isSelected()
            ? Curvature.REVERSAL
            : straightening.isSelected() ? Curvature.STRAIGHTENING : Curvature.NONE;
    EnumMap<OverviewFinding, List<String>> overviewSelections =
        new EnumMap<>(OverviewFinding.class);
    overviewControls.forEach(
        (finding, controls) -> {
          if (controls.isSelected()) {
            overviewSelections.put(finding, controls.selectedLevels());
          }
        });
    List<LevelSelection> levels =
        levelControls.values().stream().map(LevelControls::selection).toList();
    return new Selection(curvature, overviewSelections, levels);
  }

  void clearSelections() {
    straightening.setSelected(false);
    reversal.setSelected(false);
    overviewControls.values().forEach(OverviewControls::clear);
    levelControls.values().forEach(LevelControls::clear);
  }

  private JPanel buildAlignmentPanel() {
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 3));
    panel.setBorder(BorderFactory.createTitledBorder("Alignment"));
    panel.add(straightening);
    panel.add(reversal);
    return panel;
  }

  private JPanel buildOverviewPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.setBorder(BorderFactory.createTitledBorder("Degenerative changes"));
    for (OverviewFinding finding : OverviewFinding.values()) {
      OverviewControls controls = new OverviewControls(finding);
      overviewControls.put(finding, controls);
      panel.add(stretch(controls.panel()));
    }
    return panel;
  }

  private JPanel buildLevelPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.setBorder(BorderFactory.createTitledBorder("Levels"));
    for (String level : CervicalSpineFindingBuilder.LEVELS) {
      LevelControls controls = new LevelControls(level);
      levelControls.put(level, controls);
      panel.add(stretch(controls.panel()));
    }
    return panel;
  }

  private JPanel buildActions() {
    otherFindingButton.setIcon(ResourceUtil.getIcon(ActionIcon.DRAW_TEXT));
    addButton.setIcon(ResourceUtil.getIcon(ActionIcon.PLUS));
    return GuiUtils.getFlowLayoutPanel(
        FlowLayout.TRAILING, 5, 5, resetButton, otherFindingButton, addButton);
  }

  private static JButton iconButton(ActionIcon icon, String tooltip) {
    JButton button = new JButton(ResourceUtil.getIcon(icon));
    button.setToolTipText(tooltip);
    button.setPreferredSize(new Dimension(30, 28));
    return button;
  }

  private static <T extends JComponent> T stretch(T component) {
    component.setAlignmentX(LEFT_ALIGNMENT);
    Dimension preferred = component.getPreferredSize();
    component.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height));
    return component;
  }

  private static void bindEnabled(JCheckBox checkBox, JComponent... components) {
    Runnable update =
        () -> {
          for (JComponent component : components) {
            component.setEnabled(checkBox.isSelected());
          }
        };
    checkBox.addActionListener(event -> update.run());
    update.run();
  }

  private static final class OverviewControls {
    private final JPanel panel = new JPanel(new BorderLayout(2, 2));
    private final JCheckBox enabled;
    private final List<JCheckBox> levels = new ArrayList<>();

    OverviewControls(OverviewFinding finding) {
      enabled = new JCheckBox(finding.toString());
      enabled.setToolTipText("Leave all levels clear for an unlocalized finding");
      panel.add(enabled, BorderLayout.NORTH);
      JPanel levelGrid = new JPanel(new GridLayout(2, 3, 3, 0));
      levelGrid.setBorder(GuiUtils.getEmptyBorder(0, 16, 3, 0));
      for (String level : CervicalSpineFindingBuilder.LEVELS) {
        JCheckBox levelCheckBox = new JCheckBox(level);
        levels.add(levelCheckBox);
        levelGrid.add(levelCheckBox);
      }
      panel.add(levelGrid, BorderLayout.CENTER);
      bindEnabled(enabled, levels.toArray(JComponent[]::new));
    }

    JPanel panel() {
      return panel;
    }

    boolean isSelected() {
      return enabled.isSelected();
    }

    List<String> selectedLevels() {
      return levels.stream().filter(JCheckBox::isSelected).map(JCheckBox::getText).toList();
    }

    void clear() {
      enabled.setSelected(false);
      levels.forEach(
          level -> {
            level.setSelected(false);
            level.setEnabled(false);
          });
    }
  }

  private static final class LevelControls {
    private final String level;
    private final JPanel panel = new JPanel(new GridBagLayout());
    private final JCheckBox bulge = new JCheckBox("Bulge");
    private final JCheckBox protrusion = new JCheckBox("Protrusion");
    private final LateralizedControl facetArthrosis = new LateralizedControl("Facet arthrosis");
    private final LateralizedControl uncovertebralHypertrophy =
        new LateralizedControl("Uncovertebral hypertrophy");
    private final JCheckBox foraminalStenosis = new JCheckBox("Foraminal stenosis");
    private final JComboBox<Laterality> foraminalLaterality = new JComboBox<>(LATERALITIES);
    private final JComboBox<Severity> foraminalSeverity = new JComboBox<>(SEVERITIES);
    private final JTextField freeText = new JTextField();

    LevelControls(String level) {
      this.level = level;
      panel.setBorder(
          BorderFactory.createCompoundBorder(
              BorderFactory.createMatteBorder(1, 0, 0, 0, Color.GRAY),
              GuiUtils.getEmptyBorder(5, 2, 6, 2)));

      GridBagConstraints constraints = constraints(0);
      constraints.gridwidth = 3;
      JLabel title = new JLabel(level);
      title.setFont(title.getFont().deriveFont(Font.BOLD));
      panel.add(title, constraints);

      constraints = constraints(1);
      constraints.gridwidth = 3;
      panel.add(
          GuiUtils.getFlowLayoutPanel(FlowLayout.LEADING, 4, 0, bulge, protrusion), constraints);

      addLateralizedRow(2, facetArthrosis);
      addLateralizedRow(3, uncovertebralHypertrophy);

      constraints = constraints(4);
      panel.add(foraminalStenosis, constraints);
      constraints.gridx = 1;
      panel.add(foraminalLaterality, constraints);
      constraints.gridx = 2;
      panel.add(foraminalSeverity, constraints);
      foraminalLaterality.setSelectedItem(Laterality.BILATERAL);
      bindEnabled(foraminalStenosis, foraminalLaterality, foraminalSeverity);

      constraints = constraints(5);
      panel.add(new JLabel("Free text"), constraints);
      constraints.gridx = 1;
      constraints.gridwidth = 2;
      constraints.weightx = 1.0;
      constraints.fill = GridBagConstraints.HORIZONTAL;
      panel.add(freeText, constraints);
    }

    JPanel panel() {
      return panel;
    }

    LevelSelection selection() {
      Laterality foraminalSide =
          foraminalStenosis.isSelected()
              ? (Laterality) foraminalLaterality.getSelectedItem()
              : Laterality.NONE;
      Severity stenosisSeverity =
          foraminalStenosis.isSelected()
              ? (Severity) foraminalSeverity.getSelectedItem()
              : Severity.NONE;
      return new LevelSelection(
          level,
          bulge.isSelected(),
          protrusion.isSelected(),
          freeText.getText(),
          facetArthrosis.laterality(),
          uncovertebralHypertrophy.laterality(),
          foraminalSide,
          stenosisSeverity);
    }

    void clear() {
      bulge.setSelected(false);
      protrusion.setSelected(false);
      facetArthrosis.clear();
      uncovertebralHypertrophy.clear();
      foraminalStenosis.setSelected(false);
      foraminalLaterality.setSelectedItem(Laterality.BILATERAL);
      foraminalSeverity.setSelectedItem(Severity.MILD);
      foraminalLaterality.setEnabled(false);
      foraminalSeverity.setEnabled(false);
      freeText.setText("");
    }

    private void addLateralizedRow(int row, LateralizedControl control) {
      GridBagConstraints constraints = constraints(row);
      constraints.gridwidth = 2;
      panel.add(control.checkBox(), constraints);
      constraints.gridx = 2;
      constraints.gridwidth = 1;
      panel.add(control.comboBox(), constraints);
    }

    private static GridBagConstraints constraints(int row) {
      GridBagConstraints constraints = new GridBagConstraints();
      constraints.gridx = 0;
      constraints.gridy = row;
      constraints.anchor = GridBagConstraints.LINE_START;
      constraints.insets = new Insets(2, 3, 2, 3);
      return constraints;
    }
  }

  private static final class LateralizedControl {
    private final JCheckBox checkBox;
    private final JComboBox<Laterality> comboBox = new JComboBox<>(LATERALITIES);

    LateralizedControl(String label) {
      checkBox = new JCheckBox(label);
      comboBox.setSelectedItem(Laterality.BILATERAL);
      bindEnabled(checkBox, comboBox);
    }

    JCheckBox checkBox() {
      return checkBox;
    }

    JComboBox<Laterality> comboBox() {
      return comboBox;
    }

    Laterality laterality() {
      return checkBox.isSelected() ? (Laterality) comboBox.getSelectedItem() : Laterality.NONE;
    }

    void clear() {
      checkBox.setSelected(false);
      comboBox.setSelectedItem(Laterality.BILATERAL);
      comboBox.setEnabled(false);
    }
  }
}
