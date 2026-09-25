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
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

/**
 * Shows the composer views either as tabs or as resizable side-by-side columns. Callers address
 * views by index and never touch the tab or split containers directly. Called only on the Swing
 * thread.
 */
final class ComposerViewLayout {
  enum Mode {
    TABS,
    COLUMNS
  }

  static final int COMPOSE = 0;
  static final int KEY_IMAGES = 1;
  static final int PREVIEW = 2;

  private final JPanel holder = new JPanel(new BorderLayout());
  private final JTabbedPane tabs = new JTabbedPane();
  private final List<String> titles;
  private final List<Component> views;
  private final List<JPanel> columns = new ArrayList<>();
  private final List<JLabel> columnTitles = new ArrayList<>();
  private final JSplitPane trailingSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
  private final JSplitPane columnSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
  private final List<Runnable> visibilityListeners = new ArrayList<>();
  private final String[] toolTips = new String[3];
  private final boolean[] enabled = {true, true, true};
  private Mode mode;
  private int selectedTab = COMPOSE;
  private boolean columnsSized;

  ComposerViewLayout(List<String> titles, List<Component> views) {
    if (titles.size() != 3 || views.size() != 3) {
      throw new IllegalArgumentException("The composer has exactly three views.");
    }
    this.titles = List.copyOf(titles);
    this.views = List.copyOf(views);
    for (String title : titles) {
      JLabel label = new JLabel(title);
      label.setFont(label.getFont().deriveFont(Font.BOLD));
      label.setBorder(BorderFactory.createEmptyBorder(2, 4, 4, 4));
      columnTitles.add(label);
      JPanel column = new JPanel(new BorderLayout());
      column.add(label, BorderLayout.NORTH);
      // Let the reader drag dividers well past each view's preferred width.
      column.setMinimumSize(new Dimension(160, 0));
      columns.add(column);
    }
    for (JSplitPane split : List.of(trailingSplit, columnSplit)) {
      split.setContinuousLayout(true);
      split.setBorder(null);
    }
    trailingSplit.setResizeWeight(0.5);
    columnSplit.setResizeWeight(0.4);
    tabs.addChangeListener(event -> fireVisibilityChanged());
    setMode(Mode.TABS);
  }

  JComponent component() {
    return holder;
  }

  Mode mode() {
    return mode;
  }

  void setMode(Mode newMode) {
    Objects.requireNonNull(newMode);
    if (newMode == mode) return;
    if (mode == Mode.TABS) {
      selectedTab = Math.max(COMPOSE, tabs.getSelectedIndex());
    }
    mode = newMode;
    holder.removeAll();
    tabs.removeAll();
    columns.forEach(Container::removeAll);
    if (newMode == Mode.TABS) {
      for (int view = 0; view < views.size(); view++) {
        tabs.addTab(titles.get(view), views.get(view));
        tabs.setToolTipTextAt(view, toolTips[view]);
        tabs.setEnabledAt(view, enabled[view]);
      }
      tabs.setSelectedIndex(selectedTab);
      holder.add(tabs, BorderLayout.CENTER);
    } else {
      for (int view = 0; view < views.size(); view++) {
        columns.get(view).add(columnTitles.get(view), BorderLayout.NORTH);
        columns.get(view).add(views.get(view), BorderLayout.CENTER);
      }
      trailingSplit.setLeftComponent(columns.get(KEY_IMAGES));
      trailingSplit.setRightComponent(columns.get(PREVIEW));
      columnSplit.setLeftComponent(columns.get(COMPOSE));
      columnSplit.setRightComponent(trailingSplit);
      holder.add(columnSplit, BorderLayout.CENTER);
      if (!columnsSized) {
        columnsSized = true;
        // Proportional divider locations only apply once the split panes have a size.
        SwingUtilities.invokeLater(
            () -> {
              columnSplit.setDividerLocation(0.4);
              trailingSplit.setDividerLocation(0.5);
            });
      }
    }
    holder.revalidate();
    holder.repaint();
    fireVisibilityChanged();
  }

  /** The selected tab, or -1 in columns where every view is shown. */
  int selectedView() {
    return mode == Mode.TABS ? tabs.getSelectedIndex() : -1;
  }

  /** Whether the view can currently be seen: always in columns, only the selected tab in tabs. */
  boolean isVisible(int view) {
    return mode == Mode.COLUMNS || tabs.getSelectedIndex() == view;
  }

  /** Selects the tab, or moves focus into the column. */
  void show(int view) {
    if (!enabled[view]) return;
    if (mode == Mode.TABS) {
      tabs.setSelectedIndex(view);
      return;
    }
    firstFocusable(views.get(view)).ifPresent(Component::requestFocusInWindow);
  }

  /** A component that can take keyboard focus for the view. */
  Component focusTarget(int view) {
    return mode == Mode.TABS ? tabs : firstFocusable(views.get(view)).orElse(holder);
  }

  boolean contains(Component component) {
    return component != null && SwingUtilities.isDescendingFrom(component, holder);
  }

  void setToolTip(int view, String tooltip) {
    toolTips[view] = tooltip;
    columnTitles.get(view).setToolTipText(tooltip);
    if (mode == Mode.TABS) {
      tabs.setToolTipTextAt(view, tooltip);
    }
  }

  String toolTip(int view) {
    return toolTips[view];
  }

  boolean isEnabledAt(int view) {
    return enabled[view] && holder.isEnabled();
  }

  void setEnabledAt(int view, boolean viewEnabled) {
    enabled[view] = viewEnabled;
    if (mode == Mode.TABS) {
      tabs.setEnabledAt(view, viewEnabled);
    }
  }

  /** Runs whenever the set of visible views may have changed (tab switch or mode change). */
  void addVisibilityListener(Runnable listener) {
    visibilityListeners.add(Objects.requireNonNull(listener));
  }

  private void fireVisibilityChanged() {
    visibilityListeners.forEach(Runnable::run);
  }

  private static Optional<Component> firstFocusable(Component component) {
    if (component.isFocusable()
        && component.isEnabled()
        && component.isShowing()
        && !(component instanceof JPanel)) {
      return Optional.of(component);
    }
    if (component instanceof Container container) {
      for (Component child : container.getComponents()) {
        Optional<Component> found = firstFocusable(child);
        if (found.isPresent()) return found;
      }
    }
    return Optional.empty();
  }
}
