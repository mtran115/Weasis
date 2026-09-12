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

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Destination policy; resolving a default never touches the filesystem or delays image viewing. */
final class TranscriptionDestination {
  private final Map<Scope, Path> overrides = new HashMap<>();

  Optional<Destination> resolve(String studyKey, Optional<Path> importDirectory) {
    if (studyKey == null || studyKey.isBlank()) return Optional.empty();
    Scope scope = scope(studyKey, importDirectory);
    Path override = overrides.get(scope);
    if (override != null) return Optional.of(new Destination(override, false));
    return importDirectory.map(
        directory ->
            new Destination(directory.resolve("notes").toAbsolutePath().normalize(), true));
  }

  void choose(String studyKey, Optional<Path> importDirectory, Path directory) {
    overrides.put(scope(studyKey, importDirectory), directory.toAbsolutePath().normalize());
  }

  private static Scope scope(String studyKey, Optional<Path> importDirectory) {
    return new Scope(
        importDirectory.isPresent() ? "" : studyKey,
        importDirectory.map(path -> path.toAbsolutePath().normalize()).orElse(null));
  }

  /** Called only by the export worker, before any packet or completed annotation is written. */
  static Path prepare(Destination destination) throws DirectoryUnavailableException {
    Path directory = destination.directory();
    try {
      if (destination.automatic()) {
        // A disconnected drive or deleted import folder must not be silently recreated.
        if (!Files.isDirectory(directory.getParent()))
          throw new IOException("The study folder is no longer available.");
        try {
          Files.createDirectory(directory);
        } catch (FileAlreadyExistsException exists) {
          if (!Files.isDirectory(directory)) throw exists;
        }
      } else {
        Files.createDirectories(directory);
      }
      if (!Files.isDirectory(directory) || !Files.isWritable(directory)) {
        throw new IOException("The transcription folder is not writable.");
      }
      return directory;
    } catch (IOException | SecurityException error) {
      throw new DirectoryUnavailableException(directory, error);
    }
  }

  record Destination(Path directory, boolean automatic) {}

  private record Scope(String studyKey, Path importDirectory) {}

  static final class DirectoryUnavailableException extends IOException {
    DirectoryUnavailableException(Path directory, Exception cause) {
      super(
          "The transcription folder could not be created or is not writable: " + directory, cause);
    }
  }
}
