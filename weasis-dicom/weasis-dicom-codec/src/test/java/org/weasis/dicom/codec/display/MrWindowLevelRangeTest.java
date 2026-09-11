/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Array;
import java.util.Optional;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.Stream;
import org.dcm4che3.img.DicomImageAdapter;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.stubbing.Answer;
import org.opencv.core.CvType;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.lut.WlPresentation;

class MrWindowLevelRangeTest {

  @Test
  void explicitAutoLeavesHeadroomInsteadOfMakingMostTissueNearlyWhite() {
    PlanarImage image =
        image(
            CvType.CV_16U, 64, 128, (row, column) -> column < 32 || column > 96 ? 0 : 900 + column);
    MrWindowLevelRange range =
        MrWindowLevelRange.estimateRanges(image, adapter(v -> v)).autoRange();

    assertNotNull(range);
    assertEquals(0, range.min());
    double medianBrightness = (964 - range.min()) / (range.max() - range.min());
    assertEquals(0.5, medianBrightness, 0.01);
    assertTrue(range.max() > 1800);
  }

  @Test
  void noisyAirDoesNotDominateSmallAnatomyAutoLevel() {
    PlanarImage image =
        image(
            CvType.CV_16U,
            64,
            128,
            (row, column) ->
                row >= 28 && row < 36 && column >= 56 && column < 72
                    ? 900 + column
                    : 1 + (row + column) % 10);
    MrWindowLevelRange range =
        MrWindowLevelRange.estimateRanges(image, adapter(v -> v)).autoRange();

    assertNotNull(range);
    assertTrue(range.max() > 1800, "Air noise must not pull the tissue median toward black");
    assertTrue(range.max() < 2000);
    assertTrue((10 - range.min()) / (range.max() - range.min()) < 0.01);
  }

  @Test
  void explicitAutoExcludesAnIsolatedExtremeButKeepsSmallBrightStructures() {
    PlanarImage image =
        image(
            CvType.CV_16U,
            64,
            128,
            (row, column) -> {
              if (row == 32 && column == 64) return 60000;
              if (row == 33 && column >= 60 && column < 64) return 1500;
              return column < 16 || column > 112 ? 0 : 100 + column;
            });
    MrWindowLevelRange range =
        MrWindowLevelRange.estimateRanges(image, adapter(v -> v)).autoRange();
    assertNotNull(range);
    assertTrue(range.max() < 2000, "Isolated huge extrema must not make the image dark");
    assertTrue(range.max() > 1500, "A small cluster of bright pixels must be preserved");

    PlanarImage focal =
        image(
            CvType.CV_16U,
            64,
            128,
            (row, column) ->
                row == 32 && column == 64 ? 600 : column < 16 || column > 112 ? 0 : 100 + column);
    MrWindowLevelRange focalRange =
        MrWindowLevelRange.estimateRanges(focal, adapter(v -> v)).autoRange();
    assertNotNull(focalRange);
    assertTrue(focalRange.max() > 600, "A small plausible bright structure must retain headroom");
  }

  @Test
  void anatomyAtImageEdgesIsNotAssumedToBeBackground() {
    PlanarImage image = image(CvType.CV_16U, 64, 128, (row, column) -> row < 16 ? 0 : 500 + column);
    MrWindowLevelRange range =
        MrWindowLevelRange.estimateRanges(image, adapter(v -> v)).autoRange();
    assertNotNull(range);
    assertTrue(range.max() > 1000);
  }

  @Test
  void explicitAutoExcludesPaddingBeforeRescaleAndRejectsFlatImages() {
    DicomImageAdapter adapter = adapter(v -> v * 2 - 1000);
    when(adapter.getImageDescriptor().getPixelPaddingValue()).thenReturn(Optional.of(-100));
    MrWindowLevelRange range =
        MrWindowLevelRange.estimateRanges(
                image(
                    CvType.CV_16S,
                    64,
                    128,
                    (row, column) -> column < 16 ? -100 : column < 32 ? 0 : 500 + column),
                adapter)
            .autoRange();
    assertNotNull(range);
    assertEquals(-1000, range.min());
    assertTrue(range.max() > 1000);
    assertNull(
        MrWindowLevelRange.estimateRanges(
                image(CvType.CV_16U, 64, 128, (row, column) -> 500), adapter(v -> v))
            .autoRange());
  }

