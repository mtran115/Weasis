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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.dcm4che3.data.Tag;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomModel;

class StudySourceInventoryTest {
  @Test
  void includesHiddenStudySeriesAndEachEnhancedFrameWithoutDecodingPixels() {
    DicomImageElement firstFrame =
        image("series-1", "enhanced-sop", 0, "file:/loaded/enhanced.dcm");
    DicomImageElement nextFrame = image("series-1", "enhanced-sop", 7, "file:/loaded/enhanced.dcm");
    DicomImageElement hiddenImage = image("series-2", "hidden-sop", 0, null);
    List<DicomImageElement> firstMedia = new ArrayList<>(List.of(firstFrame, nextFrame));
    MediaSeries<DicomImageElement> shown = series(firstMedia);
    MediaSeries<DicomImageElement> hidden = series(List.of(hiddenImage));
    DicomModel model = mock(DicomModel.class);
    MediaSeriesGroup study = mock(MediaSeriesGroup.class);
    when(study.getTagValue(TagD.get(Tag.StudyInstanceUID))).thenReturn("study");
    when(model.getStudyNode("study")).thenReturn(study);
    when(model.getChildren(study)).thenReturn(List.of(shown, hidden));
    when(model.getParent(shown, DicomModel.study)).thenReturn(study);
    when(shown.getTagValue(TagW.ExplorerModel)).thenReturn(model);

    var inventory = StudySourceInventory.collect(shown);
    assertEquals(inventory, StudySourceInventory.collect(model, "study"));
    firstMedia.clear();

    assertTrue(StudySourceInventory.hasStudyModel(shown));
    assertEquals(3, inventory.size());
    assertEquals(
        List.of(0, 7, 0),
        inventory.stream().map(file -> file.reference().sourceFrameIndex()).toList());
    assertEquals("hidden-sop", inventory.get(2).reference().sopInstanceUid());
    assertTrue(inventory.get(2).reference().sourceUri().isEmpty());
    assertEquals(
        inventory.get(0).reference().sourceUri(), inventory.get(1).reference().sourceUri());
    for (DicomImageElement image : List.of(firstFrame, nextFrame, hiddenImage)) {
      verify(image, never()).getImage();
      verify(image, never()).getFilePath();
    }
  }

  @Test
  void missingModelHasAnExplicitlyDetectableAnchorOnlyFallback() {
    MediaSeries<DicomImageElement> anchor = series(List.of(image("series", "sop", 0, null)));
    assertFalse(StudySourceInventory.hasStudyModel(anchor));
    assertEquals(1, StudySourceInventory.collect(anchor).size());
    assertTrue(StudySourceInventory.collect((MediaSeries<?>) null).isEmpty());
    assertTrue(StudySourceInventory.collect(mock(DicomModel.class), "unknown").isEmpty());
  }

  private static DicomImageElement image(String series, String sop, int frame, String uri) {
    DicomImageElement image = mock(DicomImageElement.class);
    when(image.getKey()).thenReturn(frame);
    when(image.getTagValue(TagD.get(Tag.SeriesInstanceUID))).thenReturn(series);
    when(image.getTagValue(TagD.get(Tag.SOPInstanceUID))).thenReturn(sop);
    if (uri != null) {
      when(image.getMediaURI()).thenReturn(URI.create(uri));
    }
    return image;
  }

  @SuppressWarnings("unchecked")
  private static MediaSeries<DicomImageElement> series(List<DicomImageElement> images) {
    MediaSeries<DicomImageElement> series = mock(MediaSeries.class);
    when(series.copyOfMedias(null, null)).thenReturn(images);
    return series;
  }
}
