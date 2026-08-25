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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.GeneratedFinding;

final class StructuredFindingDraftTracker<K, S> {
  private final Map<String, Map<K, TrackedSelection<S>>> trackedSelections = new LinkedHashMap<>();
  private final Function<S, K> keyExtractor;
  private final Function<S, List<GeneratedFinding>> findingGenerator;

  StructuredFindingDraftTracker(
      Function<S, K> keyExtractor, Function<S, List<GeneratedFinding>> findingGenerator) {
    this.keyExtractor = Objects.requireNonNull(keyExtractor);
    this.findingGenerator = Objects.requireNonNull(findingGenerator);
  }

  SyncResult synchronize(ReportDraft draft, S selection) {
    Objects.requireNonNull(draft);
    Objects.requireNonNull(selection);
    K selectionKey = Objects.requireNonNull(keyExtractor.apply(selection));
    String draftKey = draft.context().draftKey();
    Map<K, TrackedSelection<S>> draftSelections =
        trackedSelections.computeIfAbsent(draftKey, ignored -> new LinkedHashMap<>());
    TrackedSelection<S> previous = draftSelections.get(selectionKey);

    if (previous != null
        && previous.selection().equals(selection)
        && containsAll(draft, previous.findingIds())) {
      return new SyncResult(true, false, findLast(draft, previous.findingIds()));
    }

    if (previous != null) {
      previous.findingIds().forEach(draft::removeFinding);
      draftSelections.remove(selectionKey);
    }

    List<GeneratedFinding> generated = findingGenerator.apply(selection);
    if (generated.isEmpty()) {
      removeEmptyDraft(draftKey, draftSelections);
      return new SyncResult(false, previous != null, null);
    }

    List<FindingEntry> entries =
        generated.stream().map(StructuredFindingDraftTracker::createEntry).toList();
    entries.forEach(draft::addFinding);
    draftSelections.put(
        selectionKey,
        new TrackedSelection<>(selection, entries.stream().map(FindingEntry::id).toList()));
    return new SyncResult(true, true, entries.getLast());
  }

  void finalizeSelection(ReportDraft draft, K selectionKey) {
    String draftKey = draft.context().draftKey();
    Map<K, TrackedSelection<S>> draftSelections = trackedSelections.get(draftKey);
    if (draftSelections != null) {
      draftSelections.remove(selectionKey);
      removeEmptyDraft(draftKey, draftSelections);
    }
  }

  void finalizeDraft(ReportDraft draft) {
    trackedSelections.remove(draft.context().draftKey());
  }

  static FindingEntry createEntry(GeneratedFinding finding) {
    return FindingEntry.create(finding.findingText(), finding.impressionText(), false);
  }

  private static boolean containsAll(ReportDraft draft, List<String> findingIds) {
    return findingIds.stream()
        .allMatch(id -> draft.findings().stream().anyMatch(finding -> finding.id().equals(id)));
  }

  private static FindingEntry findLast(ReportDraft draft, List<String> findingIds) {
    String lastId = findingIds.getLast();
    return draft.findings().stream()
        .filter(finding -> finding.id().equals(lastId))
        .findFirst()
        .orElse(null);
  }

  private void removeEmptyDraft(String draftKey, Map<K, TrackedSelection<S>> draftSelections) {
    if (draftSelections.isEmpty()) {
      trackedSelections.remove(draftKey);
    }
  }

  record SyncResult(boolean hasFindings, boolean changed, FindingEntry lastFinding) {}

  private record TrackedSelection<S>(S selection, List<String> findingIds) {
    TrackedSelection {
      selection = Objects.requireNonNull(selection);
      findingIds = List.copyOf(findingIds);
    }
  }
}
