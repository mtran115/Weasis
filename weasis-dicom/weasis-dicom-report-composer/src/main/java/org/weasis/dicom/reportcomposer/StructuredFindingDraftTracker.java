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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.GeneratedFinding;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.KeyedFinding;

/**
 * Keeps generated findings in a draft in step with a structured form. Findings are matched by their
 * stable key, so rewording a finding keeps its ID, impression flag, key-image links and position.
 * Findings the radiologist edited by hand are never overwritten or removed.
 */
final class StructuredFindingDraftTracker<K, S> {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final Map<String, Map<K, TrackedSelection<S>>> trackedSelections = new LinkedHashMap<>();
  private final Function<S, K> keyExtractor;
  private final Function<S, List<KeyedFinding>> findingGenerator;

  StructuredFindingDraftTracker(
      Function<S, K> keyExtractor, Function<S, List<KeyedFinding>> findingGenerator) {
    this.keyExtractor = Objects.requireNonNull(keyExtractor);
    this.findingGenerator = Objects.requireNonNull(findingGenerator);
  }

  /** For generators without stable keys: each finding is identified by its text. */
  static <K, S> StructuredFindingDraftTracker<K, S> keyedByText(
      Function<S, K> keyExtractor, Function<S, List<GeneratedFinding>> findingGenerator) {
    return new StructuredFindingDraftTracker<>(
        keyExtractor,
        selection ->
            findingGenerator.apply(selection).stream()
                .map(finding -> new KeyedFinding(finding.findingText(), finding))
                .toList());
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
      return new SyncResult(last != null, false, last, List.of());
    }

    List<KeyedFinding> generated = uniqueKeys(findingGenerator.apply(selection));
    Map<String, FindingEntry> available = new LinkedHashMap<>();
    if (previous != null) {
      for (int index = 0; index < previous.findingIds().size(); index++) {
        FindingEntry entry = draft.finding(previous.findingIds().get(index)).orElse(null);
        if (entry != null) {
          String key = previous.findingKeys().get(index);
          available.putIfAbsent(key.isEmpty() ? "untracked:" + entry.id() : key, entry);
        }
      }
    }

    String groupId = previous == null ? selectionKey + ":" + UUID.randomUUID() : previous.groupId();
    JsonNode selectionEvidence = MAPPER.valueToTree(selection);
    List<FindingEntry> keptEdits = new ArrayList<>();
    List<FindingEntry> entries = new ArrayList<>();
    for (KeyedFinding keyed : generated) {
      GeneratedFinding finding = keyed.finding();
      FindingEntry existing = available.remove(keyed.key());
      if (existing != null && isHandEdited(existing)) {
        entries.add(existing);
        if (!existing.metadata().rawInput().equals(ComposerText.sentence(finding.findingText()))) {
          keptEdits.add(existing);
        }
        continue;
      }
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
    for (FindingEntry leftover : available.values()) {
      if (isHandEdited(leftover)) {
        keptEdits.add(leftover);
      } else {
        draft.removeFinding(leftover.id());
      }
    }
    placeInDraft(draft, entries);

    if (entries.isEmpty()) {
      draftSelections.remove(selectionKey);
      removeEmptyDraft(draftKey, draftSelections);
      return new SyncResult(false, previous != null, null, keptEdits);
    }
    draftSelections.put(
        selectionKey,
        new TrackedSelection<>(
            selection,
            entries.stream().map(FindingEntry::id).toList(),
            generated.stream().map(KeyedFinding::key).toList(),
            groupId));
    return new SyncResult(true, true, entries.getLast(), keptEdits);
  }

  /**
   * Updates entries in place and inserts new ones after their preceding sibling, so levels stay in
   * anatomical order while any manual reordering of existing findings is preserved.
   */
  private static void placeInDraft(ReportDraft draft, List<FindingEntry> entries) {
    for (int index = 0; index < entries.size(); index++) {
      FindingEntry entry = entries.get(index);
      if (draft.finding(entry.id()).isPresent()) {
        draft.replaceFinding(entry.id(), entry);
      } else if (index > 0) {
        draft.insertFindingAfter(entries.get(index - 1).id(), entry);
      } else {
        String nextPresent =
            entries.stream()
                .skip(1)
                .map(FindingEntry::id)
                .filter(id -> draft.finding(id).isPresent())
                .findFirst()
                .orElse(null);
        if (nextPresent == null) {
          draft.addFinding(entry);
        } else {
          draft.insertFindingBefore(nextPresent, entry);
        }
      }
    }
  }

