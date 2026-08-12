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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.GeneratedFinding;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.AlignmentFinding;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.Laterality;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.LevelSelection;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.OverviewFinding;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.ProtrusionLocation;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.Selection;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.Severity;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.SpineRegion;

class SpineFindingBuilderTest {
  @Test
  void generatesCervicalAlignmentAndOptionalLevelSpecificDegenerativeFindings() {
    Selection selection =
        new Selection(
            SpineRegion.CERVICAL,
            AlignmentFinding.CERVICAL_STRAIGHTENING,
            Map.of(
                OverviewFinding.SPONDYLOSIS,
                List.of("C3-4", "C4-5"),
                OverviewFinding.DISC_DEHYDRATION,
                List.of()),
            List.of());

    List<GeneratedFinding> findings = SpineFindingBuilder.generate(selection);

    assertEquals(
        List.of(
            new GeneratedFinding(
                "Straightening of the cervical lordosis.", "Straightened cervical lordosis."),
            new GeneratedFinding(
                "Cervical spondylosis at C3-4 and C4-5.", "Cervical spondylosis at C3-4 and C4-5."),
            new GeneratedFinding("Multilevel disc desiccation.", "")),
        findings);
  }

  @Test
  void combinesCervicalLevelMorphologyLateralitySeverityAndFreeText() {
    LevelSelection level =
        new LevelSelection(
            "C5-6",
            true,
            true,
            "Mild spinal canal stenosis",
            Laterality.LEFT,
            Laterality.BILATERAL,
            Laterality.RIGHT,
            Severity.MODERATE);

    GeneratedFinding finding =
        SpineFindingBuilder.generate(
                new Selection(
                    SpineRegion.CERVICAL, AlignmentFinding.NONE, Map.of(), List.of(level)))
            .getFirst();

    assertAll(
        () ->
            assertEquals(
                "C5-6: Disc bulge with superimposed central protrusion. Left facet arthrosis. "
                    + "Bilateral uncovertebral hypertrophy. Moderate right neural foraminal "
                    + "stenosis. Mild spinal canal stenosis.",
                finding.findingText()),
        () ->
            assertEquals(
                "Central disc protrusion and moderate right neural foraminal stenosis at C5-6.",
                finding.impressionText()));
  }

  @Test
  void combinesMultipleProtrusionLocations() {
    LevelSelection level =
        new LevelSelection(
            "C4-5",
            false,
            List.of(ProtrusionLocation.CENTRAL, ProtrusionLocation.LEFT_SUBARTICULAR),
            "",
            Laterality.NONE,
            Laterality.NONE,
            Laterality.NONE,
            Severity.NONE);

    GeneratedFinding finding =
        SpineFindingBuilder.generate(
                new Selection(
                    SpineRegion.CERVICAL, AlignmentFinding.NONE, Map.of(), List.of(level)))
            .getFirst();

    assertAll(
        () ->
            assertEquals(
                "C4-5: Central and left subarticular disc protrusions.", finding.findingText()),
        () ->
            assertEquals(
                "Central and left subarticular disc protrusions at C4-5.",
                finding.impressionText()));
  }

  @Test
  void generatesThoracicFindingsWithThoracicLevelsAndTerminology() {
    LevelSelection level =
        new LevelSelection(
            "T7-8",
            true,
            List.of(ProtrusionLocation.CENTRAL, ProtrusionLocation.RIGHT_FORAMINAL),
            "",
            Laterality.BILATERAL,
            Laterality.LEFT,
            Laterality.RIGHT,
            Severity.MODERATE);
    Selection selection =
        new Selection(
            SpineRegion.THORACIC,
            AlignmentFinding.THORACIC_EXAGGERATED_KYPHOSIS,
            Map.of(OverviewFinding.SPONDYLOSIS, List.of("T7-8", "T8-9")),
            List.of(level));

    List<GeneratedFinding> findings = SpineFindingBuilder.generate(selection);

    assertAll(
        () -> assertEquals("Exaggeration of the thoracic kyphosis.", findings.get(0).findingText()),
        () -> assertEquals("Thoracic spondylosis at T7-8 and T8-9.", findings.get(1).findingText()),
        () ->
            assertEquals(
                "T7-8: Disc bulge with superimposed central and right foraminal protrusions. "
                    + "Bilateral facet arthrosis. Left ligamentum flavum hypertrophy. Moderate "
                    + "right neural foraminal stenosis.",
                findings.get(2).findingText()),
        () ->
            assertEquals(
                "Central and right foraminal disc protrusions and moderate right neural foraminal "
                    + "stenosis at T7-8.",
                findings.get(2).impressionText()));
  }