  @Test
  void explicitAutoRejectsAnExtremeEvenWithOnly64TissueSamples() {
    PlanarImage image =
        image(
            CvType.CV_16U,
            64,
            128,
            (row, column) ->
                row == 32 && column >= 32 && column < 96 ? column == 95 ? 60000 : 500 + column : 0);
    MrWindowLevelRange range =
        MrWindowLevelRange.estimateRanges(image, adapter(v -> v)).autoRange();
    assertNotNull(range);
    assertTrue(range.max() < 1500);
  }

  @Test
  void trimsRareBrightOutlierAndPreservesBackground() {
    PlanarImage image =
        image(
            CvType.CV_16U,
            16,
            128,
            (row, column) -> {
              if (row == 15 && column == 127) {
                return 60000;
              }
              return switch (column % 3) {
                case 0 -> 0;
                case 1 -> 100;
                default -> 200;
              };
            });

    assertRange(0, 200, MrWindowLevelRange.estimate(image, adapter(value -> value)));
  }

  @ParameterizedTest(name = "depth {0}: {1} to {3}")
  @MethodSource("pixelTypes")
  void interpretsEachSupportedPixelType(int depth, double minimum, double middle, double maximum) {
    PlanarImage image =
        image(
            depth,
            16,
            128,
            (row, column) -> {
              return switch (column % 3) {
                case 0 -> minimum;
                case 1 -> middle;
                default -> maximum;
              };
            });

    assertRange(minimum, maximum, MrWindowLevelRange.estimate(image, adapter(value -> value)));
  }

  private static Stream<Arguments> pixelTypes() {
    return Stream.of(
        Arguments.of(CvType.CV_8U, 0.0, 128.0, 255.0),
        Arguments.of(CvType.CV_8S, -128.0, -64.0, 127.0),
        Arguments.of(CvType.CV_16U, 0.0, 32768.0, 65535.0),
        Arguments.of(CvType.CV_16S, -32768.0, -1024.0, 32767.0),
        Arguments.of(
            CvType.CV_32S, (double) Integer.MIN_VALUE, -1234567.0, (double) Integer.MAX_VALUE),
        Arguments.of(CvType.CV_32F, -3.5, 0.25, 19.75),
        Arguments.of(CvType.CV_64F, -1.5e10, 0.125, 1.5e10 + 0.125));
  }

  @ParameterizedTest
  @CsvSource({"-200, -100", "-100, -200"})
  void excludesInclusivePaddingRangeBeforeRescale(int paddingValue, int paddingLimit) {
    double[] pixels = {-200, -150, -100, 0, 100, 200};
    PlanarImage image =
        image(CvType.CV_16S, 16, 128, (row, column) -> pixels[column % pixels.length]);
    DicomImageAdapter adapter = adapter(value -> 2 * value + 1000);
    ImageDescriptor descriptor = adapter.getImageDescriptor();
    when(descriptor.getPixelPaddingValue()).thenReturn(Optional.of(paddingValue));
    when(descriptor.getPixelPaddingRangeLimit()).thenReturn(Optional.of(paddingLimit));

    assertRange(1000, 1400, MrWindowLevelRange.estimate(image, adapter));
    verify(adapter, never())
        .pixelToRealValue(
            argThat(value -> value.doubleValue() >= -200 && value.doubleValue() <= -100),
            any(WlPresentation.class));
  }

  @Test
  void excludesSinglePaddingValueWhenRangeLimitIsAbsent() {
    double[] pixels = {-200, 0, 100, 200};
    PlanarImage image =
        image(CvType.CV_16S, 16, 128, (row, column) -> pixels[column % pixels.length]);
    DicomImageAdapter adapter = adapter(value -> value);
    when(adapter.getImageDescriptor().getPixelPaddingValue()).thenReturn(Optional.of(-200));

    assertRange(0, 200, MrWindowLevelRange.estimate(image, adapter));
  }

