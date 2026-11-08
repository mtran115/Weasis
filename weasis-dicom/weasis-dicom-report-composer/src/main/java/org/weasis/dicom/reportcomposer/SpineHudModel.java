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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.JComponent;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.Laterality;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.LevelSelection;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.ListhesisDirection;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.ListhesisSelection;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.MigrationDirection;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.ProtrusionLocation;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.Selection;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.Severity;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.SpineRegion;
import org.weasis.dicom.reportcomposer.SpineFormPanel.LevelControls;
import org.weasis.dicom.reportcomposer.SpineFormPanel.SectionControls;
import org.weasis.dicom.reportcomposer.SpineFormPanel.ShortcutTarget;

/** The spine form as the HUD shows it: chips are the form's key letters and numpad numbers. */
final class SpineHudModel implements FormHud.Model {
  private static final List<Severity> SEVERITIES =
      List.of(Severity.MILD, Severity.MODERATE, Severity.SEVERE);
  private static final List<Laterality> SIDES =
      List.of(Laterality.BILATERAL, Laterality.LEFT, Laterality.RIGHT);

  private final SpineFormPanel form;

  SpineHudModel(SpineFormPanel form) {
    this.form = form;
  }

  @Override
  public JComponent form() {
    return form;
  }

  @Override
  public String title() {
    return form.region().formLabel();
  }

  @Override
  public List<FormHud.Row> rows() {
    Selection selection = form.selection();
    return form.targets().stream()
        .map(target -> new FormHud.Row(target, target.name(), summary(target, selection)))
        .toList();
  }

  @Override
  public void chips(Object target, FormHud.Chips chips) {
    if (target instanceof SectionControls section) {
      if (section.toggle() != null)
        chips.chip(
            "S " + section.toggle().getText(),
            section.toggle().isSelected(),
            () -> section.toggle().doClick(0));
      return;
    }
    LevelControls level = (LevelControls) target;
    LevelSelection current = level.selection();
    chips.chip("B", current.bulge(), () -> level.toggle('B'));
    chips.chip("P", current.protrusion(), () -> level.toggle('P'));
    chips.chip("E", current.extrusion(), () -> level.toggle('E'));
    chips.chip("A", current.annularFissure(), () -> level.toggle('A'));
    chips.chip("V", current.ventralEpiduralLipomatosis(), () -> level.toggle('V'));
    if (level.hasDiscHerniation()) {
      List<ProtrusionLocation> locations =
          current.protrusion() ? current.protrusionLocations() : current.extrusionLocations();
      chips.newLine();
      chips.label("D");
      for (ProtrusionLocation location : ProtrusionLocation.values()) {
        chips.chip(
            abbreviation(location),
            locations.contains(location),
            () -> level.setDiscLocations(toggled(locations, location)));
      }
    }
    chips.newLine();
    severityGroup(chips, 'C', current.spinalCanalSeverity(), level);
    chips.gap();
    severityGroup(chips, 'L', current.leftForaminalSeverity(), level);
    chips.gap();
    severityGroup(chips, 'R', current.rightForaminalSeverity(), level);
    chips.newLine();
    sideGroup(chips, 'F', current.facetArthrosis(), level);
    chips.gap();
    sideGroup(chips, 'H', current.posteriorElementHypertrophy(), level);
    chips.gap();
    chips.chip("T", false, level::editText);
  }

  private static void severityGroup(
      FormHud.Chips chips, char key, Severity current, LevelControls level) {
    chips.label(String.valueOf(key));
    for (int index = 0; index < SEVERITIES.size(); index++) {
      boolean selected = SEVERITIES.get(index) == current;
      int number = selected ? 0 : index + 1;
      chips.chip(Integer.toString(index + 1), selected, () -> level.setSeverity(key, number));
    }
  }

  private static void sideGroup(
      FormHud.Chips chips, char key, Laterality current, LevelControls level) {
    chips.label(String.valueOf(key));
    for (Laterality side : SIDES) {
      boolean selected = side == current;
      char choice = selected ? '0' : side.toString().charAt(0);
      chips.chip(side.toString().substring(0, 1), selected, () -> level.setLaterality(key, choice));
    }
  }

  private static Set<ProtrusionLocation> toggled(
      List<ProtrusionLocation> locations, ProtrusionLocation location) {
    Set<ProtrusionLocation> next =
        locations.isEmpty() ? EnumSet.noneOf(ProtrusionLocation.class) : EnumSet.copyOf(locations);
    if (!next.remove(location)) next.add(location);
    // An empty choice would silently fall back to central; keep the last location instead.
    return next.isEmpty() ? EnumSet.of(location) : next;
  }

