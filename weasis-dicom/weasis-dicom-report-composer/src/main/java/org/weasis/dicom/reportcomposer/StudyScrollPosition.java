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

import java.awt.event.HierarchyEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/** Remembers each study's Compose position across deferred layout and hidden-tab updates. */
final class StudyScrollPosition {
  private final JScrollPane scrollPane;
  private final Map<String, Integer> positions = new HashMap<>();
  private String studyKey;
  private boolean composeSelected = true;
  private Integer pendingPosition;
  private long restoreRequest;

  StudyScrollPosition(JScrollPane scrollPane) {
    this.scrollPane = scrollPane;
    scrollPane.addHierarchyListener(
        event -> {
          if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
            if (scrollPane.isShowing()) {
              restorePosition();
            } else if (composeSelected) {
              rememberPosition();
              pendingPosition = positions.getOrDefault(studyKey, 0);
              restoreRequest++;
            }
          }
        });
  }

  void selectStudy(String selectedStudyKey) {
    if (Objects.equals(studyKey, selectedStudyKey)) {
      return;
    }
    rememberPosition();
    studyKey = selectedStudyKey;
    pendingPosition = positions.getOrDefault(studyKey, 0);
    restoreRequest++;
  }

  void setComposeSelected(boolean selected) {
    if (composeSelected == selected) {
      return;
    }
    rememberPosition();
    composeSelected = selected;
    restoreRequest++;
    if (selected) {
      if (pendingPosition == null) {
        pendingPosition = positions.getOrDefault(studyKey, 0);
      }
      restorePosition();
    }
  }

  void restorePosition() {
    if (!composeSelected || pendingPosition == null || !scrollPane.isShowing()) {
      return;
    }
    long request = ++restoreRequest;
    SwingUtilities.invokeLater(
        () -> {
          if (request != restoreRequest || !composeSelected || !scrollPane.isShowing()) {
            return;
          }
          scrollPane.validate();
          scrollPane.getVerticalScrollBar().setValue(pendingPosition);
          pendingPosition = null;
        });
  }

  private void rememberPosition() {
    if (studyKey != null && composeSelected) {
      positions.put(
          studyKey,
          pendingPosition == null ? scrollPane.getVerticalScrollBar().getValue() : pendingPosition);
    }
  }
}
