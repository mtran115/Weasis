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

final class KneeFindingBuilder {
  private KneeFindingBuilder() {}

  static List<GeneratedFinding> generate(Selection selection) {
    Objects.requireNonNull(selection);
    List<GeneratedFinding> findings = new ArrayList<>();

    if (selection.normal() && !selection.hasStructuredPositiveFinding()) {
      addFinding(findings, "Normal MRI of the knee");
    }
    for (Meniscus meniscus : Meniscus.values()) {
      addMeniscusFindings(
          findings,
          meniscus,
          selection.menisci().getOrDefault(meniscus, MeniscusSelection.empty()));
    }
    for (Ligament ligament : Ligament.values()) {
      LigamentStatus status = selection.ligaments().getOrDefault(ligament, LigamentStatus.NONE);
      if (status != LigamentStatus.NONE
          || (ligament == Ligament.ACL && selection.aclReconstruction())) {
        addFinding(findings, ligamentDescription(ligament, status, selection.aclReconstruction()));
      }
    }
    for (ExtensorTendon tendon : ExtensorTendon.values()) {
      TendonStatus status = selection.extensorMechanism().getOrDefault(tendon, TendonStatus.NONE);
      if (status != TendonStatus.NONE) {
        addFinding(findings, status.phrase() + " of the " + tendon.phrase());
      }
    }
    for (Compartment compartment : Compartment.values()) {
      Degree degree = selection.osteoarthrosis().getOrDefault(compartment, Degree.NONE);
      if (degree != Degree.NONE) {
        addFinding(findings, degree.phrase() + " osteoarthrosis of the " + compartment.phrase());
      }
    }
    addMarrowFinding(findings, selection.marrow());
    if (selection.effusion() != FluidAmount.NONE) {
      addFinding(findings, selection.effusion().phrase() + " knee joint effusion");
    }
    if (selection.synovitis()) {
      addFinding(findings, "Knee joint synovitis");
    }
    if (selection.poplitealCyst() != FluidAmount.NONE) {
      addFinding(findings, selection.poplitealCyst().phrase() + " popliteal cyst");
    }
    if (selection.prepatellarBursitis() != Degree.NONE) {
      addFinding(findings, selection.prepatellarBursitis().phrase() + " prepatellar bursitis");
    }
    if (selection.softTissueEdema() != Degree.NONE) {
      String locations =
          selection.softTissueLocations().isEmpty()
              ? "anterior knee"
              : joinList(
                  selection.softTissueLocations().stream()
                      .map(SoftTissueLocation::phrase)
                      .toList());
      addFinding(
          findings, selection.softTissueEdema().phrase() + " " + locations + " soft tissue edema");
    }
    if (ComposerText.hasText(selection.freeText())) {
      findings.add(new GeneratedFinding(ComposerText.sentence(selection.freeText()), ""));
    }
    return List.copyOf(findings);
  }

  private static void addMeniscusFindings(
      List<GeneratedFinding> findings, Meniscus meniscus, MeniscusSelection selection) {
    String involvedStructure = meniscusStructure(meniscus, selection.regions());
    if (selection.intrasubstanceDegeneration()) {
      addFinding(findings, "Intrasubstance degeneration of the " + involvedStructure);
    }
    if (selection.tearType() != MeniscusTearType.NONE) {
      String tear =
          selection.tearType() == MeniscusTearType.ROOT
              ? "Posterior root tear of the " + meniscus.phrase() + " meniscus"
              : selection.tearType().phrase() + " of the " + involvedStructure;
      if (selection.parameniscalCyst()) {
        tear += " with an associated parameniscal cyst";
      }
      addFinding(findings, tear);
    }
    if (selection.maceration()) {
      addFinding(findings, "Maceration of the " + involvedStructure);
    }
    if (selection.extrusion()) {
      addFinding(findings, "Extrusion of the " + meniscus.phrase() + " meniscus");
    }
    if (selection.parameniscalCyst() && selection.tearType() == MeniscusTearType.NONE) {
      addFinding(findings, "Parameniscal cyst adjacent to the " + meniscus.phrase() + " meniscus");
    }
  }

