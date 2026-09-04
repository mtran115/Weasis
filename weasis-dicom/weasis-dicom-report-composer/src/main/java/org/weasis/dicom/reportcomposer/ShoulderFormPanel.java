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
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.dicom.reportcomposer.ShoulderFindingBuilder.CuffTearSelection;
import org.weasis.dicom.reportcomposer.ShoulderFindingBuilder.CuffTearType;
import org.weasis.dicom.reportcomposer.ShoulderFindingBuilder.Degree;
import org.weasis.dicom.reportcomposer.ShoulderFindingBuilder.LabralLocation;
import org.weasis.dicom.reportcomposer.ShoulderFindingBuilder.RotatorCuffTendon;
import org.weasis.dicom.reportcomposer.ShoulderFindingBuilder.Selection;

final class ShoulderFormPanel extends JPanel {
  private static final List<Degree> DEGREES =
      List.of(Degree.MINIMAL, Degree.MILD, Degree.MODERATE, Degree.SEVERE);

  private final DirectChoiceControl<Degree> subcoracoidBursitis =
      new DirectChoiceControl<>(DEGREES);
  private final DirectChoiceControl<Degree> subacromialSubdeltoidBursitis =
      new DirectChoiceControl<>(DEGREES);
  private final DirectChoiceControl<Degree> acJointOsteoarthrosis =
      new DirectChoiceControl<>(DEGREES);
  private final Map<RotatorCuffTendon, DirectChoiceControl<Degree>> rotatorCuffTendinosis =
      new EnumMap<>(RotatorCuffTendon.class);
  private final Map<RotatorCuffTendon, CuffTearControls> rotatorCuffTears =
      new EnumMap<>(RotatorCuffTendon.class);
  private final Map<LabralLocation, JToggleButton> labralTearLocations =
      new EnumMap<>(LabralLocation.class);
  private final JToggleButton paralabralCyst = new JToggleButton("Adjacent paralabral cyst");
  private final DirectChoiceControl<Degree> longHeadBicepsTenosynovitis =
      new DirectChoiceControl<>(DEGREES);
  private final JTextArea freeText = new JTextArea(3, 24);
  private final JButton resetButton = iconButton(ActionIcon.RESET, "Clear shoulder form");
  private final JButton otherFindingButton = new JButton("Other Finding");
  private final JButton addButton = new JButton("Add Selected Findings");

