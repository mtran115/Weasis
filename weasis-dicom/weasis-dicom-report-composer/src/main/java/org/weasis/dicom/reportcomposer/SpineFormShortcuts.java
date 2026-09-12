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
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.ProtrusionLocation;
import org.weasis.dicom.reportcomposer.SpineFormPanel.LevelControls;

/** Hover-scoped form commands. No bindings are installed on the image viewer or text documents. */
final class SpineFormShortcuts implements KeyEventDispatcher {
  static final String IDLE_HELP =
      "Hover over a spine level for shortcuts.\n"
          + "B Bulge · P Protrusion · E Extrusion · A Fissure · V Lipomatosis\n"
          + "C/L/R Stenosis · F Facet · H Hypertrophy · D Location\n"
          + "T Text · Cmd+Enter Add findings · Cmd+Z Undo field edit";
  private final SpineFormPanel form;
  private Component textRoot;
  private final AWTEventListener pointerListener = this::observeInteraction;
  private final Set<Integer> heldKeys = new HashSet<>();
  private final Deque<Edit> undo = new ArrayDeque<>();
  private Consumer<String> help = text -> {};
  private Predicate<KeyEvent> viewerShortcut = event -> false;
  private Predicate<Window> viewerWindow = window -> false;
  private LevelControls hovered;
  private Window pointerWindow;
  private char pending;
  private EnumSet<ProtrusionLocation> discChoices;
  private boolean firstDiscChoice;
  private boolean installed;
  private boolean updateQueued;
  private boolean swallowTyped;

  SpineFormShortcuts(SpineFormPanel form) {
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
    cancelPending();
    setHovered(null);
  }

  private void observeInteraction(AWTEvent event) {
    if (event instanceof MouseEvent mouse) {
      pointerWindow = SwingUtilities.getWindowAncestor(mouse.getComponent());
      if (mouse.getID() == MouseEvent.MOUSE_PRESSED) cancelPending();
    }
    queueHoverUpdate();
  }

