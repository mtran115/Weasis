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
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.Laterality;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.LevelSelection;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.OverviewFinding;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.ProtrusionLocation;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.Severity;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.SpineRegion;

class ChordEngineTest {
  private final SpineFormPanel form = new SpineFormPanel(SpineRegion.LUMBAR);
  private final ChordEngine engine = new ChordEngine(form);

  private Outcome type(String keys) {
    Outcome outcome = null;
    for (char key : keys.toCharArray()) outcome = engine.type(key);
    return outcome;
  }

  private LevelSelection level(String name) {
    return form.level(name).selection();
  }

  @Test
  void levelCodesUseLastDigitsOrTheUpperVertebraWhereThoseRepeat() {
    var lumbar = ChordEngine.levelCodes(SpineRegion.LUMBAR.levels());
    assertEquals("L4-5", lumbar.get("45"));
    assertEquals("L5-S1", lumbar.get("51"));
    assertEquals("T12-L1", lumbar.get("21"));
    assertEquals("C7-T1", ChordEngine.levelCodes(SpineRegion.CERVICAL.levels()).get("71"));
    var thoracic = ChordEngine.levelCodes(SpineRegion.THORACIC.levels());
    assertEquals("T1-2", thoracic.get("01"));
    assertEquals("T12-L1", thoracic.get("12"));
    assertEquals(SpineRegion.THORACIC.levels().size(), thoracic.size());
  }

  @Test
  void levelStaysUntilTheNextOne() {
    assertEquals(Outcome.APPLIED, type("45bfc2"));
    assertEquals("L4-5", engine.levelName());
    assertEquals(List.of("b", "f", "c2"), engine.tokens());
    assertEquals("bulge, facets, mod canal", engine.reading());
    type("51l3");

    LevelSelection l45 = level("L4-5");
    assertTrue(l45.bulge());
    assertEquals(Laterality.BILATERAL, l45.facetArthrosis());
    assertEquals(Severity.MODERATE, l45.spinalCanalSeverity());
    assertEquals(Severity.NONE, l45.leftForaminalSeverity());
    assertEquals(Severity.SEVERE, level("L5-S1").leftForaminalSeverity());

    type("45");
    assertEquals(List.of("b", "f", "c2"), engine.tokens());
  }

  @Test
  void tokensSetAndNeverToggle() {
    type("45bb");
    assertTrue(level("L4-5").bulge());
    assertEquals(List.of("b"), engine.tokens());

    type("c2c3");
    assertEquals(Severity.SEVERE, level("L4-5").spinalCanalSeverity());
    assertEquals(List.of("b", "c3"), engine.tokens());
    type("c0");
    assertEquals(Severity.NONE, level("L4-5").spinalCanalSeverity());
    assertEquals(List.of("b"), engine.tokens());

    type("pe");
    assertTrue(level("L4-5").extrusion());
    assertFalse(level("L4-5").protrusion());
    assertEquals(List.of("b", "e"), engine.tokens());
  }

  @Test
  void rejectedKeysChangeNothing() {
    assertEquals(Outcome.REJECTED, type("b"));
    assertEquals(Outcome.REJECTED, type("9"));
    assertEquals(Outcome.PENDING, type("4"));
    assertEquals(Outcome.REJECTED, type("9"));
    assertEquals("4", engine.pending());
    type("5");
    assertEquals(Outcome.REJECTED, type("d"));
    assertEquals(Outcome.PENDING, type("c"));
    assertEquals(Outcome.REJECTED, type("4"));
    assertEquals("c", engine.pending());
    assertEquals(Outcome.REJECTED, type("x"));
    assertTrue(engine.escape());
    assertEquals("", engine.pending());
    assertFalse(engine.escape());
    assertFalse(level("L4-5").hasFinding());
  }

  @Test
  void backspaceRemovesTheUnfinishedKeyThenTheLastToken() {
    type("45blfc2r");
    assertEquals(Laterality.LEFT, level("L4-5").facetArthrosis());
    assertTrue(engine.backspace());
    assertEquals("", engine.pending());
    assertEquals(List.of("b", "lf", "c2"), engine.tokens());

    engine.backspace();
    assertEquals(Severity.NONE, level("L4-5").spinalCanalSeverity());
    engine.backspace();
    assertEquals(Laterality.NONE, level("L4-5").facetArthrosis());
    engine.backspace();
    assertFalse(level("L4-5").bulge());
    assertFalse(engine.backspace());
  }

  @Test
  void discLocationsReplaceCentralThenAdd() {
    type("45p");
    assertEquals(List.of(ProtrusionLocation.CENTRAL), level("L4-5").protrusionLocations());
    assertEquals(List.of("p"), engine.tokens());

    type("d5d7");
    assertEquals(
        List.of(ProtrusionLocation.LEFT_SUBARTICULAR, ProtrusionLocation.LEFT_FORAMINAL),
        level("L4-5").protrusionLocations());
    assertEquals("protrusion, left subarticular, left foraminal", engine.reading());

    engine.backspace();
    engine.backspace();
    assertEquals(List.of(ProtrusionLocation.CENTRAL), level("L4-5").protrusionLocations());
  }

