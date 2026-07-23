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

import java.awt.event.KeyEvent;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.lut.PresetWindowLevel;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.opencv.op.lut.LutShape;

class WindowLevelMemoryTest {

  @Test
  void remembersManualMrWindowLevel() {
    WindowLevelMemory memory = new WindowLevelMemory();
    DicomSeries series = buildSeries("mr-1", "MR");

    memory.remember(series, false, null, 850.5, 425.25, LutShape.SIGMOID);

    WindowLevelMemory.State state = memory.recall(series).orElseThrow();
    assertSame(WindowLevelMemory.Mode.MANUAL, state.mode());
    assertEquals(850.5, state.window());
    assertEquals(425.25, state.level());
    assertSame(LutShape.SIGMOID, state.lutShape());
  }

  @Test
  void selectingDefaultPresetForgetsManualValues() {
    WindowLevelMemory memory = new WindowLevelMemory();
    DicomSeries series = buildSeries("mr-2", "MR");
    memory.remember(series, false, null, 900.0, 450.0, LutShape.LINEAR);

    memory.remember(series, true, null, 600.0, 300.0, LutShape.LINEAR);

    assertTrue(memory.recall(series).isEmpty());
  }

  @Test
  void ignoresNonMrSeries() {
    WindowLevelMemory memory = new WindowLevelMemory();
    DicomSeries series = buildSeries("ct-1", "CT");

    memory.remember(series, false, null, 400.0, 40.0, LutShape.LINEAR);

    assertTrue(memory.recall(series).isEmpty());
  }

  @Test
  void doesNotCarryValuesIntoReimportedSeries() {
    WindowLevelMemory memory = new WindowLevelMemory();
    DicomSeries original = buildSeries("mr-3", "MR");
    memory.remember(original, false, null, 700.0, 350.0, LutShape.LINEAR);

    DicomSeries reimported = buildSeries("mr-3", "MR");

    assertTrue(memory.recall(reimported).isEmpty());
  }

  @Test
  void remembersAutoLevelAsAMode() {
    DicomSeries series = buildSeries("mr-4", "MR");
    PresetWindowLevel autoPreset = preset(true);

    WindowLevelMemory memory = new WindowLevelMemory();
    memory.remember(series, false, autoPreset, 210.0, 105.0, LutShape.LINEAR);

    WindowLevelMemory.State state = memory.recall(series).orElseThrow();
    assertSame(WindowLevelMemory.Mode.AUTO, state.mode());
  }

  @Test
  void keepsManualMrWindowLevelStableAcrossImages() {
    DicomSeries series = buildSeries("mr-5", "MR");

    assertFalse(WindowLevelMemory.shouldRefreshPreset(series, null, false));
  }

  @Test
  void refreshesAutoLevelForEachMrImage() {
    DicomSeries series = buildSeries("mr-6", "MR");

    assertTrue(WindowLevelMemory.shouldRefreshPreset(series, preset(true), false));
  }

  @Test
  void keepsDicomMrPresetStableAcrossImages() {
    DicomSeries series = buildSeries("mr-7", "MR");

    assertFalse(WindowLevelMemory.shouldRefreshPreset(series, preset(false), false));
  }

  @Test
  void refreshesInvalidMrWindowLevel() {
    DicomSeries series = buildSeries("mr-8", "MR");

    assertTrue(WindowLevelMemory.shouldRefreshPreset(series, preset(false), true));
  }

  @Test
  void retainsPerImagePresetBehaviorForNonMrSeries() {
    DicomSeries series = buildSeries("ct-9", "CT");

    assertTrue(WindowLevelMemory.shouldRefreshPreset(series, preset(false), false));
  }

  private static DicomSeries buildSeries(String uid, String modality) {
    DicomSeries series = new DicomSeries(uid);
    series.setTagNoNull(TagD.get(Tag.Modality), modality);
    return series;
  }

  private static PresetWindowLevel preset(boolean autoLevel) {
    PresetWindowLevel preset = new PresetWindowLevel("Preset", 800.0, 400.0, LutShape.LINEAR);
    if (autoLevel) {
      preset.setKeyCode(KeyEvent.VK_0);
    }
    return preset;
  }
}
