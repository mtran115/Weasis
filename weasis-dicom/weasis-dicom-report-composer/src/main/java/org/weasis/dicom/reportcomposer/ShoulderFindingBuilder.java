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

final class ShoulderFindingBuilder {
  private ShoulderFindingBuilder() {}

  static List<GeneratedFinding> generate(Selection selection) {
    Objects.requireNonNull(selection);
    List<GeneratedFinding> findings = new ArrayList<>();
    addGradedFinding(findings, selection.subcoracoidBursitis(), "subcoracoid bursitis");
    addGradedFinding(
        findings, selection.subacromialSubdeltoidBursitis(), "subacromial/subdeltoid bursitis");
    addGradedFinding(
        findings, selection.acJointOsteoarthrosis(), "acromioclavicular joint osteoarthrosis");
    for (RotatorCuffTendon tendon : RotatorCuffTendon.values()) {
      Degree degree = selection.rotatorCuffTendinosis().getOrDefault(tendon, Degree.NONE);
      CuffTearSelection tear =
          selection.rotatorCuffTears().getOrDefault(tendon, CuffTearSelection.empty());
      if (degree != Degree.NONE && !tear.backgroundTendinosis()) {
        addGradedFinding(findings, degree, tendon.phrase() + " tendinosis");
      }
      if (tear.hasFinding()) {
        addFinding(findings, cuffTearDescription(tendon, tear, degree));
      }
    }
    if (!selection.labralTearLocations().isEmpty()) {
      String locations =
          joinList(selection.labralTearLocations().stream().map(LabralLocation::phrase).toList());
      String cyst = selection.paralabralCyst() ? " with an adjacent paralabral cyst" : "";
      addFinding(findings, capitalize(locations) + " labral tear" + cyst);
    } else if (selection.paralabralCyst()) {
      addFinding(findings, "Paralabral cyst");
    }
    addGradedFinding(
        findings,
        selection.longHeadBicepsTenosynovitis(),
        "tenosynovitis of the long head of the biceps tendon");
    if (ComposerText.hasText(selection.freeText())) {
      findings.add(new GeneratedFinding(ComposerText.sentence(selection.freeText()), ""));
    }
    return List.copyOf(findings);
  }

  private static String cuffTearDescription(
      RotatorCuffTendon tendon, CuffTearSelection tear, Degree tendinosisDegree) {
    StringBuilder description =
        new StringBuilder(
                capitalize(
                    joinList(
                        tear.types().stream().map(type -> type.phrase(tear.highGrade())).toList())))
            .append(" of the ")
            .append(tendon.phrase())
            .append(" tendon");
    if (tear.atFootprint()) {
      description.append(" at the footprint");
    }
    if (tear.backgroundTendinosis()) {
      description.append(" with ");
      if (tendinosisDegree != Degree.NONE) {
        description.append(tendinosisDegree.phrase()).append(' ');
      }
      description.append("background tendinosis");
    }
    if (ComposerText.hasText(tear.details())) {
      description.append(". ").append(ComposerText.sentence(tear.details()));
    }
    return description.toString();
  }

  private static void addGradedFinding(
      List<GeneratedFinding> findings, Degree degree, String finding) {
    if (degree != Degree.NONE) {
      addFinding(findings, capitalize(degree.phrase()) + " " + finding);
    }
  }

  private static void addFinding(List<GeneratedFinding> findings, String finding) {
    String sentence = ComposerText.sentence(finding);
    findings.add(new GeneratedFinding(sentence, sentence));
  }

