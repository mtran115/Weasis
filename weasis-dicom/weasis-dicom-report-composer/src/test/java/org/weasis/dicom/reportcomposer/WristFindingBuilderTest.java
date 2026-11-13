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
import org.weasis.dicom.reportcomposer.MriFindingCatalog.GeneratedFinding;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.Bone;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.BoneSelection;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.CystSelection;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.Degree;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.Ligament;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.LigamentStatus;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.LigamentTear;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.LimitedExam;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.Selection;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.Tendon;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.TendonSelection;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.TendonTear;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.TfccTear;
import org.weasis.dicom.reportcomposer.WristFindingBuilder.WristSide;

class WristFindingBuilderTest {
  @Test
  void generatesFindingsInFormOrderWithOneSentencePerStructure() {
    Selection selection =
        new Selection(
            true,
            LimitedExam.MOTION,
            Map.of(
                Ligament.SCAPHOLUNATE,
                new LigamentTear(LigamentStatus.PARTIAL_TEAR, true),
                Ligament.LUNOTRIQUETRAL,
                new LigamentTear(LigamentStatus.NONE, true)),
            TfccTear.RADIAL_ATTACHMENT,
            false,
            Map.of(
                Tendon.EXTENSOR_CARPI_ULNARIS,
                new TendonSelection(Degree.MINIMAL, TendonTear.SPLIT),
                Tendon.EXTENSOR_CARPI_RADIALIS_BREVIS,
                new TendonSelection(Degree.MILD, null)),
            Map.of(Bone.LUNATE, new BoneSelection(Degree.MILD, false, false, false, "")),
            new CystSelection(List.of(WristSide.VOLAR), "8 mm", "radiocarpal joint"),
            List.of(WristSide.DORSAL),
            "");

    assertEquals(
        List.of(
            "Limited exam due to motion.",
            "Possible partial tear of the scapholunate ligament.",
            "Tear of the radial attachment of the TFCC.",
            "Mild tenosynovitis of the extensor carpi radialis brevis tendon.",
            "Minimal tenosynovitis of the extensor carpi ulnaris tendon.",
            "Split tear of the extensor carpi ulnaris tendon.",
            "Mild bony contusion of the lunate.",
            "An 8 mm ganglion or synovial cyst along the volar wrist at the level of the"
                + " radiocarpal joint.",
            "Soft tissue swelling along the dorsal wrist."),
        texts(selection));
  }

  @Test
  void boneFindingsCombineByKindAndExtrasFollowTheFirstSentence() {
    assertEquals(
        List.of(
            "Nondisplaced fracture of the scaphoid with surrounding bone marrow edema, at the"
                + " waist.",
            "Moderate bony contusion of the scaphoid.",
            "Cystic changes of the scaphoid."),
        texts(
            bone(
                Bone.SCAPHOID,
                new BoneSelection(Degree.MODERATE, true, true, false, "at the waist"))));
    assertEquals(
        List.of(
            "Mild bone marrow edema and cystic change involving the trapezoid may be related to"
                + " bony contusion or osteoarthrosis."),
        texts(bone(Bone.TRAPEZOID, new BoneSelection(Degree.MILD, false, true, true, ""))));
    assertEquals(
        List.of(
            "Bone marrow edema involving the lunate may be related to bony contusion or"
                + " osteoarthrosis. Possible avascular necrosis."),
        texts(
            bone(
                Bone.LUNATE,
                new BoneSelection(
                    Degree.NONE, false, false, true, "Possible avascular necrosis"))));
  }

  @Test
  void cystNeedsASideAndNormalStandsOnlyWithoutAPositiveFinding() {
    Selection sizeOnly =
        new Selection(
            false,
            null,
            Map.of(),
            null,
            true,
            Map.of(),
            Map.of(),
            new CystSelection(List.of(), "8 mm", ""),
            List.of(),
            "");
    assertFalse(sizeOnly.hasFinding());
    assertEquals(List.of(), texts(sizeOnly));

    Selection normalWithCyst =
        new Selection(
            true,
            null,
            Map.of(),
            null,
            false,
            Map.of(),
            Map.of(),
            new CystSelection(List.of(WristSide.RADIAL, WristSide.VOLAR), "", ""),
            List.of(),
            "");
    assertEquals(
        List.of("A ganglion or synovial cyst along the volar and radial wrist."),
        texts(normalWithCyst));
    assertEquals(List.of("Normal MRI of the wrist."), texts(bone(Bone.HAMATE, null, true)));
  }

  private static Selection bone(Bone bone, BoneSelection finding) {
    return bone(bone, finding, false);
  }

  private static Selection bone(Bone bone, BoneSelection finding, boolean normal) {
    return new Selection(
        normal,
        null,
        Map.of(),
        null,
        false,
        Map.of(),
        finding == null ? Map.of() : Map.of(bone, finding),
        null,
        List.of(),
        "");
  }

  private static List<String> texts(Selection selection) {
    return WristFindingBuilder.generate(selection).stream()
        .map(GeneratedFinding::findingText)
        .toList();
  }
}
