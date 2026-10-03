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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
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

  @Test
  void managerFiltersByCategoryAndSortsUnsortedShortcutsBySuggestion() throws Exception {
    assumeFalse(GraphicsEnvironment.isHeadless());
    AtomicReference<Throwable> failure = new AtomicReference<>();
    SwingUtilities.invokeAndWait(
        () -> {
          try (var library = new ShortcutLibrary(null)) {
            var tendon =
                library.save(
                    null, ExamTemplate.KNEE.name(), "Patellar", "mild patellar tendinosis", false);
            var unclear =
                library.save(
                    null, ExamTemplate.KNEE.name(), "Unclear", "custom wording to file", false);
            whenDialog(
                "Manage shortcuts",
                failure,
                dialog -> {
                  JTable table = child(dialog, JTable.class);
                  JComboBox<?> categoryFilter = children(dialog, JComboBox.class).get(1);
                  categoryFilter.setSelectedItem("Unsorted");
                  assertEquals(2, table.getRowCount());
                  button(dialog, "Sort 1 unsorted by suggestion").doClick(0);
                  assertEquals(1, table.getRowCount());
                  assertEquals("Unclear", table.getValueAt(0, 0));
                  categoryFilter.setSelectedItem(ShortcutCategory.TENDONS_LIGAMENTS);
                  List<Object> labels = new ArrayList<>();
                  for (int row = 0; row < table.getRowCount(); row++) {
                    labels.add(table.getValueAt(row, 0));
                    assertEquals(
                        ShortcutCategory.TENDONS_LIGAMENTS.label(),
                        String.valueOf(table.getValueAt(row, 2)));
                  }
                  assertTrue(labels.contains("Patellar"));
                  button(dialog, "Close").doClick(0);
                });
            ShortcutDialogs.manage(new JPanel(), library, ExamTemplate.KNEE, () -> {});
            assertEquals("TENDONS_LIGAMENTS", library.find(tendon.id()).orElseThrow().category());
            assertEquals(
                ShortcutCategory.NONE, library.find(unclear.id()).orElseThrow().category());
          }
        });
    assertNull(failure.get(), () -> String.valueOf(failure.get()));
  }

  @Test
  void managerEditsCellsInPlaceAndChangesSelectedRowsTogether() throws Exception {
    assumeFalse(GraphicsEnvironment.isHeadless());
    AtomicReference<Throwable> failure = new AtomicReference<>();
    SwingUtilities.invokeAndWait(
        () -> {
          try (var library = new ShortcutLibrary(null)) {
            String knee = ExamTemplate.KNEE.name();
            var first = library.save(null, knee, "First", "first wording", false);
            var second = library.save(null, knee, "Second", "second wording", false);
            var third = library.save(null, knee, "Third", "third wording", false);
            library.save(null, ExamTemplate.SHOULDER.name(), "Taken", "third wording", false);
            whenDialog(
                "Manage shortcuts",
                failure,
                dialog -> {
                  JTable table = child(dialog, JTable.class);
                  child(dialog, JTextField.class).setText("wording");
                  table.setValueAt(ShortcutCategory.BONE_MARROW, row(table, "First"), 2);
                  table.setValueAt(true, row(table, "First"), 3);
                  table.setValueAt("Renamed", row(table, "First"), 0);
                  var edited = library.find(first.id()).orElseThrow();
                  assertEquals("BONE_MARROW", edited.category());
                  assertTrue(edited.pinned());
                  assertEquals("Renamed", edited.label());

                  int renamed = row(table, "Renamed");
                  table.setRowSelectionInterval(renamed, renamed);
                  JTextArea preview = children(dialog, JTextArea.class).getLast();
                  assertTrue(preview.isEditable());
                  preview.setText("first wording, edited");
                  button(dialog, "Save text").doClick(0);
                  assertEquals(
                      "first wording, edited", library.find(first.id()).orElseThrow().text());

                  table.clearSelection();
                  table.addRowSelectionInterval(row(table, "Second"), row(table, "Second"));
                  table.addRowSelectionInterval(row(table, "Third"), row(table, "Third"));
                  assertFalse(preview.isEditable());
                  choose("Move to category", ShortcutCategory.JOINT_DEGENERATIVE.label(), dialog);
                  assertEquals(
                      "JOINT_DEGENERATIVE", library.find(second.id()).orElseThrow().category());
                  assertEquals(
                      "JOINT_DEGENERATIVE", library.find(third.id()).orElseThrow().category());
                  choose("Move to body part", ExamTemplate.SHOULDER.toString(), dialog);
                  assertEquals(
                      ExamTemplate.SHOULDER.name(),
                      library.find(second.id()).orElseThrow().scope());
                  assertEquals(
                      knee, library.find(third.id()).orElseThrow().scope(), "Duplicate text.");

                  table.clearSelection();
                  table.addRowSelectionInterval(row(table, "Renamed"), row(table, "Renamed"));
                  table.addRowSelectionInterval(row(table, "Third"), row(table, "Third"));
                  whenDialog(
                      "Delete shortcuts",
                      failure,
                      confirmation -> button(confirmation, "Yes").doClick(0));
                  button(dialog, "Delete").doClick(0);
                  assertTrue(library.find(first.id()).isEmpty());
                  assertTrue(library.find(third.id()).isEmpty());
                  button(dialog, "Close").doClick(0);
                });
            ShortcutDialogs.manage(new JPanel(), library, ExamTemplate.KNEE, () -> {});
            assertTrue(library.find(second.id()).isPresent());
          }
        });
    assertNull(failure.get(), () -> String.valueOf(failure.get()));
  }

  /** Opens Change selected… and clicks {@code item} in its {@code submenu}. */
  private static void choose(String submenu, String item, JDialog dialog) {
    button(dialog, "Change selected…").doClick(0);
    MenuElement[] path = MenuSelectionManager.defaultManager().getSelectedPath();
    JPopupMenu menu = (JPopupMenu) path[0];
    MenuSelectionManager.defaultManager().clearSelectedPath();
    for (Component component : menu.getComponents()) {
      if (component instanceof JMenu group && group.getText().equals(submenu)) {
        for (Component option : group.getMenuComponents()) {
          if (option instanceof JMenuItem choice && choice.getText().equals(item)) {
            choice.doClick(0);
            return;
          }
        }
      }
    }
    throw new AssertionError("Missing menu item: " + submenu + " › " + item);
  }

  private static int row(JTable table, String label) {
    for (int row = 0; row < table.getRowCount(); row++) {
      if (label.equals(table.getValueAt(row, 0))) return row;
    }
    throw new AssertionError("Missing row: " + label);
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

  private static <T> List<T> children(Container root, Class<T> type) {
    List<T> found = new ArrayList<>();
    for (Component component : root.getComponents()) {
      if (type.isInstance(component)) found.add(type.cast(component));
      if (component instanceof Container child) found.addAll(children(child, type));
    }
    return found;
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
