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
import org.weasis.dicom.reportcomposer.CervicalSpineFindingBuilder.Curvature;
import org.weasis.dicom.reportcomposer.CervicalSpineFindingBuilder.Laterality;
import org.weasis.dicom.reportcomposer.CervicalSpineFindingBuilder.LevelSelection;
import org.weasis.dicom.reportcomposer.CervicalSpineFindingBuilder.OverviewFinding;
import org.weasis.dicom.reportcomposer.CervicalSpineFindingBuilder.Selection;
import org.weasis.dicom.reportcomposer.CervicalSpineFindingBuilder.Severity;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.GeneratedFinding;

class CervicalSpineFindingBuilderTest {
  @Test
  void generatesCurvatureAndOptionalLevelSpecificDegenerativeFindings() {
    Selection selection =
        new Selection(
            Curvature.STRAIGHTENING,
            Map.of(
                OverviewFinding.SPONDYLOSIS,
                List.of("C3-4", "C4-5"),
                OverviewFinding.DISC_DEHYDRATION,
                List.of()),
            List.of());

    List<GeneratedFinding> findings = CervicalSpineFindingBuilder.generate(selection);

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
  void combinesLevelMorphologyLateralitySeverityAndFreeText() {
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

    List<GeneratedFinding> findings =
        CervicalSpineFindingBuilder.generate(
            new Selection(Curvature.NONE, Map.of(), List.of(level)));

    assertEquals(1, findings.size());
    assertAll(
        () ->
            assertEquals(
                "C5-6: Disc bulge with superimposed protrusion. Left facet arthrosis. Bilateral "
                    + "uncovertebral hypertrophy. Moderate right neural foraminal stenosis. Mild "
                    + "spinal canal stenosis.",
                findings.getFirst().findingText()),
        () ->
            assertEquals(
                "Disc protrusion and moderate right neural foraminal stenosis at C5-6.",
                findings.getFirst().impressionText()));
  }

  @Test
  void emitsLevelsInAnatomicOrderAndUsesOxfordComma() {
    Selection selection =
        new Selection(
            Curvature.NONE,
            Map.of(OverviewFinding.REACTIVE_ENDPLATE_CHANGES, List.of("C6-7", "C2-3", "C4-5")),
            List.of(level("C6-7", Severity.SEVERE), level("C3-4", Severity.MILD)));

    List<GeneratedFinding> findings = CervicalSpineFindingBuilder.generate(selection);

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
        CervicalSpineFindingBuilder.generate(new Selection(Curvature.NONE, Map.of(), List.of())));
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

  private static LevelSelection level(String level, Severity severity) {
    return new LevelSelection(
        level, false, false, "", Laterality.NONE, Laterality.NONE, Laterality.BILATERAL, severity);
  }
}
