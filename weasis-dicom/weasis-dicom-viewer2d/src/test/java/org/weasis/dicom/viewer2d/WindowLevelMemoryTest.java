/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dcm4che3.data.Tag;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.opencv.op.lut.LutShape;

class WindowLevelMemoryTest {

  @Test
  void remembersManualMrWindowLevel() {
    WindowLevelMemory memory = new WindowLevelMemory();
    DicomSeries series = buildSeries("mr-1", "MR");

    memory.remember(series, false, 850.5, 425.25, LutShape.SIGMOID);

    WindowLevelMemory.State state = memory.recall(series).orElseThrow();
    assertEquals(850.5, state.window());
    assertEquals(425.25, state.level());
    assertSame(LutShape.SIGMOID, state.lutShape());
  }

  @Test
  void selectingDefaultPresetForgetsManualValues() {
    WindowLevelMemory memory = new WindowLevelMemory();
    DicomSeries series = buildSeries("mr-2", "MR");
    memory.remember(series, false, 900.0, 450.0, LutShape.LINEAR);

    memory.remember(series, true, 600.0, 300.0, LutShape.LINEAR);

    assertTrue(memory.recall(series).isEmpty());
  }

  @Test
  void ignoresNonMrSeries() {
    WindowLevelMemory memory = new WindowLevelMemory();
    DicomSeries series = buildSeries("ct-1", "CT");

    memory.remember(series, false, 400.0, 40.0, LutShape.LINEAR);

    assertTrue(memory.recall(series).isEmpty());
  }

  @Test
  void doesNotCarryValuesIntoReimportedSeries() {
    WindowLevelMemory memory = new WindowLevelMemory();
    DicomSeries original = buildSeries("mr-3", "MR");
    memory.remember(original, false, 700.0, 350.0, LutShape.LINEAR);

    DicomSeries reimported = buildSeries("mr-3", "MR");

    assertTrue(memory.recall(reimported).isEmpty());
  }

  @Test
  void keepsValidMrWindowLevelStableAcrossImages() {
    DicomSeries series = buildSeries("mr-4", "MR");

    assertFalse(WindowLevelMemory.shouldRefreshDefaultPreset(series, true, false));
  }

  @Test
  void refreshesInvalidMrWindowLevel() {
    DicomSeries series = buildSeries("mr-5", "MR");

    assertTrue(WindowLevelMemory.shouldRefreshDefaultPreset(series, true, true));
  }

  @Test
  void retainsPerImagePresetBehaviorForNonMrSeries() {
    DicomSeries series = buildSeries("ct-2", "CT");

    assertTrue(WindowLevelMemory.shouldRefreshDefaultPreset(series, true, false));
  }

  private static DicomSeries buildSeries(String uid, String modality) {
    DicomSeries series = new DicomSeries(uid);
    series.setTagNoNull(TagD.get(Tag.Modality), modality);
    return series;
  }
}
