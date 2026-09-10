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
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.time.Instant;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
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
import org.weasis.core.ui.model.layer.GraphicLayer;
import org.weasis.core.ui.model.layer.LayerType;
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

  static Optional<Selection> visibleStudy(String preferredStudyUid) {
    String preferred = ComposerText.clean(preferredStudyUid);
    Selection fallback = null;
    for (ViewportSelection viewport : visibleViewports()) {
      Selection selection = viewport.selection();
      if (fallback == null) {
        fallback = selection;
      }
      if (!preferred.isBlank() && preferred.equals(selection.context().studyInstanceUid())) {
        return Optional.of(selection);
      }
    }
    return Optional.ofNullable(fallback);
  }

  static List<ViewportCanvas> visibleCanvases() {
    ImageViewerPlugin<DicomImageElement> viewer =
        EventManager.getInstance().getSelectedView2dContainer();
    if (viewer == null) {
      return List.of();
    }

    List<ViewCanvas<DicomImageElement>> views = viewer.getImagePanels();
    List<ViewportCanvas> canvases = new ArrayList<>(views.size());
    for (int index = 0; index < views.size(); index++) {
      ViewCanvas<DicomImageElement> view = views.get(index);
      if (view.getJComponent().isShowing()) {
        Optional<CurrentImage> current = currentImage(view);
        if (current.isPresent()) {
          canvases.add(new ViewportCanvas(index + 1, current.get().canvas()));
        }
      }
    }
    return List.copyOf(canvases);
  }

  static List<ViewportSelection> visibleViewports() {
    List<ViewportCanvas> canvases = visibleCanvases();
    List<ViewportSelection> selections = new ArrayList<>(canvases.size());
    for (ViewportCanvas viewport : canvases) {
      Optional<Selection> selection = from(viewport.canvas());
      selection.ifPresent(
          value -> selections.add(new ViewportSelection(viewport.viewportNumber(), value)));
    }
    return List.copyOf(selections);
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
    Optional<CurrentImage> currentImage = currentImage(view);
    if (currentImage.isEmpty()) {
      return Optional.empty();
    }
    CurrentImage current = currentImage.get();
    DicomImageElement image = current.image();
    MediaSeries<DicomImageElement> series = current.series();
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

    return Optional.of(new Selection(context, imageReference(current), current.canvas()));
  }

  static Optional<ImageReference> currentReference(ViewCanvas<?> view) {
    return currentImage(view).map(DicomContextReader::imageReference);
  }

  private static Optional<CurrentImage> currentImage(ViewCanvas<?> view) {
    if (view == null
        || !(view.getImage() instanceof DicomImageElement image)
        || !(view.getSeries() instanceof MediaSeries<?> genericSeries)) {
      return Optional.empty();
    }
    Optional<DefaultView2d<DicomImageElement>> canvas = dicomCanvas(view);
    if (canvas.isEmpty()) {
      return Optional.empty();
    }
    @SuppressWarnings("unchecked")
    MediaSeries<DicomImageElement> series = (MediaSeries<DicomImageElement>) genericSeries;
    return Optional.of(new CurrentImage(canvas.get(), image, series));
  }

  private static ImageReference imageReference(CurrentImage current) {
    return SourceDicomMetadata.reference(
        current.image(),
        current.series(),
        current.canvas().getFrameIndex() + 1,
        current.series().size(null));
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

  static <T> T captureWithoutReferenceLines(
      Optional<GraphicLayer> referenceLines, Supplier<T> capture) {
    if (referenceLines.isEmpty()) {
      return capture.get();
    }

    GraphicLayer layer = referenceLines.get();
    Boolean previousVisibility = layer.getVisible();
    layer.setVisible(false);
    try {
      return capture.get();
    } finally {
      layer.setVisible(previousVisibility);
    }
  }

  record Selection(
      CaseContext context, ImageReference reference, DefaultView2d<DicomImageElement> canvas) {
    /** Freeze source identity and geometry before a modal annotation dialog can change the view. */
    CapturedView capture() {
      CurrentImage current =
          currentImage(canvas)
              .orElseThrow(
                  () -> new IllegalStateException("The selected viewport has no DICOM image."));
      ImageReference capturedReference = imageReference(current);
      CaptureGeometry geometry =
          CaptureGeometry.freeze(
              canvas.getWidth(),
              canvas.getHeight(),
              canvas.getInverseTransform(),
              canvas.getClipViewCoordinatesOffset(),
              current.image().getRescaleX(),
              current.image().getRescaleY());
      String capturedAt = Instant.now().toString();
      BufferedImage image = captureView();
      if (canvas.getImage() != current.image()) {
        throw new IllegalStateException(
            "The viewport changed during capture. Capture the image again.");
      }
      return new CapturedView(capturedReference, image, geometry, capturedAt);
    }

    BufferedImage captureView() {
      RenderedImage rendered =
          captureWithoutReferenceLines(
              canvas.getGraphicManager().findLayerByType(LayerType.CROSSLINES),
              () -> ViewTransferHandler.createComponentImage(canvas, true));
      if (rendered instanceof BufferedImage bufferedImage) {
        return bufferedImage;
      }
      BufferedImage converted =
          new BufferedImage(rendered.getWidth(), rendered.getHeight(), BufferedImage.TYPE_INT_RGB);
      java.awt.Graphics2D graphics = converted.createGraphics();
      graphics.drawRenderedImage(rendered, new AffineTransform());
      graphics.dispose();
      return converted;
    }
  }

  record CapturedView(
      ImageReference reference, BufferedImage image, CaptureGeometry geometry, String capturedAt) {}

  private record CurrentImage(
      DefaultView2d<DicomImageElement> canvas,
      DicomImageElement image,
      MediaSeries<DicomImageElement> series) {}

  record ViewportCanvas(int viewportNumber, DefaultView2d<DicomImageElement> canvas) {}

  record ViewportSelection(int viewportNumber, Selection selection) {}
}