  ShoulderFormPanel() {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(BorderFactory.createTitledBorder("Structured shoulder findings"));
    add(stretch(buildBursaeAndJointPanel()));
    add(stretch(buildRotatorCuffPanel()));
    add(stretch(buildLabrumPanel()));
    add(stretch(buildBicepsPanel()));
    add(stretch(buildFreeTextPanel()));
    add(stretch(buildActions()));
    setAlignmentX(LEFT_ALIGNMENT);
    Dimension preferred = getPreferredSize();
    setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height));
    resetButton.addActionListener(event -> clearSelections());
  }

  void addSubmitListener(ActionListener listener) {
    addButton.addActionListener(listener);
  }

  void addOtherFindingListener(ActionListener listener) {
    otherFindingButton.addActionListener(listener);
  }

  Selection selection() {
    EnumMap<RotatorCuffTendon, Degree> tendinosis = new EnumMap<>(RotatorCuffTendon.class);
    rotatorCuffTendinosis.forEach(
        (tendon, control) -> {
          Degree degree = control.selectionOr(Degree.NONE);
          if (degree != Degree.NONE) {
            tendinosis.put(tendon, degree);
          }
        });
    List<LabralLocation> labralLocations =
        labralTearLocations.entrySet().stream()
            .filter(entry -> entry.getValue().isSelected())
            .map(Map.Entry::getKey)
            .toList();
    EnumMap<RotatorCuffTendon, CuffTearSelection> tears = new EnumMap<>(RotatorCuffTendon.class);
    rotatorCuffTears.forEach(
        (tendon, controls) -> {
          CuffTearSelection tear = controls.selection();
          if (tear.hasFinding()) {
            tears.put(tendon, tear);
          }
        });
    return new Selection(
        subcoracoidBursitis.selectionOr(Degree.NONE),
        subacromialSubdeltoidBursitis.selectionOr(Degree.NONE),
        acJointOsteoarthrosis.selectionOr(Degree.NONE),
        tendinosis,
        tears,
        labralLocations,
        paralabralCyst.isSelected(),
        longHeadBicepsTenosynovitis.selectionOr(Degree.NONE),
        freeText.getText());
  }

  void clearSelections() {
    subcoracoidBursitis.clear();
    subacromialSubdeltoidBursitis.clear();
    acJointOsteoarthrosis.clear();
    rotatorCuffTendinosis.values().forEach(DirectChoiceControl::clear);
    rotatorCuffTears.values().forEach(CuffTearControls::clear);
    labralTearLocations.values().forEach(button -> button.setSelected(false));
    paralabralCyst.setSelected(false);
    longHeadBicepsTenosynovitis.clear();
    freeText.setText("");
  }

  private JPanel buildBursaeAndJointPanel() {
    JPanel panel = choicePanel("Bursae and AC joint");
    addChoiceRow(panel, 0, "Subcoracoid bursitis", subcoracoidBursitis);
    addChoiceRow(panel, 1, "Subacromial/subdeltoid bursitis", subacromialSubdeltoidBursitis);
    addChoiceRow(panel, 2, "AC joint osteoarthrosis", acJointOsteoarthrosis);
    return panel;
  }

  private JPanel buildRotatorCuffPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.setBorder(BorderFactory.createTitledBorder("Rotator cuff"));
    for (RotatorCuffTendon tendon : RotatorCuffTendon.values()) {
      DirectChoiceControl<Degree> control = new DirectChoiceControl<>(DEGREES);
      rotatorCuffTendinosis.put(tendon, control);
      CuffTearControls tearControls = new CuffTearControls();
      rotatorCuffTears.put(tendon, tearControls);

      JPanel tendonPanel = choicePanel(tendon.toString());
      addChoiceRow(tendonPanel, 0, "Tendinosis", control);
      GridBagConstraints constraints = constraints(2);
      tendonPanel.add(new JLabel("Tear"), constraints);
      constraints.gridx = 1;
      constraints.gridwidth = 2;
      constraints.weightx = 1.0;
      constraints.fill = GridBagConstraints.HORIZONTAL;
      tendonPanel.add(tearControls.panel(), constraints);
      panel.add(stretch(tendonPanel));
    }
    return panel;
  }

  private JPanel buildLabrumPanel() {
    JPanel panel = new JPanel(new BorderLayout(6, 3));
    panel.setBorder(BorderFactory.createTitledBorder("Labral tear"));
    JPanel choices = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 2));
    for (LabralLocation location : LabralLocation.values()) {
      JToggleButton button = new JToggleButton(location.toString());
      button.setMargin(new Insets(2, 7, 2, 7));
      labralTearLocations.put(location, button);
      choices.add(button);
    }
    paralabralCyst.setMargin(new Insets(2, 7, 2, 7));
    choices.add(paralabralCyst);
    panel.add(choices, BorderLayout.CENTER);
    return panel;
  }

  private JPanel buildBicepsPanel() {
    JPanel panel = choicePanel("Long head of biceps");
    addChoiceRow(panel, 0, "Tenosynovitis", longHeadBicepsTenosynovitis);
    return panel;
  }

  private JPanel buildFreeTextPanel() {
    JPanel panel = new JPanel(new BorderLayout(4, 4));
    panel.setBorder(BorderFactory.createTitledBorder("Free text"));
    freeText.setLineWrap(true);
    freeText.setWrapStyleWord(true);
    panel.add(new JScrollPane(freeText), BorderLayout.CENTER);
    return panel;
  }

  private JPanel buildActions() {
    otherFindingButton.setIcon(ResourceUtil.getIcon(ActionIcon.DRAW_TEXT));
    addButton.setIcon(ResourceUtil.getIcon(ActionIcon.PLUS));
    return GuiUtils.getFlowLayoutPanel(
        FlowLayout.TRAILING, 5, 5, resetButton, otherFindingButton, addButton);
  }

  private static JPanel choicePanel(String title) {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(BorderFactory.createTitledBorder(title));
    return panel;
  }

  private static void addChoiceRow(
      JPanel panel, int row, String label, DirectChoiceControl<?> choice) {
    GridBagConstraints constraints = constraints(row * 2);
    constraints.gridwidth = 2;
    panel.add(new JLabel(label), constraints);
    constraints = constraints(row * 2 + 1);
    constraints.gridwidth = 2;
    constraints.weightx = 1.0;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.insets = new Insets(0, 15, 4, 3);
    panel.add(choice.panel(), constraints);
  }

  private static GridBagConstraints constraints(int row) {
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = row;
    constraints.anchor = GridBagConstraints.LINE_START;
    constraints.insets = new Insets(2, 3, 2, 3);
    return constraints;
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

  private static final class CuffTearControls {
    private final JPanel panel = new JPanel();
    private final Map<CuffTearType, JToggleButton> types = new EnumMap<>(CuffTearType.class);
    private final JToggleButton highGrade = new JToggleButton("High-grade");
    private final JToggleButton atFootprint = new JToggleButton("At footprint");
    private final JToggleButton backgroundTendinosis = new JToggleButton("Background tendinosis");
    private final JTextField details = new JTextField(10);

    CuffTearControls() {
      panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
      JPanel typeRow = new JPanel(new GridLayout(0, 2, 4, 2));
      for (CuffTearType type : CuffTearType.values()) {
        JToggleButton button = new JToggleButton(type.toString());
        button.setMargin(new Insets(2, 7, 2, 7));
        button.addActionListener(event -> updateModifierState());
        types.put(type, button);
        typeRow.add(button);
      }
      panel.add(typeRow);

      for (JToggleButton modifier : List.of(highGrade, atFootprint, backgroundTendinosis)) {
        modifier.setMargin(new Insets(2, 7, 2, 7));
      }
      JPanel modifierRow = new JPanel(new GridLayout(0, 2, 4, 2));
      modifierRow.add(highGrade);
      modifierRow.add(atFootprint);
      modifierRow.add(backgroundTendinosis);
      panel.add(modifierRow);

      JPanel detailsRow = new JPanel(new BorderLayout(4, 0));
      detailsRow.setBorder(GuiUtils.getEmptyBorder(2, 0, 0, 0));
      detailsRow.add(new JLabel("Details"), BorderLayout.WEST);
      detailsRow.add(details, BorderLayout.CENTER);
      panel.add(detailsRow);
      updateModifierState();
    }

    JPanel panel() {
      return panel;
    }

    CuffTearSelection selection() {
      List<CuffTearType> selectedTypes =
          types.entrySet().stream()
              .filter(entry -> entry.getValue().isSelected())
              .map(Map.Entry::getKey)
              .toList();
      return new CuffTearSelection(
          selectedTypes,
          highGrade.isSelected(),
          atFootprint.isSelected(),
          backgroundTendinosis.isSelected(),
          details.getText());
    }

    void clear() {
      types.values().forEach(button -> button.setSelected(false));
      highGrade.setSelected(false);
      atFootprint.setSelected(false);
      backgroundTendinosis.setSelected(false);
      details.setText("");
      updateModifierState();
    }

    private void updateModifierState() {
      boolean enabled = types.values().stream().anyMatch(JToggleButton::isSelected);
      highGrade.setEnabled(enabled);
      atFootprint.setEnabled(enabled);
      backgroundTendinosis.setEnabled(enabled);
      details.setEnabled(enabled);
    }
  }
}
