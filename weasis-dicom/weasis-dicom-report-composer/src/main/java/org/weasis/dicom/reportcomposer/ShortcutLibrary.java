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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.ExamTemplate;

/** Personal wording, independent of study drafts. Disk writes run on a serial background worker. */
final class ShortcutLibrary implements AutoCloseable {
  static final String ALL = "ALL";
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final int MAX_SHORTCUTS = 5000;
  private final Path path;
  private final ExecutorService writer =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread thread = new Thread(r, "report-shortcuts-save");
            thread.setDaemon(true);
            return thread;
          });
  private final LinkedHashMap<String, Shortcut> entries = new LinkedHashMap<>();
  private CompletableFuture<Void> pendingSave = CompletableFuture.completedFuture(null);
  private String loadProblem;
  private final Thread shutdownHook;
  private boolean closed;

  record Shortcut(
      String id, String scope, String label, String text, boolean pinned, Map<String, Long> uses) {
    Shortcut {
      uses = uses == null ? Map.of() : Map.copyOf(uses);
    }

    long usesFor(ExamTemplate exam) {
      return uses.getOrDefault(exam.name(), 0L);
    }

    long totalUses() {
      return uses.values().stream().reduce(0L, ShortcutLibrary::addCount);
    }

    boolean appliesTo(ExamTemplate exam) {
      return ALL.equals(scope) || exam.name().equals(scope);
    }

    ReportQuickPhrases.Phrase phrase() {
      return new ReportQuickPhrases.Phrase(label, text);
    }
  }

  record Archive(String format, int version, List<Shortcut> shortcuts) {}

  record ImportResult(int added, int matched) {}

  static ShortcutLibrary openDefault() {
    return new ShortcutLibrary(
        AppProperties.WEASIS_PATH.resolve("data/report-composer/shortcuts.json"));
  }

  // A null path is an isolated, memory-only library, also used by UI tests.
  ShortcutLibrary(Path path) {
    this.path = path == null ? null : path.toAbsolutePath().normalize();
    shutdownHook =
        this.path == null ? null : new Thread(this::flushAtShutdown, "report-shortcuts-shutdown");
    if (shutdownHook != null) Runtime.getRuntime().addShutdownHook(shutdownHook);
    try {
      List<Shortcut> initial =
          this.path != null && Files.exists(this.path) ? read(this.path).shortcuts() : defaults();
      initial.forEach(entry -> entries.put(entry.id(), entry));
    } catch (IOException | RuntimeException error) {
      loadProblem =
          "Shortcut library could not be read. The original file is preserved: " + this.path;
    }
  }

  synchronized String loadProblem() {
    return loadProblem;
  }

  synchronized List<Shortcut> all() {
    return List.copyOf(entries.values());
  }

  synchronized Optional<Shortcut> find(String id) {
    return Optional.ofNullable(entries.get(id));
  }

  synchronized CompletableFuture<Void> pendingSave() {
    return pendingSave;
  }

  synchronized List<Shortcut> ranked(ExamTemplate exam) {
    // The insertion order is the stable tie breaker; labels can be edited without reshuffling ties.
    return entries.values().stream()
        .filter(entry -> entry.appliesTo(exam))
        .sorted(
            Comparator.comparing(Shortcut::pinned)
                .reversed()
                .thenComparing(
                    Comparator.comparingLong((Shortcut entry) -> entry.usesFor(exam)).reversed())
                .thenComparing(entry -> ALL.equals(entry.scope())))
        .toList();
  }

  synchronized Optional<Shortcut> duplicate(String scope, String text, String exceptId) {
    String normalized = normalized(text);
    return entries.values().stream()
        .filter(
            entry ->
                !entry.id().equals(exceptId)
                    && entry.scope().equals(scope)
                    && normalized(entry.text()).equals(normalized))
        .findFirst();
  }

  synchronized Shortcut save(String id, String scope, String label, String text, boolean pinned) {
    ensureWritable();
    Shortcut previous = id == null ? null : entries.get(id);
    if (id != null && previous == null)
      throw new IllegalArgumentException("This shortcut no longer exists.");
    Shortcut entry =
        new Shortcut(
            id == null ? UUID.randomUUID().toString() : id,
            scope,
            label.strip(),
            text,
            pinned,
            previous == null ? Map.of() : previous.uses());
    validate(entry);
    if (duplicate(scope, text, id).isPresent()) {
      throw new IllegalArgumentException("This text already has a shortcut for this body part.");
    }
    if (id == null && entries.size() >= MAX_SHORTCUTS)
      throw new IllegalArgumentException("The shortcut library is full.");
    entries.put(entry.id(), entry);
    queueSave();
    return entry;
  }

  synchronized void remove(String id) {
    ensureWritable();
    if (entries.remove(id) != null) queueSave();
  }

  synchronized void recordUse(String id, ExamTemplate exam) {
    Shortcut entry = entries.get(id);
    if (closed || entry == null || !entry.appliesTo(exam) || loadProblem != null) return;
    Map<String, Long> counts = new HashMap<>(entry.uses());
    counts.put(exam.name(), addCount(entry.usesFor(exam), 1));
    entries.put(
        id, new Shortcut(id, entry.scope(), entry.label(), entry.text(), entry.pinned(), counts));
    queueSave();
  }

  synchronized void retrySave() {
    ensureWritable();
    queueSave();
  }

  void exportTo(Path destination) throws IOException {
    if (path != null
        && (path.equals(destination.toAbsolutePath().normalize())
            || (Files.exists(destination)
                && Files.exists(path)
                && Files.isSameFile(path, destination)))) {
      throw new IOException("Choose a backup location outside the active shortcut library.");
    }
    Archive snapshot;
    synchronized (this) {
      snapshot = archive();
    }
    write(destination.toAbsolutePath().normalize(), snapshot);
  }

  ImportResult importFrom(Path source) throws IOException {
    Archive incoming = read(source); // Validate the entire archive before changing anything.
    synchronized (this) {
      ensureWritable();
      LinkedHashMap<String, Shortcut> merged = new LinkedHashMap<>(entries);
      Map<String, Shortcut> presets = new HashMap<>();
      defaults().forEach(entry -> presets.put(entry.id(), entry));
      int added = 0;
      int matched = 0;
      for (Shortcut candidate : incoming.shortcuts()) {
        Shortcut existing =
            merged.values().stream()
                .filter(
                    entry ->
                        entry.scope().equals(candidate.scope())
                            && normalized(entry.text()).equals(normalized(candidate.text())))
                .findFirst()
                .orElse(null);
        if (existing != null) {
          Map<String, Long> counts = new HashMap<>(existing.uses());
          candidate.uses().forEach((exam, count) -> counts.merge(exam, count, Math::max));
          // Untouched presets accept backed-up customizations, while local edits are retained.
          Shortcut wording =
              existing.id().equals(candidate.id()) && unchangedPreset(existing, presets)
                  ? candidate
                  : existing;
          merged.put(
              existing.id(),
              new Shortcut(
                  existing.id(),
                  wording.scope(),
                  wording.label(),
                  wording.text(),
                  wording.pinned(),
                  counts));
          matched++;
        } else {
          Shortcut sameId = merged.get(candidate.id());
          boolean replacePreset = sameId != null && unchangedPreset(sameId, presets);
          String id =
              sameId != null && !replacePreset ? UUID.randomUUID().toString() : candidate.id();
          Map<String, Long> counts = new HashMap<>(candidate.uses());
          if (replacePreset)
            sameId.uses().forEach((exam, count) -> counts.merge(exam, count, Math::max));
          merged.put(
              id,
              new Shortcut(
                  id,
                  candidate.scope(),
                  candidate.label(),
                  candidate.text(),
                  candidate.pinned(),
                  counts));
          if (replacePreset) matched++;
          else added++;
        }
      }
      if (merged.size() > MAX_SHORTCUTS)
        throw new IOException("This import exceeds the shortcut library limit.");
      entries.clear();
      entries.putAll(merged);
      queueSave();
      return new ImportResult(added, matched);
    }
  }

  private static boolean unchangedPreset(Shortcut entry, Map<String, Shortcut> presets) {
    Shortcut preset = presets.get(entry.id());
    return preset != null
        && entry.scope().equals(preset.scope())
        && entry.label().equals(preset.label())
        && entry.text().equals(preset.text())
        && entry.pinned() == preset.pinned();
  }

  private void ensureWritable() {
    if (closed) throw new IllegalStateException("The shortcut library is closed.");
    if (loadProblem != null) throw new IllegalStateException(loadProblem);
  }

  private Archive archive() {
    return new Archive("weasis-report-shortcuts", 1, List.copyOf(entries.values()));
  }

  private void queueSave() {
    if (path == null) return;
    Archive snapshot = archive();
    pendingSave =
        CompletableFuture.runAsync(
            () -> {
              try {
                write(path, snapshot);
              } catch (IOException error) {
                throw new java.io.UncheckedIOException(error);
              }
            },
            writer);
  }

  private static Archive read(Path source) throws IOException {
    if (Files.size(source) > 10_000_000) throw new IOException("The shortcut file is too large.");
    try {
      Archive archive = MAPPER.readValue(source.toFile(), Archive.class);
      if (archive == null
          || !"weasis-report-shortcuts".equals(archive.format())
          || archive.version() != 1
          || archive.shortcuts() == null
          || archive.shortcuts().size() > MAX_SHORTCUTS) {
        throw new IllegalArgumentException("Unsupported shortcut file.");
      }
      HashSet<String> ids = new HashSet<>();
      HashSet<String> texts = new HashSet<>();
      for (Shortcut entry : archive.shortcuts()) {
        validate(entry);
        if (!ids.add(entry.id()) || !texts.add(entry.scope() + "\n" + normalized(entry.text())))
          throw new IllegalArgumentException("Duplicate shortcut in file.");
      }
      return archive;
    } catch (RuntimeException error) {
      throw new IOException("Invalid shortcut file. Nothing was imported.", error);
    }
  }

  private static void validate(Shortcut entry) {
    Objects.requireNonNull(entry, "Missing shortcut");
    if (entry.id() == null
        || entry.id().isBlank()
        || entry.id().length() > 100
        || entry.label() == null
        || entry.label().isBlank()
        || entry.label().length() > 60
        || entry.text() == null
        || entry.text().isBlank()
        || entry.text().length() > 20_000) {
      throw new IllegalArgumentException(
          "Enter a label (up to 60 characters) and nonblank shortcut text (up to 20,000 characters).");
    }
    if (!ALL.equals(entry.scope())) ExamTemplate.valueOf(entry.scope());
    entry
        .uses()
        .forEach(
            (exam, count) -> {
              ExamTemplate.valueOf(exam);
              if (count < 0) throw new IllegalArgumentException("Invalid usage count.");
            });
  }

  private static void write(Path destination, Archive archive) throws IOException {
    Files.createDirectories(destination.getParent());
    Path temporary = Files.createTempFile(destination.getParent(), ".shortcuts-", ".tmp");
    try {
      MAPPER.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), archive);
      try {
        Files.move(
            temporary,
            destination,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException error) {
        Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private static String normalized(String text) {
    return text.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }

  private static long addCount(long a, long b) {
    return Long.MAX_VALUE - a < b ? Long.MAX_VALUE : a + b;
  }

  private static List<Shortcut> defaults() {
    List<Shortcut> result = new ArrayList<>();
    for (ExamTemplate exam : ExamTemplate.values()) {
      int index = 0;
      for (var phrase : ReportQuickPhrases.forExam(exam)) {
        result.add(
            new Shortcut(
                "default-" + exam.name() + "-" + index++,
                exam.name(),
                phrase.label(),
                phrase.text(),
                false,
                Map.of()));
      }
    }
    List<ReportQuickPhrases.Phrase> common =
        new ArrayList<>(List.of(ReportQuickPhrases.MOTION, ReportQuickPhrases.ARTIFACT));
    common.addAll(ReportQuickPhrases.COMMON_MORE);
    for (int i = 0; i < common.size(); i++) {
      var phrase = common.get(i);
      result.add(
          new Shortcut("default-ALL-" + i, ALL, phrase.label(), phrase.text(), false, Map.of()));
    }
    return result;
  }

  @Override
  public synchronized void close() {
    if (closed) return;
    closed = true;
    writer.execute(
        () -> {
          if (shutdownHook != null) {
            try {
              Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
              // The running hook waits for the queued writes below.
            }
          }
        });
    writer.shutdown();
  }

  private void flushAtShutdown() {
    close();
    try {
      writer.awaitTermination(10, TimeUnit.SECONDS);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
    }
  }
}
