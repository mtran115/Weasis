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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.GeneratedFinding;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.KeyedFinding;

final class HipFindingBuilder {
  private HipFindingBuilder() {}

  static List<GeneratedFinding> generate(Selection selection) {
    return generateKeyed(selection).stream().map(KeyedFinding::finding).toList();
  }

  static List<KeyedFinding> generateKeyed(Selection selection) {
    Objects.requireNonNull(selection);
    List<KeyedFinding> findings = new ArrayList<>();
    if (selection.normal() && !selection.hasStructuredPositiveFinding()) {
      addFinding(findings, "normal", "Normal MRI of the hip");
    }
    if (selection.artifact()) {
      addFinding(findings, "artifact", "Multiple sequences are degraded due to artifact");
    }
    selection.sides().forEach((side, hip) -> addSideFindings(findings, side, hip));
    if (ComposerText.hasText(selection.freeText())) {
      findings.add(
          new KeyedFinding(
              "free-text", new GeneratedFinding(ComposerText.sentence(selection.freeText()), "")));
    }
    return List.copyOf(findings);
  }

  private static void addSideFindings(List<KeyedFinding> findings, Side side, SideSelection hip) {
    String key = side.name() + ":";
    String name = side.phrase();
    if (hip.bursitis() != Degree.NONE) {
      addFinding(
          findings,
          key + "bursitis",
          hip.bursitis().phrase() + " " + name + " greater trochanteric bursitis");
    }
    if (hip.hasLabralFinding()) {
      addFinding(findings, key + "labrum", labralDescription(name, hip));
    }
    hip.gluteals()
        .forEach(
            (tendon, finding) -> {
              String structure = name + " " + tendon.phrase();
              if (finding.tendinosis() != Degree.NONE) {
                addFinding(
                    findings,
                    key + "tendinosis:" + tendon.name(),
                    finding.tendinosis().phrase() + " " + structure + " insertional tendinosis");
              }
              if (finding.hasTear()) {
                addFinding(
                    findings,
                    key + "tear:" + tendon.name(),
                    finding.tearPhrase()
                        + " of the "
                        + structure
                        + " tendon insertion on the greater trochanter");
              }
            });
    String hamstring = name + " common hamstring tendon";
    if (hip.hamstring().tendinosis() != Degree.NONE) {
      addFinding(
          findings,
          key + "tendinosis:hamstring",
          hip.hamstring().tendinosis().phrase() + " tendinosis of the " + hamstring);
    }
    if (hip.hamstring().hasTear()) {
      addFinding(
          findings, key + "tear:hamstring", hip.hamstring().tearPhrase() + " of the " + hamstring);
    }
    if (hip.osteoarthrosis() != Degree.NONE) {
      addFinding(
          findings,
          key + "osteoarthrosis",
          hip.osteoarthrosis().phrase() + " osteoarthrosis of the " + name + " hip joint");
    }
    if (hip.effusion() != FluidAmount.NONE) {
      addFinding(
          findings,
          key + "effusion",
          hip.effusion().phrase()
              + " "
              + name
              + " hip joint effusion, nonspecific and potentially inflammatory");
    }
  }

  private static String labralDescription(String side, SideSelection hip) {
    StringBuilder text = new StringBuilder();
    if (hip.labralTear().isEmpty()) {
      text.append(capitalize(side)).append(" paralabral cyst");
    } else {
      text.append("Tear of the ").append(side).append(' ').append(labralRegion(hip.labralTear()));
      if (hip.paralabralCyst()) {
        text.append(", with an associated paralabral cyst");
      }
    }
    if (ComposerText.hasText(hip.labralExtras())) {
      // Extras typed in lower case continue the sentence; a capital starts a new one.
      boolean continues = Character.isLowerCase(hip.labralExtras().charAt(0));
      text.append(continues ? ", " : ". ").append(hip.labralExtras());
    }
    return text.toString();
  }

  /** Superior with one neighbour reads as a single region, such as anterior superior. */
  private static String labralRegion(List<LabralRegion> regions) {
    if (regions.size() == 1 && regions.getFirst() == LabralRegion.WHOLE) {
      return "labrum";
    }
    List<String> names =
        regions.stream()
            .filter(region -> region != LabralRegion.WHOLE)
            .map(LabralRegion::phrase)
            .toList();
    if (names.size() == 2 && regions.contains(LabralRegion.SUPERIOR)) {
      String neighbour = regions.contains(LabralRegion.ANTERIOR) ? "anterior" : "posterior";
      return neighbour + " superior labrum";
    }
    return switch (names.size()) {
      case 0 -> "labrum";
      case 1 -> names.getFirst() + " labrum";
      case 2 -> names.getFirst() + " and " + names.getLast() + " labrum";
      default -> "anterior, superior, and posterior labrum";
    };
  }

  private static void addFinding(List<KeyedFinding> findings, String key, String text) {
    String sentence = ComposerText.sentence(text);
    findings.add(new KeyedFinding(key, new GeneratedFinding(sentence, sentence)));
  }

