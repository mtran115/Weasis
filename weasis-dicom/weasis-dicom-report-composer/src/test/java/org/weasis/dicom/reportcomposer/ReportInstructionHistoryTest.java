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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.ExamTemplate;

class ReportInstructionHistoryTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @TempDir Path temporaryDirectory;

  @Test
  void appendsOnlyTimestampExamAndRawInstructions() throws Exception {
    Path historyPath = temporaryDirectory.resolve("history.jsonl");
    ReportInstructionHistory history = new ReportInstructionHistory(historyPath);

    history.record(ExamTemplate.SHOULDER, "intersti tear sup footp");
    history.record(ExamTemplate.LUMBAR_SPINE, "ann fiss");

    List<String> lines = Files.readAllLines(historyPath);
    assertEquals(2, lines.size());
    JsonNode first = MAPPER.readTree(lines.getFirst());
    Set<String> fields = new HashSet<>();
    first.fieldNames().forEachRemaining(fields::add);
    assertEquals(Set.of("recordedAt", "exam", "instructions"), fields);
    assertEquals("SHOULDER", first.get("exam").asText());
    assertEquals("intersti tear sup footp", first.get("instructions").asText());
    assertTrue(first.get("recordedAt").isTextual());
  }

  @Test
  void skipsBlankInstructionsAndMissingExam() {
    Path historyPath = temporaryDirectory.resolve("history.jsonl");
    ReportInstructionHistory history = new ReportInstructionHistory(historyPath);

    history.record(ExamTemplate.BRAIN, "   ");
    history.record(null, "some text");

    assertFalse(Files.exists(historyPath));
  }
}
