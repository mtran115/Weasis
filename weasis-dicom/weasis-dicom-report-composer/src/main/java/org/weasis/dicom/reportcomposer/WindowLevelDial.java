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

import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.weasis.core.api.gui.util.ShortcutManager;
import org.weasis.core.ui.recorder.InteractionRecorder;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.EventManager.WindowLevel;
import org.weasis.dicom.viewer2d.View2d;

/**
 * W/L on one controller dial, for the viewport under the mouse. The dial moves the white point and
 * keeps the black point, which is how nearly all of the reader's larger corrections went; with a
 * button held it shifts brightness, keeping the contrast, for the small touch-ups. Steps are a
 * share of the current window, so a click feels the same on every scanner.
 */
final class WindowLevelDial {
  /** The share of the window one click moves the white point. */
  static final double WHITE_POINT_STEP = 0.05;

  /** The share of the window one click shifts brightness. */
  static final double BRIGHTNESS_STEP = 0.03;

  // Clicks closer together than this are one turn, which the restore-previous key undoes at once.
  private static final Duration TURN_PAUSE = Duration.ofMillis(1500);

  private static boolean installed;
  // Used on the Swing thread only.
  private static WeakReference<View2d> lastView = new WeakReference<>(null);
  private static Instant lastClick = Instant.MIN;

  enum Action {
    WHITE_POINT_UP(ShortcutManager.ID_REPORT_COMPOSER_WHITE_POINT_UP),
    WHITE_POINT_DOWN(ShortcutManager.ID_REPORT_COMPOSER_WHITE_POINT_DOWN),
    BRIGHTER(ShortcutManager.ID_REPORT_COMPOSER_BRIGHTER),
    DARKER(ShortcutManager.ID_REPORT_COMPOSER_DARKER);

    final String shortcut;

    Action(String shortcut) {
      this.shortcut = shortcut;
    }
  }

  private WindowLevelDial() {}

  static synchronized void install() {
    if (installed) return;
    installed = true;
    KeyboardFocusManager.getCurrentKeyboardFocusManager()
        .addKeyEventDispatcher(WindowLevelDial::dispatch);
  }

  /**
   * The W/L after one click. The white point moves by a share of the window, up to darken and lower
   * contrast or down to brighten and raise it, with the black point where it was; a brightness
   * click moves both together. Up and down undo each other.
   */
  static WindowLevel apply(Action action, WindowLevel current) {
    double window = current.window();
    double black = current.level() - window / 2;
    return switch (action) {
      case WHITE_POINT_UP -> withBlackPoint(black, window * (1 + WHITE_POINT_STEP));
      case WHITE_POINT_DOWN -> withBlackPoint(black, window / (1 + WHITE_POINT_STEP));
      case BRIGHTER -> new WindowLevel(window, current.level() - BRIGHTNESS_STEP * window);
      case DARKER -> new WindowLevel(window, current.level() + BRIGHTNESS_STEP * window);
    };
  }

  private static WindowLevel withBlackPoint(double black, double window) {
    return new WindowLevel(window, black + window / 2);
  }

  /** Whether this click starts a new turn: another viewport, or a pause since the last click. */
  static boolean startsTurn(boolean sameView, Instant lastClick, Instant now) {
    return !sameView || Duration.between(lastClick, now).compareTo(TURN_PAUSE) > 0;
  }

  private static boolean dispatch(KeyEvent event) {
    if (event.getID() != KeyEvent.KEY_PRESSED || event.isConsumed()) return false;
    ShortcutManager shortcuts = ShortcutManager.getInstance();
    for (Action action : Action.values()) {
      if (!shortcuts.matches(action.shortcut, event.getKeyCode(), event.getModifiers())) {
        continue;
      }
      Optional<View2d> view = SequenceStepper.targetView(event);
      if (SequenceStepper.typesIntoText(event) || view.isEmpty()) return false;
      event.consume();
      click(view.get(), action);
      return true;
    }
    return false;
  }

  private static void click(View2d view, Action action) {
    EventManager manager = EventManager.getInstance();
    Optional<WindowLevel> current = manager.keyboardWindowLevel(view);
    if (current.isEmpty()) return;
    Instant now = Instant.now();
    boolean newTurn = startsTurn(view == lastView.get(), lastClick, now);
    lastView = new WeakReference<>(view);
    lastClick = now;
    WindowLevel target = apply(action, current.get());
    if (manager.setKeyboardWindowLevel(view, target, newTurn)) {
      InteractionRecorder.windowLevelSet(view, target.window(), target.level(), "dial");
    }
  }
}