  private static String meniscusStructure(Meniscus meniscus, List<MeniscusRegion> regions) {
    if (regions.isEmpty()) {
      return meniscus.phrase() + " meniscus";
    }
    return joinList(regions.stream().map(MeniscusRegion::phrase).toList())
        + " of the "
        + meniscus.phrase()
        + " meniscus";
  }

  private static String ligamentDescription(
      Ligament ligament, LigamentStatus status, boolean aclReconstruction) {
    if (ligament != Ligament.ACL || !aclReconstruction) {
      return status.phrase() + " of the " + ligament.phrase();
    }
    return switch (status) {
      case NONE -> "Postsurgical changes of ACL reconstruction";
      case MILD_SPRAIN -> "Postsurgical changes of ACL reconstruction with mild graft sprain";
      case MODERATE_SPRAIN ->
          "Postsurgical changes of ACL reconstruction with moderate graft sprain";
      case PARTIAL_TEAR ->
          "Postsurgical changes of ACL reconstruction with partial tear of the graft";
      case COMPLETE_TEAR ->
          "Postsurgical changes of ACL reconstruction with complete tear of the graft";
      case SCARRING -> "Postsurgical changes of ACL reconstruction with graft scarring";
    };
  }

  private static void addMarrowFinding(List<GeneratedFinding> findings, MarrowSelection selection) {
    if (!selection.hasFinding()) {
      return;
    }
    String severity =
        selection.degree() == Degree.NONE ? "" : selection.degree().phrase().toLowerCase() + " ";
    String subchondral = selection.subchondral() ? "subchondral " : "";
    String locations =
        joinList(selection.locations().stream().map(MarrowLocation::phrase).toList());
    String description =
        switch (selection.type()) {
          case EDEMA -> severity + subchondral + "bone marrow edema of the " + locations;
          case CONTUSION ->
              severity
                  + subchondral
                  + (selection.locations().size() == 1
                      ? "bone contusion of the "
                      : "bone contusions of the ")
                  + locations;
          case NONE -> "";
        };
    addFinding(findings, description);
  }

