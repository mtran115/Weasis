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
import org.weasis.dicom.reportcomposer.AnkleFindingBuilder.Bone;
import org.weasis.dicom.reportcomposer.AnkleFindingBuilder.Degree;
import org.weasis.dicom.reportcomposer.AnkleFindingBuilder.FluidAmount;
import org.weasis.dicom.reportcomposer.AnkleFindingBuilder.Ligament;
import org.weasis.dicom.reportcomposer.AnkleFindingBuilder.LigamentStatus;
import org.weasis.dicom.reportcomposer.AnkleFindingBuilder.Selection;
import org.weasis.dicom.reportcomposer.AnkleFindingBuilder.SwellingSide;
import org.weasis.dicom.reportcomposer.AnkleFindingBuilder.Tendon;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.GeneratedFinding;

class AnkleFindingBuilderTest {
  @Test
  void generatesFindingsInFormOrderWithSeparateSentencesPerStructure() {
    Selection selection =
        new Selection(
            false,
            Map.of(Tendon.PERONEUS_BREVIS, Degree.MILD, Tendon.PERONEUS_LONGUS, Degree.MINIMAL),
            List.of(Tendon.PERONEUS_LONGUS),
            Degree.NONE,
            Map.of(Ligament.DELTOID, LigamentStatus.MODERATE_SPRAIN),
            FluidAmount.NONE,
            FluidAmount.TRACE,
            List.of(SwellingSide.LATERAL),
            Map.of(Bone.TALUS, Degree.MILD, Bone.MEDIAL_MALLEOLUS, Degree.MODERATE),
            "limited by motion");

    assertEquals(
        List.of(
            "Minimal tenosynovitis of the peroneus longus tendon.",
            "Partial split tear of the peroneus longus tendon.",
            "Mild tenosynovitis of the peroneus brevis tendon.",
            "Moderate sprain of the deltoid ligament complex.",
            "Trace anterior ankle joint effusion, nonspecific and potentially inflammatory.",
            "Soft tissue swelling along the lateral ankle.",
            "Moderate bony contusion of the medial malleolus.",
            "Mild bony contusion of the talus.",
            "limited by motion."),
        AnkleFindingBuilder.generate(selection).stream()
            .map(GeneratedFinding::findingText)
            .toList());
  }

  @Test
  void normalIsSuppressedByAPositiveFindingAndAnEmptySelectionSaysNothing() {
    Selection empty = selection(false, Degree.NONE);
    assertFalse(empty.hasFinding());
    assertEquals(List.of(), AnkleFindingBuilder.generate(empty));

    assertEquals(
        "Normal MRI of the ankle.",
        AnkleFindingBuilder.generate(selection(true, Degree.NONE)).getFirst().findingText());
    assertEquals(
        "Minimal Achilles tendinosis.",
        AnkleFindingBuilder.generate(selection(true, Degree.MINIMAL)).getFirst().findingText());
  }

  private static Selection selection(boolean normal, Degree achilles) {
    return new Selection(
        normal,
        Map.of(),
        List.of(),
        achilles,
        Map.of(),
        FluidAmount.NONE,
        FluidAmount.NONE,
        List.of(),
        Map.of(),
        "");
  }
}
