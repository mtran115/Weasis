/*
 * Copyright (c) 2009-2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.event.KeyEvent;
import java.util.List;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.lut.PresetWindowLevel;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.ImageOpEvent;
import org.weasis.core.api.image.ImageOpEvent.OpEvent;
import org.weasis.core.api.image.WindowOp;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.opencv.op.lut.LutShape;
import org.weasis.opencv.op.lut.WlPresentation;

class WindowAndPresetsOpTest {

  @Test
  void imageChangePreservesPlausibleSeriesWindowLevelWhenDefaultPresetIsActive() {
    WindowAndPresetsOp op = new WindowAndPresetsOp();
    DicomImageElement image = mockImage(820.0, 410.0, 0.0, 1023.0);
    op.setParam(ActionW.DEFAULT_PRESET.cmd(), true);
    op.setParam(ActionW.WINDOW.cmd(), 820.0);
    op.setParam(ActionW.LEVEL.cmd(), 410.0);
    op.setParam(ActionW.LEVEL_MIN.cmd(), -100.0);
    op.setParam(ActionW.LEVEL_MAX.cmd(), 1123.0);

    op.handleImageOpEvent(new ImageOpEvent(OpEvent.IMAGE_CHANGE, buildSeries("MR"), image, null));

    assertSame(image, op.getParam(WindowOp.P_IMAGE_ELEMENT));
    assertEquals(820.0, op.getParam(ActionW.WINDOW.cmd()));
    assertEquals(410.0, op.getParam(ActionW.LEVEL.cmd()));
    assertEquals(-100.0, op.getParam(ActionW.LEVEL_MIN.cmd()));
    assertEquals(1123.0, op.getParam(ActionW.LEVEL_MAX.cmd()));
  }

  @Test
  void imageChangeSwitchesImplausibleMrDefaultWindowLevelToAuto() {
    WindowAndPresetsOp op = new WindowAndPresetsOp();
    DicomImageElement image = mockImage(210.0, 105.0, 0.0, 210.0);
    PresetWindowLevel dicomPreset = preset("Default 1 [DICOM]", 1860.0, 1070.0, false);
    PresetWindowLevel autoPreset = preset("Auto Level [Image]", 210.0, 105.0, true);
    when(image.getPresetList(any(WlPresentation.class)))
        .thenReturn(List.of(dicomPreset, autoPreset));
    op.setParam(ActionW.PRESET.cmd(), dicomPreset);
    op.setParam(ActionW.DEFAULT_PRESET.cmd(), true);
    op.setParam(ActionW.WINDOW.cmd(), dicomPreset.getWindow());
    op.setParam(ActionW.LEVEL.cmd(), dicomPreset.getLevel());

    op.handleImageOpEvent(new ImageOpEvent(OpEvent.IMAGE_CHANGE, buildSeries("MR"), image, null));

    assertSame(autoPreset, op.getParam(ActionW.PRESET.cmd()));
    assertFalse((Boolean) op.getParam(ActionW.DEFAULT_PRESET.cmd()));
    assertEquals(210.0, op.getParam(ActionW.WINDOW.cmd()));
    assertEquals(105.0, op.getParam(ActionW.LEVEL.cmd()));
  }

  @Test
  void imageChangeRefreshesWindowLevelForNonMrSeries() {
    WindowAndPresetsOp op = new WindowAndPresetsOp();
    DicomImageElement image = mockImage(820.0, 410.0, 0.0, 1023.0);
    op.setParam(ActionW.DEFAULT_PRESET.cmd(), true);
    op.setParam(ActionW.WINDOW.cmd(), 12000.0);
    op.setParam(ActionW.LEVEL.cmd(), 6000.0);

    op.handleImageOpEvent(new ImageOpEvent(OpEvent.IMAGE_CHANGE, buildSeries("CT"), image, null));

    assertSame(image, op.getParam(WindowOp.P_IMAGE_ELEMENT));
    assertEquals(820.0, op.getParam(ActionW.WINDOW.cmd()));
    assertEquals(410.0, op.getParam(ActionW.LEVEL.cmd()));
    assertEquals(0.0, op.getParam(ActionW.LEVEL_MIN.cmd()));
    assertEquals(1023.0, op.getParam(ActionW.LEVEL_MAX.cmd()));
  }

  @Test
  void imageChangeRefreshesAutoLevelForMrSeries() {
    WindowAndPresetsOp op = new WindowAndPresetsOp();
    DicomImageElement image = mockImage(210.0, 105.0, 0.0, 210.0);
    PresetWindowLevel previousAuto = preset("Auto Level [Image]", 189.0, 95.5, true);
    PresetWindowLevel currentAuto = preset("Auto Level [Image]", 210.0, 105.0, true);
    when(image.getPresetList(any(WlPresentation.class))).thenReturn(List.of(currentAuto));
    op.setParam(ActionW.PRESET.cmd(), previousAuto);
    op.setParam(ActionW.DEFAULT_PRESET.cmd(), false);
    op.setParam(ActionW.WINDOW.cmd(), previousAuto.getWindow());
    op.setParam(ActionW.LEVEL.cmd(), previousAuto.getLevel());

    op.handleImageOpEvent(new ImageOpEvent(OpEvent.IMAGE_CHANGE, buildSeries("MR"), image, null));

    assertSame(currentAuto, op.getParam(ActionW.PRESET.cmd()));
    assertFalse((Boolean) op.getParam(ActionW.DEFAULT_PRESET.cmd()));
    assertEquals(210.0, op.getParam(ActionW.WINDOW.cmd()));
    assertEquals(105.0, op.getParam(ActionW.LEVEL.cmd()));
  }

  @Test
  void seriesChangeLoadsTheNewSeriesDefaultWindowLevel() {
    WindowAndPresetsOp op = new WindowAndPresetsOp();
    DicomImageElement image = mockImage(820.0, 410.0, 0.0, 1023.0);
    op.setParam(ActionW.WINDOW.cmd(), 12000.0);
    op.setParam(ActionW.LEVEL.cmd(), 6000.0);

    op.handleImageOpEvent(ImageOpEvent.withImage(OpEvent.SERIES_CHANGE, image));

    assertSame(image, op.getParam(WindowOp.P_IMAGE_ELEMENT));
    assertEquals(820.0, op.getParam(ActionW.WINDOW.cmd()));
    assertEquals(410.0, op.getParam(ActionW.LEVEL.cmd()));
    assertEquals(0.0, op.getParam(ActionW.LEVEL_MIN.cmd()));
    assertEquals(1023.0, op.getParam(ActionW.LEVEL_MAX.cmd()));
  }

  @Test
  void seriesChangeUsesAutoLevelForImplausibleMrDicomPreset() {
    WindowAndPresetsOp op = new WindowAndPresetsOp();
    DicomImageElement image = mockImage(1656.0, 953.0, 1.0, 190.0);
    PresetWindowLevel dicomPreset = preset("Default 1 [DICOM]", 1656.0, 953.0, false);
    PresetWindowLevel autoPreset = preset("Auto Level [Image]", 189.0, 95.5, true);
    when(image.getDefaultPreset(any(WlPresentation.class))).thenReturn(dicomPreset);
    when(image.getPresetList(any(WlPresentation.class)))
        .thenReturn(List.of(dicomPreset, autoPreset));

    op.handleImageOpEvent(new ImageOpEvent(OpEvent.SERIES_CHANGE, buildSeries("MR"), image, null));

    assertSame(autoPreset, op.getParam(ActionW.PRESET.cmd()));
    assertFalse((Boolean) op.getParam(ActionW.DEFAULT_PRESET.cmd()));
    assertEquals(189.0, op.getParam(ActionW.WINDOW.cmd()));
    assertEquals(95.5, op.getParam(ActionW.LEVEL.cmd()));
  }

  @Test
  void seriesChangeKeepsPlausibleMrDicomPreset() {
    WindowAndPresetsOp op = new WindowAndPresetsOp();
    DicomImageElement image = mockImage(1656.0, 953.0, 0.0, 4095.0);
    PresetWindowLevel dicomPreset = preset("Default 1 [DICOM]", 1656.0, 953.0, false);
    PresetWindowLevel autoPreset = preset("Auto Level [Image]", 4095.0, 2047.5, true);
    when(image.getDefaultPreset(any(WlPresentation.class))).thenReturn(dicomPreset);
    when(image.getPresetList(any(WlPresentation.class)))
        .thenReturn(List.of(dicomPreset, autoPreset));

    op.handleImageOpEvent(new ImageOpEvent(OpEvent.SERIES_CHANGE, buildSeries("MR"), image, null));

    assertSame(dicomPreset, op.getParam(ActionW.PRESET.cmd()));
    assertTrue((Boolean) op.getParam(ActionW.DEFAULT_PRESET.cmd()));
    assertEquals(1656.0, op.getParam(ActionW.WINDOW.cmd()));
    assertEquals(953.0, op.getParam(ActionW.LEVEL.cmd()));
  }

  @Test
  void seriesChangeDoesNotOverrideNonMrDicomPreset() {
    WindowAndPresetsOp op = new WindowAndPresetsOp();
    DicomImageElement image = mockImage(1656.0, 953.0, 1.0, 190.0);
    PresetWindowLevel dicomPreset = preset("Default 1 [DICOM]", 1656.0, 953.0, false);
    PresetWindowLevel autoPreset = preset("Auto Level [Image]", 189.0, 95.5, true);
    when(image.getDefaultPreset(any(WlPresentation.class))).thenReturn(dicomPreset);
    when(image.getPresetList(any(WlPresentation.class)))
        .thenReturn(List.of(dicomPreset, autoPreset));

    op.handleImageOpEvent(new ImageOpEvent(OpEvent.SERIES_CHANGE, buildSeries("CT"), image, null));

    assertSame(dicomPreset, op.getParam(ActionW.PRESET.cmd()));
    assertTrue((Boolean) op.getParam(ActionW.DEFAULT_PRESET.cmd()));
  }

  @Test
  void imageChangePreservesManuallyAdjustedWindowLevel() {
    WindowAndPresetsOp op = new WindowAndPresetsOp();
    DicomImageElement image = mockImage(820.0, 410.0, 0.0, 1023.0);
    op.setParam(ActionW.DEFAULT_PRESET.cmd(), false);
    op.setParam(ActionW.WINDOW.cmd(), 12000.0);
    op.setParam(ActionW.LEVEL.cmd(), 6000.0);

    op.handleImageOpEvent(new ImageOpEvent(OpEvent.IMAGE_CHANGE, buildSeries("MR"), image, null));

    assertSame(image, op.getParam(WindowOp.P_IMAGE_ELEMENT));
    assertEquals(12000.0, op.getParam(ActionW.WINDOW.cmd()));
    assertEquals(6000.0, op.getParam(ActionW.LEVEL.cmd()));
    assertFalse((Boolean) op.getParam(ActionW.DEFAULT_PRESET.cmd()));
  }

  @Test
  void broadCenteredClinicalPresetIsNotRejected() {
    PresetWindowLevel preset = preset("Broad centered preset", 10000.0, 500.0, false);

    assertFalse(WindowAndPresetsOp.isImplausiblePreset(preset, 450.0, 550.0));
  }

  @Test
  void seriesChangeCorrectsDarkPresetPreviouslyJustAboveTheThreshold() {
    DicomImageElement image = mockImage(4000.0, 2000.0, 0.0, 1100.0);
    PresetWindowLevel dicom =
        new PresetWindowLevel(
            "Default [DICOM]",
            4000.0,
            2000.0,
            new LutShape(LutShape.Function.LINEAR, "Linear [DICOM]"));
    PresetWindowLevel auto = preset("Auto Level [Image]", 1100.0, 550.0, true);
    when(image.getDefaultPreset(any(WlPresentation.class))).thenReturn(dicom);
    when(image.getPresetList(any(WlPresentation.class))).thenReturn(List.of(dicom, auto));
    WindowAndPresetsOp op = new WindowAndPresetsOp();

    op.handleImageOpEvent(new ImageOpEvent(OpEvent.SERIES_CHANGE, buildSeries("MR"), image, null));

    assertSame(auto, op.getParam(ActionW.PRESET.cmd()));
    assertEquals(1100.0, op.getParam(ActionW.WINDOW.cmd()));
  }

  @Test
  void outlierDoesNotHideDarkAnatomyAndCorrectedAutoFollowsTheNextImage() {
    DicomImageElement first = mockImage(4000.0, 2000.0, 0.0, 60000.0);
    PresetWindowLevel dicom = preset("Default [DICOM]", 4000.0, 2000.0, false);
    PresetWindowLevel auto = preset("Auto Level [Image]", 800.0, 400.0, true);
    when(first.getMrWindowLevelRange(any(WlPresentation.class)))
        .thenReturn(new MrWindowLevelRange(0.0, 800.0));
    when(first.getDefaultPreset(any(WlPresentation.class))).thenReturn(dicom);
    when(first.getPresetList(any(WlPresentation.class))).thenReturn(List.of(dicom, auto));
    WindowAndPresetsOp op = new WindowAndPresetsOp();

    op.handleImageOpEvent(new ImageOpEvent(OpEvent.SERIES_CHANGE, buildSeries("MR"), first, null));

    assertSame(auto, op.getParam(ActionW.PRESET.cmd()));
    assertEquals(800.0, op.getParam(ActionW.WINDOW.cmd()));
    // Display statistics still include the original bright pixels.
    assertEquals(60000.0, op.getParam(ActionW.LEVEL_MAX.cmd()));

    DicomImageElement next = mockImage(5000.0, 2500.0, 0.0, 65000.0);
    PresetWindowLevel nextAuto = preset("Auto Level [Image]", 900.0, 450.0, true);
    when(next.getPresetList(any(WlPresentation.class))).thenReturn(List.of(nextAuto));
    op.handleImageOpEvent(new ImageOpEvent(OpEvent.IMAGE_CHANGE, buildSeries("MR"), next, null));

    assertSame(nextAuto, op.getParam(ActionW.PRESET.cmd()));
    assertEquals(900.0, op.getParam(ActionW.WINDOW.cmd()));
  }

  @Test
  void robustBoundsDoNotOverrideManualWindowLevel() {
    DicomImageElement image = mockImage(4000.0, 2000.0, 0.0, 60000.0);
    when(image.getMrWindowLevelRange(any(WlPresentation.class)))
        .thenReturn(new MrWindowLevelRange(0.0, 800.0));
    WindowAndPresetsOp op = new WindowAndPresetsOp();
    op.setParam(ActionW.DEFAULT_PRESET.cmd(), false);
    op.setParam(ActionW.WINDOW.cmd(), 4000.0);
    op.setParam(ActionW.LEVEL.cmd(), 2000.0);

    op.handleImageOpEvent(new ImageOpEvent(OpEvent.IMAGE_CHANGE, buildSeries("MR"), image, null));

    assertEquals(4000.0, op.getParam(ActionW.WINDOW.cmd()));
    assertEquals(2000.0, op.getParam(ActionW.LEVEL.cmd()));
  }

  @Test
  void nonlinearVoiPresetIsNotJudgedByLinearBrightness() {
    DicomImageElement image = mockImage(4000.0, 2000.0, 0.0, 800.0);
    PresetWindowLevel sigmoid = new PresetWindowLevel("Sigmoid", 4000.0, 2000.0, LutShape.SIGMOID);
    when(image.getDefaultPreset(any(WlPresentation.class))).thenReturn(sigmoid);
    WindowAndPresetsOp op = new WindowAndPresetsOp();

    op.handleImageOpEvent(new ImageOpEvent(OpEvent.SERIES_CHANGE, buildSeries("MR"), image, null));

    assertSame(sigmoid, op.getParam(ActionW.PRESET.cmd()));
  }

  private static DicomImageElement mockImage(double window, double level, double min, double max) {
    DicomImageElement image = mock(DicomImageElement.class);
    when(image.isImageAvailable()).thenReturn(true);
    when(image.getDefaultWindow(any(WlPresentation.class))).thenReturn(window);
    when(image.getDefaultLevel(any(WlPresentation.class))).thenReturn(level);
    when(image.getMinValue(any(WlPresentation.class))).thenReturn(min);
    when(image.getMaxValue(any(WlPresentation.class))).thenReturn(max);
    return image;
  }

  private static DicomSeries buildSeries(String modality) {
    DicomSeries series = new DicomSeries("series-" + modality);
    series.setTagNoNull(TagD.get(Tag.Modality), modality);
    return series;
  }

  private static PresetWindowLevel preset(
      String name, double window, double level, boolean autoLevel) {
    PresetWindowLevel preset = new PresetWindowLevel(name, window, level, LutShape.LINEAR);
    if (autoLevel) {
      preset.setKeyCode(KeyEvent.VK_0);
    }
    return preset;
  }
}
