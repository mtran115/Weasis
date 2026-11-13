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
import org.weasis.dicom.reportcomposer.WristFindingBuilder.Bone;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.BoneSelection;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.CystSelection;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.Degree;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.Ligament;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.LigamentStatus;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.LigamentTear;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.LimitedExam;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.Selection;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.Tendon;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.TendonSelection;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.TendonTear;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.TfccTear;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.WristSide;

final class WristFormPanel extends JPanel implements ChordForm {
  static final String CHORD_REFERENCE =
      """
      ~           chord mode on / off

      Structure; stays until the next one
        1 Scapholunate ligament
        2 TFCC
        3 Ganglion or synovial cyst
        4 Soft tissue swelling
        5 Limited exam
        0 Free text box

      On the scapholunate ligament (1)
      t p         tear, partial tear
      On the TFCC (2)
      t r u       tear, radial attachment tear,
                    ulnar attachment tear
      On the cyst (3)
      v d r       volar, dorsal, radial
      x           size box; Tab goes on to level
      On soft tissue swelling (4)
      d v         dorsal, volar
      On limited exam (5)
      m a         motion, artifact

      A bone: b and a digit
        b1 Distal radius   b2 Distal ulna
        b3 Scaphoid        b4 Lunate
        b5 Triquetrum      b6 Trapezium
        b7 Trapezoid       b8 Capitate
        b9 Hamate          b0 2nd metacarpal base
      d#          bony contusion
                    1 mild  2 moderate  3 severe
                    4 minimal  0 clear
      f           nondisplaced fracture
      c           cystic changes
      o           marrow edema, may be related to
                    contusion or osteoarthrosis
      x           extras text

      An extensor tendon: e and a digit
        e1 APL    e2 EPB    e3 ECRL   e4 ECRB
        e5 EPL    e6 Extensor digitorum
        e7 ECU
      d#          tenosynovitis, as for a bone
      s p         split tear, partial tear (ECU)

      Backspace   remove the last key or token
      Esc         drop a half-typed token

      M, the numpad and arrows still go to the viewer.
      """;

  private static final List<Degree> DEGREES =
      List.of(Degree.MINIMAL, Degree.MILD, Degree.MODERATE, Degree.SEVERE);
  private static final List<WristSide> SWELLING_SIDES = List.of(WristSide.DORSAL, WristSide.VOLAR);

  private final JToggleButton normal = new FindingToggleButton("Normal wrist MRI");
  private final DirectChoiceControl<LimitedExam> limitedExam =
      new DirectChoiceControl<>(List.of(LimitedExam.MOTION, LimitedExam.ARTIFACT), 2);
  private final Map<Ligament, TearControls<LigamentStatus>> ligaments =
      new EnumMap<>(Ligament.class);
  private final TearControls<TfccTear> tfcc =
      new TearControls<>(
          List.of(TfccTear.TEAR, TfccTear.RADIAL_ATTACHMENT, TfccTear.ULNAR_ATTACHMENT), 3);
  private final Map<Tendon, DirectChoiceControl<Degree>> tenosynovitis =
      new EnumMap<>(Tendon.class);
  private final DirectChoiceControl<TendonTear> ecuTear =
      new DirectChoiceControl<>(
          List.of(TendonTear.SPLIT, TendonTear.PARTIAL, TendonTear.COMPLETE), 3);
  private final Map<Bone, BoneControls> bones = new EnumMap<>(Bone.class);
  private final Map<WristSide, JToggleButton> cystSides = new EnumMap<>(WristSide.class);
  private final JTextField cystSize = ComposerTextEditing.install(new JTextField(5));
  private final JTextField cystLevel = ComposerTextEditing.install(new JTextField(12));
  private final Map<WristSide, JToggleButton> swelling = new EnumMap<>(WristSide.class);
  private final JTextArea freeText = ComposerTextEditing.install(new JTextArea(3, 24));
  private final JButton resetButton = new JButton(ResourceUtil.getIcon(ActionIcon.RESET));
  private final JButton otherFindingButton = new JButton("Other Finding");
  private final JButton addButton = new JButton("Add Selected Findings");

