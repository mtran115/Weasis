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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WindowLevelSettingTest {

  @Test
  void usesFiveHundredForBothKeyboardStepsByDefault() {
    WindowLevelSetting setting = new WindowLevelSetting();

    assertEquals(500, setting.getKeyboardWindowStep());
    assertEquals(500, setting.getKeyboardLevelStep());
  }

  @Test
  void clampsKeyboardStepsToSupportedRange() {
    WindowLevelSetting setting = new WindowLevelSetting();

    setting.setKeyboardWindowStep(0);
    setting.setKeyboardLevelStep(Integer.MAX_VALUE);

    assertEquals(WindowLevelSetting.MIN_KEYBOARD_STEP, setting.getKeyboardWindowStep());
    assertEquals(WindowLevelSetting.MAX_KEYBOARD_STEP, setting.getKeyboardLevelStep());
  }
}
