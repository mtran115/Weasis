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

import java.awt.Component;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;
import javax.swing.SwingUtilities;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
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
    return from(view);
  }

  static Optional<DefaultView2d<DicomImageElement>> selectedCanvas(SeriesViewerEvent event) {
    if (!(event.getSeriesViewer() instanceof ImageViewerPlugin<?> viewer)) {
      return Optional.empty();
    }
    return dicomCanvas(viewer.getSelectedViewCanvas());
  }

  static Optional<DefaultView2d<DicomImageElement>> canvasFor(Component component) {
    Component current = component;
    while (current != null) {
      if (current instanceof ViewCanvas<?> view) {
        return dicomCanvas(view);
      }
      current = current.getParent();
    }
    return Optional.empty();
  }

  static Optional<DefaultView2d<DicomImageElement>> canvasAt(Point screenPoint) {
    ImageViewerPlugin<DicomImageElement> viewer =
        EventManager.getInstance().getSelectedView2dContainer();
    if (viewer == null || screenPoint == null) {
      return Optional.empty();
    }
    for (ViewCanvas<DicomImageElement> view : viewer.getImagePanels()) {
      var component = view.getJComponent();
      if (!component.isShowing()) {
        continue;
      }
      Point localPoint = new Point(screenPoint);
      SwingUtilities.convertPointFromScreen(localPoint, component);
      if (component.contains(localPoint)) {
        return dicomCanvas(view);
      }
    }
    return Optional.empty();
  }

  static Optional<Selection> fromVisible(ViewCanvas<?> view) {
    if (view == null || !view.getJComponent().isShowing()) {
      return Optional.empty();
    }
    return from(view);
  }

  static Optional<Selection> from(ViewCanvas<?> view) {
    if (view == null
        || !(view.getImage() instanceof DicomImageElement image)
        || !(view.getSeries() instanceof MediaSeries<?> genericSeries)) {
      return Optional.empty();
    }

    Optional<DefaultView2d<DicomImageElement>> dicomCanvas = dicomCanvas(view);
    if (dicomCanvas.isEmpty()) {
      return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    MediaSeries<DicomImageElement> series = (MediaSeries<DicomImageElement>) genericSeries;
    DefaultView2d<DicomImageElement> canvas = dicomCanvas.get();
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
            canvas.getFrameIndex() + 1,
            series.size(null));
    return Optional.of(new Selection(context, reference, canvas));
  }

  private static Optional<DefaultView2d<DicomImageElement>> dicomCanvas(ViewCanvas<?> view) {
    if (!(view instanceof DefaultView2d<?> defaultView)) {
      return Optional.empty();
    }
    @SuppressWarnings("unchecked")
    DefaultView2d<DicomImageElement> canvas = (DefaultView2d<DicomImageElement>) defaultView;
    return Optional.of(canvas);
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
