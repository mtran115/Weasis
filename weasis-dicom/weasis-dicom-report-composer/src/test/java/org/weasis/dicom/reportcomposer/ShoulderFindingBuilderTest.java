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
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.GeneratedFinding;
import org.weasis.dicom.reportcomposer.ShoulderFindingBuilder.CuffTearSelection;
import org.weasis.dicom.reportcomposer.ShoulderFindingBuilder.CuffTearType;
import org.weasis.dicom.reportcomposer.ShoulderFindingBuilder.Degree;
import org.weasis.dicom.reportcomposer.ShoulderFindingBuilder.LabralLocation;
import org.weasis.dicom.reportcomposer.ShoulderFindingBuilder.RotatorCuffTendon;
import org.weasis.dicom.reportcomposer.ShoulderFindingBuilder.Selection;

class ShoulderFindingBuilderTest {
  @Test
  void generatesGradedShoulderFindingsLabralLocationsAndFreeTextInFormOrder() {
    Selection selection =
        new Selection(
            Degree.MINIMAL,
            Degree.MILD,
            Degree.MODERATE,
            Map.of(
                RotatorCuffTendon.SUBSCAPULARIS,
                Degree.SEVERE,
                RotatorCuffTendon.SUPRASPINATUS,
                Degree.MILD),
            List.of(LabralLocation.SUPERIOR, LabralLocation.ANTERIOR),
            Degree.MODERATE,
            "Small glenohumeral joint effusion");

    List<GeneratedFinding> findings = ShoulderFindingBuilder.generate(selection);

    assertEquals(
        List.of(
            finding("Minimal subcoracoid bursitis."),
            finding("Mild subacromial/subdeltoid bursitis."),
            finding("Moderate acromioclavicular joint osteoarthrosis."),
            finding("Mild supraspinatus tendinosis."),
            finding("Severe subscapularis tendinosis."),
            finding("Anterior and superior labral tear."),
            finding("Moderate tenosynovitis of the long head of the biceps tendon."),
            new GeneratedFinding("Small glenohumeral joint effusion.", "")),
        findings);
  }

  @Test
  void emptyShoulderSelectionGeneratesNoFindings() {
    Selection selection =
        new Selection(Degree.NONE, Degree.NONE, Degree.NONE, Map.of(), List.of(), Degree.NONE, "");

    assertFalse(selection.hasFinding());
    assertEquals(List.of(), ShoulderFindingBuilder.generate(selection));
  }

  @Test
  void generatesCuffTearModifiersAndParalabralCystWithoutDuplicatingTendinosis() {
    Selection selection =
        new Selection(
            Degree.NONE,
            Degree.NONE,
            Degree.NONE,
            Map.of(RotatorCuffTendon.SUPRASPINATUS, Degree.MILD),
            Map.of(
                RotatorCuffTendon.SUPRASPINATUS,
                new CuffTearSelection(
                    List.of(CuffTearType.ARTICULAR_SURFACE, CuffTearType.INTERSTITIAL),
                    true,
                    true,
                    true,
                    "Measures 8 mm")),
            List.of(LabralLocation.ANTERIOR, LabralLocation.SUPERIOR),
            true,
            Degree.NONE,
            "");

    assertEquals(
        List.of(
            finding(
                "High-grade interstitial tear and high-grade partial-thickness articular-surface "
                    + "tear of the supraspinatus tendon at the footprint with mild background "
                    + "tendinosis. Measures 8 mm."),
            finding("Anterior and superior labral tear with an adjacent paralabral cyst.")),
        ShoulderFindingBuilder.generate(selection));
  }

  @Test
  void generatesStandaloneParalabralCyst() {
    Selection selection =
        new Selection(
            Degree.NONE,
            Degree.NONE,
            Degree.NONE,
            Map.of(),
            Map.of(),
            List.of(),
            true,
            Degree.NONE,
            "");

    assertEquals(List.of(finding("Paralabral cyst.")), ShoulderFindingBuilder.generate(selection));
  }

  private static GeneratedFinding finding(String text) {
    return new GeneratedFinding(text, text);
  }
}