  WristFormPanel() {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(BorderFactory.createTitledBorder("Structured wrist findings"));
    JPanel top = FormRows.rowsPanel("Overall");
    normal.setMargin(new Insets(2, 7, 2, 7));
    JPanel normalRow = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
    normalRow.add(normal);
    FormRows.addRow(top, 0, "Normal", normalRow);
    FormRows.addRow(top, 1, "Limited exam", limitedExam.panel());
    for (JPanel section :
        List.of(
            top,
            buildLigamentsPanel(),
            buildTendonsPanel(),
            buildBonesPanel(),
            buildSoftTissuePanel(),
            buildFreeTextPanel())) {
      add(FormRows.stretch(section));
    }
    otherFindingButton.setIcon(ResourceUtil.getIcon(ActionIcon.DRAW_TEXT));
    addButton.setIcon(ResourceUtil.getIcon(ActionIcon.PLUS));
    add(
        FormRows.stretch(
            GuiUtils.getFlowLayoutPanel(
                FlowLayout.TRAILING, 5, 5, resetButton, otherFindingButton, addButton)));
    setAlignmentX(LEFT_ALIGNMENT);
    setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));

    ActionListener clearNormal = event -> normal.setSelected(false);
    positiveChoices().forEach(control -> control.addActionListener(clearNormal));
    positiveToggles().forEach(button -> button.addActionListener(clearNormal));
    cystSides.values().forEach(button -> button.addActionListener(event -> refreshCyst()));
    normal.addActionListener(
        event -> {
          if (normal.isSelected()) {
            clearPositiveSelections();
          }
        });
    resetButton.setToolTipText("Clear wrist form");
    resetButton.setPreferredSize(new Dimension(30, 28));
    resetButton.addActionListener(event -> clearSelections());
    refreshCyst();
  }

  void addSubmitListener(ActionListener listener) {
    addButton.addActionListener(listener);
  }

  void addOtherFindingListener(ActionListener listener) {
    otherFindingButton.addActionListener(listener);
  }

  @Override
  public List<Target> chordTargets() {
    TearControls<LigamentStatus> scapholunate = ligaments.get(Ligament.SCAPHOLUNATE);
    return List.of(
        new Target(
            "Scapholunate ligament",
            List.of(),
            keys(
                't', scapholunate.tear.button(LigamentStatus.TEAR),
                'p', scapholunate.tear.button(LigamentStatus.PARTIAL_TEAR)),
            null),
        new Target(
            "TFCC",
            List.of(),
            keys(
                't', tfcc.tear.button(TfccTear.TEAR),
                'r', tfcc.tear.button(TfccTear.RADIAL_ATTACHMENT),
                'u', tfcc.tear.button(TfccTear.ULNAR_ATTACHMENT)),
            null),
        new Target(
            "Ganglion or synovial cyst",
            List.of(),
            keys(
                'v', cystSides.get(WristSide.VOLAR),
                'd', cystSides.get(WristSide.DORSAL),
                'r', cystSides.get(WristSide.RADIAL)),
            cystSize),
        new Target(
            "Soft tissue swelling",
            List.of(),
            keys('d', swelling.get(WristSide.DORSAL), 'v', swelling.get(WristSide.VOLAR)),
            null),
        new Target(
            "Limited exam",
            List.of(),
            keys(
                'm', limitedExam.button(LimitedExam.MOTION),
                'a', limitedExam.button(LimitedExam.ARTIFACT)),
            null));
  }

  @Override
  public Map<Character, List<Target>> prefixedTargets() {
    List<Target> boneTargets = new ArrayList<>();
    bones.forEach((bone, controls) -> boneTargets.add(controls.chordTarget(bone)));
    List<Target> tendonTargets = new ArrayList<>();
    tenosynovitis.forEach(
        (tendon, control) ->
            tendonTargets.add(
                new Target(
                    tendon + " tendon",
                    degreeButtons(control),
                    tendon == Tendon.EXTENSOR_CARPI_ULNARIS
                        ? keys(
                            's', ecuTear.button(TendonTear.SPLIT),
                            'p', ecuTear.button(TendonTear.PARTIAL))
                        : Map.of(),
                    null)));
    return Map.of('b', boneTargets, 'e', tendonTargets);
  }

  @Override
  public JTextComponent freeText() {
    return freeText;
  }

  Selection selection() {
    Map<Ligament, LigamentTear> selectedLigaments = new EnumMap<>(Ligament.class);
    ligaments.forEach(
        (ligament, controls) ->
            selectedLigaments.put(
                ligament,
                new LigamentTear(
                    controls.tear.selectionOr(LigamentStatus.NONE),
                    controls.possible.isSelected())));
    Map<Tendon, TendonSelection> selectedTendons = new EnumMap<>(Tendon.class);
    tenosynovitis.forEach(
        (tendon, control) ->
            selectedTendons.put(
                tendon,
                new TendonSelection(
                    control.selectionOr(Degree.NONE),
                    tendon == Tendon.EXTENSOR_CARPI_ULNARIS
                        ? ecuTear.selectionOr(TendonTear.NONE)
                        : TendonTear.NONE)));
    Map<Bone, BoneSelection> selectedBones = new EnumMap<>(Bone.class);
    bones.forEach((bone, controls) -> selectedBones.put(bone, controls.selection()));
    return new Selection(
        normal.isSelected(),
        limitedExam.selectionOr(LimitedExam.NONE),
        selectedLigaments,
        tfcc.tear.selectionOr(TfccTear.NONE),
        tfcc.possible.isSelected(),
        selectedTendons,
        selectedBones,
        new CystSelection(FormRows.selected(cystSides), cystSize.getText(), cystLevel.getText()),
        FormRows.selected(swelling),
        freeText.getText());
  }

  void clearSelections() {
    normal.setSelected(false);
    limitedExam.clear();
    clearPositiveSelections();
    freeText.setText("");
  }

  private void clearPositiveSelections() {
    positiveChoices().forEach(DirectChoiceControl::clear);
    positiveToggles().forEach(button -> button.setSelected(false));
    bones.values().forEach(BoneControls::refresh);
    bones.values().forEach(controls -> controls.extras.setText(""));
    cystSize.setText("");
    cystLevel.setText("");
    refreshCyst();
  }

  private List<DirectChoiceControl<?>> positiveChoices() {
    List<DirectChoiceControl<?>> controls = new ArrayList<>(tenosynovitis.values());
    ligaments.values().forEach(ligament -> controls.add(ligament.tear));
    controls.add(tfcc.tear);
    controls.add(ecuTear);
    bones.values().forEach(bone -> controls.add(bone.contusion));
    return controls;
  }

  private List<JToggleButton> positiveToggles() {
    List<JToggleButton> buttons = new ArrayList<>(cystSides.values());
    buttons.addAll(swelling.values());
    buttons.add(tfcc.possible);
    ligaments.values().forEach(ligament -> buttons.add(ligament.possible));
    bones.values().forEach(bone -> buttons.addAll(bone.toggles()));
    return buttons;
  }

  private JPanel buildLigamentsPanel() {
    JPanel panel = FormRows.rowsPanel("Ligaments and TFCC");
    int row = 0;
    for (Ligament ligament : Ligament.values()) {
      TearControls<LigamentStatus> controls =
          new TearControls<>(List.of(LigamentStatus.TEAR, LigamentStatus.PARTIAL_TEAR), 2);
      ligaments.put(ligament, controls);
      FormRows.addRow(panel, row++, ligament.toString(), controls.panel);
    }
    FormRows.addRow(panel, row, "TFCC tear", tfcc.panel);
    return panel;
  }

  private JPanel buildTendonsPanel() {
    JPanel panel = FormRows.rowsPanel("Extensor tenosynovitis");
    int row = 0;
    for (Tendon tendon : Tendon.values()) {
      DirectChoiceControl<Degree> control = new DirectChoiceControl<>(DEGREES, 4);
      tenosynovitis.put(tendon, control);
      FormRows.addInlineRow(panel, row++, tendon.toString(), control.panel());
    }
    FormRows.addInlineRow(panel, row, "ECU tear", ecuTear.panel());
    return panel;
  }

  private JPanel buildBonesPanel() {
    JPanel panel = FormRows.rowsPanel("Bones");
    int row = 0;
    for (Bone bone : Bone.values()) {
      BoneControls controls = new BoneControls();
      bones.put(bone, controls);
      FormRows.addInlineRow(panel, row++, bone.toString(), controls.contusion.panel());
      FormRows.addInlineRow(panel, row++, "", controls.detailRow);
    }
    return panel;
  }

  private JPanel buildSoftTissuePanel() {
    JPanel panel = FormRows.rowsPanel("Cyst and soft tissues");
    FormRows.addRow(
        panel,
        0,
        "Ganglion or synovial cyst",
        FormRows.toggleRow(List.of(WristSide.values()), cystSides, WristSide::toString));
    JPanel details = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 0));
    details.add(new JLabel("Size"));
    details.add(cystSize);
    details.add(new JLabel("Level"));
    details.add(cystLevel);
    FormRows.addRow(panel, 1, "Cyst size and level, such as 8 mm, radiocarpal joint", details);
    FormRows.addRow(
        panel,
        2,
        "Soft tissue swelling",
        FormRows.toggleRow(SWELLING_SIDES, swelling, WristSide::toString));
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

  private void refreshCyst() {
    boolean hasCyst = cystSides.values().stream().anyMatch(JToggleButton::isSelected);
    cystSize.setEnabled(hasCyst);
    cystLevel.setEnabled(hasCyst);
  }

  private static Map<Character, JToggleButton> keys(Object... keysAndButtons) {
    Map<Character, JToggleButton> keys = new LinkedHashMap<>();
    for (int i = 0; i < keysAndButtons.length; i += 2) {
      keys.put((Character) keysAndButtons[i], (JToggleButton) keysAndButtons[i + 1]);
    }
    return keys;
  }

  /** The buttons for d1 to d4: mild, moderate, severe, minimal. */
  private static List<JToggleButton> degreeButtons(DirectChoiceControl<Degree> control) {
    return List.of(
        control.button(Degree.MILD),
        control.button(Degree.MODERATE),
        control.button(Degree.SEVERE),
        control.button(Degree.MINIMAL));
  }

  /** A tear choice with a toggle that hedges it as possible. */
  private static final class TearControls<T> {
    private final JPanel panel = new JPanel(new BorderLayout(4, 0));
    private final DirectChoiceControl<T> tear;
    private final JToggleButton possible = new FindingToggleButton("Possible");

    TearControls(List<T> choices, int columns) {
      tear = new DirectChoiceControl<>(choices, columns);
      possible.setMargin(new Insets(2, 7, 2, 7));
      panel.add(tear.panel(), BorderLayout.CENTER);
      panel.add(possible, BorderLayout.EAST);
    }
  }

  private static final class BoneControls {
    private final DirectChoiceControl<Degree> contusion = new DirectChoiceControl<>(DEGREES, 4);
    private final JToggleButton fracture = new FindingToggleButton("Fracture");
    private final JToggleButton cysticChange = new FindingToggleButton("Cystic");
    private final JToggleButton edema = new FindingToggleButton("Edema/OA");
    private final JTextField extras = ComposerTextEditing.install(new JTextField(6));
    private final JPanel detailRow = new JPanel(new BorderLayout(4, 0));

    BoneControls() {
      JPanel buttons = new JPanel(new GridLayout(1, 0, 4, 2));
      for (JToggleButton button : toggles()) {
        button.setMargin(new Insets(2, 5, 2, 5));
        button.addActionListener(event -> refresh());
        buttons.add(button);
      }
      fracture.setToolTipText("Nondisplaced fracture with surrounding bone marrow edema");
      edema.setToolTipText("Marrow edema, may be related to bony contusion or osteoarthrosis");
      contusion.addActionListener(event -> refresh());
      detailRow.add(buttons, BorderLayout.WEST);
      detailRow.add(extras, BorderLayout.CENTER);
      refresh();
    }

    List<JToggleButton> toggles() {
      return List.of(fracture, cysticChange, edema);
    }

    BoneSelection selection() {
      return new BoneSelection(
          contusion.selectionOr(Degree.NONE),
          fracture.isSelected(),
          cysticChange.isSelected(),
          edema.isSelected(),
          extras.getText());
    }

    Target chordTarget(Bone bone) {
      return new Target(
          bone.toString(),
          degreeButtons(contusion),
          keys('f', fracture, 'c', cysticChange, 'o', edema),
          extras);
    }

    /** The extras follow a finding on the bone. */
    void refresh() {
      extras.setEnabled(selection().hasFinding());
    }
  }
}
