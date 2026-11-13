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
import java.util.function.Predicate;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.GeneratedFinding;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.KeyedFinding;

final class WristFindingBuilder {
  private static final String EDEMA_CAUSE = " may be related to bony contusion or osteoarthrosis";

  private WristFindingBuilder() {}

  static List<GeneratedFinding> generate(Selection selection) {
    return generateKeyed(selection).stream().map(KeyedFinding::finding).toList();
  }

  static List<KeyedFinding> generateKeyed(Selection selection) {
    Objects.requireNonNull(selection);
    List<KeyedFinding> findings = new ArrayList<>();
    if (selection.normal() && !selection.hasStructuredPositiveFinding()) {
      addFinding(findings, "normal", "Normal MRI of the wrist");
    }
    if (selection.limitedExam() != LimitedExam.NONE) {
      addFinding(findings, "limited", selection.limitedExam().phrase());
    }
    selection
        .ligaments()
        .forEach(
            (ligament, tear) ->
                addFinding(
                    findings,
                    "ligament:" + ligament.name(),
                    possible(tear.possible(), tear.status().phrase())
                        + " of the "
                        + ligament.phrase()));
    if (selection.tfcc() != TfccTear.NONE) {
      addFinding(findings, "tfcc", possible(selection.tfccPossible(), selection.tfcc().phrase()));
    }
    selection.tendons().forEach((tendon, finding) -> addTendonFindings(findings, tendon, finding));
    selection.bones().forEach((bone, finding) -> addBoneFindings(findings, bone, finding));
    if (selection.cyst().hasFinding()) {
      addFinding(findings, "cyst", cystDescription(selection.cyst()));
    }
    if (!selection.swelling().isEmpty()) {
      addFinding(
          findings,
          "swelling",
          "Soft tissue swelling along the " + sides(selection.swelling()) + " wrist");
    }
    if (ComposerText.hasText(selection.freeText())) {
      findings.add(
          new KeyedFinding(
              "free-text", new GeneratedFinding(ComposerText.sentence(selection.freeText()), "")));
    }
    return List.copyOf(findings);
  }

  private static void addTendonFindings(
      List<KeyedFinding> findings, Tendon tendon, TendonSelection finding) {
    if (finding.tenosynovitis() != Degree.NONE) {
      addFinding(
          findings,
          "tenosynovitis:" + tendon.name(),
          finding.tenosynovitis().phrase() + " tenosynovitis of the " + tendon.phrase());
    }
    if (finding.tear() != TendonTear.NONE) {
      addFinding(
          findings,
          "tendon-tear:" + tendon.name(),
          finding.tear().phrase() + " of the " + tendon.phrase());
    }
  }

  /** The extras follow the bone's first sentence. */
  private static void addBoneFindings(
      List<KeyedFinding> findings, Bone bone, BoneSelection finding) {
    List<Map.Entry<String, String>> sentences = new ArrayList<>();
    String degree = finding.degree() == Degree.NONE ? "" : finding.degree().phrase() + " ";
    if (finding.fracture()) {
      sentences.add(
          Map.entry(
              "fracture",
              "Nondisplaced fracture of the "
                  + bone.phrase()
                  + " with surrounding bone marrow edema"));
    }
    if (finding.edema()) {
      String changes =
          finding.cysticChange() ? "bone marrow edema and cystic change" : "bone marrow edema";
      sentences.add(
          Map.entry("edema", degree + changes + " involving the " + bone.phrase() + EDEMA_CAUSE));
    } else {
      if (finding.degree() != Degree.NONE) {
        sentences.add(Map.entry("contusion", degree + "bony contusion of the " + bone.phrase()));
      }
      if (finding.cysticChange()) {
        sentences.add(Map.entry("cystic", "Cystic changes of the " + bone.phrase()));
      }
    }
    for (var sentence : sentences) {
      boolean first = sentence == sentences.getFirst();
      addFinding(
          findings,
          "bone:" + bone.name() + ":" + sentence.getKey(),
          first ? withExtras(sentence.getValue(), finding.extras()) : sentence.getValue());
    }
  }

