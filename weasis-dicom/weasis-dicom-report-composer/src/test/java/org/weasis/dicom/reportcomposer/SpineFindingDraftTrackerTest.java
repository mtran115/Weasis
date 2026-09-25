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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.AlignmentFinding;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.Laterality;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.LevelSelection;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.MigrationDirection;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.OverviewFinding;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.Selection;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.Severity;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.SpineRegion;

class SpineFindingDraftTrackerTest {
  @Test
  void keepsAnUnchangedSelectionAndReplacesItAfterTheFormChanges() {
    ReportDraft draft = new ReportDraft(context());
    StructuredFindingDraftTracker<SpineRegion, Selection> tracker = tracker();
    Selection spondylosis = selection(OverviewFinding.SPONDYLOSIS);

    var first = tracker.synchronize(draft, spondylosis);
    String firstId = draft.findings().getFirst().id();
    var unchanged = tracker.synchronize(draft, spondylosis);
    var replaced = tracker.synchronize(draft, selection(OverviewFinding.DISC_DEGENERATION));

    assertAll(
        () -> assertTrue(first.hasFindings()),
        () -> assertTrue(first.changed()),
        () -> assertFalse(unchanged.changed()),
        () -> assertTrue(replaced.changed()),
        () -> assertEquals(1, draft.findings().size()),
        () -> assertNotEquals(firstId, draft.findings().getFirst().id()),
        () -> assertEquals("Lumbar disc degeneration.", draft.findings().getFirst().findingText()));
  }

  @Test
  void removesUncheckedPendingFindingsButPreservesFinalizedFindings() {
    ReportDraft draft = new ReportDraft(context());
    StructuredFindingDraftTracker<SpineRegion, Selection> tracker = tracker();
    Selection selected = selection(OverviewFinding.SPONDYLOSIS);
    Selection empty = new Selection(SpineRegion.LUMBAR, AlignmentFinding.NONE, Map.of(), List.of());

    tracker.synchronize(draft, selected);
    var removed = tracker.synchronize(draft, empty);
    tracker.synchronize(draft, selected);
    tracker.finalizeDraft(draft);
    var preserved = tracker.synchronize(draft, empty);

    assertAll(
        () -> assertFalse(removed.hasFindings()),
        () -> assertTrue(removed.changed()),
        () -> assertFalse(preserved.changed()),
        () -> assertEquals(1, draft.findings().size()),
        () -> assertEquals("Lumbar spondylosis.", draft.findings().getFirst().findingText()));
  }

  @Test
  void handEditedFindingSurvivesFormChangesAtOtherLevels() {
    ReportDraft draft = new ReportDraft(context());
    StructuredFindingDraftTracker<SpineRegion, Selection> tracker = tracker();
    tracker.synchronize(draft, levels(level("L3-4", Severity.MILD), level("L5-S1", Severity.MILD)));
    FindingEntry l34 = draft.findings().getFirst();
    draft.replaceFinding(
        l34.id(),
        l34.withText("L3-4: Mild RIGHT neural foraminal stenosis.", l34.impressionText(), false));

    var result =
        tracker.synchronize(
            draft,
            levels(
                level("L3-4", Severity.MILD),
                level("L4-5", Severity.MODERATE),
                level("L5-S1", Severity.MILD)));

    assertAll(
        () -> assertEquals(l34.id(), draft.findings().getFirst().id()),
        () ->
            assertEquals(
                "L3-4: Mild RIGHT neural foraminal stenosis.",
                draft.findings().getFirst().findingText()),
        () -> assertTrue(result.keptEdits().isEmpty()));
  }

  @Test
  void handEditedFindingIsKeptAndReportedWhenItsOwnLevelChangesOrIsUnchecked() {
    ReportDraft draft = new ReportDraft(context());
    StructuredFindingDraftTracker<SpineRegion, Selection> tracker = tracker();
    tracker.synchronize(draft, levels(level("L3-4", Severity.MILD), level("L5-S1", Severity.MILD)));
    FindingEntry l34 = draft.findings().getFirst();
    draft.replaceFinding(l34.id(), l34.withText("L3-4: Edited wording.", "", false));

    var changed =
        tracker.synchronize(
            draft, levels(level("L3-4", Severity.SEVERE), level("L5-S1", Severity.MILD)));
    var unchecked = tracker.synchronize(draft, levels(level("L5-S1", Severity.MILD)));

    assertAll(
        () -> assertEquals(List.of("L3-4: Edited wording."), texts(changed.keptEdits())),
        () -> assertEquals(List.of("L3-4: Edited wording."), texts(unchecked.keptEdits())),
        () ->
            assertEquals(
                List.of("L3-4: Edited wording.", "L5-S1: Mild left neural foraminal stenosis."),
                texts(draft.findings())));
  }

