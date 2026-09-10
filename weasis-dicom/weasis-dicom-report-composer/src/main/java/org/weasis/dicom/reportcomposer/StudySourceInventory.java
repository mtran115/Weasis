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

import java.util.ArrayList;
import java.util.List;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.explorer.DicomModel;

/** Metadata snapshot of loaded study images; source file copying belongs on a background thread. */
public final class StudySourceInventory {
  private StudySourceInventory() {}

  /** Includes every loaded series in the study, even series not shown in a viewport. */
  public static List<SourceImageFile> collect(DicomModel model, String studyInstanceUid) {
    if (model == null || !ComposerText.hasText(studyInstanceUid)) {
      return List.of();
    }
    MediaSeriesGroup study = model.getStudyNode(studyInstanceUid);
    return study == null ? List.of() : collectStudy(model, study, studyInstanceUid);
  }

  /** Falls back to the anchor's loaded images when no study model is available. */
  public static List<SourceImageFile> collect(MediaSeries<?> anchorSeries) {
    if (anchorSeries == null) {
      return List.of();
    }
    if (anchorSeries.getTagValue(TagW.ExplorerModel) instanceof DicomModel model) {
      MediaSeriesGroup study = model.getParent(anchorSeries, DicomModel.study);
      if (study != null) {
        return collectStudy(model, study, SourceDicomMetadata.text(Tag.StudyInstanceUID, study));
      }
    }
    List<SourceImageFile> images = new ArrayList<>();
    collectSeries(
        anchorSeries, SourceDicomMetadata.text(Tag.StudyInstanceUID, anchorSeries), images);
    return List.copyOf(images);
  }

  public static boolean hasStudyModel(MediaSeries<?> anchorSeries) {
    return anchorSeries != null
        && anchorSeries.getTagValue(TagW.ExplorerModel) instanceof DicomModel model
        && model.getParent(anchorSeries, DicomModel.study) != null;
  }

  private static List<SourceImageFile> collectStudy(
      DicomModel model, MediaSeriesGroup study, String studyInstanceUid) {
    List<SourceImageFile> images = new ArrayList<>();
    for (MediaSeriesGroup child : List.copyOf(model.getChildren(study))) {
      if (child instanceof MediaSeries<?> series) {
        collectSeries(series, studyInstanceUid, images);
      }
    }
    return List.copyOf(images);
  }

  private static void collectSeries(
      MediaSeries<?> series, String studyInstanceUid, List<SourceImageFile> output) {
    List<?> media = series.copyOfMedias(null, null);
    for (int index = 0; index < media.size(); index++) {
      if (media.get(index) instanceof DicomImageElement image) {
        String uid = SourceDicomMetadata.text(Tag.StudyInstanceUID, image);
        output.add(
            new SourceImageFile(
                ComposerText.hasText(uid) ? uid : studyInstanceUid,
                SourceDicomMetadata.reference(image, series, index + 1, media.size())));
      }
    }
  }

  public record SourceImageFile(String studyInstanceUid, ImageReference reference) {
    public SourceImageFile {
      studyInstanceUid = ComposerText.clean(studyInstanceUid);
      java.util.Objects.requireNonNull(reference);
    }
  }
}
