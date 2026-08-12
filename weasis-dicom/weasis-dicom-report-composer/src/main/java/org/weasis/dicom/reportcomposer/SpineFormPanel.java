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
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.AlignmentFinding;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.Laterality;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.LevelSelection;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.OverviewFinding;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.ProtrusionLocation;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.Selection;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.Severity;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.SpineRegion;

final class SpineFormPanel extends JPanel {
  private static final Laterality[] LATERALITIES = {
    Laterality.LEFT, Laterality.RIGHT, Laterality.BILATERAL
  };
  private static final Severity[] SEVERITIES = {Severity.MILD, Severity.MODERATE, Severity.SEVERE};

  private final SpineRegion region;
  private final Map<AlignmentFinding, JCheckBox> alignmentControls =
      new EnumMap<>(AlignmentFinding.class);
  private final Map<OverviewFinding, OverviewControls> overviewControls =
      new EnumMap<>(OverviewFinding.class);
  private final Map<String, LevelControls> levelControls = new LinkedHashMap<>();
  private final JButton resetButton;
  private final JButton otherFindingButton = new JButton("Other Finding");
  private final JButton addButton = new JButton("Add Selected Findings");

  SpineFormPanel(SpineRegion region) {
    this.region = region;
    resetButton = iconButton(ActionIcon.RESET, "Clear " + region.formLabel() + " form");
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(BorderFactory.createTitledBorder("Structured " + region.formLabel() + " findings"));
    add(stretch(buildAlignmentPanel()));
    add(stretch(buildOverviewPanel()));
    add(stretch(buildLevelPanel()));
    add(stretch(buildActions()));
    setAlignmentX(LEFT_ALIGNMENT);
    Dimension preferred = getPreferredSize();
    setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height));
    resetButton.addActionListener(event -> clearSelections());
  }

  SpineRegion region() {
    return region;
  }

  void addSubmitListener(ActionListener listener) {
    addButton.addActionListener(listener);
  }

  void addOtherFindingListener(ActionListener listener) {
    otherFindingButton.addActionListener(listener);
  }

  Selection selection() {
    AlignmentFinding alignment =
        alignmentControls.entrySet().stream()
            .filter(entry -> entry.getValue().isSelected())
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(AlignmentFinding.NONE);
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
    return new Selection(region, alignment, overviewSelections, levels);
  }

  void clearSelections() {
    alignmentControls.values().forEach(checkBox -> checkBox.setSelected(false));
    overviewControls.values().forEach(OverviewControls::clear);
    levelControls.values().forEach(LevelControls::clear);
  }

  private JPanel buildAlignmentPanel() {
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 3));
    panel.setBorder(BorderFactory.createTitledBorder("Alignment"));
    for (AlignmentFinding finding : region.alignmentFindings()) {
      JCheckBox checkBox = new JCheckBox(finding.toString());
      alignmentControls.put(finding, checkBox);
      checkBox.addActionListener(
          event -> {
            if (checkBox.isSelected()) {
              alignmentControls.values().stream()
                  .filter(other -> other != checkBox)
                  .forEach(other -> other.setSelected(false));
            }
          });
      panel.add(checkBox);
    }
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
    for (String level : region.levels()) {
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

  private final class OverviewControls {
    private final JPanel panel = new JPanel(new BorderLayout(2, 2));
    private final JCheckBox enabled;
    private final List<JCheckBox> levels = new ArrayList<>();

    OverviewControls(OverviewFinding finding) {
      enabled = new JCheckBox(finding.toString());
      enabled.setToolTipText("Leave all levels clear for an unlocalized finding");
      panel.add(enabled, BorderLayout.NORTH);
      JPanel levelGrid = new JPanel(new GridLayout(0, 3, 3, 0));
      levelGrid.setBorder(GuiUtils.getEmptyBorder(0, 16, 3, 0));
      for (String level : region.levels()) {
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

  private final class LevelControls {
    private final String level;
    private final JPanel panel = new JPanel(new GridBagLayout());
    private final JCheckBox bulge = new JCheckBox("Bulge");
    private final JCheckBox protrusion = new JCheckBox("Protrusion");
    private final Map<ProtrusionLocation, JCheckBox> protrusionLocations =
        new EnumMap<>(ProtrusionLocation.class);
    private final LateralizedControl facetArthrosis = new LateralizedControl("Facet arthrosis");
    private final LateralizedControl posteriorElementHypertrophy =
        new LateralizedControl(region.posteriorElementLabel());
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

      JPanel protrusionLocationPanel = new JPanel(new GridLayout(0, 2, 4, 0));
      protrusionLocationPanel.setBorder(GuiUtils.getEmptyBorder(0, 18, 2, 0));
      for (ProtrusionLocation location : ProtrusionLocation.values()) {
        JCheckBox locationCheckBox = new JCheckBox(location.toString());
        locationCheckBox.setSelected(location == ProtrusionLocation.CENTRAL);
        protrusionLocations.put(location, locationCheckBox);
        protrusionLocationPanel.add(locationCheckBox);
      }
      constraints = constraints(2);
      constraints.gridwidth = 3;
      constraints.fill = GridBagConstraints.HORIZONTAL;
      panel.add(protrusionLocationPanel, constraints);
      bindEnabled(protrusion, protrusionLocations.values().toArray(JComponent[]::new));

      addLateralizedRow(3, facetArthrosis);
      addLateralizedRow(4, posteriorElementHypertrophy);

      constraints = constraints(5);
      panel.add(foraminalStenosis, constraints);
      constraints.gridx = 1;
      panel.add(foraminalLaterality, constraints);
      constraints.gridx = 2;
      panel.add(foraminalSeverity, constraints);
      foraminalLaterality.setSelectedItem(Laterality.BILATERAL);
      bindEnabled(foraminalStenosis, foraminalLaterality, foraminalSeverity);

      constraints = constraints(6);
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
          selectedProtrusionLocations(),
          freeText.getText(),
          facetArthrosis.laterality(),
          posteriorElementHypertrophy.laterality(),
          foraminalSide,
          stenosisSeverity);
    }

    private List<ProtrusionLocation> selectedProtrusionLocations() {
      if (!protrusion.isSelected()) {
        return List.of();
      }
      List<ProtrusionLocation> selected =
          protrusionLocations.entrySet().stream()
              .filter(entry -> entry.getValue().isSelected())
              .map(Map.Entry::getKey)
              .toList();
      return selected.isEmpty() ? List.of(ProtrusionLocation.CENTRAL) : selected;
    }

    void clear() {
      bulge.setSelected(false);
      protrusion.setSelected(false);
      protrusionLocations.forEach(
          (location, checkBox) -> {
            checkBox.setSelected(location == ProtrusionLocation.CENTRAL);
            checkBox.setEnabled(false);
          });
      facetArthrosis.clear();
      posteriorElementHypertrophy.clear();
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

    private GridBagConstraints constraints(int row) {
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