  @Test
  void newAndChangedLevelsStayInAnatomicalOrder() {
    ReportDraft draft = new ReportDraft(context());
    StructuredFindingDraftTracker<SpineRegion, Selection> tracker = tracker();
    tracker.synchronize(draft, levels(level("L3-4", Severity.MILD), level("L5-S1", Severity.MILD)));
    tracker.synchronize(
        draft,
        levels(
            level("L3-4", Severity.SEVERE),
            level("L4-5", Severity.MILD),
            level("L5-S1", Severity.MILD)));

    assertEquals(List.of("L3-4", "L4-5", "L5-S1"), levelOrder(draft));
  }

  @Test
  void rewordedLevelKeepsItsIdAndImpressionFlag() {
    ReportDraft draft = new ReportDraft(context());
    StructuredFindingDraftTracker<SpineRegion, Selection> tracker = tracker();
    tracker.synchronize(draft, levels(level("L5-S1", Severity.MILD)));
    FindingEntry original = draft.findings().getFirst();
    draft.replaceFinding(
        original.id(), original.withText(original.findingText(), original.impressionText(), true));

    tracker.synchronize(draft, levels(level("L5-S1", Severity.SEVERE)));
    FindingEntry reworded = draft.findings().getFirst();

    assertAll(
        () -> assertEquals(original.id(), reworded.id()),
        () -> assertTrue(reworded.includeInImpression()),
        () ->
            assertEquals("L5-S1: Severe left neural foraminal stenosis.", reworded.findingText()));
  }

  @Test
  void manualReorderingIsPreservedWhenALevelIsAdded() {
    ReportDraft draft = new ReportDraft(context());
    StructuredFindingDraftTracker<SpineRegion, Selection> tracker = tracker();
    tracker.synchronize(draft, levels(level("L3-4", Severity.MILD), level("L5-S1", Severity.MILD)));
    draft.moveFinding(draft.findings().get(1).id(), -1);

    tracker.synchronize(
        draft,
        levels(
            level("L3-4", Severity.MILD),
            level("L4-5", Severity.MILD),
            level("L5-S1", Severity.MILD)));

    assertEquals(List.of("L5-S1", "L3-4", "L4-5"), levelOrder(draft));
  }

  @Test
  void restoredStateWithoutKeysStillProtectsHandEdits() {
    ReportDraft draft = new ReportDraft(context());
    StructuredFindingDraftTracker<SpineRegion, Selection> tracker = tracker();
    Selection original = levels(level("L3-4", Severity.MILD), level("L5-S1", Severity.MILD));
    tracker.synchronize(draft, original);
    FindingEntry l34 = draft.findings().getFirst();
    draft.replaceFinding(l34.id(), l34.withText("L3-4: Edited wording.", "", false));
    ObjectNode legacyState = (ObjectNode) tracker.captureState(draft);
    legacyState
        .withArray("selections")
        .forEach(entry -> ((ObjectNode) entry).remove("findingKeys"));

    StructuredFindingDraftTracker<SpineRegion, Selection> restored = tracker();
    restored.restoreSelection(draft, original, legacyState);
    restored.synchronize(
        draft,
        levels(
            level("L3-4", Severity.MILD),
            level("L4-5", Severity.MILD),
            level("L5-S1", Severity.MILD)));

    assertEquals(
        List.of(
            "L3-4: Edited wording.",
            "L4-5: Mild left neural foraminal stenosis.",
            "L5-S1: Mild left neural foraminal stenosis."),
        texts(draft.findings()));
  }

  private static List<String> levelOrder(ReportDraft draft) {
    return draft.findings().stream().map(entry -> entry.findingText().split(":")[0]).toList();
  }

  private static List<String> texts(List<FindingEntry> entries) {
    return entries.stream().map(FindingEntry::findingText).toList();
  }

  private static Selection levels(LevelSelection... levels) {
    return new Selection(SpineRegion.LUMBAR, AlignmentFinding.NONE, Map.of(), List.of(levels));
  }

  private static LevelSelection level(String level, Severity leftForaminal) {
    return new LevelSelection(
        level,
        false,
        List.of(),
        List.of(),
        false,
        false,
        MigrationDirection.NONE,
        "",
        "",
        Laterality.NONE,
        Laterality.NONE,
        Severity.NONE,
        leftForaminal,
        Severity.NONE);
  }

  private static Selection selection(OverviewFinding finding) {
    return new Selection(
        SpineRegion.LUMBAR, AlignmentFinding.NONE, Map.of(finding, List.of()), List.of());
  }

  private static StructuredFindingDraftTracker<SpineRegion, Selection> tracker() {
    return new StructuredFindingDraftTracker<>(
        Selection::region, SpineFindingBuilder::generateKeyed);
  }

  private static CaseContext context() {
    return new CaseContext(
        "1.2.3", "DOE^JANE", "MRN123", "19800101", "ACC456", "20260820", "MRI LUMBAR SPINE");
  }
}
