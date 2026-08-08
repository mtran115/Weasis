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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.reportcomposer.WristFindingCatalog.FindingChoice;
import org.weasis.dicom.reportcomposer.WristFindingCatalog.GeneratedFinding;

class WristFindingCatalogTest {
  @Test
  void keepsClinicalCategoriesInDisplayOrder() {
    assertEquals(
        List.of(
            "Intrinsic ligaments",
            "TFCC",
            "Tendons",
            "Bones",
            "Joints and cartilage",
            "Nerves",
            "Other"),
        WristFindingCatalog.categories());
  }

  @Test
  void generatesLunotriquetralTearFindingAndImpression() {
    FindingChoice tear =
        WristFindingCatalog.findings("Intrinsic ligaments").stream()
            .filter(choice -> "Tear".equals(choice.label()))
            .findFirst()
            .orElseThrow();

    GeneratedFinding generated =
        WristFindingCatalog.generate("Intrinsic ligaments", "lunotriquetral ligament", tear);

    assertEquals("Tear of the lunotriquetral ligament.", generated.findingText());
    assertEquals("lunotriquetral ligament tear.", generated.impressionText());
  }
}
