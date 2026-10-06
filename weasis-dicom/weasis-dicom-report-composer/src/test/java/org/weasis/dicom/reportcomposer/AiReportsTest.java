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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AiReportsTest {
  private static final String STUDY = "1.2.840.113619.2.55.3.1234";
  private static final String OTHER_STUDY = "1.2.840.113619.2.55.3.9999";

  @TempDir Path root;

  @Test
  void listsEveryAnswerForTheStudyNewestFirst() throws IOException {
    Path morning = packet("ai-packet-20261005-090000-aaaaaa");
    Path afternoon = packet("ai-packet-20261005-150000-bbbbbb");
    Path elsewhere = packet("ai-packet-20261005-160000-cccccc");
    answer(morning, "answer-openai-20261005-090500.txt", "OpenAI · gpt-6-astra · 2026-10-05 09:05");
    answer(
        afternoon,
        "answer-anthropic-20261005-151000.txt",
        "Claude · claude-opus-5-5 · 2026-10-05 15:10");
    answer(
        afternoon, "answer-openai-20261005-152000.txt", "OpenAI · gpt-6.1-sol · 2026-10-05 15:20");
    answer(
        elsewhere, "answer-openai-20261005-160500.txt", "OpenAI · gpt-6-astra · 2026-10-05 16:05");
    AiReports.link(root, STUDY, morning);
    AiReports.link(root, STUDY, afternoon);
    AiReports.link(root, STUDY, afternoon);
    AiReports.link(root, OTHER_STUDY, elsewhere);

    List<AiReports.Report> reports = AiReports.forStudy(root, STUDY);

    LocalDate today = LocalDate.of(2026, 10, 5);
    assertEquals(
        List.of("gpt-6.1-sol · 3:20 PM", "claude-opus-5-5 · 3:10 PM", "gpt-6-astra · 9:05 AM"),
        reports.stream().map(r -> r.label(today)).toList());
    assertEquals("Claude", reports.get(1).provider());
    assertEquals(afternoon, reports.get(1).packet());
    assertEquals("gpt-6-astra · Oct 5, 9:05 AM", reports.get(2).label(LocalDate.of(2026, 10, 6)));
    assertEquals(1, AiReports.forStudy(root, OTHER_STUDY).size());
    assertTrue(AiReports.forStudy(root, "unknown").isEmpty());
    assertTrue(AiReports.forStudy(root, null).isEmpty());
  }

  @Test
  void theIndexHoldsOnlyAHashAndForgetsDeletedPackets() throws IOException {
    Path kept = packet("ai-packet-20261005-090000-aaaaaa");
    Path deleted = packet("ai-packet-20261001-090000-dddddd");
    AiReports.link(root, STUDY, deleted);
    deleteRecursively(deleted);

    AiReports.link(root, STUDY, kept);

    String index = Files.readString(root.resolve(AiReports.INDEX));
    assertFalse(index.contains(STUDY));
    assertTrue(index.contains(AiReports.hash(STUDY)));
    assertTrue(index.contains(kept.getFileName().toString()));
    assertFalse(index.contains(deleted.getFileName().toString()));
  }

  @Test
  void answersWithoutAHeadingFallBackToTheFileNameAndTime() throws IOException {
    Path packet = packet("ai-packet-20261005-090000-aaaaaa");
    answer(packet, "answer-openai.txt", "FINDINGS");
    AiReports.link(root, STUDY, packet);

    AiReports.Report report = AiReports.forStudy(root, STUDY).getFirst();

    assertEquals("answer-openai.txt", report.model());
    assertTrue(report.time().isAfter(LocalDateTime.now().minusMinutes(5)));
  }

  private Path packet(String name) throws IOException {
    return Files.createDirectories(root.resolve(name));
  }

  private static void answer(Path packet, String file, String heading) throws IOException {
    Files.writeString(packet.resolve(file), heading + "\nImages sent: 1 of 1\n\nFINDINGS\n");
  }

  private static void deleteRecursively(Path path) throws IOException {
    try (Stream<Path> walk = Files.walk(path)) {
      for (Path entry : walk.sorted(Comparator.reverseOrder()).toList()) {
        Files.delete(entry);
      }
    }
  }
}
