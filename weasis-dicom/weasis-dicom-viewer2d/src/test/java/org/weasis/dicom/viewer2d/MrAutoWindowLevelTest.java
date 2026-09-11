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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.lut.PresetWindowLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.display.MrWindowLevelRange;
import org.weasis.opencv.op.lut.LutShape;

class MrAutoWindowLevelTest {
  @Test
  void samplesOnlyTwoNeighborsEachSideInDisplayedFilteredOrder() {
    View2d view = mock(View2d.class);
    DicomSeries series = mock(DicomSeries.class);
    Filter<DicomImageElement> filter = mock(Filter.class);
    when(view.getSeries()).thenReturn(series);
    when(view.getActionValue(ActionW.FILTERED_SERIES.cmd())).thenReturn(filter);
    List<DicomImageElement> stack =
        List.of(image(1), image(2), image(3), image(4), image(5), image(6), image(7));
    when(view.getImage()).thenReturn(stack.get(3));
    when(series.copyOfMedias(eq(filter), any())).thenReturn(stack);

    assertEquals(
        List.of(stack.get(3), stack.get(2), stack.get(4), stack.get(1), stack.get(5)),
        MrAutoWindowLevel.nearbyImages(view));
    for (DicomImageElement image : stack) {
      verify(image, never()).getMrAutoWindowLevelRange();
    }
    when(view.getImage()).thenReturn(stack.getFirst());
    assertEquals(stack.subList(0, 3), MrAutoWindowLevel.nearbyImages(view));
  }

  @Test
  void neighborsStabilizeNarrowCurrentRangeAndIgnoreOneExtremeNeighbor() {
    PresetWindowLevel auto =
        MrAutoWindowLevel.estimate(
            List.of(image(400), image(1000), image(1100), image(1200), image(60000)), () -> false);
    assertNotNull(auto);
    assertEquals(1100, auto.getWindow());
    assertEquals(550, auto.getLevel());
    assertSame(LutShape.LINEAR, auto.getLutShape());
    assertEquals(KeyEvent.VK_0, auto.getKeyCode());
  }

  @Test
  void neighborsNeverNarrowTheCurrentSliceRange() {
    PresetWindowLevel auto =
        MrAutoWindowLevel.estimate(List.of(image(1500), image(1000), image(800)), () -> false);
    assertEquals(1500, auto.getWindow());
  }

  @Test
  void oneSliceStillComputesTissueAuto() {
    assertEquals(900, MrAutoWindowLevel.estimate(List.of(image(900)), () -> false).getWindow());
  }

  @Test
  void emptyTissueSampleFallsBackToFreshCurrentImagePreset() {
    DicomImageElement image = image(Double.NaN);
    PresetWindowLevel fallback =
        new PresetWindowLevel("Auto Level [Image]", 750., 325., LutShape.LINEAR);
    fallback.setKeyCode(KeyEvent.VK_0);
    when(image.getPresetList(any(), eq(true))).thenReturn(List.of(fallback));
    assertSame(fallback, MrAutoWindowLevel.estimate(List.of(image), () -> false));
  }

  @Test
  void cancelledRequestStopsBeforeLoadingMoreNeighbors() {
    AtomicBoolean cancelled = new AtomicBoolean();
    DicomImageElement first = image(1000);
    DicomImageElement second = image(800);
    when(first.getMrAutoWindowLevelRange())
        .thenAnswer(
            invocation -> {
              cancelled.set(true);
              return new MrWindowLevelRange(0, 1000);
            });
    assertNull(MrAutoWindowLevel.estimate(List.of(first, second), cancelled::get));
    verify(second, never()).getMrAutoWindowLevelRange();
    verify(first, never()).getPresetList(any(), anyBoolean());
  }

  @ParameterizedTest
  @ValueSource(
      ints = {
        Tag.SeriesInstanceUID,
        Tag.Rows,
        Tag.Columns,
        Tag.ImageType,
        Tag.PhotometricInterpretation,
        Tag.EchoTime,
        Tag.EchoNumbers,
        Tag.RepetitionTime,
        Tag.TemporalPositionIdentifier,
        Tag.RescaleSlope,
        Tag.RescaleIntercept
      })
  void excludesDifferentSequenceGeometryEchoTimepointAndScaling(int tag) {
    DicomImageElement first = image(1000);
    DicomImageElement second = image(1000);
    when(first.getTagValue(TagD.get(tag))).thenReturn("first");
    when(second.getTagValue(TagD.get(tag))).thenReturn("second");
    assertFalse(MrAutoWindowLevel.compatible(first, second));
  }

  @Test
  void comparesOrientationWithRoundingTolerance() {
    DicomImageElement first = image(1000);
    DicomImageElement second = image(1000);
    when(first.getTagValue(TagD.get(Tag.ImageOrientationPatient)))
        .thenReturn(new double[] {1, 0, 0, 0, 1, 0});
    when(second.getTagValue(TagD.get(Tag.ImageOrientationPatient)))
        .thenReturn(new double[] {0.999999, 0, 0, 0, 1, 0});
    assertTrue(MrAutoWindowLevel.compatible(first, second));
    when(second.getTagValue(TagD.get(Tag.ImageOrientationPatient)))
        .thenReturn(new double[] {0, 1, 0, 0, 0, 1});
    assertFalse(MrAutoWindowLevel.compatible(first, second));
  }

  private static DicomImageElement image(double maximum) {
    DicomImageElement image = mock(DicomImageElement.class);
    when(image.getMrAutoWindowLevelRange()).thenReturn(new MrWindowLevelRange(0, maximum));
    return image;
  }
}
