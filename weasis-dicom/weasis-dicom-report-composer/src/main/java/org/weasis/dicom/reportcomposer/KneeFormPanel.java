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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
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
import javax.swing.text.JTextComponent;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.dicom.reportcomposer.KneeFindingBuilder.Compartment;
import org.weasis.dicom.reportcomposer.KneeFindingBuilder.Degree;
import org.weasis.dicom.reportcomposer.KneeFindingBuilder.ExtensorTendon;
import org.weasis.dicom.reportcomposer.KneeFindingBuilder.FluidAmount;
import org.weasis.dicom.reportcomposer.KneeFindingBuilder.Ligament;
import org.weasis.dicom.reportcomposer.KneeFindingBuilder.LigamentStatus;
import org.weasis.dicom.reportcomposer.KneeFindingBuilder.MarrowFindingType;
import org.weasis.dicom.reportcomposer.KneeFindingBuilder.MarrowLocation;
import org.weasis.dicom.reportcomposer.KneeFindingBuilder.MarrowSelection;
import org.weasis.dicom.reportcomposer.KneeFindingBuilder.Meniscus;
import org.weasis.dicom.reportcomposer.KneeFindingBuilder.MeniscusFinding;
import org.weasis.dicom.reportcomposer.KneeFindingBuilder.MeniscusRegion;
import org.weasis.dicom.reportcomposer.KneeFindingBuilder.MeniscusSelection;
import org.weasis.dicom.reportcomposer.KneeFindingBuilder.Selection;
import org.weasis.dicom.reportcomposer.KneeFindingBuilder.SoftTissueLocation;
import org.weasis.dicom.reportcomposer.KneeFindingBuilder.TendonStatus;

final class KneeFormPanel extends JPanel implements ChordForm {
  static final String CHORD_REFERENCE =
      """
      ~           chord mode on / off

      Structure; stays until the next one
        1 Medial meniscus    2 Lateral meniscus
        3 Quadriceps tendon  4 Patellar tendon
        5 Joint effusion
        6 Prepatellar/pretibial soft tissue edema
        7 Popliteal cyst
        8 Lateral femoral condyle friction
            (the digit alone sets it)
        0 Free text box

      On a meniscus (1, 2)
      i t         intrasubstance degeneration, tear
      a b p       anterior horn, body, posterior horn
      r           extending to the posterior root
                    (tear only)
      x           extras text

      d#          on a tendon (3, 4): tendinosis
                    1 mild  2 moderate  3 severe
                    4 minimal  0 clear
                  on effusion or cyst (5, 7)
                    1 small  2 moderate  3 large
                    4 trace  0 clear
                  on soft tissue edema (6)
                    1 mild  2 moderate  3 severe
                    4 minimal  0 clear

      On soft tissue edema (6)
      p t         prepatellar, pretibial

      Backspace   remove the last key or token
      Esc         drop a half-typed token

      M, the numpad and arrows still go to the viewer.
      The other knee findings are buttons only.
      """;

  private static final List<LigamentStatus> LIGAMENT_STATUSES =
      List.of(
          LigamentStatus.MILD_SPRAIN,
          LigamentStatus.MODERATE_SPRAIN,
          LigamentStatus.PARTIAL_TEAR,
          LigamentStatus.COMPLETE_TEAR,
          LigamentStatus.SCARRING);
  private static final List<TendonStatus> TENDON_STATUSES =
      List.of(
          TendonStatus.MINIMAL_TENDINOSIS,
          TendonStatus.MILD_TENDINOSIS,
          TendonStatus.MODERATE_TENDINOSIS,
          TendonStatus.SEVERE_TENDINOSIS,
          TendonStatus.PARTIAL_TEAR,
          TendonStatus.COMPLETE_TEAR);
  private static final List<Degree> DEGREES =
      List.of(Degree.MINIMAL, Degree.MILD, Degree.MODERATE, Degree.SEVERE);
  private static final List<FluidAmount> FLUID_AMOUNTS =
      List.of(FluidAmount.TRACE, FluidAmount.SMALL, FluidAmount.MODERATE, FluidAmount.LARGE);

