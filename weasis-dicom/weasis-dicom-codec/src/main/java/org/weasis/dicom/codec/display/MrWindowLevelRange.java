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

import java.util.Arrays;
import org.dcm4che3.img.DicomImageAdapter;
import org.opencv.core.CvType;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.lut.DefaultWlPresentation;

/** A sampled MR display range that preserves the background and excludes isolated bright pixels. */
public record MrWindowLevelRange(double min, double max) {
  private static final int MAX_ROWS = 64;
  private static final int MAX_COLUMNS = 128;
  private static final int MIN_FOREGROUND_SAMPLES = 64;

  /**
   * Estimates a range from decoded monochrome pixels, or returns {@code null} when the sample is
   * insufficient. The caller must keep the image alive during sampling and decide whether the
   * image's modality and presentation permit automatic windowing.
   */
  public static MrWindowLevelRange estimate(PlanarImage image, DicomImageAdapter adapter) {
    try {
      return sample(image, adapter);
    } catch (RuntimeException e) {
      // Automatic presentation is optional; an unreadable sample must not prevent image decoding.
      return null;
    }
  }

  private static MrWindowLevelRange sample(PlanarImage image, DicomImageAdapter adapter) {
    if (image == null || adapter == null || image.channels() != 1) {
      return null;
    }
    int width = image.width();
    int height = image.height();
    if (width <= 0 || height <= 0) {
      return null;
    }

    int depth = image.depth();
    Object rowBuffer =
        switch (depth) {
          case CvType.CV_8U, CvType.CV_8S -> new byte[width];
          case CvType.CV_16U, CvType.CV_16S -> new short[width];
          case CvType.CV_32S -> new int[width];
          case CvType.CV_32F -> new float[width];
          case CvType.CV_64F -> new double[width];
          default -> null;
        };
    if (rowBuffer == null) {
      return null;
    }
    int bytesPerValue = CvType.ELEM_SIZE(depth);
    var descriptor = adapter.getImageDescriptor();
    var padding = descriptor.getPixelPaddingValue();
    int paddingValue = padding.orElse(0);
    int paddingLimit = descriptor.getPixelPaddingRangeLimit().orElse(paddingValue);
    int paddingMin = Math.min(paddingValue, paddingLimit);
    int paddingMax = Math.max(paddingValue, paddingLimit);
    var presentation = new DefaultWlPresentation(null, true);

    int rows = Math.min(height, MAX_ROWS);
    int columns = Math.min(width, MAX_COLUMNS);
    double[] samples = new double[rows * columns];
    int count = 0;
    for (int r = 0; r < rows; r++) {
      int readBytes = readRow(image, sampleIndex(r, height, rows), rowBuffer);
      int available = Math.min(width, readBytes / bytesPerValue);
      for (int c = 0; c < columns; c++) {
        int column = sampleIndex(c, width, columns);
        if (column >= available) {
          break;
        }
        double raw = valueAt(rowBuffer, depth, column);
        if (!Double.isFinite(raw)
            || (padding.isPresent() && raw >= paddingMin && raw <= paddingMax)) {
          continue;
        }
        Number transformed = adapter.pixelToRealValue(raw, presentation);
        if (transformed != null && Double.isFinite(transformed.doubleValue())) {
          samples[count++] = transformed.doubleValue();
        }
      }
    }
    if (count <= MIN_FOREGROUND_SAMPLES) {
      return null;
    }

    Arrays.sort(samples, 0, count);
    double minimum = samples[0];
    int foregroundStart = 1;
    while (foregroundStart < count && samples[foregroundStart] <= minimum) {
      foregroundStart++;
    }
    if (count - foregroundStart < MIN_FOREGROUND_SAMPLES) {
      return null;
    }
    double foregroundMin = percentile(samples, foregroundStart, count, 0.005);
    double foregroundMax = percentile(samples, foregroundStart, count, 0.995);
    if (foregroundMax <= foregroundMin) {
      return null;
    }
    // Keep the true sampled minimum so trimming the foreground does not lift the background.
    return new MrWindowLevelRange(minimum, foregroundMax);
  }

  private static int sampleIndex(int sample, int length, int samples) {
    return samples == 1 ? 0 : (int) ((long) sample * (length - 1) / (samples - 1));
  }

  private static int readRow(PlanarImage image, int row, Object buffer) {
    return switch (buffer) {
      case byte[] values -> image.get(row, 0, values);
      case short[] values -> image.get(row, 0, values);
      case int[] values -> image.get(row, 0, values);
      case float[] values -> image.get(row, 0, values);
      case double[] values -> image.get(row, 0, values);
      default -> 0;
    };
  }

  private static double valueAt(Object buffer, int depth, int column) {
    return switch (buffer) {
      case byte[] values -> depth == CvType.CV_8U ? values[column] & 0xff : values[column];
      case short[] values -> depth == CvType.CV_16U ? values[column] & 0xffff : values[column];
      case int[] values -> values[column];
      case float[] values -> values[column];
      case double[] values -> values[column];
      default -> Double.NaN;
    };
  }

  private static double percentile(double[] values, int start, int end, double fraction) {
    double position = start + (end - start - 1) * fraction;
    int lower = (int) position;
    int upper = Math.min(lower + 1, end - 1);
    double weight = position - lower;
    return values[lower] * (1.0 - weight) + values[upper] * weight;
  }
}
