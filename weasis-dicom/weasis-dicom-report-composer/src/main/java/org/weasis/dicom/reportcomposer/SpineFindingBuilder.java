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
import java.util.Optional;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.ExamTemplate;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.GeneratedFinding;

final class SpineFindingBuilder {
  private SpineFindingBuilder() {}

  static List<GeneratedFinding> generate(Selection selection) {
    Objects.requireNonNull(selection);
    SpineRegion region = selection.region();
    List<GeneratedFinding> findings = new ArrayList<>();

    if (selection.alignment() != AlignmentFinding.NONE) {
      findings.add(
          new GeneratedFinding(
              ComposerText.sentence(selection.alignment().findingText()),
              ComposerText.sentence(selection.alignment().impressionText())));
    }

    for (OverviewFinding overview : OverviewFinding.values()) {
      if (selection.overviewLevels().containsKey(overview)) {
        List<String> levels = selection.overviewLevels().get(overview);
        String text =
            levels.isEmpty()
                ? overview.generalizedText(region)
                : overview.localizedText(region) + " at " + joinList(levels);
        String findingText = ComposerText.sentence(text);
        findings.add(
            new GeneratedFinding(findingText, overview.includeInImpression() ? findingText : ""));
      }
    }

    selection.levelSelections().stream()
        .filter(LevelSelection::hasFinding)
        .sorted(Comparator.comparingInt(level -> region.levels().indexOf(level.level())))
        .map(level -> generateLevel(region, level))
        .forEach(findings::add);
    return List.copyOf(findings);
  }

