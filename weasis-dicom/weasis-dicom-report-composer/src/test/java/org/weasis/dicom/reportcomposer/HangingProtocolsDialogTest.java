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
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.reportcomposer.HangingProtocolsDialog.OpenStudy;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.ExamTemplate;
import org.weasis.dicom.reportcomposer.ViewerMacros.SequenceOrder;

class HangingProtocolsDialogTest {
  private static final SequenceOrder SPINE =
      new SequenceOrder(
          "Spine MR",
          List.of("LUMBAR_SPINE"),
          "1x2",
          List.of("SAG T2", "COR", "SAG STIR", "SAG T1", "AX T2"));
  private static final OpenStudy LUMBAR =
      new OpenStudy(
          Optional.of(ExamTemplate.LUMBAR_SPINE),
          "MRI LUMBAR SPINE",
          List.of("SURVEY", "SAG T1", "SAG T2", "COR T2", "AX T2"));

  @Test
  void thePreviewShowsWhatEachViewportGetsAndWhatFollows() {
    assertEquals(
        """
        Open study: MRI LUMBAR SPINE (Lumbar spine MRI)
        Hung by "Spine MR" in 1x2
          Viewport 1: SAG T2
          Viewport 2: COR T2
          Then: SAG T1
          Then: AX T2
          Then: SURVEY""",
        HangingProtocolsDialog.preview(LUMBAR, List.of(SPINE)));
  }

  @Test
  void aStudyNoProtocolCoversGoesBySeriesNumber() {
    String preview = HangingProtocolsDialog.preview(LUMBAR, List.of());

    assertEquals(
        "No protocol covers it: series number order in 1x2", preview.lines().toList().get(1));
    assertEquals("  Viewport 1: SAG T1", preview.lines().toList().get(2));
  }

  @Test
  void layoutsCountTheirViewports() {
    assertEquals(2, HangingProtocolsDialog.viewportCount("1x2"));
    assertEquals(4, HangingProtocolsDialog.viewportCount("2x2"));
    assertEquals(2, HangingProtocolsDialog.viewportCount("layout_c2x1"));
  }
}
