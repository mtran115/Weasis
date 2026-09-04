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
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.GeneratedFinding;

final class BrainFindingBuilder {
  private static final Pattern BARE_NUMBER = Pattern.compile("[0-9]+(?:\\.[0-9]+)?");

  private BrainFindingBuilder() {}

  static List<GeneratedFinding> generate(Selection selection) {
    Objects.requireNonNull(selection);
    List<GeneratedFinding> findings = new ArrayList<>();

    for (TechnicalNote note : selection.technicalNotes()) {
      addFinding(findings, note.phrase());
    }
    if (selection.normal() && !selection.hasIntracranialPositiveFinding()) {
      addFinding(findings, "Normal MRI of the brain");
    }
    if (selection.acuteFinding().hasFinding()) {
      addFinding(findings, acuteFindingDescription(selection.acuteFinding()));
    }
    if (selection.whiteMatter().hasFinding()) {
      addFinding(findings, whiteMatterDescription(selection.whiteMatter()));
    }
    if (selection.chronicMicrovascularChange() != Degree.NONE) {
      addFinding(
          findings,
          selection.chronicMicrovascularChange().phrase()
              + " chronic microvascular ischemic white matter change");
    }
    if (selection.cerebralVolumeLoss() != Degree.NONE) {
      addFinding(
          findings, selection.cerebralVolumeLoss().phrase() + " generalized cerebral volume loss");
    }
    if (selection.anteriorFalxLipoma()) {
      String size = measurement(selection.anteriorFalxLipomaSize(), "mm");
      addFinding(findings, (size.isBlank() ? "" : size + " ") + "lipoma along the anterior falx");
    }
    if (selection.sinus().hasFinding()) {
      addFinding(findings, sinusDescription(selection.sinus()));
    }
    if (ComposerText.hasText(selection.freeText())) {
      findings.add(new GeneratedFinding(ComposerText.sentence(selection.freeText()), ""));
    }
    return List.copyOf(findings);
  }

  private static String whiteMatterDescription(WhiteMatterSelection selection) {
    boolean singular = selection.quantity() == WhiteMatterQuantity.SINGLE;
    String size = measurement(selection.maximumSize(), "mm");
    String subject;
    if (singular) {
      subject = "A " + (size.isBlank() ? "" : size + " ") + "T2/FLAIR hyperintense focus";
    } else {
      String quantity = selection.quantity().phrase();
      subject = (quantity.isBlank() ? "T2/FLAIR" : quantity + " T2/FLAIR") + " hyperintense foci";
    }

    String distribution = whiteMatterDistribution(selection.distributions());
    String regions = cerebralRegionPhrase(selection.regions());
    StringBuilder description =
        new StringBuilder(subject)
            .append(singular ? " is present in the " : " are present in the ")
            .append(distribution);
    if (!regions.isBlank()) {
      description.append(" of the ").append(regions);
    }
    if (!singular && !size.isBlank()) {
      description.append(", measuring up to ").append(size);
    }
    if (selection.nonspecific()) {
      description.append(singular ? ", and is nonspecific" : ", and are nonspecific");
    }
    if (!selection.etiologies().isEmpty()) {
      description
          .append(" and may be related to ")
          .append(
              joinAlternatives(
                  selection.etiologies().stream().map(WhiteMatterEtiology::phrase).toList()));
    }
    return description.toString();
  }

  private static String whiteMatterDistribution(List<WhiteMatterDistribution> distributions) {
    if (distributions.isEmpty()) {
      return "supratentorial white matter";
    }
    return joinList(distributions.stream().map(WhiteMatterDistribution::phrase).toList())
        + " white matter";
  }

