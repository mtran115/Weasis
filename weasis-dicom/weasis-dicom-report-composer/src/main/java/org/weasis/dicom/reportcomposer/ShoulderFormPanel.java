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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionListener;
import java.util.ArrayList;
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
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
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
  private final JToggleButton paralabralCyst = new FindingToggleButton("Adjacent paralabral cyst");
  private final DirectChoiceControl<Degree> longHeadBicepsTenosynovitis =
      new DirectChoiceControl<>(DEGREES);
  private final JTextArea freeText = new JTextArea(3, 24);
  private final JButton resetButton = iconButton(ActionIcon.RESET, "Clear shoulder form");
  private final JButton otherFindingButton = new JButton("Other Finding");
  private final JButton addButton = new JButton("Add Selected Findings");

  private final List<ShortcutTarget> shortcutTargets = new ArrayList<>();
  private final ShoulderFormShortcuts shortcuts;
  private ShortcutTarget keyboardHovered;

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
    setFocusable(true);
    shortcuts = new ShoulderFormShortcuts(this);
    resetButton.addActionListener(event -> clearSelections());
  }

  @Override
  public void addNotify() {
    super.addNotify();
    shortcuts.install();
  }

  @Override
  public void removeNotify() {
    shortcuts.uninstall();
    super.removeNotify();
  }

  ShoulderFormShortcuts shortcuts() {
    return shortcuts;
  }

  ShortcutTarget targetAt(Point point) {
    if (!getVisibleRect().contains(point)) return null;
    return shortcutTargets.stream()
        .filter(target -> target.bounds().contains(point))
        .findFirst()
        .orElse(null);
  }

  ShortcutTarget target(String name) {
    return shortcutTargets.stream()
        .filter(target -> target.name().equals(name))
        .findFirst()
        .orElseThrow();
  }

  void setKeyboardHovered(ShortcutTarget target) {
    if (keyboardHovered != target) {
      keyboardHovered = target;
      repaint();
    }
  }

  @Override
  protected void paintChildren(Graphics graphics) {
    super.paintChildren(graphics);
    if (keyboardHovered != null) {
      Rectangle bounds = keyboardHovered.bounds();
      Graphics copy = graphics.create();
      try {
        copy.setColor(new Color(59, 130, 246));
        copy.drawRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1);
      } finally {
        copy.dispose();
      }
    }
  }

  void submitFromKeyboard() {
    addButton.doClick(0);
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
    shortcuts.reset();
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
    addSeverityTarget(panel, 0, "Subcoracoid bursitis", subcoracoidBursitis);
    addSeverityTarget(panel, 1, "Subacromial/subdeltoid bursitis", subacromialSubdeltoidBursitis);
    addSeverityTarget(panel, 2, "AC joint osteoarthrosis", acJointOsteoarthrosis);
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
      shortcutTargets.add(
          new ShortcutTarget(
              tendon.toString(),
              tendonPanel,
              tendonPanel,
              tendonPanel,
              control,
              tearControls,
              false));
      panel.add(stretch(tendonPanel));
    }
    return panel;
  }

  private JPanel buildLabrumPanel() {
    JPanel panel = new JPanel(new BorderLayout(6, 3));
    panel.setBorder(BorderFactory.createTitledBorder("Labral tear"));
    // Fixed rows keep the cyst toggle visible in a narrow composer. Preserve child paths for
    // drafts.
    JPanel choices = new JPanel(new GridBagLayout());
    GridBagConstraints locationConstraints = new GridBagConstraints();
    locationConstraints.fill = GridBagConstraints.HORIZONTAL;
    locationConstraints.weightx = 1.0;
    locationConstraints.insets = new Insets(1, 2, 1, 2);
    for (LabralLocation location : LabralLocation.values()) {
      JToggleButton button = new FindingToggleButton(location.toString());
      button.setMargin(new Insets(2, 7, 2, 7));
      labralTearLocations.put(location, button);
      int index = choices.getComponentCount();
      locationConstraints.gridx = index % 2;
      locationConstraints.gridy = index / 2;
      choices.add(button, locationConstraints);
    }
    paralabralCyst.setMargin(new Insets(2, 7, 2, 7));
    locationConstraints.gridx = 0;
    locationConstraints.gridy = 2;
    locationConstraints.gridwidth = 2;
    choices.add(paralabralCyst, locationConstraints);
    panel.add(choices, BorderLayout.CENTER);
    shortcutTargets.add(new ShortcutTarget("Labral tear", panel, panel, panel, null, null, true));
    return panel;
  }

  private JPanel buildBicepsPanel() {
    JPanel panel = choicePanel("Long head of biceps");
    addChoiceRow(panel, 0, "Tenosynovitis", longHeadBicepsTenosynovitis);
    shortcutTargets.add(
        new ShortcutTarget(
            "Long head of biceps", panel, panel, panel, longHeadBicepsTenosynovitis, null, false));
    return panel;
  }

  private JPanel buildFreeTextPanel() {
    JPanel panel = new JPanel(new BorderLayout(4, 4));
    panel.setBorder(BorderFactory.createTitledBorder("Free text"));
    freeText.setLineWrap(true);
    freeText.setWrapStyleWord(true);
    panel.add(new JScrollPane(freeText), BorderLayout.CENTER);
    shortcutTargets.add(new ShortcutTarget("Free text", panel, panel, panel, null, null, false));
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

  private void addSeverityTarget(
      JPanel panel, int row, String label, DirectChoiceControl<Degree> choice) {
    JLabel heading = addChoiceRow(panel, row, label, choice);
    // Keep the component hierarchy unchanged: saved drafts address controls by their paths.
    shortcutTargets.add(
        new ShortcutTarget(label, choice.panel(), heading, choice.panel(), choice, null, false));
  }

  private static JLabel addChoiceRow(
      JPanel panel, int row, String label, DirectChoiceControl<?> choice) {
    GridBagConstraints constraints = constraints(row * 2);
    constraints.gridwidth = 2;
    JLabel heading = new JLabel(label);
    panel.add(heading, constraints);
    constraints = constraints(row * 2 + 1);
    constraints.gridwidth = 2;
    constraints.weightx = 1.0;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.insets = new Insets(0, 15, 4, 3);
    panel.add(choice.panel(), constraints);
    return heading;
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

  final class ShortcutTarget {
    private final String name;
    private final JPanel panel;
    private final Component first;
    private final Component last;
    private final DirectChoiceControl<Degree> degree;
    private final CuffTearControls tear;
    private final boolean labrum;

    ShortcutTarget(
        String name,
        JPanel panel,
        Component first,
        Component last,
        DirectChoiceControl<Degree> degree,
        CuffTearControls tear,
        boolean labrum) {
      this.name = name;
      this.panel = panel;
      this.first = first;
      this.last = last;
      this.degree = degree;
      this.tear = tear;
      this.labrum = labrum;
    }

    String name() {
      return name;
    }

    JPanel panel() {
      return panel;
    }

    boolean hasDegree() {
      return degree != null;
    }

    Rectangle bounds() {
      Rectangle result =
          SwingUtilities.convertRectangle(
              first.getParent(), first.getBounds(), ShoulderFormPanel.this);
      if (first != last) {
        result =
            result.union(
                SwingUtilities.convertRectangle(
                    last.getParent(), last.getBounds(), ShoulderFormPanel.this));
        result.grow(2, 1);
      }
      return result;
    }

    void setDegree(Degree value) {
      if (value == Degree.NONE) degree.clear();
      else degree.select(value);
    }

    JToggleButton button(char key) {
      if (tear != null) {
        return switch (key) {
          case 'A' -> tear.types.get(CuffTearType.ARTICULAR_SURFACE);
          case 'B' -> tear.types.get(CuffTearType.BURSAL_SURFACE);
          case 'I' -> tear.types.get(CuffTearType.INTERSTITIAL);
          case 'F' -> tear.types.get(CuffTearType.FULL_THICKNESS);
          case 'H' -> tear.highGrade;
          case 'P' -> tear.atFootprint;
          case 'G' -> tear.backgroundTendinosis;
          default -> null;
        };
      }
      if (labrum) {
        return switch (key) {
          case 'A' -> labralTearLocations.get(LabralLocation.ANTERIOR);
          case 'S' -> labralTearLocations.get(LabralLocation.SUPERIOR);
          case 'P' -> labralTearLocations.get(LabralLocation.POSTERIOR);
          case 'I' -> labralTearLocations.get(LabralLocation.INFERIOR);
          case 'C' -> paralabralCyst;
          default -> null;
        };
      }
      return null;
    }

    JTextComponent textField() {
      return tear == null ? freeText : tear.details;
    }

    void editText() {
      JTextComponent text = textField();
      java.awt.Window window = SwingUtilities.getWindowAncestor(text);
      if (window != null && !window.isFocused()) {
        window.toFront();
        window.requestFocus();
        SwingUtilities.invokeLater(text::requestFocusInWindow);
      }
      text.scrollRectToVisible(new Rectangle(0, 0, text.getWidth(), text.getHeight()));
      text.requestFocusInWindow();
      text.setCaretPosition(text.getDocument().getLength());
    }

    String help() {
      if (tear != null) {
        return name
            + " · Numpad: 1 Mild · 2 Mod · 3 Severe\n"
            + "Numpad . Minimal · 0 Clear · A Articular · B Bursal\n"
            + "I Interstitial · F Full · H High-grade · P Footprint\n"
            + "G Background tendinosis · T Details · Cmd+Z Undo";
      }
      if (labrum) {
        return name
            + "\nA Anterior · S Superior · P Posterior · I Inferior\n"
            + "C Paralabral cyst · T Text\n"
            + ShoulderFormShortcuts.COMMON_HELP;
      }
      if (hasDegree()) {
        return name
            + "\nNumpad: 1 Mild · 2 Moderate · 3 Severe\n"
            + "Numpad . Minimal · 0 Clear · T Text\n"
            + ShoulderFormShortcuts.COMMON_HELP;
      }
      return name + " — T Text · Esc Leave text\n" + ShoulderFormShortcuts.COMMON_HELP;
    }
  }

  private static final class CuffTearControls {
    private final JPanel panel = new JPanel();
    private final Map<CuffTearType, JToggleButton> types = new EnumMap<>(CuffTearType.class);
    private final JToggleButton highGrade = new FindingToggleButton("High-grade");
    private final JToggleButton atFootprint = new FindingToggleButton("At footprint");
    private final JToggleButton backgroundTendinosis =
        new FindingToggleButton("Background tendinosis");
    private final JTextField details = new JTextField(10);

    CuffTearControls() {
      panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
      JPanel typeRow = new JPanel(new GridLayout(0, 2, 4, 2));
      for (CuffTearType type : CuffTearType.values()) {
        JToggleButton button = new FindingToggleButton(type.toString());
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
      highGrade.setEnabled(
          types.entrySet().stream()
              .anyMatch(
                  entry ->
                      entry.getKey() != CuffTearType.FULL_THICKNESS
                          && entry.getValue().isSelected()));
      atFootprint.setEnabled(enabled);
      backgroundTendinosis.setEnabled(enabled);
      details.setEnabled(enabled);
    }
  }
}
