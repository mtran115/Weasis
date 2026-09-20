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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.ExamTemplate;

class ShortcutLibraryTest {
  @TempDir Path temporary;

  @Test
  void wordingPinsPerExamCountsAndPresetDeletionsSurviveRestart() throws Exception {
    Path file = temporary.resolve("library.json");
    String id;
    String verbatim = "  T2 hyperint lesion [level] likely intraoss hemang\n";
    try (var library = new ShortcutLibrary(file)) {
      var entry =
          library.save(null, ExamTemplate.LUMBAR_SPINE.name(), "Hemang shorthand", verbatim, true);
      id = entry.id();
      library.recordUse(id, ExamTemplate.LUMBAR_SPINE);
      library.recordUse(id, ExamTemplate.KNEE); // Different body part must not count.
      library.remove("default-ALL-0");
      library.save(id, ExamTemplate.LUMBAR_SPINE.name(), "Hemang", verbatim, true);
      library.pendingSave().get();
    }
    try (var restored = new ShortcutLibrary(file)) {
      assertNull(restored.loadProblem());
      var entry = restored.find(id).orElseThrow();
      assertEquals(verbatim, entry.text());
      assertEquals("Hemang", entry.label());
      assertTrue(entry.pinned());
      assertEquals(1, entry.usesFor(ExamTemplate.LUMBAR_SPINE));
      assertEquals(0, entry.usesFor(ExamTemplate.KNEE));
      assertTrue(restored.find("default-ALL-0").isEmpty());
      assertEquals(id, restored.ranked(ExamTemplate.LUMBAR_SPINE).getFirst().id());
      assertFalse(restored.ranked(ExamTemplate.KNEE).contains(entry));
    }
  }

  @Test
  void sharedPhrasesRankIndependentlyForEachBodyPart() {
    try (var library = new ShortcutLibrary(null)) {
      String shared = "default-ALL-0";
      library.recordUse(shared, ExamTemplate.KNEE);
      library.recordUse(shared, ExamTemplate.KNEE);
      assertEquals(shared, library.ranked(ExamTemplate.KNEE).getFirst().id());
      assertNotEquals(shared, library.ranked(ExamTemplate.SHOULDER).getFirst().id());
      var pinned = library.save(null, ExamTemplate.KNEE.name(), "Pinned", "new custom text", true);
      assertEquals(pinned.id(), library.ranked(ExamTemplate.KNEE).getFirst().id());
      library.save(pinned.id(), ExamTemplate.SHOULDER.name(), "Moved", pinned.text(), true);
      assertEquals(shared, library.ranked(ExamTemplate.KNEE).getFirst().id());
      assertEquals(pinned.id(), library.ranked(ExamTemplate.SHOULDER).getFirst().id());
    }
  }

  @Test
  void duplicateDetectionIgnoresCaseAndSpacingWithinACategoryWithoutRewritingText() {
    try (var library = new ShortcutLibrary(null)) {
      var entry =
          library.save(null, ExamTemplate.KNEE.name(), "Shorthand", "  int dec\n  edema ", false);
      assertEquals(
          entry, library.duplicate(ExamTemplate.KNEE.name(), "INT DEC edema", null).orElseThrow());
      assertThrows(
          IllegalArgumentException.class,
          () -> library.save(null, ExamTemplate.KNEE.name(), "Again", "int dec edema", false));
      assertTrue(library.duplicate(ExamTemplate.ANKLE.name(), entry.text(), null).isEmpty());
      assertTrue(library.duplicate(entry.scope(), entry.text(), entry.id()).isEmpty());
      assertEquals("  int dec\n  edema ", library.find(entry.id()).orElseThrow().text());
      assertThrows(
          IllegalArgumentException.class,
          () -> library.save(null, ShortcutLibrary.ALL, "", "text", false));
    }
  }

  @Test
  void exportAndRepeatedImportsMergeWithoutDoubleCountingOrOverwritingLocalEdits()
      throws Exception {
    Path backup = temporary.resolve("backup.json");
    Path destination = temporary.resolve("destination.json");
    try (var source = new ShortcutLibrary(null);
        var target = new ShortcutLibrary(destination)) {
      var custom =
          source.save(null, ExamTemplate.LUMBAR_SPINE.name(), "Original", "custom [level]", true);
      source.recordUse(custom.id(), ExamTemplate.LUMBAR_SPINE);
      source.recordUse(custom.id(), ExamTemplate.LUMBAR_SPINE);
      source.exportTo(backup);
      assertEquals(1, target.importFrom(backup).added());
      target.save(custom.id(), custom.scope(), "Local label", custom.text(), false);
      target.recordUse(custom.id(), ExamTemplate.LUMBAR_SPINE);
      assertEquals(0, target.importFrom(backup).added());
      var retained = target.find(custom.id()).orElseThrow();
      assertEquals("Local label", retained.label());
      assertFalse(retained.pinned());
      assertEquals(3, retained.usesFor(ExamTemplate.LUMBAR_SPINE));
      // If the same ID has different wording, preserve both versions and remain idempotent.
      target.save(custom.id(), custom.scope(), "Changed text", "different local text", false);
      assertEquals(1, target.importFrom(backup).added());
      assertEquals(0, target.importFrom(backup).added());
      assertTrue(target.duplicate(custom.scope(), custom.text(), null).isPresent());
      assertTrue(target.duplicate(custom.scope(), "different local text", null).isPresent());
      target.pendingSave().get();
      assertThrows(IOException.class, () -> target.exportTo(destination));
    }
  }

