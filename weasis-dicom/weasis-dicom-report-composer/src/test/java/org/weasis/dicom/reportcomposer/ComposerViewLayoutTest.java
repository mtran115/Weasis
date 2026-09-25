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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.reportcomposer.ComposerViewLayout.Mode;

class ComposerViewLayoutTest {
  @Test
  void columnsShowEveryViewAndTabsRestoreTheSelectedOne() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          JPanel compose = new JPanel();
          JPanel keyImages = new JPanel();
          JPanel preview = new JPanel();
          ComposerViewLayout views = layout(compose, keyImages, preview);
          views.show(ComposerViewLayout.PREVIEW);
          AtomicInteger changes = new AtomicInteger();
          views.addVisibilityListener(changes::incrementAndGet);

          views.setMode(Mode.COLUMNS);
          boolean allVisible =
              views.isVisible(ComposerViewLayout.COMPOSE)
                  && views.isVisible(ComposerViewLayout.KEY_IMAGES)
                  && views.isVisible(ComposerViewLayout.PREVIEW);
          int selectedInColumns = views.selectedView();
          boolean containsAll =
              views.contains(compose) && views.contains(keyImages) && views.contains(preview);

          views.setMode(Mode.TABS);

          assertAll(
              () -> assertTrue(allVisible),
              () -> assertEquals(-1, selectedInColumns),
              () -> assertTrue(containsAll),
              () -> assertEquals(ComposerViewLayout.PREVIEW, views.selectedView()),
              () -> assertFalse(views.isVisible(ComposerViewLayout.COMPOSE)),
              () -> assertTrue(views.contains(preview)),
              () -> assertTrue(changes.get() >= 2));
        });
  }

  @Test
  void toolTipsAndDisabledViewsSurviveModeChanges() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          ComposerViewLayout views = layout(new JPanel(), new JPanel(), new JPanel());
          views.setMode(Mode.COLUMNS);
          views.setToolTip(ComposerViewLayout.KEY_IMAGES, "Key Images — F7");
          views.setEnabledAt(ComposerViewLayout.KEY_IMAGES, false);
          views.setMode(Mode.TABS);
          views.show(ComposerViewLayout.KEY_IMAGES);

          assertAll(
              () -> assertEquals("Key Images — F7", views.toolTip(ComposerViewLayout.KEY_IMAGES)),
              () -> assertFalse(views.isEnabledAt(ComposerViewLayout.KEY_IMAGES)),
              () -> assertEquals(ComposerViewLayout.COMPOSE, views.selectedView()));
        });
  }

  private static ComposerViewLayout layout(JPanel compose, JPanel keyImages, JPanel preview) {
    return new ComposerViewLayout(
        List.of("Compose", "Key Images", "Preview"), List.of(compose, keyImages, preview));
  }
}
