/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.main;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.event.KeyEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.gui.util.ShortcutManager;

class ThumbnailMouseAndKeyAdapterTest {
  private final ShortcutManager shortcutManager = ShortcutManager.getInstance();

  @BeforeEach
  void registerDefaultShortcuts() {
    shortcutManager.registerDefaults();
  }

  @AfterEach
  void restoreDefaultShortcuts() {
    shortcutManager.registerDefaults();
  }

  @Test
  void mapsDefaultNumpadShortcutsToViewerSlots() {
    assertEquals(0, ThumbnailMouseAndKeyAdapter.getViewSlotIndex(KeyEvent.VK_NUMPAD1, 0));
    assertEquals(1, ThumbnailMouseAndKeyAdapter.getViewSlotIndex(KeyEvent.VK_NUMPAD2, 0));
    assertEquals(2, ThumbnailMouseAndKeyAdapter.getViewSlotIndex(KeyEvent.VK_NUMPAD3, 0));
    assertEquals(3, ThumbnailMouseAndKeyAdapter.getViewSlotIndex(KeyEvent.VK_NUMPAD4, 0));
    assertEquals(-1, ThumbnailMouseAndKeyAdapter.getViewSlotIndex(KeyEvent.VK_NUMPAD5, 0));
  }

  @Test
  void mapsCustomizedShortcutToViewerSlot() {
    shortcutManager.setShortcut(
        ShortcutManager.ID_EXPLORER_OPEN_IN_VIEW_2, KeyEvent.VK_2, KeyEvent.ALT_MASK);

    assertEquals(1, ThumbnailMouseAndKeyAdapter.getViewSlotIndex(KeyEvent.VK_2, KeyEvent.ALT_MASK));
    assertEquals(-1, ThumbnailMouseAndKeyAdapter.getViewSlotIndex(KeyEvent.VK_NUMPAD2, 0));
  }
}
