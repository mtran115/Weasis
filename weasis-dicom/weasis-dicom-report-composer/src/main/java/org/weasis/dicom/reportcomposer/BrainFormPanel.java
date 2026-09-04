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
import org.weasis.dicom.reportcomposer.BrainFindingBuilder.AcuteFindingSelection;
import org.weasis.dicom.reportcomposer.BrainFindingBuilder.AcuteFindingType;
import org.weasis.dicom.reportcomposer.BrainFindingBuilder.CerebralRegion;
import org.weasis.dicom.reportcomposer.BrainFindingBuilder.Degree;
import org.weasis.dicom.reportcomposer.BrainFindingBuilder.Laterality;
import org.weasis.dicom.reportcomposer.BrainFindingBuilder.Selection;
import org.weasis.dicom.reportcomposer.BrainFindingBuilder.SinusFindingType;
import org.weasis.dicom.reportcomposer.BrainFindingBuilder.SinusSelection;
import org.weasis.dicom.reportcomposer.BrainFindingBuilder.SinusSite;
import org.weasis.dicom.reportcomposer.BrainFindingBuilder.TechnicalNote;
import org.weasis.dicom.reportcomposer.BrainFindingBuilder.WhiteMatterDistribution;
import org.weasis.dicom.reportcomposer.BrainFindingBuilder.WhiteMatterEtiology;
import org.weasis.dicom.reportcomposer.BrainFindingBuilder.WhiteMatterQuantity;
import org.weasis.dicom.reportcomposer.BrainFindingBuilder.WhiteMatterSelection;

final class BrainFormPanel extends JPanel {
  private static final List<WhiteMatterQuantity> WHITE_MATTER_QUANTITIES =
      List.of(
          WhiteMatterQuantity.SINGLE,
          WhiteMatterQuantity.TWO,
          WhiteMatterQuantity.FEW,
          WhiteMatterQuantity.MULTIPLE,
          WhiteMatterQuantity.NUMEROUS);
  private static final List<AcuteFindingType> ACUTE_FINDING_TYPES =
      List.of(
          AcuteFindingType.ACUTE_INFARCT,
          AcuteFindingType.SUBACUTE_INFARCT,
          AcuteFindingType.RESTRICTED_DIFFUSION,
          AcuteFindingType.INTRAPARENCHYMAL_HEMORRHAGE,
          AcuteFindingType.MICROHEMORRHAGE);
  private static final List<Laterality> LATERALITIES =
      List.of(Laterality.RIGHT, Laterality.LEFT, Laterality.BILATERAL);
  private static final List<Degree> DEGREES = List.of(Degree.MILD, Degree.MODERATE, Degree.SEVERE);
  private static final List<SinusFindingType> SINUS_FINDINGS =
      List.of(
          SinusFindingType.MUCOSAL_THICKENING,
          SinusFindingType.RETENTION_CYST,
          SinusFindingType.FLUID_OPACIFICATION);

