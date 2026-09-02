/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.SynchCineEvent;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.dicom.codec.DicomImageElement;

class DicomSynchManagerTest {

  @Test
  @SuppressWarnings("unchecked")
  void crosslineRefreshDoesNotBroadcastToSamePlaneStacks() {
    ViewCanvas<DicomImageElement> source = mock(ViewCanvas.class);
    DefaultView2d<DicomImageElement> crosslineView = mock(DefaultView2d.class);
    DefaultView2d<DicomImageElement> samePlaneView = mock(DefaultView2d.class);
    DicomImageElement image = mock(DicomImageElement.class);

    when(source.getImage()).thenReturn(image);
    when(source.getFrameIndex()).thenReturn(7);
    when(image.getTagValue(TagW.SlicePosition)).thenReturn(42.5);
    when(crosslineView.getActionValue(ActionW.SYNCH_CROSSLINE.cmd())).thenReturn(true);
    when(samePlaneView.getActionValue(ActionW.SYNCH_CROSSLINE.cmd())).thenReturn(false);

    DicomSynchManager.refreshCrosslines(source, List.of(source, crosslineView, samePlaneView));

    ArgumentCaptor<SynchCineEvent> event = ArgumentCaptor.forClass(SynchCineEvent.class);
    verify(crosslineView).propertyChange(event.capture());
    verify(samePlaneView, never()).propertyChange(any(SynchCineEvent.class));
    assertSame(source, event.getValue().getView());
    assertSame(image, event.getValue().getMedia());
    assertEquals(7, event.getValue().getSeriesIndex());
    assertEquals(42.5, event.getValue().getLocation());
  }
}
