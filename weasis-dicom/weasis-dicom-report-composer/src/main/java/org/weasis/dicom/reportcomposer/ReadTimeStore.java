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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.dicom.reportcomposer.ReadClock.Entry;

/** Reading times on this computer, keyed by a hash of the study UID and kept for 90 days. */
final class ReadTimeStore {
  private static final Logger LOGGER = LoggerFactory.getLogger(ReadTimeStore.class);
  private static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  static final Duration RETENTION = Duration.ofDays(90);

  private final Path path;

  ReadTimeStore() {
    this(AppProperties.WEASIS_PATH.resolve("data/report-composer/read-times.json"));
  }

  ReadTimeStore(Path path) {
    this.path = path.toAbsolutePath().normalize();
  }

  static String key(String studyUid) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(studyUid.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest, 0, 16);
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException(error);
    }
  }

  Map<String, Entry> load(Instant now) {
    Map<String, Entry> entries = new TreeMap<>();
    if (!Files.isRegularFile(path)) return entries;
    try {
      Stored stored = MAPPER.readValue(path.toFile(), Stored.class);
      Instant oldest = now.minus(RETENTION);
      stored
          .studies()
          .forEach(
              (key, study) -> {
                Entry entry = study.toEntry();
                if (entry.updatedAt().isAfter(oldest)) entries.put(key, entry);
              });
    } catch (IOException | RuntimeException error) {
      LOGGER.warn("Cannot read Report Composer reading times", error);
    }
    return entries;
  }

  void save(Map<String, Entry> entries) {
    Map<String, StoredStudy> studies = new TreeMap<>();
    entries.forEach((key, entry) -> studies.put(key, StoredStudy.of(entry)));
    try {
      Files.createDirectories(path.getParent());
      Path temporary = Files.createTempFile(path.getParent(), ".read-times-", ".tmp");
      try {
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), new Stored(studies));
        try {
          Files.move(
              temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException error) {
          Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
      } finally {
        Files.deleteIfExists(temporary);
      }
    } catch (IOException error) {
      LOGGER.warn("Cannot save Report Composer reading times", error);
    }
  }

  private record Stored(Map<String, StoredStudy> studies) {
    Stored {
      studies = studies == null ? Map.of() : studies;
    }
  }

  private record StoredStudy(long activeSeconds, String exportedAt, String updatedAt) {
    static StoredStudy of(Entry entry) {
      return new StoredStudy(
          entry.active().toSeconds(),
          entry.exported() ? entry.exportedAt().toString() : null,
          entry.updatedAt().toString());
    }

    Entry toEntry() {
      return new Entry(
          Duration.ofSeconds(activeSeconds),
          exportedAt == null ? null : Instant.parse(exportedAt),
          Instant.parse(updatedAt));
    }
  }
}
