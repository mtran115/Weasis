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
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.dicom.reportcomposer.TranscriptionDestination.Destination;
import org.weasis.dicom.reportcomposer.TranscriptionDestination.DirectoryUnavailableException;

class TranscriptionDestinationTest {
  @TempDir Path temporaryDirectory;

  @Test
  void defaultFollowsCurrentStudyAndIsCreatedOnlyWhenExporting() throws Exception {
    Path first = Files.createDirectory(temporaryDirectory.resolve("first"));
    Path second = Files.createDirectory(temporaryDirectory.resolve("second"));
    TranscriptionDestination policy = new TranscriptionDestination();
    Destination destination = policy.resolve("study-a", Optional.of(first)).orElseThrow();
    assertEquals(first.resolve("notes"), destination.directory());
    assertTrue(destination.automatic());
    assertFalse(Files.exists(destination.directory()));
    assertEquals(
        second.resolve("notes"),
        policy.resolve("study-b", Optional.of(second)).orElseThrow().directory());
    assertEquals(destination, policy.resolve("study-a", Optional.of(first)).orElseThrow());
    assertEquals(first.resolve("notes"), TranscriptionDestination.prepare(destination));
    assertTrue(Files.isDirectory(destination.directory()));
    assertFalse(Files.exists(second.resolve("notes")));
  }

  @Test
  void existingNotesAndUserFilesArePreserved() throws Exception {
    Path notes = Files.createDirectory(temporaryDirectory.resolve("notes"));
    Path existing = Files.writeString(notes.resolve("existing.txt"), "keep me");
    Destination destination = new Destination(notes, true);
    TranscriptionDestination.prepare(destination);
    TranscriptionDestination.prepare(destination);
    assertEquals("keep me", Files.readString(existing));
  }

  @Test
  void manualChoiceAppliesToSameImportBatchButDoesNotLeakToAnotherFolder() {
    TranscriptionDestination policy = new TranscriptionDestination();
    Path first = temporaryDirectory.resolve("first");
    Path second = temporaryDirectory.resolve("second");
    Path manual = temporaryDirectory.resolve("transcription");
    policy.choose("study-a", Optional.of(first), manual);
    Destination selected = policy.resolve("study-b", Optional.of(first)).orElseThrow();
    assertEquals(manual, selected.directory());
    assertFalse(selected.automatic());
    assertEquals(
        second.resolve("notes"),
        policy.resolve("study-c", Optional.of(second)).orElseThrow().directory());
    assertEquals(manual, policy.resolve("study-a", Optional.of(first)).orElseThrow().directory());
  }

  @Test
  void unknownOrAmbiguousSourceRequiresChoiceForThatStudy() {
    TranscriptionDestination policy = new TranscriptionDestination();
    assertTrue(policy.resolve("study-a", Optional.empty()).isEmpty());
    assertTrue(policy.resolve(null, Optional.of(temporaryDirectory)).isEmpty());
    policy.choose("study-a", Optional.empty(), temporaryDirectory);
    assertEquals(
        temporaryDirectory, policy.resolve("study-a", Optional.empty()).orElseThrow().directory());
    assertTrue(policy.resolve("study-b", Optional.empty()).isEmpty());
  }

  @Test
  void aFileNamedNotesFailsWithoutReplacingIt() throws Exception {
    Path notes = Files.writeString(temporaryDirectory.resolve("notes"), "keep this file");
    assertThrows(
        DirectoryUnavailableException.class,
        () -> TranscriptionDestination.prepare(new Destination(notes, true)));
    assertEquals("keep this file", Files.readString(notes));
  }

  @Test
  void missingStudyFolderIsNotRecreated() {
    Path missing = temporaryDirectory.resolve("removed-study-folder");
    assertThrows(
        DirectoryUnavailableException.class,
        () -> TranscriptionDestination.prepare(new Destination(missing.resolve("notes"), true)));
    assertFalse(Files.exists(missing));
  }

  @Test
  void unwritableNotesRequiresAnotherDestination() throws Exception {
    assumeTrue(Files.getFileStore(temporaryDirectory).supportsFileAttributeView("posix"));
    Path notes = Files.createDirectory(temporaryDirectory.resolve("notes"));
    var original = Files.getPosixFilePermissions(notes);
    try {
      Files.setPosixFilePermissions(notes, PosixFilePermissions.fromString("r-xr-xr-x"));
      assumeFalse(Files.isWritable(notes), "Privileged test processes may bypass permissions");
      assertThrows(
          DirectoryUnavailableException.class,
          () -> TranscriptionDestination.prepare(new Destination(notes, true)));
    } finally {
      Files.setPosixFilePermissions(notes, original);
    }
  }
}
