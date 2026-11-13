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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.reportcomposer.ChordGrammar.Outcome;

class HipChordEngineTest {
  private final HipFormPanel form = new HipFormPanel();
  private final FormChordEngine engine = new FormChordEngine(form);

  private Outcome type(String keys) {
    Outcome outcome = null;
    for (char key : keys.toCharArray()) outcome = engine.type(key);
    return outcome;
  }

  private List<String> findings() {
    return HipFindingBuilder.generate(form.selection()).stream()
        .map(MriFindingCatalog.GeneratedFinding::findingText)
        .toList();
  }

  @Test
  void aSideIsNeededFirstAndThenStays() {
    assertEquals("chord · type a side, r or l", engine.lineText());
    assertEquals(Outcome.REJECTED, type("1"));
    assertEquals(Outcome.APPLIED, type("r"));
    assertEquals("chord · type a structure, 1 to 7", engine.lineText());
    type("1d4");
    type("3p");
    type("l1d1");

    assertEquals(
        List.of(
            "Minimal right greater trochanteric bursitis.",
            "Partial tear of the right gluteus medius tendon insertion on the greater trochanter.",
            "Mild left greater trochanteric bursitis."),
        findings());
    assertEquals("Left greater trochanteric bursitis  d1   ·   mild", engine.lineText());
  }

  @Test
  void changingSideKeepsTheStructure() {
    type("r7d1l");
    assertEquals("Left hip joint effusion", engine.structureName());
    type("d2");
    assertEquals(
        List.of(
            "Small right hip joint effusion, nonspecific and potentially inflammatory.",
            "Moderate left hip joint effusion, nonspecific and potentially inflammatory."),
        findings());
  }

  @Test
  void labrumTakesRegionsACystAndExtras() {
    assertEquals(Outcome.REJECTED, type("l2x"));
    type("as");
    assertEquals(List.of("Tear of the left anterior superior labrum."), findings());

    type("c");
    assertEquals(Outcome.APPLIED, type("x"));
    assertEquals(
        List.of("Tear of the left anterior superior labrum, with an associated paralabral cyst."),
        findings());

    assertTrue(engine.backspace());
    assertTrue(engine.backspace());
    assertEquals(List.of("Tear of the left anterior labrum."), findings());
  }

  @Test
  void highGradeStandsForAPartialTearOnTheGlutealTendonsOnly() {
    type("r4hd4");
    assertEquals(
        List.of(
            "Minimal right gluteus minimus insertional tendinosis.",
            "High-grade partial tear of the right gluteus minimus tendon insertion on the greater"
                + " trochanter."),
        findings());

    assertEquals(Outcome.REJECTED, type("5h"));
    assertEquals(Outcome.APPLIED, type("p"));
    assertEquals(Outcome.REJECTED, type("8"));
    assertTrue(findings().contains("Partial tear of the right common hamstring tendon."));
  }
}
