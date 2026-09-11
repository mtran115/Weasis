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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.BoundedRangeModel;
import javax.swing.DefaultBoundedRangeModel;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.lut.PresetWindowLevel;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.opencv.core.CvType;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.Feature;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.image.ImageOpEvent;
import org.weasis.core.api.image.SimpleOpManager;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.display.WindowAndPresetsOp;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.lut.LutShape;
import org.weasis.opencv.op.lut.WlPresentation;

class MrWindowLevelScrollingTest {
  @ParameterizedTest
  @CsvSource({"true,800.25,333.75", "false,800.25,333.75", "false,1.0,0.0"})
  void scrollingKeepsExactContrastDespiteAlternatingExtremaAndRoundedSliders(
      boolean auto, double window, double level) throws Exception {
    EventManager manager = mock(EventManager.class);
    View2d view = mock(View2d.class);
    View2dContainer container = mock(View2dContainer.class);
    when(container.getSelectedViewCanvas()).thenReturn(view);
    Field selected = ImageViewerEventManager.class.getDeclaredField("selectedView2dContainer");
    selected.setAccessible(true);
    selected.set(manager, container);

    DicomSeries series = mock(DicomSeries.class);
    when(series.getTagValue(TagD.get(Tag.Modality))).thenReturn("MR");
    when(view.getSeries()).thenReturn(series);
    List<DicomImageElement> slices = List.of(image(1100), image(60000), image(1300));
    when(series.getMedia(anyInt(), any(), any()))
        .thenAnswer(invocation -> slices.get(invocation.getArgument(0)));
    AtomicReference<DicomImageElement> current = new AtomicReference<>(slices.getFirst());
    when(view.getImage()).thenAnswer(invocation -> current.get());

    WindowAndPresetsOp node = new WindowAndPresetsOp();
    SimpleOpManager display = new SimpleOpManager();
    display.addImageOperationAction(node);
    when(view.getDisplayOpManager()).thenReturn(display);
    PresetWindowLevel preset = auto ? autoPreset(window, level) : null;
    node.setParam(WindowOp.P_IMAGE_ELEMENT, current.get());
    node.setParam(ActionW.PRESET.cmd(), preset);
    node.setParam(ActionW.DEFAULT_PRESET.cmd(), false);
    node.setParam(ActionW.WINDOW.cmd(), window);
    node.setParam(ActionW.LEVEL.cmd(), level);
    node.setParam(ActionW.LUT_SHAPE.cmd(), LutShape.LINEAR);
    SliderChangeListener windowSlider = slider(ActionW.WINDOW, 1, 65535, window);
    SliderChangeListener levelSlider = slider(ActionW.LEVEL, 0, 65535, level);
    if (window > 1) assertNotEquals(window, windowSlider.getRealValue());
    doReturn(Optional.of(windowSlider)).when(manager).getAction(ActionW.WINDOW);
    doReturn(Optional.of(levelSlider)).when(manager).getAction(ActionW.LEVEL);
    doReturn(Optional.of(mock(ToggleButtonListener.class)))
        .when(manager)
        .getAction(ActionW.DEFAULT_PRESET);
    doCallRealMethod()
        .when(manager)
        .getMoveTroughSliceAction(20, SliderCineListener.TIME.SECOND, .1);
    SliderCineListener scroll =
        manager.getMoveTroughSliceAction(20, SliderCineListener.TIME.SECOND, .1);

    for (int index : new int[] {1, 2, 1, 0, 1, 2, 0}) {
      scroll.stateChanged(new DefaultBoundedRangeModel(index + 1, 0, 1, slices.size()));
      DicomImageElement next = slices.get(index);
      // Selected-pane preparation and the image-operation event are separate scrolling paths.
      node.handleImageOpEvent(
          new ImageOpEvent(ImageOpEvent.OpEvent.IMAGE_CHANGE, series, next, null));
      current.set(next);
      assertSame(next, node.getParam(WindowOp.P_IMAGE_ELEMENT));
      assertSame(preset, node.getParam(ActionW.PRESET.cmd()));
      assertEquals(window, node.getParam(ActionW.WINDOW.cmd()));
      assertEquals(level, node.getParam(ActionW.LEVEL.cmd()));
      assertSame(LutShape.LINEAR, node.getParam(ActionW.LUT_SHAPE.cmd()));
    }
  }

  private static SliderChangeListener slider(
      Feature<SliderChangeListener> action, double min, double max, double value) {
    return new SliderChangeListener(action, min, max, value, true, 1, 4096) {
      @Override
      public void stateChanged(BoundedRangeModel model) {}
    };
  }

  private static DicomImageElement image(double max) {
    DicomImageElement image = mock(DicomImageElement.class);
    PlanarImage pixels = mock(PlanarImage.class);
    when(pixels.type()).thenReturn(CvType.CV_16UC1);
    when(image.isImageAvailable()).thenReturn(true);
    when(image.getImage()).thenReturn(pixels);
    when(image.getMinValue(any(WlPresentation.class))).thenReturn(0.0);
    when(image.getMaxValue(any(WlPresentation.class))).thenReturn(max);
    when(image.getPresetList(any(WlPresentation.class)))
        .thenReturn(List.of(autoPreset(max, max / 2)));
    when(image.getLutShapeCollection(any(WlPresentation.class)))
        .thenReturn(List.of(LutShape.LINEAR));
    return image;
  }

  private static PresetWindowLevel autoPreset(double window, double level) {
    PresetWindowLevel preset =
        new PresetWindowLevel("Auto Level [Image]", window, level, LutShape.LINEAR);
    preset.setKeyCode(KeyEvent.VK_0);
    return preset;
  }
}
