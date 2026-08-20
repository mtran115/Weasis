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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import org.weasis.core.api.gui.util.GuiUtils;

final class ArrowAnnotationDialog extends JDialog {
  private final ArrowCanvas arrowCanvas;
  private boolean accepted;

  private ArrowAnnotationDialog(Window owner, BufferedImage image) {
    super(owner, "Key Image Arrows", ModalityType.APPLICATION_MODAL);
    setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    arrowCanvas = new ArrowCanvas(image);
    init();
  }

  static AnnotationResult showDialog(JComponent parent, BufferedImage image) {
    Window owner = SwingUtilities.getWindowAncestor(parent);
    ArrowAnnotationDialog dialog = new ArrowAnnotationDialog(owner, image);
    dialog.setLocationRelativeTo(parent);
    ComposerDialogSupport.keepInFront(dialog);
    dialog.setVisible(true);
    return new AnnotationResult(dialog.accepted, dialog.arrowCanvas.getPlacements());
  }

  private void init() {
    setLayout(new BorderLayout(8, 8));
    JLabel instructions =
        new JLabel(
            "Press on a finding and drag outward, or click for an automatic arrow. Repeat as needed.",
            SwingConstants.CENTER);
    instructions.setBorder(GuiUtils.getEmptyBorder(8, 8, 0, 8));
    add(instructions, BorderLayout.NORTH);
    add(arrowCanvas, BorderLayout.CENTER);

    JButton undoButton = new JButton("Undo Last");
    undoButton.setEnabled(false);
    undoButton.addActionListener(event -> arrowCanvas.removeLastPlacement());
    JButton clearButton = new JButton("Clear All");
    clearButton.setEnabled(false);
    clearButton.addActionListener(event -> arrowCanvas.clearPlacements());
    JButton noArrowButton = new JButton("Use Without Arrows");
    noArrowButton.addActionListener(
        event -> {
          arrowCanvas.clearPlacements();
          accepted = true;
          dispose();
        });
    JButton cancelButton = new JButton("Cancel");
    cancelButton.addActionListener(event -> dispose());
    JButton useButton = new JButton("Use Arrow");
    useButton.setEnabled(false);
    arrowCanvas.setPlacementListener(
        placements -> {
          boolean hasArrows = !placements.isEmpty();
          undoButton.setEnabled(hasArrows);
          clearButton.setEnabled(hasArrows);
          useButton.setEnabled(hasArrows);
          useButton.setText(placements.size() == 1 ? "Use Arrow" : "Use Arrows");
        });
    useButton.addActionListener(
        event -> {
          accepted = true;
          dispose();
        });
    getRootPane().setDefaultButton(useButton);
    getRootPane()
        .registerKeyboardAction(
            event -> dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);

    JPanel buttons =
        GuiUtils.getFlowLayoutPanel(
            java.awt.FlowLayout.TRAILING,
            8,
            8,
            undoButton,
            clearButton,
            noArrowButton,
            cancelButton,
            useButton);
    add(buttons, BorderLayout.SOUTH);
    setMinimumSize(new Dimension(720, 560));
    setSize(new Dimension(980, 760));
  }

  record AnnotationResult(boolean accepted, List<ArrowPlacement> placements) {
    AnnotationResult {
      placements = List.copyOf(placements);
    }
  }

  static ArrowPlacement placementFromHeadFirstGesture(
      Point arrowHead, Point arrowTail, int imageWidth, int imageHeight) {
    double width = Math.max(1, imageWidth - 1);
    double height = Math.max(1, imageHeight - 1);
    return new ArrowPlacement(
        arrowTail.x / width, arrowTail.y / height, arrowHead.x / width, arrowHead.y / height);
  }

  static Point automaticTailFor(Point arrowHead, int imageWidth, int imageHeight) {
    int offset = Math.clamp(Math.min(imageWidth, imageHeight) / 12, 28, 80);
    int maxX = imageWidth - 1;
    int maxY = imageHeight - 1;
    int directionX = arrowHead.x > maxX / 2 ? -1 : 1;
    int directionY = arrowHead.y > maxY / 2 ? -1 : 1;
    return new Point(
        Math.clamp(arrowHead.x + directionX * offset, 0, maxX),
        Math.clamp(arrowHead.y + directionY * offset, 0, maxY));
  }

