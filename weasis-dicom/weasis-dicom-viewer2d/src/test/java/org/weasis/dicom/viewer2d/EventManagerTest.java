/*
 * Copyright (c) 2009-2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.weasis.core.api.image.util.KernelData;

class EventManagerTest {

  @ParameterizedTest
  @ValueSource(
      doubles = {0.1, 0.10232929922807542, 0.5, 0.5011872336272722, 1.0, 1.9952623149688795, 2.0})
  void identifiesPreviousZoomSensitivityValues(double sensitivity) {
    assertTrue(EventManager.isPreviousZoomMouseSensitivity(sensitivity));
  }

  @ParameterizedTest
  @ValueSource(doubles = {0.05, 0.25, 3.0, 4.0})
  void preservesUnrelatedZoomSensitivityValues(double sensitivity) {
    assertFalse(EventManager.isPreviousZoomMouseSensitivity(sensitivity));
  }

  @ParameterizedTest
  @ValueSource(strings = {"MR", "mr", "CR", "DX", "DR", "MG", "RF", "XA", "IO", "PX"})
  void enablesKeyboardWindowLevelForMrAndXrayModalities(String modality) {
    assertTrue(EventManager.isKeyboardWindowLevelModality(modality));
  }

  @ParameterizedTest
  @ValueSource(strings = {"CT", "US", "NM", "PT", "OT"})
  void preservesNumberedPresetsForOtherModalities(String modality) {
    assertFalse(EventManager.isKeyboardWindowLevelModality(modality));
  }

  @ParameterizedTest
  @ValueSource(strings = {"CR", "cr", "DX", "DR", "MG", "RF", "XA", "IO", "PX", " DX "})
  void usesSharpenMoreAsTheDefaultXrayFilter(String modality) {
    assertSame(KernelData.SHARPEN_MORE, EventManager.getDefaultImageFilter(modality));
  }

  @ParameterizedTest
  @ValueSource(strings = {"MR", "CT", "US", "NM", "PT", "OT", "XC"})
  void leavesOtherModalitiesUnfilteredByDefault(String modality) {
    assertSame(KernelData.NONE, EventManager.getDefaultImageFilter(modality));
  }

  @Test
  void leavesMissingModalityUnfilteredByDefault() {
    assertSame(KernelData.NONE, EventManager.getDefaultImageFilter(null));
  }
}