  private static String cystDescription(CystSelection cyst) {
    String size = cyst.size();
    String article = size.matches("(8|11|18).*") ? "An " : "A ";
    StringBuilder text =
        new StringBuilder(article)
            .append(size.isEmpty() ? "" : size + " ")
            .append("ganglion or synovial cyst along the ")
            .append(sides(cyst.sides()))
            .append(" wrist");
    if (!cyst.level().isEmpty()) {
      boolean hasArticle = cyst.level().toLowerCase().startsWith("the ");
      text.append(" at the level of ").append(hasArticle ? "" : "the ").append(cyst.level());
    }
    return text.toString();
  }

  /** Extras typed in lower case continue the sentence; a capital starts a new one. */
  private static String withExtras(String text, String extras) {
    if (!ComposerText.hasText(extras)) {
      return text;
    }
    return text + (Character.isLowerCase(extras.charAt(0)) ? ", " : ". ") + extras;
  }

  private static String possible(boolean possible, String phrase) {
    return possible
        ? "Possible " + Character.toLowerCase(phrase.charAt(0)) + phrase.substring(1)
        : phrase;
  }

  private static String sides(List<WristSide> sides) {
    return String.join(" and ", sides.stream().map(WristSide::phrase).toList());
  }

  private static void addFinding(List<KeyedFinding> findings, String key, String text) {
    String sentence = ComposerText.sentence(capitalize(text));
    findings.add(new KeyedFinding(key, new GeneratedFinding(sentence, sentence)));
  }