  private static GeneratedFinding generateLevel(SpineRegion region, LevelSelection selection) {
    List<String> details = new ArrayList<>();
    if (selection.bulge() && selection.protrusion()) {
      details.add("Disc bulge with superimposed " + protrusionDescription(selection, false));
    } else if (selection.bulge()) {
      details.add("Disc bulge");
    } else if (selection.protrusion()) {
      details.add(capitalize(protrusionDescription(selection, true)));
    }
    addLateralized(details, selection.facetArthrosis(), "facet arthrosis");
    addLateralized(
        details, selection.posteriorElementHypertrophy(), region.posteriorElementFinding());
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
      impressionParts.add(protrusionDescription(selection, true));
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

  private static String protrusionDescription(LevelSelection selection, boolean includeDisc) {
    List<String> locations =
        selection.protrusionLocations().stream().map(ProtrusionLocation::phrase).toList();
    String prefix = locations.isEmpty() ? "" : joinList(locations) + " ";
    String noun = locations.size() > 1 ? "protrusions" : "protrusion";
    return prefix + (includeDisc ? "disc " : "") + noun;
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

  enum SpineRegion {
    CERVICAL(
        ExamTemplate.CERVICAL_SPINE,
        "C-spine",
        "cervical",
        List.of("C2-3", "C3-4", "C4-5", "C5-6", "C6-7", "C7-T1"),
        List.of(AlignmentFinding.CERVICAL_STRAIGHTENING, AlignmentFinding.CERVICAL_REVERSAL),
        "Uncovertebral hypertrophy",
        "uncovertebral hypertrophy"),
    THORACIC(
        ExamTemplate.THORACIC_SPINE,
        "T-spine",
        "thoracic",
        List.of(
            "T1-2", "T2-3", "T3-4", "T4-5", "T5-6", "T6-7", "T7-8", "T8-9", "T9-10", "T10-11",
            "T11-12", "T12-L1"),
        List.of(
            AlignmentFinding.THORACIC_EXAGGERATED_KYPHOSIS, AlignmentFinding.THORACIC_SCOLIOSIS),
        "Ligamentum flavum hypertrophy",
        "ligamentum flavum hypertrophy"),
    LUMBAR(
        ExamTemplate.LUMBAR_SPINE,
        "L-spine",
        "lumbar",
        List.of("T12-L1", "L1-2", "L2-3", "L3-4", "L4-5", "L5-S1"),
        List.of(AlignmentFinding.LUMBAR_STRAIGHTENING, AlignmentFinding.LUMBAR_SCOLIOSIS),
        "Ligamentum flavum hypertrophy",
        "ligamentum flavum hypertrophy");

    private final ExamTemplate examTemplate;
    private final String formLabel;
    private final String anatomicAdjective;
    private final List<String> levels;
    private final List<AlignmentFinding> alignmentFindings;
    private final String posteriorElementLabel;
    private final String posteriorElementFinding;

    SpineRegion(
        ExamTemplate examTemplate,
        String formLabel,
        String anatomicAdjective,
        List<String> levels,
        List<AlignmentFinding> alignmentFindings,
        String posteriorElementLabel,
        String posteriorElementFinding) {
      this.examTemplate = examTemplate;
      this.formLabel = formLabel;
      this.anatomicAdjective = anatomicAdjective;
      this.levels = levels;
      this.alignmentFindings = alignmentFindings;
      this.posteriorElementLabel = posteriorElementLabel;
      this.posteriorElementFinding = posteriorElementFinding;
    }

    ExamTemplate examTemplate() {
      return examTemplate;
    }

    String formLabel() {
      return formLabel;
    }

    String anatomicAdjective() {
      return anatomicAdjective;
    }

    List<String> levels() {
      return levels;
    }

    List<AlignmentFinding> alignmentFindings() {
      return alignmentFindings;
    }

    String posteriorElementLabel() {
      return posteriorElementLabel;
    }

    String posteriorElementFinding() {
      return posteriorElementFinding;
    }

    static Optional<SpineRegion> fromExam(ExamTemplate exam) {
      return List.of(values()).stream().filter(region -> region.examTemplate == exam).findFirst();
    }
  }

  enum AlignmentFinding {
    NONE("", "", ""),
    CERVICAL_STRAIGHTENING(
        "Straightening",
        "Straightening of the cervical lordosis",
        "Straightened cervical lordosis"),
    CERVICAL_REVERSAL(
        "Reversal", "Reversal of the cervical lordosis", "Reversed cervical lordosis"),
    THORACIC_EXAGGERATED_KYPHOSIS(
        "Exaggerated kyphosis",
        "Exaggeration of the thoracic kyphosis",
        "Exaggerated thoracic kyphosis"),
    THORACIC_SCOLIOSIS("Scoliosis", "Thoracic scoliosis", "Thoracic scoliosis"),
    LUMBAR_STRAIGHTENING(
        "Straightening", "Straightening of the lumbar lordosis", "Straightened lumbar lordosis"),
    LUMBAR_SCOLIOSIS("Scoliosis", "Lumbar scoliosis", "Lumbar scoliosis");

    private final String label;
    private final String findingText;
    private final String impressionText;

    AlignmentFinding(String label, String findingText, String impressionText) {
      this.label = label;
      this.findingText = findingText;
      this.impressionText = impressionText;
    }

    String findingText() {
      return findingText;
    }

    String impressionText() {
      return impressionText;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  enum OverviewFinding {
    SPONDYLOSIS("Spondylosis", true),
    DISC_DEHYDRATION("Disc dehydration", false),
    DISC_HEIGHT_LOSS("Disc height loss", false),
    OSTEOPHYTES("Osteophytes", false),
    REACTIVE_ENDPLATE_CHANGES("Reactive endplate changes", false);

    private final String label;
    private final boolean includeInImpression;

    OverviewFinding(String label, boolean includeInImpression) {
      this.label = label;
      this.includeInImpression = includeInImpression;
    }

    String generalizedText(SpineRegion region) {
      return switch (this) {
        case SPONDYLOSIS -> capitalize(region.anatomicAdjective()) + " spondylosis";
        case DISC_DEHYDRATION -> "Multilevel disc desiccation";
        case DISC_HEIGHT_LOSS -> "Disc height loss";
        case OSTEOPHYTES -> "Endplate osteophytes";
        case REACTIVE_ENDPLATE_CHANGES -> "Reactive endplate marrow changes";
      };
    }

    String localizedText(SpineRegion region) {
      return this == SPONDYLOSIS
          ? capitalize(region.anatomicAdjective()) + " spondylosis"
          : generalizedText(region).replace("Multilevel disc", "Disc");
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

  enum ProtrusionLocation {
    CENTRAL("Central", "central"),
    LEFT_CENTRAL("Left central", "left central"),
    RIGHT_CENTRAL("Right central", "right central"),
    LEFT_SUBARTICULAR("Left subarticular", "left subarticular"),
    RIGHT_SUBARTICULAR("Right subarticular", "right subarticular"),
    LEFT_FORAMINAL("Left foraminal", "left foraminal"),
    RIGHT_FORAMINAL("Right foraminal", "right foraminal");

    private final String label;
    private final String phrase;

    ProtrusionLocation(String label, String phrase) {
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
      List<ProtrusionLocation> protrusionLocations,
      String freeText,
      Laterality facetArthrosis,
      Laterality posteriorElementHypertrophy,
      Laterality foraminalLaterality,
      Severity foraminalSeverity) {

    LevelSelection {
      level = ComposerText.clean(level);
      if (level.isBlank()) {
        throw new IllegalArgumentException("A spine level is required.");
      }
      List<ProtrusionLocation> requestedLocations =
          protrusionLocations == null ? List.of() : protrusionLocations;
      protrusionLocations =
          List.of(ProtrusionLocation.values()).stream()
              .filter(requestedLocations::contains)
              .toList();
      freeText = ComposerText.clean(freeText);
      facetArthrosis = Objects.requireNonNullElse(facetArthrosis, Laterality.NONE);
      posteriorElementHypertrophy =
          Objects.requireNonNullElse(posteriorElementHypertrophy, Laterality.NONE);
      foraminalLaterality = Objects.requireNonNullElse(foraminalLaterality, Laterality.NONE);
      foraminalSeverity = Objects.requireNonNullElse(foraminalSeverity, Severity.NONE);
      if ((foraminalLaterality == Laterality.NONE) != (foraminalSeverity == Severity.NONE)) {
        throw new IllegalArgumentException(
            "Foraminal stenosis requires both laterality and severity.");
      }
    }

    LevelSelection(
        String level,
        boolean bulge,
        boolean protrusion,
        String freeText,
        Laterality facetArthrosis,
        Laterality posteriorElementHypertrophy,
        Laterality foraminalLaterality,
        Severity foraminalSeverity) {
      this(
          level,
          bulge,
          protrusion ? List.of(ProtrusionLocation.CENTRAL) : List.of(),
          freeText,
          facetArthrosis,
          posteriorElementHypertrophy,
          foraminalLaterality,
          foraminalSeverity);
    }

    boolean protrusion() {
      return !protrusionLocations.isEmpty();
    }

    boolean hasFinding() {
      return bulge
          || protrusion()
          || ComposerText.hasText(freeText)
          || facetArthrosis != Laterality.NONE
          || posteriorElementHypertrophy != Laterality.NONE
          || foraminalSeverity != Severity.NONE;
    }
  }

  record Selection(
      SpineRegion region,
      AlignmentFinding alignment,
      Map<OverviewFinding, List<String>> overviewLevels,
      List<LevelSelection> levelSelections) {

    Selection {
      region = Objects.requireNonNull(region);
      SpineRegion selectedRegion = region;
      alignment = Objects.requireNonNullElse(alignment, AlignmentFinding.NONE);
      if (alignment != AlignmentFinding.NONE && !region.alignmentFindings().contains(alignment)) {
        throw new IllegalArgumentException(
            "Unsupported alignment finding for " + region.formLabel());
      }
      EnumMap<OverviewFinding, List<String>> overviewCopy = new EnumMap<>(OverviewFinding.class);
      if (overviewLevels != null) {
        overviewLevels.forEach(
            (finding, levels) -> {
              Objects.requireNonNull(finding);
              List<String> orderedLevels =
                  selectedRegion.levels().stream()
                      .filter(level -> levels != null && levels.contains(level))
                      .toList();
              overviewCopy.put(finding, orderedLevels);
            });
      }
      overviewLevels = Collections.unmodifiableMap(overviewCopy);
      levelSelections = levelSelections == null ? List.of() : List.copyOf(levelSelections);
      for (LevelSelection level : levelSelections) {
        if (!region.levels().contains(level.level())) {
          throw new IllegalArgumentException(
              "Unsupported " + region.formLabel() + " level: " + level.level());
        }
      }
    }
  }
}
