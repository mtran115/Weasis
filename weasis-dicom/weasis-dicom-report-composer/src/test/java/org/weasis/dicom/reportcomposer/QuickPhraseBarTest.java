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
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
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
          menuItem(f.bar, "Motion limit").doClick(0);
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
          menuItem(f.bar, "Motion limit").doClick(0);
          assertEquals("New study instructions.", f.text.getText());
        });
  }

  @Test
  void usagePromotesPhrasesOnlyWhenTheStudyChangesAndPinsRemainFirst() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          Fixture f = new Fixture(ExamTemplate.KNEE);
          f.bar.setContext(ExamTemplate.KNEE, "study-a");
          String first = button(f.bar, "Prepatellar edema").getText();
          menuItem(f.bar, "Popliteal cyst").doClick(0);
          menuItem(f.bar, "Popliteal cyst").doClick(0);
          assertEquals(first, button(f.bar, "Prepatellar edema").getText());
          assertNull(button(f.bar, "Popliteal cyst"));
          f.bar.setContext(ExamTemplate.KNEE, "study-a");
          assertNull(button(f.bar, "Popliteal cyst"));
          var used =
              f.library
                  .duplicate(ExamTemplate.KNEE.name(), "Small popliteal cyst.", null)
                  .orElseThrow();
          assertEquals(2, used.usesFor(ExamTemplate.KNEE));
          f.bar.setContext(ExamTemplate.KNEE, "study-b");
          assertNotNull(button(f.bar, "Popliteal cyst"));
          assertEquals(used.id(), f.library.ranked(ExamTemplate.KNEE).getFirst().id());
          var pinned =
              f.library.save(
                  null, ExamTemplate.KNEE.name(), "Custom", "exact shorthand [side]", true);
          f.bar.setContext(ExamTemplate.KNEE, "study-c");
          assertEquals(pinned.id(), f.library.ranked(ExamTemplate.KNEE).getFirst().id());
          button(f.bar, "★ Custom").doClick(0);
          assertEquals("[side]", f.text.getSelectedText());
          assertEquals(1, f.library.find(pinned.id()).orElseThrow().usesFor(ExamTemplate.KNEE));
        });
  }

  @Test
  void disabledAndStaleActionsDoNotCountOrInsert() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          Fixture f = new Fixture(ExamTemplate.KNEE);
          JMenuItem old = menuItem(f.bar, "Motion limit");
          f.bar.setContext(ExamTemplate.SHOULDER, "different-study");
          old.doClick(0);
          assertEquals("", f.text.getText());
          assertEquals(0, f.library.find("default-ALL-0").orElseThrow().totalUses());
          f.text.setEditable(false);
          menuItem(f.bar, "Motion limit").doClick(0);
          assertEquals(0, f.library.find("default-ALL-0").orElseThrow().totalUses());
        });
  }

  @Test
  void addSelectionRequiresNonblankEditableTextAndMenusDoNotChangeFormState() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          Fixture f = new Fixture(ExamTemplate.LUMBAR_SPINE);
          JButton add = button(f.bar, "Add shortcut");
          assertFalse(add.isEnabled());
          f.text.setText("T2 hyperint lesion L4 likely intraoss hemang");
          f.text.selectAll();
          assertTrue(add.isEnabled());
          assertFalse(add.isFocusable());
          String selected = f.text.getSelectedText();
          f.text.setEditable(false);
          assertFalse(add.isEnabled());
          f.text.setEditable(true);
          assertEquals(selected, f.text.getSelectedText());
          JPanel form = new JPanel();
          JTextField field = new JTextField("selected shorthand");
          JTextArea readonly = new JTextArea("preview");
          readonly.setEditable(false);
          JSpinner number = new JSpinner();
          form.add(field);
          form.add(readonly);
          form.add(number);
          var before = ComposerFormState.capture(form);
          f.bar.installTextMenus(form);
          f.bar.installTextMenus(form);
          assertNull(readonly.getComponentPopupMenu());
          assertNull(
              ((JSpinner.DefaultEditor) number.getEditor()).getTextField().getComponentPopupMenu());
          var menu = field.getComponentPopupMenu();
          assertNotNull(menu);
          assertEquals(6, menu.getComponentCount());
          field.selectAll();
          var event = new PopupMenuEvent(menu);
          for (var listener : menu.getPopupMenuListeners())
            listener.popupMenuWillBecomeVisible(event);
          JMenuItem save = (JMenuItem) menu.getComponent(menu.getComponentCount() - 1);
          assertTrue(save.isEnabled());
          f.bar.setContext(ExamTemplate.KNEE, "new-study");
          // An old menu cannot open a save dialog under the new exam category.
          save.doClick(0);
          assertEquals(before, ComposerFormState.capture(form));
          assertEquals(selected, f.text.getSelectedText());
        });
  }

  private static JMenuItem menuItem(QuickPhraseBar bar, String label) {
    for (Component component : bar.createMenu().getComponents()) {
      if (component instanceof JMenuItem item && item.getText().equals(label)) return item;
    }
    throw new AssertionError("Missing menu item: " + label);
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
    final ShortcutLibrary library = new ShortcutLibrary(null);
    final QuickPhraseBar bar = new QuickPhraseBar(text, library);

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
