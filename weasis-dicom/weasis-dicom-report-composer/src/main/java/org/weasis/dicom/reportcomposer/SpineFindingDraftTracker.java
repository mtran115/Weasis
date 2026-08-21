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

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.GeneratedFinding;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.Selection;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.SpineRegion;

final class SpineFindingDraftTracker {
  private final Map<String, EnumMap<SpineRegion, TrackedSelection>> trackedSelections =
      new LinkedHashMap<>();

  SyncResult synchronize(ReportDraft draft, Selection selection) {
    Objects.requireNonNull(draft);
    Objects.requireNonNull(selection);
    String draftKey = draft.context().draftKey();
    EnumMap<SpineRegion, TrackedSelection> draftSelections =
        trackedSelections.computeIfAbsent(draftKey, ignored -> new EnumMap<>(SpineRegion.class));
    TrackedSelection previous = draftSelections.get(selection.region());

    if (previous != null
        && previous.selection().equals(selection)
        && containsAll(draft, previous.findingIds())) {
      return new SyncResult(true, false, findLast(draft, previous.findingIds()));
    }

    if (previous != null) {
      previous.findingIds().forEach(draft::removeFinding);
      draftSelections.remove(selection.region());
    }

    List<GeneratedFinding> generated = SpineFindingBuilder.generate(selection);
    if (generated.isEmpty()) {
      removeEmptyDraft(draftKey, draftSelections);
      return new SyncResult(false, previous != null, null);
    }

    List<FindingEntry> entries =
        generated.stream().map(SpineFindingDraftTracker::createEntry).toList();
    entries.forEach(draft::addFinding);
    draftSelections.put(
        selection.region(),
        new TrackedSelection(selection, entries.stream().map(FindingEntry::id).toList()));
    return new SyncResult(true, true, entries.getLast());
  }

  void finalizeSelection(ReportDraft draft, SpineRegion region) {
    String draftKey = draft.context().draftKey();
    EnumMap<SpineRegion, TrackedSelection> draftSelections = trackedSelections.get(draftKey);
    if (draftSelections != null) {
      draftSelections.remove(region);
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

  private void removeEmptyDraft(
      String draftKey, EnumMap<SpineRegion, TrackedSelection> draftSelections) {
    if (draftSelections.isEmpty()) {
      trackedSelections.remove(draftKey);
    }
  }

  record SyncResult(boolean hasFindings, boolean changed, FindingEntry lastFinding) {}

  private record TrackedSelection(Selection selection, List<String> findingIds) {
    TrackedSelection {
      findingIds = List.copyOf(findingIds);
    }
  }
}