  @ParameterizedTest
  @CsvSource({"2, -100, -100, 300", "-2, 100, -300, 100"})
  void computesBoundsInRescaledValueOrder(
      double slope, double intercept, double minimum, double maximum) {
    PlanarImage image = image(CvType.CV_16U, 16, 128, (row, column) -> 100 * (column % 3));

    assertRange(
        minimum,
        maximum,
        MrWindowLevelRange.estimate(image, adapter(value -> slope * value + intercept)));
  }

  @Test
  void ignoresNonfiniteRawAndRescaledValues() {
    double[] pixels = {
      0, 100, 200, 300, 400, 500, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY
    };
    PlanarImage image =
        image(CvType.CV_64F, 16, 128, (row, column) -> pixels[column % pixels.length]);
    DicomImageAdapter adapter =
        adapter(
            value -> {
              if (value == 300) {
                return Double.NaN;
              }
              if (value == 400) {
                return Double.POSITIVE_INFINITY;
              }
              return value == 500 ? Double.NEGATIVE_INFINITY : value;
            });

    assertRange(0, 200, MrWindowLevelRange.estimate(image, adapter));
    verify(adapter, never())
        .pixelToRealValue(
            argThat(value -> !Double.isFinite(value.doubleValue())), any(WlPresentation.class));
  }

  @Test
  void requiresAtLeast64ForegroundSamples() {
    DicomImageAdapter adapter = adapter(value -> value);

    assertNull(
        MrWindowLevelRange.estimate(image(CvType.CV_16U, 1, 64, (row, column) -> column), adapter));
    assertNotNull(
        MrWindowLevelRange.estimate(image(CvType.CV_16U, 1, 65, (row, column) -> column), adapter));
  }

  @Test
  void rejectsFlatImage() {
    assertNull(
        MrWindowLevelRange.estimate(
            image(CvType.CV_16U, 16, 128, (row, column) -> 100), adapter(value -> value)));
  }

  @Test
  void rejectsFlatForegroundEvenWhenOneBrightOutlierIncreasesFullRange() {
    PlanarImage image =
        image(
            CvType.CV_16U,
            16,
            128,
            (row, column) -> {
              if (column == 0) {
                return 0;
              }
              return row == 15 && column == 127 ? 60000 : 100;
            });

    assertNull(MrWindowLevelRange.estimate(image, adapter(value -> value)));
  }

  @Test
  void boundsSamplingWorkForLargeImages() {
    PlanarImage image = image(CvType.CV_32S, 100000, 20000, (row, column) -> row + column);
    DicomImageAdapter adapter = adapter(value -> value);

    assertNotNull(MrWindowLevelRange.estimate(image, adapter));
    verify(image, atMost(64)).get(anyInt(), eq(0), any(int[].class));
    verify(image, never()).get(anyInt(), anyInt());
    verify(adapter, atMost(8192)).pixelToRealValue(any(Number.class), any(WlPresentation.class));
    long sampledRows =
        mockingDetails(image).getInvocations().stream()
            .filter(invocation -> invocation.getMethod().getName().equals("get"))
            .map(invocation -> invocation.getArgument(0))
            .distinct()
            .count();
    assertTrue(sampledRows > 1, "Sampling must cover multiple rows");
  }

  @Test
  void rejectsEmptyAndMultichannelImages() {
    DicomImageAdapter adapter = adapter(value -> value);
    PlanarImage color = image(CvType.CV_8U, 16, 128, (row, column) -> column);
    when(color.channels()).thenReturn(3);

    assertNull(MrWindowLevelRange.estimate(color, adapter));
    assertNull(
        MrWindowLevelRange.estimate(
            image(CvType.CV_16U, 0, 128, (row, column) -> column), adapter));
    assertNull(MrWindowLevelRange.estimate(null, adapter));
    assertNull(MrWindowLevelRange.estimate(color, null));
    verify(adapter, never()).pixelToRealValue(any(Number.class), any(WlPresentation.class));
  }

