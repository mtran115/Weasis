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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ArrowAnnotationDialogTest {
  @Test
  void initialGesturePointBecomesArrowHead() {
    ArrowPlacement placement =
        ArrowAnnotationDialog.placementFromHeadFirstGesture(
            new Point(80, 20), new Point(20, 80), 101, 101);

    assertEquals(0.2, placement.tailX());
    assertEquals(0.8, placement.tailY());
    assertEquals(0.8, placement.tipX());
    assertEquals(0.2, placement.tipY());
  }

  @Test
  void automaticArrowUsesACompactTailInsideTheImage() {
    Point head = new Point(500, 500);
    Point tail = ArrowAnnotationDialog.automaticTailFor(head, 1000, 1000);

    assertTrue(tail.x >= 0 && tail.x < 1000);
    assertTrue(tail.y >= 0 && tail.y < 1000);
    assertTrue(head.distance(tail) < 120.0);
    assertTrue(head.distance(tail) > 30.0);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 3})
  void enterSavesExactlyTheCurrentArrowsIncludingNone(int arrowCount) throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          Fixture f = new Fixture();
          for (int index = 0; index < arrowCount; index++) f.addArrow(index);
          List<ArrowPlacement> expected = f.canvas.getPlacements();
          f.enter(f.root, JComponent.WHEN_IN_FOCUSED_WINDOW, KeyEvent.KEY_LOCATION_STANDARD);
          assertEquals(
              List.of(new ArrowAnnotationDialog.AnnotationResult(true, expected)), f.saved);
          assertEquals(0, f.cancelled.get());
        });
  }

  @Test
  void numpadEnterSavesWithoutArrowsAndSaveButtonStaysEnabledAfterClear() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          Fixture f = new Fixture();
          assertTrue(f.root.getDefaultButton().isEnabled());
          f.addArrow(1);
          f.button("Clear All").doClick(0);
          assertTrue(f.root.getDefaultButton().isEnabled());
          f.enter(f.root, JComponent.WHEN_IN_FOCUSED_WINDOW, KeyEvent.KEY_LOCATION_NUMPAD);
          assertEquals(List.of(), f.saved.getFirst().placements());
        });
  }

  @Test
  void enterAfterUndoKeepsRemainingArrows() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          Fixture f = new Fixture();
          f.addArrow(0);
          List<ArrowPlacement> expected = f.canvas.getPlacements();
          f.addArrow(1);
          f.button("Undo Last").doClick(0);
          f.enter(f.root, JComponent.WHEN_IN_FOCUSED_WINDOW, KeyEvent.KEY_LOCATION_STANDARD);
          assertEquals(expected, f.saved.getFirst().placements());
        });
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Undo Last",
        "Clear All",
        "Use Without Arrows",
        "Cancel",
        "Save Key Image (Enter)"
      })
  void focusedButtonCannotChangeEnterIntoDiscardOrCancel(String label) throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          Fixture f = new Fixture();
          f.addArrow(0);
          f.addArrow(1);
          List<ArrowPlacement> expected = f.canvas.getPlacements();
          f.enter(f.button(label), JComponent.WHEN_FOCUSED, KeyEvent.KEY_LOCATION_STANDARD);
          assertEquals(
              List.of(new ArrowAnnotationDialog.AnnotationResult(true, expected)), f.saved);
          assertEquals(0, f.cancelled.get());
        });
  }

  @Test
  void explicitWithoutArrowsStillDiscardsAndEscapeStillCancels() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          Fixture withoutArrows = new Fixture();
          withoutArrows.addArrow(0);
          withoutArrows.button("Use Without Arrows").doClick(0);
          assertEquals(
              List.of(new ArrowAnnotationDialog.AnnotationResult(true, List.of())),
              withoutArrows.saved);
          Fixture cancelled = new Fixture();
          cancelled.addArrow(0);
          invokeKey(
              cancelled.root,
              JComponent.WHEN_IN_FOCUSED_WINDOW,
              KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
          assertEquals(1, cancelled.cancelled.get());
          assertTrue(cancelled.saved.isEmpty());
          assertFalse(cancelled.canvas.getPlacements().isEmpty());
        });
  }

  private static void invokeKey(JComponent component, int condition, KeyStroke stroke) {
    Object binding = component.getInputMap(condition).get(stroke);
    assertNotNull(binding);
    var action = component.getActionMap().get(binding);
    assertNotNull(action);
    assertTrue(action.isEnabled());
    action.actionPerformed(new ActionEvent(component, ActionEvent.ACTION_PERFORMED, ""));
  }

  private static class Fixture {
    final JRootPane root = new JRootPane();
    final ArrowAnnotationDialog.ArrowCanvas canvas =
        new ArrowAnnotationDialog.ArrowCanvas(
            new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB));
    final List<ArrowAnnotationDialog.AnnotationResult> saved = new ArrayList<>();
    final AtomicInteger cancelled = new AtomicInteger();
    final JPanel buttons =
        ArrowAnnotationDialog.buildButtons(
            root,
            canvas,
            () ->
                saved.add(new ArrowAnnotationDialog.AnnotationResult(true, canvas.getPlacements())),
            cancelled::incrementAndGet);

    void addArrow(int index) {
      canvas.addPlacement(new ArrowPlacement(0.1, 0.1, 0.5 + index * 0.1, 0.5));
    }

    JButton button(String text) {
      return Arrays.stream(buttons.getComponents())
          .filter(JButton.class::isInstance)
          .map(JButton.class::cast)
          .filter(button -> text.equals(button.getText()))
          .findFirst()
          .orElseThrow();
    }

    void enter(JComponent component, int condition, int location) {
      KeyEvent event =
          new KeyEvent(component, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ENTER, '\n', location);
      invokeKey(component, condition, KeyStroke.getKeyStrokeForEvent(event));
    }
  }
}
