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
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
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
import javax.swing.SwingUtilities;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.AlignmentFinding;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.Laterality;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.LevelSelection;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.ListhesisDirection;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.ListhesisSelection;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.MigrationDirection;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.OverviewFinding;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.ProtrusionLocation;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.Selection;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.Severity;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.SpineRegion;

final class SpineFormPanel extends JPanel {
  private static final Severity[] SEVERITIES = {Severity.MILD, Severity.MODERATE, Severity.SEVERE};

  private final SpineRegion region;
  private final Map<AlignmentFinding, JCheckBox> alignmentControls =
      new EnumMap<>(AlignmentFinding.class);
  private final Map<OverviewFinding, OverviewControls> overviewControls =
      new EnumMap<>(OverviewFinding.class);
  private final Map<String, LevelControls> levelControls = new LinkedHashMap<>();
  private final JComboBox<Severity> scoliosisSeverity = new JComboBox<>(SEVERITIES);
  private final JTextField scoliosisDegrees = new JTextField(5);
  private final JTextField degenerativeDetails = new JTextField();
  private final JButton resetButton;
  private final JButton otherFindingButton = new JButton("Other Finding");
  private final JButton addButton = new JButton("Add Selected Findings");
  private final SpineFormShortcuts shortcuts;

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
    setFocusable(true);
    shortcuts = new SpineFormShortcuts(this);
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

  SpineFormShortcuts shortcuts() {
    return shortcuts;
  }

  LevelControls levelAt(Point point) {
    if (!getVisibleRect().contains(point)) return null;
    return levelControls.values().stream()
        .filter(
            level -> level.panel.contains(SwingUtilities.convertPoint(this, point, level.panel)))
        .findFirst()
        .orElse(null);
  }

  LevelControls levelFor(Component component) {
    return levelControls.values().stream()
        .filter(
            level -> component != null && SwingUtilities.isDescendingFrom(component, level.panel))
        .findFirst()
        .orElse(null);
  }

  LevelControls level(String name) {
    return levelControls.get(name);
  }

  void submitFromKeyboard() {
    addButton.doClick(0);
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
    List<AlignmentFinding> alignments =
        alignmentControls.entrySet().stream()
            .filter(entry -> entry.getValue().isSelected())
            .map(Map.Entry::getKey)
            .toList();
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
    List<ListhesisSelection> listheses = new ArrayList<>();
    for (LevelControls controls : levelControls.values()) {
      ListhesisSelection listhesis = controls.listhesisSelection();
      if (listhesis != null) {
        listheses.add(listhesis);
      }
    }
    Severity selectedScoliosisSeverity =
        alignments.stream().anyMatch(AlignmentFinding::isLumbarScoliosis)
            ? (Severity) scoliosisSeverity.getSelectedItem()
            : Severity.NONE;
    return new Selection(
        region,
        alignments,
        selectedScoliosisSeverity,
        scoliosisDegrees.getText(),
        listheses,
        overviewSelections,
        degenerativeDetails.getText(),
        levels);
  }

  void clearSelections() {
    shortcuts.reset();
    alignmentControls.values().forEach(checkBox -> checkBox.setSelected(false));
    scoliosisSeverity.setSelectedItem(Severity.MILD);
    scoliosisSeverity.setEnabled(false);
    scoliosisDegrees.setText("");
    scoliosisDegrees.setEnabled(false);
    overviewControls.values().forEach(OverviewControls::clear);
    degenerativeDetails.setText("");
    levelControls.values().forEach(LevelControls::clear);
  }

