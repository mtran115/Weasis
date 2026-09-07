/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.event.KeyEvent;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

class ShortcutManagerTest {

  @Test
  void keepsTopRowDigitsDistinctFromNumpadDigits() {
    KeyEvent event = keyEvent(KeyEvent.VK_1, '1', KeyEvent.KEY_LOCATION_STANDARD);

    assertEquals(KeyEvent.VK_1, ShortcutManager.getNormalizedKeyCode(event));
  }

  @Test
  void normalizesNumpadDigitReportedAsRegularDigit() {
    KeyEvent event = keyEvent(KeyEvent.VK_1, '1', KeyEvent.KEY_LOCATION_NUMPAD);

    assertEquals(KeyEvent.VK_NUMPAD1, ShortcutManager.getNormalizedKeyCode(event));
  }

  @Test
  void normalizesNumpadNavigationKeysWhenNumLockIsOff() {
    assertEquals(
        KeyEvent.VK_NUMPAD1,
        ShortcutManager.getNormalizedKeyCode(
            keyEvent(KeyEvent.VK_END, KeyEvent.CHAR_UNDEFINED, KeyEvent.KEY_LOCATION_NUMPAD)));
    assertEquals(
        KeyEvent.VK_NUMPAD2,
        ShortcutManager.getNormalizedKeyCode(
            keyEvent(KeyEvent.VK_KP_DOWN, KeyEvent.CHAR_UNDEFINED, KeyEvent.KEY_LOCATION_NUMPAD)));
    assertEquals(
        KeyEvent.VK_NUMPAD7,
        ShortcutManager.getNormalizedKeyCode(
            keyEvent(KeyEvent.VK_HOME, KeyEvent.CHAR_UNDEFINED, KeyEvent.KEY_LOCATION_NUMPAD)));
    assertEquals(
        KeyEvent.VK_NUMPAD9,
        ShortcutManager.getNormalizedKeyCode(
            keyEvent(KeyEvent.VK_PAGE_UP, KeyEvent.CHAR_UNDEFINED, KeyEvent.KEY_LOCATION_NUMPAD)));
  }

  @Test
  void doesNotNormalizeStandardNavigationKeys() {
    KeyEvent event =
        keyEvent(KeyEvent.VK_END, KeyEvent.CHAR_UNDEFINED, KeyEvent.KEY_LOCATION_STANDARD);

    assertEquals(KeyEvent.VK_END, ShortcutManager.getNormalizedKeyCode(event));
  }

  private static KeyEvent keyEvent(int keyCode, char keyChar, int keyLocation) {
    return new KeyEvent(new JPanel(), KeyEvent.KEY_PRESSED, 0L, 0, keyCode, keyChar, keyLocation);
  }
}
