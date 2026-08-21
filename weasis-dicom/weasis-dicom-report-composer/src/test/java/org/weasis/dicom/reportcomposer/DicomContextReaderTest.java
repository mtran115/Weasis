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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.layer.imp.DefaultLayer;
import org.weasis.dicom.codec.DicomImageElement;

class DicomContextReaderTest {
  @Test
  void readsTheSelectedCanvasFromTheViewerEvent() {
    @SuppressWarnings("unchecked")
    ImageViewerPlugin<DicomImageElement> viewer = mock(ImageViewerPlugin.class);
    @SuppressWarnings("unchecked")
    DefaultView2d<DicomImageElement> canvas = mock(DefaultView2d.class);
    when(viewer.getSelectedViewCanvas()).thenReturn(canvas);

    SeriesViewerEvent event = new SeriesViewerEvent(viewer, null, null, EVENT.SELECT_VIEW);

    assertSame(canvas, DicomContextReader.selectedCanvas(event).orElseThrow());
  }

  @Test
  void ignoresEventsFromNonImageViewers() {
    SeriesViewer<?> viewer = mock(SeriesViewer.class);
    SeriesViewerEvent event = new SeriesViewerEvent(viewer, null, null, EVENT.SELECT_VIEW);

    assertTrue(DicomContextReader.selectedCanvas(event).isEmpty());
  }

  @Test
  void ignoresFocusSelectionWhileThePointerIsInsideTheComposer() {
    assertFalse(ReportComposerTool.shouldRememberViewerSelection(EVENT.SELECT_VIEW, true, false));
    assertTrue(ReportComposerTool.shouldRememberViewerSelection(EVENT.SELECT_VIEW, false, false));
    assertTrue(ReportComposerTool.shouldRememberViewerSelection(EVENT.LAYOUT, true, false));
  }

  @Test
  void ignoresSelectionCausedOnlyByCrossingAViewer() {
    assertFalse(ReportComposerTool.shouldRememberViewerSelection(EVENT.SELECT_VIEW, false, true));
    assertTrue(ReportComposerTool.shouldRememberViewerSelection(EVENT.LAYOUT, false, true));
  }

  @Test
  void crossingAnotherCanvasDoesNotCountAsAnInteraction() {
    assertFalse(ReportComposerTool.isDeliberateCanvasMouseEvent(MouseEvent.MOUSE_ENTERED));
    assertFalse(ReportComposerTool.isDeliberateCanvasMouseEvent(MouseEvent.MOUSE_MOVED));
    assertTrue(ReportComposerTool.isDeliberateCanvasMouseEvent(MouseEvent.MOUSE_PRESSED));
    assertTrue(ReportComposerTool.isDeliberateCanvasMouseEvent(MouseEvent.MOUSE_RELEASED));
    assertTrue(ReportComposerTool.isDeliberateCanvasMouseEvent(MouseEvent.MOUSE_WHEEL));
  }

  @Test
  void explicitCaptureViewportWinsOverLaterViewerActivity() {
    DefaultView2d<DicomImageElement> left = canvas();
    DefaultView2d<DicomImageElement> right = canvas();

    assertSame(
        left,
        ReportComposerTool.preferredCaptureCanvas(List.of(left, right), left, right, right, right));
  }

  @Test
  void captureViewportFallsBackToTheLastInteractedVisibleCanvas() {
    DefaultView2d<DicomImageElement> removed = canvas();
    DefaultView2d<DicomImageElement> left = canvas();
    DefaultView2d<DicomImageElement> right = canvas();

    assertSame(
        right,
        ReportComposerTool.preferredCaptureCanvas(
            List.of(left, right), removed, right, left, left));
  }

  @Test
  void referenceLinesAreHiddenOnlyWhileRenderingTheCapture() {
    DefaultLayer referenceLines = new DefaultLayer(LayerType.CROSSLINES);

    DicomContextReader.captureWithoutReferenceLines(
        Optional.of(referenceLines),
        () -> {
          assertFalse(referenceLines.getVisible());
          return null;
        });

    assertTrue(referenceLines.getVisible());
  }

  @Test
  void referenceLineVisibilityIsRestoredWhenCaptureFails() {
    DefaultLayer referenceLines = new DefaultLayer(LayerType.CROSSLINES);

    assertThrows(
        IllegalStateException.class,
        () ->
            DicomContextReader.captureWithoutReferenceLines(
                Optional.of(referenceLines),
                () -> {
                  assertFalse(referenceLines.getVisible());
                  throw new IllegalStateException("capture failed");
                }));

    assertTrue(referenceLines.getVisible());
  }

  @Test
  void currentReferenceFollowsTheCanvasFrameWithoutRebuildingASelectorEntry() {
    DefaultView2d<DicomImageElement> canvas = canvas();
    DicomImageElement image = mock(DicomImageElement.class);
    @SuppressWarnings("unchecked")
    MediaSeries<DicomImageElement> series = mock(MediaSeries.class);
    when(canvas.getImage()).thenReturn(image);
    when(canvas.getSeries()).thenReturn(series);
    when(canvas.getFrameIndex()).thenReturn(2, 8);
    when(series.getSeriesNumber()).thenReturn("4");
    when(series.size(null)).thenReturn(20);

    ReportComposerTool.CaptureViewport viewport = new ReportComposerTool.CaptureViewport(1, canvas);

    assertEquals("Viewport 1 - Series 4, image 3", viewport.toString());
    assertEquals("Viewport 1 - Series 4, image 9", viewport.toString());
  }

  @SuppressWarnings("unchecked")
  private static DefaultView2d<DicomImageElement> canvas() {
    return mock(DefaultView2d.class);
  }
}
