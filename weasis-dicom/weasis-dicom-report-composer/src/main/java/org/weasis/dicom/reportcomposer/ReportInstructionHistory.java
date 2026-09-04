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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.ExamTemplate;

/** Appends raw instruction text without copying DICOM metadata for future local phrase analysis. */
final class ReportInstructionHistory {
  private static final Logger LOGGER = LoggerFactory.getLogger(ReportInstructionHistory.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Path DEFAULT_PATH =
      AppProperties.WEASIS_PATH
          .resolve("data")
          .resolve("report-composer")
          .resolve("instruction-history.jsonl");

  private final Path historyPath;

  ReportInstructionHistory() {
    this(DEFAULT_PATH);
  }

  ReportInstructionHistory(Path historyPath) {
    this.historyPath = Objects.requireNonNull(historyPath).toAbsolutePath().normalize();
  }

  synchronized void record(ExamTemplate exam, String instructions) {
    String cleanInstructions = ComposerText.clean(instructions);
    if (exam == null || cleanInstructions.isBlank()) {
      return;
    }

    try {
      Files.createDirectories(historyPath.getParent());
      HistoryEntry entry =
          new HistoryEntry(Instant.now().toString(), exam.name(), cleanInstructions);
      Files.writeString(
          historyPath,
          MAPPER.writeValueAsString(entry) + System.lineSeparator(),
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (IOException error) {
      LOGGER.warn("Cannot append Report Composer instruction history", error);
    }
  }

  private record HistoryEntry(String recordedAt, String exam, String instructions) {}
}