  @Override
  public Object pinned() {
    return form.shortcuts().pinned();
  }

  @Override
  public void pin(Object target) {
    form.shortcuts().pin((ShortcutTarget) target);
  }

  @Override
  public boolean hasPending() {
    return form.shortcuts().hasPending();
  }

  @Override
  public String helpText() {
    return form.shortcuts().helpText();
  }

  @Override
  public void editPinned(Runnable action) {
    form.shortcuts().editPinned(action);
  }

  @Override
  public void setStateListener(Runnable listener) {
    form.shortcuts().setStateListener(listener);
  }

  static String summary(ShortcutTarget target, Selection selection) {
    if (target instanceof LevelControls level) {
      return levelSummary(level.selection(), level.listhesisSelection(), selection.region());
    }
    if (target.name().equals("Alignment")) {
      return selection.alignments().stream()
          .map(
              alignment ->
                  alignment.isLumbarScoliosis() && selection.scoliosisSeverity() != Severity.NONE
                      ? alignment + " " + abbreviation(selection.scoliosisSeverity())
                      : alignment.toString())
          .collect(Collectors.joining(" · "));
    }
    List<String> parts = new ArrayList<>();
    selection
        .overviewLevels()
        .forEach(
            (finding, levels) ->
                parts.add(finding + (levels.isEmpty() ? "" : " " + String.join(", ", levels))));
    if (!selection.degenerativeDetails().isBlank())
      parts.add(quote(selection.degenerativeDetails()));
    return String.join(" · ", parts);
  }

  static String levelSummary(
      LevelSelection level, ListhesisSelection listhesis, SpineRegion region) {
    List<String> parts = new ArrayList<>();
    if (listhesis != null) {
      parts.add(
          (listhesis.direction() == ListhesisDirection.ANTEROLISTHESIS ? "Antero" : "Retro")
              + millimetres(listhesis.displacement()));
    }
    if (level.bulge()) parts.add("Bulge");
    if (level.protrusion()) parts.add("Prot " + locations(level.protrusionLocations()));
    if (level.extrusion()) parts.add("Extr " + locations(level.extrusionLocations()));
    if (level.migrationDirection() != MigrationDirection.NONE) {
      parts.add(
          (level.migrationDirection() == MigrationDirection.SUPERIOR ? "Migr sup" : "Migr inf")
              + millimetres(level.migrationDistance()));
    }
    if (level.annularFissure()) parts.add("Fissure");
    if (level.ventralEpiduralLipomatosis()) parts.add("Lipo");
    if (level.facetArthrosis() != Laterality.NONE)
      parts.add("Facet " + side(level.facetArthrosis()));
    if (level.posteriorElementHypertrophy() != Laterality.NONE)
      parts.add(posteriorElementShort(region) + " " + side(level.posteriorElementHypertrophy()));
    if (level.spinalCanalSeverity() != Severity.NONE)
      parts.add("Canal " + abbreviation(level.spinalCanalSeverity()));
    if (level.leftForaminalSeverity() != Severity.NONE)
      parts.add("L foramen " + abbreviation(level.leftForaminalSeverity()));
    if (level.rightForaminalSeverity() != Severity.NONE)
      parts.add("R foramen " + abbreviation(level.rightForaminalSeverity()));
    if (!level.freeText().isBlank()) parts.add(quote(level.freeText()));
    return String.join(" · ", parts);
  }

  private static String locations(List<ProtrusionLocation> locations) {
    return locations.stream().map(SpineHudModel::abbreviation).collect(Collectors.joining("/"));
  }

  private static String millimetres(String value) {
    return value == null || value.isBlank() ? "" : " " + value.strip() + " mm";
  }

  static String quote(String text) {
    return "“" + text.strip() + "”";
  }

  private static String side(Laterality laterality) {
    return laterality.toString().substring(0, 1);
  }

  static String abbreviation(Severity severity) {
    return switch (severity) {
      case MILD -> "mild";
      case MODERATE -> "mod";
      case SEVERE -> "sev";
      case NONE -> "";
    };
  }

  static String abbreviation(ProtrusionLocation location) {
    return switch (location) {
      case CENTRAL -> "C";
      case BROAD_BASED -> "Broad";
      case LEFT_CENTRAL -> "LC";
      case RIGHT_CENTRAL -> "RC";
      case LEFT_SUBARTICULAR -> "LSA";
      case RIGHT_SUBARTICULAR -> "RSA";
      case LEFT_FORAMINAL -> "LF";
      case RIGHT_FORAMINAL -> "RF";
    };
  }

  private static String posteriorElementShort(SpineRegion region) {
    return region == SpineRegion.CERVICAL ? "Uncovertebral" : "Lig flavum";
  }
}
