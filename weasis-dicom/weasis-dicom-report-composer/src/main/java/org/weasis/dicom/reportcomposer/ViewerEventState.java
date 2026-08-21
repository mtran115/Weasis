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

import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;

/** Tracks viewer structure without inspecting the image selected within a series. */
final class ViewerEventState {
  private Object viewer;
  private Object canvas;
  private Object series;

  boolean observe(EVENT type, Object currentViewer, Object currentCanvas, Object currentSeries) {
    boolean structureChanged =
        viewer != currentViewer || canvas != currentCanvas || series != currentSeries;
    viewer = currentViewer;
    canvas = currentCanvas;
    series = currentSeries;
    return EVENT.LAYOUT.equals(type) || structureChanged;
  }
}
