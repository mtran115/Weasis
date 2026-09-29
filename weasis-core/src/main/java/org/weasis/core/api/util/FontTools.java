/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.util;

import java.awt.Color;
import java.awt.Graphics2D;
import org.weasis.core.util.StringUtil;

public class FontTools {

  private FontTools() {}

  public static void paintColorFontOutline(
      Graphics2D g2, String str, float x, float y, Color color) {
    if (StringUtil.hasText(str)) {
      // A one-pixel halo from eight offset copies uses the glyph cache. Stroking the text outline
      // as a shape went through the antialiased path renderer on every viewer repaint (~7x
      // slower) and delayed other UI work, such as scrolling, on the event thread.
      g2.setPaint(Color.BLACK);
      for (float dx = -1f; dx <= 1f; dx++) {
        for (float dy = -1f; dy <= 1f; dy++) {
          if (dx != 0f || dy != 0f) {
            g2.drawString(str, x + dx, y + dy);
          }
        }
      }
      g2.setPaint(color);
      g2.drawString(str, x, y);
    }
  }

  public static void paintFontOutline(Graphics2D g2, String str, float x, float y) {
    paintColorFontOutline(g2, str, x, y, Color.WHITE);
  }
}