  private static final class ArrowCanvas extends JComponent {
    private final BufferedImage image;
    private final List<ArrowPlacement> placements = new ArrayList<>();
    private Point arrowHeadPoint;
    private ArrowPlacement pendingPlacement;
    private java.util.function.Consumer<List<ArrowPlacement>> placementListener = ignored -> {};

    ArrowCanvas(BufferedImage image) {
      this.image = image;
      setOpaque(true);
      setBackground(java.awt.Color.BLACK);
      setBorder(BorderFactory.createLineBorder(java.awt.Color.DARK_GRAY));
      MouseAdapter mouseAdapter =
          new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
              arrowHeadPoint = toImagePoint(event.getPoint());
            }

            @Override
            public void mouseDragged(MouseEvent event) {
              Point arrowTail = toImagePoint(event.getPoint());
              if (arrowHeadPoint != null && arrowTail != null) {
                setPendingPlacement(
                    placementFromHeadFirstGesture(
                        arrowHeadPoint, arrowTail, image.getWidth(), image.getHeight()));
              }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
              Point arrowTail = toImagePoint(event.getPoint());
              if (arrowTail == null) {
                arrowHeadPoint = null;
                setPendingPlacement(null);
                return;
              }
              Point arrowHead = arrowHeadPoint == null ? arrowTail : arrowHeadPoint;
              if (arrowHeadPoint == null || arrowHeadPoint.distance(arrowTail) < 6.0) {
                arrowTail = automaticTailFor(arrowHead, image.getWidth(), image.getHeight());
                addPlacement(
                    placementFromHeadFirstGesture(
                        arrowHead, arrowTail, image.getWidth(), image.getHeight()));
              } else {
                addPlacement(
                    placementFromHeadFirstGesture(
                        arrowHead, arrowTail, image.getWidth(), image.getHeight()));
              }
              arrowHeadPoint = null;
            }
          };
      addMouseListener(mouseAdapter);
      addMouseMotionListener(mouseAdapter);
    }

    void setPlacementListener(java.util.function.Consumer<List<ArrowPlacement>> listener) {
      placementListener = listener == null ? ignored -> {} : listener;
    }

    List<ArrowPlacement> getPlacements() {
      return List.copyOf(placements);
    }

    void addPlacement(ArrowPlacement placement) {
      placements.add(placement);
      pendingPlacement = null;
      placementsChanged();
    }

    void removeLastPlacement() {
      if (!placements.isEmpty()) {
        placements.removeLast();
        placementsChanged();
      }
    }

    void clearPlacements() {
      placements.clear();
      pendingPlacement = null;
      placementsChanged();
    }

    private void setPendingPlacement(ArrowPlacement placement) {
      pendingPlacement = placement;
      repaint();
    }

    private void placementsChanged() {
      placementListener.accept(List.copyOf(placements));
      repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      super.paintComponent(graphics);
      Rectangle bounds = imageBounds();
      Graphics2D graphics2D = (Graphics2D) graphics.create();
      graphics2D.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      graphics2D.drawImage(image, bounds.x, bounds.y, bounds.width, bounds.height, null);
      if (!placements.isEmpty() || pendingPlacement != null) {
        graphics2D.translate(bounds.x, bounds.y);
        for (ArrowPlacement placement : placements) {
          ArrowRenderer.draw(graphics2D, bounds.width, bounds.height, placement);
        }
        if (pendingPlacement != null) {
          ArrowRenderer.draw(graphics2D, bounds.width, bounds.height, pendingPlacement);
        }
      }
      graphics2D.dispose();
    }

    private Point toImagePoint(Point componentPoint) {
      Rectangle bounds = imageBounds();
      if (!bounds.contains(componentPoint)) {
        return null;
      }
      double scaleX = image.getWidth() / (double) bounds.width;
      double scaleY = image.getHeight() / (double) bounds.height;
      int x = (int) Math.round((componentPoint.x - bounds.x) * scaleX);
      int y = (int) Math.round((componentPoint.y - bounds.y) * scaleY);
      return new Point(
          Math.clamp(x, 0, image.getWidth() - 1), Math.clamp(y, 0, image.getHeight() - 1));
    }

    private Rectangle imageBounds() {
      int availableWidth = Math.max(1, getWidth());
      int availableHeight = Math.max(1, getHeight());
      double scale =
          Math.min(
              availableWidth / (double) image.getWidth(),
              availableHeight / (double) image.getHeight());
      int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
      int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
      return new Rectangle(
          (availableWidth - width) / 2, (availableHeight - height) / 2, width, height);
    }
  }
}
