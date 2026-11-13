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
import org.weasis.dicom.reportcomposer.HipFindingBuilder.Degree;
import org.weasis.dicom.reportcomposer.HipFindingBuilder.FluidAmount;
import org.weasis.dicom.reportcomposer.HipFindingBuilder.LabralRegion;
import org.weasis.dicom.reportcomposer.HipFindingBuilder.Selection;
import org.weasis.dicom.reportcomposer.HipFindingBuilder.Side;
import org.weasis.dicom.reportcomposer.HipFindingBuilder.SideSelection;
import org.weasis.dicom.reportcomposer.HipFindingBuilder.TendonSelection;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.GeneratedFinding;

class HipFindingBuilderTest {
  @Test
  void reportsTheRightHipBeforeTheLeftWithTheSideInEachSentence() {
    SideSelection left =
        new SideSelection(
            Degree.NONE,
            List.of(LabralRegion.POSTERIOR, LabralRegion.SUPERIOR),
            true,
            "measuring 2.1 cm",
            Map.of(),
            new TendonSelection(Degree.MILD, true, false),
            Degree.SEVERE,
            FluidAmount.NONE);
    SideSelection right =
        new SideSelection(
            Degree.MILD, List.of(), true, "", Map.of(), null, Degree.NONE, FluidAmount.NONE);

    assertEquals(
        List.of(
            "Multiple sequences are degraded due to artifact.",
            "Mild right greater trochanteric bursitis.",
            "Right paralabral cyst.",
            "Tear of the left posterior superior labrum, with an associated paralabral cyst,"
                + " measuring 2.1 cm.",
            "Mild tendinosis of the left common hamstring tendon.",
            "Partial tear of the left common hamstring tendon.",
            "Severe osteoarthrosis of the left hip joint."),
        texts(new Selection(true, true, Map.of(Side.LEFT, left, Side.RIGHT, right), "")));
  }

  @Test
  void labralRegionsReadNaturally() {
    for (var test :
        Map.of(
                List.of(LabralRegion.WHOLE), "Tear of the right labrum.",
                List.of(LabralRegion.SUPERIOR), "Tear of the right superior labrum.",
                List.of(LabralRegion.ANTERIOR, LabralRegion.POSTERIOR),
                    "Tear of the right anterior and posterior labrum.",
                List.of(LabralRegion.ANTERIOR, LabralRegion.SUPERIOR, LabralRegion.POSTERIOR),
                    "Tear of the right anterior, superior, and posterior labrum.")
            .entrySet()) {
      SideSelection hip =
          new SideSelection(
              Degree.NONE, test.getKey(), false, "", Map.of(), null, Degree.NONE, FluidAmount.NONE);
      assertEquals(
          List.of(test.getValue()),
          texts(new Selection(false, false, Map.of(Side.RIGHT, hip), "")));
    }
  }

  @Test
  void normalStandsOnlyWithoutAPositiveFinding() {
    Selection empty = new Selection(false, false, Map.of(Side.LEFT, SideSelection.empty()), "");
    assertFalse(empty.hasFinding());
    assertEquals(List.of(), texts(empty));
    assertEquals(
        List.of("Normal MRI of the hip."), texts(new Selection(true, false, Map.of(), "")));
  }

  private static List<String> texts(Selection selection) {
    return HipFindingBuilder.generate(selection).stream()
        .map(GeneratedFinding::findingText)
        .toList();
  }
}
