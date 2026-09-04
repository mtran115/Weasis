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

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.GeneratedFinding;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.BoneFindingType;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.BoneLocation;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.BoneSelection;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.Degree;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.FluidAmount;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.FluidLocation;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.FluidSelection;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.GanglionLocation;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.GanglionSelection;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.Ligament;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.LigamentSelection;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.LigamentStatus;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.Selection;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.Tendon;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.TendonSelection;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.TendonTear;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.TfccFinding;

class WristFindingBuilderTest {
  @Test
  void generatesNormalWristExam() {
    assertEquals(
        List.of(new GeneratedFinding("Normal MRI of the wrist.", "Normal MRI of the wrist.")),
        WristFindingBuilder.generate(emptySelection(true)));
  }

  @Test
  void generatesLigamentTfccAndBoneFindingsFromCommonSelections() {
    Selection selection =
        new Selection(
            false,
            Map.of(Ligament.SCAPHOLUNATE, new LigamentSelection(LigamentStatus.TEAR, true, "4")),
            TfccFinding.RADIAL_ATTACHMENT_TEAR,
            Map.of(),
            new BoneSelection(
                BoneFindingType.CONTUSION,
                Degree.MILD,
                List.of(BoneLocation.LUNATE, BoneLocation.DISTAL_RADIUS),
                ""),
            GanglionSelection.empty(),
            FluidSelection.empty(),
            false,
            "");

    assertEquals(
        List.of(
            finding(
                "Tear of the scapholunate ligament with widening of the scapholunate interval "
                    + "measuring 4 mm."),
            finding("Tear of the radial attachment of the TFCC."),
            finding("Mild bone contusions of the distal radius and lunate.")),
        WristFindingBuilder.generate(selection));
  }

  @Test
  void combinesEcuSplitTearWithTendinosisAndAddsEcrbTenosynovitis() {
    Selection selection =
        new Selection(
            false,
            Map.of(),
            TfccFinding.NONE,
            Map.of(
                Tendon.EXTENSOR_CARPI_ULNARIS,
                new TendonSelection(Degree.MILD, Degree.NONE, TendonTear.SPLIT),
                Tendon.EXTENSOR_CARPI_RADIALIS_BREVIS,
                new TendonSelection(Degree.NONE, Degree.MINIMAL, TendonTear.NONE)),
            BoneSelection.empty(),
            GanglionSelection.empty(),
            FluidSelection.empty(),
            false,
            "");

    assertEquals(
        List.of(
            finding(
                "Split tear of the extensor carpi ulnaris tendon on a background of mild "
                    + "tendinosis."),
            finding("Minimal tenosynovitis of the extensor carpi radialis brevis tendon.")),
        WristFindingBuilder.generate(selection));
  }

  @Test
  void generatesCystFluidAndMotionArtifactWording() {
    Selection selection =
        new Selection(
            false,
            Map.of(),
            TfccFinding.NONE,
            Map.of(),
            new BoneSelection(
                BoneFindingType.INTRAOSSEOUS_CYST,
                Degree.NONE,
                List.of(BoneLocation.TRAPEZOID),
                "small"),
            new GanglionSelection(
                GanglionLocation.DORSAL, "6", "at the level of the scapholunate ligament"),
            new FluidSelection(FluidLocation.PISOTRIQUETRAL_RECESS, FluidAmount.MODERATE, true),
            true,
            "");

    assertEquals(
        List.of(
            finding("Multiple sequences are degraded by motion artifact."),
            finding("A small intraosseous cyst in the trapezoid."),
            finding(
                "A 6 mm ganglion cyst along the dorsal aspect of the wrist at the level of the "
                    + "scapholunate ligament."),
            finding("Moderate amount of fluid in the pisotriquetral recess, likely inflammatory.")),
        WristFindingBuilder.generate(selection));
  }

  @Test
  void suppressesNormalStatementWhenFreeTextDescribesAnAbnormality() {
    Selection selection =
        new Selection(
            true,
            Map.of(),
            TfccFinding.NONE,
            Map.of(),
            BoneSelection.empty(),
            GanglionSelection.empty(),
            FluidSelection.empty(),
            false,
            "Injury to the volar radiocarpal joint capsule");

    assertEquals(
        List.of(new GeneratedFinding("Injury to the volar radiocarpal joint capsule.", "")),
        WristFindingBuilder.generate(selection));
  }

  private static Selection emptySelection(boolean normal) {
    return new Selection(
        normal,
        Map.of(),
        TfccFinding.NONE,
        Map.of(),
        BoneSelection.empty(),
        GanglionSelection.empty(),
        FluidSelection.empty(),
        false,
        "");
  }

  private static GeneratedFinding finding(String text) {
    return new GeneratedFinding(text, text);
  }
}