  private static String cerebralRegionPhrase(List<CerebralRegion> regions) {
    if (regions.isEmpty()) {
      return "";
    }
    List<Lobe> selectedLobes =
        List.of(Lobe.values()).stream()
            .filter(lobe -> regions.stream().anyMatch(region -> region.lobe() == lobe))
            .toList();
    boolean rightOnly =
        regions.stream().allMatch(region -> region.laterality() == Laterality.RIGHT);
    boolean leftOnly = regions.stream().allMatch(region -> region.laterality() == Laterality.LEFT);
    boolean bilateralEverywhere =
        selectedLobes.stream()
            .allMatch(
                lobe ->
                    regions.contains(CerebralRegion.of(Laterality.RIGHT, lobe))
                        && regions.contains(CerebralRegion.of(Laterality.LEFT, lobe)));
    if (rightOnly || leftOnly || bilateralEverywhere) {
      String side = rightOnly ? "right" : leftOnly ? "left" : "bilateral";
      String lobes = joinList(selectedLobes.stream().map(Lobe::phrase).toList());
      boolean plural = bilateralEverywhere || selectedLobes.size() > 1;
      return side + " " + lobes + (plural ? " lobes" : " lobe");
    }
    return joinList(
        selectedLobes.stream()
            .map(
                lobe -> {
                  boolean right = regions.contains(CerebralRegion.of(Laterality.RIGHT, lobe));
                  boolean left = regions.contains(CerebralRegion.of(Laterality.LEFT, lobe));
                  if (right && left) {
                    return "bilateral " + lobe.phrase() + " lobes";
                  }
                  return (right ? "right " : "left ") + lobe.phrase() + " lobe";
                })
            .toList());
  }

  private static String acuteFindingDescription(AcuteFindingSelection selection) {
    StringBuilder description = new StringBuilder(selection.type().phrase());
    if (ComposerText.hasText(selection.location())) {
      description.append(" in the ");
      if (selection.laterality() != Laterality.NONE) {
        description.append(selection.laterality().phrase()).append(' ');
      }
      description.append(selection.location());
    } else if (selection.laterality() != Laterality.NONE) {
      description
          .append(" in the ")
          .append(selection.laterality().phrase())
          .append(" cerebral hemisphere");
    }
    return description.toString();
  }

  private static String sinusDescription(SinusSelection selection) {
    String location = sinusLocation(selection.laterality(), selection.sites());
    String size = measurement(selection.size(), "cm");
    String description =
        switch (selection.type()) {
          case MUCOSAL_THICKENING ->
              (selection.degree() == Degree.NONE
                      ? "Mucosal thickening"
                      : selection.degree().phrase() + " mucosal thickening")
                  + " in the "
                  + location;
          case RETENTION_CYST ->
              (size.isBlank() ? "Mucous retention cyst" : "A " + size + " mucous retention cyst")
                  + " in the "
                  + location;
          case FLUID_OPACIFICATION -> "Fluid opacification of the " + location;
          case NONE -> "";
        };
    return selection.correlateForSinusitis()
        ? description + "; correlate for sinusitis"
        : description;
  }

