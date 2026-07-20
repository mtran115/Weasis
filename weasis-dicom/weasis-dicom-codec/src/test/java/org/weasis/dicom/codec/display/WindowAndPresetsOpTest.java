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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.dcm4che3.data.Tag;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.ImageOpEvent;
import org.weasis.core.api.image.ImageOpEvent.OpEvent;
import org.weasis.core.api.image.WindowOp;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.opencv.op.lut.WlPresentation;

class WindowAndPresetsOpTest {

  @Test
  void imageChangePreservesSeriesWindowLevelWhenDefaultPresetIsActive() {
    WindowAndPresetsOp op = new WindowAndPresetsOp();
    DicomImageElement image = mockImage(820.0, 410.0, 0.0, 1023.0);
    op.setParam(ActionW.DEFAULT_PRESET.cmd(), true);
    op.setParam(ActionW.WINDOW.cmd(), 12000.0);
    op.setParam(ActionW.LEVEL.cmd(), 6000.0);
    op.setParam(ActionW.LEVEL_MIN.cmd(), -100.0);
    op.setParam(ActionW.LEVEL_MAX.cmd(), 12100.0);

    op.handleImageOpEvent(
        new ImageOpEvent(OpEvent.IMAGE_CHANGE, buildSeries("MR"), image, null));

    assertSame(image, op.getParam(WindowOp.P_IMAGE_ELEMENT));
    assertEquals(12000.0, op.getParam(ActionW.WINDOW.cmd()));
    assertEquals(6000.0, op.getParam(ActionW.LEVEL.cmd()));
    assertEquals(-100.0, op.getParam(ActionW.LEVEL_MIN.cmd()));
    assertEquals(12100.0, op.getParam(ActionW.LEVEL_MAX.cmd()));
  }

  @Test
  void imageChangeRefreshesWindowLevelForNonMrSeries() {
    WindowAndPresetsOp op = new WindowAndPresetsOp();
    DicomImageElement image = mockImage(820.0, 410.0, 0.0, 1023.0);
    op.setParam(ActionW.DEFAULT_PRESET.cmd(), true);
    op.setParam(ActionW.WINDOW.cmd(), 12000.0);
    op.setParam(ActionW.LEVEL.cmd(), 6000.0);

    op.handleImageOpEvent(
        new ImageOpEvent(OpEvent.IMAGE_CHANGE, buildSeries("CT"), image, null));

    assertSame(image, op.getParam(WindowOp.P_IMAGE_ELEMENT));
    assertEquals(820.0, op.getParam(ActionW.WINDOW.cmd()));
    assertEquals(410.0, op.getParam(ActionW.LEVEL.cmd()));
    assertEquals(0.0, op.getParam(ActionW.LEVEL_MIN.cmd()));
    assertEquals(1023.0, op.getParam(ActionW.LEVEL_MAX.cmd()));
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
  void imageChangePreservesManuallyAdjustedWindowLevel() {
    WindowAndPresetsOp op = new WindowAndPresetsOp();
    DicomImageElement image = mockImage(820.0, 410.0, 0.0, 1023.0);
    op.setParam(ActionW.DEFAULT_PRESET.cmd(), false);
    op.setParam(ActionW.WINDOW.cmd(), 275.0);
    op.setParam(ActionW.LEVEL.cmd(), 145.0);

    op.handleImageOpEvent(ImageOpEvent.withImage(OpEvent.IMAGE_CHANGE, image));

    assertSame(image, op.getParam(WindowOp.P_IMAGE_ELEMENT));
    assertEquals(275.0, op.getParam(ActionW.WINDOW.cmd()));
    assertEquals(145.0, op.getParam(ActionW.LEVEL.cmd()));
    assertFalse((Boolean) op.getParam(ActionW.DEFAULT_PRESET.cmd()));
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
}