  private final JToggleButton normal = new JToggleButton("Normal brain MRI");
  private final DirectChoiceControl<WhiteMatterQuantity> whiteMatterQuantity =
      new DirectChoiceControl<>(WHITE_MATTER_QUANTITIES, 2);
  private final Map<WhiteMatterDistribution, JToggleButton> whiteMatterDistributions =
      new EnumMap<>(WhiteMatterDistribution.class);
  private final Map<CerebralRegion, JToggleButton> whiteMatterRegions =
      new EnumMap<>(CerebralRegion.class);
  private final JTextField whiteMatterMaximumSize = new JTextField(7);
  private final JToggleButton whiteMatterNonspecific = new JToggleButton("Nonspecific");
  private final Map<WhiteMatterEtiology, JToggleButton> whiteMatterEtiologies =
      new EnumMap<>(WhiteMatterEtiology.class);
  private final DirectChoiceControl<AcuteFindingType> acuteFindingType =
      new DirectChoiceControl<>(ACUTE_FINDING_TYPES, 1);
  private final DirectChoiceControl<Laterality> acuteLaterality =
      new DirectChoiceControl<>(LATERALITIES, 3);
  private final JTextField acuteLocation = new JTextField(18);
  private final DirectChoiceControl<Degree> chronicMicrovascularChange =
      new DirectChoiceControl<>(DEGREES, 3);
  private final DirectChoiceControl<Degree> cerebralVolumeLoss =
      new DirectChoiceControl<>(DEGREES, 3);
  private final DirectChoiceControl<SinusFindingType> sinusFindingType =
      new DirectChoiceControl<>(SINUS_FINDINGS, 1);
  private final DirectChoiceControl<Laterality> sinusLaterality =
      new DirectChoiceControl<>(LATERALITIES, 3);
  private final Map<SinusSite, JToggleButton> sinusSites = new EnumMap<>(SinusSite.class);
  private final DirectChoiceControl<Degree> sinusDegree = new DirectChoiceControl<>(DEGREES, 3);
  private final JTextField sinusSize = new JTextField(7);
  private final JToggleButton correlateForSinusitis = new JToggleButton("Correlate for sinusitis");
  private final JToggleButton anteriorFalxLipoma = new JToggleButton("Anterior falx lipoma");
  private final JTextField anteriorFalxLipomaSize = new JTextField(7);
  private final Map<TechnicalNote, JToggleButton> technicalNotes =
      new EnumMap<>(TechnicalNote.class);
  private final JTextArea freeText = new JTextArea(3, 24);
  private final JButton resetButton = iconButton(ActionIcon.RESET, "Clear brain form");
  private final JButton otherFindingButton = new JButton("Other Finding");
  private final JButton addButton = new JButton("Add Selected Findings");