  @Test
  void importingRestoresCustomizedPresetsOnAFreshInstallationButKeepsLocalEdits() throws Exception {
    Path backup = temporary.resolve("customized-presets.json");
    try (var source = new ShortcutLibrary(null);
        var target = new ShortcutLibrary(null)) {
      var motion = source.find("default-ALL-0").orElseThrow();
      source.save(motion.id(), motion.scope(), "My motion phrase", motion.text(), true);
      var lumbar = source.find("default-LUMBAR_SPINE-0").orElseThrow();
      source.save(
          lumbar.id(), lumbar.scope(), "My shorthand", "my abbreviated lumbar wording", true);
      source.exportTo(backup);
      target.importFrom(backup);
      assertEquals("My motion phrase", target.find(motion.id()).orElseThrow().label());
      assertTrue(target.find(motion.id()).orElseThrow().pinned());
      assertEquals("my abbreviated lumbar wording", target.find(lumbar.id()).orElseThrow().text());
      assertTrue(target.find(lumbar.id()).orElseThrow().pinned());
      target.save(motion.id(), motion.scope(), "Local motion label", motion.text(), false);
      assertEquals(0, target.importFrom(backup).added());
      assertEquals("Local motion label", target.find(motion.id()).orElseThrow().label());
      assertFalse(target.find(motion.id()).orElseThrow().pinned());
    }
  }

  @Test
  void invalidOrFutureArchiveCannotPartiallyImportOrOverwriteAnExistingLibrary() throws Exception {
    Path file = temporary.resolve("library.json");
    Path invalid = temporary.resolve("invalid.json");
    try (var library = new ShortcutLibrary(file)) {
      library.recordUse("default-ALL-0", ExamTemplate.BRAIN);
      library.pendingSave().get();
      String saved = Files.readString(file);
      var before = library.all();
      Files.writeString(invalid, saved.replace("\"version\" : 1", "\"version\" : 999"));
      assertThrows(IOException.class, () -> library.importFrom(invalid));
      assertEquals(before, library.all());
      var tree = new ObjectMapper().readTree(saved);
      ((com.fasterxml.jackson.databind.node.ObjectNode) tree.path("shortcuts").get(1))
          .put("scope", "NOT_AN_EXAM");
      Files.writeString(invalid, tree.toString());
      assertThrows(IOException.class, () -> library.importFrom(invalid));
      assertEquals(before, library.all());
      assertEquals(saved, Files.readString(file));
    }
    Files.writeString(file, "broken existing library");
    try (var library = new ShortcutLibrary(file)) {
      assertNotNull(library.loadProblem());
      assertThrows(
          IllegalStateException.class,
          () -> library.save(null, ShortcutLibrary.ALL, "Test", "text", false));
      assertEquals("broken existing library", Files.readString(file));
    }
  }

  @Test
  void failedSaveKeepsMemoryAndCanBeRetriedWithoutLosingCounts() throws Exception {
    Path directory = temporary.resolve("blocked");
    Path file = directory.resolve("library.json");
    try (var library = new ShortcutLibrary(file)) {
      Files.writeString(directory, "not a directory");
      var entry = library.save(null, ShortcutLibrary.ALL, "Test", "my shorthand", false);
      assertThrows(ExecutionException.class, () -> library.pendingSave().get());
      assertTrue(library.find(entry.id()).isPresent());
      library.recordUse(entry.id(), ExamTemplate.BRAIN);
      assertThrows(ExecutionException.class, () -> library.pendingSave().get());
      Files.delete(directory);
      library.retrySave();
      library.pendingSave().get();
      try (var restored = new ShortcutLibrary(file)) {
        assertEquals(1, restored.find(entry.id()).orElseThrow().usesFor(ExamTemplate.BRAIN));
      }
    }
  }

  @Test
  void closingDrainsPendingWritesAndPreventsUnsavedEdits() throws Exception {
    Path file = temporary.resolve("closing.json");
    var library = new ShortcutLibrary(file);
    var entry = library.save(null, ShortcutLibrary.ALL, "Before closing", "saved wording", true);
    library.recordUse(entry.id(), ExamTemplate.BRAIN);
    library.close();
    library.close();
    library.pendingSave().get();
    library.recordUse(entry.id(), ExamTemplate.BRAIN);
    assertThrows(IllegalStateException.class, () -> library.remove(entry.id()));
    try (var restored = new ShortcutLibrary(file)) {
      assertEquals(1, restored.find(entry.id()).orElseThrow().totalUses());
    }
  }

  @Test
  void everySeededPhraseIsValidAndUsageNeverChangesTheStableOrderOfTies() throws Exception {
    Path backup = temporary.resolve("defaults.json");
    try (var library = new ShortcutLibrary(null)) {
      library.exportTo(backup);
      try (var loaded = new ShortcutLibrary(backup)) {
        assertNull(loaded.loadProblem());
        assertEquals(library.all(), loaded.all());
      }
      var order =
          library.ranked(ExamTemplate.KNEE).stream().map(ShortcutLibrary.Shortcut::id).toList();
      var first = library.ranked(ExamTemplate.KNEE).getFirst();
      library.save(first.id(), first.scope(), "ZZZ renamed", first.text(), first.pinned());
      assertEquals(
          order,
          library.ranked(ExamTemplate.KNEE).stream().map(ShortcutLibrary.Shortcut::id).toList());
    }
  }
}
