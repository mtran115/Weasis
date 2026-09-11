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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.WeakHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import org.dcm4che3.data.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opencv.core.CvType;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.SimpleOpManager;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.layer.imp.RenderedImageLayer;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.display.MrWindowLevelRange;
import org.weasis.dicom.codec.display.WindowAndPresetsOp;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.lut.LutShape;

class MrAutoWindowLevelShortcutTest {
  @Test
  void autoWorksWithoutPresetComboAndRepeatingItPreservesPreviousSetting() throws Exception {
    Fixture f = new Fixture();
    f.start().finish();
    assertEquals(1200.0, f.node.getParam(ActionW.WINDOW.cmd()));
    assertEquals(600.0, f.node.getParam(ActionW.LEVEL.cmd()));
    assertSame(LutShape.LINEAR, f.node.getParam(ActionW.LUT_SHAPE.cmd()));
    f.start().finish();
    f.previous();
    assertEquals(400.0, f.node.getParam(ActionW.WINDOW.cmd()));
    assertEquals(100.0, f.node.getParam(ActionW.LEVEL.cmd()));
    assertSame(LutShape.SIGMOID, f.node.getParam(ActionW.LUT_SHAPE.cmd()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"manual", "series", "pane", "padding", "presentation", "filter"})
  void delayedAutoCannotOverrideNewReadingState(String change) throws Exception {
    Fixture f = new Fixture();
    Job job = f.start();
    SwingUtilities.invokeAndWait(
        () -> {
          switch (change) {
            case "manual" -> f.node.setParam(ActionW.WINDOW.cmd(), 777.0);
            case "series" -> when(f.view.getSeries()).thenReturn(mock(DicomSeries.class));
            case "pane" -> when(f.manager.getSelectedViewPane()).thenReturn(mock(View2d.class));
            case "padding" -> f.node.setParam(ActionW.IMAGE_PIX_PADDING.cmd(), false);
            case "presentation" ->
                when(f.view.getActionValue(ActionW.PR_STATE.cmd())).thenReturn("changed");
            case "filter" ->
                when(f.view.getActionValue(ActionW.FILTERED_SERIES.cmd())).thenReturn(new Object());
            default -> fail("Unexpected change");
          }
        });
    job.finish();
    assertEquals(change.equals("manual") ? 777.0 : 400.0, f.node.getParam(ActionW.WINDOW.cmd()));
    verify(f.view.getImageLayer(), never()).updateDisplayOperations();
  }

  @Test
  void scrollingWithinSameSeriesDoesNotDiscardRequestedAuto() throws Exception {
    Fixture f = new Fixture();
    Job job = f.start();
    DicomImageElement next = mock(DicomImageElement.class);
    when(next.getImage()).thenReturn(f.pixels);
    when(f.view.getImage()).thenReturn(next);
    job.finish();
    assertEquals(1200.0, f.node.getParam(ActionW.WINDOW.cmd()));
    verify(f.view.getImageLayer()).updateDisplayOperations();
  }

  @Test
  void previousCancelsPendingAutoEvenWhenThereIsNothingToRestore() throws Exception {
    Fixture f = new Fixture();
    Job job = f.start();
    f.previous();
    job.finish();
    assertEquals(400.0, f.node.getParam(ActionW.WINDOW.cmd()));
  }

  @Test
  void newestAutoRequestWinsEvenIfOldDecodeFinishesLater() throws Exception {
    Fixture f = new Fixture();
    Job old = f.start();
    Job latest = f.start();
    latest.finish();
    old.finish();
    assertEquals(1200.0, f.node.getParam(ActionW.WINDOW.cmd()));
    verify(f.view.getImageLayer(), times(1)).updateDisplayOperations();
  }

  private record Job(CountDownLatch release, CountDownLatch completed) {
    void finish() throws Exception {
      release.countDown();
      assertTrue(completed.await(5, TimeUnit.SECONDS), "Auto should finish promptly");
      SwingUtilities.invokeAndWait(() -> {});
    }
  }

  private static class Fixture {
    final EventManager manager = mock(EventManager.class);
    final View2d view = mock(View2d.class);
    final DicomSeries series = mock(DicomSeries.class);
    final DicomImageElement image = mock(DicomImageElement.class);
    final PlanarImage pixels = mock(PlanarImage.class);
    final WindowAndPresetsOp node = new WindowAndPresetsOp();

    Fixture() throws Exception {
      Field selected = ImageViewerEventManager.class.getDeclaredField("selectedView2dContainer");
      selected.setAccessible(true);
      selected.set(manager, mock(View2dContainer.class));
      when(manager.getSelectedViewPane()).thenReturn(view);
      when(view.getSeries()).thenReturn(series);
      when(view.getImage()).thenReturn(image);
      when(view.getImageLayer()).thenReturn(mock(RenderedImageLayer.class));
      when(view.getActionsInView()).thenReturn(new HashMap<>());
      when(series.getTagValue(TagD.get(Tag.Modality))).thenReturn("MR");
      when(series.copyOfMedias(any(), any())).thenReturn(List.of(image));
      when(image.getImage()).thenReturn(pixels);
      when(pixels.type()).thenReturn(CvType.CV_16UC1);
      SimpleOpManager display = new SimpleOpManager();
      display.addImageOperationAction(node);
      when(view.getDisplayOpManager()).thenReturn(display);
      node.setParam(WindowOp.P_IMAGE_ELEMENT, image);
      node.setParam(ActionW.WINDOW.cmd(), 400.0);
      node.setParam(ActionW.LEVEL.cmd(), 100.0);
      node.setParam(ActionW.LUT_SHAPE.cmd(), LutShape.SIGMOID);
      node.setParam(ActionW.DEFAULT_PRESET.cmd(), false);
      Field previous = EventManager.class.getDeclaredField("previousWindowLevels");
      previous.setAccessible(true);
      previous.set(manager, new WeakHashMap<>());
      doCallRealMethod().when(manager).applyAutoWindowLevel(view);
    }

    Job start() throws Exception {
      CountDownLatch decoding = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      CountDownLatch completed = new CountDownLatch(1);
      doAnswer(
              invocation -> {
                assertFalse(
                    SwingUtilities.isEventDispatchThread(), "Do not decode on the UI thread");
                decoding.countDown();
                assertTrue(release.await(5, TimeUnit.SECONDS));
                return new MrWindowLevelRange(0, 1200);
              })
          .when(image)
          .getMrAutoWindowLevelRange();
      SwingUtilities.invokeAndWait(
          () -> {
            manager.applyAutoWindowLevel(view);
            try {
              Field pending = EventManager.class.getDeclaredField("pendingMrAutoLevel");
              pending.setAccessible(true);
              SwingWorker<?, ?> worker = (SwingWorker<?, ?>) pending.get(manager);
              worker.addPropertyChangeListener(
                  event -> {
                    if ("state".equals(event.getPropertyName())
                        && event.getNewValue() == SwingWorker.StateValue.DONE) {
                      completed.countDown();
                    }
                  });
            } catch (ReflectiveOperationException e) {
              throw new AssertionError(e);
            }
          });
      assertTrue(decoding.await(5, TimeUnit.SECONDS));
      return new Job(release, completed);
    }

    void previous() throws Exception {
      Method previous =
          EventManager.class.getDeclaredMethod(
              "restorePreviousKeyboardWindowLevel", ViewCanvas.class);
      previous.setAccessible(true);
      SwingUtilities.invokeAndWait(
          () -> {
            try {
              previous.invoke(manager, view);
            } catch (ReflectiveOperationException e) {
              throw new AssertionError(e);
            }
          });
    }
  }
}
