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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AiMarksTest {
  private static final String STUDY = "1.2.3";

  @Test
  void ringsAreFifteenMillimetersAcrossInPatientSpace() {
    assertArrayEquals(
        new double[] {15, 15}, AiMarks.radiusPixels(image("a", 0.5, 0.5).geometry()), 1e-9);
    // Non-square pixels: 0.5 mm between rows, 0.25 mm between columns.
    assertArrayEquals(
        new double[] {30, 15}, AiMarks.radiusPixels(image("a", 0.5, 0.25).geometry()), 1e-9);
    assertArrayEquals(new double[] {12, 12}, AiMarks.radiusPixels(ImageGeometry.empty()), 1e-9);
  }

  @Test
  void clickingInsideARingRemovesItAndTheRestAreRenumbered() {
    AiMarks marks = new AiMarks();
    ImageReference first = image("sop-1", 0.5, 0.5);
    ImageReference second = image("sop-2", 0.5, 0.5);

    assertTrue(marks.toggle(STUDY, first, 100, 100));
    assertTrue(marks.toggle(STUDY, second, 100, 100));
    assertTrue(marks.toggle(STUDY, first, 200, 200));
    assertEquals(
        List.of(1, 3),
        marks.forImage(STUDY, "sop-1", 0).stream().map(AiMarks.Mark::number).toList());

    // Ten pixels away is inside the 15-pixel radius of ring 1, so it removes that ring.
    assertFalse(marks.toggle(STUDY, first, 110, 100));

    List<AiMarks.Mark> remaining = marks.forStudy(STUDY);
    assertEquals(List.of(1, 2), remaining.stream().map(AiMarks.Mark::number).toList());
    assertEquals(
        List.of("sop-2", "sop-1"), remaining.stream().map(AiMarks.Mark::sopInstanceUid).toList());
    assertTrue(marks.forStudy("other").isEmpty());

    marks.clear(STUDY);
    assertTrue(marks.forStudy(STUDY).isEmpty());
  }

  static ImageReference image(String sop, double rowSpacing, double columnSpacing) {
    ImageGeometry geometry =
        new ImageGeometry(
            400,
            400,
            List.of(1.0, 0.0, 0.0, 0.0, 1.0, 0.0),
            List.of(0.0, 0.0, 0.0),
            List.of(rowSpacing, columnSpacing),
            "frame");
    return new ImageReference("series", sop, "1", "", "1", 1, 1, 0, "", geometry);
  }
}
