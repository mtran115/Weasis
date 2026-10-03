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

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.weasis.dicom.reportcomposer.ShortcutCategory.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.ExamTemplate;
import org.weasis.dicom.reportcomposer.ReportQuickPhrases.Phrase;

class ShortcutCategoryTest {
  @Test
  void everyBuiltInShortcutIsFiledWhereAReaderWouldLookForIt() {
    Map<String, ShortcutCategory> expected =
        Map.ofEntries(
            entry("ACL reconstruction", POSTOP_HARDWARE),
            entry("Posterior fusion", POSTOP_HARDWARE),
            entry("ACDF changes", POSTOP_HARDWARE),
            entry("Cuff repair", POSTOP_HARDWARE),
            entry("Hemilaminectomy", POSTOP_HARDWARE),
            entry("Metal artifact", LIMITATIONS),
            entry("Motion limit", LIMITATIONS),
            entry("Sag T2 limited", LIMITATIONS),
            entry("Diffusion limited", LIMITATIONS),
            entry("Congenital fusion", NORMAL_VARIANTS),
            entry("Lumbarized S1", NORMAL_VARIANTS),
            entry("Contusion / OA", BONE_MARROW),
            entry("Carpal cyst", BONE_MARROW),
            entry("Lipid-poor hemang", BONE_MARROW),
            entry("Compression", BONE_MARROW),
            entry("Insertional enthesitis", TENDONS_LIGAMENTS),
            entry("Insertional traction", TENDONS_LIGAMENTS),
            entry("PLL thickening", TENDONS_LIGAMENTS),
            entry("Cuff atrophy", TENDONS_LIGAMENTS),
            entry("ECU split tear", TENDONS_LIGAMENTS),
            entry("Joint effusion", JOINT_DEGENERATIVE),
            entry("Paralabral cyst", JOINT_DEGENERATIVE),
            entry("Popliteal cyst", JOINT_DEGENERATIVE),
            entry("Lateral recess", JOINT_DEGENERATIVE),
            entry("Acetabular cysts", JOINT_DEGENERATIVE),
            entry("Dorsal lipo", SOFT_TISSUE_INCIDENTAL),
            entry("Dorsal ganglion", SOFT_TISSUE_INCIDENTAL),
            entry("Sinus cyst", SOFT_TISSUE_INCIDENTAL),
            entry("Prepatellar bursitis", SOFT_TISSUE_INCIDENTAL),
            entry("Pretibial edema", SOFT_TISSUE_INCIDENTAL));
    List<Phrase> builtIn = new ArrayList<>();
    for (ExamTemplate exam : ExamTemplate.values())
      builtIn.addAll(ReportQuickPhrases.forExam(exam));
    builtIn.add(ReportQuickPhrases.MOTION);
    builtIn.add(ReportQuickPhrases.ARTIFACT);
    builtIn.addAll(ReportQuickPhrases.COMMON_MORE);
    for (Phrase phrase : builtIn) {
      Optional<ShortcutCategory> suggested = suggest(phrase.label(), phrase.text());
      assertTrue(suggested.isPresent(), phrase.label());
      if (expected.containsKey(phrase.label())) {
        assertEquals(expected.get(phrase.label()), suggested.get(), phrase.label());
      }
    }
  }

  @Test
  void personalShorthandIsFiledByItsWordsOrLeftUnsorted() {
    Map<String, Optional<ShortcutCategory>> cases =
        Map.ofEntries(
            entry("There is a 2.7cm L thyroid nod", Optional.of(SOFT_TISSUE_INCIDENTAL)),
            entry("red marrow reconversion", Optional.of(NORMAL_VARIANTS)),
            entry("pt is skeletally immature", Optional.of(NORMAL_VARIANTS)),
            entry("Possible tear post horn med me", Optional.of(JOINT_DEGENERATIVE)),
            entry("Tear of the post sup labrum.", Optional.of(JOINT_DEGENERATIVE)),
            entry("Mild s shaped scoliotic curvat", Optional.of(JOINT_DEGENERATIVE)),
            entry("Troch bursitis", Optional.of(SOFT_TISSUE_INCIDENTAL)),
            entry(
                "mild edema in the infrapatellar fat may be related to a contusion",
                Optional.of(SOFT_TISSUE_INCIDENTAL)),
            entry("Mild ant wedging of L1.", Optional.of(BONE_MARROW)),
            entry("Gluteus med tear", Optional.of(TENDONS_LIGAMENTS)),
            entry("High grade tear sup prox to th", Optional.of(TENDONS_LIGAMENTS)),
            entry("Multiple sequences degraded by", Optional.of(LIMITATIONS)),
            entry("moderate cerebral volume loss", Optional.empty()));
    cases.forEach((label, category) -> assertEquals(category, suggest(label, ""), label));
  }

  @Test
  void storedValuesRoundTripAndUnknownOnesAreRejected() {
    assertEquals(Optional.empty(), ShortcutCategory.of(NONE));
    assertEquals(Optional.of(BONE_MARROW), ShortcutCategory.of("BONE_MARROW"));
    assertThrows(IllegalArgumentException.class, () -> ShortcutCategory.of("ORGANS"));
  }
}
