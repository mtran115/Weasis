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

class AnkleChordEngineTest {
  private final AnkleFormPanel form = new AnkleFormPanel();
  private final FormChordEngine engine = new FormChordEngine(form);

  private Outcome type(String keys) {
    Outcome outcome = null;
    for (char key : keys.toCharArray()) outcome = engine.type(key);
    return outcome;
  }

  private List<String> findings() {
    return AnkleFindingBuilder.generate(form.selection()).stream()
        .map(MriFindingCatalog.GeneratedFinding::findingText)
        .toList();
  }

  @Test
  void frequentFindingsEachTakeADigitAndADegree() {
    assertEquals("chord · type a structure, 1 to 9", engine.lineText());
    type("1d4");
    type("2d4");
    type("3d1");
    type("4d2");
    type("5d4");
    type("8d1");
    type("9ml");

    assertEquals(
        List.of(
            "Minimal posterior tibial tenosynovitis.",
            "Minimal tenosynovitis of the peroneus longus tendon.",
            "Mild tenosynovitis of the peroneus brevis tendon.",
            "Moderate tenosynovitis of the flexor hallucis longus tendon.",
            "Minimal Achilles tendinosis.",
            "Small posterior ankle joint effusion, nonspecific and potentially inflammatory.",
            "Soft tissue swelling along the medial and lateral ankle."),
        findings());
    assertEquals("Soft tissue swelling  m l   ·   medial, lateral", engine.lineText());
  }

  @Test
  void ligamentsTakeSprainDegreesAndTearLetters() {
    assertEquals(Outcome.APPLIED, type("6d1"));
    assertEquals(Outcome.REJECTED, type("d3"));
    assertTrue(engine.escape());
    assertEquals(Outcome.APPLIED, type("7p"));
    assertEquals(
        List.of(
            "Mild sprain of the anterior talofibular ligament.",
            "Partial tear of the anterior inferior tibiofibular ligament."),
        findings());

    type("6c");
    assertEquals(List.of("c"), engine.tokens());
    assertEquals("Complete tear of the anterior talofibular ligament.", findings().getFirst());
  }

  @Test
  void bonesArePickedByBAndADigitEachWithItsOwnDegree() {
    assertEquals(Outcome.PENDING, type("b"));
    assertEquals(Outcome.REJECTED, type("x"));
    assertEquals(Outcome.APPLIED, type("4"));
    assertEquals("Bony contusion, talus", engine.structureName());
    type("d1");
    type("b6d4b0d2");

    assertEquals(
        List.of(
            "Mild bony contusion of the talus.",
            "Minimal bony contusion of the navicular.",
            "Moderate bony contusion of the lateral cuneiform."),
        findings());

    assertTrue(engine.backspace());
    assertEquals(2, findings().size());
    assertEquals(Outcome.REJECTED, type("m"));
  }
}