  @Test
  void generatesLumbarFindingsWithLumbarLevelsAndTerminology() {
    LevelSelection level =
        new LevelSelection(
            "L4-5",
            true,
            false,
            "Mild spinal canal stenosis",
            Laterality.BILATERAL,
            Laterality.BILATERAL,
            Laterality.LEFT,
            Severity.SEVERE);
    Selection selection =
        new Selection(
            SpineRegion.LUMBAR,
            AlignmentFinding.LUMBAR_STRAIGHTENING,
            Map.of(OverviewFinding.DISC_DEHYDRATION, List.of("L3-4", "L4-5")),
            List.of(level));

    List<GeneratedFinding> findings = SpineFindingBuilder.generate(selection);

    assertAll(
        () -> assertEquals("Straightening of the lumbar lordosis.", findings.get(0).findingText()),
        () -> assertEquals("Disc desiccation at L3-4 and L4-5.", findings.get(1).findingText()),
        () ->
            assertEquals(
                "L4-5: Disc bulge. Bilateral facet arthrosis. Bilateral ligamentum flavum "
                    + "hypertrophy. Severe left neural foraminal stenosis. Mild spinal canal "
                    + "stenosis.",
                findings.get(2).findingText()),
        () ->
            assertEquals(
                "Severe left neural foraminal stenosis at L4-5.",
                findings.get(2).impressionText()));
  }

  @Test
  void emitsLevelsInAnatomicOrderAndUsesOxfordComma() {
    Selection selection =
        new Selection(
            SpineRegion.CERVICAL,
            AlignmentFinding.NONE,
            Map.of(OverviewFinding.REACTIVE_ENDPLATE_CHANGES, List.of("C6-7", "C2-3", "C4-5")),
            List.of(level("C6-7", Severity.SEVERE), level("C3-4", Severity.MILD)));

    List<GeneratedFinding> findings = SpineFindingBuilder.generate(selection);

    assertAll(
        () ->
            assertEquals(
                "Reactive endplate marrow changes at C2-3, C4-5, and C6-7.",
                findings.get(0).findingText()),
        () ->
            assertEquals(
                "C3-4: Mild bilateral neural foraminal stenosis.", findings.get(1).findingText()),
        () ->
            assertEquals(
                "C6-7: Severe bilateral neural foraminal stenosis.",
                findings.get(2).findingText()));
  }

  @Test
  void emptyFormGeneratesNoFindings() {
    assertEquals(
        List.of(),
        SpineFindingBuilder.generate(
            new Selection(SpineRegion.LUMBAR, AlignmentFinding.NONE, Map.of(), List.of())));
  }

  @Test
  void rejectsIncompleteForaminalStenosisSelection() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LevelSelection(
                "C4-5",
                false,
                false,
                "",
                Laterality.NONE,
                Laterality.NONE,
                Laterality.LEFT,
                Severity.NONE));
  }

  @Test
  void rejectsFindingsThatDoNotBelongToSelectedSpineRegion() {
    assertAll(
        () ->
            assertThrows(
                IllegalArgumentException.class,
                () ->
                    new Selection(
                        SpineRegion.LUMBAR,
                        AlignmentFinding.CERVICAL_STRAIGHTENING,
                        Map.of(),
                        List.of())),
        () ->
            assertThrows(
                IllegalArgumentException.class,
                () ->
                    new Selection(
                        SpineRegion.THORACIC,
                        AlignmentFinding.NONE,
                        Map.of(),
                        List.of(level("L4-5", Severity.MILD)))));
  }

  private static LevelSelection level(String level, Severity severity) {
    return new LevelSelection(
        level, false, false, "", Laterality.NONE, Laterality.NONE, Laterality.BILATERAL, severity);
  }
}
