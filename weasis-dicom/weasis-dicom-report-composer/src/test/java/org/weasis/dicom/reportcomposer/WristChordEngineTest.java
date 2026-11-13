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

class WristChordEngineTest {
  private final WristFormPanel form = new WristFormPanel();
  private final FormChordEngine engine = new FormChordEngine(form);

  private Outcome type(String keys) {
    Outcome outcome = null;
    for (char key : keys.toCharArray()) outcome = engine.type(key);
    return outcome;
  }

  private List<String> findings() {
    return WristFindingBuilder.generate(form.selection()).stream()
        .map(MriFindingCatalog.GeneratedFinding::findingText)
        .toList();
  }

  @Test
  void digitsPickTheLigamentsCystSwellingAndLimitedExam() {
    assertEquals("chord · type a structure, 1 to 5", engine.lineText());
    type("1t");
    type("2r");
    type("3v");
    type("4d");
    type("5m");

    assertEquals(
        List.of(
            "Limited exam due to motion.",
            "Tear of the scapholunate ligament.",
            "Tear of the radial attachment of the TFCC.",
            "A ganglion or synovial cyst along the volar wrist.",
            "Soft tissue swelling along the dorsal wrist."),
        findings());
    assertEquals("Limited exam  m   ·   motion", engine.lineText());

    type("1p");
    assertEquals(List.of("p"), engine.tokens());
    assertEquals("Partial tear of the scapholunate ligament.", findings().get(1));
  }

  @Test
  void dIsALetterWhereAStructureHasNoDegrees() {
    assertEquals(Outcome.APPLIED, type("3d"));
    assertEquals(List.of("d"), engine.tokens());
    assertEquals(Outcome.APPLIED, type("x"));
    assertTrue(engine.backspace());
    assertEquals(List.of(), findings());
  }

  @Test
  void bonesAndTendonsArePickedByALetterAndADigit() {
    assertEquals(Outcome.PENDING, type("b"));
    assertEquals(Outcome.APPLIED, type("4"));
    assertEquals("Lunate", engine.structureName());
    assertEquals(Outcome.REJECTED, type("x"));
    type("d1");
    type("b3f");
    type("b7oc");
    type("e4d4");
    type("e7d1s");
    assertEquals(Outcome.REJECTED, type("e8"));
    assertTrue(engine.escape());
    assertEquals(Outcome.REJECTED, type("e4s"));

    assertEquals(
        List.of(
            "Minimal tenosynovitis of the extensor carpi radialis brevis tendon.",
            "Mild tenosynovitis of the extensor carpi ulnaris tendon.",
            "Split tear of the extensor carpi ulnaris tendon.",
            "Nondisplaced fracture of the scaphoid with surrounding bone marrow edema.",
            "Mild bony contusion of the lunate.",
            "Bone marrow edema and cystic change involving the trapezoid may be related to bony"
                + " contusion or osteoarthrosis."),
        findings());
  }
}
