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

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.reportcomposer.ChordGrammar.Outcome;
import org.weasis.dicom.reportcomposer.ShoulderFindingBuilder.CuffTearType;
import org.weasis.dicom.reportcomposer.ShoulderFindingBuilder.Degree;
import org.weasis.dicom.reportcomposer.ShoulderFindingBuilder.RotatorCuffTendon;

class ShoulderChordEngineTest {
  private final ShoulderFormPanel form = new ShoulderFormPanel();
  private final ShoulderChordEngine engine = new ShoulderChordEngine(form);

  private Outcome type(String keys) {
    Outcome outcome = null;
    for (char key : keys.toCharArray()) outcome = engine.type(key);
    return outcome;
  }

  @Test
  void structureStaysUntilTheNextOne() {
    assertEquals("chord · type a structure, 1 to 9", engine.lineText());
    assertEquals(Outcome.REJECTED, type("a"));
    assertEquals(Outcome.APPLIED, type("2d1"));
    assertEquals("Subacromial/subdeltoid bursitis", engine.structureName());
    assertEquals(Degree.MILD, form.target("Subacromial/subdeltoid bursitis").currentDegree());

    type("4d2ah");
    var supraspinatus = form.target("Supraspinatus");
    assertEquals(Degree.MODERATE, supraspinatus.currentDegree());
    assertEquals(List.of(CuffTearType.ARTICULAR_SURFACE), supraspinatus.tearSelection().types());
    assertTrue(supraspinatus.tearSelection().highGrade());
    assertEquals(List.of("d2", "a", "h"), engine.tokens());
    assertEquals(
        "Supraspinatus  d2 a h   ·   mod tendinosis, articular surface, high-grade",
        engine.lineText());

    type("2");
    assertEquals(List.of("d1"), engine.tokens());
    assertEquals("mild", engine.reading());
  }

  @Test
  void tokensSetAndNeverToggle() {
    type("5ffd3d4");
    var infraspinatus = form.target("Infraspinatus");
    assertEquals(List.of(CuffTearType.FULL_THICKNESS), infraspinatus.tearSelection().types());
    assertEquals(Degree.MINIMAL, infraspinatus.currentDegree());
    assertEquals(List.of("f", "d4"), engine.tokens());

    type("d0");
    assertEquals(Degree.NONE, infraspinatus.currentDegree());
    assertEquals(List.of("f"), engine.tokens());
  }

  @Test
  void rejectedKeysChangeNothing() {
    type("4");
    // Footprint and details need a tear; high-grade counts as one by itself.
    assertEquals(Outcome.REJECTED, type("p"));
    assertEquals(Outcome.REJECTED, type("t"));
    assertEquals(Outcome.REJECTED, type("x"));
    assertEquals(Outcome.APPLIED, type("h"));
    assertEquals(Outcome.APPLIED, type("p"));
    assertEquals(List.of("h", "p"), engine.tokens());
    // A full-thickness tear alone has no grade.
    type("f");
    assertEquals(List.of("p", "f"), engine.tokens());
    assertEquals(Outcome.REJECTED, type("h"));

    assertEquals(Outcome.PENDING, type("d"));
    assertEquals(Outcome.REJECTED, type("7"));
    assertEquals("d", engine.pending());
    assertTrue(engine.escape());
    assertEquals("Supraspinatus", engine.structureName());

    type("8");
    assertEquals(Outcome.REJECTED, type("d"));
  }

  @Test
  void backspaceRemovesTheLastToken() {
    type("6bhd");
    assertTrue(engine.backspace());
    assertEquals("", engine.pending());
    assertEquals(List.of("b", "h"), engine.tokens());
    engine.backspace();
    assertFalse(form.target("Subscapularis").tearSelection().highGrade());
    engine.backspace();
    assertTrue(form.target("Subscapularis").tearSelection().types().isEmpty());
    assertFalse(engine.backspace());
  }

  @Test
  void labrumTakesLocationsAndCyst() {
    type("8spc");
    var labrum = form.target("Labral tear");
    assertTrue(labrum.button('S').isSelected());
    assertTrue(labrum.button('P').isSelected());
    assertTrue(labrum.button('C').isSelected());
    assertFalse(labrum.button('A').isSelected());
    assertEquals("superior, posterior, adjacent paralabral cyst", engine.reading());

    labrum.button('A').doClick(0);
    assertEquals(List.of("s", "p", "c", "a"), engine.tokens());

    engine.reset();
    assertEquals("", engine.structureName());
  }

  @Test
  void insertionalCystsAreATendonToken() {
    type("5d4c");
    assertEquals(List.of(RotatorCuffTendon.INFRASPINATUS), form.selection().insertionalCysts());
    assertEquals(
        List.of(
            "Minimal infraspinatus tendinosis.",
            "Cystic changes at the insertion of the infraspinatus tendon, related to traction."),
        ShoulderFindingBuilder.generate(form.selection()).stream()
            .map(finding -> finding.findingText())
            .toList());
    assertEquals("minimal tendinosis, insertional cystic changes", engine.reading());

    engine.backspace();
    assertTrue(form.selection().insertionalCysts().isEmpty());
    type("2");
    assertEquals(Outcome.REJECTED, type("c"));
  }
}
