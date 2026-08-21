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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;

class ViewerEventStateTest {
  @Test
  void repeatedSliceSelectionsDoNotRequestStructuralSynchronization() {
    ViewerEventState state = new ViewerEventState();
    Object viewer = new Object();
    Object canvas = new Object();
    Object series = new Object();

    assertTrue(state.observe(EVENT.SELECT, viewer, canvas, series));
    for (int index = 0; index < 100; index++) {
      assertFalse(state.observe(EVENT.SELECT, viewer, canvas, series));
    }
  }

  @Test
  void replacingTheSeriesRequestsSynchronizationOnce() {
    ViewerEventState state = new ViewerEventState();
    Object viewer = new Object();
    Object canvas = new Object();
    Object firstSeries = new Object();
    Object secondSeries = new Object();

    assertTrue(state.observe(EVENT.SELECT, viewer, canvas, firstSeries));
    assertTrue(state.observe(EVENT.SELECT, viewer, canvas, secondSeries));
    assertFalse(state.observe(EVENT.SELECT, viewer, canvas, secondSeries));
  }

  @Test
  void changingCanvasOrViewerRequestsSynchronization() {
    ViewerEventState state = new ViewerEventState();
    Object viewer = new Object();
    Object canvas = new Object();
    Object series = new Object();

    assertTrue(state.observe(EVENT.SELECT, viewer, canvas, series));
    assertTrue(state.observe(EVENT.SELECT_VIEW, viewer, new Object(), series));
    assertTrue(state.observe(EVENT.SELECT_VIEW, new Object(), canvas, series));
  }

  @Test
  void layoutEventsAlwaysRequestSynchronization() {
    ViewerEventState state = new ViewerEventState();
    Object viewer = new Object();
    Object canvas = new Object();
    Object series = new Object();

    assertTrue(state.observe(EVENT.LAYOUT, viewer, canvas, series));
    assertTrue(state.observe(EVENT.LAYOUT, viewer, canvas, series));
  }
}
