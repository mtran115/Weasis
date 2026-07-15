/*
 * Copyright (c) 2009-2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.pref;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ZoomSettingTest {

  @Test
  void usesFivePercentKeyboardZoomByDefault() {
    ZoomSetting setting = new ZoomSetting();

    assertEquals(5, setting.getKeyboardZoomPercent());
    assertEquals(1.05, setting.getKeyboardZoomFactor(), 0.000001);
  }

  @Test
  void clampsKeyboardZoomPercentToSupportedRange() {
    ZoomSetting setting = new ZoomSetting();

    setting.setKeyboardZoomPercent(0);
    assertEquals(ZoomSetting.MIN_KEYBOARD_ZOOM_PERCENT, setting.getKeyboardZoomPercent());

    setting.setKeyboardZoomPercent(12);
    assertEquals(12, setting.getKeyboardZoomPercent());
    assertEquals(1.12, setting.getKeyboardZoomFactor(), 0.000001);

    setting.setKeyboardZoomPercent(101);
    assertEquals(ZoomSetting.MAX_KEYBOARD_ZOOM_PERCENT, setting.getKeyboardZoomPercent());
  }
}
