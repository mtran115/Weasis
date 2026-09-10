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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.GeneratedFinding;

final class StructuredFindingDraftTracker<K, S> {
  private static final ObjectMapper MAPPER = new ObjectMapper();
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

    if (previous != null && previous.selection().equals(selection)) {
      FindingEntry last = findLast(draft, previous.findingIds());
      return new SyncResult(last != null, false, last);
    }

    List<GeneratedFinding> generated = findingGenerator.apply(selection);
    if (generated.isEmpty()) {
      if (previous != null) {
        previous.findingIds().forEach(draft::removeFinding);
        draftSelections.remove(selectionKey);
      }
      removeEmptyDraft(draftKey, draftSelections);
      return new SyncResult(false, previous != null, null);
    }

    String groupId = previous == null ? selectionKey + ":" + UUID.randomUUID() : previous.groupId();
    JsonNode selectionEvidence = MAPPER.valueToTree(selection);
    List<FindingEntry> available = new ArrayList<>();
    if (previous != null) {
      previous.findingIds().stream()
          .flatMap(id -> draft.findings().stream().filter(entry -> entry.id().equals(id)))
          .forEach(available::add);
    }
    // Reserve exact matches first, so inserting a new sentence does not steal another image's ID.
    List<FindingEntry> matches = new ArrayList<>();
    for (GeneratedFinding finding : generated) {
      FindingEntry match =
          available.stream()
              .filter(
                  entry ->
                      entry.findingText().equals(ComposerText.sentence(finding.findingText()))
                          && entry
                              .impressionText()
                              .equals(ComposerText.sentence(finding.impressionText())))
              .findFirst()
              .orElse(null);
      matches.add(match);
      available.remove(match);
    }
    List<FindingEntry> entries = new ArrayList<>();
    for (int index = 0; index < generated.size(); index++) {
      GeneratedFinding finding = generated.get(index);
      FindingEntry existing = matches.get(index);
      entries.add(
          new FindingEntry(
              existing == null ? UUID.randomUUID().toString() : existing.id(),
              finding.findingText(),
              finding.impressionText(),
              existing != null && existing.includeInImpression(),
              FindingMetadata.structured(
                  finding.findingText(),
                  selection.getClass().getName(),
                  groupId,
                  selectionEvidence,
                  generated.size())));
    }
    available.forEach(entry -> draft.removeFinding(entry.id()));
    for (FindingEntry entry : entries) {
      if (draft.findings().stream().anyMatch(existing -> existing.id().equals(entry.id()))) {
        draft.replaceFinding(entry.id(), entry);
      } else {
        draft.addFinding(entry);
      }
    }
    draftSelections.put(
        selectionKey,
        new TrackedSelection<>(
            selection, entries.stream().map(FindingEntry::id).toList(), groupId));
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

  void restoreSelection(ReportDraft draft, S selection) {
    Objects.requireNonNull(draft);
    Objects.requireNonNull(selection);
    JsonNode evidence = MAPPER.valueToTree(selection);
    Map<String, List<String>> matchingGroups = new LinkedHashMap<>();
    for (FindingEntry entry : draft.findings()) {
      FindingMetadata metadata = entry.metadata();
      if (metadata.selectionType().equals(selection.getClass().getName())
          && evidence.equals(metadata.structuredSelection())
          && !metadata.groupId().isBlank()) {
        matchingGroups
            .computeIfAbsent(metadata.groupId(), ignored -> new ArrayList<>())
            .add(entry.id());
      }
    }
    if (matchingGroups.size() == 1) {
      Map.Entry<String, List<String>> group = matchingGroups.entrySet().iterator().next();
      trackedSelections
          .computeIfAbsent(draft.context().draftKey(), ignored -> new LinkedHashMap<>())
          .put(
              keyExtractor.apply(selection),
              new TrackedSelection<>(selection, group.getValue(), group.getKey()));
    }
  }

  JsonNode captureState(ReportDraft draft) {
    ObjectNode state = MAPPER.createObjectNode();
    state.put("version", 1);
    ArrayNode selections = state.putArray("selections");
    Map<K, TrackedSelection<S>> tracked = trackedSelections.get(draft.context().draftKey());
    if (tracked != null) {
      tracked.forEach(
          (key, value) -> {
            ObjectNode selection = selections.addObject();
            selection.put("selectionType", value.selection().getClass().getName());
            selection.set("selection", MAPPER.valueToTree(value.selection()));
            selection.put("groupId", value.groupId());
            ArrayNode ids = selection.putArray("findingIds");
            value.findingIds().forEach(ids::add);
          });
    }
    return state;
  }

  void restoreSelection(ReportDraft draft, S selection, JsonNode state) {
    if (state != null && state.path("version").asInt() == 1) {
      JsonNode expected = MAPPER.valueToTree(selection);
      for (JsonNode entry : state.path("selections")) {
        if (entry.path("selectionType").asText().equals(selection.getClass().getName())
            && expected.equals(entry.path("selection"))
            && !entry.path("groupId").asText().isBlank()
            && entry.path("findingIds").isArray()) {
          List<String> ids = new ArrayList<>();
          entry.path("findingIds").forEach(id -> ids.add(id.asText()));
          trackedSelections
              .computeIfAbsent(draft.context().draftKey(), ignored -> new LinkedHashMap<>())
              .put(
                  keyExtractor.apply(selection),
                  new TrackedSelection<>(selection, ids, entry.path("groupId").asText()));
          return;
        }
      }
    }
    restoreSelection(draft, selection);
  }

  void finalizeDraft(ReportDraft draft) {
    trackedSelections.remove(draft.context().draftKey());
  }

  static FindingEntry createEntry(GeneratedFinding finding) {
    return FindingEntry.create(finding.findingText(), finding.impressionText(), false);
  }

  private static FindingEntry findLast(ReportDraft draft, List<String> findingIds) {
    for (String id : findingIds.reversed()) {
      FindingEntry entry =
          draft.findings().stream()
              .filter(finding -> finding.id().equals(id))
              .findFirst()
              .orElse(null);
      if (entry != null) {
        return entry;
      }
    }
    return null;
  }

  private void removeEmptyDraft(String draftKey, Map<K, TrackedSelection<S>> draftSelections) {
    if (draftSelections.isEmpty()) {
      trackedSelections.remove(draftKey);
    }
  }

  record SyncResult(boolean hasFindings, boolean changed, FindingEntry lastFinding) {}

  private record TrackedSelection<S>(S selection, List<String> findingIds, String groupId) {
    TrackedSelection {
      selection = Objects.requireNonNull(selection);
      findingIds = List.copyOf(findingIds);
    }
  }
}