  private final JToggleButton normal = new FindingToggleButton("Normal knee MRI");
  private final Map<Meniscus, MeniscusControls> menisci = new EnumMap<>(Meniscus.class);
  private final Map<Ligament, DirectChoiceControl<LigamentStatus>> ligaments =
      new EnumMap<>(Ligament.class);
  private final JToggleButton aclReconstruction = new FindingToggleButton("ACL reconstruction");
  private final Map<ExtensorTendon, DirectChoiceControl<TendonStatus>> extensorMechanism =
      new EnumMap<>(ExtensorTendon.class);
  private final Map<Compartment, DirectChoiceControl<Degree>> osteoarthrosis =
      new EnumMap<>(Compartment.class);
  private final DirectChoiceControl<MarrowFindingType> marrowType =
      new DirectChoiceControl<>(List.of(MarrowFindingType.EDEMA, MarrowFindingType.CONTUSION), 2);
  private final DirectChoiceControl<Degree> marrowDegree = new DirectChoiceControl<>(DEGREES, 2);
  private final Map<MarrowLocation, JToggleButton> marrowLocations =
      new EnumMap<>(MarrowLocation.class);
  private final JToggleButton subchondral = new FindingToggleButton("Subchondral");
  private final DirectChoiceControl<FluidAmount> effusion =
      new DirectChoiceControl<>(FLUID_AMOUNTS, 2);
  private final JToggleButton synovitis = new FindingToggleButton("Synovitis");
  private final DirectChoiceControl<FluidAmount> poplitealCyst =
      new DirectChoiceControl<>(FLUID_AMOUNTS, 2);
  private final DirectChoiceControl<Degree> prepatellarBursitis =
      new DirectChoiceControl<>(DEGREES, 2);
  private final DirectChoiceControl<Degree> softTissueEdema = new DirectChoiceControl<>(DEGREES, 2);
  private final Map<SoftTissueLocation, JToggleButton> softTissueLocations =
      new EnumMap<>(SoftTissueLocation.class);
  private final JToggleButton lateralCondyleFriction =
      new FindingToggleButton("Lateral femoral condyle friction");
  private final JTextArea freeText = ComposerTextEditing.install(new JTextArea(3, 24));
  private final JButton resetButton = iconButton(ActionIcon.RESET, "Clear knee form");
  private final JButton otherFindingButton = new JButton("Other Finding");
  private final JButton addButton = new JButton("Add Selected Findings");

  private final SectionJumpBar jumpBar;

