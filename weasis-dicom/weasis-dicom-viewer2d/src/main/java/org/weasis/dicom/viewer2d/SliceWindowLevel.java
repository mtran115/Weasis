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

import java.util.Locale;
import java.util.Optional;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.lut.PresetWindowLevel;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.opencv.op.lut.LutShape;

/**
 * On the Taylor Philips Intera, where a single slice can need a different window/level than its
 * neighbors, Auto W/L is kept per slice instead of for the whole series.
 */
final class SliceWindowLevel {
  private static final TagW SLICE_TAG = new TagW("SliceWindowLevel", TagW.TagType.OBJECT);
  private static final String STATION = "PHILIPS-2E18F45";

  record State(
      double window,
      double level,
      LutShape lutShape,
      PresetWindowLevel preset,
      boolean defaultPreset) {
    static State of(PresetWindowLevel preset) {
      return new State(preset.getWindow(), preset.getLevel(), preset.getLutShape(), preset, false);
    }
  }

  private SliceWindowLevel() {}

  /** Whether the series comes from the Taylor Philips Intera (station PHILIPS-2E18F45). */
  static boolean applies(MediaSeries<?> series) {
    return series != null
        && WindowLevelMemory.isMrSeries(series)
        && text(series, Tag.Manufacturer).contains("philips")
        && text(series, Tag.ManufacturerModelName).equals("intera")
        && text(series, Tag.StationName).equals(STATION.toLowerCase(Locale.ROOT));
  }

  static Optional<State> of(DicomImageElement image) {
    return image == null
        ? Optional.empty()
        : Optional.ofNullable(image.getTagValue(SLICE_TAG))
            .filter(State.class::isInstance)
            .map(State.class::cast);
  }

  static void set(DicomImageElement image, State state) {
    image.setTag(SLICE_TAG, state);
  }

  static void clear(DicomImageElement image) {
    image.setTag(SLICE_TAG, null);
  }

  /** The Auto W/L estimate, named so the preset menu shows that it belongs to this slice. */
  static PresetWindowLevel slicePreset(PresetWindowLevel estimate) {
    PresetWindowLevel preset =
        new PresetWindowLevel(
            "Auto Level [This slice]",
            estimate.getWindow(),
            estimate.getLevel(),
            estimate.getLutShape());
    preset.setKeyCode(estimate.getKeyCode());
    return preset;
  }

  private static String text(MediaSeries<?> series, int tag) {
    String value = TagD.getTagValue(series, tag, String.class);
    return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
  }
}
