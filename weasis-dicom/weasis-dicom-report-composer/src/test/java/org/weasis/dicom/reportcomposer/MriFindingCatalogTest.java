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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.ExamTemplate;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.FindingChoice;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.GeneratedFinding;

class MriFindingCatalogTest {
  @Test
  void exposesAllSupportedMriTemplatesInAnatomicalOrder() {
    assertEquals(
        List.of(
            ExamTemplate.GENERAL,
            ExamTemplate.BRAIN,
            ExamTemplate.CERVICAL_SPINE,
            ExamTemplate.THORACIC_SPINE,
            ExamTemplate.LUMBAR_SPINE,
            ExamTemplate.SHOULDER,
            ExamTemplate.ELBOW,
            ExamTemplate.WRIST,
            ExamTemplate.HAND,
            ExamTemplate.HIP,
            ExamTemplate.KNEE,
            ExamTemplate.ANKLE,
            ExamTemplate.FOOT),
        MriFindingCatalog.exams());
  }

  @Test
  void everyCatalogChoiceGeneratesCompleteEditableText() {
    for (ExamTemplate exam : MriFindingCatalog.exams()) {
      List<String> categories = MriFindingCatalog.categories(exam);
      assertFalse(categories.isEmpty(), () -> exam + " has no categories");
      for (String category : categories) {
        List<String> structures = MriFindingCatalog.structures(exam, category);
        List<FindingChoice> findings = MriFindingCatalog.findings(exam, category);
        assertFalse(structures.isEmpty(), () -> exam + " / " + category + " has no structures");
        assertFalse(findings.isEmpty(), () -> exam + " / " + category + " has no findings");
        for (String structure : structures) {
          for (FindingChoice finding : findings) {
            GeneratedFinding generated =
                MriFindingCatalog.generate(exam, category, structure, finding);
            String location = exam + " / " + category + " / " + structure + " / " + finding;
            assertAll(
                location,
                () -> assertFalse(generated.findingText().isBlank()),
                () -> assertFalse(generated.findingText().contains("{structure}")),
                () -> assertTrue(generated.findingText().endsWith(".")),
                () -> assertFalse(generated.impressionText().contains("{structure}")),
                () ->
                    assertTrue(
                        generated.impressionText().isBlank()
                            || generated.impressionText().endsWith(".")));
          }
        }
      }
    }
  }

  @Test
  void infersCommonFacilityStudyDescriptionVariants() {
    assertAll(
        () -> assertInferred(ExamTemplate.CERVICAL_SPINE, "MRI C-SPINE WO"),
        () -> assertInferred(ExamTemplate.THORACIC_SPINE, "MR TSPINE W WO"),
        () -> assertInferred(ExamTemplate.LUMBAR_SPINE, "MRI LUMBAR SPINE"),
        () -> assertInferred(ExamTemplate.BRAIN, "MR BRAIN DTI"),
        () -> assertInferred(ExamTemplate.SHOULDER, "MRI SHOULDER^RIGHT SHOULDER"),
        () -> assertInferred(ExamTemplate.ELBOW, "LEFT ELBOW MRI"),
        () -> assertInferred(ExamTemplate.WRIST, "MRI WRIST LEFT"),
        () -> assertInferred(ExamTemplate.HAND, "MR HAND WO CONTRAST"),
        () -> assertInferred(ExamTemplate.HIP, "RIGHT HIP MRI"),
        () -> assertInferred(ExamTemplate.KNEE, "MRI LT KNEE"),
        () -> assertInferred(ExamTemplate.ANKLE, "MRI ANKLE RIGHT"),
        () -> assertInferred(ExamTemplate.FOOT, "MR FOOT LEFT"),
        () -> assertEquals(Optional.empty(), MriFindingCatalog.inferExam("MRI PELVIS")));
  }

  @Test
  void preservesTheWristLunotriquetralTearPhrase() {
    FindingChoice tear = choice(ExamTemplate.WRIST, "Intrinsic ligaments", "Tear");

    GeneratedFinding generated =
        MriFindingCatalog.generate(
            ExamTemplate.WRIST, "Intrinsic ligaments", "lunotriquetral ligament", tear);

    assertEquals("Tear of the lunotriquetral ligament.", generated.findingText());
    assertEquals("lunotriquetral ligament tear.", generated.impressionText());
  }

  @Test
  void generatesRepresentativeSpineAndKneePhrases() {
    FindingChoice stenosis = choice(ExamTemplate.LUMBAR_SPINE, "Neural foramina", "Severe right");
    FindingChoice meniscus = choice(ExamTemplate.KNEE, "Menisci", "Horizontal tear");

    assertEquals(
        "Severe right neural foraminal narrowing at L4-5.",
        MriFindingCatalog.generate(ExamTemplate.LUMBAR_SPINE, "Neural foramina", "L4-5", stenosis)
            .findingText());
    assertEquals(
        "Horizontal tear of the medial meniscus.",
        MriFindingCatalog.generate(ExamTemplate.KNEE, "Menisci", "medial meniscus", meniscus)
            .findingText());
  }

  private static FindingChoice choice(ExamTemplate exam, String category, String label) {
    return MriFindingCatalog.findings(exam, category).stream()
        .filter(choice -> label.equals(choice.label()))
        .findFirst()
        .orElseThrow();
  }

  private static void assertInferred(ExamTemplate expected, String description) {
    assertEquals(Optional.of(expected), MriFindingCatalog.inferExam(description));
  }
}
