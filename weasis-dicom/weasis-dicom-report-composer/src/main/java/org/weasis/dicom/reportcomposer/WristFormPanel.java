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
import org.weasis.dicom.reportcomposer.WristFindingBuilder.BoneFindingType;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.BoneLocation;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.BoneSelection;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.Degree;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.FluidAmount;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.FluidLocation;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.FluidSelection;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.GanglionLocation;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.GanglionSelection;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.Ligament;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.LigamentSelection;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.LigamentStatus;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.Selection;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.Tendon;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.TendonSelection;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.TendonTear;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.TfccFinding;

final class WristFormPanel extends JPanel {
  private static final List<Degree> DEGREES =
      List.of(Degree.MINIMAL, Degree.MILD, Degree.MODERATE, Degree.SEVERE);
  private static final List<LigamentStatus> LIGAMENT_STATUSES =
      List.of(
          LigamentStatus.DEGENERATION,
          LigamentStatus.SPRAIN,
          LigamentStatus.PARTIAL_TEAR,
          LigamentStatus.TEAR,
          LigamentStatus.COMPLETE_TEAR);
  private static final List<TfccFinding> TFCC_FINDINGS =
      List.of(
          TfccFinding.DEGENERATION,
          TfccFinding.CENTRAL_PERFORATION,
          TfccFinding.RADIAL_ATTACHMENT_TEAR,
          TfccFinding.ULNAR_ATTACHMENT_TEAR,
          TfccFinding.PERIPHERAL_TEAR,
          TfccFinding.TEAR);
  private static final List<TendonTear> TENDON_TEARS =
      List.of(TendonTear.SPLIT, TendonTear.PARTIAL, TendonTear.COMPLETE);
  private static final List<BoneFindingType> BONE_FINDINGS =
      List.of(
          BoneFindingType.MARROW_EDEMA,
          BoneFindingType.CONTUSION,
          BoneFindingType.CYSTIC_CHANGES,
          BoneFindingType.INTRAOSSEOUS_CYST);
  private static final List<GanglionLocation> GANGLION_LOCATIONS =
      List.of(GanglionLocation.DORSAL, GanglionLocation.VOLAR);
  private static final List<FluidLocation> FLUID_LOCATIONS =
      List.of(
          FluidLocation.PISOTRIQUETRAL_RECESS,
          FluidLocation.DORSAL_RADIOCARPAL_JOINT,
          FluidLocation.RADIOCARPAL_JOINT,
          FluidLocation.MIDCARPAL_JOINT,
          FluidLocation.DISTAL_RADIOULNAR_JOINT,
          FluidLocation.ULNAR_STYLOID);
  private static final List<FluidAmount> FLUID_AMOUNTS =
      List.of(FluidAmount.TRACE, FluidAmount.SMALL, FluidAmount.MODERATE, FluidAmount.LARGE);

  private final JToggleButton normal = new FindingToggleButton("Normal wrist MRI");
  private final Map<Ligament, LigamentControls> ligaments = new EnumMap<>(Ligament.class);
  private final DirectChoiceControl<TfccFinding> tfccFinding =
      new DirectChoiceControl<>(TFCC_FINDINGS, 2);
  private final Map<Tendon, TendonControls> tendons = new EnumMap<>(Tendon.class);
  private final DirectChoiceControl<BoneFindingType> boneType =
      new DirectChoiceControl<>(BONE_FINDINGS, 2);
  private final DirectChoiceControl<Degree> boneDegree = new DirectChoiceControl<>(DEGREES, 2);
  private final Map<BoneLocation, JToggleButton> boneLocations = new EnumMap<>(BoneLocation.class);
  private final JTextField cystSize = new JTextField(6);
  private final DirectChoiceControl<GanglionLocation> ganglionLocation =
      new DirectChoiceControl<>(GANGLION_LOCATIONS, 2);
  private final JTextField ganglionSize = new JTextField(6);
  private final JTextField ganglionAdditionalLocation = new JTextField();
  private final DirectChoiceControl<FluidLocation> fluidLocation =
      new DirectChoiceControl<>(FLUID_LOCATIONS, 2);
  private final DirectChoiceControl<FluidAmount> fluidAmount =
      new DirectChoiceControl<>(FLUID_AMOUNTS, 2);
  private final JToggleButton likelyInflammatory = new FindingToggleButton("Likely inflammatory");
  private final JToggleButton motionArtifact = new FindingToggleButton("Motion artifact");
  private final JTextArea freeText = new JTextArea(3, 24);
  private final JButton resetButton = iconButton(ActionIcon.RESET, "Clear wrist form");
  private final JButton otherFindingButton = new JButton("Other Finding");
  private final JButton addButton = new JButton("Add Selected Findings");

