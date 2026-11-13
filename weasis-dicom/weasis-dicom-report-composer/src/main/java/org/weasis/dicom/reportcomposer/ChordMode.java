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

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.JTextComponent;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ViewCanvasOverlay;
import org.weasis.dicom.reportcomposer.ChordGrammar.Outcome;

/**
 * Chord mode for the visible spine or shoulder form: the tilde key (Shift+back-quote) turns it on
 * and off. While on, letters and top-row digits go to its {@link ChordGrammar} wherever the
 * keyboard focus is, and the chord line shows at the bottom of each viewport. M (measure), the
 * numeric keypad, arrows and modified keys still reach the viewer.
 */
final class ChordMode implements KeyEventDispatcher {
  /** Pressed with Shift; the bare key is left to other tools. */
  static final int TOGGLE_KEY = KeyEvent.VK_BACK_QUOTE;

  private static final String OVERLAY = "chord-line";
  private static final int ACTIVE_MS = 1500;
  private static final int REJECT_MS = 600;
  private static ChordMode current;

  private final Component composer;
  private final Supplier<ChordGrammar> grammar;
  private final Predicate<Window> viewerWindow;
  private final Set<Integer> heldKeys = new HashSet<>();
  private final ViewCanvasOverlay line = this::paintLine;
  private final Timer fade = new Timer(REJECT_MS, event -> repaintLine());
  private boolean on;
  private boolean swallowTyped;
  private long lastKey;
  private long rejectedAt;

  ChordMode(Component composer, Supplier<ChordGrammar> grammar, Predicate<Window> viewerWindow) {
    this.composer = composer;
    this.grammar = grammar;
    this.viewerWindow = viewerWindow;
    fade.setRepeats(false);
  }

  void install() {
    current = this;
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);
  }

  void uninstall() {
    if (current == this) current = null;
    KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(this);
    setOn(false);
    heldKeys.clear();
  }

  /** Whether chord mode will take this key press, so handlers that run earlier leave it alone. */
  static boolean claims(KeyEvent event) {
    return current != null && current.wants(event);
  }

  boolean isOn() {
    return on;
  }

  /** Puts the line on the visible viewports, as after a study or layout change. */
  void showLine() {
    for (var viewport : DicomContextReader.visibleCanvases()) {
      DefaultView2d<?> canvas = viewport.canvas();
      if (on && !ComposerOverlays.has(canvas, OVERLAY)) ComposerOverlays.set(canvas, OVERLAY, line);
      else if (!on) ComposerOverlays.remove(canvas, OVERLAY);
      else canvas.repaint();
    }
  }

  private void setOn(boolean on) {
    this.on = on;
    lastKey = System.currentTimeMillis();
    showLine();
  }

  private void repaintLine() {
    if (on) showLine();
  }

  private boolean wants(KeyEvent event) {
    return event.getID() == KeyEvent.KEY_PRESSED
        && !event.isConsumed()
        && reachable()
        && (event.getKeyCode() == TOGGLE_KEY && event.getModifiersEx() == InputEvent.SHIFT_DOWN_MASK
            || on && engineKey(event));
  }

  /** A spine form is up, nothing is being typed into, and the composer or viewer is in front. */
  private boolean reachable() {
    ChordGrammar chords = grammar.get();
    KeyboardFocusManager focus = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    Window active = focus.getActiveWindow();
    return chords != null
        && chords.enabled()
        && !(focus.getFocusOwner() instanceof JTextComponent)
        && active != null
        && (active == SwingUtilities.getWindowAncestor(composer) || viewerWindow.test(active))
        && MenuSelectionManager.defaultManager().getSelectedPath().length == 0;
  }

  private boolean engineKey(KeyEvent event) {
    if (event.getModifiersEx() != 0) return false;
    int key = event.getKeyCode();
    return key == KeyEvent.VK_BACK_SPACE
        || key == KeyEvent.VK_ESCAPE
            && (!grammar.get().pending().isEmpty() || grammar.get().wantsEnter())
        || key == KeyEvent.VK_ENTER && grammar.get().wantsEnter()
        || SpineFormShortcuts.isTopRowDigit(event)
        || key >= KeyEvent.VK_A && key <= KeyEvent.VK_Z && key != KeyEvent.VK_M;
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
    if (!wants(event)) return false;
    if (heldKeys.add(event.getKeyCode())) handle(event.getKeyCode());
    swallowTyped = true;
    return consume(event);
  }

  private void handle(int key) {
    if (key == TOGGLE_KEY) {
      setOn(!on);
      return;
    }
    ChordGrammar engine = grammar.get();
    boolean accepted =
        switch (key) {
          case KeyEvent.VK_BACK_SPACE -> engine.backspace();
          case KeyEvent.VK_ESCAPE -> engine.escape();
          case KeyEvent.VK_ENTER -> engine.enter();
          default -> engine.type(keyChar(key)) != Outcome.REJECTED;
        };
    lastKey = System.currentTimeMillis();
    rejectedAt = accepted ? 0 : lastKey;
    fade.setInitialDelay(accepted ? ACTIVE_MS : REJECT_MS);
    fade.restart();
    showLine();
  }

  private static char keyChar(int key) {
    return key <= KeyEvent.VK_9 ? (char) ('0' + key - KeyEvent.VK_0) : (char) ('a' + key - 'A');
  }

  private static boolean consume(KeyEvent event) {
    event.consume();
    return true;
  }

  private void paintLine(Graphics2D g, DefaultView2d<?> canvas) {
    ChordGrammar engine = grammar.get();
    if (!on || engine == null) return;
    long now = System.currentTimeMillis();
    boolean rejected = now - rejectedAt < REJECT_MS;
    boolean idle = engine.pending().isEmpty() && now - lastKey >= ACTIVE_MS;
    String text = engine.lineText();

    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(
        RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g.setFont(canvas.getFont().deriveFont(Font.BOLD, 15f));
    FontMetrics metrics = g.getFontMetrics();
    int width = metrics.stringWidth(text) + 24;
    int height = metrics.getHeight() + 8;
    int x = (canvas.getWidth() - width) / 2;
    int y = canvas.getHeight() - height - 36;
    g.setColor(rejected ? new Color(170, 30, 30, 230) : new Color(0, 0, 0, idle ? 90 : 200));
    g.fillRoundRect(x, y, width, height, height, height);
    g.setColor(new Color(255, 255, 255, idle ? 110 : 255));
    g.drawString(text, x + 12, y + 4 + metrics.getAscent());
  }
}
