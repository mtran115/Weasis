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

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.AlignmentFinding;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.OverviewFinding;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.Selection;
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

  private static Selection selection(OverviewFinding finding) {
    return new Selection(
        SpineRegion.LUMBAR, AlignmentFinding.NONE, Map.of(finding, List.of()), List.of());
  }

  private static StructuredFindingDraftTracker<SpineRegion, Selection> tracker() {
    return new StructuredFindingDraftTracker<>(Selection::region, SpineFindingBuilder::generate);
  }

  private static CaseContext context() {
    return new CaseContext(
        "1.2.3", "DOE^JANE", "MRN123", "19800101", "ACC456", "20260820", "MRI LUMBAR SPINE");
  }
}
