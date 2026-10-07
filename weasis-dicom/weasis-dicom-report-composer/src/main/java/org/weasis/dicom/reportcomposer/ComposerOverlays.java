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

import java.awt.Graphics2D;
import java.util.LinkedHashMap;
import java.util.Map;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ViewCanvasOverlay;

/** A viewport paints one overlay; this lets the level map and the AI marks share it by name. */
final class ComposerOverlays implements ViewCanvasOverlay {
  private static final String KEY = ComposerOverlays.class.getName();

  private final Map<String, ViewCanvasOverlay> overlays = new LinkedHashMap<>();

  private ComposerOverlays() {}

  static void set(DefaultView2d<?> canvas, String name, ViewCanvasOverlay overlay) {
    ComposerOverlays composite =
        canvas.getClientProperty(KEY) instanceof ComposerOverlays existing
            ? existing
            : new ComposerOverlays();
    composite.overlays.put(name, overlay);
    canvas.putClientProperty(KEY, composite);
    canvas.putClientProperty(ViewCanvasOverlay.KEY, composite);
    canvas.repaint();
  }

  static void remove(DefaultView2d<?> canvas, String name) {
    if (canvas.getClientProperty(KEY) instanceof ComposerOverlays composite
        && composite.overlays.remove(name) != null) {
      if (composite.overlays.isEmpty()) {
        canvas.putClientProperty(KEY, null);
        canvas.putClientProperty(ViewCanvasOverlay.KEY, null);
      }
      canvas.repaint();
    }
  }

  static boolean has(DefaultView2d<?> canvas, String name) {
    return canvas.getClientProperty(KEY) instanceof ComposerOverlays composite
        && composite.overlays.containsKey(name);
  }

  @Override
  public void paint(Graphics2D graphics, DefaultView2d<?> canvas) {
    for (ViewCanvasOverlay overlay : overlays.values()) {
      Graphics2D copy = (Graphics2D) graphics.create();
      try {
        overlay.paint(copy, canvas);
      } finally {
        copy.dispose();
      }
    }
  }
}
