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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.GeneratedFinding;

final class WristFindingBuilder {
  private static final Pattern BARE_NUMBER = Pattern.compile("[0-9]+(?:\\.[0-9]+)?");

  private WristFindingBuilder() {}

  static List<GeneratedFinding> generate(Selection selection) {
    Objects.requireNonNull(selection);
    List<GeneratedFinding> findings = new ArrayList<>();

    if (selection.motionArtifact()) {
      addFinding(findings, "Multiple sequences are degraded by motion artifact");
    }
    if (selection.normal() && !selection.hasClinicalPositiveFinding()) {
      addFinding(findings, "Normal MRI of the wrist");
    }
    for (Ligament ligament : Ligament.values()) {
      LigamentSelection injury =
          selection.ligaments().getOrDefault(ligament, LigamentSelection.empty());
      if (injury.hasFinding()) {
        addFinding(findings, ligamentDescription(ligament, injury));
      }
    }
    if (selection.tfccFinding() != TfccFinding.NONE) {
      addFinding(findings, selection.tfccFinding().phrase());
    }
    for (Tendon tendon : Tendon.values()) {
      addTendonFindings(
          findings, tendon, selection.tendons().getOrDefault(tendon, TendonSelection.empty()));
    }
    if (selection.bone().hasFinding()) {
      addFinding(findings, boneDescription(selection.bone()));
    }
    if (selection.ganglion().hasFinding()) {
      addFinding(findings, ganglionDescription(selection.ganglion()));
    }
    if (selection.fluid().hasFinding()) {
      addFinding(findings, fluidDescription(selection.fluid()));
    }
    if (ComposerText.hasText(selection.freeText())) {
      findings.add(new GeneratedFinding(ComposerText.sentence(selection.freeText()), ""));
    }
    return List.copyOf(findings);
  }

  private static String ligamentDescription(Ligament ligament, LigamentSelection injury) {
    StringBuilder description = new StringBuilder();
    if (injury.status() != LigamentStatus.NONE) {
      description.append(injury.status().phrase()).append(" of the ").append(ligament.phrase());
    }
    if (injury.intervalWidening()) {
      if (!description.isEmpty()) {
        description.append(" with widening of the scapholunate interval");
      } else {
        description.append("Widening of the scapholunate interval");
      }
      String measurement = measurement(injury.intervalMeasurement(), "mm");
      if (!measurement.isBlank()) {
        description.append(" measuring ").append(measurement);
      }
    }
    return description.toString();
  }

  private static void addTendonFindings(
      List<GeneratedFinding> findings, Tendon tendon, TendonSelection selection) {
    if (selection.tear() != TendonTear.NONE) {
      StringBuilder tear =
          new StringBuilder(selection.tear().phrase()).append(" of the ").append(tendon.phrase());
      if (selection.tendinosis() != Degree.NONE) {
        tear.append(" on a background of ")
            .append(selection.tendinosis().lowerPhrase())
            .append(" tendinosis");
      }
      addFinding(findings, tear.toString());
    } else if (selection.tendinosis() != Degree.NONE) {
      addFinding(
          findings, selection.tendinosis().phrase() + " tendinosis of the " + tendon.phrase());
    }
    if (selection.tenosynovitis() != Degree.NONE) {
      addFinding(
          findings,
          selection.tenosynovitis().phrase() + " tenosynovitis of the " + tendon.phrase());
    }
  }

  private static String boneDescription(BoneSelection selection) {
    List<String> locations = selection.locations().stream().map(BoneLocation::phrase).toList();
    String location = locations.isEmpty() ? "carpal bones" : joinList(locations);
    String degree = selection.degree() == Degree.NONE ? "" : selection.degree().phrase() + " ";
    String size = measurement(selection.cystSize(), "mm");
    return switch (selection.type()) {
      case MARROW_EDEMA -> degree + "bone marrow edema involving the " + location;
      case CONTUSION ->
          degree
              + (locations.size() > 1 ? "bone contusions of the " : "bone contusion of the ")
              + location;
      case CYSTIC_CHANGES ->
          degree
              + "cystic changes of the "
              + location
              + (size.isBlank() ? "" : " measuring up to " + size);
      case INTRAOSSEOUS_CYST ->
          (size.isBlank() ? degree : (locations.size() > 1 ? "" : "A ") + size + " ")
              + (locations.size() > 1 ? "intraosseous cysts in the " : "intraosseous cyst in the ")
              + location;
      case NONE -> "";
    };
  }

  private static String ganglionDescription(GanglionSelection selection) {
    String size = measurement(selection.size(), "mm");
    StringBuilder description =
        new StringBuilder(size.isBlank() ? "Ganglion cyst" : "A " + size + " ganglion cyst")
            .append(" along the ")
            .append(selection.location().phrase());
    if (ComposerText.hasText(selection.additionalLocation())) {
      description.append(' ').append(selection.additionalLocation());
    }
    return description.toString();
  }