  private void queueHoverUpdate() {
    if (updateQueued || !installed) return;
    updateQueued = true;
    SwingUtilities.invokeLater(
        () -> {
          updateQueued = false;
          if (installed) setHovered(activeWindow() ? levelUnderPointer() : null);
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

  LevelControls levelUnderPointer() {
    // A detached composer can be behind the active viewer at the same screen coordinates.
    // Use the actual mouse-event window as well as coordinates so it cannot edit through it.
    if (pointerWindow != null && pointerWindow != SwingUtilities.getWindowAncestor(form))
      return null;
    var pointer = MouseInfo.getPointerInfo();
    if (pointer == null) return null;
    Point point = new Point(pointer.getLocation());
    SwingUtilities.convertPointFromScreen(point, form);
    return form.levelAt(point);
  }

  void setHovered(LevelControls level) {
    if (hovered != level) {
      if (hovered != null) hovered.setKeyboardHovered(false);
      hovered = level;
      pending = 0;
      discChoices = null;
      if (hovered != null) hovered.setKeyboardHovered(true);
    }
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
    setHovered(levelUnderPointer());
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

  /**
   * Separated from screen/focus lookup so the command and numpad rules can be tested on the EDT.
   */
  boolean handleKey(KeyEvent event, Component focus) {
    if (!form.isEnabled()) return false;
    int key = event.getKeyCode();
    int modifiers = event.getModifiersEx();
    if (focus instanceof JTextComponent) {
      if (key == KeyEvent.VK_ESCAPE
          && modifiers == 0
          && SwingUtilities.isDescendingFrom(focus, textRoot)) {
        cancelPending();
        form.requestFocusInWindow();
        return true;
      }
      cancelPending();
      return false;
    }
    if (hovered == null) return false;
    if (modifiers == InputEvent.META_DOWN_MASK || modifiers == InputEvent.CTRL_DOWN_MASK) {
      if (key == KeyEvent.VK_Z) {
        cancelPending();
        undo();
        return true;
      }
      if (key == KeyEvent.VK_ENTER) {
        cancelPending();
        form.submitFromKeyboard();
        return true;
      }
      return false;
    }
    if (modifiers != 0) return false;
    // Top-row digits never select, clear, or dismiss a composer choice.
    int digit = numpadDigit(event);
    if (digit >= 0) return applyNumber(digit);
    if (key == KeyEvent.VK_ESCAPE) {
      cancelPending();
      return true;
    }
    if (pending == 'D' && key == KeyEvent.VK_ENTER) {
      if (!discChoices.isEmpty()) edit(() -> hovered.setDiscLocations(discChoices));
      cancelPending();
      return true;
    }
    char letter = key >= KeyEvent.VK_A && key <= KeyEvent.VK_Z ? (char) key : 0;
    if ((pending == 'F' || pending == 'H') && "LRB".indexOf(letter) >= 0 && letter != 0) {
      char target = pending;
      edit(() -> hovered.setLaterality(target, letter));
      cancelPending();
      return true;
    }
    if ("BPEAV".indexOf(letter) >= 0 && letter != 0) {
      cancelPending();
      edit(() -> hovered.toggle(letter));
      return true;
    }
    if ("CLRFHD".indexOf(letter) >= 0 && letter != 0) {
      cancelPending();
      if (letter == 'C' && !hovered.hasCanalControl()) {
        help.accept(hovered.name() + " — this form has no canal stenosis control.");
        return true;
      }
      if (letter == 'D') {
        if (!hovered.hasDiscHerniation()) {
          help.accept(hovered.name() + " — select P Protrusion or E Extrusion first.");
          return true;
        }
        var selected = hovered.selection();
        discChoices = EnumSet.noneOf(ProtrusionLocation.class);
        discChoices.addAll(
            selected.protrusion() ? selected.protrusionLocations() : selected.extrusionLocations());
        firstDiscChoice = true;
      }
      pending = letter;
      updateHelp();
      return true;
    }
    if (letter == 'T') {
      cancelPending();
      hovered.editText();
      return true;
    }
    return false;
  }

  static int numpadDigit(KeyEvent event) {
    int key = event.getKeyCode();
    if (key >= KeyEvent.VK_NUMPAD0 && key <= KeyEvent.VK_NUMPAD9) return key - KeyEvent.VK_NUMPAD0;
    if (event.getKeyLocation() != KeyEvent.KEY_LOCATION_NUMPAD) return -1;
    if (key >= KeyEvent.VK_0 && key <= KeyEvent.VK_9) return key - KeyEvent.VK_0;
    // Some keyboards report navigation key codes when Num Lock is off.
    return switch (key) {
      case KeyEvent.VK_INSERT -> 0;
      case KeyEvent.VK_END -> 1;
      case KeyEvent.VK_DOWN, KeyEvent.VK_KP_DOWN -> 2;
      case KeyEvent.VK_PAGE_DOWN -> 3;
      case KeyEvent.VK_LEFT, KeyEvent.VK_KP_LEFT -> 4;
      case KeyEvent.VK_CLEAR -> 5;
      case KeyEvent.VK_RIGHT, KeyEvent.VK_KP_RIGHT -> 6;
      case KeyEvent.VK_HOME -> 7;
      case KeyEvent.VK_UP, KeyEvent.VK_KP_UP -> 8;
      case KeyEvent.VK_PAGE_UP -> 9;
      default -> -1;
    };
  }

  static boolean isTopRowDigit(KeyEvent event) {
    return event.getKeyCode() >= KeyEvent.VK_0
        && event.getKeyCode() <= KeyEvent.VK_9
        && event.getKeyLocation() != KeyEvent.KEY_LOCATION_NUMPAD;
  }

  private boolean applyNumber(int digit) {
    if (pending == 0) return false;
    if (pending == 'D') {
      if (digit >= 1 && digit <= ProtrusionLocation.values().length) {
        if (firstDiscChoice) discChoices.clear();
        firstDiscChoice = false;
        ProtrusionLocation location = ProtrusionLocation.values()[digit - 1];
        if (!discChoices.add(location)) discChoices.remove(location);
        updateHelp();
      }
    } else if ((pending == 'F' || pending == 'H') && digit == 0) {
      char target = pending;
      edit(() -> hovered.setLaterality(target, '0'));
      cancelPending();
    } else if ("CLR".indexOf(pending) >= 0 && digit <= 3) {
      char target = pending;
      edit(() -> hovered.setSeverity(target, digit));
      cancelPending();
    }
    // Invalid numpad choices stay within the active command, without reaching viewer actions.
    return true;
  }

  void cancelPending() {
    pending = 0;
    discChoices = null;
    updateHelp();
  }

  private void edit(Runnable action) {
    JsonNode before = ComposerFormState.capture(hovered.panel());
    action.run();
    JsonNode after = ComposerFormState.capture(hovered.panel());
    if (!before.equals(after)) {
      undo.push(new Edit(hovered, before, after));
      while (undo.size() > 100) undo.removeLast();
    }
  }

  private void undo() {
    if (undo.isEmpty()) return;
    Edit edit = undo.pop();
    // Do not overwrite intervening mouse or free-text edits in this section.
    if (!edit.after().equals(ComposerFormState.capture(edit.level().panel()))) {
      undo.clear();
      return;
    }
    ComposerFormState.restore(edit.level().panel(), edit.before());
  }

  private void updateHelp() {
    if (hovered == null) {
      help.accept(IDLE_HELP);
      return;
    }
    String prefix = hovered.name() + " — ";
    if (pending == 'D') {
      String selected =
          discChoices.stream()
              .map(location -> Integer.toString(location.ordinal() + 1))
              .collect(Collectors.joining(", "));
      help.accept(
          prefix
              + "disc location (numpad); Enter apply / Esc cancel\n"
              + "1 Central · 2 Broad · 3 L central · 4 R central\n"
              + "5 L subart. · 6 R subart. · 7 L foram. · 8 R foram.\n"
              + "Selected: "
              + selected
              + " · first replaces; next toggles");
    } else if (pending == 'F' || pending == 'H') {
      help.accept(
          prefix
              + (pending == 'F' ? "facet arthrosis" : "posterior-element hypertrophy")
              + "\nL Left · R Right · B Bilateral · Numpad 0 Clear · Esc Cancel");
    } else if (pending != 0) {
      String target =
          switch (pending) {
            case 'C' -> "canal stenosis";
            case 'L' -> "left foraminal stenosis";
            default -> "right foraminal stenosis";
          };
      help.accept(
          prefix + target + "\nNumpad: 1 Mild · 2 Moderate · 3 Severe · 0 Clear\nEsc Cancel");
    } else {
      help.accept(prefix + IDLE_HELP.substring(IDLE_HELP.indexOf('\n') + 1));
    }
  }

  private record Edit(LevelControls level, JsonNode before, JsonNode after) {}
}
