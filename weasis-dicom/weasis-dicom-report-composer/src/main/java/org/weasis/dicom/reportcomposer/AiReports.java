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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * The AI answers saved for each study. Packets stay de-identified, so a separate index beside them
 * links a one-way hash of the study key to the packets made from that study.
 */
final class AiReports {
  static final String INDEX = "study-index.json";
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final DateTimeFormatter HEADING_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  record Report(Path file, Path packet, String provider, String model, LocalDateTime time) {
    String label(LocalDate today) {
      String when =
          time.toLocalDate().equals(today)
              ? time.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
              : time.format(DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US));
      return model + " · " + when;
    }
  }

  private AiReports() {}

  /** Remembers that a packet came from a study, forgetting packets that have been deleted. */
  static synchronized void link(Path root, String studyKey, Path packet) throws IOException {
    ObjectNode index = readIndex(root);
    ObjectNode studies =
        index.get("studies") instanceof ObjectNode existing ? existing : index.putObject("studies");
    String hash = hash(studyKey);
    ArrayNode packets = studies.has(hash) ? (ArrayNode) studies.get(hash) : studies.putArray(hash);
    String name = packet.getFileName().toString();
    if (!strings(packets).contains(name)) {
      packets.add(name);
    }
    List<String> emptied = new ArrayList<>();
    studies
        .properties()
        .forEach(
            study -> {
              ArrayNode names = (ArrayNode) study.getValue();
              for (int i = names.size() - 1; i >= 0; i--) {
                if (!Files.isDirectory(root.resolve(names.get(i).asText()))) {
                  names.remove(i);
                }
              }
              if (names.isEmpty()) {
                emptied.add(study.getKey());
              }
            });
    studies.remove(emptied);
    Path temporary = root.resolve("." + INDEX + ".tmp");
    JSON.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), index);
    Files.move(
        temporary,
        root.resolve(INDEX),
        StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.ATOMIC_MOVE);
  }

  /** Answers for the study, newest first. */
  static List<Report> forStudy(Path root, String studyKey) {
    if (studyKey == null || studyKey.isBlank()) {
      return List.of();
    }
    JsonNode packets = readIndex(root).path("studies").path(hash(studyKey));
    List<Report> reports = new ArrayList<>();
    for (String name : strings(packets)) {
      Path packet = root.resolve(name);
      if (!Files.isDirectory(packet)) {
        continue;
      }
      try (Stream<Path> files = Files.list(packet)) {
        files
            .filter(f -> f.getFileName().toString().matches("answer-.+\\.txt"))
            .map(f -> report(packet, f))
            .forEach(reports::add);
      } catch (IOException e) {
        // A packet being deleted or written is skipped until the next refresh.
      }
    }
    reports.sort(
        Comparator.comparing(Report::time).thenComparing(r -> r.file().toString()).reversed());
    return List.copyOf(reports);
  }

  static String hash(String studyKey) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(studyKey.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }

  // The answer's first line reads "Provider · model · yyyy-MM-dd HH:mm".
  private static Report report(Path packet, Path file) {
    String provider = "";
    String model = file.getFileName().toString();
    LocalDateTime time = modified(file);
    try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      String[] heading = String.valueOf(reader.readLine()).split(" · ");
      if (heading.length == 3) {
        provider = heading[0].strip();
        model = heading[1].strip();
        time = LocalDateTime.parse(heading[2].strip(), HEADING_TIME);
      }
    } catch (IOException | DateTimeParseException e) {
      // Fall back to the file name and modification time.
    }
    return new Report(file, packet, provider, model, time);
  }

  private static LocalDateTime modified(Path file) {
    try {
      return LocalDateTime.ofInstant(
          Files.getLastModifiedTime(file).toInstant(), ZoneId.systemDefault());
    } catch (IOException e) {
      return LocalDateTime.MIN;
    }
  }

  private static ObjectNode readIndex(Path root) {
    Path index = root.resolve(INDEX);
    try {
      if (Files.isRegularFile(index) && JSON.readTree(index.toFile()) instanceof ObjectNode node) {
        return node;
      }
    } catch (IOException e) {
      // A damaged index starts over; packets themselves are unaffected.
    }
    ObjectNode node = JSON.createObjectNode();
    node.put("version", 1);
    return node;
  }

  private static List<String> strings(JsonNode values) {
    List<String> strings = new ArrayList<>();
    values.forEach(value -> strings.add(value.asText()));
    return strings;
  }
}
