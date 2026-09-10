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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.dcm4che3.data.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.media.data.FileCache;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.dicom.codec.DcmMediaReader;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;

class SourceDicomMetadataTest {
  @Test
  void preservesActualFrameAndOriginalGeometryIndependentlyOfDisplayOrder(@TempDir Path directory) {
    DicomImageElement image = mock(DicomImageElement.class);
    when(image.getKey()).thenReturn(17);
    when(image.getTagValue(TagD.get(Tag.Rows))).thenReturn(256);
    when(image.getTagValue(TagD.get(Tag.Columns))).thenReturn(512);
    when(image.getTagValue(TagD.get(Tag.PixelSpacing))).thenReturn(new double[] {2, 0.5});
    double[] orientation = {1, 0, 0, 0, 1, 0};
    when(image.getTagValue(TagD.get(Tag.ImageOrientationPatient))).thenReturn(orientation);
    when(image.getTagValue(TagD.get(Tag.ImagePositionPatient)))
        .thenReturn(new double[] {10, 20, 30});
    when(image.getTagValue(TagD.get(Tag.FrameOfReferenceUID))).thenReturn("frame-of-reference");
    FileCache cache = mock(FileCache.class);
    when(image.getFileCache()).thenReturn(cache);
    Path original = directory.resolve("original multi frame.dcm");
    when(cache.getOriginalFile()).thenReturn(Optional.of(original));
    when(image.getFilePath()).thenReturn(directory.resolve("transformed-image.tmp"));

    ImageReference reference = SourceDicomMetadata.reference(image, series(), 3, 40);
    orientation[0] = -1;

    assertEquals(3, reference.displayIndex());
    assertEquals(17, reference.sourceFrameIndex());
    assertEquals(18, reference.sourceFrameNumber());
    assertEquals(original.toUri().toString(), reference.sourceUri());
    assertEquals(256, reference.geometry().rows());
    assertEquals(512, reference.geometry().columns());
    assertEquals(List.of(11.0, 26.0, 30.0), reference.geometry().patientPosition(2, 3));
    assertEquals("frame-of-reference", reference.geometry().frameOfReferenceUid());
    verify(image, never()).getImage();
    verify(image, never()).getFilePath();
  }

  @Test
  void missingSourcePathOrFrameIsNotReplacedWithDisplayIndex() {
    DicomImageElement image = mock(DicomImageElement.class);
    when(image.getMediaURI()).thenReturn(URI.create("https://example.invalid/image"));

    ImageReference reference = SourceDicomMetadata.reference(image, series(), 12, 20);

    assertNull(reference.sourceFrameIndex());
    assertNull(reference.sourceFrameNumber());
    assertTrue(reference.sourceUri().isEmpty());
    assertEquals(ImageGeometry.empty(), reference.geometry());
  }

  @Test
  void knownSingleFrameImageHasDicomFrameOne() {
    DicomImageElement image = mock(DicomImageElement.class);
    DcmMediaReader reader = mock(DcmMediaReader.class);
    when(image.getMediaReader()).thenReturn(reader);
    when(reader.getMediaElementNumber()).thenReturn(1);

    assertEquals(1, SourceDicomMetadata.reference(image, series(), 30, 60).sourceFrameNumber());
  }

  @Test
  void legacyReferencesRemainValidWithoutInventedSourceMetadata() {
    ImageReference legacy = new ImageReference("series", "sop", "2", "T2", "4", 4, 20);

    assertEquals("Series 2 (T2), image 4", legacy.humanReference());
    assertNull(legacy.sourceFrameIndex());
    assertTrue(legacy.sourceUri().isEmpty());
    assertEquals(ImageGeometry.empty(), legacy.geometry());
  }

  @SuppressWarnings("unchecked")
  private static MediaSeries<DicomImageElement> series() {
    MediaSeries<DicomImageElement> series = mock(MediaSeries.class);
    when(series.getSeriesNumber()).thenReturn("2");
    return series;
  }
}
