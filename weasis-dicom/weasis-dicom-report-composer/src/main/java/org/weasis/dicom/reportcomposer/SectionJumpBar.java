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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.KeyedFinding;

/**
 * Section buttons for a structured form, mounted as the Compose scroll pane's fixed header so they
 * stay visible while the form scrolls. The buttons wrap into more rows as the pane narrows. A dot
 * marks sections whose generated findings are present.
 */
final class SectionJumpBar extends JPanel implements Scrollable {
  private static final int MAX_COLUMNS = 8;
  private static final int GAP = 3;

  /** A form section and the finding-key prefixes generated from its controls. */
  record Section(String label, Component target, List<String> keyPrefixes) {
    Section {
      keyPrefixes = List.copyOf(keyPrefixes);
    }
  }

  private final List<Section> sections;
  private final Supplier<Set<String>> findingKeys;
  private final List<JButton> buttons = new ArrayList<>();
  private final GridLayout grid;

  SectionJumpBar(List<Section> sections, Supplier<Set<String>> findingKeys) {
    super(new GridLayout(0, Math.min(sections.size(), MAX_COLUMNS), GAP, GAP));
    this.grid = (GridLayout) getLayout();
    this.sections = List.copyOf(sections);
    this.findingKeys = findingKeys;
    setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Color.GRAY),
            BorderFactory.createEmptyBorder(4, 4, 4, 4)));
    for (Section section : this.sections) {
      JButton button = new JButton(section.label());
      button.setFont(button.getFont().deriveFont(11f));
      button.setMargin(new Insets(2, 3, 2, 3));
      // Keep the caret and hover-driven form shortcuts where they are.
      button.setFocusable(false);
      button.setToolTipText("Jump to " + section.label());
      button.addActionListener(event -> jumpTo(section));
      buttons.add(button);
      add(button);
    }
    // The header's height depends on its width, so a width that changes the column count needs
    // another layout pass.
    addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentResized(ComponentEvent event) {
            if (grid.getColumns() != columnsFor(getWidth())) revalidate();
          }
        });
  }

  /** The most columns that fit {@code width}, spread so the last row is not left nearly empty. */
  int columnsFor(int width) {
    int max = Math.min(buttons.size(), MAX_COLUMNS);
    if (max <= 1 || width <= 0) return Math.max(1, max);
    Insets insets = getInsets();
    int cell = buttons.stream().mapToInt(button -> button.getPreferredSize().width).max().orElse(1);
    int fit = Math.clamp((width - insets.left - insets.right + GAP) / (cell + GAP), 1, max);
    return Math.ceilDiv(buttons.size(), Math.ceilDiv(buttons.size(), fit));
  }

  @Override
  public Dimension getPreferredSize() {
    grid.setColumns(columnsFor(getWidth()));
    return super.getPreferredSize();
  }

  @Override
  public void doLayout() {
    grid.setColumns(columnsFor(getWidth()));
    super.doLayout();
  }

  @Override
  public Dimension getPreferredScrollableViewportSize() {
    return getPreferredSize();
  }

  @Override
  public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
    return 16;
  }

  @Override
  public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
    return 16;
  }

  @Override
  public boolean getScrollableTracksViewportWidth() {
    return true;
  }

  @Override
  public boolean getScrollableTracksViewportHeight() {
    return false;
  }

  static Set<String> keys(Collection<KeyedFinding> findings) {
    return findings.stream().map(KeyedFinding::key).collect(Collectors.toSet());
  }

  static boolean hasFindings(Set<String> keys, List<String> prefixes) {
    return keys.stream().anyMatch(key -> prefixes.stream().anyMatch(key::startsWith));
  }

  void refresh() {
    Set<String> keys = findingKeys.get();
    for (int index = 0; index < sections.size(); index++) {
      boolean marked = hasFindings(keys, sections.get(index).keyPrefixes());
      JButton button = buttons.get(index);
      button.setText(sections.get(index).label() + (marked ? " •" : ""));
      button.setFont(button.getFont().deriveFont(marked ? Font.BOLD : Font.PLAIN));
    }
  }

  List<String> labels() {
    return buttons.stream().map(JButton::getText).toList();
  }

  /** Scrolls so the section's top sits just below this bar. */
  void jumpTo(Section section) {
    Component target = section.target();
    if (!(SwingUtilities.getAncestorOfClass(JViewport.class, target) instanceof JViewport viewport)
        || viewport.getView() == null
        || target.getParent() == null) {
      return;
    }
    Component view = viewport.getView();
    Rectangle bounds =
        SwingUtilities.convertRectangle(target.getParent(), target.getBounds(), view);
    int maxY = Math.max(0, view.getHeight() - viewport.getExtentSize().height);
    viewport.setViewPosition(
        new Point(viewport.getViewPosition().x, Math.max(0, Math.min(bounds.y, maxY))));
  }
}
