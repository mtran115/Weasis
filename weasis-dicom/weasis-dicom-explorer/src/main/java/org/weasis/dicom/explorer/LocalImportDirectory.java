/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;

/** Remembers selected import roots without changing the source DICOM files. */
public final class LocalImportDirectory {
  private static final TagW IMPORT_DIRECTORIES =
      new TagW("LocalImportDirectories", TagW.TagType.OBJECT);

  private LocalImportDirectory() {}

  public static void record(MediaSeriesGroup study, Path directory) {
    if (study == null || directory == null) return;
    Path normalized = directory.toAbsolutePath().normalize();
    synchronized (study) {
      Origins previous = origins(study);
      if (previous.directories().contains(normalized)) return;
      Set<Path> paths = new LinkedHashSet<>(previous.directories());
      paths.add(normalized);
      study.setTag(IMPORT_DIRECTORIES, new Origins(Set.copyOf(paths)));
    }
  }

  /** Empty when the study came from multiple roots, a temporary download, or an unknown source. */
  public static Optional<Path> find(MediaSeries<?> anchor) {
    if (anchor == null || !(anchor.getTagValue(TagW.ExplorerModel) instanceof DicomModel model))
      return Optional.empty();
    MediaSeriesGroup study = model.getParent(anchor, DicomModel.study);
    if (study == null) return Optional.empty();
    synchronized (study) {
      Set<Path> directories = origins(study).directories();
      if (directories.size() != 1) return Optional.empty();
      Path directory = directories.iterator().next();
      return directory.getParent() != null
              && !directory.startsWith(AppProperties.APP_TEMP_DIR.toAbsolutePath().normalize())
          ? Optional.of(directory)
          : Optional.empty();
    }
  }

  /** Each selected directory retains its own root, including recursive imports of batch folders. */
  static Optional<Path> forFile(Path file, List<Path> selectedRoots) {
    Path normalized = file.toAbsolutePath().normalize();
    // Prefer the outer explicitly selected folder if selections overlap.
    return selectedRoots.stream()
        .filter(normalized::startsWith)
        .min(java.util.Comparator.comparingInt(Path::getNameCount));
  }

  private static Origins origins(MediaSeriesGroup study) {
    return study.getTagValue(IMPORT_DIRECTORIES) instanceof Origins value
        ? value
        : new Origins(Set.of());
  }

  private record Origins(Set<Path> directories) {}
}
