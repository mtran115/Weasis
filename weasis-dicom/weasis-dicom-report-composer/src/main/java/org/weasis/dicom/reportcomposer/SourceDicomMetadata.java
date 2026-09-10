/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.reportcomposer;

import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;

/**
 * Reads metadata already held by a loaded image; never decodes pixels or inspects the filesystem.
 */
final class SourceDicomMetadata {
  private SourceDicomMetadata() {}

  static ImageReference reference(
      DicomImageElement image, MediaSeries<?> series, int displayIndex, int seriesSize) {
    return new ImageReference(
        text(Tag.SeriesInstanceUID, image, series),
        text(Tag.SOPInstanceUID, image, series),
        series == null ? "" : series.getSeriesNumber(),
        text(Tag.SeriesDescription, image, series),
        text(Tag.InstanceNumber, image, series),
        displayIndex,
        seriesSize,
        sourceFrameIndex(image),
        originalLocalUri(image),
        new ImageGeometry(
            integer(Tag.Rows, image, series),
            integer(Tag.Columns, image, series),
            vector(Tag.ImageOrientationPatient, image),
            vector(Tag.ImagePositionPatient, image),
            vector(Tag.PixelSpacing, image),
            text(Tag.FrameOfReferenceUID, image, series)));
  }

  static Integer sourceFrameIndex(DicomImageElement image) {
    if (image.getKey() instanceof Integer frame && frame >= 0) {
      return frame;
    }
    return image.getMediaReader() != null && image.getMediaReader().getMediaElementNumber() == 1
        ? 0
        : null;
  }

  static String originalLocalUri(DicomImageElement image) {
    try {
      if (image.getFileCache() != null) {
        var file = image.getFileCache().getOriginalFile();
        if (file.isPresent()) {
          return file.get().toAbsolutePath().normalize().toUri().toString();
        }
      }
      URI uri = image.getMediaURI();
      return uri != null && "file".equalsIgnoreCase(uri.getScheme())
          ? Path.of(uri).toAbsolutePath().normalize().toUri().toString()
          : "";
    } catch (RuntimeException unavailable) {
      // Missing or malformed source metadata is recorded as unavailable, never invented.
      return "";
    }
  }

  static String text(int tag, TagReadable... sources) {
    Object value = value(tag, sources);
    return value == null ? "" : value.toString();
  }

  private static int integer(int tag, TagReadable... sources) {
    Object value = value(tag, sources);
    return value instanceof Number number ? number.intValue() : 0;
  }

  private static List<Double> vector(int tag, TagReadable source) {
    Object value = value(tag, source);
    return value instanceof double[] array ? Arrays.stream(array).boxed().toList() : List.of();
  }

  private static Object value(int tag, TagReadable... sources) {
    for (TagReadable source : sources) {
      if (source != null) {
        Object value = TagD.getTagValue(source, tag);
        if (value != null) {
          return value;
        }
      }
    }
    return null;
  }
}