  private static String capitalize(String value) {
    return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
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

  enum LimitedExam {
    NONE("None", ""),
    MOTION("Motion", "Limited exam due to motion"),
    ARTIFACT("Artifact", "Multiple sequences are degraded due to artifact");

    private final String label;
    private final String phrase;

    LimitedExam(String label, String phrase) {
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

  enum Ligament {
    SCAPHOLUNATE("Scapholunate ligament", "scapholunate ligament"),
    LUNOTRIQUETRAL("Lunotriquetral ligament", "lunotriquetral ligament");

    private final String label;
    private final String phrase;

    Ligament(String label, String phrase) {
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

  enum LigamentStatus {
    NONE("None", ""),
    TEAR("Tear", "Tear"),
    PARTIAL_TEAR("Partial tear", "Partial tear");

    private final String label;
    private final String phrase;

    LigamentStatus(String label, String phrase) {
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

  enum TfccTear {
    NONE("None", ""),
    TEAR("Tear", "Tear of the triangular fibrocartilage complex"),
    RADIAL_ATTACHMENT("Radial attachment", "Tear of the radial attachment of the TFCC"),
    ULNAR_ATTACHMENT("Ulnar attachment", "Tear of the ulnar attachment of the TFCC");

    private final String label;
    private final String phrase;

    TfccTear(String label, String phrase) {
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

  /** In extensor compartment order, which is the order of the tendon chords e1 to e7. */
  enum Tendon {
    ABDUCTOR_POLLICIS_LONGUS("APL", "abductor pollicis longus tendon"),
    EXTENSOR_POLLICIS_BREVIS("EPB", "extensor pollicis brevis tendon"),
    EXTENSOR_CARPI_RADIALIS_LONGUS("ECRL", "extensor carpi radialis longus tendon"),
    EXTENSOR_CARPI_RADIALIS_BREVIS("ECRB", "extensor carpi radialis brevis tendon"),
    EXTENSOR_POLLICIS_LONGUS("EPL", "extensor pollicis longus tendon"),
    EXTENSOR_DIGITORUM("Ext digitorum", "extensor digitorum tendons"),
    EXTENSOR_CARPI_ULNARIS("ECU", "extensor carpi ulnaris tendon");

    private final String label;
    private final String phrase;

    Tendon(String label, String phrase) {
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

  enum TendonTear {
    NONE("None", ""),
    SPLIT("Split tear", "Split tear"),
    PARTIAL("Partial tear", "Partial tear"),
    COMPLETE("Complete tear", "Complete tear");

    private final String label;
    private final String phrase;

    TendonTear(String label, String phrase) {
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

  /** In the order of the bone chords: b1 to b9, then b0. */
  enum Bone {
    DISTAL_RADIUS("Distal radius", "distal radius"),
    DISTAL_ULNA("Distal ulna", "distal ulna"),
    SCAPHOID("Scaphoid", "scaphoid"),
    LUNATE("Lunate", "lunate"),
    TRIQUETRUM("Triquetrum", "triquetrum"),
    TRAPEZIUM("Trapezium", "trapezium"),
    TRAPEZOID("Trapezoid", "trapezoid"),
    CAPITATE("Capitate", "capitate"),
    HAMATE("Hamate", "hamate"),
    SECOND_METACARPAL_BASE("2nd MC base", "base of the second metacarpal");

    private final String label;
    private final String phrase;

    Bone(String label, String phrase) {
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

  enum WristSide {
    VOLAR("Volar"),
    DORSAL("Dorsal"),
    RADIAL("Radial");

    private final String label;

    WristSide(String label) {
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

  record LigamentTear(LigamentStatus status, boolean possible) {
    LigamentTear {
      status = Objects.requireNonNullElse(status, LigamentStatus.NONE);
    }
  }

  record TendonSelection(Degree tenosynovitis, TendonTear tear) {
    TendonSelection {
      tenosynovitis = Objects.requireNonNullElse(tenosynovitis, Degree.NONE);
      tear = Objects.requireNonNullElse(tear, TendonTear.NONE);
    }

    boolean hasFinding() {
      return tenosynovitis != Degree.NONE || tear != TendonTear.NONE;
    }
  }

  /**
   * The degree is that of a bony contusion, or of the marrow edema when {@code edema} is set. Edema
   * with no stated degree is still a finding.
   */
  record BoneSelection(
      Degree degree, boolean fracture, boolean cysticChange, boolean edema, String extras) {
    BoneSelection {
      degree = Objects.requireNonNullElse(degree, Degree.NONE);
      extras = ComposerText.clean(extras);
    }

    boolean hasFinding() {
      return degree != Degree.NONE || fracture || cysticChange || edema;
    }
  }

  /** A cyst is reported once it has a side; the size and level are optional. */
  record CystSelection(List<WristSide> sides, String size, String level) {
    CystSelection {
      sides = inOrder(sides);
      size = ComposerText.clean(size);
      level = ComposerText.clean(level);
    }

    static CystSelection empty() {
      return new CystSelection(List.of(), "", "");
    }

    boolean hasFinding() {
      return !sides.isEmpty();
    }
  }

  /** The maps hold only the structures with a finding, in form order. */
  record Selection(
      boolean normal,
      LimitedExam limitedExam,
      Map<Ligament, LigamentTear> ligaments,
      TfccTear tfcc,
      boolean tfccPossible,
      Map<Tendon, TendonSelection> tendons,
      Map<Bone, BoneSelection> bones,
      CystSelection cyst,
      List<WristSide> swelling,
      String freeText) {

    Selection {
      limitedExam = Objects.requireNonNullElse(limitedExam, LimitedExam.NONE);
      ligaments =
          withFindings(Ligament.class, ligaments, tear -> tear.status() != LigamentStatus.NONE);
      tfcc = Objects.requireNonNullElse(tfcc, TfccTear.NONE);
      tendons = withFindings(Tendon.class, tendons, TendonSelection::hasFinding);
      bones = withFindings(Bone.class, bones, BoneSelection::hasFinding);
      cyst = Objects.requireNonNullElseGet(cyst, CystSelection::empty);
      swelling = inOrder(swelling);
      freeText = ComposerText.clean(freeText);
    }

    boolean hasFinding() {
      return normal
          || limitedExam != LimitedExam.NONE
          || hasStructuredPositiveFinding()
          || ComposerText.hasText(freeText);
    }

    boolean hasStructuredPositiveFinding() {
      return !ligaments.isEmpty()
          || tfcc != TfccTear.NONE
          || !tendons.isEmpty()
          || !bones.isEmpty()
          || cyst.hasFinding()
          || !swelling.isEmpty();
    }

    private static <K extends Enum<K>, V> Map<K, V> withFindings(
        Class<K> keyType, Map<K, V> requested, Predicate<V> hasFinding) {
      EnumMap<K, V> findings = new EnumMap<>(keyType);
      if (requested != null) {
        requested.forEach(
            (key, value) -> {
              if (value != null && hasFinding.test(value)) {
                findings.put(key, value);
              }
            });
      }
      return Collections.unmodifiableMap(findings);
    }
  }

  private static List<WristSide> inOrder(List<WristSide> requested) {
    return requested == null
        ? List.of()
        : List.of(WristSide.values()).stream().filter(requested::contains).toList();
  }
}
