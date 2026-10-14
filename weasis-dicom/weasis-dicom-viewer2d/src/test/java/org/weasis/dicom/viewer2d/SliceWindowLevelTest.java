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

class SliceWindowLevelTest {

  @Test
  void appliesToTheTaylorIntera() {
    assertTrue(
        SliceWindowLevel.applies(
            series("MR", "Philips Medical Systems", "Intera", " PHILIPS-2E18F45 ")));
  }

  @Test
  void ignoresOtherScannersAndModalities() {
    assertFalse(
        SliceWindowLevel.applies(
            series("MR", "Philips Medical Systems", "Intera", "PHILIPS-OTHER")));
    assertFalse(
        SliceWindowLevel.applies(
            series("MR", "Philips Medical Systems", "Ingenia", "PHILIPS-2E18F45")));
    assertFalse(SliceWindowLevel.applies(series("MR", "SIEMENS", "Intera", "PHILIPS-2E18F45")));
    assertFalse(
        SliceWindowLevel.applies(
            series("CT", "Philips Medical Systems", "Intera", "PHILIPS-2E18F45")));
    assertFalse(SliceWindowLevel.applies(null));
  }

  @Test
  void slicePresetKeepsTheEstimate() {
    PresetWindowLevel estimate =
        new PresetWindowLevel("Auto Level [Nearby slices]", 812.0, 406.0, LutShape.LINEAR);
    estimate.setKeyCode(KeyEvent.VK_0);

    PresetWindowLevel preset = SliceWindowLevel.slicePreset(estimate);

    assertEquals("Auto Level [This slice]", preset.getName());
    assertEquals(812.0, preset.getWindow());
    assertEquals(406.0, preset.getLevel());
    assertSame(LutShape.LINEAR, preset.getLutShape());
    assertEquals(KeyEvent.VK_0, preset.getKeyCode());
  }

  private static DicomSeries series(
      String modality, String manufacturer, String model, String station) {
    DicomSeries series = new DicomSeries("1.2.3." + station.strip() + model);
    series.setTagNoNull(TagD.get(Tag.Modality), modality);
    series.setTagNoNull(TagD.get(Tag.Manufacturer), manufacturer);
    series.setTagNoNull(TagD.get(Tag.ManufacturerModelName), model);
    series.setTagNoNull(TagD.get(Tag.StationName), station);
    return series;
  }
}
