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
import org.junit.jupiter.api.Test;
import org.weasis.dicom.reportcomposer.BrainFindingBuilder.AcuteFindingSelection;
import org.weasis.dicom.reportcomposer.BrainFindingBuilder.AcuteFindingType;
import org.weasis.dicom.reportcomposer.BrainFindingBuilder.CerebralRegion;
import org.weasis.dicom.reportcomposer.BrainFindingBuilder.Degree;
import org.weasis.dicom.reportcomposer.BrainFindingBuilder.Laterality;
import org.weasis.dicom.reportcomposer.BrainFindingBuilder.Selection;
import org.weasis.dicom.reportcomposer.BrainFindingBuilder.SinusFindingType;
import org.weasis.dicom.reportcomposer.BrainFindingBuilder.SinusSelection;
import org.weasis.dicom.reportcomposer.BrainFindingBuilder.SinusSite;
import org.weasis.dicom.reportcomposer.BrainFindingBuilder.TechnicalNote;
import org.weasis.dicom.reportcomposer.BrainFindingBuilder.WhiteMatterDistribution;
import org.weasis.dicom.reportcomposer.BrainFindingBuilder.WhiteMatterEtiology;
import org.weasis.dicom.reportcomposer.BrainFindingBuilder.WhiteMatterQuantity;
import org.weasis.dicom.reportcomposer.BrainFindingBuilder.WhiteMatterSelection;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.GeneratedFinding;

class BrainFindingBuilderTest {
  @Test
  void generatesCommonWhiteMatterFocusWordingWithSizeLocationAndDifferential() {
    Selection selection =
        selection(
            false,
            new WhiteMatterSelection(
                WhiteMatterQuantity.MULTIPLE,
                List.of(WhiteMatterDistribution.DEEP, WhiteMatterDistribution.SUBCORTICAL),
                List.of(
                    CerebralRegion.LEFT_PARIETAL,
                    CerebralRegion.RIGHT_FRONTAL,
                    CerebralRegion.LEFT_FRONTAL),
                "5",
                true,
                List.of(
                    WhiteMatterEtiology.CHRONIC_ISCHEMIC_CHANGE,
                    WhiteMatterEtiology.TRAUMATIC_INJURY)),
            AcuteFindingSelection.empty(),
            Degree.NONE,
            Degree.NONE,
            SinusSelection.empty(),
            false,
            "",
            List.of(),
            "");

    assertEquals(
        List.of(
            finding(
                "Multiple T2/FLAIR hyperintense foci are present in the subcortical and deep "
                    + "white matter of the bilateral frontal lobes and left parietal lobe, "
                    + "measuring up to 5 mm, and are nonspecific and may be related to traumatic "
                    + "injury or other chronic ischemic change.")),
        BrainFindingBuilder.generate(selection));
  }

  @Test
  void normalBrainCanCoexistWithTechnicalAndExtracranialSinusFindings() {
    Selection selection =
        selection(
            true,
            WhiteMatterSelection.empty(),
            AcuteFindingSelection.empty(),
            Degree.NONE,
            Degree.NONE,
            new SinusSelection(
                SinusFindingType.RETENTION_CYST,
                Laterality.LEFT,
                List.of(SinusSite.MAXILLARY),
                Degree.NONE,
                "1.5",
                false),
            false,
            "",
            List.of(TechnicalNote.METALLIC_SKULL_BASE_ARTIFACT),
            "");

    assertEquals(
        List.of(
            finding("Evaluation of the skull base is limited by metallic susceptibility artifact."),
            finding("Normal MRI of the brain."),
            finding("A 1.5 cm mucous retention cyst in the left maxillary sinus.")),
        BrainFindingBuilder.generate(selection));
  }

  @Test
  void intracranialFindingsSuppressNormalAndGenerateInClinicalOrder() {
    Selection selection =
        selection(
            true,
            WhiteMatterSelection.empty(),
            new AcuteFindingSelection(
                AcuteFindingType.ACUTE_INFARCT, Laterality.RIGHT, "parietal lobe"),
            Degree.MILD,
            Degree.MODERATE,
            SinusSelection.empty(),
            true,
            "8",
            List.of(),
            "");

    assertEquals(
        List.of(
            finding("Acute infarct in the right parietal lobe."),
            finding("Mild chronic microvascular ischemic white matter change."),
            finding("Moderate generalized cerebral volume loss."),
            finding("8 mm lipoma along the anterior falx.")),
        BrainFindingBuilder.generate(selection));
  }

  @Test
  void emptyBrainSelectionGeneratesNoFindings() {
    Selection selection =
        selection(
            false,
            WhiteMatterSelection.empty(),
            AcuteFindingSelection.empty(),
            Degree.NONE,
            Degree.NONE,
            SinusSelection.empty(),
            false,
            "",
            List.of(),
            "");

    assertFalse(selection.hasFinding());
    assertEquals(List.of(), BrainFindingBuilder.generate(selection));
  }

  private static Selection selection(
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
    return new Selection(
        normal,
        whiteMatter,
        acuteFinding,
        chronicMicrovascularChange,
        cerebralVolumeLoss,
        sinus,
        anteriorFalxLipoma,
        anteriorFalxLipomaSize,
        technicalNotes,
        freeText);
  }

  private static GeneratedFinding finding(String text) {
    return new GeneratedFinding(text, text);
  }
}
