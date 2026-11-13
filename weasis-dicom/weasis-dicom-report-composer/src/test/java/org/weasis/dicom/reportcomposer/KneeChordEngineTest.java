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

class KneeChordEngineTest {
  private final KneeFormPanel form = new KneeFormPanel();
  private final FormChordEngine engine = new FormChordEngine(form);

  private Outcome type(String keys) {
    Outcome outcome = null;
    for (char key : keys.toCharArray()) outcome = engine.type(key);
    return outcome;
  }

  private List<String> findings() {
    return KneeFindingBuilder.generate(form.selection()).stream()
        .map(MriFindingCatalog.GeneratedFinding::findingText)
        .toList();
  }

  @Test
  void meniscusTearTakesPortionsAndTheRootExtension() {
    assertEquals("chord · type a structure, 1 to 8", engine.lineText());
    assertEquals(Outcome.REJECTED, type("t"));
    assertEquals(Outcome.APPLIED, type("1tpbr"));

    assertEquals(
        List.of(
            "Tear of the body and posterior horn of the medial meniscus, extending to the "
                + "posterior root."),
        findings());
    assertEquals(List.of("t", "p", "b", "r"), engine.tokens());
    assertEquals(
        "Medial meniscus  t p b r   ·   tear, posterior horn, body, extending to the posterior"
            + " root",
        engine.lineText());
  }

  @Test
  void rootExtensionNeedsATearAndLeavesWithIt() {
    assertEquals(Outcome.REJECTED, type("2r"));
    assertEquals(Outcome.REJECTED, type("x"));
    assertEquals(Outcome.APPLIED, type("ip"));
    assertEquals(Outcome.REJECTED, type("r"));
    assertEquals(Outcome.APPLIED, type("tr"));
    assertEquals(Outcome.APPLIED, type("i"));
    assertEquals(List.of("p", "i"), engine.tokens());
  }

  @Test
  void degenerationReplacesTheTear() {
    type("2tar");
    assertEquals(Outcome.REJECTED, type("ir"));
    assertEquals(List.of("a", "i"), engine.tokens());
    assertEquals(
        List.of("Intrasubstance degeneration of the anterior horn of the lateral meniscus."),
        findings());
  }

  @Test
  void degreesSetAndClearOnTendonsFluidAndEdema() {
    type("3d4");
    type("4d1d2");
    type("5d1");
    type("7d4");
    type("6d1pt");
    assertEquals(
        List.of(
            "Minimal tendinosis of the quadriceps tendon.",
            "Moderate tendinosis of the patellar tendon.",
            "Small knee joint effusion, nonspecific and potentially inflammatory.",
            "Trace popliteal cyst.",
            "Mild prepatellar and pretibial soft tissue edema."),
        findings());
    assertEquals(List.of("d1", "p", "t"), engine.tokens());

    assertEquals(Outcome.REJECTED, type("a"));
    assertEquals(Outcome.PENDING, type("d"));
    assertEquals(Outcome.REJECTED, type("9"));
    assertEquals(Outcome.APPLIED, type("0"));
    assertEquals(List.of("p", "t"), engine.tokens());
    assertTrue(findings().contains("Prepatellar and pretibial soft tissue edema."));
    assertEquals(Outcome.REJECTED, type("9"));
  }

  @Test
  void condyleFrictionIsSetByItsDigitAlone() {
    assertEquals(Outcome.APPLIED, type("88"));
    assertEquals(List.of("Lateral femoral condyle friction."), findings());
    assertEquals("Lateral femoral condyle friction  +   ·   set", engine.lineText());
    assertEquals(Outcome.REJECTED, type("p"));

    assertTrue(engine.backspace());
    assertEquals(List.of(), findings());
  }

  @Test
  void backspaceRemovesTheLastTokenOfTheCurrentStructure() {
    type("1tpr3d2");
    assertTrue(engine.backspace());
    assertEquals(List.of(), engine.tokens());
    assertEquals("Quadriceps tendon", engine.structureName());

    type("1");
    assertTrue(engine.backspace());
    assertEquals(List.of("t", "p"), engine.tokens());
    assertTrue(engine.backspace());
    assertTrue(engine.backspace());
    assertEquals(List.of(), findings());
    assertEquals(false, engine.backspace());
  }
}
