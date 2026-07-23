/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Map;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.lut.PresetWindowLevel;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.lut.LutShape;
import org.weasis.opencv.op.lut.WlPresentation;

class DicomImageElementTest {

  @Test
  void thumbnailUsesAutoLevelForImplausibleMrPreset() {
    PresetWindowLevel dicomPreset = preset("Default 1 [DICOM]", 1656.0, 953.0, false);
    PresetWindowLevel autoPreset = preset("Auto Level [Image]", 189.0, 95.5, true);
    TestDicomImageElement image =
        new TestDicomImageElement(reader("MR"), dicomPreset, autoPreset, 1.0, 190.0);

    image.getRenderedImageForThumbnail(null);

    assertEquals(189.0, image.renderedParams.get(ActionW.WINDOW.cmd()));
    assertEquals(95.5, image.renderedParams.get(ActionW.LEVEL.cmd()));
    assertEquals(LutShape.LINEAR, image.renderedParams.get(ActionW.LUT_SHAPE.cmd()));
  }

  @Test
  void thumbnailKeepsPlausibleMrPreset() {
    PresetWindowLevel dicomPreset = preset("Default 1 [DICOM]", 1656.0, 953.0, false);
    PresetWindowLevel autoPreset = preset("Auto Level [Image]", 4095.0, 2047.5, true);
    TestDicomImageElement image =
        new TestDicomImageElement(reader("MR"), dicomPreset, autoPreset, 0.0, 4095.0);

    image.getRenderedImageForThumbnail(null);

    assertNull(image.renderedParams);
  }

  @Test
  void thumbnailKeepsNonMrPreset() {
    PresetWindowLevel dicomPreset = preset("Default 1 [DICOM]", 1656.0, 953.0, false);
    PresetWindowLevel autoPreset = preset("Auto Level [Image]", 189.0, 95.5, true);
    TestDicomImageElement image =
        new TestDicomImageElement(reader("CT"), dicomPreset, autoPreset, 1.0, 190.0);

    image.getRenderedImageForThumbnail(null);

    assertNull(image.renderedParams);
  }

  private static DcmMediaReader reader(String modality) {
    DcmMediaReader reader = mock(DcmMediaReader.class);
    when(reader.getTagValue(TagD.get(Tag.Modality))).thenReturn(modality);
    return reader;
  }

  private static PresetWindowLevel preset(
      String name, double window, double level, boolean autoLevel) {
    PresetWindowLevel preset = new PresetWindowLevel(name, window, level, LutShape.LINEAR);
    if (autoLevel) {
      preset.setKeyCode(KeyEvent.VK_0);
    }
    return preset;
  }

  private static class TestDicomImageElement extends DicomImageElement {
    private final PresetWindowLevel defaultPreset;
    private final PresetWindowLevel autoPreset;
    private final double min;
    private final double max;
    private Map<String, Object> renderedParams;

    TestDicomImageElement(
        DcmMediaReader reader,
        PresetWindowLevel defaultPreset,
        PresetWindowLevel autoPreset,
        double min,
        double max) {
      super(reader, 0);
      this.defaultPreset = defaultPreset;
      this.autoPreset = autoPreset;
      this.min = min;
      this.max = max;
    }

    @Override
    public PresetWindowLevel getDefaultPreset(WlPresentation wlp) {
      return defaultPreset;
    }

    @Override
    public List<PresetWindowLevel> getPresetList(WlPresentation wlp) {
      return List.of(defaultPreset, autoPreset);
    }

    @Override
    public double getMinValue(WlPresentation wlp) {
      return min;
    }

    @Override
    public double getMaxValue(WlPresentation wlp) {
      return max;
    }

    @Override
    public PlanarImage getRenderedImage(PlanarImage imageSource, Map<String, Object> params) {
      renderedParams = params;
      return imageSource;
    }
  }
}
