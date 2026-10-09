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

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultEditorKit;
import org.junit.jupiter.api.Test;

class ComposerTextEditingTest {
  private final AtomicLong now = new AtomicLong(10_000);

  private JTextArea box(String text) {
    JTextArea area = ComposerTextEditing.install(new JTextArea(text), now::get);
    area.setSize(400, 200);
    return area;
  }

  private static void press(JTextArea area, int key, int modifiers) {
    Object name = area.getInputMap().get(KeyStroke.getKeyStroke(key, modifiers));
    area.getActionMap()
        .get(name)
        .actionPerformed(new ActionEvent(area, ActionEvent.ACTION_PERFORMED, ""));
  }

  private void type(JTextArea area, String text) throws BadLocationException {
    for (char character : text.toCharArray()) {
      area.getDocument()
          .insertString(area.getDocument().getLength(), String.valueOf(character), null);
      now.addAndGet(120);
    }
  }

  @Test
  void homeAndEndWorkOnTheLineInsteadOfTheWholeBox() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          JTextArea area = box("L4-5 disc bulge.\n\nL5-S1 protrusion.\n\nNo stenosis.");
          area.setCaretPosition(area.getDocument().getLength());

          press(area, KeyEvent.VK_HOME, InputEvent.SHIFT_DOWN_MASK);
          assertEquals("No stenosis.", area.getSelectedText());

          area.setCaretPosition(area.getText().indexOf("L5-S1"));
          press(area, KeyEvent.VK_END, InputEvent.SHIFT_DOWN_MASK);
          assertEquals("L5-S1 protrusion.", area.getSelectedText());

          assertEquals(
              DefaultEditorKit.beginLineAction,
              area.getInputMap().get(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0)));
          assertTrue(
              Arrays.asList(area.getInputMap().keys())
                  .contains(KeyStroke.getKeyStroke(KeyEvent.VK_END, 0)),
              "End is bound on the box itself, ahead of the look and feel");
        });
  }

  @Test
  void undoTakesBackABurstOfTypingAtATime() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            JTextArea area = box("");
            type(area, "mild");
            now.addAndGet(ComposerTextEditing.GROUP_MILLIS);
            type(area, " bulge");
            area.getDocument().remove(area.getDocument().getLength() - 1, 1);

            press(area, KeyEvent.VK_Z, InputEvent.META_DOWN_MASK);
            assertEquals("mild bulge", area.getText());
            press(area, KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK);
            assertEquals("mild", area.getText());
            press(area, KeyEvent.VK_Z, InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
            assertEquals("mild bulge", area.getText());
          } catch (BadLocationException e) {
            fail(e);
          }
        });
  }

  @Test
  void aReplacementUndoesInOneStep() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          JTextArea area = box("");
          area.setText("Before.");
          now.addAndGet(5_000);
          area.setText("After.");

          press(area, KeyEvent.VK_Z, InputEvent.META_DOWN_MASK);
          assertEquals("Before.", area.getText());
        });
  }

  @Test
  void readOnlyBoxesKeepNoHistory() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          JTextArea preview = box("Report");
          preview.setEditable(false);
          preview.setText("Updated report");

          press(preview, KeyEvent.VK_Z, InputEvent.META_DOWN_MASK);
          preview.setEditable(true);
          press(preview, KeyEvent.VK_Z, InputEvent.META_DOWN_MASK);
          assertEquals("Updated report", preview.getText());
        });
  }

  @Test
  void forgettingHistoryKeepsAnotherStudysTextOutOfReach() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          JPanel composer = new JPanel();
          JTextArea findings = box("");
          JTextArea impression = box("");
          JPanel form = new JPanel();
          form.add(impression);
          composer.add(findings);
          composer.add(form);
          findings.setText("Previous patient finding.");
          impression.setText("Previous patient impression.");
          now.addAndGet(5_000);
          findings.setText("");
          impression.setText("");

          ComposerTextEditing.forgetHistoryWithin(composer);
          press(findings, KeyEvent.VK_Z, InputEvent.META_DOWN_MASK);
          press(impression, KeyEvent.VK_Z, InputEvent.META_DOWN_MASK);

          assertEquals("", findings.getText());
          assertEquals("", impression.getText());
        });
  }
}
