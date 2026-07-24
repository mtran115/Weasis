/*
 * Copyright (c) 2009-2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ComboItemListenerTest {

  @Test
  void explicitlyTriggersActionWhenItemIsAlreadySelected() {
    AtomicInteger actionCount = new AtomicInteger();
    ComboItemListener<String> action =
        new ComboItemListener<>(ActionW.PRESET, new String[] {"Auto", "Default"}) {
          @Override
          public void itemStateChanged(Object object) {
            actionCount.incrementAndGet();
          }
        };

    assertEquals("Auto", action.getSelectedItem());
    assertEquals(0, actionCount.get());

    action.setSelectedItemAndTriggerAction("Auto");

    assertEquals("Auto", action.getSelectedItem());
    assertEquals(1, actionCount.get());
  }

  @Test
  void changedSelectionTriggersActionOnlyOnce() {
    AtomicInteger actionCount = new AtomicInteger();
    ComboItemListener<String> action =
        new ComboItemListener<>(ActionW.PRESET, new String[] {"Auto", "Default"}) {
          @Override
          public void itemStateChanged(Object object) {
            actionCount.incrementAndGet();
          }
        };

    action.setSelectedItemAndTriggerAction("Default");

    assertEquals("Default", action.getSelectedItem());
    assertEquals(1, actionCount.get());
  }
}
