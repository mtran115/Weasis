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
import java.util.List;
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

  @Test
  void drawsEveryArrowInACollection() {
    BufferedImage source = new BufferedImage(240, 160, BufferedImage.TYPE_INT_RGB);
    int background = new Color(32, 32, 32).getRGB();
    for (int y = 0; y < source.getHeight(); y++) {
      for (int x = 0; x < source.getWidth(); x++) {
        source.setRGB(x, y, background);
      }
    }

    ArrowPlacement upperLeft = new ArrowPlacement(0.4, 0.4, 0.15, 0.15);
    ArrowPlacement lowerRight = new ArrowPlacement(0.6, 0.6, 0.85, 0.85);
    BufferedImage firstOnly = ArrowRenderer.render(source, upperLeft);
    BufferedImage both = ArrowRenderer.renderAll(source, List.of(upperLeft, lowerRight));

    assertTrue(changedPixels(both, background) > changedPixels(firstOnly, background));
    assertTrue(changedPixels(both, background, 180, 110, 240, 160) > 0);
  }

  private static long changedPixels(BufferedImage image, int background) {
    return changedPixels(image, background, 0, 0, image.getWidth(), image.getHeight());
  }

  private static long changedPixels(
      BufferedImage image, int background, int minX, int minY, int maxX, int maxY) {
    long changedPixels = 0;
    for (int y = minY; y < maxY; y++) {
      for (int x = minX; x < maxX; x++) {
        if (image.getRGB(x, y) != background) {
          changedPixels++;
        }
      }
    }
    return changedPixels;
  }
}
