/*
 * Copyright (c) 2009-2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ShortcutManager;
import org.weasis.core.api.image.ImageOpNode;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.model.layer.imp.RenderedImageLayer;
import org.weasis.core.ui.model.utils.bean.PanPoint;
import org.weasis.core.ui.model.utils.imp.DefaultViewModel;

class ImageViewerEventManagerTest {

  @Test
  void calculatesPanNeededToRestoreCursorAnchor() {
    Point2D adjustment =
        ImageViewerEventManager.calculateZoomAnchorPanAdjustment(
            new Point2D.Double(300.0, 200.0), new Point2D.Double(330.0, 185.0), 2.0);

    assertEquals(15.0, adjustment.getX(), 0.000001);
    assertEquals(-7.5, adjustment.getY(), 0.000001);
  }

  @Test
  void skipsCursorAnchoringWithoutAValidViewportScale() {
    Point2D cursor = new Point2D.Double(300.0, 200.0);
    Point2D anchor = new Point2D.Double(330.0, 185.0);

    assertNull(ImageViewerEventManager.calculateZoomAnchorPanAdjustment(null, anchor, 2.0));
    assertNull(ImageViewerEventManager.calculateZoomAnchorPanAdjustment(cursor, null, 2.0));
    assertNull(ImageViewerEventManager.calculateZoomAnchorPanAdjustment(cursor, anchor, 0.0));
    assertNull(
        ImageViewerEventManager.calculateZoomAnchorPanAdjustment(cursor, anchor, Double.NaN));
  }

  @Test
  void keyboardZoomKeepsTheImageUnderTheCursorWhenItExceedsTheViewport() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          ZoomFixture fixture = new ZoomFixture(512, 1600, new Point(600, 450));
          Point2D anchor = fixture.cursorImagePoint();

          fixture.pressZoom(true);

          fixture.assertUnderCursor(anchor);
        });
  }

  @Test
  void repeatedKeyboardZoomPreservesTheAnchorAcrossTheClippingBoundary() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          ZoomFixture fixture = new ZoomFixture(512, 512, new Point(600, 400));
          fixture.manager.getZoomSetting().setKeyboardZoomPercent(100);
          Point2D anchor = fixture.cursorImagePoint();

          for (int i = 0; i < 4; i++) {
            fixture.pressZoom(true);
            fixture.assertUnderCursor(anchor);
            fixture.pressZoom(false);
            fixture.assertUnderCursor(anchor);
          }
        });
  }

  @Test
  void keyboardZoomKeepsTheCursorAnchorAfterPanningRotatingAndFlipping() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          ZoomFixture fixture = new ZoomFixture(1024, 1600, new Point(650, 450));
          fixture.pane.getViewModel().setModelOffset(80.0, -60.0);
          fixture.pane.getActionsInView().put(ActionW.ROTATION.cmd(), 90);
          fixture.pane.getActionsInView().put(ActionW.FLIP.cmd(), true);
          fixture.updateTransform();
          Point2D anchor = fixture.cursorImagePoint();

          fixture.pressZoom(true);
          fixture.assertUnderCursor(anchor);
          fixture.pressZoom(false);
          fixture.assertUnderCursor(anchor);
        });
  }

  @Test
  void keyboardZoomWithoutACursorKeepsTheExistingPan() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          ZoomFixture fixture = new ZoomFixture(1024, 1600, null);
          fixture.pane.getViewModel().setModelOffset(80.0, -60.0);
          fixture.updateTransform();
          double previousScale = fixture.pane.getViewModel().getViewScale();

          fixture.pressZoom(true);

          assertTrue(fixture.pane.getViewModel().getViewScale() > previousScale);
          assertEquals(80.0, fixture.pane.getViewModel().getModelOffsetX(), 0.000001);
          assertEquals(-60.0, fixture.pane.getViewModel().getModelOffsetY(), 0.000001);
        });
  }

  private static final class ZoomFixture {
    private final GraphicsPane pane;
    private final Point cursor;
    private final ViewCanvas<ImageElement> view;
    private final RenderedImageLayer<ImageElement> imageLayer;
    private final ImageOpNode transformNode = mock(ImageOpNode.class);
    private final ImageViewerEventManager<ImageElement> manager;

    @SuppressWarnings("unchecked")
    private ZoomFixture(int imageWidth, int imageHeight, Point cursor) {
      this.cursor = cursor;
      pane =
          new GraphicsPane(new DefaultViewModel()) {
            @Override
            public double getRealWorldViewScale() {
              return 1.0;
            }

            @Override
            public double adjustViewScale(double viewScale) {
              return viewScale;
            }

            @Override
            public Point getMousePosition() {
              return cursor;
            }
          };
      pane.setSize(800, 600);
      pane.getViewModel().setModelArea(new Rectangle2D.Double(0, 0, imageWidth, imageHeight));
      imageLayer = mock(RenderedImageLayer.class);
      view = mock(ViewCanvas.class);
      when(view.getJComponent()).thenReturn(pane);
      when(view.getImageLayer()).thenReturn(imageLayer);
      when(view.getViewModel()).thenReturn(pane.getViewModel());
      when(view.getAffineTransform()).thenReturn(pane.getAffineTransform());
      when(view.getActionValue(anyString()))
          .thenAnswer(invocation -> pane.getActionValue(invocation.getArgument(0)));
      when(view.getClipViewCoordinatesOffset())
          .thenAnswer(invocation -> pane.getClipViewCoordinatesOffset());
      when(view.getImageCoordinatesFromMouse(anyInt(), anyInt()))
          .thenAnswer(
              invocation ->
                  pane.getImageCoordinatesFromMouse(
                      invocation.getArgument(0), invocation.getArgument(1)));
      when(view.viewToModel(anyDouble(), anyDouble()))
          .thenAnswer(
              invocation -> pane.viewToModel(invocation.getArgument(0), invocation.getArgument(1)));
      when(view.modelToView(anyDouble(), anyDouble()))
          .thenAnswer(
              invocation -> pane.modelToView(invocation.getArgument(0), invocation.getArgument(1)));
      doAnswer(
              invocation -> {
                PanPoint move = invocation.getArgument(0);
                assertEquals(PanPoint.State.MOVE, move.getState());
                pane.getViewModel()
                    .setModelOffset(
                        pane.getViewModel().getModelOffsetX() + move.getX(),
                        pane.getViewModel().getModelOffsetY() + move.getY());
                updateTransform();
                return null;
              })
          .when(view)
          .moveOrigin(any(PanPoint.class));
      updateTransform();

      // Keyboard zoom does not need the application's physical mouse preferences.
      try (var ignored = mockConstruction(MouseActions.class)) {
        manager =
            mock(
                ImageViewerEventManager.class,
                withSettings().useConstructor().defaultAnswer(CALLS_REAL_METHODS));
      }
      doReturn(view).when(manager).getSelectedViewPane();
      manager.setAction(manager.newZoomAction());
      manager.addPropertyChangeListener(
          ActionW.SYNCH.cmd(),
          event -> {
            SynchEvent zoomEvent = (SynchEvent) event.getNewValue();
            pane.zoom((Double) zoomEvent.getEvents().get(ActionW.ZOOM.cmd()));
            updateTransform();
          });
    }

    private void updateTransform() {
      // Exercise viewport clipping without allocating native image data.
      pane.updateAffineTransform(view, transformNode, imageLayer, 0.0);
    }

    private Point2D cursorImagePoint() {
      return pane.getImageCoordinatesFromMouse(cursor.x, cursor.y);
    }

    private void pressZoom(boolean zoomIn) {
      int keyCode = zoomIn ? KeyEvent.VK_SLASH : KeyEvent.VK_BACK_SLASH;
      ShortcutManager shortcuts = mock(ShortcutManager.class);
      when(shortcuts.matches(
              zoomIn ? ShortcutManager.ID_VIEWER_ZOOM_IN : ShortcutManager.ID_VIEWER_ZOOM_OUT,
              keyCode,
              0))
          .thenReturn(true);
      try (var shortcutScope = mockStatic(ShortcutManager.class, CALLS_REAL_METHODS)) {
        shortcutScope.when(ShortcutManager::getInstance).thenReturn(shortcuts);
        assertTrue(
            manager.commonDisplayShortcuts(
                new KeyEvent(pane, KeyEvent.KEY_PRESSED, 0L, 0, keyCode, KeyEvent.CHAR_UNDEFINED)));
      }
    }

    private void assertUnderCursor(Point2D anchor) {
      Point2D transformed = pane.getAffineTransform().transform(anchor, null);
      Point2D offset = pane.getClipViewCoordinatesOffset();
      assertEquals(cursor.x, transformed.getX() + offset.getX(), 0.000001);
      assertEquals(cursor.y, transformed.getY() + offset.getY(), 0.000001);
    }
  }
}
