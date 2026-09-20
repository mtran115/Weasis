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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import java.awt.event.InputEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Optional;
import javax.swing.JComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.media.data.ImageElement;

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

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void singleClickRestoresTheActualViewerWithoutRequiringMouseEntry(boolean selectionMissing) {
    Fixture f = new Fixture();
    when(f.manager.getSelectedView2dContainer())
        .thenReturn(selectionMissing ? null : f.previousContainer);
    when(f.container.getSelectedViewCanvas()).thenReturn(f.previousView);
    try (MockedStatic<WinUtil> ignored = f.parent(f.container)) {
      f.handler.mousePressed(f.press(1));
    }
    var order = inOrder(f.manager, f.container, f.component);
    order.verify(f.manager).setSelectedView2dContainer(f.container);
    order.verify(f.container).setSelectedImagePane(f.view);
    order.verify(f.component).requestFocusInWindow();
    verifyNoInteractions(f.previousContainer);
  }

  @Test
  void dockingReselectionKeepsKeyboardFocusOnTheImageAfterTheClick() {
    Fixture f = new Fixture();
    when(f.container.requestFocusInWindow()).thenCallRealMethod();
    try (MockedStatic<WinUtil> ignored = f.parent(f.container)) {
      f.handler.mousePressed(f.press(1));
      // WeasisWin reselects its current plugin when the docking framework regains focus.
      assertTrue(f.container.requestFocusInWindow());
    }
    verify(f.component, times(2)).requestFocusInWindow();
    verify(f.manager, never()).setSelectedView2dContainer(any());
    verify(f.container, never()).setMouseActions(any());
  }

  @Test
  void missingKeyboardListenersAreRestoredOnceWithoutRebuildingOnEveryClick() {
    Fixture f = new Fixture();
    when(f.component.getKeyListeners()).thenReturn(new KeyListener[0]);
    MouseActions actions = mock(MouseActions.class);
    when(f.manager.getMouseActions()).thenReturn(actions);
    doAnswer(
            invocation -> {
              when(f.component.getKeyListeners()).thenReturn(new KeyListener[] {f.view});
              return null;
            })
        .when(f.container)
        .setMouseActions(actions);
    try (MockedStatic<WinUtil> ignored = f.parent(f.container)) {
      f.handler.mousePressed(f.press(1));
      f.handler.mousePressed(f.press(1));
    }
    verify(f.container).setMouseActions(actions);
    verify(f.component, times(2)).requestFocusInWindow();
    verify(f.manager, never()).setSelectedView2dContainer(any());
  }

  @Test
  void onlyAnExplicitClickCanRequestFocusWhileTheNativeWindowIsReactivating() {
    Fixture f = new Fixture();
    when(f.component.requestFocusInWindow()).thenReturn(false);
    try (MockedStatic<WinUtil> ignored = f.parent(f.container)) {
      f.handler.mouseEntered(null);
      verify(f.component, never()).requestFocus();
      f.handler.mousePressed(f.press(1));
    }
    verify(f.component).requestFocus();
  }

  @Test
  void doubleClickUsesTheClickedImagesViewerInsteadOfAStaleSelection() {
    Fixture f = new Fixture();
    when(f.manager.getSelectedView2dContainer()).thenReturn(f.previousContainer);
    MouseEvent press = f.press(2);
    try (MockedStatic<WinUtil> ignored = f.parent(f.container)) {
      f.handler.mousePressed(press);
    }
    verify(f.container).maximizedSelectedImagePane(f.view, press);
    verifyNoInteractions(f.previousContainer);
  }

  @Test
  void fullscreenCanvasUsesItsLayoutOwnerButAnUnrelatedCanvasDoesNot() {
    Fixture f = new Fixture();
    try (MockedStatic<WinUtil> ignored = f.parent(null)) {
      f.handler.mousePressed(f.press(1));
      verify(f.component, never()).requestFocusInWindow();
      when(f.container.isContainingView(f.view)).thenReturn(true);
      f.handler.mousePressed(f.press(1));
    }
    verify(f.component).requestFocusInWindow();
  }

  @Test
  void overlayButtonPressRemainsConsumedBeforeViewerActivation() {
    Fixture f = new Fixture();
    when(f.manager.getSelectedView2dContainer()).thenReturn(f.previousContainer);
    ViewButton button = mock(ViewButton.class);
    when(button.isVisible()).thenReturn(true);
    when(button.contains(any(java.awt.Point.class))).thenReturn(true);
    when(f.view.getViewButtons()).thenReturn(List.of(button));
    MouseEvent event = f.press(1);
    doAnswer(
            invocation -> {
              assertTrue(event.isConsumed());
              return null;
            })
        .when(f.manager)
        .setSelectedView2dContainer(f.container);
    try (MockedStatic<WinUtil> ignored = f.parent(f.container)) {
      f.handler.mousePressed(event);
    }
    verify(button).showPopup(f.component, 20, 20);
  }

  @SuppressWarnings("unchecked")
  private static final class Fixture {
    final ViewCanvas<ImageElement> view = mock(ViewCanvas.class);
    final ViewCanvas<ImageElement> previousView = mock(ViewCanvas.class);
    final ImageViewerEventManager<ImageElement> manager = mock(ImageViewerEventManager.class);
    final ImageViewerPlugin<ImageElement> container = mock(ImageViewerPlugin.class);
    final ImageViewerPlugin<ImageElement> previousContainer = mock(ImageViewerPlugin.class);
    final JComponent component = mock(JComponent.class);
    final FocusHandler<ImageElement> handler = new FocusHandler<>(view);

    Fixture() {
      when(view.getEventManager()).thenReturn(manager);
      when(view.getJComponent()).thenReturn(component);
      when(view.getViewButtons()).thenReturn(List.of());
      when(manager.getSelectedView2dContainer()).thenReturn(container);
      when(manager.getMouseAction(anyInt())).thenReturn(Optional.empty());
      when(container.getSelectedViewCanvas()).thenReturn(view);
      when(component.getKeyListeners()).thenReturn(new KeyListener[] {view});
      when(component.requestFocusInWindow()).thenReturn(true);
    }

    MouseEvent press(int count) {
      return new MouseEvent(
          component,
          MouseEvent.MOUSE_PRESSED,
          0,
          InputEvent.BUTTON1_DOWN_MASK,
          20,
          20,
          20,
          20,
          count,
          false,
          MouseEvent.BUTTON1);
    }

    MockedStatic<WinUtil> parent(ImageViewerPlugin<ImageElement> parent) {
      MockedStatic<WinUtil> winUtil = mockStatic(WinUtil.class);
      winUtil
          .when(() -> WinUtil.getParentOfClass(component, ImageViewerPlugin.class))
          .thenReturn(parent);
      return winUtil;
    }
  }
}
