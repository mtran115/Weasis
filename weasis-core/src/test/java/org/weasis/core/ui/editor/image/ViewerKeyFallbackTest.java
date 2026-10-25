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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ViewerKeyFallbackTest {
  private static KeyEvent press(int keyCode, int modifiers) {
    return new KeyEvent(
        new JPanel(), KeyEvent.KEY_PRESSED, 0L, modifiers, keyCode, KeyEvent.CHAR_UNDEFINED);
  }

  @Test
  void redirectsWhenFocusRestsOnAnInertPartOfTheViewerWindow() {
    KeyEvent numpadPlus = press(KeyEvent.VK_ADD, 0);
    assertTrue(ViewerKeyFallback.redirects(numpadPlus, null, true));
    assertTrue(ViewerKeyFallback.redirects(numpadPlus, new JButton(), true));
    assertTrue(ViewerKeyFallback.redirects(numpadPlus, new JPanel(), true));
    assertTrue(
        ViewerKeyFallback.redirects(press(KeyEvent.VK_W, InputEvent.SHIFT_DOWN_MASK), null, true));
  }

  @Test
  void leavesKeysAloneOutsideAFocusedViewerWindow() {
    assertFalse(ViewerKeyFallback.redirects(press(KeyEvent.VK_ADD, 0), null, false));
  }

  @Test
  void leavesTheCanvasAndKeyUsingControlsAlone() {
    KeyEvent key = press(KeyEvent.VK_DOWN, 0);
    Component canvas = mock(JPanel.class, withSettings().extraInterfaces(ViewCanvas.class));
    assertFalse(ViewerKeyFallback.redirects(key, canvas, true));
    assertFalse(ViewerKeyFallback.redirects(key, new JTextField(), true));
    assertFalse(ViewerKeyFallback.redirects(key, new JList<String>(), true));
    assertFalse(ViewerKeyFallback.redirects(key, new JComboBox<String>(), true));
    JList<String> list = new JList<>();
    new JScrollPane(list);
    assertFalse(ViewerKeyFallback.redirects(key, list, true));
  }

  @Test
  void leavesPanelsThatHandleTheirOwnKeysAlone() {
    JPanel tool = new JPanel();
    tool.putClientProperty(ViewerKeyFallback.HANDLES_OWN_KEYS, Boolean.TRUE);
    JButton formButton = new JButton();
    tool.add(formButton);
    assertFalse(ViewerKeyFallback.redirects(press(KeyEvent.VK_1, 0), formButton, true));
  }

  @ParameterizedTest
  @ValueSource(
      ints = {InputEvent.META_DOWN_MASK, InputEvent.CTRL_DOWN_MASK, InputEvent.ALT_DOWN_MASK})
  void leavesMenuAndSystemShortcutsAlone(int modifier) {
    assertFalse(ViewerKeyFallback.redirects(press(KeyEvent.VK_Q, modifier), null, true));
  }

  @ParameterizedTest
  @ValueSource(ints = {KeyEvent.VK_TAB, KeyEvent.VK_ENTER, KeyEvent.VK_SPACE, KeyEvent.VK_ESCAPE})
  void leavesKeyboardNavigationAlone(int keyCode) {
    assertFalse(ViewerKeyFallback.redirects(press(keyCode, 0), new JButton(), true));
  }
}
