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

import com.fasterxml.jackson.databind.JsonNode;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import org.weasis.dicom.reportcomposer.ShoulderFindingBuilder.Degree;
import org.weasis.dicom.reportcomposer.ShoulderFormPanel.ShortcutTarget;

/** Hover-scoped form commands. No bindings are installed on the image viewer or text documents. */
final class ShoulderFormShortcuts implements KeyEventDispatcher {
  static final String COMMON_HELP = "Cmd+Enter Add findings · Cmd+Z Undo field edit";
  static final String IDLE_HELP =
      "Hover over a shoulder tendon, bursa, joint, or labrum.\n"
          + "Numpad: 1 Mild · 2 Moderate · 3 Severe · . Minimal · 0 Clear\n"
          + "T Details/text · Esc Leave text\n"
          + COMMON_HELP;
  private final ShoulderFormPanel form;
  private Component textRoot;
  private final AWTEventListener pointerListener = this::observeInteraction;
  private final Set<Integer> heldKeys = new HashSet<>();
  private final Deque<Edit> undo = new ArrayDeque<>();
  private Consumer<String> help = text -> {};
  private Predicate<KeyEvent> viewerShortcut = event -> false;
  private Predicate<Window> viewerWindow = window -> false;
  private ShortcutTarget hovered;
  private Window pointerWindow;
  private boolean installed;
  private boolean updateQueued;
  private boolean swallowTyped;

  ShoulderFormShortcuts(ShoulderFormPanel form) {
    this.form = form;
    textRoot = form;
  }

  void setTextRoot(Component root) {
    textRoot = root;
  }

  void setHelpListener(Consumer<String> listener) {
    help = listener;
  }

  void setViewerShortcutHandler(Predicate<KeyEvent> handler) {
    viewerShortcut = handler;
  }

  void setViewerWindowPredicate(Predicate<Window> predicate) {
    viewerWindow = predicate;
  }

