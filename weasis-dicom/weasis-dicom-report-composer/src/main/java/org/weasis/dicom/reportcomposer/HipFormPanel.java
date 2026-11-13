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
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.text.JTextComponent;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.dicom.reportcomposer.HipFindingBuilder.Degree;
import org.weasis.dicom.reportcomposer.HipFindingBuilder.FluidAmount;
import org.weasis.dicom.reportcomposer.HipFindingBuilder.GlutealTendon;
import org.weasis.dicom.reportcomposer.HipFindingBuilder.LabralRegion;
import org.weasis.dicom.reportcomposer.HipFindingBuilder.Selection;
import org.weasis.dicom.reportcomposer.HipFindingBuilder.Side;
import org.weasis.dicom.reportcomposer.HipFindingBuilder.SideSelection;
import org.weasis.dicom.reportcomposer.HipFindingBuilder.TendonSelection;

final class HipFormPanel extends JPanel implements ChordForm {
  static final String CHORD_REFERENCE =
      """
      ~           chord mode on / off

      r l         right or left hip; stays until
                    the other one

      Structure; stays until the next one
        1 Greater trochanteric bursitis
        2 Labral tear
        3 Gluteus medius insertion
        4 Gluteus minimus insertion
        5 Common hamstring tendon
        6 Hip joint osteoarthrosis
        7 Hip joint effusion
        0 Free text box

      d#          bursitis, tendinosis or
                    osteoarthrosis (1, 3 to 6)
                    1 mild  2 moderate  3 severe
                    4 minimal  0 clear
                  on effusion (7)
                    1 small  2 moderate  3 large
                    4 trace  0 clear

      On the labrum (2)
      a s p       anterior, superior, posterior
      t           tear with no region named
      c           paralabral cyst
      x           extras text, such as the cyst size

      On a tendon (3 to 5)
      p           partial tear
      h           high-grade partial tear (3, 4)

      Backspace   remove the last key or token
      Esc         drop a half-typed token

      M, the numpad and arrows still go to the viewer.
      """;

  private static final List<Degree> DEGREES =
      List.of(Degree.MINIMAL, Degree.MILD, Degree.MODERATE, Degree.SEVERE);
  private static final List<FluidAmount> FLUID_AMOUNTS =
      List.of(FluidAmount.TRACE, FluidAmount.SMALL, FluidAmount.MODERATE, FluidAmount.LARGE);

  private final JToggleButton normal = new FindingToggleButton("Normal hip MRI");
  private final JToggleButton artifact = new FindingToggleButton("Sequences degraded by artifact");
  private final Map<Side, SideControls> sides = new EnumMap<>(Side.class);
  private final JTextArea freeText = ComposerTextEditing.install(new JTextArea(3, 24));
  private final JButton resetButton = new JButton(ResourceUtil.getIcon(ActionIcon.RESET));
  private final JButton otherFindingButton = new JButton("Other Finding");
  private final JButton addButton = new JButton("Add Selected Findings");
  private final SectionJumpBar jumpBar;

