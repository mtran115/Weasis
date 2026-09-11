/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.lut.PresetWindowLevel;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.display.MrWindowLevelRange;
import org.weasis.opencv.op.lut.DefaultWlPresentation;
import org.weasis.opencv.op.lut.LutShape;

/** One explicit Auto calculation; never invoked by slice scrolling. */
final class MrAutoWindowLevel {
  private MrAutoWindowLevel() {}

  /** Snapshot the displayed stack on the EDT without reading pixel data. Current image is first. */
  static List<DicomImageElement> nearbyImages(ViewCanvas<DicomImageElement> view) {
    DicomImageElement current = view.getImage();
    List<DicomImageElement> result = new ArrayList<>();
    result.add(current);
    List<DicomImageElement> stack =
        view.getSeries()
            .copyOfMedias(
                (Filter<DicomImageElement>) view.getActionValue(ActionW.FILTERED_SERIES.cmd()),
                view.getCurrentSortComparator());
    int index = stack.indexOf(current);
    if (index >= 0) {
      for (int distance = 1; distance <= 2; distance++) {
        for (int neighbor : new int[] {index - distance, index + distance}) {
          if (neighbor >= 0 && neighbor < stack.size()) {
            DicomImageElement image = stack.get(neighbor);
            if (!result.contains(image) && compatible(current, image)) {
              result.add(image);
            }
          }
        }
      }
    }
    return List.copyOf(result);
  }

  static boolean compatible(DicomImageElement current, DicomImageElement other) {
    // A DICOM series can contain interleaved echoes, time points, or localizer orientations.
    for (int tag :
        new int[] {
          Tag.SeriesInstanceUID,
          Tag.Rows,
          Tag.Columns,
          Tag.ImageType,
          Tag.PhotometricInterpretation,
          Tag.EchoTime,
          Tag.EchoNumbers,
          Tag.RepetitionTime,
          Tag.TemporalPositionIdentifier,
          Tag.RescaleSlope,
          Tag.RescaleIntercept
        }) {
      if (!Objects.deepEquals(
          current.getTagValue(TagD.get(tag)), other.getTagValue(TagD.get(tag)))) {
        return false;
      }
    }
    double[] first = TagD.getTagValue(current, Tag.ImageOrientationPatient, double[].class);
    double[] second = TagD.getTagValue(other, Tag.ImageOrientationPatient, double[].class);
    if (first == null || second == null) {
      return first == second;
    }
    if (first.length != second.length) {
      return false;
    }
    for (int i = 0; i < first.length; i++) {
      if (!Double.isFinite(first[i])
          || !Double.isFinite(second[i])
          || Math.abs(first[i] - second[i]) > 0.001) {
        return false;
      }
    }
    return true;
  }

  /** Loads at most five images on a worker. Only immutable statistics leave the image decoder. */
  static PresetWindowLevel estimate(List<DicomImageElement> images, BooleanSupplier cancelled) {
    List<MrWindowLevelRange> ranges = new ArrayList<>();
    MrWindowLevelRange currentRange = null;
    for (DicomImageElement image : images) {
      if (cancelled.getAsBoolean()) {
        return null;
      }
      MrWindowLevelRange range = image.getMrAutoWindowLevelRange();
      if (valid(range)) {
        ranges.add(range);
        if (image == images.getFirst()) {
          currentRange = range;
        }
      }
    }
    if (cancelled.getAsBoolean()) {
      return null;
    }
    if (ranges.isEmpty()) {
      // Refresh from this image, not the global preset combo, which may be absent or stale.
      return images.getFirst().getPresetList(new DefaultWlPresentation(null, true), true).stream()
          .filter(PresetWindowLevel::isAutoLevel)
          .findFirst()
          .orElse(null);
    }
    double min = median(ranges.stream().mapToDouble(MrWindowLevelRange::min).sorted().toArray());
    double max = median(ranges.stream().mapToDouble(MrWindowLevelRange::max).sorted().toArray());
    // Neighbors stabilize a narrow/bright slice; never narrow the current slice's tissue range.
    if (currentRange != null) {
      min = Math.min(min, currentRange.min());
      max = Math.max(max, currentRange.max());
    }
    PresetWindowLevel preset =
        new PresetWindowLevel(
            "Auto Level [Nearby slices]", max - min, min + (max - min) / 2.0, LutShape.LINEAR);
    preset.setKeyCode(KeyEvent.VK_0);
    return preset;
  }

  private static boolean valid(MrWindowLevelRange range) {
    return range != null
        && Double.isFinite(range.min())
        && Double.isFinite(range.max())
        && Double.isFinite(range.max() - range.min())
        && range.max() > range.min();
  }

  private static double median(double[] values) {
    int middle = values.length / 2;
    return values.length % 2 == 0
        ? values[middle - 1] / 2.0 + values[middle] / 2.0
        : values[middle];
  }
}
