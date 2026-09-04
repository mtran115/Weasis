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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.reportcomposer.KneeFindingBuilder.Compartment;
import org.weasis.dicom.reportcomposer.KneeFindingBuilder.Degree;
import org.weasis.dicom.reportcomposer.KneeFindingBuilder.ExtensorTendon;
import org.weasis.dicom.reportcomposer.KneeFindingBuilder.FluidAmount;
import org.weasis.dicom.reportcomposer.KneeFindingBuilder.Ligament;
import org.weasis.dicom.reportcomposer.KneeFindingBuilder.LigamentStatus;
import org.weasis.dicom.reportcomposer.KneeFindingBuilder.MarrowFindingType;
import org.weasis.dicom.reportcomposer.KneeFindingBuilder.MarrowLocation;
import org.weasis.dicom.reportcomposer.KneeFindingBuilder.MarrowSelection;
import org.weasis.dicom.reportcomposer.KneeFindingBuilder.Meniscus;
import org.weasis.dicom.reportcomposer.KneeFindingBuilder.MeniscusRegion;
import org.weasis.dicom.reportcomposer.KneeFindingBuilder.MeniscusSelection;
import org.weasis.dicom.reportcomposer.KneeFindingBuilder.MeniscusTearType;
import org.weasis.dicom.reportcomposer.KneeFindingBuilder.Selection;
import org.weasis.dicom.reportcomposer.KneeFindingBuilder.SoftTissueLocation;
import org.weasis.dicom.reportcomposer.KneeFindingBuilder.TendonStatus;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.GeneratedFinding;

class KneeFindingBuilderTest {
  @Test
  void generatesMeniscalAndLigamentFindingsInAnatomicOrder() {
    Selection selection =
        selection(
            false,
            Map.of(
                Meniscus.MEDIAL,
                new MeniscusSelection(
                    true,
                    List.of(MeniscusRegion.POSTERIOR_HORN),
                    MeniscusTearType.NONE,
                    false,
                    false,
                    false),
                Meniscus.LATERAL,
                new MeniscusSelection(
                    false,
                    List.of(
                        MeniscusRegion.POSTERIOR_HORN,
                        MeniscusRegion.ANTERIOR_HORN,
                        MeniscusRegion.BODY),
                    MeniscusTearType.COMPLEX,
                    false,
                    false,
                    true)),
            Map.of(
                Ligament.MCL,
                LigamentStatus.MILD_SPRAIN,
                Ligament.ACL,
                LigamentStatus.COMPLETE_TEAR),
            false,
            Map.of(),
            Map.of(),
            MarrowSelection.empty(),
            FluidAmount.NONE,
            false,
            FluidAmount.NONE,
            Degree.NONE,
            Degree.NONE,
            List.of(),
            "");

    assertEquals(
        List.of(
            finding("Intrasubstance degeneration of the posterior horn of the medial meniscus."),
            finding(
                "Complex tear of the anterior horn, body, and posterior horn of the lateral "
                    + "meniscus with an associated parameniscal cyst."),
            finding("Complete tear of the anterior cruciate ligament."),
            finding("Mild sprain of the medial collateral ligament.")),
        KneeFindingBuilder.generate(selection));
  }

  @Test
  void generatesFrequentTendonMarrowJointAndSoftTissueFindings() {
    Selection selection =
        selection(
            false,
            Map.of(),
            Map.of(),
            false,
            Map.of(ExtensorTendon.QUADRICEPS, TendonStatus.MILD_TENDINOSIS),
            Map.of(Compartment.MEDIAL, Degree.MODERATE),
            new MarrowSelection(
                MarrowFindingType.CONTUSION,
                Degree.MODERATE,
                List.of(MarrowLocation.PROXIMAL_TIBIA, MarrowLocation.LATERAL_FEMORAL_CONDYLE),
                false),
            FluidAmount.SMALL,
            true,
            FluidAmount.MODERATE,
            Degree.MILD,
            Degree.MILD,
            List.of(SoftTissueLocation.PRETIBIAL, SoftTissueLocation.PREPATELLAR),
            "");

    assertEquals(
        List.of(
            finding("Mild tendinosis of the quadriceps tendon."),
            finding("Moderate osteoarthrosis of the medial femorotibial compartment."),
            finding("Moderate bone contusions of the lateral femoral condyle and proximal tibia."),
            finding("Small knee joint effusion."),
            finding("Knee joint synovitis."),
            finding("Moderate popliteal cyst."),
            finding("Mild prepatellar bursitis."),
            finding("Mild prepatellar and pretibial soft tissue edema.")),
        KneeFindingBuilder.generate(selection));
  }

  @Test
  void normalFindingCanIncludeComplementaryFreeTextButIsSuppressedByPositiveControls() {
    Selection normal = emptySelection(true, "Patient is skeletally immature");

    assertEquals(
        List.of(
            finding("Normal MRI of the knee."),
            new GeneratedFinding("Patient is skeletally immature.", "")),
        KneeFindingBuilder.generate(normal));

    Selection reconstructedAcl =
        selection(
            true,
            Map.of(),
            Map.of(Ligament.ACL, LigamentStatus.PARTIAL_TEAR),
            true,
            Map.of(),
            Map.of(),
            MarrowSelection.empty(),
            FluidAmount.NONE,
            false,
            FluidAmount.NONE,
            Degree.NONE,
            Degree.NONE,
            List.of(),
            "");

    assertEquals(
        List.of(
            finding("Postsurgical changes of ACL reconstruction with partial tear of the graft.")),
        KneeFindingBuilder.generate(reconstructedAcl));
  }

  @Test
  void emptyKneeSelectionGeneratesNoFindings() {
    Selection selection = emptySelection(false, "");

    assertFalse(selection.hasFinding());
    assertEquals(List.of(), KneeFindingBuilder.generate(selection));
  }

  private static Selection emptySelection(boolean normal, String freeText) {
    return selection(
        normal,
        Map.of(),
        Map.of(),
        false,
        Map.of(),
        Map.of(),
        MarrowSelection.empty(),
        FluidAmount.NONE,
        false,
        FluidAmount.NONE,
        Degree.NONE,
        Degree.NONE,
        List.of(),
        freeText);
  }

  private static Selection selection(
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
    return new Selection(
        normal,
        menisci,
        ligaments,
        aclReconstruction,
        extensorMechanism,
        osteoarthrosis,
        marrow,
        effusion,
        synovitis,
        poplitealCyst,
        prepatellarBursitis,
        softTissueEdema,
        softTissueLocations,
        freeText);
  }

  private static GeneratedFinding finding(String text) {
    return new GeneratedFinding(text, text);
  }
}
