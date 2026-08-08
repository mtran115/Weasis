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

import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewTransferHandler;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.InfoLayer;

final class DicomContextReader {
  private DicomContextReader() {}

  static Optional<Selection> selected() {
    ViewCanvas<DicomImageElement> view = EventManager.getInstance().getSelectedViewPane();
    if (view == null
        || view.getImage() == null
        || view.getSeries() == null
        || !(view instanceof DefaultView2d<?> defaultView)) {
      return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    DefaultView2d<DicomImageElement> canvas = (DefaultView2d<DicomImageElement>) defaultView;
    DicomImageElement image = view.getImage();
    MediaSeries<DicomImageElement> series = view.getSeries();
    MediaSeriesGroup study = InfoLayer.getParent(series, DicomModel.study);
    MediaSeriesGroup patient = InfoLayer.getParent(series, DicomModel.patient);

    CaseContext context =
        new CaseContext(
            value(Tag.StudyInstanceUID, study, image, series),
            value(Tag.PatientName, patient, image, series),
            value(Tag.PatientID, patient, image, series),
            value(Tag.PatientBirthDate, patient, image, series),
            value(Tag.AccessionNumber, study, image, series),
            value(Tag.StudyDate, study, image, series),
            value(Tag.StudyDescription, study, image, series));

    ImageReference reference =
        new ImageReference(
            value(Tag.SeriesInstanceUID, image, series),
            value(Tag.SOPInstanceUID, image, series),
            series.getSeriesNumber(),
            value(Tag.SeriesDescription, image, series),
            value(Tag.InstanceNumber, image, series),
            view.getFrameIndex() + 1,
            series.size(null));
    return Optional.of(new Selection(context, reference, canvas));
  }

  private static String value(int tag, TagReadable... sources) {
    Object value = firstValue(tag, sources);
    if (value instanceof TemporalAccessor temporal) {
      return temporal.toString();
    }
    return value == null ? "" : value.toString();
  }

  private static Object firstValue(int tag, TagReadable... sources) {
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

  record Selection(
      CaseContext context, ImageReference reference, DefaultView2d<DicomImageElement> canvas) {
    BufferedImage captureView() {
      RenderedImage rendered = ViewTransferHandler.createComponentImage(canvas, true);
      if (rendered instanceof BufferedImage bufferedImage) {
        return bufferedImage;
      }
      BufferedImage converted =
          new BufferedImage(rendered.getWidth(), rendered.getHeight(), BufferedImage.TYPE_INT_RGB);
      java.awt.Graphics2D graphics = converted.createGraphics();
      graphics.drawRenderedImage(rendered, new java.awt.geom.AffineTransform());
      graphics.dispose();
      return converted;
    }
  }
}
