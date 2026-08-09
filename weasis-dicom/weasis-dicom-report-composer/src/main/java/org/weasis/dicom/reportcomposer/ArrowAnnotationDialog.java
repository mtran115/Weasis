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
            "Press on the finding, then drag outward to place the tail, or click for an automatic arrow.",
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
    private Point arrowHeadPoint;
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
              arrowHeadPoint = toImagePoint(event.getPoint());
            }

            @Override
            public void mouseDragged(MouseEvent event) {
              Point arrowTail = toImagePoint(event.getPoint());
              if (arrowHeadPoint != null && arrowTail != null) {
                setPlacement(
                    placementFromHeadFirstGesture(
                        arrowHeadPoint, arrowTail, image.getWidth(), image.getHeight()));
              }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
              Point arrowTail = toImagePoint(event.getPoint());
              if (arrowTail == null) {
                arrowHeadPoint = null;
                return;
              }
              Point arrowHead = arrowHeadPoint == null ? arrowTail : arrowHeadPoint;
              if (arrowHeadPoint == null || arrowHeadPoint.distance(arrowTail) < 6.0) {
                arrowTail = automaticTailFor(arrowHead, image.getWidth(), image.getHeight());
                setPlacement(
                    placementFromHeadFirstGesture(
                        arrowHead, arrowTail, image.getWidth(), image.getHeight()));
              } else {
                setPlacement(
                    placementFromHeadFirstGesture(
                        arrowHead, arrowTail, image.getWidth(), image.getHeight()));
              }
              arrowHeadPoint = null;
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
