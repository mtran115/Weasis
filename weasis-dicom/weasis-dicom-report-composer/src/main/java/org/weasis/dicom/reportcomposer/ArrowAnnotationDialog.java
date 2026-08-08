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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import org.weasis.core.api.gui.util.GuiUtils;

final class ArrowAnnotationDialog extends JDialog {
  private final ArrowCanvas arrowCanvas;
  private boolean accepted;

  private ArrowAnnotationDialog(Window owner, BufferedImage image) {
    super(owner, "Key Image Arrow", ModalityType.APPLICATION_MODAL);
    setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    arrowCanvas = new ArrowCanvas(image);
    init();
  }

  static AnnotationResult showDialog(JComponent parent, BufferedImage image) {
    Window owner = SwingUtilities.getWindowAncestor(parent);
    ArrowAnnotationDialog dialog = new ArrowAnnotationDialog(owner, image);
    dialog.setLocationRelativeTo(parent);
    dialog.setVisible(true);
    return new AnnotationResult(dialog.accepted, dialog.arrowCanvas.getPlacement());
  }

  private void init() {
    setLayout(new BorderLayout(8, 8));
    JLabel instructions =
        new JLabel(
            "Drag from the arrow tail to the finding, or click the finding for automatic placement.",
            SwingConstants.CENTER);
    instructions.setBorder(GuiUtils.getEmptyBorder(8, 8, 0, 8));
    add(instructions, BorderLayout.NORTH);
    add(arrowCanvas, BorderLayout.CENTER);

    JButton clearButton = new JButton("Clear");
    clearButton.addActionListener(event -> arrowCanvas.setPlacement(null));
    JButton noArrowButton = new JButton("Use Without Arrow");
    noArrowButton.addActionListener(
        event -> {
          arrowCanvas.setPlacement(null);
          accepted = true;
          dispose();
        });
    JButton cancelButton = new JButton("Cancel");
    cancelButton.addActionListener(event -> dispose());
    JButton useButton = new JButton("Use Arrow");
    useButton.setEnabled(false);
    arrowCanvas.setPlacementListener(placement -> useButton.setEnabled(placement != null));
    useButton.addActionListener(
        event -> {
          accepted = true;
          dispose();
        });
    getRootPane().setDefaultButton(useButton);

    JPanel buttons =
        GuiUtils.getFlowLayoutPanel(
            java.awt.FlowLayout.TRAILING,
            8,
            8,
            clearButton,
            noArrowButton,
            cancelButton,
            useButton);
    add(buttons, BorderLayout.SOUTH);
    setMinimumSize(new Dimension(720, 560));
    setSize(new Dimension(980, 760));
  }

  record AnnotationResult(boolean accepted, ArrowPlacement placement) {}

  private static final class ArrowCanvas extends JComponent {
    private final BufferedImage image;
    private Point pressPoint;
    private ArrowPlacement placement;
    private java.util.function.Consumer<ArrowPlacement> placementListener = ignored -> {};

    ArrowCanvas(BufferedImage image) {
      this.image = image;
      setOpaque(true);
      setBackground(java.awt.Color.BLACK);
      setBorder(BorderFactory.createLineBorder(java.awt.Color.DARK_GRAY));
      MouseAdapter mouseAdapter =
          new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
              pressPoint = toImagePoint(event.getPoint());
            }

            @Override
            public void mouseDragged(MouseEvent event) {
              Point current = toImagePoint(event.getPoint());
              if (pressPoint != null && current != null) {
                setPlacement(toPlacement(pressPoint, current));
              }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
              Point releasePoint = toImagePoint(event.getPoint());
              if (releasePoint == null) {
                pressPoint = null;
                return;
              }
              if (pressPoint == null || pressPoint.distance(releasePoint) < 6.0) {
                int offset = Math.max(50, Math.min(image.getWidth(), image.getHeight()) / 7);
                Point automaticTail =
                    new Point(
                        Math.max(0, releasePoint.x - offset), Math.max(0, releasePoint.y - offset));
                if (automaticTail.distance(releasePoint) < 10.0) {
                  automaticTail =
                      new Point(
                          Math.min(image.getWidth() - 1, releasePoint.x + offset),
                          Math.min(image.getHeight() - 1, releasePoint.y + offset));
                }
                setPlacement(toPlacement(automaticTail, releasePoint));
              } else {
                setPlacement(toPlacement(pressPoint, releasePoint));
              }
              pressPoint = null;
            }
          };
      addMouseListener(mouseAdapter);
      addMouseMotionListener(mouseAdapter);
    }

    void setPlacementListener(java.util.function.Consumer<ArrowPlacement> listener) {
      placementListener = listener == null ? ignored -> {} : listener;
    }

    ArrowPlacement getPlacement() {
      return placement;
    }

    void setPlacement(ArrowPlacement placement) {
      this.placement = placement;
      placementListener.accept(placement);
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
      if (placement != null) {
        graphics2D.translate(bounds.x, bounds.y);
        ArrowRenderer.draw(graphics2D, bounds.width, bounds.height, placement);
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

    private ArrowPlacement toPlacement(Point tail, Point tip) {
      double width = Math.max(1, image.getWidth() - 1);
      double height = Math.max(1, image.getHeight() - 1);
      return new ArrowPlacement(tail.x / width, tail.y / height, tip.x / width, tip.y / height);
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