  HipFormPanel() {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(BorderFactory.createTitledBorder("Structured hip findings"));
    JPanel top = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 2));
    for (JToggleButton button : List.of(normal, artifact)) {
      button.setMargin(new Insets(2, 7, 2, 7));
      top.add(button);
    }
    add(FormRows.stretch(top));
    List<SectionJumpBar.Section> sections = new ArrayList<>();
    sections.add(new SectionJumpBar.Section("Normal", top, List.of("normal", "artifact")));
    ActionListener clearNormal = event -> normal.setSelected(false);
    for (Side side : Side.values()) {
      SideControls controls = new SideControls(side, clearNormal);
      sides.put(side, controls);
      add(FormRows.stretch(controls.panel));
      sections.add(
          new SectionJumpBar.Section(side + " hip", controls.panel, List.of(side.name() + ":")));
    }
    JPanel freeTextPanel = new JPanel(new BorderLayout(4, 4));
    freeTextPanel.setBorder(BorderFactory.createTitledBorder("Free text"));
    freeText.setLineWrap(true);
    freeText.setWrapStyleWord(true);
    freeTextPanel.add(new JScrollPane(freeText), BorderLayout.CENTER);
    add(FormRows.stretch(freeTextPanel));
    sections.add(new SectionJumpBar.Section("Free text", freeTextPanel, List.of("free-text")));

    otherFindingButton.setIcon(ResourceUtil.getIcon(ActionIcon.DRAW_TEXT));
    addButton.setIcon(ResourceUtil.getIcon(ActionIcon.PLUS));
    add(
        FormRows.stretch(
            GuiUtils.getFlowLayoutPanel(
                FlowLayout.TRAILING, 5, 5, resetButton, otherFindingButton, addButton)));
    jumpBar =
        new SectionJumpBar(
            sections, () -> SectionJumpBar.keys(HipFindingBuilder.generateKeyed(selection())));
    setAlignmentX(LEFT_ALIGNMENT);
    setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));

    normal.addActionListener(
        event -> {
          if (normal.isSelected()) {
            sides.values().forEach(SideControls::clear);
          }
        });
    resetButton.setToolTipText("Clear hip form");
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
    return List.of();
  }

  @Override
  public Map<Character, List<Target>> sidedTargets() {
    Map<Character, List<Target>> targets = new LinkedHashMap<>();
    sides.forEach((side, controls) -> targets.put(side.key(), controls.chordTargets()));
    return targets;
  }

  @Override
  public JTextComponent freeText() {
    return freeText;
  }

  Selection selection() {
    Map<Side, SideSelection> selected = new EnumMap<>(Side.class);
    sides.forEach((side, controls) -> selected.put(side, controls.selection()));
    return new Selection(normal.isSelected(), artifact.isSelected(), selected, freeText.getText());
  }

  void clearSelections() {
    normal.setSelected(false);
    artifact.setSelected(false);
    sides.values().forEach(SideControls::clear);
    freeText.setText("");
  }

  private static final class TendonControls {
    private final DirectChoiceControl<Degree> tendinosis = new DirectChoiceControl<>(DEGREES, 4);
    private final JToggleButton partialTear = new FindingToggleButton("Partial tear");
    private final JToggleButton highGrade = new FindingToggleButton("High-grade partial tear");

    TendonSelection selection() {
      return new TendonSelection(
          tendinosis.selectionOr(Degree.NONE), partialTear.isSelected(), highGrade.isSelected());
    }

    Target chordTarget(String name, boolean withHighGrade) {
      Map<Character, JToggleButton> keys = new LinkedHashMap<>();
      keys.put('p', partialTear);
      if (withHighGrade) {
        keys.put('h', highGrade);
      }
      return new Target(name, degreeButtons(tendinosis), keys, null);
    }
  }

  private static final class SideControls {
    private final JPanel panel;
    private final Side side;
    private final DirectChoiceControl<Degree> bursitis = new DirectChoiceControl<>(DEGREES, 4);
    private final Map<LabralRegion, JToggleButton> labralTear = new EnumMap<>(LabralRegion.class);
    private final JToggleButton paralabralCyst = new FindingToggleButton("Paralabral cyst");
    private final JTextField labralExtras = ComposerTextEditing.install(new JTextField(10));
    private final Map<GlutealTendon, TendonControls> gluteals = new EnumMap<>(GlutealTendon.class);
    private final TendonControls hamstring = new TendonControls();
    private final DirectChoiceControl<Degree> osteoarthrosis =
        new DirectChoiceControl<>(DEGREES, 4);
    private final DirectChoiceControl<FluidAmount> effusion =
        new DirectChoiceControl<>(FLUID_AMOUNTS, 4);

    SideControls(Side side, ActionListener changed) {
      this.side = side;
      panel = FormRows.rowsPanel(side + " hip");
      int row = 0;
      FormRows.addRow(panel, row++, "Greater trochanteric bursitis", bursitis.panel());
      FormRows.addRow(
          panel,
          row++,
          "Labral tear",
          FormRows.toggleRow(List.of(LabralRegion.values()), labralTear, LabralRegion::toString));
      JPanel cystRow = new JPanel(new BorderLayout(4, 0));
      paralabralCyst.setMargin(new Insets(2, 7, 2, 7));
      cystRow.add(paralabralCyst, BorderLayout.WEST);
      cystRow.add(labralExtras, BorderLayout.CENTER);
      FormRows.addRow(panel, row++, "Paralabral cyst and labral extras", cystRow);
      for (GlutealTendon tendon : GlutealTendon.values()) {
        TendonControls controls = new TendonControls();
        gluteals.put(tendon, controls);
        FormRows.addRow(
            panel, row++, tendon + " insertional tendinosis", controls.tendinosis.panel());
        FormRows.addRow(
            panel, row++, tendon + " tear", buttonRow(controls.partialTear, controls.highGrade));
      }
      FormRows.addRow(panel, row++, "Common hamstring tendinosis", hamstring.tendinosis.panel());
      FormRows.addRow(panel, row++, "Common hamstring tear", buttonRow(hamstring.partialTear));
      FormRows.addRow(panel, row++, "Hip joint osteoarthrosis", osteoarthrosis.panel());
      FormRows.addRow(panel, row, "Hip joint effusion", effusion.panel());

      choices().forEach(control -> control.addActionListener(changed));
      toggles().forEach(button -> button.addActionListener(changed));
      labralTear.values().forEach(button -> button.addActionListener(event -> refresh()));
      paralabralCyst.addActionListener(event -> refresh());
      refresh();
    }

    SideSelection selection() {
      Map<GlutealTendon, TendonSelection> tendons = new EnumMap<>(GlutealTendon.class);
      gluteals.forEach((tendon, controls) -> tendons.put(tendon, controls.selection()));
      return new SideSelection(
          bursitis.selectionOr(Degree.NONE),
          FormRows.selected(labralTear),
          paralabralCyst.isSelected(),
          labralExtras.getText(),
          tendons,
          hamstring.selection(),
          osteoarthrosis.selectionOr(Degree.NONE),
          effusion.selectionOr(FluidAmount.NONE));
    }

    List<Target> chordTargets() {
      String name = side + " ";
      List<Target> targets = new ArrayList<>();
      targets.add(
          new Target(
              name + "greater trochanteric bursitis", degreeButtons(bursitis), Map.of(), null));
      Map<Character, JToggleButton> labrum = new LinkedHashMap<>();
      labrum.put('t', labralTear.get(LabralRegion.WHOLE));
      labrum.put('a', labralTear.get(LabralRegion.ANTERIOR));
      labrum.put('s', labralTear.get(LabralRegion.SUPERIOR));
      labrum.put('p', labralTear.get(LabralRegion.POSTERIOR));
      labrum.put('c', paralabralCyst);
      targets.add(new Target(name + "labral tear", List.of(), labrum, labralExtras));
      gluteals.forEach(
          (tendon, controls) ->
              targets.add(controls.chordTarget(name + tendon.phrase() + " insertion", true)));
      targets.add(hamstring.chordTarget(name + "common hamstring tendon", false));
      targets.add(
          new Target(
              name + "hip joint osteoarthrosis", degreeButtons(osteoarthrosis), Map.of(), null));
      targets.add(
          new Target(
              name + "hip joint effusion",
              List.of(
                  effusion.button(FluidAmount.SMALL),
                  effusion.button(FluidAmount.MODERATE),
                  effusion.button(FluidAmount.LARGE),
                  effusion.button(FluidAmount.TRACE)),
              Map.of(),
              null));
      return targets;
    }

    void clear() {
      choices().forEach(DirectChoiceControl::clear);
      toggles().forEach(button -> button.setSelected(false));
      labralExtras.setText("");
      refresh();
    }

    private List<DirectChoiceControl<?>> choices() {
      List<DirectChoiceControl<?>> controls =
          new ArrayList<>(List.of(bursitis, hamstring.tendinosis, osteoarthrosis, effusion));
      gluteals.values().forEach(tendon -> controls.add(tendon.tendinosis));
      return controls;
    }

    private List<JToggleButton> toggles() {
      List<JToggleButton> buttons = new ArrayList<>(labralTear.values());
      buttons.add(paralabralCyst);
      buttons.add(hamstring.partialTear);
      gluteals
          .values()
          .forEach(
              tendon -> {
                buttons.add(tendon.partialTear);
                buttons.add(tendon.highGrade);
              });
      return buttons;
    }

    /** The extras follow a labral finding. */
    private void refresh() {
      labralExtras.setEnabled(
          paralabralCyst.isSelected()
              || labralTear.values().stream().anyMatch(JToggleButton::isSelected));
    }
  }

  private static JPanel buttonRow(JToggleButton... buttons) {
    JPanel panel = new JPanel(new java.awt.GridLayout(1, 0, 4, 2));
    for (JToggleButton button : buttons) {
      button.setMargin(new Insets(2, 7, 2, 7));
      panel.add(button);
    }
    return panel;
  }

  /** The buttons for d1 to d4: mild, moderate, severe, minimal. */
  private static List<JToggleButton> degreeButtons(DirectChoiceControl<Degree> control) {
    return List.of(
        control.button(Degree.MILD),
        control.button(Degree.MODERATE),
        control.button(Degree.SEVERE),
        control.button(Degree.MINIMAL));
  }
}
