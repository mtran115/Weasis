/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.recorder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InteractionRecorderTest {
  private static final ZoneId ZONE = ZoneId.of("America/Los_Angeles");
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-13T20:00:00Z"), ZONE);
  private static final Map<String, Object> SERIES =
      Map.of(
          InteractionRecorder.STUDY_UID,
          "1.2.840.113619.2.55",
          "modality",
          "MR",
          "study_desc",
          "MRI LUMBAR SPINE",
          "body_part",
          "LSPINE",
          "series_desc",
          "T2 SAG");

  @TempDir Path directory;

  private InteractionRecorder recorder(Path dir, boolean enabled) {
    return new InteractionRecorder(dir, ZONE, CLOCK, series -> Map.of(), enabled);
  }

  private List<String> lines(Path dir) throws IOException {
    return Files.readAllLines(dir.resolve("2026-10-13.jsonl"));
  }

  @Test
  void actionsGoToTheDaysFile() throws IOException {
    InteractionRecorder recorder = recorder(directory, true);
    recorder.add("report_copied", null, Map.of("chars", 812), Map.of());
    recorder.flush();

    assertEquals(
        List.of("{\"t\":\"2026-10-13T13:00:00-07:00\",\"type\":\"report_copied\",\"chars\":812}"),
        lines(directory));
  }

  @Test
  void nothingIsWrittenWhenRecordingIsOff() {
    InteractionRecorder recorder = recorder(directory, false);
    recorder.add("report_copied", null, Map.of(), Map.of());
    recorder.flush();

    assertFalse(Files.exists(directory.resolve("2026-10-13.jsonl")));
  }

  @Test
  void studyIsWrittenOnlyAsASaltedHashAndAnnouncedOncePerStudy() throws IOException {
    InteractionRecorder recorder = recorder(directory, true);
    recorder.add("series_shown", null, SERIES, Map.of());
    recorder.add("select_view", null, SERIES, Map.of());
    recorder.flush();

    List<String> lines = lines(directory);
    assertEquals(3, lines.size());
    assertTrue(lines.get(0).contains("\"type\":\"study\""));
    assertTrue(
        lines.get(0).contains("\"study_desc\":\"MRI LUMBAR SPINE\",\"body_part\":\"LSPINE\""));
    assertTrue(lines.get(1).contains("\"type\":\"series_shown\""));
    assertTrue(lines.get(1).contains("\"series_desc\":\"T2 SAG\""));
    assertFalse(lines.get(1).contains("study_desc"));
    assertTrue(lines.get(2).contains("\"type\":\"select_view\""));
    for (String line : lines) assertFalse(line.contains("1.2.840.113619"), line);

    String hash = lines.get(1).replaceAll(".*\"study\":\"([0-9a-f]+)\".*", "$1");
    assertEquals(12, hash.length());
    Path other = directory.resolve("other");
    InteractionRecorder otherSalt = recorder(other, true);
    otherSalt.add("series_shown", null, SERIES, Map.of());
    otherSalt.flush();
    assertNotEquals(hash, lines(other).get(1).replaceAll(".*\"study\":\"([0-9a-f]+)\".*", "$1"));

    InteractionRecorder sameSalt = recorder(directory, true);
    sameSalt.add("series_shown", null, SERIES, Map.of());
    sameSalt.flush();
    assertTrue(lines(directory).get(4).contains("\"study\":\"" + hash + "\""));
  }

  @Test
  void filesOlderThanTheRetentionAreDeleted() throws IOException {
    Files.createDirectories(directory);
    for (String name :
        List.of("2026-09-12.jsonl", "2026-09-13.jsonl", "2026-10-12.jsonl", "notes.jsonl")) {
      Files.writeString(directory.resolve(name), "{}\n");
    }

    recorder(directory, true).deleteExpiredFiles();

    assertFalse(Files.exists(directory.resolve("2026-09-12.jsonl")));
    assertTrue(Files.exists(directory.resolve("2026-09-13.jsonl")));
    assertTrue(Files.exists(directory.resolve("2026-10-12.jsonl")));
    assertTrue(Files.exists(directory.resolve("notes.jsonl")));
  }

  @Test
  void keyCombosNameTheModifiersAndNumPad() {
    JPanel source = new JPanel();
    KeyEvent undo =
        new KeyEvent(
            source, KeyEvent.KEY_PRESSED, 0, InputEvent.META_DOWN_MASK, KeyEvent.VK_Z, 'z');
    KeyEvent plus =
        new KeyEvent(
            source, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ADD, '+', KeyEvent.KEY_LOCATION_NUMPAD);

    assertTrue(InteractionRecorder.combo(undo).endsWith("+Z"));
    assertTrue(InteractionRecorder.combo(plus).startsWith("NumPad"));
  }

  static final class ReportPanel extends JPanel {
    private final JTextArea impressionText = new JTextArea("Patient has a secret");

    ReportPanel() {
      JPanel section = new JPanel();
      section.add(impressionText);
      add(section);
    }
  }

  @Test
  void textBoxesAreNamedByTheirHolderOrLabelNeverByTheirText() {
    ReportPanel panel = new ReportPanel();
    JTextField labeled = new JTextField("hidden");
    new JLabel("Disc level").setLabelFor(labeled);

    assertEquals(
        "impressionText (ReportPanel)", InteractionRecorder.describeField(panel.impressionText));
    assertEquals("Disc level", InteractionRecorder.describeField(labeled));
    assertEquals("JTextField", InteractionRecorder.describeField(new JTextField("hidden")));
  }

  @Test
  void valuesAreRoundedOrShortened() {
    assertEquals(1.235, InteractionRecorder.simple(1.23456));
    assertEquals(400, InteractionRecorder.simple(400));
    assertEquals(80, ((String) InteractionRecorder.simple("x".repeat(200))).length());
    assertEquals(null, InteractionRecorder.simple(null));
  }
}
