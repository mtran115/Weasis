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

import java.util.Optional;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.lut.PresetWindowLevel;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.TagD;
import org.weasis.opencv.op.lut.LutShape;

final class WindowLevelMemory {
  private static final TagW MEMORY_TAG = new TagW("MrWindowLevelMemory", TagW.TagType.OBJECT);

  void remember(
      MediaSeries<?> series,
      boolean defaultPreset,
      PresetWindowLevel preset,
      Number window,
      Number level,
      LutShape lutShape) {
    if (!isMrSeries(series)) {
      return;
    }

    if (window == null || level == null) {
      series.setTag(MEMORY_TAG, null);
    } else {
      series.setTag(
          MEMORY_TAG,
          new State(defaultPreset, preset, window.doubleValue(), level.doubleValue(), lutShape));
    }
  }

  Optional<State> recall(MediaSeries<?> series) {
    if (!isMrSeries(series)) {
      return Optional.empty();
    }
    return Optional.ofNullable(series.getTagValue(MEMORY_TAG))
        .filter(State.class::isInstance)
        .map(State.class::cast);
  }

  static boolean shouldRefreshPreset(
      MediaSeries<?> series, PresetWindowLevel preset, boolean invalidWindowLevel) {
    if (invalidWindowLevel) {
      return true;
    }
    if (preset == null) {
      return false;
    }
    return !isMrSeries(series);
  }

  static boolean isMrSeries(MediaSeries<?> series) {
    String modality = TagD.getTagValue(series, Tag.Modality, String.class);
    return "MR".equalsIgnoreCase(modality);
  }

  record State(
      boolean defaultPreset,
      PresetWindowLevel preset,
      double window,
      double level,
      LutShape lutShape) {}
}
