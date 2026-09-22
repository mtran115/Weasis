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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import javax.swing.JPanel;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ExportImage;
import org.weasis.core.ui.editor.image.ViewCanvasOverlay;

class LumbarLevelExportTest {
  @Test
  @SuppressWarnings("unchecked")
  void viewportLevelOverlayIsNotCopiedIntoKeyImageOrPrintCanvas() {
    var source = (DefaultView2d<ImageElement>) mock(DefaultView2d.class, RETURNS_DEEP_STUBS);
    var component = new JPanel();
    component.setSize(256, 256);
    when(source.getJComponent()).thenReturn(component);
    when(source.getActionValue(ActionW.ZOOM.cmd())).thenReturn(1.0);
    when(source.getImage()).thenReturn(null);
    when(source.getSeries()).thenReturn(null);
    ViewCanvasOverlay overlay = (g, canvas) -> fail("A transient proposal must not be exported");
    when(source.getClientProperty(ViewCanvasOverlay.KEY)).thenReturn(overlay);
    var exported = new ExportImage<>(source);
    try {
      assertNull(exported.getClientProperty(ViewCanvasOverlay.KEY));
    } finally {
      exported.disposeView();
    }
  }
}
