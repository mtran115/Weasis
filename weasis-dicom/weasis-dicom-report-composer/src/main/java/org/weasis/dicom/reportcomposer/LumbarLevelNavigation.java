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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ViewCanvasOverlay;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer2d.EventManager;

/** Patient-coordinate navigation; no instance-number assumptions and no persisted graphics. */
final class LumbarLevelNavigation {
  private final List<DefaultView2d<DicomImageElement>> attached = new ArrayList<>();

  void clear() {
    for (var canvas : attached) {
      if (canvas.getClientProperty(ViewCanvasOverlay.KEY) instanceof LevelOverlay)
        canvas.putClientProperty(ViewCanvasOverlay.KEY, null);
      canvas.repaint();
    }
    attached.clear();
  }

  void show(LumbarLevelMap map, List<LumbarLevelMap.Landmark> points, boolean confirmed) {
    clear();
    for (var viewport : DicomContextReader.visibleCanvases()) {
      var canvas = viewport.canvas();
      canvas.putClientProperty(ViewCanvasOverlay.KEY, new LevelOverlay(map, points, confirmed));
      attached.add(canvas);
      canvas.repaint();
    }
  }

  @SuppressWarnings("unchecked")
  boolean jump(LumbarLevelMap map, LumbarLevelMap.Landmark point) {
    var manager = EventManager.getInstance();
    var viewer = manager.getSelectedView2dContainer();
    if (viewer == null) return false;
    DefaultView2d<DicomImageElement> best = null;
    int bestIndex = -1;
    double bestDistance = 10.0;
    for (var viewport : DicomContextReader.visibleCanvases()) {
      var canvas = viewport.canvas();
      if (canvas.getSeries() == null) continue;
      var images =
          canvas
              .getSeries()
              .copyOfMedias(
                  (Filter<DicomImageElement>) canvas.getActionValue(ActionW.FILTERED_SERIES.cmd()),
                  canvas.getCurrentSortComparator());
      for (int index = 0; index < images.size(); index++) {
        var image = images.get(index);
        if (!map.studyInstanceUid().equals(SourceDicomMetadata.text(Tag.StudyInstanceUID, image)))
          continue;
        var geometry =
            SourceDicomMetadata.reference(image, canvas.getSeries(), index + 1, images.size())
                .geometry();
        if (!LumbarLevelMap.validGeometry(geometry)
            || Math.abs(LumbarLevelMap.normal(geometry)[2]) < .65
            || !map.frameOfReferenceUid().equals(geometry.frameOfReferenceUid())) continue;
        Point2D pixel = LumbarLevelMap.pixel(geometry, point.lps());
        double distance = LumbarLevelMap.planeDistance(geometry, point.lps());
        if (geometry.contains(pixel.getX(), pixel.getY()) && distance < bestDistance) {
          best = canvas;
          bestIndex = index;
          bestDistance = distance;
        }
      }
    }
    if (best == null) return false;
    viewer.setSelectedImagePane(best);
    int target = bestIndex + 1;
    manager.getAction(ActionW.SCROLL_SERIES).ifPresent(action -> action.setSliderValue(target));
    return true;
  }

  private static final class LevelOverlay implements ViewCanvasOverlay {
    private final LumbarLevelMap map;
    private final List<LumbarLevelMap.Landmark> points;
    private final boolean confirmed;
    private Object lastImage;
    private Object lastSeries;
    private ImageGeometry geometry;

    LevelOverlay(LumbarLevelMap map, List<LumbarLevelMap.Landmark> points, boolean confirmed) {
      this.map = map;
      this.points = List.copyOf(points);
      this.confirmed = confirmed;
    }

    @Override
    public void paint(Graphics2D g, DefaultView2d<?> canvas) {
      if (canvas.getImage() == null) return;
      if (lastImage != canvas.getImage() || lastSeries != canvas.getSeries()) {
        lastImage = canvas.getImage();
        lastSeries = canvas.getSeries();
        geometry = null;
        if (map.studyInstanceUid()
            .equals(SourceDicomMetadata.text(Tag.StudyInstanceUID, canvas.getImage()))) {
          geometry =
              DicomContextReader.currentReference(canvas)
                  .map(ImageReference::geometry)
                  .orElse(null);
        }
      }
      if (!LumbarLevelMap.validGeometry(geometry)
          || !map.frameOfReferenceUid().equals(geometry.frameOfReferenceUid())
          || Math.abs(LumbarLevelMap.normal(geometry)[0]) < .90) return;
      g.setFont(g.getFont().deriveFont(java.awt.Font.BOLD, 13f));
      for (int index = 0; index < points.size(); index++) {
        var point = points.get(index);
        if (LumbarLevelMap.planeDistance(geometry, point.lps()) > 12) continue;
        Point2D p = LumbarLevelMap.pixel(geometry, point.lps());
        if (!geometry.contains(p.getX(), p.getY())) continue;
        Point2D view =
            canvas.getMouseCoordinatesFromImage(
                p.getX() * canvas.getImage().getRescaleX(),
                p.getY() * canvas.getImage().getRescaleY());
        int x = (int) Math.round(view.getX()), y = (int) Math.round(view.getY());
        String label =
            point.displayLabel(index)
                + (confirmed || LumbarLevelMap.UNASSIGNED.equals(point.level()) ? "" : " ?");
        int width = g.getFontMetrics().stringWidth(label);
        g.setColor(new Color(0, 0, 0, 180));
        g.fillRoundRect(x + 7, y - 13, width + 9, 19, 5, 5);
        g.setColor(confirmed ? new Color(110, 230, 175) : new Color(120, 195, 255));
        g.fillOval(x - 3, y - 3, 6, 6);
        g.drawString(label, x + 11, y + 1);
      }
    }
  }
}
