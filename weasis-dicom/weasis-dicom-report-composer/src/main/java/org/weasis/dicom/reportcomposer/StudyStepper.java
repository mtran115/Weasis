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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.SwingUtilities;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.DicomSorter;
import org.weasis.dicom.explorer.main.DicomExplorer;
import org.weasis.dicom.explorer.main.StudyPane;
import org.weasis.dicom.reportcomposer.ViewerMacros.SequenceOrder;
import org.weasis.dicom.viewer2d.EventManager;

/**
 * Opens the next or previous study in the explorer's list, across patients, and hangs it: the
 * layout of its sequence order, with the viewports filled in that order.
 */
final class StudyStepper {
  private final ViewerMacros macros;

  StudyStepper(ViewerMacros macros) {
    this.macros = macros;
  }

  /** The item after {@code current}, or before it going backwards; the first without a current. */
  static <T> Optional<T> adjacent(List<T> items, T current, boolean backwards) {
    int index = current == null ? -1 : items.indexOf(current);
    if (index < 0) return backwards ? Optional.empty() : items.stream().findFirst();
    int target = index + (backwards ? -1 : 1);
    return target >= 0 && target < items.size() ? Optional.of(items.get(target)) : Optional.empty();
  }

  /** The series for each viewport in turn; a viewport past the end gets none. */
  static <T> List<Optional<T>> slots(List<T> ordered, int viewports) {
    List<Optional<T>> slots = new ArrayList<>();
    for (int i = 0; i < viewports; i++) {
      slots.add(i < ordered.size() ? Optional.of(ordered.get(i)) : Optional.empty());
    }
    return slots;
  }

  void step(MediaSeriesGroup current, boolean backwards) {
    DataExplorerView view = GuiUtils.getUICore().getExplorerPlugin(DicomExplorer.NAME);
    ImageViewerPlugin<DicomImageElement> viewer =
        EventManager.getInstance().getSelectedView2dContainer();
    if (!(view instanceof DicomExplorer explorer) || viewer == null) return;
    adjacent(orderedStudies(explorer), current, backwards)
        .ifPresent(study -> hang(viewer, explorer, study));
  }

  // Patients and studies as the explorer lists them, leaving out studies without images.
  private static List<MediaSeriesGroup> orderedStudies(DicomExplorer explorer) {
    DicomModel model = explorer.getDataExplorerModel();
    List<MediaSeriesGroup> patients;
    synchronized (model) {
      patients = new ArrayList<>(model.getChildren(MediaSeriesGroupNode.rootNode));
    }
    patients.sort(DicomSorter.PATIENT_COMPARATOR);
    List<MediaSeriesGroup> studies = new ArrayList<>();
    for (MediaSeriesGroup patient : patients) {
      for (StudyPane pane : explorer.getPaneManager().getStudyList(patient)) {
        MediaSeriesGroup study = pane.getDicomStudy();
        if (!SequenceStepper.imageSeries(model, study).isEmpty()) studies.add(study);
      }
    }
    return studies;
  }

  private void hang(
      ImageViewerPlugin<DicomImageElement> viewer, DicomExplorer explorer, MediaSeriesGroup study) {
    DicomModel model = explorer.getDataExplorerModel();
    SequenceOrder hanging = macros.hangingFor(SequenceStepper.examOf(study));
    viewer.changeLayoutModelById(hanging.layout());
    List<ViewCanvas<DicomImageElement>> views = viewer.getImagePanels(false);
    List<Optional<DicomSeries>> slots =
        slots(
            SequenceStepper.ordered(
                SequenceStepper.imageSeries(model, study),
                SequenceStepper::description,
                hanging.order()),
            views.size());
    // The first viewport is filled last so that it ends up selected.
    for (int i = views.size() - 1; i >= 0; i--) {
      int slot = i;
      slots
          .get(i)
          .ifPresentOrElse(
              series -> explorer.displaySeriesInViewSlot(series, null, slot),
              () -> views.get(slot).setSeries(null));
    }
    // Once the viewer has settled on the new series, show the study's thumbnails for loading
    // other sequences by hand.
    SwingUtilities.invokeLater(() -> explorer.revealStudy(study));
  }
}
