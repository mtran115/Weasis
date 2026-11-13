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

final class AnkleFindingBuilder {
  private static final String EFFUSION_SUFFIX =
      " ankle joint effusion, nonspecific and potentially inflammatory";

  private AnkleFindingBuilder() {}

  static List<GeneratedFinding> generate(Selection selection) {
    return generateKeyed(selection).stream().map(KeyedFinding::finding).toList();
  }

  static List<KeyedFinding> generateKeyed(Selection selection) {
    Objects.requireNonNull(selection);
    List<KeyedFinding> findings = new ArrayList<>();
    if (selection.normal() && !selection.hasStructuredPositiveFinding()) {
      addFinding(findings, "normal", "Normal MRI of the ankle");
    }
    for (Tendon tendon : Tendon.values()) {
      Degree degree = selection.tenosynovitis().get(tendon);
      if (degree != null) {
        addFinding(
            findings,
            "tenosynovitis:" + tendon.name(),
            degree.phrase() + " " + tendon.tenosynovitis());
      }
      if (selection.splitTears().contains(tendon)) {
        addFinding(
            findings,
            "split-tear:" + tendon.name(),
            "Partial split tear of the " + tendon.phrase());
      }
    }
    if (selection.achillesTendinosis() != Degree.NONE) {
      addFinding(
          findings, "achilles", selection.achillesTendinosis().phrase() + " Achilles tendinosis");
    }
    selection
        .ligaments()
        .forEach(
            (ligament, status) ->
                addFinding(
                    findings,
                    "ligament:" + ligament.name(),
                    status.phrase() + " of the " + ligament.phrase()));
    if (selection.posteriorEffusion() != FluidAmount.NONE) {
      addFinding(
          findings,
          "effusion:posterior",
          selection.posteriorEffusion().phrase() + " posterior" + EFFUSION_SUFFIX);
    }
    if (selection.anteriorEffusion() != FluidAmount.NONE) {
      addFinding(
          findings,
          "effusion:anterior",
          selection.anteriorEffusion().phrase() + " anterior" + EFFUSION_SUFFIX);
    }
    if (!selection.swelling().isEmpty()) {
      addFinding(
          findings,
          "swelling",
          "Soft tissue swelling along the "
              + String.join(
                  " and ", selection.swelling().stream().map(SwellingSide::phrase).toList())
              + " ankle");
    }
    selection
        .contusions()
        .forEach(
            (bone, degree) ->
                addFinding(
                    findings,
                    "contusion:" + bone.name(),
                    degree.phrase() + " bony contusion of the " + bone.phrase()));
    if (ComposerText.hasText(selection.freeText())) {
      findings.add(
          new KeyedFinding(
              "free-text", new GeneratedFinding(ComposerText.sentence(selection.freeText()), "")));
    }
    return List.copyOf(findings);
  }

  private static void addFinding(List<KeyedFinding> findings, String key, String text) {
    String sentence = ComposerText.sentence(text);
    findings.add(new KeyedFinding(key, new GeneratedFinding(sentence, sentence)));
  }

  enum Tendon {
    POSTERIOR_TIBIAL("Posterior tibial", "posterior tibial tendon"),
    PERONEUS_LONGUS("Peroneus longus", "peroneus longus tendon"),
    PERONEUS_BREVIS("Peroneus brevis", "peroneus brevis tendon"),
    FLEXOR_HALLUCIS_LONGUS("Flexor hallucis longus", "flexor hallucis longus tendon");

    private final String label;
    private final String phrase;

    Tendon(String label, String phrase) {
      this.label = label;
      this.phrase = phrase;
    }

    String phrase() {
      return phrase;
    }

    String tenosynovitis() {
      return this == POSTERIOR_TIBIAL
          ? "posterior tibial tenosynovitis"
          : "tenosynovitis of the " + phrase;
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

  enum Ligament {
    ATFL("ATFL", "anterior talofibular ligament"),
    AITFL("AITFL", "anterior inferior tibiofibular ligament"),
    CFL("CFL", "calcaneofibular ligament"),
    DELTOID("Deltoid", "deltoid ligament complex");

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
    MILD_SPRAIN("Mild sprain", "Mild sprain"),
    MODERATE_SPRAIN("Mod sprain", "Moderate sprain"),
    PARTIAL_TEAR("Partial tear", "Partial tear"),
    COMPLETE_TEAR("Complete tear", "Complete tear");

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

  enum SwellingSide {
    MEDIAL("Medial", "medial"),
    LATERAL("Lateral", "lateral");

    private final String label;
    private final String phrase;

    SwellingSide(String label, String phrase) {
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
    MEDIAL_MALLEOLUS("Medial malleolus"),
    LATERAL_MALLEOLUS("Lateral malleolus"),
    DISTAL_TIBIA("Distal tibia"),
    TALUS("Talus"),
    CALCANEUS("Calcaneus"),
    NAVICULAR("Navicular"),
    CUBOID("Cuboid"),
    MEDIAL_CUNEIFORM("Medial cuneiform"),
    MIDDLE_CUNEIFORM("Middle cuneiform"),
    LATERAL_CUNEIFORM("Lateral cuneiform");

    private final String label;

    Bone(String label) {
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

  /** The maps hold only the structures with a finding, in anatomic order. */
  record Selection(
      boolean normal,
      Map<Tendon, Degree> tenosynovitis,
      List<Tendon> splitTears,
      Degree achillesTendinosis,
      Map<Ligament, LigamentStatus> ligaments,
      FluidAmount posteriorEffusion,
      FluidAmount anteriorEffusion,
      List<SwellingSide> swelling,
      Map<Bone, Degree> contusions,
      String freeText) {

    Selection {
      tenosynovitis = withFindings(Tendon.class, tenosynovitis, Degree.NONE);
      splitTears = inOrder(Tendon.values(), splitTears);
      achillesTendinosis = Objects.requireNonNullElse(achillesTendinosis, Degree.NONE);
      ligaments = withFindings(Ligament.class, ligaments, LigamentStatus.NONE);
      posteriorEffusion = Objects.requireNonNullElse(posteriorEffusion, FluidAmount.NONE);
      anteriorEffusion = Objects.requireNonNullElse(anteriorEffusion, FluidAmount.NONE);
      swelling = inOrder(SwellingSide.values(), swelling);
      contusions = withFindings(Bone.class, contusions, Degree.NONE);
      freeText = ComposerText.clean(freeText);
    }

    boolean hasFinding() {
      return normal || hasStructuredPositiveFinding() || ComposerText.hasText(freeText);
    }

    boolean hasStructuredPositiveFinding() {
      return !tenosynovitis.isEmpty()
          || !splitTears.isEmpty()
          || achillesTendinosis != Degree.NONE
          || !ligaments.isEmpty()
          || posteriorEffusion != FluidAmount.NONE
          || anteriorEffusion != FluidAmount.NONE
          || !swelling.isEmpty()
          || !contusions.isEmpty();
    }

    private static <K extends Enum<K>, V> Map<K, V> withFindings(
        Class<K> keyType, Map<K, V> requested, V emptyValue) {
      EnumMap<K, V> findings = new EnumMap<>(keyType);
      if (requested != null) {
        requested.forEach(
            (key, value) -> {
              if (value != null && value != emptyValue) {
                findings.put(key, value);
              }
            });
      }
      return Collections.unmodifiableMap(findings);
    }

    private static <T> List<T> inOrder(T[] values, List<T> requested) {
      return requested == null
          ? List.of()
          : List.of(values).stream().filter(requested::contains).toList();
    }
  }
}
