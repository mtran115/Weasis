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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.GeneratedFinding;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.AlignmentFinding;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.OverviewFinding;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.Selection;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.SpineRegion;

class StructuredFindingProvenanceTest {
  @Test
  void retainsTheOriginalSelectionAndAdoptsItAfterRestoringADraft() {
    Selection selection =
        new Selection(
            SpineRegion.LUMBAR,
            AlignmentFinding.NONE,
            Map.of(OverviewFinding.SPONDYLOSIS, List.of("L4-5")),
            List.of());
    ReportDraft draft = draft();
    var original =
        new StructuredFindingDraftTracker<>(Selection::region, SpineFindingBuilder::generate);
    original.synchronize(draft, selection);
    FindingEntry entry = draft.findings().getFirst();
    assertEquals(new ObjectMapper().valueToTree(selection), entry.metadata().structuredSelection());
    assertTrue(entry.metadata().selectionType().endsWith("SpineFindingBuilder$Selection"));
    assertFalse(entry.metadata().groupId().isBlank());

    var restored =
        new StructuredFindingDraftTracker<>(Selection::region, SpineFindingBuilder::generate);
    restored.restoreSelection(draft, selection);
    assertFalse(restored.synchronize(draft, selection).changed());
    assertEquals(List.of(entry), draft.findings());
  }

  @Test
  void unchangedAnatomicalFindingsKeepIdsAcrossInsertionDeletionAndReordering() {
    var tracker =
        new StructuredFindingDraftTracker<String, TextSelection>(
            TextSelection::group,
            selection ->
                selection.texts().stream().map(text -> new GeneratedFinding(text, "")).toList());
    ReportDraft draft = draft();
    tracker.synchronize(
        draft, new TextSelection("lumbar", List.of("L3-4 finding", "L4-5 finding")));
    Map<String, String> originalIds = ids(draft);
    tracker.synchronize(
        draft,
        new TextSelection("lumbar", List.of("L4-5 finding", "L2-3 finding", "L3-4 finding")));
    assertEquals(originalIds.get("L3-4 finding."), ids(draft).get("L3-4 finding."));
    assertEquals(originalIds.get("L4-5 finding."), ids(draft).get("L4-5 finding."));
    String addedId = ids(draft).get("L2-3 finding.");
    tracker.synchronize(
        draft, new TextSelection("lumbar", List.of("L2-3 finding", "Changed L4-5 finding")));
    assertEquals(addedId, ids(draft).get("L2-3 finding."));
    assertNotEquals(originalIds.get("L4-5 finding."), ids(draft).get("Changed L4-5 finding."));
    assertFalse(draft.findings().stream().anyMatch(entry -> originalIds.containsValue(entry.id())));
  }

  @Test
  void unchangedSelectionHonorsManualEditsAndDeletionsAcrossRestarts() {
    TextSelection selection = new TextSelection("lumbar", List.of("L3-4 finding", "L4-5 finding"));
    var tracker = textTracker();
    ReportDraft draft = draft();
    tracker.synchronize(draft, selection);
    FindingEntry first = draft.findings().getFirst();
    FindingEntry last = draft.findings().getLast();
    draft.replaceFinding(first.id(), first.withText("Edited L3-4 finding", "", false));
    draft.removeFinding(last.id());
    assertFalse(tracker.synchronize(draft, selection).changed());
    assertEquals(1, draft.findings().size());
    assertEquals("Edited L3-4 finding.", draft.findings().getFirst().findingText());

    var restored = textTracker();
    restored.restoreSelection(draft, selection);
    assertFalse(restored.synchronize(draft, selection).changed());
    JsonNode state = restored.captureState(draft);
    draft.removeFinding(first.id());
    var restoredEmpty = textTracker();
    restoredEmpty.restoreSelection(draft, selection, state);
    assertFalse(restoredEmpty.synchronize(draft, selection).changed());
    assertEquals(0, draft.findings().size());
    assertTrue(
        restoredEmpty
            .synchronize(draft, new TextSelection("lumbar", List.of("Actually changed selection")))
            .changed());
    assertEquals(1, draft.findings().size());
  }

  private static StructuredFindingDraftTracker<String, TextSelection> textTracker() {
    return new StructuredFindingDraftTracker<>(
        TextSelection::group,
        selection ->
            selection.texts().stream().map(text -> new GeneratedFinding(text, "")).toList());
  }

  private static Map<String, String> ids(ReportDraft draft) {
    return draft.findings().stream()
        .collect(Collectors.toMap(FindingEntry::findingText, FindingEntry::id));
  }

  private static ReportDraft draft() {
    return new ReportDraft(new CaseContext("test-study", "", "", "", "", "", "MRI LUMBAR SPINE"));
  }

  record TextSelection(String group, List<String> texts) {}
}
