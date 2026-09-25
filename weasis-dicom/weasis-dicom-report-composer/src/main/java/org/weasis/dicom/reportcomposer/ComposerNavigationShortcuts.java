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

import java.awt.Component;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import org.weasis.core.api.gui.util.ShortcutManager;

/** Composer navigation works from its controls or the associated viewer, without a hover target. */
final class ComposerNavigationShortcuts implements KeyEventDispatcher {
  enum Command {
    COMPOSE(ShortcutManager.ID_REPORT_COMPOSER_COMPOSE, 0, "Compose"),
    KEY_IMAGES(ShortcutManager.ID_REPORT_COMPOSER_KEY_IMAGES, 1, "Key Images"),
    PREVIEW(ShortcutManager.ID_REPORT_COMPOSER_PREVIEW, 2, "Preview"),
    FOCUS_PREVIEW(
        ShortcutManager.ID_REPORT_COMPOSER_FOCUS_PREVIEW, 2, "Activate composer and open Preview"),
    EXPORT(ShortcutManager.ID_REPORT_COMPOSER_EXPORT, 2, "Export packet / finish normal case");

    final String id;
    final int tabIndex;
    final String description;

    Command(String id, int tabIndex, String description) {
      this.id = id;
      this.tabIndex = tabIndex;
      this.description = description;
    }
  }

  private final JComponent composer;
  private final ComposerViewLayout views;
  private final JButton exportButton;
  private final Predicate<Component> viewerFocus;
  private final Runnable beforeNavigation;
  private final Runnable activateComposer;
  private final ShortcutManager shortcuts = ShortcutManager.getInstance();
  private final Set<Integer> heldKeys = new HashSet<>();
  private final PropertyChangeListener shortcutListener =
      event -> SwingUtilities.invokeLater(this::updateTooltips);
  private boolean installed;
  private boolean swallowTyped;

  ComposerNavigationShortcuts(
      JComponent composer,
      ComposerViewLayout views,
      JButton exportButton,
      Predicate<Component> viewerFocus,
      Runnable beforeNavigation,
      Runnable activateComposer) {
    this.composer = composer;
    this.views = views;
    this.exportButton = exportButton;
    this.viewerFocus = viewerFocus;
    this.beforeNavigation = beforeNavigation;
    this.activateComposer = activateComposer;
  }

  void install() {
    if (installed) return;
    installed = true;
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);
    shortcuts.addPropertyChangeListener(shortcutListener);
    updateTooltips();
  }

  void uninstall() {
    if (!installed) return;
    installed = false;
    KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(this);
    shortcuts.removePropertyChangeListener(shortcutListener);
    heldKeys.clear();
    swallowTyped = false;
  }

  void updateTooltips() {
    for (Command command : Command.values()) {
      if (command == Command.FOCUS_PREVIEW) continue;
      String tooltip = tooltip(command);
      if (command == Command.PREVIEW) tooltip += " | " + tooltip(Command.FOCUS_PREVIEW);
      if (command == Command.EXPORT) exportButton.setToolTipText(tooltip);
      else views.setToolTip(command.tabIndex, tooltip);
    }
  }

  private String tooltip(Command command) {
    var entry = shortcuts.getEntry(command.id);
    String binding = entry == null ? "" : entry.getShortcutText().replace("Meta", "Cmd");
    return command.description + (binding.isEmpty() ? "" : " — " + binding);
  }

  boolean acceptsFocus(Component focus) {
    return focus != null
        && (SwingUtilities.isDescendingFrom(focus, composer) || viewerFocus.test(focus));
  }

  boolean activeContext(Component focus, Command command) {
    Window active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    return (composer.isShowing() || (command == Command.FOCUS_PREVIEW && composer.isDisplayable()))
        && composer.isEnabled()
        && views.component().isEnabled()
        && acceptsFocus(focus)
        && active != null
        && active == SwingUtilities.getWindowAncestor(focus)
        && MenuSelectionManager.defaultManager().getSelectedPath().length == 0;
  }

  Component focusOwner() {
    return KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    if (event.isConsumed()) return false;
    if (event.getID() == KeyEvent.KEY_RELEASED) {
      swallowTyped = false;
      return heldKeys.remove(event.getKeyCode()) && consume(event);
    }
    if (event.getID() == KeyEvent.KEY_TYPED) {
      if (!swallowTyped) return false;
      swallowTyped = false;
      return consume(event);
    }
    if (event.getID() != KeyEvent.KEY_PRESSED) return false;
    if (heldKeys.contains(event.getKeyCode())) {
      swallowTyped = true;
      return consume(event);
    }
    swallowTyped = false;
    Component focus = focusOwner();
    // Custom unmodified bindings must still leave ordinary typing and Option characters alone.
    if (focus instanceof JTextComponent
        && (event.getModifiersEx() & (InputEvent.META_DOWN_MASK | InputEvent.CTRL_DOWN_MASK)) == 0)
      return false;
    for (Command command : Command.values()) {
      if (shortcuts.matches(command.id, event)) {
        if (!activeContext(focus, command)) return false;
        // Record the press before an export can open a modal folder chooser or result dialog.
        heldKeys.add(event.getKeyCode());
        swallowTyped = true;
        consume(event);
        if (views.isEnabledAt(command.tabIndex)
            && (command != Command.EXPORT || exportButton.isEnabled())) {
          // Explicitly opening the already-selected view also ends a temporary capture preview.
          beforeNavigation.run();
          views.show(command.tabIndex);
          if (command == Command.FOCUS_PREVIEW) activateComposer.run();
          if (command == Command.EXPORT) exportButton.doClick(0);
        }
        return true;
      }
    }
    return false;
  }

  private static boolean consume(KeyEvent event) {
    event.consume();
    return true;
  }
}