  private static String capitalize(String value) {
    return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
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

  enum Degree {
    NONE("None", ""),
    MINIMAL("Minimal", "minimal"),
    MILD("Mild", "mild"),
    MODERATE("Moderate", "moderate"),
    SEVERE("Severe", "severe");

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

  enum RotatorCuffTendon {
    SUPRASPINATUS("Supraspinatus", "supraspinatus"),
    INFRASPINATUS("Infraspinatus", "infraspinatus"),
    SUBSCAPULARIS("Subscapularis", "subscapularis"),
    TERES_MINOR("Teres minor", "teres minor");

    private final String label;
    private final String phrase;

    RotatorCuffTendon(String label, String phrase) {
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

  enum CuffTearType {
    INTERSTITIAL("Interstitial", "interstitial tear"),
    ARTICULAR_SURFACE("Articular surface", "partial-thickness articular-surface tear"),
    BURSAL_SURFACE("Bursal surface", "partial-thickness bursal-surface tear"),
    FULL_THICKNESS("Full-thickness", "full-thickness tear");

    private final String label;
    private final String phrase;

    CuffTearType(String label, String phrase) {
      this.label = label;
      this.phrase = phrase;
    }

    String phrase(boolean highGrade) {
      return highGrade && this != FULL_THICKNESS ? "high-grade " + phrase : phrase;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  enum LabralLocation {
    ANTERIOR("Anterior", "anterior"),
    SUPERIOR("Superior", "superior"),
    POSTERIOR("Posterior", "posterior"),
    INFERIOR("Inferior", "inferior");

    private final String label;
    private final String phrase;

    LabralLocation(String label, String phrase) {
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

  record CuffTearSelection(
      List<CuffTearType> types,
      boolean highGrade,
      boolean atFootprint,
      boolean backgroundTendinosis,
      String details) {

    CuffTearSelection {
      List<CuffTearType> requestedTypes = types == null ? List.of() : types;
      types = List.of(CuffTearType.values()).stream().filter(requestedTypes::contains).toList();
      details = ComposerText.clean(details);
    }

    static CuffTearSelection empty() {
      return new CuffTearSelection(List.of(), false, false, false, "");
    }

    boolean hasFinding() {
      return !types.isEmpty();
    }
  }

  record Selection(
      Degree subcoracoidBursitis,
      Degree subacromialSubdeltoidBursitis,
      Degree acJointOsteoarthrosis,
      Map<RotatorCuffTendon, Degree> rotatorCuffTendinosis,
      Map<RotatorCuffTendon, CuffTearSelection> rotatorCuffTears,
      List<LabralLocation> labralTearLocations,
      boolean paralabralCyst,
      Degree longHeadBicepsTenosynovitis,
      String freeText) {

    Selection(
        Degree subcoracoidBursitis,
        Degree subacromialSubdeltoidBursitis,
        Degree acJointOsteoarthrosis,
        Map<RotatorCuffTendon, Degree> rotatorCuffTendinosis,
        List<LabralLocation> labralTearLocations,
        Degree longHeadBicepsTenosynovitis,
        String freeText) {
      this(
          subcoracoidBursitis,
          subacromialSubdeltoidBursitis,
          acJointOsteoarthrosis,
          rotatorCuffTendinosis,
          Map.of(),
          labralTearLocations,
          false,
          longHeadBicepsTenosynovitis,
          freeText);
    }

    Selection {
      subcoracoidBursitis = Objects.requireNonNullElse(subcoracoidBursitis, Degree.NONE);
      subacromialSubdeltoidBursitis =
          Objects.requireNonNullElse(subacromialSubdeltoidBursitis, Degree.NONE);
      acJointOsteoarthrosis = Objects.requireNonNullElse(acJointOsteoarthrosis, Degree.NONE);
      EnumMap<RotatorCuffTendon, Degree> tendinosis = new EnumMap<>(RotatorCuffTendon.class);
      if (rotatorCuffTendinosis != null) {
        for (RotatorCuffTendon tendon : RotatorCuffTendon.values()) {
          Degree degree = rotatorCuffTendinosis.get(tendon);
          if (degree != null && degree != Degree.NONE) {
            tendinosis.put(tendon, degree);
          }
        }
      }
      rotatorCuffTendinosis = Collections.unmodifiableMap(tendinosis);
      EnumMap<RotatorCuffTendon, CuffTearSelection> tears = new EnumMap<>(RotatorCuffTendon.class);
      if (rotatorCuffTears != null) {
        for (RotatorCuffTendon tendon : RotatorCuffTendon.values()) {
          CuffTearSelection tear = rotatorCuffTears.get(tendon);
          if (tear != null && tear.hasFinding()) {
            tears.put(tendon, tear);
          }
        }
      }
      rotatorCuffTears = Collections.unmodifiableMap(tears);
      List<LabralLocation> requestedLocations =
          labralTearLocations == null ? List.of() : labralTearLocations;
      labralTearLocations =
          List.of(LabralLocation.values()).stream().filter(requestedLocations::contains).toList();
      longHeadBicepsTenosynovitis =
          Objects.requireNonNullElse(longHeadBicepsTenosynovitis, Degree.NONE);
      freeText = ComposerText.clean(freeText);
    }

    boolean hasFinding() {
      return subcoracoidBursitis != Degree.NONE
          || subacromialSubdeltoidBursitis != Degree.NONE
          || acJointOsteoarthrosis != Degree.NONE
          || !rotatorCuffTendinosis.isEmpty()
          || !rotatorCuffTears.isEmpty()
          || !labralTearLocations.isEmpty()
          || paralabralCyst
          || longHeadBicepsTenosynovitis != Degree.NONE
          || ComposerText.hasText(freeText);
    }
  }
}
