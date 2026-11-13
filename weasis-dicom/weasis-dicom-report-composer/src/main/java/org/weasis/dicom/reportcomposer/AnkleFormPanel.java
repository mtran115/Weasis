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
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.text.JTextComponent;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.dicom.reportcomposer.AnkleFindingBuilder.Bone;
import org.weasis.dicom.reportcomposer.AnkleFindingBuilder.Degree;
import org.weasis.dicom.reportcomposer.AnkleFindingBuilder.FluidAmount;
import org.weasis.dicom.reportcomposer.AnkleFindingBuilder.Ligament;
import org.weasis.dicom.reportcomposer.AnkleFindingBuilder.LigamentStatus;
import org.weasis.dicom.reportcomposer.AnkleFindingBuilder.Selection;
import org.weasis.dicom.reportcomposer.AnkleFindingBuilder.SwellingSide;
import org.weasis.dicom.reportcomposer.AnkleFindingBuilder.Tendon;

final class AnkleFormPanel extends JPanel implements ChordForm {
  static final String CHORD_REFERENCE =
      """
      ~           chord mode on / off

      Structure; stays until the next one
        1 Posterior tibial tenosynovitis
        2 Peroneus longus tenosynovitis
        3 Peroneus brevis tenosynovitis
        4 Flexor hallucis longus tenosynovitis
        5 Achilles tendinosis
        6 ATFL               7 AITFL
        8 Posterior joint effusion
        9 Soft tissue swelling
        0 Free text box

      d#          on a tendon (1 to 5)
                    1 mild  2 moderate  3 severe
                    4 minimal  0 clear
                  on effusion (8)
                    1 small  2 moderate  3 large
                    4 trace  0 clear

      On a ligament (6, 7)
      d1 d2       mild, moderate sprain
      p c         partial tear, complete tear

      On soft tissue swelling (9)
      m l         medial, lateral

      Bony contusion: b and a bone, then d#
        b1 Medial malleolus   b2 Lateral malleolus
        b3 Distal tibia       b4 Talus
        b5 Calcaneus          b6 Navicular
        b7 Cuboid             b8 Medial cuneiform
        b9 Middle cuneiform   b0 Lateral cuneiform
      d#          1 mild  2 moderate  3 severe
                    4 minimal  0 clear

      Backspace   remove the last key or token
      Esc         drop a half-typed token

      M, the numpad and arrows still go to the viewer.
      The other ankle findings are buttons only.
      """;

  private static final List<Degree> DEGREES =
      List.of(Degree.MINIMAL, Degree.MILD, Degree.MODERATE, Degree.SEVERE);
  private static final List<LigamentStatus> LIGAMENT_STATUSES =
      List.of(
          LigamentStatus.MILD_SPRAIN,
          LigamentStatus.MODERATE_SPRAIN,
          LigamentStatus.PARTIAL_TEAR,
          LigamentStatus.COMPLETE_TEAR);
  private static final List<FluidAmount> FLUID_AMOUNTS =
      List.of(FluidAmount.TRACE, FluidAmount.SMALL, FluidAmount.MODERATE, FluidAmount.LARGE);
  private static final List<Tendon> SPLIT_TEAR_TENDONS =
      List.of(Tendon.PERONEUS_LONGUS, Tendon.PERONEUS_BREVIS);

  private final JToggleButton normal = new FindingToggleButton("Normal ankle MRI");
  private final Map<Tendon, DirectChoiceControl<Degree>> tenosynovitis =
      new EnumMap<>(Tendon.class);
  private final Map<Tendon, JToggleButton> splitTears = new EnumMap<>(Tendon.class);
  private final DirectChoiceControl<Degree> achilles = new DirectChoiceControl<>(DEGREES, 4);
  private final Map<Ligament, DirectChoiceControl<LigamentStatus>> ligaments =
      new EnumMap<>(Ligament.class);
  private final DirectChoiceControl<FluidAmount> posteriorEffusion =
      new DirectChoiceControl<>(FLUID_AMOUNTS, 4);
  private final DirectChoiceControl<FluidAmount> anteriorEffusion =
      new DirectChoiceControl<>(FLUID_AMOUNTS, 4);
  private final Map<SwellingSide, JToggleButton> swelling = new EnumMap<>(SwellingSide.class);
  private final Map<Bone, DirectChoiceControl<Degree>> contusions = new EnumMap<>(Bone.class);
  private final JTextArea freeText = ComposerTextEditing.install(new JTextArea(3, 24));
  private final JButton resetButton = new JButton(ResourceUtil.getIcon(ActionIcon.RESET));
  private final JButton otherFindingButton = new JButton("Other Finding");
  private final JButton addButton = new JButton("Add Selected Findings");
  private final SectionJumpBar jumpBar;