  private static String capitalize(String value) {
    return Character.toUpperCase(value.charAt(0)) + value.substring(1);
  }

  enum Side {
    RIGHT("Right", 'r'),
    LEFT("Left", 'l');

    private final String label;
    private final char key;

    Side(String label, char key) {
      this.label = label;
      this.key = key;
    }

    String phrase() {
      return label.toLowerCase();
    }

    char key() {
      return key;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  enum Degree {
    NONE("None", ""),
    MINIMAL("Min", "Minimal"),
    MILD("Mild", "Mild"),
    MODERATE("Mod", "Moderate"),
    SEVERE("Severe", "Severe");

    private final String label;
    private final String phrase;

    Degree(String label, String phrase) {
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

  enum FluidAmount {
    NONE("None", ""),
    TRACE("Trace", "Trace"),
    SMALL("Small", "Small"),
    MODERATE("Mod", "Moderate"),
    LARGE("Large", "Large");

    private final String label;
    private final String phrase;

    FluidAmount(String label, String phrase) {
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

  /** {@code WHOLE} is a tear with no region named. */
  enum LabralRegion {
    WHOLE("Tear"),
    ANTERIOR("Anterior"),
    SUPERIOR("Superior"),
    POSTERIOR("Posterior");

    private final String label;

    LabralRegion(String label) {
      this.label = label;
    }

    String phrase() {
      return label.toLowerCase();
    }

    @Override
    public String toString() {
      return label;
    }
  }

  enum GlutealTendon {
    MEDIUS("Gluteus medius", "gluteus medius"),
    MINIMUS("Gluteus minimus", "gluteus minimus");

    private final String label;
    private final String phrase;

    GlutealTendon(String label, String phrase) {
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

  /** High grade alone counts as a partial tear. */
  record TendonSelection(Degree tendinosis, boolean partialTear, boolean highGrade) {
    TendonSelection {
      tendinosis = Objects.requireNonNullElse(tendinosis, Degree.NONE);
    }

    static TendonSelection empty() {
      return new TendonSelection(Degree.NONE, false, false);
    }

    boolean hasTear() {
      return partialTear || highGrade;
    }

    String tearPhrase() {
      return highGrade ? "High-grade partial tear" : "Partial tear";
    }

    boolean hasFinding() {
      return tendinosis != Degree.NONE || hasTear();
    }
  }

  record SideSelection(
      Degree bursitis,
      List<LabralRegion> labralTear,
      boolean paralabralCyst,
      String labralExtras,
      Map<GlutealTendon, TendonSelection> gluteals,
      TendonSelection hamstring,
      Degree osteoarthrosis,
      FluidAmount effusion) {

    SideSelection {
      bursitis = Objects.requireNonNullElse(bursitis, Degree.NONE);
      List<LabralRegion> requested = labralTear == null ? List.of() : labralTear;
      labralTear = List.of(LabralRegion.values()).stream().filter(requested::contains).toList();
      labralExtras = ComposerText.clean(labralExtras);
      EnumMap<GlutealTendon, TendonSelection> tendons = new EnumMap<>(GlutealTendon.class);
      if (gluteals != null) {
        gluteals.forEach(
            (tendon, finding) -> {
              if (finding != null && finding.hasFinding()) {
                tendons.put(tendon, finding);
              }
            });
      }
      gluteals = Collections.unmodifiableMap(tendons);
      hamstring = Objects.requireNonNullElseGet(hamstring, TendonSelection::empty);
      osteoarthrosis = Objects.requireNonNullElse(osteoarthrosis, Degree.NONE);
      effusion = Objects.requireNonNullElse(effusion, FluidAmount.NONE);
    }

    static SideSelection empty() {
      return new SideSelection(
          Degree.NONE, List.of(), false, "", Map.of(), null, Degree.NONE, FluidAmount.NONE);
    }

    boolean hasLabralFinding() {
      return !labralTear.isEmpty() || paralabralCyst;
    }

    boolean hasFinding() {
      return bursitis != Degree.NONE
          || hasLabralFinding()
          || !gluteals.isEmpty()
          || hamstring.hasFinding()
          || osteoarthrosis != Degree.NONE
          || effusion != FluidAmount.NONE;
    }
  }

  /** {@code sides} holds only the hips with a finding, right before left. */
  record Selection(
      boolean normal, boolean artifact, Map<Side, SideSelection> sides, String freeText) {

    Selection {
      EnumMap<Side, SideSelection> hips = new EnumMap<>(Side.class);
      if (sides != null) {
        sides.forEach(
            (side, hip) -> {
              if (hip != null && hip.hasFinding()) {
                hips.put(side, hip);
              }
            });
      }
      sides = Collections.unmodifiableMap(hips);
      freeText = ComposerText.clean(freeText);
    }

    boolean hasFinding() {
      return normal || artifact || hasStructuredPositiveFinding() || ComposerText.hasText(freeText);
    }

    boolean hasStructuredPositiveFinding() {
      return !sides.isEmpty();
    }
  }
}
