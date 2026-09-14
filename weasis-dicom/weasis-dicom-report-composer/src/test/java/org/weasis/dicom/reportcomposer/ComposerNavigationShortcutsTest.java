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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.gui.util.ShortcutManager;
import org.weasis.dicom.reportcomposer.ComposerNavigationShortcuts.Command;

class ComposerNavigationShortcutsTest {
  private final ShortcutManager shortcuts = ShortcutManager.getInstance();

  @BeforeEach
  @AfterEach
  void defaultBindings() {
    shortcuts.registerDefaults();
  }

  @Test
  void defaultBindingsHaveNoViewerConflicts() {
    for (Command command : Command.values()) {
      var entry = shortcuts.getEntry(command.id);
      assertNotNull(entry);
      assertNotEquals(0, entry.getKeyCode());
      assertFalse(entry.getDescription().startsWith("!"));
      assertEquals(
          java.util.List.of(),
          shortcuts.findConflicts(command.id, entry.getKeyCode(), entry.getModifier()),
          command.description);
    }
  }

  @Test
  void tabCommandsUseExistingChangeListenersAndPreserveText() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          Fixture f = new Fixture();
          f.text.setText("Draft shorthand");
          AtomicInteger changes = new AtomicInteger();
          f.tabs.addChangeListener(event -> changes.incrementAndGet());
          f.tap(Command.KEY_IMAGES);
          assertEquals(1, f.tabs.getSelectedIndex());
          f.tap(Command.PREVIEW);
          assertEquals(2, f.tabs.getSelectedIndex());
          f.tap(Command.COMPOSE);
          assertEquals(0, f.tabs.getSelectedIndex());
          f.tap(Command.COMPOSE);
          assertEquals(3, changes.get());
          assertEquals("Draft shorthand", f.text.getText());
          assertEquals(0, f.exports.get());
        });
  }

  @Test
  void exportOpensPreviewAndInvokesButtonOncePerPressIncludingReentrantEvents() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          Fixture f = new Fixture();
          f.export.addActionListener(
              event -> {
                assertEquals(2, f.tabs.getSelectedIndex());
                // A modal folder chooser can pump another key press before this action returns.
                assertTrue(
                    f.dispatcher.dispatchKeyEvent(f.key(Command.EXPORT, KeyEvent.KEY_PRESSED)));
              });
          assertTrue(f.dispatcher.dispatchKeyEvent(f.key(Command.EXPORT, KeyEvent.KEY_PRESSED)));
          assertTrue(f.dispatcher.dispatchKeyEvent(f.key(Command.EXPORT, KeyEvent.KEY_PRESSED)));
          assertEquals(1, f.exports.get());
          assertTrue(
              f.dispatcher.dispatchKeyEvent(
                  new KeyEvent(f.text, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, 'E')));
          assertTrue(f.dispatcher.dispatchKeyEvent(f.key(Command.EXPORT, KeyEvent.KEY_RELEASED)));
          f.tap(Command.EXPORT);
          assertEquals(2, f.exports.get());
        });
  }

  @Test
  void disabledExportAndDisabledTabsDoNotRunActions() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          Fixture f = new Fixture();
          f.export.setEnabled(false);
          f.tap(Command.EXPORT);
          assertEquals(0, f.exports.get());
          assertEquals(0, f.tabs.getSelectedIndex());
          f.tabs.setEnabledAt(1, false);
          f.tap(Command.KEY_IMAGES);
          assertEquals(0, f.tabs.getSelectedIndex());
        });
  }

  @Test
  void typingCopyPasteAndViewerKeysAreNotConsumed() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          Fixture f = new Fixture();
          for (int code :
              new int[] {
                KeyEvent.VK_C,
                KeyEvent.VK_K,
                KeyEvent.VK_P,
                KeyEvent.VK_E,
                KeyEvent.VK_1,
                KeyEvent.VK_NUMPAD1,
                KeyEvent.VK_LEFT,
                KeyEvent.VK_RIGHT,
                KeyEvent.VK_ENTER
              }) {
            assertFalse(
                f.dispatcher.dispatchKeyEvent(
                    new KeyEvent(
                        f.text, KeyEvent.KEY_PRESSED, 0, 0, code, KeyEvent.CHAR_UNDEFINED)));
          }
          for (int modifier : new int[] {InputEvent.META_DOWN_MASK, InputEvent.CTRL_DOWN_MASK}) {
            for (int code :
                new int[] {KeyEvent.VK_C, KeyEvent.VK_V, KeyEvent.VK_X, KeyEvent.VK_Z}) {
              assertFalse(
                  f.dispatcher.dispatchKeyEvent(
                      new KeyEvent(
                          f.text,
                          KeyEvent.KEY_PRESSED,
                          0,
                          modifier,
                          code,
                          KeyEvent.CHAR_UNDEFINED)));
            }
          }
          assertEquals(0, f.exports.get());
        });
  }

  @Test
  void onlyComposerAndAssociatedViewerFocusQualifyAndInactiveContextBlocksActions()
      throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          Fixture f = new Fixture();
          assertTrue(f.dispatcher.acceptsFocus(f.text));
          assertTrue(f.dispatcher.acceptsFocus(f.viewer));
          assertFalse(f.dispatcher.acceptsFocus(new JTextField()));
          assertFalse(f.dispatcher.acceptsFocus(new JPanel()));
          assertFalse(f.dispatcher.acceptsFocus(null));
          doReturn(f.viewer).when(f.dispatcher).focusOwner();
          f.tap(Command.KEY_IMAGES);
          assertEquals(1, f.tabs.getSelectedIndex());
          doReturn(false).when(f.dispatcher).activeContext(any());
          assertFalse(f.dispatcher.dispatchKeyEvent(f.key(Command.PREVIEW, KeyEvent.KEY_PRESSED)));
          assertFalse(f.dispatcher.dispatchKeyEvent(f.key(Command.EXPORT, KeyEvent.KEY_PRESSED)));
          assertEquals(1, f.tabs.getSelectedIndex());
          assertEquals(0, f.exports.get());
        });
  }

  @Test
  void customizationUpdatesBindingsAndHintsWithoutTakingPlainTextInput() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          Fixture f = new Fixture();
          KeyEvent old = f.key(Command.PREVIEW, KeyEvent.KEY_PRESSED);
          var entry = shortcuts.getEntry(Command.PREVIEW.id);
          entry.setKeyCode(KeyEvent.VK_F9);
          entry.setModifier(0);
          f.dispatcher.updateTooltips();
          assertTrue(f.tabs.getToolTipTextAt(2).contains("F9"));
          assertFalse(f.dispatcher.dispatchKeyEvent(old));
          assertFalse(f.dispatcher.dispatchKeyEvent(f.key(Command.PREVIEW, KeyEvent.KEY_PRESSED)));
          doReturn(f.viewer).when(f.dispatcher).focusOwner();
          f.tap(Command.PREVIEW);
          assertEquals(2, f.tabs.getSelectedIndex());
          entry.setKeyCode(0);
          f.dispatcher.updateTooltips();
          assertEquals("Preview", f.tabs.getToolTipTextAt(2));
          assertFalse(f.dispatcher.dispatchKeyEvent(old));
          assertTrue(f.export.getToolTipText().contains("Export packet"));
        });
  }

  @Test
  void uninstallClearsHeldKeysForDockingAndReopening() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          Fixture f = new Fixture();
          f.dispatcher.install();
          f.dispatcher.install();
          try {
            assertTrue(f.dispatcher.dispatchKeyEvent(f.key(Command.EXPORT, KeyEvent.KEY_PRESSED)));
            f.dispatcher.uninstall();
            f.dispatcher.install();
            f.tap(Command.EXPORT);
            assertEquals(2, f.exports.get());
          } finally {
            f.dispatcher.uninstall();
          }
        });
  }

  private static class Fixture {
    final JPanel composer = new JPanel();
    final JPanel viewer = new JPanel();
    final JTabbedPane tabs = new JTabbedPane();
    final JTextField text = new JTextField();
    final JButton export = new JButton("Export Instruction Packet");
    final AtomicInteger exports = new AtomicInteger();
    final ComposerNavigationShortcuts dispatcher;

    Fixture() {
      JPanel compose = new JPanel();
      compose.add(text);
      tabs.addTab("Compose", compose);
      tabs.addTab("Key Images", new JPanel());
      JPanel preview = new JPanel();
      preview.add(export);
      tabs.addTab("Preview", preview);
      composer.add(tabs);
      export.addActionListener(event -> exports.incrementAndGet());
      dispatcher =
          spy(new ComposerNavigationShortcuts(composer, tabs, export, focus -> focus == viewer));
      doReturn(true).when(dispatcher).activeContext(any());
      doReturn(text).when(dispatcher).focusOwner();
      dispatcher.updateTooltips();
    }

    KeyEvent key(Command command, int eventId) {
      var entry = ShortcutManager.getInstance().getEntry(command.id);
      return new KeyEvent(
          text, eventId, 0, entry.getModifier(), entry.getKeyCode(), KeyEvent.CHAR_UNDEFINED);
    }

    void tap(Command command) {
      assertTrue(dispatcher.dispatchKeyEvent(key(command, KeyEvent.KEY_PRESSED)));
      assertTrue(dispatcher.dispatchKeyEvent(key(command, KeyEvent.KEY_RELEASED)));
    }
  }
}
