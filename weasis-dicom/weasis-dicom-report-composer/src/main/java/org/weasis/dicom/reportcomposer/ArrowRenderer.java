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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

final class ArrowRenderer {
  private static final Color ARROW_COLOR = new Color(255, 213, 0);

  private ArrowRenderer() {}

  static BufferedImage render(BufferedImage source, ArrowPlacement placement) {
    BufferedImage output =
        new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = output.createGraphics();
    graphics.drawImage(source, 0, 0, null);
    if (placement != null) {
      draw(graphics, source.getWidth(), source.getHeight(), placement);
    }
    graphics.dispose();
    return output;
  }

  static void draw(Graphics2D graphics, int width, int height, ArrowPlacement placement) {
    double tailX = placement.tailX() * width;
    double tailY = placement.tailY() * height;
    double tipX = placement.tipX() * width;
    double tipY = placement.tipY() * height;
    double dx = tipX - tailX;
    double dy = tipY - tailY;
    double distance = Math.hypot(dx, dy);
    if (distance < 2.0) {
      return;
    }

    RenderingHints oldHints = graphics.getRenderingHints();
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    float foregroundStroke = Math.max(3.0f, Math.min(width, height) / 220.0f);
    float outlineStroke = foregroundStroke + Math.max(2.0f, foregroundStroke * 0.75f);
    double headLength = Math.max(18.0, Math.min(distance * 0.42, Math.min(width, height) * 0.09));
    double headHalfWidth = headLength * 0.48;
    double unitX = dx / distance;
    double unitY = dy / distance;
    double baseX = tipX - unitX * headLength;
    double baseY = tipY - unitY * headLength;
    double perpendicularX = -unitY;
    double perpendicularY = unitX;

    graphics.setStroke(
        new BasicStroke(outlineStroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    graphics.setColor(Color.BLACK);
    graphics.drawLine(
        (int) Math.round(tailX),
        (int) Math.round(tailY),
        (int) Math.round(baseX),
        (int) Math.round(baseY));
    graphics.fill(
        arrowHead(tipX, tipY, baseX, baseY, perpendicularX, perpendicularY, headHalfWidth + 2.0));

    graphics.setStroke(
        new BasicStroke(foregroundStroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    graphics.setColor(ARROW_COLOR);
    graphics.drawLine(
        (int) Math.round(tailX),
        (int) Math.round(tailY),
        (int) Math.round(baseX),
        (int) Math.round(baseY));
    graphics.fill(
        arrowHead(tipX, tipY, baseX, baseY, perpendicularX, perpendicularY, headHalfWidth));

    graphics.setRenderingHints(oldHints);
  }

  private static Path2D arrowHead(
      double tipX,
      double tipY,
      double baseX,
      double baseY,
      double perpendicularX,
      double perpendicularY,
      double halfWidth) {
    Path2D path = new Path2D.Double();
    path.moveTo(tipX, tipY);
    path.lineTo(baseX + perpendicularX * halfWidth, baseY + perpendicularY * halfWidth);
    path.lineTo(baseX - perpendicularX * halfWidth, baseY - perpendicularY * halfWidth);
    path.closePath();
    return path;
  }
}