  BrainFormPanel() {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(BorderFactory.createTitledBorder("Structured brain findings"));
    add(stretch(buildNormalPanel()));
    add(stretch(buildWhiteMatterPanel()));
    add(stretch(buildAcuteFindingsPanel()));
    add(stretch(buildBackgroundChangePanel()));
    add(stretch(buildSinusPanel()));
    add(stretch(buildIncidentalPanel()));
    add(stretch(buildTechnicalNotesPanel()));
    add(stretch(buildFreeTextPanel()));
    add(stretch(buildActions()));
    setAlignmentX(LEFT_ALIGNMENT);
    Dimension preferred = getPreferredSize();
    setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height));

    whiteMatterNonspecific.setSelected(true);
    sinusSites.get(SinusSite.MAXILLARY).setSelected(true);
    registerIntracranialSelectionListeners();
    normal.addActionListener(
        event -> {
          if (normal.isSelected()) {
            clearIntracranialSelections();
          }
        });
    acuteFindingType.addActionListener(event -> updateAcuteFindingState());
    sinusFindingType.addActionListener(event -> updateSinusState());
    anteriorFalxLipoma.addActionListener(event -> updateFalxLipomaState());
    resetButton.addActionListener(event -> clearSelections());
    updateAcuteFindingState();
    updateSinusState();
    updateFalxLipomaState();
  }

  void addSubmitListener(ActionListener listener) {
    addButton.addActionListener(listener);
  }

  void addOtherFindingListener(ActionListener listener) {
    otherFindingButton.addActionListener(listener);
  }

  Selection selection() {
    return new Selection(
        normal.isSelected(),
        new WhiteMatterSelection(
            whiteMatterQuantity.selectionOr(WhiteMatterQuantity.NONE),
            selected(whiteMatterDistributions),
            selected(whiteMatterRegions),
            whiteMatterMaximumSize.getText(),
            whiteMatterNonspecific.isSelected(),
            selected(whiteMatterEtiologies)),
        new AcuteFindingSelection(
            acuteFindingType.selectionOr(AcuteFindingType.NONE),
            acuteLaterality.selectionOr(Laterality.NONE),
            acuteLocation.getText()),
        chronicMicrovascularChange.selectionOr(Degree.NONE),
        cerebralVolumeLoss.selectionOr(Degree.NONE),
        new SinusSelection(
            sinusFindingType.selectionOr(SinusFindingType.NONE),
            sinusLaterality.selectionOr(Laterality.NONE),
            selected(sinusSites),
            sinusDegree.selectionOr(Degree.NONE),
            sinusSize.getText(),
            correlateForSinusitis.isSelected()),
        anteriorFalxLipoma.isSelected(),
        anteriorFalxLipomaSize.getText(),
        selected(technicalNotes),
        freeText.getText());
  }

  void clearSelections() {
    normal.setSelected(false);
    clearIntracranialSelections();
    sinusFindingType.clear();
    sinusLaterality.clear();
    sinusSites.values().forEach(button -> button.setSelected(false));
    sinusSites.get(SinusSite.MAXILLARY).setSelected(true);
    sinusDegree.clear();
    sinusSize.setText("");
    correlateForSinusitis.setSelected(false);
    technicalNotes.values().forEach(button -> button.setSelected(false));
    freeText.setText("");
    updateSinusState();
  }

  private void clearIntracranialSelections() {
    whiteMatterQuantity.clear();
    whiteMatterDistributions.values().forEach(button -> button.setSelected(false));
    whiteMatterRegions.values().forEach(button -> button.setSelected(false));
    whiteMatterMaximumSize.setText("");
    whiteMatterNonspecific.setSelected(true);
    whiteMatterEtiologies.values().forEach(button -> button.setSelected(false));
    acuteFindingType.clear();
    acuteLaterality.clear();
    acuteLocation.setText("");
    chronicMicrovascularChange.clear();
    cerebralVolumeLoss.clear();
    anteriorFalxLipoma.setSelected(false);
    anteriorFalxLipomaSize.setText("");
    updateAcuteFindingState();
    updateFalxLipomaState();
  }

  private JPanel buildNormalPanel() {
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 2));
    normal.setMargin(new Insets(2, 7, 2, 7));
    panel.add(normal);
    return panel;
  }

  private JPanel buildWhiteMatterPanel() {
    JPanel panel = choicePanel("T2/FLAIR white matter foci");
    addChoiceRow(panel, 0, "Quantity", whiteMatterQuantity);
    addToggleRow(
        panel,
        1,
        "Distribution",
        toggleGrid(WhiteMatterDistribution.values(), whiteMatterDistributions, 2));
    addToggleRow(panel, 2, "Location", toggleGrid(CerebralRegion.values(), whiteMatterRegions, 2));
    addTextRow(panel, 3, "Maximum size (mm)", whiteMatterMaximumSize);

    JPanel interpretation = new JPanel(new GridLayout(0, 2, 4, 2));
    whiteMatterNonspecific.setMargin(new Insets(2, 7, 2, 7));
    interpretation.add(whiteMatterNonspecific);
    for (WhiteMatterEtiology etiology : WhiteMatterEtiology.values()) {
      JToggleButton button = toggle(etiology.toString());
      whiteMatterEtiologies.put(etiology, button);
      interpretation.add(button);
    }
    addToggleRow(panel, 4, "Interpretation", interpretation);
    return panel;
  }

  private JPanel buildAcuteFindingsPanel() {
    JPanel panel = choicePanel("Acute and hemorrhagic findings");
    addChoiceRow(panel, 0, "Finding", acuteFindingType);
    addChoiceRow(panel, 1, "Side", acuteLaterality);
    addTextRow(panel, 2, "Location", acuteLocation);
    return panel;
  }

  private JPanel buildBackgroundChangePanel() {
    JPanel panel = choicePanel("Background change");
    addChoiceRow(panel, 0, "Chronic microvascular change", chronicMicrovascularChange);
    addChoiceRow(panel, 1, "Generalized cerebral volume loss", cerebralVolumeLoss);
    return panel;
  }

  private JPanel buildSinusPanel() {
    JPanel panel = choicePanel("Paranasal sinuses");
    addChoiceRow(panel, 0, "Finding", sinusFindingType);
    addChoiceRow(panel, 1, "Side", sinusLaterality);
    addToggleRow(panel, 2, "Sinus", toggleGrid(SinusSite.values(), sinusSites, 2));
    addChoiceRow(panel, 3, "Degree", sinusDegree);
    addTextRow(panel, 4, "Cyst size (include unit)", sinusSize);
    correlateForSinusitis.setMargin(new Insets(2, 7, 2, 7));
    addToggleRow(panel, 5, "", correlateForSinusitis);
    return panel;
  }

  private JPanel buildIncidentalPanel() {
    JPanel panel = choicePanel("Incidental intracranial finding");
    anteriorFalxLipoma.setMargin(new Insets(2, 7, 2, 7));
    addToggleRow(panel, 0, "", anteriorFalxLipoma);
    addTextRow(panel, 1, "Lipoma size (mm)", anteriorFalxLipomaSize);
    return panel;
  }

  private JPanel buildTechnicalNotesPanel() {
    JPanel panel = new JPanel(new GridLayout(0, 1, 4, 2));
    panel.setBorder(BorderFactory.createTitledBorder("Technical notes"));
    for (TechnicalNote note : TechnicalNote.values()) {
      JToggleButton button = toggle(note.toString());
      technicalNotes.put(note, button);
      panel.add(button);
    }
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

  private void registerIntracranialSelectionListeners() {
    ActionListener clearNormal = event -> normal.setSelected(false);
    whiteMatterQuantity.addActionListener(clearNormal);
    whiteMatterDistributions.values().forEach(button -> button.addActionListener(clearNormal));
    whiteMatterRegions.values().forEach(button -> button.addActionListener(clearNormal));
    whiteMatterEtiologies.values().forEach(button -> button.addActionListener(clearNormal));
    acuteFindingType.addActionListener(clearNormal);
    acuteLaterality.addActionListener(clearNormal);
    chronicMicrovascularChange.addActionListener(clearNormal);
    cerebralVolumeLoss.addActionListener(clearNormal);
    anteriorFalxLipoma.addActionListener(clearNormal);
  }

  private void updateAcuteFindingState() {
    boolean enabled = acuteFindingType.selectionOr(AcuteFindingType.NONE) != AcuteFindingType.NONE;
    acuteLaterality.setEnabled(enabled);
    acuteLocation.setEnabled(enabled);
  }

  private void updateSinusState() {
    SinusFindingType type = sinusFindingType.selectionOr(SinusFindingType.NONE);
    boolean enabled = type != SinusFindingType.NONE;
    sinusLaterality.setEnabled(enabled);
    sinusSites.values().forEach(button -> button.setEnabled(enabled));
    sinusDegree.setEnabled(type == SinusFindingType.MUCOSAL_THICKENING);
    sinusSize.setEnabled(type == SinusFindingType.RETENTION_CYST);
    correlateForSinusitis.setEnabled(enabled);
  }

  private void updateFalxLipomaState() {
    anteriorFalxLipomaSize.setEnabled(anteriorFalxLipoma.isSelected());
  }

  private static JPanel choicePanel(String title) {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(BorderFactory.createTitledBorder(title));
    return panel;
  }

  private static void addChoiceRow(
      JPanel panel, int row, String label, DirectChoiceControl<?> choice) {
    addToggleRow(panel, row, label, choice.panel());
  }

  private static void addTextRow(JPanel panel, int row, String label, JTextField field) {
    addToggleRow(panel, row, label, field);
  }

  private static void addToggleRow(JPanel panel, int row, String label, JComponent component) {
    GridBagConstraints constraints = constraints(row * 2);
    constraints.gridwidth = 2;
    if (!label.isBlank()) {
      panel.add(new JLabel(label), constraints);
    }
    constraints = constraints(row * 2 + 1);
    constraints.gridwidth = 2;
    constraints.weightx = 1.0;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.insets = new Insets(0, 15, 4, 3);
    panel.add(component, constraints);
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
      JToggleButton button = toggle(value.toString());
      buttons.put(value, button);
      panel.add(button);
    }
    return panel;
  }

  private static JToggleButton toggle(String text) {
    JToggleButton button = new JToggleButton(text);
    button.setMargin(new Insets(2, 7, 2, 7));
    return button;
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
}
