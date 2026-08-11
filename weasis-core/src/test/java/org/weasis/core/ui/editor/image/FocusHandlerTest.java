/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;

class FocusHandlerTest {

  @Test
  @SuppressWarnings("unchecked")
  void mouseEntryActivatesHoveredViewAndMovesKeyboardFocus() {
    ViewCanvas<ImageElement> viewCanvas = mock(ViewCanvas.class);
    ViewCanvas<ImageElement> previousView = mock(ViewCanvas.class);
    ImageViewerEventManager<ImageElement> eventManager = mock(ImageViewerEventManager.class);
    ImageViewerPlugin<ImageElement> container = mock(ImageViewerPlugin.class);
    ImageViewerPlugin<ImageElement> previousContainer = mock(ImageViewerPlugin.class);
    JComponent component = mock(JComponent.class);

    when(viewCanvas.getEventManager()).thenReturn(eventManager);
    when(viewCanvas.getJComponent()).thenReturn(component);
    when(eventManager.getSelectedView2dContainer()).thenReturn(previousContainer);
    when(container.getSelectedViewCanvas()).thenReturn(previousView);

    try (MockedStatic<WinUtil> winUtil = mockStatic(WinUtil.class)) {
      winUtil
          .when(() -> WinUtil.getParentOfClass(component, ImageViewerPlugin.class))
          .thenReturn(container);

      new FocusHandler<>(viewCanvas).mouseEntered(null);
    }

    verify(eventManager).setSelectedView2dContainer(container);
    verify(container).setSelectedImagePane(viewCanvas);
    verify(component).requestFocusInWindow();
  }

  @Test
  @SuppressWarnings("unchecked")
  void postLoadFocusUsesPopulatedPaneWhenSelectionAdvancedToEmptyPane() throws Exception {
    ImageViewerPlugin<ImageElement> container = mock(ImageViewerPlugin.class, CALLS_REAL_METHODS);
    ViewCanvas<ImageElement> emptyView = mock(ViewCanvas.class);
    ViewCanvas<ImageElement> populatedView = mock(ViewCanvas.class);
    MediaSeries<ImageElement> series = mock(MediaSeries.class);
    JComponent component = mock(JComponent.class);

    when(populatedView.getSeries()).thenReturn(series);
    when(populatedView.getJComponent()).thenReturn(component);
    doReturn(emptyView).when(container).getSelectedViewCanvas();
    doReturn(List.of(emptyView, populatedView)).when(container).getImagePanels();
    doNothing().when(container).setSelectedImagePane(populatedView);

    container.requestFocusInSelectedImagePane();
    SwingUtilities.invokeAndWait(() -> {});

    verify(container).setSelectedImagePane(populatedView);
    verify(populatedView).setFocused(true);
    verify(component, atLeastOnce()).requestFocusInWindow();
  }
}
