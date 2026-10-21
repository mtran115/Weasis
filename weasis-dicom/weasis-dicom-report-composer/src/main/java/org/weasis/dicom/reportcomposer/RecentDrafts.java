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

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The drafts kept in memory: the most recently opened few. Each draft holds its key images, so
 * keeping every study of a long session ran out of heap; older drafts reload from the local store.
 */
final class RecentDrafts {
  private final int limit;
  private final Map<String, ReportDraft> drafts = new LinkedHashMap<>(16, 0.75f, true);
  private final Set<String> memoryOnly = new HashSet<>();

  RecentDrafts(int limit) {
    if (limit < 1) throw new IllegalArgumentException("Keep at least one draft.");
    this.limit = limit;
  }

  /** The draft for a study, which becomes the most recently opened. */
  Optional<ReportDraft> get(String key) {
    return Optional.ofNullable(drafts.get(key));
  }

  /**
   * Adds a study's draft as the most recently opened and drops the oldest beyond the limit.
   *
   * @param memoryOnly whether the draft cannot be saved locally, so memory holds its only copy
   */
  void put(String key, ReportDraft draft, boolean memoryOnly) {
    drafts.put(key, draft);
    if (memoryOnly) this.memoryOnly.add(key);
    var oldestFirst = drafts.entrySet().iterator();
    while (drafts.size() > limit && oldestFirst.hasNext()) {
      var entry = oldestFirst.next();
      if (entry.getValue() != draft && !this.memoryOnly.contains(entry.getKey())) {
        oldestFirst.remove();
      }
    }
  }

  int size() {
    return drafts.size();
  }
}