  private static String sinusLocation(Laterality laterality, List<SinusSite> sites) {
    if (sites.isEmpty()) {
      return laterality == Laterality.NONE
          ? "paranasal sinuses"
          : laterality.phrase() + " paranasal sinuses";
    }
    String siteNames = joinList(sites.stream().map(SinusSite::phrase).toList());
    if (laterality == Laterality.BILATERAL) {
      return "bilateral " + siteNames + " sinuses";
    }
    if (sites.size() == 1) {
      String side = laterality == Laterality.NONE ? "" : laterality.phrase() + " ";
      return side + siteNames + " sinus";
    }
    String side = laterality == Laterality.NONE ? "" : laterality.phrase() + " ";
    return side + siteNames + " sinuses";
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

  private static String joinAlternatives(List<String> values) {
    return switch (values.size()) {
      case 0 -> "";
      case 1 -> values.getFirst();
      case 2 -> values.getFirst() + " or " + values.getLast();
      default ->
          String.join(", ", values.subList(0, values.size() - 1)) + ", or " + values.getLast();
    };
  }

  enum WhiteMatterQuantity {
    NONE("None", ""),
    SINGLE("Single", ""),
    TWO("Two", "Two"),
    FEW("Few", "A few"),
    MULTIPLE("Multiple", "Multiple"),
    NUMEROUS("Numerous", "Numerous");

    private final String label;
    private final String phrase;

    WhiteMatterQuantity(String label, String phrase) {
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

  enum WhiteMatterDistribution {
    SUBCORTICAL("Subcortical", "subcortical"),
    DEEP("Deep", "deep"),
    PERIVENTRICULAR("Periventricular", "periventricular");

    private final String label;
    private final String phrase;

    WhiteMatterDistribution(String label, String phrase) {
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

  enum WhiteMatterEtiology {
    TRAUMATIC_INJURY("Traumatic injury", "traumatic injury"),
    CHRONIC_ISCHEMIC_CHANGE("Chronic ischemic change", "other chronic ischemic change"),
    DEMYELINATING_DISEASE("Demyelinating disease", "demyelinating disease");

    private final String label;
    private final String phrase;

    WhiteMatterEtiology(String label, String phrase) {
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

  enum Lobe {
    FRONTAL("frontal"),
    PARIETAL("parietal"),
    TEMPORAL("temporal"),
    OCCIPITAL("occipital");

    private final String phrase;

    Lobe(String phrase) {
      this.phrase = phrase;
    }

    String phrase() {
      return phrase;
    }
  }

  enum CerebralRegion {
    RIGHT_FRONTAL("Right frontal", Laterality.RIGHT, Lobe.FRONTAL),
    LEFT_FRONTAL("Left frontal", Laterality.LEFT, Lobe.FRONTAL),
    RIGHT_PARIETAL("Right parietal", Laterality.RIGHT, Lobe.PARIETAL),
    LEFT_PARIETAL("Left parietal", Laterality.LEFT, Lobe.PARIETAL),
    RIGHT_TEMPORAL("Right temporal", Laterality.RIGHT, Lobe.TEMPORAL),
    LEFT_TEMPORAL("Left temporal", Laterality.LEFT, Lobe.TEMPORAL),
    RIGHT_OCCIPITAL("Right occipital", Laterality.RIGHT, Lobe.OCCIPITAL),
    LEFT_OCCIPITAL("Left occipital", Laterality.LEFT, Lobe.OCCIPITAL);

    private final String label;
    private final Laterality laterality;
    private final Lobe lobe;

    CerebralRegion(String label, Laterality laterality, Lobe lobe) {
      this.label = label;
      this.laterality = laterality;
      this.lobe = lobe;
    }

    Laterality laterality() {
      return laterality;
    }

    Lobe lobe() {
      return lobe;
    }

    String phrase() {
      return label.toLowerCase(Locale.ROOT) + " lobe";
    }

    static CerebralRegion of(Laterality laterality, Lobe lobe) {
      return List.of(values()).stream()
          .filter(region -> region.laterality == laterality && region.lobe == lobe)
          .findFirst()
          .orElseThrow();
    }

    @Override
    public String toString() {
      return label;
    }
  }

  enum AcuteFindingType {
    NONE("None", ""),
    ACUTE_INFARCT("Acute infarct", "Acute infarct"),
    SUBACUTE_INFARCT("Subacute infarct", "Subacute infarct"),
    RESTRICTED_DIFFUSION("Restricted diffusion", "Restricted diffusion"),
    INTRAPARENCHYMAL_HEMORRHAGE("Intraparenchymal hemorrhage", "Intraparenchymal hemorrhage"),
    MICROHEMORRHAGE("Microhemorrhage", "Microhemorrhage");

    private final String label;
    private final String phrase;

    AcuteFindingType(String label, String phrase) {
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

  enum Laterality {
    NONE("None", ""),
    RIGHT("Right", "right"),
    LEFT("Left", "left"),
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

  enum Degree {
    NONE("None", ""),
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

  enum SinusFindingType {
    NONE("None"),
    MUCOSAL_THICKENING("Mucosal thickening"),
    RETENTION_CYST("Retention cyst"),
    FLUID_OPACIFICATION("Fluid opacification");

    private final String label;

    SinusFindingType(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  enum SinusSite {
    MAXILLARY("Maxillary", "maxillary"),
    ETHMOID("Ethmoid", "ethmoid"),
    SPHENOID("Sphenoid", "sphenoid"),
    FRONTAL("Frontal", "frontal");

    private final String label;
    private final String phrase;

    SinusSite(String label, String phrase) {
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

  enum TechnicalNote {
    MOTION_ARTIFACT("Motion artifact", "Study is limited by motion artifact"),
    METALLIC_SKULL_BASE_ARTIFACT(
        "Metallic skull-base artifact",
        "Evaluation of the skull base is limited by metallic susceptibility artifact"),
    AXIAL_LATERALITY_SWITCHED(
        "Axial right/left switched",
        "On the axial sequences, the right and left sides of the patient appear to be switched");

    private final String label;
    private final String phrase;

    TechnicalNote(String label, String phrase) {
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

  record WhiteMatterSelection(
      WhiteMatterQuantity quantity,
      List<WhiteMatterDistribution> distributions,
      List<CerebralRegion> regions,
      String maximumSize,
      boolean nonspecific,
      List<WhiteMatterEtiology> etiologies) {

    WhiteMatterSelection {
      quantity = Objects.requireNonNullElse(quantity, WhiteMatterQuantity.NONE);
      List<WhiteMatterDistribution> requestedDistributions =
          distributions == null ? List.of() : distributions;
      distributions =
          List.of(WhiteMatterDistribution.values()).stream()
              .filter(requestedDistributions::contains)
              .toList();
      List<CerebralRegion> requestedRegions = regions == null ? List.of() : regions;
      regions =
          List.of(CerebralRegion.values()).stream().filter(requestedRegions::contains).toList();
      maximumSize = ComposerText.clean(maximumSize);
      List<WhiteMatterEtiology> requestedEtiologies = etiologies == null ? List.of() : etiologies;
      etiologies =
          List.of(WhiteMatterEtiology.values()).stream()
              .filter(requestedEtiologies::contains)
              .toList();
    }

    static WhiteMatterSelection empty() {
      return new WhiteMatterSelection(
          WhiteMatterQuantity.NONE, List.of(), List.of(), "", true, List.of());
    }

    boolean hasFinding() {
      return quantity != WhiteMatterQuantity.NONE
          || !distributions.isEmpty()
          || !regions.isEmpty()
          || ComposerText.hasText(maximumSize)
          || !etiologies.isEmpty();
    }
  }

  record AcuteFindingSelection(AcuteFindingType type, Laterality laterality, String location) {
    AcuteFindingSelection {
      type = Objects.requireNonNullElse(type, AcuteFindingType.NONE);
      laterality = Objects.requireNonNullElse(laterality, Laterality.NONE);
      location = ComposerText.clean(location);
    }

    static AcuteFindingSelection empty() {
      return new AcuteFindingSelection(AcuteFindingType.NONE, Laterality.NONE, "");
    }

    boolean hasFinding() {
      return type != AcuteFindingType.NONE;
    }
  }

  record SinusSelection(
      SinusFindingType type,
      Laterality laterality,
      List<SinusSite> sites,
      Degree degree,
      String size,
      boolean correlateForSinusitis) {

    SinusSelection {
      type = Objects.requireNonNullElse(type, SinusFindingType.NONE);
      laterality = Objects.requireNonNullElse(laterality, Laterality.NONE);
      List<SinusSite> requestedSites = sites == null ? List.of() : sites;
      sites = List.of(SinusSite.values()).stream().filter(requestedSites::contains).toList();
      degree = Objects.requireNonNullElse(degree, Degree.NONE);
      size = ComposerText.clean(size);
    }

    static SinusSelection empty() {
      return new SinusSelection(
          SinusFindingType.NONE,
          Laterality.NONE,
          List.of(SinusSite.MAXILLARY),
          Degree.NONE,
          "",
          false);
    }

    boolean hasFinding() {
      return type != SinusFindingType.NONE;
    }
  }

  record Selection(
      boolean normal,
      WhiteMatterSelection whiteMatter,
      AcuteFindingSelection acuteFinding,
      Degree chronicMicrovascularChange,
      Degree cerebralVolumeLoss,
      SinusSelection sinus,
      boolean anteriorFalxLipoma,
      String anteriorFalxLipomaSize,
      List<TechnicalNote> technicalNotes,
      String freeText) {

    Selection {
      whiteMatter = Objects.requireNonNullElseGet(whiteMatter, WhiteMatterSelection::empty);
      acuteFinding = Objects.requireNonNullElseGet(acuteFinding, AcuteFindingSelection::empty);
      chronicMicrovascularChange =
          Objects.requireNonNullElse(chronicMicrovascularChange, Degree.NONE);
      cerebralVolumeLoss = Objects.requireNonNullElse(cerebralVolumeLoss, Degree.NONE);
      sinus = Objects.requireNonNullElseGet(sinus, SinusSelection::empty);
      anteriorFalxLipomaSize = ComposerText.clean(anteriorFalxLipomaSize);
      List<TechnicalNote> requestedNotes = technicalNotes == null ? List.of() : technicalNotes;
      technicalNotes =
          List.of(TechnicalNote.values()).stream().filter(requestedNotes::contains).toList();
      freeText = ComposerText.clean(freeText);
    }

    boolean hasFinding() {
      return normal
          || hasIntracranialPositiveFinding()
          || sinus.hasFinding()
          || !technicalNotes.isEmpty()
          || ComposerText.hasText(freeText);
    }

    boolean hasIntracranialPositiveFinding() {
      return whiteMatter.hasFinding()
          || acuteFinding.hasFinding()
          || chronicMicrovascularChange != Degree.NONE
          || cerebralVolumeLoss != Degree.NONE
          || anteriorFalxLipoma;
    }
  }
}
