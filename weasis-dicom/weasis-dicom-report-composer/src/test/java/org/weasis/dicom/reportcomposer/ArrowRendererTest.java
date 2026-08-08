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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class ArrowRendererTest {
  @Test
  void drawsArrowOnACopyWithoutChangingSource() {
    BufferedImage source = new BufferedImage(240, 160, BufferedImage.TYPE_INT_RGB);
    int background = new Color(32, 32, 32).getRGB();
    for (int y = 0; y < source.getHeight(); y++) {
      for (int x = 0; x < source.getWidth(); x++) {
        source.setRGB(x, y, background);
      }
    }

    BufferedImage rendered = ArrowRenderer.render(source, new ArrowPlacement(0.15, 0.8, 0.7, 0.35));

    assertNotEquals(source, rendered);
    assertEquals(background, source.getRGB(168, 56));
    long changedPixels = 0;
    for (int y = 0; y < rendered.getHeight(); y++) {
      for (int x = 0; x < rendered.getWidth(); x++) {
        if (rendered.getRGB(x, y) != background) {
          changedPixels++;
        }
      }
    }
    assertTrue(changedPixels > 100, "the rendered copy should contain a visible arrow");
  }
}