  @Test
  void fallsBackWhenSamplingCannotReadPixels() {
    PlanarImage image = image(CvType.CV_16U, 16, 128, (row, column) -> column);
    when(image.get(anyInt(), eq(0), any(short[].class)))
        .thenThrow(new IllegalStateException("Pixel buffer unavailable"));

    assertNull(MrWindowLevelRange.estimate(image, adapter(value -> value)));
  }

  private static void assertRange(double minimum, double maximum, MrWindowLevelRange range) {
    assertNotNull(range);
    assertEquals(minimum, range.min(), Math.max(1e-9, Math.ulp(minimum) * 2));
    assertEquals(maximum, range.max(), Math.max(1e-9, Math.ulp(maximum) * 2));
  }

  private static DicomImageAdapter adapter(DoubleUnaryOperator transform) {
    DicomImageAdapter adapter = mock(DicomImageAdapter.class);
    ImageDescriptor descriptor = mock(ImageDescriptor.class);
    when(adapter.getImageDescriptor()).thenReturn(descriptor);
    when(descriptor.getPixelPaddingValue()).thenReturn(Optional.empty());
    when(descriptor.getPixelPaddingRangeLimit()).thenReturn(Optional.empty());
    when(adapter.pixelToRealValue(any(Number.class), any(WlPresentation.class)))
        .thenAnswer(
            invocation -> {
              WlPresentation presentation = invocation.getArgument(1);
              assertTrue(presentation.isPixelPadding());
              assertNull(presentation.getPresentationState());
              return transform.applyAsDouble(((Number) invocation.getArgument(0)).doubleValue());
            });
    return adapter;
  }

  private static PlanarImage image(int depth, int rows, int columns, PixelSource pixels) {
    PlanarImage image = mock(PlanarImage.class);
    when(image.channels()).thenReturn(1);
    when(image.depth()).thenReturn(depth);
    when(image.type()).thenReturn(depth);
    when(image.height()).thenReturn(rows);
    when(image.width()).thenReturn(columns);
    Answer<Integer> readRow =
        invocation -> {
          int row = invocation.getArgument(0);
          Object buffer = invocation.getArgument(2);
          assertEquals(columns, Array.getLength(buffer), "Read one full row at a time");
          assertTrue(row >= 0 && row < rows);
          for (int column = 0; column < columns; column++) {
            double value = pixels.value(row, column);
            switch (buffer) {
              case byte[] values -> values[column] = (byte) value;
              case short[] values -> values[column] = (short) value;
              case int[] values -> values[column] = (int) value;
              case float[] values -> values[column] = (float) value;
              case double[] values -> values[column] = value;
              default -> throw new AssertionError("Unexpected pixel buffer");
            }
          }
          // OpenCV reports bytes read, including for the multi-byte array overloads.
          return columns * CvType.ELEM_SIZE(depth);
        };
    switch (depth) {
      case CvType.CV_8U, CvType.CV_8S ->
          when(image.get(anyInt(), eq(0), any(byte[].class))).thenAnswer(readRow);
      case CvType.CV_16U, CvType.CV_16S ->
          when(image.get(anyInt(), eq(0), any(short[].class))).thenAnswer(readRow);
      case CvType.CV_32S -> when(image.get(anyInt(), eq(0), any(int[].class))).thenAnswer(readRow);
      case CvType.CV_32F ->
          when(image.get(anyInt(), eq(0), any(float[].class))).thenAnswer(readRow);
      case CvType.CV_64F ->
          when(image.get(anyInt(), eq(0), any(double[].class))).thenAnswer(readRow);
      default -> throw new IllegalArgumentException("Unsupported test pixel depth " + depth);
    }
    return image;
  }

  @FunctionalInterface
  private interface PixelSource {
    double value(int row, int column);
  }
}
