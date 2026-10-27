/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import java.awt.Component;
import java.awt.KeyEventDispatcher;
import java.awt.KeyEventPostProcessor;
import java.awt.KeyboardFocusManager;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Viewer shortcuts only reach the focused image canvas. When focus rests on a part of the viewer
 * window that ignores keys (a toolbar button, a docking tab, or nothing after switching apps), an
 * unmodified key press moves focus to the selected image and is replayed there. When another window
 * is active (e.g. a detached report composer) and nothing there used the key, a key pressed with
 * the pointer over the selected image is delivered to it without raising its window.
 */
public final class ViewerKeyFallback implements KeyEventDispatcher, KeyEventPostProcessor {
  /** Client property marking a panel that handles its own keys, e.g. a docked tool. */
  public static final String HANDLES_OWN_KEYS = "ViewerKeyFallback.handlesOwnKeys";

  private static final Logger LOGGER = LoggerFactory.getLogger(ViewerKeyFallback.class);
  private static final List<Class<?>> KEY_CONSUMERS =
      List.of(
          JTextComponent.class,
          JList.class,
          JTable.class,
          JTree.class,
          JComboBox.class,
          JSpinner.class,
          JSlider.class);
  private static final int COMMAND_MODIFIERS =
      InputEvent.META_DOWN_MASK | InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK;

  private final Supplier<? extends ViewCanvas<?>> selectedView;
  private KeyEvent replayed;

  public ViewerKeyFallback(Supplier<? extends ViewCanvas<?>> selectedView) {
    this.selectedView = selectedView;
  }

  public void install() {
    KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    focusManager.addKeyEventDispatcher(this);
    focusManager.addKeyEventPostProcessor(this);
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    if (event.getID() != KeyEvent.KEY_PRESSED || event.isConsumed() || event == replayed) {
      return false;
    }
    ViewCanvas<?> view = selectedView.get();
    if (view == null) {
      return false;
    }
    JComponent canvas = view.getJComponent();
    KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    Component owner = focusManager.getFocusOwner();
    Window focusedWindow = focusManager.getFocusedWindow();
    boolean canvasInFocusedWindow =
        canvas.isShowing()
            && focusedWindow != null
            && SwingUtilities.getWindowAncestor(canvas) == focusedWindow;
    boolean ownerInFocusedWindow =
        owner == null || SwingUtilities.getWindowAncestor(owner) == focusedWindow;
    boolean menuOpen = MenuSelectionManager.defaultManager().getSelectedPath().length > 0;
    if (!redirects(event, owner, canvasInFocusedWindow && ownerInFocusedWindow && !menuOpen)) {
      return false;
    }
    LOGGER.info(
        "Viewer shortcut redirected to the image; focus was on {}",
        owner == null ? "no component" : owner.getClass().getName());
    canvas.requestFocusInWindow();
    // Replay after the focus change so every key handler sees the canvas as the focus owner.
    replayed =
        new KeyEvent(
            canvas,
            KeyEvent.KEY_PRESSED,
            event.getWhen(),
            event.getModifiersEx(),
            event.getKeyCode(),
            event.getKeyChar(),
            event.getKeyLocation());
    Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(replayed);
    event.consume();
    return true;
  }

  /** Runs after every dispatcher and the focus owner, so only keys nobody used are delivered. */
  @Override
  public boolean postProcessKeyEvent(KeyEvent event) {
    if (event.getID() != KeyEvent.KEY_PRESSED || event.isConsumed()) {
      return false;
    }
    ViewCanvas<?> view = selectedView.get();
    if (view == null) {
      return false;
    }
    JComponent canvas = view.getJComponent();
    KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    Window focusedWindow = focusManager.getFocusedWindow();
    PointerInfo pointer = MouseInfo.getPointerInfo();
    if (!canvas.isShowing()
        || focusedWindow == null
        || SwingUtilities.getWindowAncestor(canvas) == focusedWindow
        || pointer == null) {
      return false;
    }
    Point onScreen = pointer.getLocation();
    Point onCanvas = new Point(onScreen);
    SwingUtilities.convertPointFromScreen(onCanvas, canvas);
    boolean pointerOnImageOnly =
        canvas.contains(onCanvas) && !focusedWindow.getBounds().contains(onScreen);
    boolean menuOpen = MenuSelectionManager.defaultManager().getSelectedPath().length > 0;
    if (!redirectsAcrossWindows(
        event, focusManager.getFocusOwner(), pointerOnImageOnly && !menuOpen)) {
      return false;
    }
    LOGGER.info("Viewer shortcut delivered to the image under the pointer in another window");
    focusManager.redispatchEvent(
        canvas,
        new KeyEvent(
            canvas,
            KeyEvent.KEY_PRESSED,
            event.getWhen(),
            event.getModifiersEx(),
            event.getKeyCode(),
            event.getKeyChar(),
            event.getKeyLocation()));
    event.consume();
    return true;
  }

  static boolean redirectsAcrossWindows(
      KeyEvent event, Component owner, boolean pointerOnImageOnly) {
    return pointerOnImageOnly
        && (event.getModifiersEx() & COMMAND_MODIFIERS) == 0
        && !isNavigationKey(event.getKeyCode())
        && (owner == null || !consumesKeys(owner));
  }

  static boolean redirects(KeyEvent event, Component owner, boolean viewerWindowHasFocus) {
    if (!viewerWindowHasFocus
        || (event.getModifiersEx() & COMMAND_MODIFIERS) != 0
        || isNavigationKey(event.getKeyCode())) {
      return false;
    }
    return owner == null
        || !(owner instanceof ViewCanvas<?> || consumesKeys(owner) || handlesOwnKeys(owner));
  }

  private static boolean handlesOwnKeys(Component owner) {
    for (Component c = owner; c != null; c = c.getParent()) {
      if (c instanceof JComponent component
          && Boolean.TRUE.equals(component.getClientProperty(HANDLES_OWN_KEYS))) {
        return true;
      }
    }
    return false;
  }

  private static boolean isNavigationKey(int keyCode) {
    return keyCode == KeyEvent.VK_TAB
        || keyCode == KeyEvent.VK_ENTER
        || keyCode == KeyEvent.VK_SPACE
        || keyCode == KeyEvent.VK_ESCAPE;
  }

  private static boolean consumesKeys(Component owner) {
    return KEY_CONSUMERS.stream()
        .anyMatch(
            type ->
                type.isInstance(owner)
                    || SwingUtilities.getAncestorOfClass(type.asSubclass(Component.class), owner)
                        != null);
  }
}
