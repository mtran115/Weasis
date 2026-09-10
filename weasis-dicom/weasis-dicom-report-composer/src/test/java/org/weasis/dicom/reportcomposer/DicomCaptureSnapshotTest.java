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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import org.dcm4che3.data.Tag;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ViewTransferHandler;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;

class DicomCaptureSnapshotTest {
  @Test
  void annotationUsesTheImageAndTransformCapturedBeforeTheViewportChanges() {
    @SuppressWarnings("unchecked")
    DefaultView2d<DicomImageElement> canvas = mock(DefaultView2d.class);
    @SuppressWarnings("unchecked")
    MediaSeries<DicomImageElement> series = mock(MediaSeries.class);
    DicomImageElement firstImage = mock(DicomImageElement.class);
    when(firstImage.getKey()).thenReturn(4);
    when(firstImage.getTagValue(TagD.get(Tag.SOPInstanceUID))).thenReturn("captured-sop");
    when(firstImage.getTagValue(TagD.get(Tag.Rows))).thenReturn(100);
    when(firstImage.getTagValue(TagD.get(Tag.Columns))).thenReturn(100);
    when(firstImage.getRescaleX()).thenReturn(1.0);
    when(firstImage.getRescaleY()).thenReturn(1.0);
    when(canvas.getImage()).thenReturn(firstImage);
    when(canvas.getSeries()).thenReturn(series);
    when(canvas.getWidth()).thenReturn(100);
    when(canvas.getHeight()).thenReturn(100);
    AffineTransform inverse = new AffineTransform();
    when(canvas.getInverseTransform()).thenReturn(inverse);
    when(canvas.getClipViewCoordinatesOffset()).thenReturn(new Point2D.Double());
    GraphicModel graphics = mock(GraphicModel.class);
    when(canvas.getGraphicManager()).thenReturn(graphics);
    when(graphics.findLayerByType(LayerType.CROSSLINES)).thenReturn(Optional.empty());
    ImageReference staleReference = new ImageReference("old-series", "old-sop", "1", "", "", 1, 1);
    var selection = new DicomContextReader.Selection(CaseContext.empty(), staleReference, canvas);

    DicomContextReader.CapturedView capture;
    try (var renderer = mockStatic(ViewTransferHandler.class)) {
      renderer
          .when(() -> ViewTransferHandler.createComponentImage(canvas, true))
          .thenReturn(new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB));
      capture = selection.capture();
    }
    when(canvas.getImage()).thenReturn(mock(DicomImageElement.class));
    inverse.translate(5000, 5000);
    KeyImageCapture keyImage =
        KeyImageCapture.createWithArrows(
            capture,
            List.of(new ArrowPlacement(0.1, 0.1, 0.2, 0.3)),
            "Caption",
            "finding",
            "selected_finding");

    assertEquals("captured-sop", keyImage.reference().sopInstanceUid());
    assertEquals(5, keyImage.reference().sourceFrameNumber());
    assertEquals(20, keyImage.sourceArrows().getFirst().tipX(), 1e-9);
    assertEquals(30, keyImage.sourceArrows().getFirst().tipY(), 1e-9);
  }
}