  @Test
  void lineFollowsTheFormAndShowsUnfinishedTokens() {
    assertEquals("chord · type a level", engine.lineText());
    type("45b");
    form.level("L4-5").setSeverity('R', 1);
    assertEquals(List.of("b", "r1"), engine.tokens());
    form.level("L4-5").set('B', false);
    type("c");
    assertEquals("L4-5  r1 c_   ·   mild R foramen", engine.lineText());

    engine.reset();
    assertEquals("", engine.levelName());
  }

  @Test
  void oneTokenSetsBothForamina() {
    type("45n2");
    assertEquals(Severity.MODERATE, level("L4-5").leftForaminalSeverity());
    assertEquals(Severity.MODERATE, level("L4-5").rightForaminalSeverity());
    assertEquals(List.of("n2"), engine.tokens());
    assertEquals("mod foramina", engine.reading());

    type("l3");
    assertEquals(List.of("l3", "r2"), engine.tokens());
    type("r3");
    assertEquals(List.of("n3"), engine.tokens());
    engine.backspace();
    assertEquals(Severity.NONE, level("L4-5").leftForaminalSeverity());
    assertEquals(Severity.NONE, level("L4-5").rightForaminalSeverity());
  }

  @Test
  void straighteningNeedsNoLevel() {
    assertEquals(Outcome.APPLIED, type("ss"));
    assertTrue(form.straightening());
    assertEquals("no level  ss   ·   straightening", engine.lineText());
    type("45b");
    assertEquals(List.of("ss", "b"), engine.tokens());
    engine.backspace();
    engine.backspace();
    assertFalse(form.straightening());

    var thoracic = new ChordEngine(new SpineFormPanel(SpineRegion.THORACIC));
    thoracic.type('s');
    assertEquals(Outcome.REJECTED, thoracic.type('s'));
  }

  @Test
  void spondylosisIsItsOwnLineWithOptionalLevels() {
    type("45b");
    assertEquals(Outcome.APPLIED, type("sp"));
    assertTrue(engine.wantsEnter());
    assertEquals(List.of(), form.selection().overviewLevels().get(OverviewFinding.SPONDYLOSIS));
    assertEquals("Spondylosis  no levels   ·   Enter to finish", engine.lineText());

    type("45515");
    assertEquals(Outcome.REJECTED, type("b"));
    assertEquals(List.of("L4-5", "L5-S1"), engine.spondylosisLevels());
    assertEquals("Spondylosis  L4-5 L5-S1 5_   ·   Enter to finish", engine.lineText());
    engine.backspace();
    engine.backspace();
    assertEquals(List.of("L4-5"), engine.spondylosisLevels());

    assertTrue(engine.enter());
    assertFalse(engine.enter());
    assertEquals("L4-5", engine.levelName());
    type("51c1");
    assertEquals(List.of("L4-5"), engine.spondylosisLevels());

    type("sp");
    engine.backspace();
    assertTrue(engine.wantsEnter());
    engine.backspace();
    assertFalse(engine.wantsEnter());
    assertFalse(form.selection().overviewLevels().containsKey(OverviewFinding.SPONDYLOSIS));
  }

  @Test
  void cervicalFormUsesItsOwnLevelsAndAlignment() {
    SpineFormPanel cervical = new SpineFormPanel(SpineRegion.CERVICAL);
    ChordEngine chords = new ChordEngine(cervical);
    for (char key : "sr56bhn271c1".toCharArray()) chords.type(key);

    assertTrue(cervical.reversal());
    LevelSelection c56 = cervical.level("C5-6").selection();
    assertTrue(c56.bulge());
    assertEquals(Laterality.BILATERAL, c56.posteriorElementHypertrophy());
    assertEquals(Severity.MODERATE, c56.leftForaminalSeverity());
    assertEquals(Severity.MODERATE, c56.rightForaminalSeverity());
    assertEquals(Severity.MILD, cervical.level("C7-T1").selection().spinalCanalSeverity());
    assertEquals("C7-T1", chords.levelName());

    for (char key : "56".toCharArray()) chords.type(key);
    assertEquals(List.of("sr", "b", "h", "n2"), chords.tokens());
    assertEquals("reversal, bulge, uncovertebral, mod foramina", chords.reading());

    chords.type('s');
    chords.type('s');
    assertTrue(cervical.straightening());
    assertFalse(cervical.reversal());
    assertEquals(List.of("ss", "b", "h", "n2"), chords.tokens());

    type("s");
    assertEquals(Outcome.REJECTED, type("r"));
  }

  @Test
  void referenceListsOnlyTheKeysOfItsRegion() {
    String lumbar = ChordEngine.reference(SpineRegion.LUMBAR);
    assertTrue(lumbar.contains("45 L4-5"));
    assertTrue(lumbar.contains("21 T12-L1"));
    assertTrue(lumbar.contains("ligamentum flavum hypertrophy"));
    assertTrue(lumbar.contains("ss "));
    assertFalse(lumbar.contains("sr "));

    String cervical = ChordEngine.reference(SpineRegion.CERVICAL);
    assertTrue(cervical.contains("71 C7-T1"));
    assertTrue(cervical.contains("uncovertebral hypertrophy"));
    assertTrue(cervical.contains("sr "));

    String thoracic = ChordEngine.reference(SpineRegion.THORACIC);
    assertTrue(thoracic.contains("01 T1-2"));
    assertFalse(thoracic.contains("ss "));
  }
}
