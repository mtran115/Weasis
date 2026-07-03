/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.dcm4che3.data.Tag;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagReadable;

class DicomDisplayTextTest {

  @Test
  void examTitlePrefersStudyDescription() {
    MediaSeriesGroup study = mock(MediaSeriesGroup.class);
    tag(study, Tag.StudyDescription, "BILATERAL KNEE");

    MediaSeriesGroup series = mock(MediaSeriesGroup.class);
    tag(series, Tag.BodyPartExamined, "FOOT");
    tag(series, Tag.Laterality, "R");

    assertEquals("Bilateral Knee", DicomDisplayText.getExamTitle(study, series));
  }

  @Test
  void examTitleFallsBackToLateralityAndBodyPart() {
    MediaSeriesGroup series = mock(MediaSeriesGroup.class);
    tag(series, Tag.BodyPartExamined, "KNEE");
    tag(series, Tag.Laterality, "L");

    assertEquals("Left Knee", DicomDisplayText.getExamTitle(null, series));
  }

  @Test
  void viewTitlePrefersViewPosition() {
    DicomImageElement image = mock(DicomImageElement.class);
    tag(image, Tag.ViewPosition, "LATERAL");
    tag(image, Tag.ImageLaterality, "R");

    assertEquals("Lateral", DicomDisplayText.getViewTitle(image, null));
  }

  @Test
  void seriesTitleFallsBackToProtocolName() {
    MediaSeriesGroup series = mock(MediaSeriesGroup.class);
    tag(series, Tag.ProtocolName, "SAG T2");

    assertEquals("SAG T2", DicomDisplayText.getSeriesTitle(series));
  }

  private static void tag(TagReadable taggable, int tagId, String value) {
    when(taggable.getTagValue(TagD.get(tagId))).thenReturn(value);
  }
}