  WristFormPanel() {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(BorderFactory.createTitledBorder("Structured wrist findings"));
    add(stretch(buildNormalPanel()));
    add(stretch(buildLigamentPanel()));
    add(stretch(buildTfccPanel()));
    add(stretch(buildTendonPanel()));
    add(stretch(buildBonePanel()));
    add(stretch(buildGanglionPanel()));
    add(stretch(buildFluidPanel()));
    add(stretch(buildTechnicalPanel()));
    add(stretch(buildFreeTextPanel()));
    add(stretch(buildActions()));
    setAlignmentX(LEFT_ALIGNMENT);
    Dimension preferred = getPreferredSize();
    setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height));

    boneType.select(BoneFindingType.CONTUSION);
    updateCystSizeState();
    updateGanglionState();
    updateFluidState();
    registerPositiveSelectionListeners();
    normal.addActionListener(
        event -> {
          if (normal.isSelected()) {
            clearPositiveSelections();
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
    EnumMap<Ligament, LigamentSelection> selectedLigaments = new EnumMap<>(Ligament.class);
    ligaments.forEach(
        (ligament, controls) -> {
          LigamentSelection selection = controls.selection();
          if (selection.hasFinding()) {
            selectedLigaments.put(ligament, selection);
          }
        });
    EnumMap<Tendon, TendonSelection> selectedTendons = new EnumMap<>(Tendon.class);
    tendons.forEach(
        (tendon, controls) -> {
          TendonSelection selection = controls.selection();
          if (selection.hasFinding()) {
            selectedTendons.put(tendon, selection);
          }
        });

    return new Selection(
        normal.isSelected(),
        selectedLigaments,
        tfccFinding.selectionOr(TfccFinding.NONE),
        selectedTendons,
        new BoneSelection(
            boneType.selectionOr(BoneFindingType.NONE),
            boneDegree.selectionOr(Degree.NONE),
            selected(boneLocations),
            cystSize.getText()),
        new GanglionSelection(
            ganglionLocation.selectionOr(GanglionLocation.NONE),
            ganglionSize.getText(),
            ganglionAdditionalLocation.getText()),
        new FluidSelection(
            fluidLocation.selectionOr(FluidLocation.NONE),
            fluidAmount.selectionOr(FluidAmount.NONE),
            likelyInflammatory.isSelected()),
        motionArtifact.isSelected(),
        freeText.getText());
  }

  void clearSelections() {
    normal.setSelected(false);
    motionArtifact.setSelected(false);
    clearPositiveSelections();
    freeText.setText("");
  }

  private void clearPositiveSelections() {
    ligaments.values().forEach(LigamentControls::clear);
    tfccFinding.clear();
    tendons.values().forEach(TendonControls::clear);
    boneType.clear();
    boneType.select(BoneFindingType.CONTUSION);
    boneDegree.clear();
    boneLocations.values().forEach(button -> button.setSelected(false));
    cystSize.setText("");
    ganglionLocation.clear();
    ganglionSize.setText("");
    ganglionAdditionalLocation.setText("");
    fluidLocation.clear();
    fluidAmount.clear();
    likelyInflammatory.setSelected(false);
    updateCystSizeState();
    updateGanglionState();
    updateFluidState();
  }

  private JPanel buildNormalPanel() {
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 2));
    normal.setMargin(new Insets(2, 7, 2, 7));
    panel.add(normal);
    return panel;
  }

  private JPanel buildLigamentPanel() {
    JPanel panel = verticalPanel("Intrinsic ligaments");
    for (Ligament ligament : Ligament.values()) {
      LigamentControls controls = new LigamentControls(ligament);
      ligaments.put(ligament, controls);
      panel.add(stretch(controls.panel()));
    }
    return panel;
  }

  private JPanel buildTfccPanel() {
    JPanel panel = choicePanel("TFCC");
    addChoiceRow(panel, 0, "Finding", tfccFinding);
    return panel;
  }

  private JPanel buildTendonPanel() {
    JPanel panel = verticalPanel("Frequently used tendons");
    for (Tendon tendon : Tendon.values()) {
      TendonControls controls = new TendonControls(tendon);
      tendons.put(tendon, controls);
      panel.add(stretch(controls.panel()));
    }
    return panel;
  }

  private JPanel buildBonePanel() {
    JPanel panel = choicePanel("Bone findings");
    addChoiceRow(panel, 0, "Finding", boneType);
    addChoiceRow(panel, 1, "Degree", boneDegree);

    GridBagConstraints constraints = constraints(4);
    constraints.gridwidth = 2;
    panel.add(new JLabel("Location"), constraints);
    constraints = constraints(5);
    constraints.gridwidth = 2;
    constraints.weightx = 1.0;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.insets = new Insets(0, 15, 4, 3);
    panel.add(toggleGrid(BoneLocation.values(), boneLocations, 2), constraints);

    constraints = constraints(6);
    panel.add(new JLabel("Cyst size"), constraints);
    constraints.gridx = 1;
    constraints.weightx = 1.0;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    panel.add(cystSize, constraints);
    boneType.addActionListener(event -> updateCystSizeState());
    return panel;
  }

  private JPanel buildGanglionPanel() {
    JPanel panel = choicePanel("Ganglion cyst");
    addChoiceRow(panel, 0, "Location", ganglionLocation);
    addTextRow(panel, 1, "Size", ganglionSize);
    addTextRow(panel, 2, "Additional location", ganglionAdditionalLocation);
    ganglionLocation.addActionListener(event -> updateGanglionState());
    return panel;
  }

  private JPanel buildFluidPanel() {
    JPanel panel = choicePanel("Joint and recess fluid");
    addChoiceRow(panel, 0, "Location", fluidLocation);
    addChoiceRow(panel, 1, "Amount", fluidAmount);
    likelyInflammatory.setMargin(new Insets(2, 7, 2, 7));
    GridBagConstraints constraints = constraints(4);
    constraints.gridx = 1;
    constraints.gridwidth = 2;
    panel.add(likelyInflammatory, constraints);
    fluidLocation.addActionListener(event -> updateFluidState());
    return panel;
  }

  private JPanel buildTechnicalPanel() {
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 2));
    panel.setBorder(BorderFactory.createTitledBorder("Technical notes"));
    motionArtifact.setMargin(new Insets(2, 7, 2, 7));
    panel.add(motionArtifact);
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

  private void registerPositiveSelectionListeners() {
    ActionListener clearNormal = event -> normal.setSelected(false);
    ligaments.values().forEach(controls -> controls.addActionListener(clearNormal));
    tfccFinding.addActionListener(clearNormal);
    tendons.values().forEach(controls -> controls.addActionListener(clearNormal));
    boneType.addActionListener(clearNormal);
    boneDegree.addActionListener(clearNormal);
    boneLocations.values().forEach(button -> button.addActionListener(clearNormal));
    ganglionLocation.addActionListener(clearNormal);
    fluidLocation.addActionListener(clearNormal);
    fluidAmount.addActionListener(clearNormal);
    likelyInflammatory.addActionListener(clearNormal);
  }

  private void updateCystSizeState() {
    cystSize.setEnabled(boneType.selectionOr(BoneFindingType.NONE).isCystic());
  }

  private void updateGanglionState() {
    boolean enabled = ganglionLocation.selectionOr(GanglionLocation.NONE) != GanglionLocation.NONE;
    ganglionSize.setEnabled(enabled);
    ganglionAdditionalLocation.setEnabled(enabled);
  }

  private void updateFluidState() {
    boolean enabled = fluidLocation.selectionOr(FluidLocation.NONE) != FluidLocation.NONE;
    fluidAmount.setEnabled(enabled);
    likelyInflammatory.setEnabled(enabled);
  }

  private static JPanel verticalPanel(String title) {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.setBorder(BorderFactory.createTitledBorder(title));
    return panel;
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

  private static void addTextRow(JPanel panel, int row, String label, JTextField field) {
    GridBagConstraints constraints = constraints(row * 2);
    panel.add(new JLabel(label), constraints);
    constraints = constraints(row * 2 + 1);
    constraints.gridwidth = 2;
    constraints.weightx = 1.0;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.insets = new Insets(0, 15, 4, 3);
    panel.add(field, constraints);
  }

  private static GridBagConstraints constraints(int row) {
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = row;
    constraints.anchor = GridBagConstraints.LINE_START;
    constraints.insets = new Insets(2, 3, 2, 3);
    return constraints;
  }

  private static <T extends Enum<T>> JPanel toggleGrid(
      T[] values, Map<T, JToggleButton> buttons, int columns) {
    JPanel panel = new JPanel(new GridLayout(0, columns, 4, 2));
    for (T value : values) {
      JToggleButton button = new FindingToggleButton(value.toString());
      button.setMargin(new Insets(2, 7, 2, 7));
      buttons.put(value, button);
      panel.add(button);
    }
    return panel;
  }

  private static <T> List<T> selected(Map<T, JToggleButton> buttons) {
    return buttons.entrySet().stream()
        .filter(entry -> entry.getValue().isSelected())
        .map(Map.Entry::getKey)
        .toList();
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

  private static final class LigamentControls {
    private final Ligament ligament;
    private final JPanel panel = new JPanel(new GridBagLayout());
    private final DirectChoiceControl<LigamentStatus> status =
        new DirectChoiceControl<>(LIGAMENT_STATUSES, 2);
    private final JToggleButton intervalWidening =
        new FindingToggleButton("Scapholunate interval widening");
    private final JTextField intervalMeasurement = new JTextField(6);

    LigamentControls(Ligament ligament) {
      this.ligament = ligament;
      panel.setBorder(BorderFactory.createTitledBorder(ligament.toString()));
      addChoiceRow(panel, 0, "Injury", status);
      if (ligament == Ligament.SCAPHOLUNATE) {
        intervalWidening.setMargin(new Insets(2, 7, 2, 7));
        GridBagConstraints constraints = constraints(2);
        constraints.gridwidth = 2;
        panel.add(intervalWidening, constraints);
        constraints = constraints(3);
        panel.add(new JLabel("Interval measurement"), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(intervalMeasurement, constraints);
        intervalWidening.addActionListener(event -> updateMeasurementState());
        updateMeasurementState();
      }
    }

    JPanel panel() {
      return panel;
    }

    LigamentSelection selection() {
      return new LigamentSelection(
          status.selectionOr(LigamentStatus.NONE),
          ligament == Ligament.SCAPHOLUNATE && intervalWidening.isSelected(),
          intervalMeasurement.getText());
    }

    void clear() {
      status.clear();
      intervalWidening.setSelected(false);
      intervalMeasurement.setText("");
      updateMeasurementState();
    }

    void addActionListener(ActionListener listener) {
      status.addActionListener(listener);
      if (ligament == Ligament.SCAPHOLUNATE) {
        intervalWidening.addActionListener(listener);
      }
    }

    private void updateMeasurementState() {
      intervalMeasurement.setEnabled(intervalWidening.isSelected());
    }
  }

  private static final class TendonControls {
    private final JPanel panel = new JPanel(new GridBagLayout());
    private final DirectChoiceControl<Degree> tendinosis = new DirectChoiceControl<>(DEGREES, 2);
    private final DirectChoiceControl<Degree> tenosynovitis = new DirectChoiceControl<>(DEGREES, 2);
    private final DirectChoiceControl<TendonTear> tear = new DirectChoiceControl<>(TENDON_TEARS, 2);

    TendonControls(Tendon tendon) {
      panel.setBorder(BorderFactory.createTitledBorder(tendon.toString()));
      addChoiceRow(panel, 0, "Tendinosis", tendinosis);
      addChoiceRow(panel, 1, "Tenosynovitis", tenosynovitis);
      addChoiceRow(panel, 2, "Tear", tear);
    }

    JPanel panel() {
      return panel;
    }

    TendonSelection selection() {
      return new TendonSelection(
          tendinosis.selectionOr(Degree.NONE),
          tenosynovitis.selectionOr(Degree.NONE),
          tear.selectionOr(TendonTear.NONE));
    }

    void clear() {
      tendinosis.clear();
      tenosynovitis.clear();
      tear.clear();
    }

    void addActionListener(ActionListener listener) {
      tendinosis.addActionListener(listener);
      tenosynovitis.addActionListener(listener);
      tear.addActionListener(listener);
    }
  }
}
