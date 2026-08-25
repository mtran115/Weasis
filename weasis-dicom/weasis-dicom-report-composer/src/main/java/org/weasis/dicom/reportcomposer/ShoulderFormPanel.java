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
import javax.swing.JToggleButton;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
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
  private final Map<LabralLocation, JToggleButton> labralTearLocations =
      new EnumMap<>(LabralLocation.class);
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
    return new Selection(
        subcoracoidBursitis.selectionOr(Degree.NONE),
        subacromialSubdeltoidBursitis.selectionOr(Degree.NONE),
        acJointOsteoarthrosis.selectionOr(Degree.NONE),
        tendinosis,
        labralLocations,
        longHeadBicepsTenosynovitis.selectionOr(Degree.NONE),
        freeText.getText());
  }

  void clearSelections() {
    subcoracoidBursitis.clear();
    subacromialSubdeltoidBursitis.clear();
    acJointOsteoarthrosis.clear();
    rotatorCuffTendinosis.values().forEach(DirectChoiceControl::clear);
    labralTearLocations.values().forEach(button -> button.setSelected(false));
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
    JPanel panel = choicePanel("Rotator cuff tendinosis");
    int row = 0;
    for (RotatorCuffTendon tendon : RotatorCuffTendon.values()) {
      DirectChoiceControl<Degree> control = new DirectChoiceControl<>(DEGREES);
      rotatorCuffTendinosis.put(tendon, control);
      addChoiceRow(panel, row++, tendon.toString(), control);
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
}
