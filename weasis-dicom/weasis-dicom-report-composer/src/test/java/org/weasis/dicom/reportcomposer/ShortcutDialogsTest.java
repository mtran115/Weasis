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
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.ExamTemplate;

class ShortcutDialogsTest {
  @Test
  void addingSelectedTextAndEditingADuplicateKeepTheStudyAndCountsIntact() throws Exception {
    assumeFalse(GraphicsEnvironment.isHeadless());
    AtomicReference<Throwable> failure = new AtomicReference<>();
    SwingUtilities.invokeAndWait(
        () -> {
          try (var library = new ShortcutLibrary(null)) {
            JTextArea source =
                new JTextArea("Before.\nT2 hyperint lesion L4 likely intraoss hemang\nAfter.");
            source.select(8, source.getText().indexOf("\nAfter."));
            String original = source.getText();
            String selection = source.getSelectedText();
            QuickPhraseBar bar = new QuickPhraseBar(source, library);
            bar.setContext(ExamTemplate.LUMBAR_SPINE, "example-study");
            bar.setAvailable(true);
            whenDialog(
                "Add shortcut",
                failure,
                dialog -> {
                  assertEquals(selection, child(dialog, JTextArea.class).getText());
                  assertEquals(
                      ExamTemplate.LUMBAR_SPINE, child(dialog, JComboBox.class).getSelectedItem());
                  child(dialog, JTextField.class).setText("My hemang");
                  button(dialog, "Save").doClick(0);
                });
            button(bar, "Add shortcut").doClick(0);
            assertEquals(original, source.getText());
            assertEquals(selection, source.getSelectedText());
            var entry =
                library.duplicate(ExamTemplate.LUMBAR_SPINE.name(), selection, null).orElseThrow();
            assertEquals("My hemang", entry.label());
            assertTrue(entry.pinned());
            library.recordUse(entry.id(), ExamTemplate.LUMBAR_SPINE);
            int count = library.all().size();
            whenDialog(
                "Add shortcut",
                failure,
                dialog -> {
                  whenDialog(
                      "Existing shortcut",
                      failure,
                      confirmation -> button(confirmation, "Yes").doClick(0));
                  button(dialog, "Save").doClick(0);
                  assertEquals("Edit shortcut", dialog.getTitle());
                  child(dialog, JTextField.class).setText("Edited hemang");
                  child(dialog, JCheckBox.class).setSelected(false);
                  button(dialog, "Save").doClick(0);
                });
            button(bar, "Add shortcut").doClick(0);
            assertEquals(count, library.all().size());
            var edited = library.find(entry.id()).orElseThrow();
            assertEquals("Edited hemang", edited.label());
            assertFalse(edited.pinned());
            assertEquals(1, edited.usesFor(ExamTemplate.LUMBAR_SPINE));
            assertEquals(original, source.getText());
            assertEquals(selection, source.getSelectedText());
          }
        });
    assertNull(failure.get(), () -> String.valueOf(failure.get()));
  }

  @Test
  void managerSearchEditMoveAndDeleteRespectTheBodyPartFilter() throws Exception {
    assumeFalse(GraphicsEnvironment.isHeadless());
    AtomicReference<Throwable> failure = new AtomicReference<>();
    SwingUtilities.invokeAndWait(
        () -> {
          try (var library = new ShortcutLibrary(null)) {
            var entry =
                library.save(null, ExamTemplate.KNEE.name(), "Probe", "probe wording knee", true);
            var other =
                library.save(
                    null, ExamTemplate.SHOULDER.name(), "Other", "other shoulder text", false);
            whenDialog(
                "Manage shortcuts",
                failure,
                dialog -> {
                  JTable table = child(dialog, JTable.class);
                  child(dialog, JTextField.class).setText("probe wording knee");
                  assertEquals(1, table.getRowCount());
                  table.setRowSelectionInterval(0, 0);
                  whenDialog(
                      "Edit shortcut",
                      failure,
                      editor -> {
                        child(editor, JComboBox.class).setSelectedItem(ExamTemplate.SHOULDER);
                        child(editor, JTextField.class).setText("Moved probe");
                        button(editor, "Save").doClick(0);
                      });
                  button(dialog, "Edit…").doClick(0);
                  assertEquals(0, table.getRowCount());
                  child(dialog, JComboBox.class).setSelectedItem(ExamTemplate.SHOULDER);
                  assertEquals(1, table.getRowCount());
                  assertEquals("Moved probe", table.getValueAt(0, 0));
                  table.setRowSelectionInterval(0, 0);
                  whenDialog(
                      "Delete shortcut",
                      failure,
                      confirmation -> button(confirmation, "Yes").doClick(0));
                  button(dialog, "Delete").doClick(0);
                  assertEquals(0, table.getRowCount());
                  child(dialog, JTextField.class).setText("");
                  child(dialog, JComboBox.class).setSelectedItem("All body parts");
                  assertEquals(library.all().size(), table.getRowCount());
                  button(dialog, "Close").doClick(0);
                });
            ShortcutDialogs.manage(new JPanel(), library, ExamTemplate.KNEE, () -> {});
            assertTrue(library.find(entry.id()).isEmpty());
            assertTrue(library.find(other.id()).isPresent());
          }
        });
    assertNull(failure.get(), () -> String.valueOf(failure.get()));
  }

  private static void whenDialog(
      String title, AtomicReference<Throwable> failure, Consumer<JDialog> action) {
    int[] attempts = {0};
    Timer timer = new Timer(50, null);
    timer.addActionListener(
        event -> {
          try {
            for (Window window : Window.getWindows()) {
              if (window instanceof JDialog dialog
                  && dialog.isShowing()
                  && title.equals(dialog.getTitle())) {
                timer.stop();
                action.accept(dialog);
                return;
              }
            }
            if (++attempts[0] > 100) throw new AssertionError("Dialog did not open: " + title);
          } catch (Throwable error) {
            timer.stop();
            failure.compareAndSet(null, error);
            for (Window window : Window.getWindows()) window.dispose();
          }
        });
    timer.start();
  }

  private static JButton button(Container root, String label) {
    for (Component component : root.getComponents()) {
      if (component instanceof JButton button && button.getText().equals(label)) return button;
      if (component instanceof Container child) {
        JButton found = button(child, label);
        if (found != null) return found;
      }
    }
    return null;
  }

  private static <T> T child(Container root, Class<T> type) {
    for (Component component : root.getComponents()) {
      if (type.isInstance(component)) return type.cast(component);
      if (component instanceof Container child) {
        T found = child(child, type);
        if (found != null) return found;
      }
    }
    return null;
  }
}
