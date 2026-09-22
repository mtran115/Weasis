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
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

/** Explicit review only: never opens itself or takes focus when inference completes. */
final class LumbarLevelReview {
  record Decision(String decision, String basis, List<LumbarLevelMap.Landmark> points) {}

  private static final List<String> BASIS =
      List.of("reporting_convention", "whole_spine_count", "prior_correlation", "uncertain");

  static Decision show(
      Component owner,
      LumbarLevelMap map,
      BufferedImage image,
      List<LumbarLevelMap.Landmark> initial,
      String previousBasis) {
    var points = new ArrayList<>(initial);
    JPanel content = new JPanel(new BorderLayout(8, 8));
    var labels = new JPanel(new GridLayout(0, 1, 3, 3));
    Preview preview = new Preview(image, map.reference().geometry(), points);
    Runnable rebuild =
        () -> {
          labels.removeAll();
          for (int index = 0; index < points.size(); index++) {
            int row = index;
            var choices = new ArrayList<String>();
            choices.add(LumbarLevelMap.UNASSIGNED);
            choices.addAll(LumbarLevelMap.LEVELS);
            var choice = new JComboBox<>(choices.toArray(String[]::new));
            choice.setSelectedItem(
                LumbarLevelMap.LEVELS.contains(points.get(row).level())
                    ? points.get(row).level()
                    : LumbarLevelMap.UNASSIGNED);
            choice.addActionListener(
                event -> {
                  var old = points.get(row);
                  points.set(
                      row,
                      new LumbarLevelMap.Landmark(
                          old.id(), (String) choice.getSelectedItem(), old.lps()));
                  preview.selected = row;
                  preview.repaint();
                });
            var rowPanel = new JPanel(new BorderLayout(4, 0));
            rowPanel.add(new JLabel("Disc " + (row + 1)), BorderLayout.WEST);
            rowPanel.add(choice, BorderLayout.CENTER);
            labels.add(rowPanel);
          }
          labels.revalidate();
          labels.repaint();
        };
    preview.changed = rebuild;
    rebuild.run();
    var remove = new JButton("Remove selected disc");
    remove.addActionListener(
        event -> {
          if (preview.selected >= 0 && points.size() > 2) {
            points.remove(preview.selected);
            preview.selected = -1;
            rebuild.run();
            preview.repaint();
          }
        });
    JPanel right = new JPanel(new BorderLayout(4, 4));
    right.add(labels, BorderLayout.NORTH);
    right.add(remove, BorderLayout.SOUTH);
    content.add(preview, BorderLayout.CENTER);
    content.add(right, BorderLayout.EAST);
    var basis =
        new JComboBox<>(
            new String[] {
              "Reporting convention",
              "Counted on whole-spine imaging",
              "Correlated with prior",
              "Uncertain numbering"
            });
    basis.setSelectedIndex(Math.max(0, BASIS.indexOf(previousBasis)));
    JPanel footer = new JPanel(new GridLayout(0, 1, 3, 3));
    var numbering = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEADING, 3, 0));
    var lowest = new JComboBox<>(new String[] {"L5-S1", "L6-S1"});
    var renumber = new JButton("Number upward");
    numbering.add(new JLabel("Lowest disc:"));
    numbering.add(lowest);
    numbering.add(renumber);
    renumber.addActionListener(
        event -> {
          List<LumbarLevelMap.Landmark> numbered;
          try {
            numbered = numberUpward(points, (String) lowest.getSelectedItem());
          } catch (IllegalArgumentException error) {
            JOptionPane.showMessageDialog(
                owner, "Remove extra upper landmarks or label these levels individually.");
            return;
          }
          points.clear();
          points.addAll(numbered);
          rebuild.run();
          preview.repaint();
        });
    footer.add(numbering);
    footer.add(new JLabel("Drag dots to correct location; Shift-click to add a missing disc."));
    footer.add(new JLabel("Lumbar-only images may not establish the absolute vertebral count."));
    List<String> warnings =
        map.numberingUncertain()
            ? List.of(
                "Numbering uncertain—review required. Assign the entire sequence before confirming.")
            : map.reviewReasons().stream().skip(1).toList();
    for (String reason : warnings) {
      JLabel warning = new JLabel(reason);
      warning.setForeground(new Color(200, 145, 50));
      footer.add(warning);
    }
    footer.add(basis);
    content.add(footer, BorderLayout.SOUTH);
    while (true) {
      int action =
          JOptionPane.showOptionDialog(
              owner,
              content,
              "Review lumbar numbering · pilot",
              JOptionPane.DEFAULT_OPTION,
              JOptionPane.PLAIN_MESSAGE,
              null,
              new String[] {"Confirm map", "Mark uncertain", "Skip", "Cancel"},
              "Confirm map");
      if (action < 0 || action == 3) return null;
      if (action == 2) return new Decision("skipped", "uncertain", map.points());
      points.sort(Comparator.comparingDouble((LumbarLevelMap.Landmark p) -> -p.lps().get(2)));
      rebuild.run();
      try {
        LumbarLevelMap.validateGeometry(points, map.reference().geometry());
        String selectedBasis = BASIS.get(basis.getSelectedIndex());
        if (action == 1 || selectedBasis.equals("uncertain"))
          return new Decision("uncertain", "uncertain", List.copyOf(points));
        LumbarLevelMap.validatePoints(points, map.reference().geometry());
        return new Decision(
            points.equals(map.points()) ? "accepted" : "corrected",
            selectedBasis,
            List.copyOf(points));
      } catch (IllegalArgumentException error) {
        JOptionPane.showMessageDialog(
            owner, error.getMessage(), "Check level map", JOptionPane.WARNING_MESSAGE);
      }
    }
  }

  static List<LumbarLevelMap.Landmark> numberUpward(
      List<LumbarLevelMap.Landmark> points, String lowest) {
    var sequence =
        switch (lowest) {
          case "L5-S1" -> List.of("T11-T12", "T12-L1", "L1-L2", "L2-L3", "L3-L4", "L4-L5", "L5-S1");
          case "L6-S1" ->
              List.of("T11-T12", "T12-L1", "L1-L2", "L2-L3", "L3-L4", "L4-L5", "L5-L6", "L6-S1");
          default -> throw new IllegalArgumentException("Choose the lowest disc level");
        };
    if (points.size() > sequence.size())
      throw new IllegalArgumentException("Too many landmarks for this sequence");
    var ordered =
        points.stream()
            .sorted(Comparator.comparingDouble((LumbarLevelMap.Landmark p) -> -p.lps().get(2)))
            .toList();
    var result = new ArrayList<LumbarLevelMap.Landmark>();
    for (int i = 0; i < ordered.size(); i++) {
      var old = ordered.get(i);
      result.add(
          new LumbarLevelMap.Landmark(
              old.id(), sequence.get(sequence.size() - ordered.size() + i), old.lps()));
    }
    return List.copyOf(result);
  }

  private static final class Preview extends JComponent {
    private final BufferedImage image;
    private final ImageGeometry geometry;
    private final ArrayList<LumbarLevelMap.Landmark> points;
    private int selected = -1;
    private Runnable changed = () -> {};

    Preview(
        BufferedImage image, ImageGeometry geometry, ArrayList<LumbarLevelMap.Landmark> points) {
      this.image = image;
      this.geometry = geometry;
      this.points = points;
      setPreferredSize(new Dimension(470, 510));
      setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
      var mouse =
          new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
              Point2D p = source(event);
              if (!geometry.contains(p.getX(), p.getY())) return;
              if (event.isShiftDown() && points.size() < LumbarLevelMap.MAX_POINTS) {
                points.add(
                    new LumbarLevelMap.Landmark(
                        UUID.randomUUID().toString(),
                        LumbarLevelMap.UNASSIGNED,
                        geometry.patientPosition(p.getX(), p.getY())));
                selected = points.size() - 1;
                changed.run();
                repaint();
                return;
              }
              selected = -1;
              double nearest = 24;
              for (int i = 0; i < points.size(); i++) {
                Point2D location = screen(LumbarLevelMap.pixel(geometry, points.get(i).lps()));
                double distance = location.distance(event.getPoint());
                if (distance < nearest) {
                  nearest = distance;
                  selected = i;
                }
              }
              repaint();
            }

            @Override
            public void mouseDragged(MouseEvent event) {
              if (selected < 0) return;
              Point2D p = source(event);
              if (!geometry.contains(p.getX(), p.getY())) return;
              var old = points.get(selected);
              points.set(
                  selected,
                  new LumbarLevelMap.Landmark(
                      old.id(), old.level(), geometry.patientPosition(p.getX(), p.getY())));
              repaint();
            }
          };
      addMouseListener(mouse);
      addMouseMotionListener(mouse);
    }

    private double scale() {
      return Math.min(
          getWidth() / (image.getWidth() * geometry.pixelSpacing().get(1)),
          getHeight() / (image.getHeight() * geometry.pixelSpacing().get(0)));
    }

    private double sx() {
      return scale() * geometry.pixelSpacing().get(1);
    }

    private double sy() {
      return scale() * geometry.pixelSpacing().get(0);
    }

    private double ox() {
      return (getWidth() - image.getWidth() * sx()) / 2;
    }

    private double oy() {
      return (getHeight() - image.getHeight() * sy()) / 2;
    }

    private Point2D screen(Point2D p) {
      return new Point2D.Double(ox() + p.getX() * sx(), oy() + p.getY() * sy());
    }

    private Point2D source(MouseEvent e) {
      return new Point2D.Double((e.getX() - ox()) / sx(), (e.getY() - oy()) / sy());
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      super.paintComponent(graphics);
      graphics.setColor(Color.BLACK);
      graphics.fillRect(0, 0, getWidth(), getHeight());
      graphics.drawImage(
          image,
          (int) ox(),
          (int) oy(),
          (int) (image.getWidth() * sx()),
          (int) (image.getHeight() * sy()),
          null);
      for (int i = 0; i < points.size(); i++) {
        var point = points.get(i);
        Point2D p = screen(LumbarLevelMap.pixel(geometry, point.lps()));
        int x = (int) p.getX(), y = (int) p.getY();
        graphics.setColor(i == selected ? Color.YELLOW : new Color(115, 190, 255));
        graphics.fillOval(x - 4, y - 4, 8, 8);
        graphics.setColor(Color.BLACK);
        graphics.drawString(point.displayLabel(i), x + 10, y + 1);
        graphics.setColor(i == selected ? Color.YELLOW : new Color(115, 190, 255));
        graphics.drawString(point.displayLabel(i), x + 9, y);
      }
    }
  }
}
