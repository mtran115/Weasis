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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.dicom.reportcomposer.ReadClock.Entry;

class ReadTimeStoreTest {
  private static final Instant NOW = Instant.parse("2026-10-29T20:00:00Z");
  private static final String STUDY_UID = "1.2.840.113619.2.55.3.604688119.971.1234567890.123";

  @TempDir Path temporaryDirectory;

  @Test
  void savesAndReloadsTimesUnderAHashedStudyKey() throws Exception {
    Path file = temporaryDirectory.resolve("read-times.json");
    ReadTimeStore store = new ReadTimeStore(file);
    String key = ReadTimeStore.key(STUDY_UID);
    Entry exported = new Entry(Duration.ofSeconds(272), NOW, NOW);
    Entry inProgress = new Entry(Duration.ofSeconds(45), null, NOW);

    store.save(Map.of(key, exported, "other", inProgress));

    assertFalse(Files.readString(file).contains(STUDY_UID));
    assertEquals(Map.of(key, exported, "other", inProgress), store.load(NOW));
  }

  @Test
  void dropsTimesOlderThanTheRetention() {
    ReadTimeStore store = new ReadTimeStore(temporaryDirectory.resolve("read-times.json"));
    Instant old = NOW.minus(ReadTimeStore.RETENTION).minusSeconds(1);
    store.save(
        Map.of(
            "old", new Entry(Duration.ofSeconds(60), old, old),
            "recent", new Entry(Duration.ofSeconds(60), NOW, NOW)));

    assertEquals(Map.of("recent", new Entry(Duration.ofSeconds(60), NOW, NOW)), store.load(NOW));
  }

  @Test
  void aMissingOrDamagedFileLoadsNoTimes() throws Exception {
    Path file = temporaryDirectory.resolve("read-times.json");
    assertTrue(new ReadTimeStore(file).load(NOW).isEmpty());

    Files.writeString(file, "{not json");
    assertTrue(new ReadTimeStore(file).load(NOW).isEmpty());
  }

  @Test
  void formatsMinutesAndHours() {
    assertEquals("0:07", ReadTimerLabel.format(Duration.ofSeconds(7)));
    assertEquals("4:32", ReadTimerLabel.format(Duration.ofSeconds(272)));
    assertEquals("1:02:05", ReadTimerLabel.format(Duration.ofSeconds(3725)));
  }
}
