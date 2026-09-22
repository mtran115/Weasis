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

import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.ExamTemplate;

class LumbarLevelPanelTest {
  @Test
  @SuppressWarnings("unchecked")
  void delayedResultCannotAppearOnTheNextStudy() throws Exception {
    var service = mock(LumbarLevelService.class);
    var selection = mock(DicomContextReader.Selection.class);
    var context = mock(CaseContext.class);
    var canvas = (DefaultView2d<DicomImageElement>) mock(DefaultView2d.class);
    var series = (MediaSeries<DicomImageElement>) mock(MediaSeries.class);
    when(selection.context()).thenReturn(context);
    when(selection.canvas()).thenReturn(canvas);
    when(context.studyInstanceUid()).thenReturn("study");
    when(canvas.getSeries()).thenReturn(series);
    when(series.copyOfMedias(null, null)).thenReturn(List.of());
    var first = new CompletableFuture<LumbarLevelService.Result>();
    var second = new CompletableFuture<LumbarLevelService.Result>();
    when(service.request(eq("first"), anyString(), anyList())).thenReturn(first);
    when(service.request(eq("second"), anyString(), anyList())).thenReturn(second);
    var panel = new LumbarLevelPanel[] {null};
    SwingUtilities.invokeAndWait(
        () -> {
          panel[0] = new LumbarLevelPanel(() -> Optional.of(selection), service);
          panel[0].setContext(ExamTemplate.LUMBAR_SPINE, "first", "study");
          when(service.installed()).thenReturn(true);
          button(panel[0], "Retry").doClick();
          when(service.installed()).thenReturn(false);
          panel[0].setContext(ExamTemplate.LUMBAR_SPINE, "second", "study");
          when(service.installed()).thenReturn(true);
          button(panel[0], "Retry").doClick();
        });
    first.complete(result("first"));
    SwingUtilities.invokeAndWait(() -> assertFalse(button(panel[0], "Review…").isEnabled()));
    second.complete(result("second"));
    SwingUtilities.invokeAndWait(
        () -> {
          assertTrue(button(panel[0], "Review…").isEnabled());
          assertFalse(
              button(panel[0], "L1-L2").isEnabled()); // Navigation requires reader confirmation.
          assertTrue(
              children(panel[0]).stream()
                  .filter(JLabel.class::isInstance)
                  .map(JLabel.class::cast)
                  .anyMatch(label -> label.getText().startsWith("Provisional")));
          when(service.installed()).thenReturn(false);
          when(service.request(eq("case"), anyString(), anyList()))
              .thenReturn(
                  CompletableFuture.completedFuture(
                      new LumbarLevelService.Result(
                          LumbarLevelMapTest.unexpectedMap(),
                          new BufferedImage(256, 256, BufferedImage.TYPE_BYTE_GRAY),
                          null)));
          panel[0].setContext(ExamTemplate.LUMBAR_SPINE, "case", "study");
          when(service.installed()).thenReturn(true);
          button(panel[0], "Retry").doClick();
        });
    SwingUtilities.invokeAndWait(
        () -> {
          assertTrue(
              children(panel[0]).stream()
                  .filter(JLabel.class::isInstance)
                  .map(JLabel.class::cast)
                  .anyMatch(
                      label -> label.getText().equals("Numbering uncertain—review required.")));
          assertFalse(button(panel[0], "Disc 1 ?").isEnabled());
          assertTrue(
              children(panel[0]).stream()
                  .filter(JButton.class::isInstance)
                  .map(JButton.class::cast)
                  .noneMatch(button -> button.getText().contains("T13")));
          try (var reader = mockStatic(DicomContextReader.class)) {
            reader.when(DicomContextReader::visibleCanvases).thenReturn(List.of());
            children(panel[0]).stream()
                .filter(javax.swing.JCheckBox.class::isInstance)
                .map(javax.swing.JCheckBox.class::cast)
                .filter(box -> box.getText().equals("Show levels"))
                .findFirst()
                .orElseThrow()
                .doClick();
          }
          assertFalse(button(panel[0], "Confirm").isEnabled());
          panel[0].setContext(ExamTemplate.SHOULDER, "shoulder", "study");
          assertFalse(panel[0].isVisible());
          assertFalse(button(panel[0], "Review…").isEnabled());
          panel[0].close();
        });
  }

  private static LumbarLevelService.Result result(String key) {
    return new LumbarLevelService.Result(
        LumbarLevelMapTest.map(key, "a".repeat(64)),
        new BufferedImage(256, 256, BufferedImage.TYPE_BYTE_GRAY),
        null);
  }

  private static JButton button(Container root, String text) {
    return children(root).stream()
        .filter(JButton.class::isInstance)
        .map(JButton.class::cast)
        .filter(button -> button.getText().equals(text))
        .findFirst()
        .orElseThrow();
  }

  private static List<Component> children(Container root) {
    var result = new ArrayList<Component>();
    for (var child : root.getComponents()) {
      result.add(child);
      if (child instanceof Container c) result.addAll(children(c));
    }
    return result;
  }
}