  KneeFormPanel() {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(BorderFactory.createTitledBorder("Structured knee findings"));
    JPanel normalPanel = buildNormalPanel();
    JPanel menisci = buildMenisciPanel();
    JPanel ligaments = buildLigamentsPanel();
    JPanel extensor = buildExtensorPanel();
    JPanel osteoarthrosis = buildOsteoarthrosisPanel();
    JPanel marrow = buildMarrowPanel();
    JPanel jointAndSoftTissue = buildJointAndSoftTissuePanel();
    JPanel freeTextPanel = buildFreeTextPanel();
    for (JPanel section :
        List.of(
            normalPanel,
            menisci,
            ligaments,
            extensor,
            osteoarthrosis,
            marrow,
            jointAndSoftTissue,
            freeTextPanel)) {
      add(stretch(section));
    }
    add(stretch(buildActions()));
    jumpBar =
        new SectionJumpBar(
            List.of(
                new SectionJumpBar.Section("Normal", normalPanel, List.of("normal")),
                new SectionJumpBar.Section("Menisci", menisci, List.of("meniscus:")),
                new SectionJumpBar.Section("Ligaments", ligaments, List.of("ligament:")),
                new SectionJumpBar.Section("Extensor", extensor, List.of("extensor:")),
                new SectionJumpBar.Section(
                    "Osteoarthrosis", osteoarthrosis, List.of("osteoarthrosis:")),
                new SectionJumpBar.Section("Marrow", marrow, List.of("marrow")),
                new SectionJumpBar.Section(
                    "Joint/soft tissue",
                    jointAndSoftTissue,
                    List.of(
                        "effusion",
                        "synovitis",
                        "popliteal-cyst",
                        "prepatellar-bursitis",
                        "soft-tissue-edema",
                        "condyle-friction")),
                new SectionJumpBar.Section("Free text", freeTextPanel, List.of("free-text"))),
            () -> SectionJumpBar.keys(KneeFindingBuilder.generateKeyed(selection())));
    setAlignmentX(LEFT_ALIGNMENT);
    Dimension preferred = getPreferredSize();
    setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height));

    marrowType.select(MarrowFindingType.EDEMA);
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

  SectionJumpBar jumpBar() {
    return jumpBar;
  }

  @Override
  public List<Target> chordTargets() {
    List<Target> targets = new ArrayList<>();
    menisci.forEach(
        (meniscus, controls) -> targets.add(controls.chordTarget(meniscus + " meniscus")));
    extensorMechanism.forEach(
        (tendon, control) ->
            targets.add(
                new Target(
                    tendon + " tendon",
                    buttons(
                        control,
                        TendonStatus.MILD_TENDINOSIS,
                        TendonStatus.MODERATE_TENDINOSIS,
                        TendonStatus.SEVERE_TENDINOSIS,
                        TendonStatus.MINIMAL_TENDINOSIS),
                    Map.of(),
                    null)));
    targets.add(new Target("Joint effusion", fluidButtons(effusion), Map.of(), null));
    Map<Character, JToggleButton> edemaLocations = new LinkedHashMap<>();
    edemaLocations.put('p', softTissueLocations.get(SoftTissueLocation.PREPATELLAR));
    edemaLocations.put('t', softTissueLocations.get(SoftTissueLocation.PRETIBIAL));
    targets.add(
        new Target(
            "Soft tissue edema",
            buttons(softTissueEdema, Degree.MILD, Degree.MODERATE, Degree.SEVERE, Degree.MINIMAL),
            edemaLocations,
            null));
    targets.add(new Target("Popliteal cyst", fluidButtons(poplitealCyst), Map.of(), null));
    targets.add(
        new Target(
            lateralCondyleFriction.getText(),
            List.of(),
            Map.of(FormChordEngine.WHOLE, lateralCondyleFriction),
            null));
    return targets;
  }

  @Override
  public JTextComponent freeText() {
    return freeText;
  }

  Selection selection() {
    EnumMap<Meniscus, MeniscusSelection> selectedMenisci = new EnumMap<>(Meniscus.class);
    menisci.forEach(
        (meniscus, controls) -> {
          MeniscusSelection selection = controls.selection();
          if (selection.hasFinding()) {
            selectedMenisci.put(meniscus, selection);
          }
        });
    EnumMap<Ligament, LigamentStatus> selectedLigaments = new EnumMap<>(Ligament.class);
    ligaments.forEach(
        (ligament, control) -> {
          LigamentStatus status = control.selectionOr(LigamentStatus.NONE);
          if (status != LigamentStatus.NONE) {
            selectedLigaments.put(ligament, status);
          }
        });
    EnumMap<ExtensorTendon, TendonStatus> selectedTendons = new EnumMap<>(ExtensorTendon.class);
    extensorMechanism.forEach(
        (tendon, control) -> {
          TendonStatus status = control.selectionOr(TendonStatus.NONE);
          if (status != TendonStatus.NONE) {
            selectedTendons.put(tendon, status);
          }
        });
    EnumMap<Compartment, Degree> selectedOsteoarthrosis = new EnumMap<>(Compartment.class);
    osteoarthrosis.forEach(
        (compartment, control) -> {
          Degree degree = control.selectionOr(Degree.NONE);
          if (degree != Degree.NONE) {
            selectedOsteoarthrosis.put(compartment, degree);
          }
        });
    List<MarrowLocation> selectedMarrowLocations = selected(marrowLocations);
    List<SoftTissueLocation> selectedSoftTissueLocations = selected(softTissueLocations);

    return new Selection(
        normal.isSelected(),
        selectedMenisci,
        selectedLigaments,
        aclReconstruction.isSelected(),
        selectedTendons,
        selectedOsteoarthrosis,
        new MarrowSelection(
            marrowType.selectionOr(MarrowFindingType.EDEMA),
            marrowDegree.selectionOr(Degree.NONE),
            selectedMarrowLocations,
            subchondral.isSelected()),
        effusion.selectionOr(FluidAmount.NONE),
        synovitis.isSelected(),
        poplitealCyst.selectionOr(FluidAmount.NONE),
        prepatellarBursitis.selectionOr(Degree.NONE),
        softTissueEdema.selectionOr(Degree.NONE),
        selectedSoftTissueLocations,
        lateralCondyleFriction.isSelected(),
        freeText.getText());
  }

  void clearSelections() {
    normal.setSelected(false);
    clearPositiveSelections();
    freeText.setText("");
  }

  private void clearPositiveSelections() {
    menisci.values().forEach(MeniscusControls::clear);
    ligaments.values().forEach(DirectChoiceControl::clear);
    aclReconstruction.setSelected(false);
    extensorMechanism.values().forEach(DirectChoiceControl::clear);
    osteoarthrosis.values().forEach(DirectChoiceControl::clear);
    marrowType.clear();
    marrowType.select(MarrowFindingType.EDEMA);
    marrowDegree.clear();
    marrowLocations.values().forEach(button -> button.setSelected(false));
    subchondral.setSelected(false);
    effusion.clear();
    synovitis.setSelected(false);
    poplitealCyst.clear();
    prepatellarBursitis.clear();
    softTissueEdema.clear();
    softTissueLocations.values().forEach(button -> button.setSelected(false));
    lateralCondyleFriction.setSelected(false);
  }

  private JPanel buildNormalPanel() {
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 2));
    normal.setMargin(new Insets(2, 7, 2, 7));
    panel.add(normal);
    return panel;
  }

  private JPanel buildMenisciPanel() {
    JPanel panel = verticalPanel("Menisci");
    for (Meniscus meniscus : Meniscus.values()) {
      MeniscusControls controls = new MeniscusControls(meniscus);
      menisci.put(meniscus, controls);
      panel.add(stretch(controls.panel()));
    }
    return panel;
  }

  private JPanel buildLigamentsPanel() {
    JPanel panel = verticalPanel("Ligaments");
    for (Ligament ligament : Ligament.values()) {
      DirectChoiceControl<LigamentStatus> control = new DirectChoiceControl<>(LIGAMENT_STATUSES, 2);
      ligaments.put(ligament, control);
      JPanel ligamentPanel = choicePanel(ligament.toString());
      addChoiceRow(ligamentPanel, 0, "Injury", control);
      if (ligament == Ligament.ACL) {
        aclReconstruction.setMargin(new Insets(2, 7, 2, 7));
        GridBagConstraints constraints = constraints(2);
        constraints.gridx = 1;
        constraints.gridwidth = 2;
        ligamentPanel.add(aclReconstruction, constraints);
      }
      panel.add(stretch(ligamentPanel));
    }
    return panel;
  }

  private JPanel buildExtensorPanel() {
    JPanel panel = choicePanel("Extensor mechanism");
    int row = 0;
    for (ExtensorTendon tendon : ExtensorTendon.values()) {
      DirectChoiceControl<TendonStatus> control = new DirectChoiceControl<>(TENDON_STATUSES, 2);
      extensorMechanism.put(tendon, control);
      addChoiceRow(panel, row++, tendon.toString(), control);
    }
    return panel;
  }

  private JPanel buildOsteoarthrosisPanel() {
    JPanel panel = choicePanel("Osteoarthrosis");
    int row = 0;
    for (Compartment compartment : Compartment.values()) {
      DirectChoiceControl<Degree> control = new DirectChoiceControl<>(DEGREES, 2);
      osteoarthrosis.put(compartment, control);
      addChoiceRow(panel, row++, compartment.toString(), control);
    }
    return panel;
  }

  private JPanel buildMarrowPanel() {
    JPanel panel = choicePanel("Bone marrow");
    addChoiceRow(panel, 0, "Finding", marrowType);
    addChoiceRow(panel, 1, "Degree", marrowDegree);

    GridBagConstraints constraints = constraints(4);
    constraints.gridwidth = 2;
    panel.add(new JLabel("Location"), constraints);
    JPanel locations = toggleGrid(MarrowLocation.values(), marrowLocations, 2);
    constraints = constraints(5);
    constraints.gridwidth = 2;
    constraints.weightx = 1.0;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.insets = new Insets(0, 15, 4, 3);
    panel.add(locations, constraints);

    subchondral.setMargin(new Insets(2, 7, 2, 7));
    constraints = constraints(6);
    constraints.gridx = 1;
    constraints.gridwidth = 2;
    panel.add(subchondral, constraints);
    return panel;
  }

  private JPanel buildJointAndSoftTissuePanel() {
    JPanel panel = choicePanel("Joint, cysts, and soft tissues");
    addChoiceRow(panel, 0, "Joint effusion", effusion);
    addChoiceRow(panel, 1, "Popliteal cyst", poplitealCyst);
    addChoiceRow(panel, 2, "Prepatellar bursitis", prepatellarBursitis);
    addChoiceRow(panel, 3, "Anterior soft tissue edema", softTissueEdema);

    GridBagConstraints constraints = constraints(8);
    constraints.gridwidth = 2;
    panel.add(new JLabel("Edema location"), constraints);
    JPanel locations = toggleGrid(SoftTissueLocation.values(), softTissueLocations, 2);
    constraints = constraints(9);
    constraints.gridwidth = 2;
    constraints.weightx = 1.0;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.insets = new Insets(0, 15, 4, 3);
    panel.add(locations, constraints);

    synovitis.setMargin(new Insets(2, 7, 2, 7));
    constraints = constraints(10);
    constraints.gridx = 1;
    constraints.gridwidth = 2;
    panel.add(synovitis, constraints);

    lateralCondyleFriction.setMargin(new Insets(2, 7, 2, 7));
    constraints = constraints(11);
    constraints.gridx = 1;
    constraints.gridwidth = 2;
    panel.add(lateralCondyleFriction, constraints);
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
    menisci.values().forEach(controls -> controls.addActionListener(clearNormal));
    ligaments.values().forEach(control -> control.addActionListener(clearNormal));
    aclReconstruction.addActionListener(clearNormal);
    extensorMechanism.values().forEach(control -> control.addActionListener(clearNormal));
    osteoarthrosis.values().forEach(control -> control.addActionListener(clearNormal));
    marrowType.addActionListener(clearNormal);
    marrowDegree.addActionListener(clearNormal);
    marrowLocations.values().forEach(button -> button.addActionListener(clearNormal));
    subchondral.addActionListener(clearNormal);
    effusion.addActionListener(clearNormal);
    synovitis.addActionListener(clearNormal);
    poplitealCyst.addActionListener(clearNormal);
    prepatellarBursitis.addActionListener(clearNormal);
    softTissueEdema.addActionListener(clearNormal);
    softTissueLocations.values().forEach(button -> button.addActionListener(clearNormal));
    lateralCondyleFriction.addActionListener(clearNormal);
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

  @SafeVarargs
  private static <T> List<JToggleButton> buttons(DirectChoiceControl<T> control, T... choices) {
    return List.of(choices).stream().map(control::button).toList();
  }

  private static List<JToggleButton> fluidButtons(DirectChoiceControl<FluidAmount> control) {
    return buttons(
        control, FluidAmount.SMALL, FluidAmount.MODERATE, FluidAmount.LARGE, FluidAmount.TRACE);
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

  private static final class MeniscusControls {
    private final JPanel panel = new JPanel();
    private final DirectChoiceControl<MeniscusFinding> finding =
        new DirectChoiceControl<>(List.of(MeniscusFinding.DEGENERATION, MeniscusFinding.TEAR), 2);
    private final Map<MeniscusRegion, JToggleButton> regions = new EnumMap<>(MeniscusRegion.class);
    private final JToggleButton posteriorRoot =
        new FindingToggleButton("Extending to the posterior root");
    private final JTextField extras = ComposerTextEditing.install(new JTextField(10));

    MeniscusControls(Meniscus meniscus) {
      panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
      panel.setBorder(BorderFactory.createTitledBorder(meniscus + " meniscus"));
      panel.add(finding.panel());

      JPanel regionPanel = new JPanel(new BorderLayout(4, 2));
      regionPanel.setBorder(GuiUtils.getEmptyBorder(2, 0, 2, 0));
      regionPanel.add(toggleGrid(MeniscusRegion.values(), regions, 3), BorderLayout.CENTER);
      panel.add(regionPanel);

      JPanel rootPanel = new JPanel(new GridLayout(1, 1));
      posteriorRoot.setMargin(new Insets(2, 7, 2, 7));
      rootPanel.add(posteriorRoot);
      panel.add(rootPanel);

      JPanel extrasRow = new JPanel(new BorderLayout(4, 0));
      extrasRow.setBorder(GuiUtils.getEmptyBorder(2, 0, 0, 0));
      extrasRow.add(new JLabel("Extras"), BorderLayout.WEST);
      extrasRow.add(extras, BorderLayout.CENTER);
      panel.add(extrasRow);

      finding.addActionListener(event -> refresh());
      refresh();
    }

    JPanel panel() {
      return panel;
    }

    MeniscusSelection selection() {
      return new MeniscusSelection(
          finding.selectionOr(MeniscusFinding.NONE),
          selected(regions),
          posteriorRoot.isSelected(),
          extras.getText());
    }

    Target chordTarget(String name) {
      Map<Character, JToggleButton> keys = new LinkedHashMap<>();
      keys.put('i', finding.button(MeniscusFinding.DEGENERATION));
      keys.put('t', finding.button(MeniscusFinding.TEAR));
      keys.put('a', regions.get(MeniscusRegion.ANTERIOR_HORN));
      keys.put('b', regions.get(MeniscusRegion.BODY));
      keys.put('p', regions.get(MeniscusRegion.POSTERIOR_HORN));
      keys.put('r', posteriorRoot);
      return new Target(name, List.of(), keys, extras);
    }

    void clear() {
      finding.clear();
      regions.values().forEach(button -> button.setSelected(false));
      extras.setText("");
      refresh();
    }

    void addActionListener(ActionListener listener) {
      finding.addActionListener(listener);
      regions.values().forEach(button -> button.addActionListener(listener));
      posteriorRoot.addActionListener(listener);
    }

    /** The root extension needs a tear, and the extras a finding to follow. */
    private void refresh() {
      MeniscusFinding selected = finding.selectionOr(MeniscusFinding.NONE);
      boolean tear = selected == MeniscusFinding.TEAR;
      if (!tear) {
        posteriorRoot.setSelected(false);
      }
      posteriorRoot.setEnabled(tear);
      extras.setEnabled(selected != MeniscusFinding.NONE);
    }
  }
}
