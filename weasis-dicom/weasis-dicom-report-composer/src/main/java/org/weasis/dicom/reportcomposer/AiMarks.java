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
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Findings the radiologist rings for the AI, per study and in source pixels. A ring is 15 mm across
 * in patient space, so it circles a disc margin or facet whatever the field of view.
 */
final class AiMarks {
  static final double RING_DIAMETER_MM = 15;
  static final Color RING = new Color(255, 45, 45);
  private static final double FALLBACK_RADIUS_PIXELS = 12;

  /** A ring centered on a source pixel of one image (frame for multi-frame objects). */
  record Mark(int number, String sopInstanceUid, Integer frame, double column, double row) {}

  private final Map<String, List<Mark>> byStudy = new HashMap<>();

  /** Rings the point, or removes the ring it falls inside; returns true when a ring was added. */
  synchronized boolean toggle(String studyKey, ImageReference image, double column, double row) {
    List<Mark> marks = byStudy.computeIfAbsent(studyKey, key -> new ArrayList<>());
    double[] radius = radiusPixels(image.geometry());
    Optional<Mark> inside =
        marks.stream()
            .filter(mark -> onImage(mark, image.sopInstanceUid(), image.sourceFrameIndex()))
            .filter(mark -> inside(mark, column, row, radius))
            .findFirst();
    if (inside.isPresent()) {
      marks.remove(inside.get());
      renumber(marks);
      return false;
    }
    marks.add(
        new Mark(marks.size() + 1, image.sopInstanceUid(), image.sourceFrameIndex(), column, row));
    return true;
  }

  synchronized List<Mark> forStudy(String studyKey) {
    return List.copyOf(byStudy.getOrDefault(studyKey, List.of()));
  }

  synchronized List<Mark> forImage(String studyKey, String sopInstanceUid, Integer frame) {
    return byStudy.getOrDefault(studyKey, List.of()).stream()
        .filter(mark -> onImage(mark, sopInstanceUid, frame))
        .toList();
  }

  synchronized void clear(String studyKey) {
    byStudy.remove(studyKey);
  }

  /** Ring radius in source pixels along columns and rows; non-square pixels give an ellipse. */
  static double[] radiusPixels(ImageGeometry geometry) {
    List<Double> spacing = geometry.pixelSpacing();
    if (spacing.size() != 2 || spacing.get(0) <= 0 || spacing.get(1) <= 0) {
      return new double[] {FALLBACK_RADIUS_PIXELS, FALLBACK_RADIUS_PIXELS};
    }
    double radiusMm = RING_DIAMETER_MM / 2;
    return new double[] {radiusMm / spacing.get(1), radiusMm / spacing.get(0)};
  }

  /** Draws a numbered ring centered at (x, y) with the given radius, in the graphics' units. */
  static void paintRing(Graphics2D g, double x, double y, double radius, int number) {
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(
        RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    Ellipse2D ring = new Ellipse2D.Double(x - radius, y - radius, 2 * radius, 2 * radius);
    // A dark halo keeps the ring visible on bright marrow or fat.
    g.setStroke(new BasicStroke(3.5f));
    g.setColor(new Color(0, 0, 0, 150));
    g.draw(ring);
    g.setStroke(new BasicStroke(1.75f));
    g.setColor(RING);
    g.draw(ring);
    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
    FontMetrics metrics = g.getFontMetrics();
    String label = Integer.toString(number);
    int labelX = (int) Math.round(x + radius * 0.75 + 3);
    int labelY = (int) Math.round(y - radius * 0.75 - 3);
    g.setColor(new Color(0, 0, 0, 170));
    g.fillRoundRect(
        labelX - 2,
        labelY - metrics.getAscent(),
        metrics.stringWidth(label) + 4,
        metrics.getHeight(),
        4,
        4);
    g.setColor(RING);
    g.drawString(label, labelX, labelY);
  }

  private static boolean onImage(Mark mark, String sopInstanceUid, Integer frame) {
    return mark.sopInstanceUid().equals(sopInstanceUid) && Objects.equals(mark.frame(), frame);
  }

  private static boolean inside(Mark mark, double column, double row, double[] radius) {
    double dx = (mark.column() - column) / radius[0];
    double dy = (mark.row() - row) / radius[1];
    return dx * dx + dy * dy <= 1;
  }

  private static void renumber(List<Mark> marks) {
    for (int index = 0; index < marks.size(); index++) {
      Mark mark = marks.get(index);
      marks.set(
          index,
          new Mark(index + 1, mark.sopInstanceUid(), mark.frame(), mark.column(), mark.row()));
    }
  }
}