  private static boolean isHandEdited(FindingEntry entry) {
    return !entry.metadata().structuredSelectionCurrent();
  }

  private static List<KeyedFinding> uniqueKeys(List<KeyedFinding> findings) {
    Map<String, Integer> seen = new HashMap<>();
    return findings.stream()
        .map(
            keyed -> {
              int count = seen.merge(keyed.key(), 1, Integer::sum);
              return count == 1
                  ? keyed
                  : new KeyedFinding(keyed.key() + "#" + count, keyed.finding());
            })
        .toList();
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
      track(
          draft,
          selection,
          group.getValue(),
          keysByText(draft, selection, group.getValue()),
          group.getKey());
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
            ArrayNode keys = selection.putArray("findingKeys");
            value.findingKeys().forEach(keys::add);
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
          List<String> savedKeys = new ArrayList<>();
          entry.path("findingKeys").forEach(key -> savedKeys.add(key.asText()));
          List<String> keys =
              savedKeys.size() == ids.size() ? savedKeys : keysByPosition(draft, selection, ids);
          track(draft, selection, ids, keys, entry.path("groupId").asText());
          return;
        }
      }
    }
    restoreSelection(draft, selection);
  }

  void finalizeDraft(ReportDraft draft) {
    trackedSelections.remove(draft.context().draftKey());
  }

  private void track(
      ReportDraft draft, S selection, List<String> ids, List<String> keys, String groupId) {
    trackedSelections
        .computeIfAbsent(draft.context().draftKey(), ignored -> new LinkedHashMap<>())
        .put(keyExtractor.apply(selection), new TrackedSelection<>(selection, ids, keys, groupId));
  }

  /** Saved IDs follow generation order, so older state without keys can recover them by index. */
  private List<String> keysByPosition(ReportDraft draft, S selection, List<String> ids) {
    List<KeyedFinding> generated = uniqueKeys(findingGenerator.apply(selection));
    if (generated.size() != ids.size()) {
      return keysByText(draft, selection, ids);
    }
    return generated.stream().map(KeyedFinding::key).toList();
  }

  /** Recovers keys for unedited entries; hand-edited ones stay unkeyed and are never replaced. */
  private List<String> keysByText(ReportDraft draft, S selection, List<String> ids) {
    Map<String, String> keyByText = new HashMap<>();
    uniqueKeys(findingGenerator.apply(selection))
        .forEach(
            keyed ->
                keyByText.putIfAbsent(
                    ComposerText.sentence(keyed.finding().findingText()), keyed.key()));
    return ids.stream()
        .map(
            id ->
                draft
                    .finding(id)
                    .filter(entry -> !isHandEdited(entry))
                    .map(entry -> keyByText.getOrDefault(entry.findingText(), ""))
                    .orElse(""))
        .toList();
  }

  static FindingEntry createEntry(GeneratedFinding finding) {
    return FindingEntry.create(finding.findingText(), finding.impressionText(), false);
  }

  private static FindingEntry findLast(ReportDraft draft, List<String> findingIds) {
    return findingIds.reversed().stream()
        .flatMap(id -> draft.finding(id).stream())
        .findFirst()
        .orElse(null);
  }

  private void removeEmptyDraft(String draftKey, Map<K, TrackedSelection<S>> draftSelections) {
    if (draftSelections.isEmpty()) {
      trackedSelections.remove(draftKey);
    }
  }

  /**
   * @param keptEdits hand-edited findings kept although the form now generates different wording,
   *     or no longer generates them at all
   */
  record SyncResult(
      boolean hasFindings,
      boolean changed,
      FindingEntry lastFinding,
      List<FindingEntry> keptEdits) {
    SyncResult {
      keptEdits = List.copyOf(keptEdits);
    }
  }

  private record TrackedSelection<S>(
      S selection, List<String> findingIds, List<String> findingKeys, String groupId) {
    TrackedSelection {
      selection = Objects.requireNonNull(selection);
      findingIds = List.copyOf(findingIds);
      findingKeys = List.copyOf(findingKeys);
      if (findingKeys.size() != findingIds.size()) {
        throw new IllegalArgumentException("Each tracked finding needs a key.");
      }
    }
  }
}
