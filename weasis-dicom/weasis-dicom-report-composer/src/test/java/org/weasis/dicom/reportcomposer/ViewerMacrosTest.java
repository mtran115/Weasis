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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.ExamTemplate;
import org.weasis.dicom.reportcomposer.ViewerMacros.Rule;
import org.weasis.dicom.reportcomposer.ViewerMacros.SequenceOrder;

class ViewerMacrosTest {
  private static final Optional<ExamTemplate> CERVICAL = Optional.of(ExamTemplate.CERVICAL_SPINE);
  private static final Optional<ExamTemplate> LUMBAR = Optional.of(ExamTemplate.LUMBAR_SPINE);

  @TempDir Path directory;

  private Path file() {
    return directory.resolve("report-composer/macros.json");
  }

  private ViewerMacros startingMacros() {
    ViewerMacros macros = new ViewerMacros(file());
    macros.writeDefaultsIfMissing();
    return macros;
  }

  // Each edit gets a later time so that a reload never depends on the file system's clock.
  private void edit(String json, int second) throws IOException {
    Files.writeString(file(), json);
    Files.setLastModifiedTime(
        file(), FileTime.from(Instant.ofEpochSecond(1_800_000_000L + second)));
  }

  @Test
  void startingFileAppliesAutoWindowLevelWithoutZoomToEveryMrSeries() {
    ViewerMacros macros = startingMacros();

    for (Optional<ExamTemplate> exam :
        List.of(
            CERVICAL, LUMBAR, Optional.of(ExamTemplate.SHOULDER), Optional.<ExamTemplate>empty())) {
      Rule other = macros.ruleFor(exam, "MR", "COR PD FS").orElseThrow();
      assertEquals("Every MR series", other.name());
      assertNull(other.zoom());
      assertTrue(other.autoWindowLevel());
    }
    assertTrue(macros.ruleFor(LUMBAR, "CT", "SAG BONE").isEmpty());
  }

  @Test
  void spineAndJointStudiesHaveASequenceOrderAndOtherStudiesGoBySeriesNumber() throws IOException {
    ViewerMacros macros = startingMacros();
    List<String> spine = List.of("SAG T2", "COR", "SAG STIR", "SAG T1", "AX T2", "OBL");

    assertEquals(spine, macros.sequenceOrderFor(CERVICAL));
    assertEquals(spine, macros.sequenceOrderFor(Optional.of(ExamTemplate.THORACIC_SPINE)));
    assertEquals(spine, macros.sequenceOrderFor(LUMBAR));
    assertEquals(
        List.of("COR STIR", "COR", "SAG STIR", "SAG", "AX STIR", "AX"),
        macros.sequenceOrderFor(Optional.of(ExamTemplate.SHOULDER)));
    assertEquals(List.of(), macros.sequenceOrderFor(Optional.of(ExamTemplate.BRAIN)));
    assertEquals(List.of(), macros.sequenceOrderFor(Optional.empty()));

    edit(ViewerMacros.DEFAULTS.replace("\"SAG STIR\"", "\"SAG FLAIR\""), 1);
    assertEquals(
        spine, macros.sequenceOrderFor(LUMBAR), "A misspelled step keeps the order read before.");
  }

  @Test
  void jointsHaveABestGuessAndEveryProtocolHasALayout() {
    ViewerMacros macros = startingMacros();

    SequenceOrder knee = macros.hangingFor(Optional.of(ExamTemplate.KNEE));
    assertEquals("Joint MR", knee.name());
    assertEquals("1x2", knee.layout());
    assertEquals(List.of("COR STIR", "COR", "SAG STIR", "SAG", "AX STIR", "AX"), knee.order());
    assertEquals(
        SequenceOrder.BY_SERIES_NUMBER, macros.hangingFor(Optional.of(ExamTemplate.BRAIN)));
    assertEquals("1x2", new SequenceOrder("x", null, " ", null).layout());
  }

  @Test
  void savingProtocolsKeepsTheSeriesOpenRules() throws IOException {
    ViewerMacros macros = startingMacros();
    SequenceOrder brain =
        new SequenceOrder(
            "Brain MR", List.of("BRAIN"), "2x2", List.of("AX T2", "AX STIR", "SAG T1"));

    macros.saveSequenceOrders(List.of(brain));

    ViewerMacros reread = new ViewerMacros(file());
    assertEquals(List.of(brain), reread.sequenceOrders());
    Rule kept = reread.ruleFor(CERVICAL, "MR", "SAG T2").orElseThrow();
    assertEquals("Every MR series", kept.name());
    assertTrue(kept.autoWindowLevel());
    assertEquals("2x2", reread.hangingFor(Optional.of(ExamTemplate.BRAIN)).layout());
    assertFalse(Files.exists(file().resolveSibling("macros.json.saving")));
  }

  @Test
  void anExistingFileIsKept() throws IOException {
    Files.createDirectories(file().getParent());
    Files.writeString(file(), "{\"series_open\": []}");

    ViewerMacros macros = startingMacros();

    assertEquals("{\"series_open\": []}", Files.readString(file()));
    assertTrue(macros.ruleFor(CERVICAL, "MR", "SAG T2").isEmpty());
  }

  @Test
  void editsApplyToTheNextSeriesAndABrokenEditKeepsTheRulesReadBefore() throws IOException {
    ViewerMacros macros = startingMacros();
    assertNull(macros.ruleFor(LUMBAR, "MR", "SAG T2").orElseThrow().zoom());
    String lumbarZoom =
        "{\"series_open\": [{\"name\": \"Lumbar\", \"exam\": \"LUMBAR_SPINE\", \"zoom\": %s}]}";

    edit(lumbarZoom.formatted("1.25"), 1);
    assertEquals(1.25, macros.ruleFor(LUMBAR, "MR", "SAG T2").orElseThrow().zoom());

    edit(lumbarZoom.formatted("1.5"), 2);
    assertEquals(1.5, macros.ruleFor(LUMBAR, "MR", "SAG T2").orElseThrow().zoom());

    edit("{\"series_open\": [", 3);
    assertEquals(1.5, macros.ruleFor(LUMBAR, "MR", "SAG T2").orElseThrow().zoom());

    edit("{\"series_open\": [{\"name\": \"bad\", \"series\": \"(\"}]}", 4);
    assertEquals(1.5, macros.ruleFor(LUMBAR, "MR", "SAG T2").orElseThrow().zoom());

    edit("{\"series_open\": [{\"name\": \"bad\", \"zoom\": 0}]}", 5);
    assertEquals(1.5, macros.ruleFor(LUMBAR, "MR", "SAG T2").orElseThrow().zoom());
  }

  @Test
  void theFirstMatchingRuleWinsAndSeriesPatternsIgnoreCase() throws IOException {
    ViewerMacros macros = startingMacros();
    edit(
        """
        {
          "series_open": [
            {"name": "Lumbar axials", "exam": "lumbar_spine", "series": "^ax", "zoom": 1.1,
             "note": "unknown keys are allowed for the reader's own notes"},
            {"name": "Lumbar", "exam": "LUMBAR_SPINE", "zoom": 1.25, "auto_window_level": true}
          ]
        }
        """,
        1);

    Rule axial = macros.ruleFor(LUMBAR, "MR", "Ax T2 FSE").orElseThrow();
    assertEquals("Lumbar axials", axial.name());
    assertFalse(axial.autoWindowLevel());
    assertEquals("Lumbar", macros.ruleFor(LUMBAR, "MR", "SAG T2 AX-REF").orElseThrow().name());
  }
}
