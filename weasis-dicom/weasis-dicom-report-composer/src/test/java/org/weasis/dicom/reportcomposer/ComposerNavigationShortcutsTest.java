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

import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JPanel;
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
  void defaultBindingsOnlyReuseUpForTheRequestedFocusShortcut() {
    for (Command command : Command.values()) {
      var entry = shortcuts.getEntry(command.id);
      assertNotNull(entry);
      assertNotEquals(0, entry.getKeyCode());
      assertFalse(entry.getDescription().startsWith("!"));
      assertEquals(
          command == Command.FOCUS_PREVIEW
              ? java.util.List.of(ShortcutManager.ID_VIEWER_SCROLL_UP)
              : java.util.List.of(),
          shortcuts.findConflicts(command.id, entry.getKeyCode(), entry.getModifier()).stream()
              .map(conflict -> conflict.getId())
              .toList(),
          command.description);
    }
  }

  @Test
  void upActivatesPreviewWithoutExportingAndTheNextExportShortcutWorksAfterFocusMoves()
      throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          Fixture f = new Fixture();
          f.focus.set(f.viewer);
          KeyEvent up = f.key(Command.FOCUS_PREVIEW, KeyEvent.KEY_PRESSED);
          assertEquals(KeyEvent.VK_UP, up.getKeyCode());
          assertEquals(0, up.getModifiersEx());
          assertTrue(shortcuts.matches(ShortcutManager.ID_VIEWER_SCROLL_UP, up));
          assertTrue(f.dispatcher.dispatchKeyEvent(up));
          assertTrue(up.isConsumed());
          assertEquals(2, f.views.selectedView());
          assertEquals(1, f.activations.get());
          assertSame(f.export, f.dispatcher.focusOwner());
          assertEquals(0, f.exports.get());
          // The held key remains consumed even after focus crosses into the composer window.
          assertTrue(
              f.dispatcher.dispatchKeyEvent(f.key(Command.FOCUS_PREVIEW, KeyEvent.KEY_PRESSED)));
          assertEquals(1, f.activations.get());
          assertTrue(
              f.dispatcher.dispatchKeyEvent(f.key(Command.FOCUS_PREVIEW, KeyEvent.KEY_RELEASED)));
          // A customized plain-letter export shortcut must work from the new non-text focus.
          var exportBinding = shortcuts.getEntry(Command.EXPORT.id);
          exportBinding.setKeyCode(KeyEvent.VK_E);
          exportBinding.setModifier(0);
          f.tap(Command.EXPORT);
          assertEquals(1, f.exports.get());
          f.tap(Command.FOCUS_PREVIEW);
          assertEquals(2, f.activations.get());
        });
  }

  @Test
  void focusShortcutCanBeReboundAndStillRespectsEditingAndInactiveContexts() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          Fixture f = new Fixture();
          KeyEvent up = f.key(Command.FOCUS_PREVIEW, KeyEvent.KEY_PRESSED);
          assertFalse(f.dispatcher.dispatchKeyEvent(up));
          assertEquals(0, f.activations.get());
          var binding = shortcuts.getEntry(Command.FOCUS_PREVIEW.id);
          binding.setKeyCode(KeyEvent.VK_F8);
          f.dispatcher.updateTooltips();
          assertTrue(f.views.toolTip(2).contains("F8"));
          f.focus.set(f.viewer);
          assertFalse(f.dispatcher.dispatchKeyEvent(up));
          f.tap(Command.FOCUS_PREVIEW);
          assertEquals(1, f.activations.get());
          doReturn(false).when(f.dispatcher).activeContext(any(), any());
          assertFalse(
              f.dispatcher.dispatchKeyEvent(f.key(Command.FOCUS_PREVIEW, KeyEvent.KEY_PRESSED)));
          assertEquals(1, f.activations.get());
        });
  }

  @Test
  void tabCommandsUseExistingChangeListenersAndPreserveText() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          Fixture f = new Fixture();
          f.text.setText("Draft shorthand");
          AtomicInteger changes = new AtomicInteger();
          f.views.addVisibilityListener(changes::incrementAndGet);
          f.tap(Command.KEY_IMAGES);
          assertEquals(1, f.views.selectedView());
          f.tap(Command.PREVIEW);
          assertEquals(2, f.views.selectedView());
          f.tap(Command.COMPOSE);
          assertEquals(0, f.views.selectedView());
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
                assertEquals(2, f.views.selectedView());
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
          assertEquals(0, f.views.selectedView());
          f.views.setEnabledAt(1, false);
          f.tap(Command.KEY_IMAGES);
          assertEquals(0, f.views.selectedView());
          f.focus.set(f.viewer);
          f.views.setEnabledAt(2, false);
          f.tap(Command.FOCUS_PREVIEW);
          assertEquals(0, f.activations.get());
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
                KeyEvent.VK_UP,
                KeyEvent.VK_DOWN,
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
          assertEquals(1, f.views.selectedView());
          doReturn(false).when(f.dispatcher).activeContext(any(), any());
          assertFalse(f.dispatcher.dispatchKeyEvent(f.key(Command.PREVIEW, KeyEvent.KEY_PRESSED)));
          assertFalse(f.dispatcher.dispatchKeyEvent(f.key(Command.EXPORT, KeyEvent.KEY_PRESSED)));
          assertEquals(1, f.views.selectedView());
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
          assertTrue(f.views.toolTip(2).contains("F9"));
          assertFalse(f.dispatcher.dispatchKeyEvent(old));
          assertFalse(f.dispatcher.dispatchKeyEvent(f.key(Command.PREVIEW, KeyEvent.KEY_PRESSED)));
          doReturn(f.viewer).when(f.dispatcher).focusOwner();
          f.tap(Command.PREVIEW);
          assertEquals(2, f.views.selectedView());
          entry.setKeyCode(0);
          f.dispatcher.updateTooltips();
          assertTrue(f.views.toolTip(2).startsWith("Preview | Activate composer"));
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
    final ComposerViewLayout views;
    final JTextField text = new JTextField();
    final JButton export = new JButton("Export Instruction Packet");
    final AtomicInteger exports = new AtomicInteger();
    final AtomicInteger activations = new AtomicInteger();
    final AtomicReference<Component> focus = new AtomicReference<>(text);
    final ComposerNavigationShortcuts dispatcher;

    Fixture() {
      JPanel compose = new JPanel();
      compose.add(text);
      JPanel preview = new JPanel();
      preview.add(export);
      views =
          new ComposerViewLayout(
              List.of("Compose", "Key Images", "Preview"), List.of(compose, new JPanel(), preview));
      composer.add(views.component());
      export.addActionListener(event -> exports.incrementAndGet());
      dispatcher =
          spy(
              new ComposerNavigationShortcuts(
                  composer,
                  views,
                  export,
                  component -> component == viewer,
                  () -> {},
                  () -> {
                    assertEquals(2, views.selectedView());
                    activations.incrementAndGet();
                    focus.set(export);
                  }));
      doReturn(true).when(dispatcher).activeContext(any(), any());
      doAnswer(ignored -> focus.get()).when(dispatcher).focusOwner();
      dispatcher.updateTooltips();
    }

    KeyEvent key(Command command, int eventId) {
      var entry = ShortcutManager.getInstance().getEntry(command.id);
      return new KeyEvent(
          focus.get(),
          eventId,
          0,
          entry.getModifier(),
          entry.getKeyCode(),
          KeyEvent.CHAR_UNDEFINED);
    }

    void tap(Command command) {
      assertTrue(dispatcher.dispatchKeyEvent(key(command, KeyEvent.KEY_PRESSED)));
      assertTrue(dispatcher.dispatchKeyEvent(key(command, KeyEvent.KEY_RELEASED)));
    }
  }
}