  private static String fluidDescription(FluidSelection selection) {
    String amount =
        selection.amount() == FluidAmount.NONE
            ? "Fluid"
            : selection.amount().phrase() + " amount of fluid";
    return amount
        + " in the "
        + selection.location().phrase()
        + (selection.likelyInflammatory() ? ", likely inflammatory" : "");
  }

  private static String measurement(String value, String defaultUnit) {
    String clean = ComposerText.clean(value);
    return BARE_NUMBER.matcher(clean).matches() ? clean + " " + defaultUnit : clean;
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

  enum Degree {
    NONE("None"),
    MINIMAL("Minimal"),
    MILD("Mild"),
    MODERATE("Moderate"),
    SEVERE("Severe");

    private final String label;

    Degree(String label) {
      this.label = label;
    }

    String phrase() {
      return label;
    }

    String lowerPhrase() {
      return label.toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
      return label;
    }
  }

  enum Ligament {
    SCAPHOLUNATE("Scapholunate", "scapholunate ligament"),
    LUNOTRIQUETRAL("Lunotriquetral", "lunotriquetral ligament");

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
    DEGENERATION("Degeneration", "Degeneration"),
    SPRAIN("Sprain", "Sprain"),
    PARTIAL_TEAR("Partial tear", "Partial tear"),
    TEAR("Tear", "Tear"),
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

  enum TfccFinding {
    NONE("None", ""),
    DEGENERATION("Degeneration", "Degeneration of the triangular fibrocartilage complex"),
    CENTRAL_PERFORATION(
        "Central perforation", "Central perforation of the triangular fibrocartilage complex"),
    RADIAL_ATTACHMENT_TEAR("Radial attachment tear", "Tear of the radial attachment of the TFCC"),
    ULNAR_ATTACHMENT_TEAR("Ulnar attachment tear", "Tear of the ulnar attachment of the TFCC"),
    PERIPHERAL_TEAR("Peripheral tear", "Peripheral tear of the TFCC"),
    TEAR("Tear", "Tear of the triangular fibrocartilage complex");

    private final String label;
    private final String phrase;

    TfccFinding(String label, String phrase) {
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

  enum Tendon {
    EXTENSOR_CARPI_ULNARIS("ECU", "extensor carpi ulnaris tendon"),
    EXTENSOR_CARPI_RADIALIS_BREVIS("ECRB", "extensor carpi radialis brevis tendon");

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

  enum BoneFindingType {
    NONE("None"),
    MARROW_EDEMA("Marrow edema"),
    CONTUSION("Bone contusion"),
    CYSTIC_CHANGES("Cystic changes"),
    INTRAOSSEOUS_CYST("Intraosseous cyst");

    private final String label;

    BoneFindingType(String label) {
      this.label = label;
    }

    boolean isCystic() {
      return this == CYSTIC_CHANGES || this == INTRAOSSEOUS_CYST;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  enum BoneLocation {
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

    BoneLocation(String label, String phrase) {
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

  enum GanglionLocation {
    NONE("None", ""),
    DORSAL("Dorsal wrist", "dorsal aspect of the wrist"),
    VOLAR("Volar wrist", "volar aspect of the wrist");

    private final String label;
    private final String phrase;

    GanglionLocation(String label, String phrase) {
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

  enum FluidLocation {
    NONE("None", ""),
    PISOTRIQUETRAL_RECESS("Pisotriquetral recess", "pisotriquetral recess"),
    DORSAL_RADIOCARPAL_JOINT("Dorsal radiocarpal", "dorsal radiocarpal joint"),
    RADIOCARPAL_JOINT("Radiocarpal joint", "radiocarpal joint"),
    MIDCARPAL_JOINT("Midcarpal joint", "midcarpal joint"),
    DISTAL_RADIOULNAR_JOINT("Distal radioulnar", "distal radioulnar joint"),
    ULNAR_STYLOID("Along ulnar styloid", "soft tissues along the ulnar styloid");

    private final String label;
    private final String phrase;

    FluidLocation(String label, String phrase) {
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

  record LigamentSelection(
      LigamentStatus status, boolean intervalWidening, String intervalMeasurement) {
    LigamentSelection {
      status = Objects.requireNonNullElse(status, LigamentStatus.NONE);
      intervalMeasurement = intervalWidening ? ComposerText.clean(intervalMeasurement) : "";
    }

    static LigamentSelection empty() {
      return new LigamentSelection(LigamentStatus.NONE, false, "");
    }

    boolean hasFinding() {
      return status != LigamentStatus.NONE || intervalWidening;
    }
  }

  record TendonSelection(Degree tendinosis, Degree tenosynovitis, TendonTear tear) {
    TendonSelection {
      tendinosis = Objects.requireNonNullElse(tendinosis, Degree.NONE);
      tenosynovitis = Objects.requireNonNullElse(tenosynovitis, Degree.NONE);
      tear = Objects.requireNonNullElse(tear, TendonTear.NONE);
    }

    static TendonSelection empty() {
      return new TendonSelection(Degree.NONE, Degree.NONE, TendonTear.NONE);
    }

    boolean hasFinding() {
      return tendinosis != Degree.NONE || tenosynovitis != Degree.NONE || tear != TendonTear.NONE;
    }
  }

  record BoneSelection(
      BoneFindingType type, Degree degree, List<BoneLocation> locations, String cystSize) {
    BoneSelection {
      type = Objects.requireNonNullElse(type, BoneFindingType.NONE);
      degree = Objects.requireNonNullElse(degree, Degree.NONE);
      List<BoneLocation> requestedLocations = locations == null ? List.of() : locations;
      locations =
          List.of(BoneLocation.values()).stream().filter(requestedLocations::contains).toList();
      cystSize = type.isCystic() ? ComposerText.clean(cystSize) : "";
    }

    static BoneSelection empty() {
      return new BoneSelection(BoneFindingType.NONE, Degree.NONE, List.of(), "");
    }

    boolean hasFinding() {
      return type != BoneFindingType.NONE && !locations.isEmpty();
    }
  }

  record GanglionSelection(GanglionLocation location, String size, String additionalLocation) {
    GanglionSelection {
      location = Objects.requireNonNullElse(location, GanglionLocation.NONE);
      size = location == GanglionLocation.NONE ? "" : ComposerText.clean(size);
      additionalLocation =
          location == GanglionLocation.NONE ? "" : ComposerText.clean(additionalLocation);
    }

    static GanglionSelection empty() {
      return new GanglionSelection(GanglionLocation.NONE, "", "");
    }

    boolean hasFinding() {
      return location != GanglionLocation.NONE;
    }
  }

  record FluidSelection(FluidLocation location, FluidAmount amount, boolean likelyInflammatory) {
    FluidSelection {
      location = Objects.requireNonNullElse(location, FluidLocation.NONE);
      amount = Objects.requireNonNullElse(amount, FluidAmount.NONE);
      if (location == FluidLocation.NONE) {
        amount = FluidAmount.NONE;
        likelyInflammatory = false;
      }
    }

    static FluidSelection empty() {
      return new FluidSelection(FluidLocation.NONE, FluidAmount.NONE, false);
    }

    boolean hasFinding() {
      return location != FluidLocation.NONE;
    }
  }

  record Selection(
      boolean normal,
      Map<Ligament, LigamentSelection> ligaments,
      TfccFinding tfccFinding,
      Map<Tendon, TendonSelection> tendons,
      BoneSelection bone,
      GanglionSelection ganglion,
      FluidSelection fluid,
      boolean motionArtifact,
      String freeText) {

    Selection {
      EnumMap<Ligament, LigamentSelection> selectedLigaments = new EnumMap<>(Ligament.class);
      if (ligaments != null) {
        for (Ligament ligament : Ligament.values()) {
          LigamentSelection injury = ligaments.get(ligament);
          if (injury != null && injury.hasFinding()) {
            if (ligament != Ligament.SCAPHOLUNATE && injury.intervalWidening()) {
              throw new IllegalArgumentException(
                  "Interval widening is only supported for the scapholunate ligament.");
            }
            selectedLigaments.put(ligament, injury);
          }
        }
      }
      ligaments = Collections.unmodifiableMap(selectedLigaments);
      tfccFinding = Objects.requireNonNullElse(tfccFinding, TfccFinding.NONE);

      EnumMap<Tendon, TendonSelection> selectedTendons = new EnumMap<>(Tendon.class);
      if (tendons != null) {
        for (Tendon tendon : Tendon.values()) {
          TendonSelection finding = tendons.get(tendon);
          if (finding != null && finding.hasFinding()) {
            selectedTendons.put(tendon, finding);
          }
        }
      }
      tendons = Collections.unmodifiableMap(selectedTendons);
      bone = Objects.requireNonNullElseGet(bone, BoneSelection::empty);
      ganglion = Objects.requireNonNullElseGet(ganglion, GanglionSelection::empty);
      fluid = Objects.requireNonNullElseGet(fluid, FluidSelection::empty);
      freeText = ComposerText.clean(freeText);
    }

    boolean hasClinicalPositiveFinding() {
      return !ligaments.isEmpty()
          || tfccFinding != TfccFinding.NONE
          || !tendons.isEmpty()
          || bone.hasFinding()
          || ganglion.hasFinding()
          || fluid.hasFinding()
          || ComposerText.hasText(freeText);
    }

    boolean hasFinding() {
      return normal || motionArtifact || hasClinicalPositiveFinding();
    }
  }
}
