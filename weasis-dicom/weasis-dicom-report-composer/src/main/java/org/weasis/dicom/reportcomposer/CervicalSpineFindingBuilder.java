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
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.GeneratedFinding;

final class CervicalSpineFindingBuilder {
  static final List<String> LEVELS = List.of("C2-3", "C3-4", "C4-5", "C5-6", "C6-7", "C7-T1");

  private CervicalSpineFindingBuilder() {}

  static List<GeneratedFinding> generate(Selection selection) {
    Objects.requireNonNull(selection);
    List<GeneratedFinding> findings = new ArrayList<>();

    if (selection.curvature() != Curvature.NONE) {
      findings.add(
          new GeneratedFinding(
              ComposerText.sentence(selection.curvature().findingText()),
              ComposerText.sentence(selection.curvature().impressionText())));
    }

    for (OverviewFinding overview : OverviewFinding.values()) {
      if (selection.overviewLevels().containsKey(overview)) {
        List<String> levels = selection.overviewLevels().get(overview);
        String text =
            levels.isEmpty()
                ? overview.generalizedText()
                : overview.localizedText() + " at " + joinList(levels);
        String findingText = ComposerText.sentence(text);
        findings.add(
            new GeneratedFinding(findingText, overview.includeInImpression() ? findingText : ""));
      }
    }

    selection.levelSelections().stream()
        .filter(LevelSelection::hasFinding)
        .sorted(Comparator.comparingInt(level -> LEVELS.indexOf(level.level())))
        .map(CervicalSpineFindingBuilder::generateLevel)
        .forEach(findings::add);
    return List.copyOf(findings);
  }

  private static GeneratedFinding generateLevel(LevelSelection selection) {
    List<String> details = new ArrayList<>();
    if (selection.bulge() && selection.protrusion()) {
      details.add("Disc bulge with superimposed protrusion");
    } else if (selection.bulge()) {
      details.add("Disc bulge");
    } else if (selection.protrusion()) {
      details.add("Disc protrusion");
    }
    addLateralized(details, selection.facetArthrosis(), "facet arthrosis");
    addLateralized(details, selection.uncovertebralHypertrophy(), "uncovertebral hypertrophy");
    if (selection.foraminalSeverity() != Severity.NONE) {
      details.add(
          capitalize(selection.foraminalSeverity().phrase())
              + " "
              + selection.foraminalLaterality().phrase()
              + " neural foraminal stenosis");
    }
    if (ComposerText.hasText(selection.freeText())) {
      details.add(selection.freeText());
    }

    String findingText =
        selection.level()
            + ": "
            + details.stream().map(ComposerText::sentence).reduce((a, b) -> a + " " + b).orElse("");

    List<String> impressionParts = new ArrayList<>();
    if (selection.protrusion()) {
      impressionParts.add("disc protrusion");
    }
    if (selection.foraminalSeverity() != Severity.NONE) {
      impressionParts.add(
          selection.foraminalSeverity().phrase()
              + " "
              + selection.foraminalLaterality().phrase()
              + " neural foraminal stenosis");
    }
    String impressionText =
        impressionParts.isEmpty()
            ? ""
            : ComposerText.sentence(
                capitalize(joinList(impressionParts)) + " at " + selection.level());
    return new GeneratedFinding(findingText, impressionText);
  }

  private static void addLateralized(List<String> details, Laterality laterality, String finding) {
    if (laterality != Laterality.NONE) {
      details.add(capitalize(laterality.phrase()) + " " + finding);
    }
  }

  private static String joinList(List<String> values) {
    return switch (values.size()) {
      case 0 -> "";
      case 1 -> values.getFirst();
      case 2 -> values.getFirst() + " and " + values.getLast();
      default ->
          String.join(", ", values.subList(0, values.size() - 1)) + ", and " + values.getLast();
    };
  }

  private static String capitalize(String value) {
    return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
  }

  enum Curvature {
    NONE("", ""),
    STRAIGHTENING("Straightening of the cervical lordosis", "Straightened cervical lordosis"),
    REVERSAL("Reversal of the cervical lordosis", "Reversed cervical lordosis");

    private final String findingText;
    private final String impressionText;

    Curvature(String findingText, String impressionText) {
      this.findingText = findingText;
      this.impressionText = impressionText;
    }