  AnkleFormPanel() {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(BorderFactory.createTitledBorder("Structured ankle findings"));
    JPanel normalPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 2));
    normal.setMargin(new Insets(2, 7, 2, 7));
    normalPanel.add(normal);
    JPanel tendons = buildTendonsPanel();
    JPanel ligamentsPanel = buildLigamentsPanel();
    JPanel joint = buildJointPanel();
    JPanel bones = buildBonesPanel();
    JPanel freeTextPanel = buildFreeTextPanel();
    for (JPanel section :
        List.of(normalPanel, tendons, ligamentsPanel, joint, bones, freeTextPanel)) {
      add(FormRows.stretch(section));
    }
    add(FormRows.stretch(buildActions()));
    jumpBar =
        new SectionJumpBar(
            List.of(
                new SectionJumpBar.Section("Normal", normalPanel, List.of("normal")),
                new SectionJumpBar.Section(
                    "Tendons", tendons, List.of("tenosynovitis:", "split-tear:", "achilles")),
                new SectionJumpBar.Section("Ligaments", ligamentsPanel, List.of("ligament:")),
                new SectionJumpBar.Section(
                    "Joint/soft tissue", joint, List.of("effusion:", "swelling")),
                new SectionJumpBar.Section("Bones", bones, List.of("contusion:")),
                new SectionJumpBar.Section("Free text", freeTextPanel, List.of("free-text"))),
            () -> SectionJumpBar.keys(AnkleFindingBuilder.generateKeyed(selection())));
    setAlignmentX(LEFT_ALIGNMENT);
    setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));

    ActionListener clearNormal = event -> normal.setSelected(false);
    choiceControls().forEach(control -> control.addActionListener(clearNormal));
    toggles().forEach(button -> button.addActionListener(clearNormal));
    normal.addActionListener(
        event -> {
          if (normal.isSelected()) {
            clearPositiveSelections();
          }
        });
    resetButton.setToolTipText("Clear ankle form");
    resetButton.setPreferredSize(new Dimension(30, 28));
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
    tenosynovitis.forEach(
        (tendon, control) ->
            targets.add(
                new Target(tendon + " tenosynovitis", degreeButtons(control), Map.of(), null)));
    targets.add(new Target("Achilles tendinosis", degreeButtons(achilles), Map.of(), null));
    for (Ligament ligament : List.of(Ligament.ATFL, Ligament.AITFL)) {
      DirectChoiceControl<LigamentStatus> control = ligaments.get(ligament);
      Map<Character, JToggleButton> tears = new LinkedHashMap<>();
      tears.put('p', control.button(LigamentStatus.PARTIAL_TEAR));
      tears.put('c', control.button(LigamentStatus.COMPLETE_TEAR));
      targets.add(
          new Target(
              ligament.toString(),
              List.of(
                  control.button(LigamentStatus.MILD_SPRAIN),
                  control.button(LigamentStatus.MODERATE_SPRAIN)),
              tears,
              null));
    }
    targets.add(
        new Target(
            "Posterior joint effusion",
            List.of(
                posteriorEffusion.button(FluidAmount.SMALL),
                posteriorEffusion.button(FluidAmount.MODERATE),
                posteriorEffusion.button(FluidAmount.LARGE),
                posteriorEffusion.button(FluidAmount.TRACE)),
            Map.of(),
            null));
    Map<Character, JToggleButton> sides = new LinkedHashMap<>();
    sides.put('m', swelling.get(SwellingSide.MEDIAL));
    sides.put('l', swelling.get(SwellingSide.LATERAL));
    targets.add(new Target("Soft tissue swelling", List.of(), sides, null));
    return targets;
  }

  @Override
  public Map<Character, List<Target>> prefixedTargets() {
    List<Target> targets = new ArrayList<>();
    contusions.forEach(
        (bone, control) ->
            targets.add(
                new Target(
                    "Bony contusion, " + bone.phrase(), degreeButtons(control), Map.of(), null)));
    return Map.of('b', targets);
  }

  @Override
  public JTextComponent freeText() {
    return freeText;
  }

  Selection selection() {
    return new Selection(
        normal.isSelected(),
        chosen(tenosynovitis, Degree.NONE),
        FormRows.selected(splitTears),
        achilles.selectionOr(Degree.NONE),
        chosen(ligaments, LigamentStatus.NONE),
        posteriorEffusion.selectionOr(FluidAmount.NONE),
        anteriorEffusion.selectionOr(FluidAmount.NONE),
        FormRows.selected(swelling),
        chosen(contusions, Degree.NONE),
        freeText.getText());
  }

  void clearSelections() {
    normal.setSelected(false);
    clearPositiveSelections();
    freeText.setText("");
  }

  private void clearPositiveSelections() {
    choiceControls().forEach(DirectChoiceControl::clear);
    toggles().forEach(button -> button.setSelected(false));
  }

  private List<DirectChoiceControl<?>> choiceControls() {
    List<DirectChoiceControl<?>> controls = new ArrayList<>(tenosynovitis.values());
    controls.add(achilles);
    controls.addAll(ligaments.values());
    controls.add(posteriorEffusion);
    controls.add(anteriorEffusion);
    controls.addAll(contusions.values());
    return controls;
  }

  private List<JToggleButton> toggles() {
    List<JToggleButton> buttons = new ArrayList<>(splitTears.values());
    buttons.addAll(swelling.values());
    return buttons;
  }

  private JPanel buildTendonsPanel() {
    JPanel panel = FormRows.rowsPanel("Tendons");
    int row = 0;
    for (Tendon tendon : Tendon.values()) {
      DirectChoiceControl<Degree> control = new DirectChoiceControl<>(DEGREES, 4);
      tenosynovitis.put(tendon, control);
      FormRows.addRow(panel, row++, tendon + " tenosynovitis", control.panel());
    }
    FormRows.addRow(panel, row++, "Achilles tendinosis", achilles.panel());
    FormRows.addRow(
        panel,
        row,
        "Partial split tear",
        FormRows.toggleRow(SPLIT_TEAR_TENDONS, splitTears, Tendon::toString));
    return panel;
  }

  private JPanel buildLigamentsPanel() {
    JPanel panel = FormRows.rowsPanel("Ligaments");
    int row = 0;
    for (Ligament ligament : Ligament.values()) {
      DirectChoiceControl<LigamentStatus> control = new DirectChoiceControl<>(LIGAMENT_STATUSES, 2);
      ligaments.put(ligament, control);
      FormRows.addRow(panel, row++, ligament.toString(), control.panel());
    }
    return panel;
  }

  private JPanel buildJointPanel() {
    JPanel panel = FormRows.rowsPanel("Joint and soft tissues");
    FormRows.addRow(panel, 0, "Posterior joint effusion", posteriorEffusion.panel());
    FormRows.addRow(panel, 1, "Anterior joint effusion", anteriorEffusion.panel());
    FormRows.addRow(
        panel,
        2,
        "Soft tissue swelling",
        FormRows.toggleRow(List.of(SwellingSide.values()), swelling, SwellingSide::toString));
    return panel;
  }

  private JPanel buildBonesPanel() {
    JPanel panel = FormRows.rowsPanel("Bony contusion");
    int row = 0;
    for (Bone bone : Bone.values()) {
      DirectChoiceControl<Degree> control = new DirectChoiceControl<>(DEGREES, 4);
      contusions.put(bone, control);
      FormRows.addInlineRow(panel, row++, bone.toString(), control.panel());
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

  /** The buttons for d1 to d4: mild, moderate, severe, minimal. */
  private static List<JToggleButton> degreeButtons(DirectChoiceControl<Degree> control) {
    return List.of(
        control.button(Degree.MILD),
        control.button(Degree.MODERATE),
        control.button(Degree.SEVERE),
        control.button(Degree.MINIMAL));
  }

  private static <K extends Enum<K>, V> Map<K, V> chosen(
      Map<K, DirectChoiceControl<V>> controls, V none) {
    Map<K, V> values = new LinkedHashMap<>();
    controls.forEach((key, control) -> values.put(key, control.selectionOr(none)));
    return values;
  }
}