  private static void addFinding(List<GeneratedFinding> findings, String text) {
    String sentence = ComposerText.sentence(capitalize(text));
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

  enum Meniscus {
    MEDIAL("Medial", "medial"),
    LATERAL("Lateral", "lateral");

    private final String label;
    private final String phrase;

    Meniscus(String label, String phrase) {
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

  enum MeniscusRegion {
    ANTERIOR_HORN("Anterior horn", "anterior horn"),
    BODY("Body", "body"),
    POSTERIOR_HORN("Posterior horn", "posterior horn");

    private final String label;
    private final String phrase;

    MeniscusRegion(String label, String phrase) {
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

  enum MeniscusTearType {
    NONE("None", ""),
    TEAR("Tear", "Tear"),
    HORIZONTAL("Horizontal", "Horizontal tear"),
    VERTICAL("Vertical", "Vertical tear"),
    RADIAL("Radial", "Radial tear"),
    COMPLEX("Complex", "Complex tear"),
    ROOT("Root", "Root tear");

    private final String label;
    private final String phrase;

    MeniscusTearType(String label, String phrase) {
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
    ACL("ACL", "anterior cruciate ligament"),
    PCL("PCL", "posterior cruciate ligament"),
    MCL("MCL", "medial collateral ligament"),
    LCL("LCL", "lateral collateral ligament complex");

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
    MODERATE_SPRAIN("Moderate sprain", "Moderate sprain"),
    PARTIAL_TEAR("Partial tear", "Partial tear"),
    COMPLETE_TEAR("Complete tear", "Complete tear"),
    SCARRING("Scarring", "Scarring");

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

  enum ExtensorTendon {
    QUADRICEPS("Quadriceps", "quadriceps tendon"),
    PATELLAR("Patellar", "patellar tendon");

    private final String label;
    private final String phrase;

    ExtensorTendon(String label, String phrase) {
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

  enum TendonStatus {
    NONE("None", ""),
    MINIMAL_TENDINOSIS("Minimal tendinosis", "Minimal tendinosis"),
    MILD_TENDINOSIS("Mild tendinosis", "Mild tendinosis"),
    MODERATE_TENDINOSIS("Moderate tendinosis", "Moderate tendinosis"),
    SEVERE_TENDINOSIS("Severe tendinosis", "Severe tendinosis"),
    PARTIAL_TEAR("Partial tear", "Partial tear"),
    COMPLETE_TEAR("Complete tear", "Complete tear");

    private final String label;
    private final String phrase;

    TendonStatus(String label, String phrase) {
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

  enum Compartment {
    PATELLOFEMORAL("Patellofemoral", "patellofemoral compartment"),
    MEDIAL("Medial", "medial femorotibial compartment"),
    LATERAL("Lateral", "lateral femorotibial compartment");

    private final String label;
    private final String phrase;

    Compartment(String label, String phrase) {
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

  enum Degree {
    NONE("None", ""),
    MINIMAL("Minimal", "Minimal"),
    MILD("Mild", "Mild"),
    MODERATE("Moderate", "Moderate"),
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

  enum MarrowFindingType {
    NONE("None"),
    EDEMA("Edema"),
    CONTUSION("Contusion");

    private final String label;

    MarrowFindingType(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  enum MarrowLocation {
    PATELLA("Patella", "patella"),
    MEDIAL_FEMORAL_CONDYLE("Medial femoral condyle", "medial femoral condyle"),
    LATERAL_FEMORAL_CONDYLE("Lateral femoral condyle", "lateral femoral condyle"),
    MEDIAL_TIBIAL_PLATEAU("Medial tibial plateau", "medial tibial plateau"),
    LATERAL_TIBIAL_PLATEAU("Lateral tibial plateau", "lateral tibial plateau"),
    PROXIMAL_TIBIA("Proximal tibia", "proximal tibia"),
    FIBULAR_HEAD("Fibular head", "fibular head");

    private final String label;
    private final String phrase;

    MarrowLocation(String label, String phrase) {
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
    MODERATE("Moderate", "Moderate"),
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

  enum SoftTissueLocation {
    PREPATELLAR("Prepatellar", "prepatellar"),
    PRETIBIAL("Pretibial", "pretibial");

    private final String label;
    private final String phrase;

    SoftTissueLocation(String label, String phrase) {
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

  record MeniscusSelection(
      boolean intrasubstanceDegeneration,
      List<MeniscusRegion> regions,
      MeniscusTearType tearType,
      boolean maceration,
      boolean extrusion,
      boolean parameniscalCyst) {

    MeniscusSelection {
      List<MeniscusRegion> requestedRegions = regions == null ? List.of() : regions;
      regions =
          List.of(MeniscusRegion.values()).stream().filter(requestedRegions::contains).toList();
      tearType = Objects.requireNonNullElse(tearType, MeniscusTearType.NONE);
    }

    static MeniscusSelection empty() {
      return new MeniscusSelection(false, List.of(), MeniscusTearType.NONE, false, false, false);
    }

    boolean hasFinding() {
      return intrasubstanceDegeneration
          || tearType != MeniscusTearType.NONE
          || maceration
          || extrusion
          || parameniscalCyst;
    }
  }

  record MarrowSelection(
      MarrowFindingType type, Degree degree, List<MarrowLocation> locations, boolean subchondral) {

    MarrowSelection {
      type = Objects.requireNonNullElse(type, MarrowFindingType.NONE);
      degree = Objects.requireNonNullElse(degree, Degree.NONE);
      List<MarrowLocation> requestedLocations = locations == null ? List.of() : locations;
      locations =
          List.of(MarrowLocation.values()).stream().filter(requestedLocations::contains).toList();
    }

    static MarrowSelection empty() {
      return new MarrowSelection(MarrowFindingType.NONE, Degree.NONE, List.of(), false);
    }

    boolean hasFinding() {
      return type != MarrowFindingType.NONE && !locations.isEmpty();
    }
  }

  record Selection(
      boolean normal,
      Map<Meniscus, MeniscusSelection> menisci,
      Map<Ligament, LigamentStatus> ligaments,
      boolean aclReconstruction,
      Map<ExtensorTendon, TendonStatus> extensorMechanism,
      Map<Compartment, Degree> osteoarthrosis,
      MarrowSelection marrow,
      FluidAmount effusion,
      boolean synovitis,
      FluidAmount poplitealCyst,
      Degree prepatellarBursitis,
      Degree softTissueEdema,
      List<SoftTissueLocation> softTissueLocations,
      String freeText) {

    Selection {
      menisci = normalizeMenisci(menisci);
      ligaments =
          normalizeEnumMap(Ligament.class, Ligament.values(), ligaments, LigamentStatus.NONE);
      extensorMechanism =
          normalizeEnumMap(
              ExtensorTendon.class, ExtensorTendon.values(), extensorMechanism, TendonStatus.NONE);
      osteoarthrosis =
          normalizeEnumMap(Compartment.class, Compartment.values(), osteoarthrosis, Degree.NONE);
      marrow = Objects.requireNonNullElseGet(marrow, MarrowSelection::empty);
      effusion = Objects.requireNonNullElse(effusion, FluidAmount.NONE);
      poplitealCyst = Objects.requireNonNullElse(poplitealCyst, FluidAmount.NONE);
      prepatellarBursitis = Objects.requireNonNullElse(prepatellarBursitis, Degree.NONE);
      softTissueEdema = Objects.requireNonNullElse(softTissueEdema, Degree.NONE);
      List<SoftTissueLocation> requestedLocations =
          softTissueLocations == null ? List.of() : softTissueLocations;
      softTissueLocations =
          List.of(SoftTissueLocation.values()).stream()
              .filter(requestedLocations::contains)
              .toList();
      freeText = ComposerText.clean(freeText);
    }

    boolean hasFinding() {
      return normal || hasStructuredPositiveFinding() || ComposerText.hasText(freeText);
    }

    boolean hasStructuredPositiveFinding() {
      return menisci.values().stream().anyMatch(MeniscusSelection::hasFinding)
          || !ligaments.isEmpty()
          || aclReconstruction
          || !extensorMechanism.isEmpty()
          || !osteoarthrosis.isEmpty()
          || marrow.hasFinding()
          || effusion != FluidAmount.NONE
          || synovitis
          || poplitealCyst != FluidAmount.NONE
          || prepatellarBursitis != Degree.NONE
          || softTissueEdema != Degree.NONE;
    }

    private static Map<Meniscus, MeniscusSelection> normalizeMenisci(
        Map<Meniscus, MeniscusSelection> requested) {
      EnumMap<Meniscus, MeniscusSelection> normalized = new EnumMap<>(Meniscus.class);
      if (requested != null) {
        for (Meniscus meniscus : Meniscus.values()) {
          MeniscusSelection selection = requested.get(meniscus);
          if (selection != null && selection.hasFinding()) {
            normalized.put(meniscus, selection);
          }
        }
      }
      return Collections.unmodifiableMap(normalized);
    }

    private static <K extends Enum<K>, V> Map<K, V> normalizeEnumMap(
        Class<K> keyType, K[] keys, Map<K, V> requested, V emptyValue) {
      EnumMap<K, V> normalized = new EnumMap<>(keyType);
      if (requested != null) {
        for (K key : keys) {
          V value = requested.get(key);
          if (value != null && value != emptyValue) {
            normalized.put(key, value);
          }
        }
      }
      return Collections.unmodifiableMap(normalized);
    }
  }
}
