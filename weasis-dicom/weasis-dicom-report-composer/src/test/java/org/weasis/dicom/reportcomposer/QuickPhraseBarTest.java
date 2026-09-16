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

import java.awt.Component;
import java.awt.Container;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.ExamTemplate;
import org.weasis.dicom.reportcomposer.ReportQuickPhrases.Phrase;

class QuickPhraseBarTest {
  @Test
  void clickingAPhrasePreservesSurroundingInstructionsAndTriggersTheExistingSaveWatcher()
      throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          Fixture f = new Fixture(ExamTemplate.KNEE);
          f.text.setText("Keep this finding.\nKeep this instruction.");
          f.text.setCaretPosition("Keep this finding.".length());
          AtomicReference<String> saved = new AtomicReference<>();
          ComposerFormState.watch(f.text, () -> saved.set(f.text.getText()));
          button(f.bar, "Prepatellar edema").doClick(0);
          assertEquals(
              "Keep this finding.\nMild prepatellar soft tissue edema.\nKeep this instruction.",
              f.text.getText());
          assertEquals(f.text.getText(), saved.get());
          assertEquals(
              f.text.getText().indexOf("\nKeep this instruction."), f.text.getCaretPosition());
          button(f.bar, "Motion limit").doClick(0);
          assertTrue(
              f.text
                  .getText()
                  .contains("edema.\nLimited exam due to motion.\nKeep this instruction."));
        });
  }

  @Test
  void replacingSelectedTextDoesNotEraseTheRestOfTheReport() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          Fixture f = new Fixture(ExamTemplate.KNEE);
          f.text.setText("Before.\nreplace me\nAfter.");
          f.text.select(8, 18);
          button(f.bar, "Quad tendinosis").doClick(0);
          assertEquals("Before.\nMild quadriceps tendinosis.\nAfter.", f.text.getText());
        });
  }

  @Test
  void detailsRemainNavigableAfterTypingAndOtherKeysRetainTheirNormalBehavior() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          Fixture f = new Fixture(ExamTemplate.SHOULDER);
          f.text.setText("Existing [unrelated] text.\n");
          f.text.setCaretPosition(f.text.getText().length());
          button(f.bar, "Paralabral cyst").doClick(0);
          assertEquals("[size]", f.text.getSelectedText());
          f.text.replaceSelection("9 mm");
          assertFalse(f.key(KeyEvent.VK_1, 0).isConsumed());
          assertFalse(f.key(KeyEvent.VK_TAB, KeyEvent.CTRL_DOWN_MASK).isConsumed());
          assertTrue(f.key(KeyEvent.VK_TAB, 0).isConsumed());
          assertEquals("[location]", f.text.getSelectedText());
          f.text.replaceSelection("superior posterior");
          assertFalse(f.key(KeyEvent.VK_TAB, 0).isConsumed());
          assertEquals(
              "Existing [unrelated] text.\nA 9 mm superior posterior paralabral cyst suggests an occult tear of the adjacent labrum.",
              f.text.getText());

          f.bar.insert(new Phrase("Details", "[side] at [level]."));
          assertEquals("[side]", f.text.getSelectedText());
          assertTrue(f.key(KeyEvent.VK_TAB, 0).isConsumed());
          assertEquals("[level]", f.text.getSelectedText());
          assertTrue(f.key(KeyEvent.VK_TAB, KeyEvent.SHIFT_DOWN_MASK).isConsumed());
          assertEquals("[side]", f.text.getSelectedText());
        });
  }

  @Test
  void examChangesUpdateButtonsAndMoreWithoutChangingExistingText() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          Fixture f = new Fixture(ExamTemplate.KNEE);
          f.text.setText("Existing study text.");
          JMenuItem extra = (JMenuItem) f.bar.createMenu().getComponent(0);
          extra.doClick(0);
          assertTrue(f.text.getText().contains("Mild prepatellar bursitis."));
          String before = f.text.getText();
          f.bar.setExam(ExamTemplate.ANKLE);
          assertEquals(before, f.text.getText());
          button(f.bar, "Post tib tenosyn").doClick(0);
          assertTrue(f.text.getText().contains("Minimal posterior tibial tenosynovitis."));
          extra.doClick(0);
          assertEquals(1, f.text.getText().split("Mild prepatellar bursitis", -1).length - 1);
        });
  }

  @Test
  void staleMenusAndPlaceholderNavigationCannotEditANewStudy() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          Fixture f = new Fixture(ExamTemplate.LUMBAR_SPINE);
          button(f.bar, "Hemangioma").doClick(0);
          assertEquals("[level]", f.text.getSelectedText());
          JMenuItem old = (JMenuItem) f.bar.createMenu().getComponent(0);
          f.bar.setAvailable(false);
          f.text.setEnabled(false);
          f.bar.insert(ReportQuickPhrases.MOTION);
          assertFalse(f.text.getText().contains("motion"));
          f.text.setText("New study instructions.");
          f.text.setEnabled(true);
          f.bar.setExam(ExamTemplate.LUMBAR_SPINE);
          f.bar.setAvailable(true);
          old.doClick(0);
          assertFalse(f.key(KeyEvent.VK_TAB, 0).isConsumed());
          assertEquals("New study instructions.", f.text.getText());
          f.text.setEditable(false);
          button(f.bar, "Motion limit").doClick(0);
          assertEquals("New study instructions.", f.text.getText());
        });
  }

  private static JButton button(Container root, String label) {
    for (Component component : root.getComponents()) {
      if (component instanceof JButton button && button.getText().equals(label)) return button;
      if (component instanceof Container child) {
        JButton match = button(child, label);
        if (match != null) return match;
      }
    }
    return null;
  }

  private static final class Fixture {
    final JTextArea text = new JTextArea();
    final QuickPhraseBar bar = new QuickPhraseBar(text);

    Fixture(ExamTemplate exam) {
      bar.setExam(exam);
      bar.setAvailable(true);
    }

    KeyEvent key(int code, int modifiers) {
      KeyEvent event =
          new KeyEvent(text, KeyEvent.KEY_PRESSED, 0, modifiers, code, KeyEvent.CHAR_UNDEFINED);
      for (var listener : text.getKeyListeners()) listener.keyPressed(event);
      return event;
    }
  }
}
