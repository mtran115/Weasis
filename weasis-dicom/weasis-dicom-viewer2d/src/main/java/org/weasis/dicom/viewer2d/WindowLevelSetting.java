/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d;

import org.osgi.service.prefs.Preferences;
import org.weasis.core.api.service.BundlePreferences;

public final class WindowLevelSetting {
  public static final String PREFERENCE_NODE = "window.level"; // NON-NLS
  public static final int DEFAULT_KEYBOARD_WINDOW_STEP = 500;
  public static final int DEFAULT_KEYBOARD_LEVEL_STEP = 500;
  public static final int MIN_KEYBOARD_STEP = 1;
  public static final int MAX_KEYBOARD_STEP = 1_000_000;

  private int keyboardWindowStep = DEFAULT_KEYBOARD_WINDOW_STEP;
  private int keyboardLevelStep = DEFAULT_KEYBOARD_LEVEL_STEP;

  public void applyPreferences(Preferences prefs) {
    if (prefs != null) {
      Preferences node = prefs.node(PREFERENCE_NODE);
      setKeyboardWindowStep(
          node.getInt("keyboardWindowStep", DEFAULT_KEYBOARD_WINDOW_STEP)); // NON-NLS
      setKeyboardLevelStep(
          node.getInt("keyboardLevelStep", DEFAULT_KEYBOARD_LEVEL_STEP)); // NON-NLS
    }
  }

  public void savePreferences(Preferences prefs) {
    if (prefs != null) {
      Preferences node = prefs.node(PREFERENCE_NODE);
      BundlePreferences.putIntPreferences(
          node, "keyboardWindowStep", keyboardWindowStep); // NON-NLS
      BundlePreferences.putIntPreferences(node, "keyboardLevelStep", keyboardLevelStep); // NON-NLS
    }
  }

  public int getKeyboardWindowStep() {
    return keyboardWindowStep;
  }

  public void setKeyboardWindowStep(int keyboardWindowStep) {
    this.keyboardWindowStep = clampStep(keyboardWindowStep);
  }

  public int getKeyboardLevelStep() {
    return keyboardLevelStep;
  }

  public void setKeyboardLevelStep(int keyboardLevelStep) {
    this.keyboardLevelStep = clampStep(keyboardLevelStep);
  }

  private static int clampStep(int step) {
    return Math.max(MIN_KEYBOARD_STEP, Math.min(MAX_KEYBOARD_STEP, step));
  }
}