  void install() {
    if (installed) return;
    installed = true;
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);
    Toolkit.getDefaultToolkit()
        .addAWTEventListener(
            pointerListener,
            AWTEvent.MOUSE_EVENT_MASK
                | AWTEvent.MOUSE_MOTION_EVENT_MASK
                | AWTEvent.MOUSE_WHEEL_EVENT_MASK
                | AWTEvent.FOCUS_EVENT_MASK
                | AWTEvent.WINDOW_EVENT_MASK);
    form.addHierarchyListener(hierarchyListener);
    queueHoverUpdate();
  }

  private final java.awt.event.HierarchyListener hierarchyListener = event -> queueHoverUpdate();

  void uninstall() {
    if (!installed) return;
    installed = false;
    KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(this);
    Toolkit.getDefaultToolkit().removeAWTEventListener(pointerListener);
    form.removeHierarchyListener(hierarchyListener);
    heldKeys.clear();
    swallowTyped = false;
    reset();
  }

  void reset() {
    undo.clear();
    setHovered(null);
  }

  private void observeInteraction(AWTEvent event) {
    if (event instanceof MouseEvent mouse) {
      pointerWindow = SwingUtilities.getWindowAncestor(mouse.getComponent());
    }
    queueHoverUpdate();
  }

  private void queueHoverUpdate() {
    if (updateQueued || !installed) return;
    updateQueued = true;
    SwingUtilities.invokeLater(
        () -> {
          updateQueued = false;
          if (installed) setHovered(activeWindow() ? targetUnderPointer() : null);
        });
  }

  boolean activeWindow() {
    Window owner = SwingUtilities.getWindowAncestor(form);
    Window active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    return form.isShowing()
        && form.isEnabled()
        && owner != null
        && active != null
        && (owner == active || viewerWindow.test(active))
        && MenuSelectionManager.defaultManager().getSelectedPath().length == 0;
  }

  ShortcutTarget targetUnderPointer() {
    // A detached composer can be behind the active viewer at the same screen coordinates.
    // Use the actual mouse-event window as well as coordinates so it cannot edit through it.
    if (pointerWindow != null && pointerWindow != SwingUtilities.getWindowAncestor(form))
      return null;
    var pointer = MouseInfo.getPointerInfo();
    if (pointer == null) return null;
    Point point = new Point(pointer.getLocation());
    SwingUtilities.convertPointFromScreen(point, form);
    return form.targetAt(point);
  }

  void setHovered(ShortcutTarget target) {
    hovered = target;
    form.setKeyboardHovered(target);
    updateHelp();
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
    if (!activeWindow()) {
      setHovered(null);
      return false;
    }
    // Resolve from the current pointer, including after a scroll/layout change without mouse
    // motion.
    setHovered(targetUnderPointer());
    Component focus = focusOwner();
    boolean handled = handleKey(event, focus);
    if (!handled
        && !(focus instanceof JTextComponent)
        && focus != null
        && SwingUtilities.isDescendingFrom(focus, form)) {
      handled = viewerShortcut.test(event);
    }
    if (handled) {
      heldKeys.add(event.getKeyCode());
      swallowTyped = true;
      return consume(event);
    }
    return false;
  }

  Component focusOwner() {
    return KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
  }

  private static boolean consume(KeyEvent event) {
    event.consume();
    return true;
  }

  /** Keyboard rules can be tested separately from the native pointer and window lookup. */
  boolean handleKey(KeyEvent event, Component focus) {
    if (!form.isEnabled()) return false;
    int key = event.getKeyCode();
    int modifiers = event.getModifiersEx();
    if (focus instanceof JTextComponent) {
      if (key == KeyEvent.VK_ESCAPE
          && modifiers == 0
          && SwingUtilities.isDescendingFrom(focus, textRoot)) {
        form.requestFocusInWindow();
        return true;
      }
      return false;
    }
    if (hovered == null) return false;
    if (modifiers == InputEvent.META_DOWN_MASK || modifiers == InputEvent.CTRL_DOWN_MASK) {
      if (key == KeyEvent.VK_Z) {
        undo();
        return true;
      }
      if (key == KeyEvent.VK_ENTER) {
        form.submitFromKeyboard();
        return true;
      }
      return false;
    }
    if (modifiers != 0) return false;
    int digit = SpineFormShortcuts.numpadDigit(event);
    if (digit >= 0) {
      if (!hovered.hasDegree()) return false;
      if (digit <= 3) {
        Degree degree =
            switch (digit) {
              case 1 -> Degree.MILD;
              case 2 -> Degree.MODERATE;
              case 3 -> Degree.SEVERE;
              default -> Degree.NONE;
            };
        edit(() -> hovered.setDegree(degree));
      }
      return true;
    }
    if (isNumpadDecimal(event) && hovered.hasDegree()) {
      edit(() -> hovered.setDegree(Degree.MINIMAL));
      return true;
    }
    if (key == KeyEvent.VK_ESCAPE) return true;
    if (key == KeyEvent.VK_T) {
      if (hovered.textField().isEnabled()) hovered.editText();
      else help.accept(hovered.name() + " — select a tear before entering tear details.");
      return true;
    }
    char letter = key >= KeyEvent.VK_A && key <= KeyEvent.VK_Z ? (char) key : 0;
    var button = hovered.button(letter);
    if (button == null) return false;
    if (button.isEnabled()) edit(() -> button.doClick(0));
    else
      help.accept(
          hovered.name()
              + (letter == 'H'
                  ? " — select a partial/interstitial tear for high-grade."
                  : " — select a tear before adding modifiers."));
    return true;
  }

  static boolean isNumpadDecimal(KeyEvent event) {
    int key = event.getKeyCode();
    return key == KeyEvent.VK_DECIMAL
        || key == KeyEvent.VK_SEPARATOR
        || (event.getKeyLocation() == KeyEvent.KEY_LOCATION_NUMPAD
            && (key == KeyEvent.VK_PERIOD
                || key == KeyEvent.VK_COMMA
                || key == KeyEvent.VK_DELETE));
  }

  private void edit(Runnable action) {
    ShortcutTarget target = hovered;
    JsonNode before = ComposerFormState.capture(target.panel());
    action.run();
    JsonNode after = ComposerFormState.capture(target.panel());
    if (!before.equals(after)) {
      undo.push(new Edit(target, before, after));
      while (undo.size() > 100) undo.removeLast();
    }
  }

  private void undo() {
    if (undo.isEmpty()) return;
    Edit edit = undo.pop();
    // Preserve subsequent mouse and free-text edits to this same section.
    if (!edit.after().equals(ComposerFormState.capture(edit.target().panel()))) {
      undo.clear();
      return;
    }
    ComposerFormState.restore(edit.target().panel(), edit.before());
  }

  private void updateHelp() {
    help.accept(hovered == null ? IDLE_HELP : hovered.help());
  }

  private record Edit(ShortcutTarget target, JsonNode before, JsonNode after) {}
}