    String findingText() {
      return findingText;
    }

    String impressionText() {
      return impressionText;
    }
  }

  enum OverviewFinding {
    SPONDYLOSIS("Spondylosis", "Cervical spondylosis", "Cervical spondylosis", true),
    DISC_DEHYDRATION("Disc dehydration", "Multilevel disc desiccation", "Disc desiccation", false),
    DISC_HEIGHT_LOSS("Disc height loss", "Disc height loss", "Disc height loss", false),
    OSTEOPHYTES("Osteophytes", "Endplate osteophytes", "Endplate osteophytes", false),
    REACTIVE_ENDPLATE_CHANGES(
        "Reactive endplate changes",
        "Reactive endplate marrow changes",
        "Reactive endplate marrow changes",
        false);

    private final String label;
    private final String generalizedText;
    private final String localizedText;
    private final boolean includeInImpression;

    OverviewFinding(
        String label, String generalizedText, String localizedText, boolean includeInImpression) {
      this.label = label;
      this.generalizedText = generalizedText;
      this.localizedText = localizedText;
      this.includeInImpression = includeInImpression;
    }

    String generalizedText() {
      return generalizedText;
    }

    String localizedText() {
      return localizedText;
    }

    boolean includeInImpression() {
      return includeInImpression;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  enum Laterality {
    NONE("None", ""),
    LEFT("Left", "left"),
    RIGHT("Right", "right"),
    BILATERAL("Bilateral", "bilateral");

    private final String label;
    private final String phrase;

    Laterality(String label, String phrase) {
      this.label = label;
      this.phrase = phrase;
    }

    String phrase() {
      return phrase;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  enum Severity {
    NONE("None", ""),
    MILD("Mild", "mild"),
    MODERATE("Moderate", "moderate"),
    SEVERE("Severe", "severe");

    private final String label;
    private final String phrase;

    Severity(String label, String phrase) {
      this.label = label;
      this.phrase = phrase;
    }

    String phrase() {
      return phrase;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  record LevelSelection(
      String level,
      boolean bulge,
      boolean protrusion,
      String freeText,
      Laterality facetArthrosis,
      Laterality uncovertebralHypertrophy,
      Laterality foraminalLaterality,
      Severity foraminalSeverity) {

    LevelSelection {
      level = ComposerText.clean(level);
      if (!LEVELS.contains(level)) {
        throw new IllegalArgumentException("Unsupported cervical level: " + level);
      }
      freeText = ComposerText.clean(freeText);
      facetArthrosis = Objects.requireNonNullElse(facetArthrosis, Laterality.NONE);
      uncovertebralHypertrophy =
          Objects.requireNonNullElse(uncovertebralHypertrophy, Laterality.NONE);
      foraminalLaterality = Objects.requireNonNullElse(foraminalLaterality, Laterality.NONE);
      foraminalSeverity = Objects.requireNonNullElse(foraminalSeverity, Severity.NONE);
      if ((foraminalLaterality == Laterality.NONE) != (foraminalSeverity == Severity.NONE)) {
        throw new IllegalArgumentException(
            "Foraminal stenosis requires both laterality and severity.");
      }
    }

    boolean hasFinding() {
      return bulge
          || protrusion
          || ComposerText.hasText(freeText)
          || facetArthrosis != Laterality.NONE
          || uncovertebralHypertrophy != Laterality.NONE
          || foraminalSeverity != Severity.NONE;
    }
  }

  record Selection(
      Curvature curvature,
      Map<OverviewFinding, List<String>> overviewLevels,
      List<LevelSelection> levelSelections) {

    Selection {
      curvature = Objects.requireNonNullElse(curvature, Curvature.NONE);
      EnumMap<OverviewFinding, List<String>> overviewCopy = new EnumMap<>(OverviewFinding.class);
      if (overviewLevels != null) {
        overviewLevels.forEach(
            (finding, levels) -> {
              Objects.requireNonNull(finding);
              List<String> orderedLevels =
                  LEVELS.stream()
                      .filter(level -> levels != null && levels.contains(level))
                      .toList();
              overviewCopy.put(finding, orderedLevels);
            });
      }
      overviewLevels = Collections.unmodifiableMap(overviewCopy);
      levelSelections = levelSelections == null ? List.of() : List.copyOf(levelSelections);
    }
  }
}
