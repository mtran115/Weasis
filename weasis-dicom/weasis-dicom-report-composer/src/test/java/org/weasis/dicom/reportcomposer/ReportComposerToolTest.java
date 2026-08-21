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

import java.awt.Rectangle;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.reportcomposer.DicomContextReader.ViewportCanvas;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.GeneratedFinding;
import org.weasis.dicom.reportcomposer.ReportComposerTool.CaptureViewport;

class ReportComposerToolTest {
  @Test
  void popOutWindowFitsOnASecondaryDisplayWithNegativeCoordinates() {
    Rectangle display = new Rectangle(-1440, 0, 1440, 900);

    Rectangle window = ReportComposerTool.preferredPopOutBounds(display);

    assertEquals(new Rectangle(-1416, 24, 600, 852), window);
    assertTrue(display.contains(window));
  }

  @Test
  void popOutWindowFitsOnASmallDisplay() {
    Rectangle display = new Rectangle(0, 0, 400, 300);

    Rectangle window = ReportComposerTool.preferredPopOutBounds(display);

    assertEquals(new Rectangle(15, 15, 370, 270), window);
    assertTrue(display.contains(window));
  }

  @Test
  void structuredSpineFindingIsNotRepeatedAsAnImpression() {
    FindingEntry finding =
        ReportComposerTool.structuredSpineFinding(
            new GeneratedFinding("Lumbar spondylosis.", "Lumbar spondylosis."));

    assertFalse(finding.includeInImpression());
  }

  @Test
  void unchangedViewportConfigurationKeepsTheExistingSelectorModel() {
    DefaultView2d<DicomImageElement> left = canvas();
    DefaultView2d<DicomImageElement> right = canvas();

    assertTrue(
        ReportComposerTool.sameViewportConfiguration(
            List.of(new CaptureViewport(1, left), new CaptureViewport(2, right)),
            List.of(new ViewportCanvas(1, left), new ViewportCanvas(2, right))));
  }

  @Test
  void changedViewportIdentityOrPositionRebuildsTheSelectorModel() {
    DefaultView2d<DicomImageElement> left = canvas();
    DefaultView2d<DicomImageElement> right = canvas();
    List<CaptureViewport> existing =
        List.of(new CaptureViewport(1, left), new CaptureViewport(2, right));

    assertFalse(
        ReportComposerTool.sameViewportConfiguration(
            existing, List.of(new ViewportCanvas(1, left), new ViewportCanvas(2, canvas()))));
    assertFalse(
        ReportComposerTool.sameViewportConfiguration(
            existing, List.of(new ViewportCanvas(2, left), new ViewportCanvas(3, right))));
  }

  @SuppressWarnings("unchecked")
  private static DefaultView2d<DicomImageElement> canvas() {
    return mock(DefaultView2d.class);
  }
}
