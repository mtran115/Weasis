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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.reportcomposer.SequenceStepper.Kind;

class SequenceStepperTest {
  private static final List<String> SPINE =
      List.of("SAG T2", "COR", "SAG STIR", "SAG T1", "AX T2", "OBL");

  @Test
  void descriptionsFromDifferentScannersReadAsPlaneAndContrast() {
    Map<String, Kind> expected =
        Map.ofEntries(
            Map.entry("SAG T2 RESTORE", new Kind("SAG", "T2")),
            Map.entry("T2 SAG_smcs_AIRS", new Kind("SAG", "T2")),
            Map.entry("UF - Sag T2", new Kind("SAG", "T2")),
            Map.entry("T2 FSE SAG", new Kind("SAG", "T2")),
            Map.entry("SAG T2 FS RES", new Kind("SAG", "STIR")),
            Map.entry("SAG T2 TIRM", new Kind("SAG", "STIR")),
            Map.entry("STIR SAG CLEAR_smls_AIRS", new Kind("SAG", "STIR")),
            Map.entry("FIR COR", new Kind("COR", "STIR")),
            Map.entry("DLR - Cor STIR", new Kind("COR", "STIR")),
            Map.entry("COR T2", new Kind("COR", "T2")),
            Map.entry("T1 SE SAG", new Kind("SAG", "T1")),
            Map.entry("Z T2 FSE TRS", new Kind("AX", "T2")),
            Map.entry("TSE T2 AX FLOW COMP", new Kind("AX", "T2")),
            Map.entry("UF - Axial T2", new Kind("AX", "T2")),
            Map.entry("AX T1 TSE (DISC CUTS)", new Kind("AX", "T1")),
            Map.entry("LT OBL SAG T2_smcs_AIRS", new Kind("OBL", "T2")),
            Map.entry("OBL SAG RIGHT_2DSMCS_AIRS", new Kind("OBL", "")),
            Map.entry("COR PD FS_smwr_AIRS", new Kind("COR", "STIR")),
            Map.entry("SAG PD", new Kind("SAG", "PD")),
            Map.entry("SURVEY", new Kind("", "")));
    expected.forEach((description, kind) -> assertEquals(kind, Kind.of(description), description));
  }

  @Test
  void aSpineStudyFollowsTheReadersOrderThenTheRestBySeriesNumberWithLocalizersLast() {
    List<String> study =
        List.of(
            "SURVEY",
            "SAG T1",
            "SAG T2",
            "SAG STIR",
            "AX T2",
            "AX T1",
            "LT SAG T2 OBL",
            "RT SAG T2 OBL",
            "COR T2");

    assertEquals(
        List.of(
            "SAG T2",
            "COR T2",
            "SAG STIR",
            "SAG T1",
            "AX T2",
            "LT SAG T2 OBL",
            "RT SAG T2 OBL",
            "AX T1",
            "SURVEY"),
        SequenceStepper.ordered(study, Function.identity(), SPINE));
    assertEquals(
        List.of(
            "SAG T1",
            "SAG T2",
            "SAG STIR",
            "AX T2",
            "AX T1",
            "LT SAG T2 OBL",
            "RT SAG T2 OBL",
            "COR T2",
            "SURVEY"),
        SequenceStepper.ordered(study, Function.identity(), List.of()));
  }

  @Test
  void localizersGoLastSoTheyNeverFillAViewport() {
    List<String> study = List.of("3 PLANE LOC", "ASSET CAL", "COR T1", "SAG STIR", "T/S/C SCANO");

    assertEquals(
        List.of("COR T1", "SAG STIR", "3 PLANE LOC", "ASSET CAL", "T/S/C SCANO"),
        SequenceStepper.ordered(study, Function.identity(), List.of()));
  }

  @Test
  void stepsSkipWhatOtherViewportsShowAndStopAtEitherEnd() {
    List<String> ordered = List.of("SAG T2", "COR T2", "SAG STIR", "SAG T1", "AX T2");
    Set<String> inViewport1 = Set.of("SAG T2");

    assertEquals(
        Optional.of("SAG STIR"), SequenceStepper.step(ordered, "COR T2", inViewport1, false));
    assertEquals(
        Optional.of("COR T2"), SequenceStepper.step(ordered, "SAG STIR", inViewport1, true));
    assertEquals(Optional.empty(), SequenceStepper.step(ordered, "COR T2", inViewport1, true));
    assertEquals(Optional.empty(), SequenceStepper.step(ordered, "AX T2", inViewport1, false));
    // A viewport still showing the previous study starts this one from the top.
    assertEquals(
        Optional.of("COR T2"), SequenceStepper.step(ordered, "OLD SAG T2", inViewport1, false));
    assertEquals(Optional.of("AX T2"), SequenceStepper.step(ordered, null, inViewport1, true));
  }

  @Test
  void stepsMustBeAPlaneAContrastOrOneOfEach() {
    SPINE.forEach(SequenceStepper::checkStep);
    SequenceStepper.checkStep("stir");
    for (String bad : List.of("", "SAGT2", "SAG COR", "T1 T2", "SAG T2 FS", "DWI")) {
      assertThrows(IllegalArgumentException.class, () -> SequenceStepper.checkStep(bad), bad);
    }
  }
}