  private JPanel buildAlignmentPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.setBorder(BorderFactory.createTitledBorder("Alignment"));
    JPanel findingRow = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 3));
    for (AlignmentFinding finding : region.alignmentFindings()) {
      JCheckBox checkBox = new JCheckBox(finding.toString());
      alignmentControls.put(finding, checkBox);
      checkBox.addActionListener(
          event -> {
            if (checkBox.isSelected()) {
              alignmentControls.entrySet().stream()
                  .filter(entry -> entry.getKey() != finding)
                  .filter(
                      entry ->
                          region != SpineRegion.LUMBAR
                              || (finding.isLumbarScoliosis()
                                  && entry.getKey().isLumbarScoliosis()))
                  .map(Map.Entry::getValue)
                  .forEach(other -> other.setSelected(false));
            }
            updateScoliosisControls();
          });
      findingRow.add(checkBox);
    }
    panel.add(stretch(findingRow));
    if (region == SpineRegion.LUMBAR) {
      scoliosisSeverity.setSelectedItem(Severity.MILD);
      JPanel scoliosisDetails =
          GuiUtils.getFlowLayoutPanel(
              FlowLayout.LEADING,
              6,
              1,
              new JLabel("Scoliosis severity"),
              scoliosisSeverity,
              new JLabel("Degrees"),
              scoliosisDegrees);
      panel.add(stretch(scoliosisDetails));
      updateScoliosisControls();
    }
    return panel;
  }

  private void updateScoliosisControls() {
    boolean enabled =
        alignmentControls.entrySet().stream()
            .anyMatch(entry -> entry.getKey().isLumbarScoliosis() && entry.getValue().isSelected());
    scoliosisSeverity.setEnabled(enabled);
    scoliosisDegrees.setEnabled(enabled);
  }

  private JPanel buildOverviewPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.setBorder(BorderFactory.createTitledBorder("Degenerative changes"));
    for (OverviewFinding finding : region.overviewFindings()) {
      OverviewControls controls = new OverviewControls(finding);
      overviewControls.put(finding, controls);
      panel.add(stretch(controls.panel()));
    }
    if (region == SpineRegion.LUMBAR) {
      JPanel details = new JPanel(new BorderLayout(6, 0));
      details.setBorder(GuiUtils.getEmptyBorder(3, 3, 3, 3));
      details.add(new JLabel("Details"), BorderLayout.WEST);
      details.add(degenerativeDetails, BorderLayout.CENTER);
      panel.add(stretch(details));
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

  final class LevelControls {
    private final String level;
    private boolean keyboardHovered;
    private final JPanel panel =
        new JPanel(new GridBagLayout()) {
          @Override
          protected void paintBorder(Graphics graphics) {
            super.paintBorder(graphics);
            if (keyboardHovered) {
              Graphics copy = graphics.create();
              try {
                copy.setColor(new Color(59, 130, 246));
                copy.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
              } finally {
                copy.dispose();
              }
            }
          }
        };
    private final DirectChoiceControl<ListhesisDirection> listhesisDirection =
        new DirectChoiceControl<>(
            List.of(ListhesisDirection.ANTEROLISTHESIS, ListhesisDirection.RETROLISTHESIS));
    private final JTextField listhesisDistance = new JTextField(4);
    private final JCheckBox bulge = new JCheckBox("Bulge");
    private final JCheckBox protrusion = new JCheckBox("Protrusion");
    private final JCheckBox extrusion = new JCheckBox("Extrusion");
    private final Map<ProtrusionLocation, JCheckBox> discLocations =
        new EnumMap<>(ProtrusionLocation.class);
    private final JCheckBox annularFissure = new JCheckBox("Annular fissure");
    private final JCheckBox ventralEpiduralLipomatosis =
        new JCheckBox("Ventral epidural lipomatosis");
    private final DirectChoiceControl<MigrationDirection> migrationDirection =
        new DirectChoiceControl<>(
            List.of(MigrationDirection.SUPERIOR, MigrationDirection.INFERIOR));
    private final JTextField migrationDistance = new JTextField(4);
    private final DirectChoiceControl<Laterality> facetArthrosis =
        new DirectChoiceControl<>(List.of(Laterality.BILATERAL, Laterality.LEFT, Laterality.RIGHT));
    private final DirectChoiceControl<Laterality> posteriorElementHypertrophy =
        new DirectChoiceControl<>(List.of(Laterality.BILATERAL, Laterality.LEFT, Laterality.RIGHT));
    private final DirectChoiceControl<Severity> spinalCanalSeverity =
        new DirectChoiceControl<>(List.of(SEVERITIES));
    private final DirectChoiceControl<Severity> leftForaminalSeverity =
        new DirectChoiceControl<>(List.of(SEVERITIES));
    private final DirectChoiceControl<Severity> rightForaminalSeverity =
        new DirectChoiceControl<>(List.of(SEVERITIES));
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
      panel.add(new JLabel("Listhesis"), constraints);
      constraints.gridx = 1;
      constraints.gridwidth = 2;
      constraints.fill = GridBagConstraints.HORIZONTAL;
      panel.add(
          GuiUtils.getFlowLayoutPanel(
              FlowLayout.LEADING,
              4,
              0,
              listhesisDirection.panel(),
              new JLabel("Displacement"),
              listhesisDistance,
              new JLabel("mm")),
          constraints);
      listhesisDirection.addActionListener(event -> updateListhesisControls());
      updateListhesisControls();

      constraints = constraints(2);
      constraints.gridwidth = 3;
      panel.add(
          GuiUtils.getFlowLayoutPanel(FlowLayout.LEADING, 4, 0, bulge, protrusion, extrusion),
          constraints);

      protrusion.addActionListener(
          event -> {
            if (protrusion.isSelected()) {
              extrusion.setSelected(false);
            }
            updateDiscLocationControls();
          });
      extrusion.addActionListener(
          event -> {
            if (extrusion.isSelected()) {
              protrusion.setSelected(false);
            }
            updateDiscLocationControls();
          });

      JPanel discLocationPanel = new JPanel(new GridLayout(0, 2, 4, 0));
      discLocationPanel.setBorder(GuiUtils.getEmptyBorder(0, 18, 2, 0));
      for (ProtrusionLocation location : ProtrusionLocation.values()) {
        JCheckBox locationCheckBox = new JCheckBox(location.toString());
        locationCheckBox.setSelected(location == ProtrusionLocation.CENTRAL);
        discLocations.put(location, locationCheckBox);
        discLocationPanel.add(locationCheckBox);
      }
      constraints = constraints(3);
      constraints.gridwidth = 3;
      constraints.fill = GridBagConstraints.HORIZONTAL;
      panel.add(discLocationPanel, constraints);
      updateDiscLocationControls();

      constraints = constraints(4);
      constraints.gridwidth = 3;
      panel.add(
          GuiUtils.getFlowLayoutPanel(
              FlowLayout.LEADING, 4, 0, annularFissure, ventralEpiduralLipomatosis),
          constraints);

      constraints = constraints(5);
      panel.add(new JLabel("Disc migration"), constraints);
      constraints.gridx = 1;
      constraints.gridwidth = 2;
      constraints.fill = GridBagConstraints.HORIZONTAL;
      JPanel migrationPanel = new JPanel();
      migrationPanel.setLayout(new BoxLayout(migrationPanel, BoxLayout.Y_AXIS));
      migrationPanel.add(stretch(migrationDirection.panel()));
      migrationPanel.add(
          stretch(
              GuiUtils.getFlowLayoutPanel(
                  FlowLayout.LEADING,
                  4,
                  0,
                  new JLabel("Distance"),
                  migrationDistance,
                  new JLabel("mm"))));
      panel.add(migrationPanel, constraints);

      addChoiceRow(6, "Facet arthrosis", facetArthrosis);
      addChoiceRow(7, region.posteriorElementLabel(), posteriorElementHypertrophy);

      int row = 8;
      if (region == SpineRegion.CERVICAL || region == SpineRegion.LUMBAR) {
        addChoiceRow(row++, "Spinal canal stenosis", spinalCanalSeverity);
      }

      addChoiceRow(row++, "Foraminal stenosis - left", leftForaminalSeverity);
      addChoiceRow(row++, "Foraminal stenosis - right", rightForaminalSeverity);

      constraints = constraints(row);
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

    String name() {
      return level;
    }

    void setKeyboardHovered(boolean hovered) {
      if (keyboardHovered != hovered) {
        keyboardHovered = hovered;
        panel.repaint();
      }
    }

    void editText() {
      java.awt.Window window = SwingUtilities.getWindowAncestor(panel);
      if (window != null && !window.isFocused()) {
        window.toFront();
        window.requestFocus();
        SwingUtilities.invokeLater(freeText::requestFocusInWindow);
      }
      freeText.requestFocusInWindow();
      freeText.setCaretPosition(freeText.getDocument().getLength());
    }

    void toggle(char key) {
      JCheckBox button =
          switch (key) {
            case 'B' -> bulge;
            case 'P' -> protrusion;
            case 'E' -> extrusion;
            case 'A' -> annularFissure;
            case 'V' -> ventralEpiduralLipomatosis;
            default -> throw new IllegalArgumentException("Unknown finding shortcut");
          };
      button.doClick(0);
    }

    boolean hasCanalControl() {
      return region == SpineRegion.CERVICAL || region == SpineRegion.LUMBAR;
    }

    void setSeverity(char target, int number) {
      DirectChoiceControl<Severity> control =
          switch (target) {
            case 'C' -> spinalCanalSeverity;
            case 'L' -> leftForaminalSeverity;
            case 'R' -> rightForaminalSeverity;
            default -> throw new IllegalArgumentException("Unknown severity shortcut");
          };
      if (number == 0) control.clear();
      else control.select(SEVERITIES[number - 1]);
    }

    void setLaterality(char target, char side) {
      DirectChoiceControl<Laterality> control =
          target == 'F' ? facetArthrosis : posteriorElementHypertrophy;
      if (side == '0') control.clear();
      else
        control.select(
            switch (side) {
              case 'L' -> Laterality.LEFT;
              case 'R' -> Laterality.RIGHT;
              case 'B' -> Laterality.BILATERAL;
              default -> throw new IllegalArgumentException("Unknown laterality shortcut");
            });
    }

    boolean hasDiscHerniation() {
      return protrusion.isSelected() || extrusion.isSelected();
    }

    void setDiscLocations(java.util.Set<ProtrusionLocation> selected) {
      discLocations.forEach((location, button) -> button.setSelected(selected.contains(location)));
    }

    LevelSelection selection() {
      Severity canalSeverity =
          region == SpineRegion.CERVICAL || region == SpineRegion.LUMBAR
              ? spinalCanalSeverity.selectionOr(Severity.NONE)
              : Severity.NONE;
      List<ProtrusionLocation> selectedDiscLocations = selectedDiscLocations();
      return new LevelSelection(
          level,
          bulge.isSelected(),
          protrusion.isSelected() ? selectedDiscLocations : List.of(),
          extrusion.isSelected() ? selectedDiscLocations : List.of(),
          annularFissure.isSelected(),
          ventralEpiduralLipomatosis.isSelected(),
          migrationDirection.selectionOr(MigrationDirection.NONE),
          migrationDistance.getText(),
          freeText.getText(),
          facetArthrosis.selectionOr(Laterality.NONE),
          posteriorElementHypertrophy.selectionOr(Laterality.NONE),
          canalSeverity,
          leftForaminalSeverity.selectionOr(Severity.NONE),
          rightForaminalSeverity.selectionOr(Severity.NONE));
    }

    ListhesisSelection listhesisSelection() {
      ListhesisDirection direction = listhesisDirection.selectionOr(ListhesisDirection.NONE);
      return direction == ListhesisDirection.NONE
          ? null
          : new ListhesisSelection(level, direction, listhesisDistance.getText());
    }

    private List<ProtrusionLocation> selectedDiscLocations() {
      if (!protrusion.isSelected() && !extrusion.isSelected()) {
        return List.of();
      }
      List<ProtrusionLocation> selected =
          discLocations.entrySet().stream()
              .filter(entry -> entry.getValue().isSelected())
              .map(Map.Entry::getKey)
              .toList();
      return selected.isEmpty() ? List.of(ProtrusionLocation.CENTRAL) : selected;
    }

    void clear() {
      listhesisDirection.clear();
      listhesisDistance.setText("");
      updateListhesisControls();
      bulge.setSelected(false);
      protrusion.setSelected(false);
      extrusion.setSelected(false);
      discLocations.forEach(
          (location, checkBox) -> {
            checkBox.setSelected(location == ProtrusionLocation.CENTRAL);
            checkBox.setEnabled(false);
          });
      annularFissure.setSelected(false);
      ventralEpiduralLipomatosis.setSelected(false);
      migrationDirection.clear();
      migrationDistance.setText("");
      facetArthrosis.clear();
      posteriorElementHypertrophy.clear();
      spinalCanalSeverity.clear();
      leftForaminalSeverity.clear();
      rightForaminalSeverity.clear();
      freeText.setText("");
    }

    private void updateDiscLocationControls() {
      boolean enabled = protrusion.isSelected() || extrusion.isSelected();
      discLocations.values().forEach(checkBox -> checkBox.setEnabled(enabled));
    }

    private void updateListhesisControls() {
      listhesisDistance.setEnabled(
          listhesisDirection.selectionOr(ListhesisDirection.NONE) != ListhesisDirection.NONE);
    }

    private void addChoiceRow(int row, String label, DirectChoiceControl<?> choice) {
      GridBagConstraints constraints = constraints(row);
      panel.add(new JLabel(label), constraints);
      constraints.gridx = 1;
      constraints.gridwidth = 2;
      constraints.fill = GridBagConstraints.HORIZONTAL;
      panel.add(choice.panel(), constraints);
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
}
