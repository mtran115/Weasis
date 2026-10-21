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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RecentDraftsTest {
  @Test
  void dropsTheLeastRecentlyOpenedDraftBeyondTheLimit() {
    RecentDrafts drafts = new RecentDrafts(2);
    ReportDraft first = draft("1");
    drafts.put("1", first, false);
    drafts.put("2", draft("2"), false);
    // Reopening the first study makes the second the oldest.
    assertSame(first, drafts.get("1").orElseThrow());

    drafts.put("3", draft("3"), false);

    assertEquals(2, drafts.size());
    assertTrue(drafts.get("2").isEmpty());
    assertSame(first, drafts.get("1").orElseThrow());
  }

  @Test
  void keepsADraftThatOnlyExistsInMemory() {
    RecentDrafts drafts = new RecentDrafts(1);
    ReportDraft unsaved = draft("1");
    drafts.put("1", unsaved, true);
    ReportDraft current = draft("2");
    drafts.put("2", current, false);
    drafts.put("3", draft("3"), false);

    assertSame(unsaved, drafts.get("1").orElseThrow());
    assertTrue(drafts.get("2").isEmpty());
    assertTrue(drafts.get("3").isPresent());
  }

  @Test
  void needsRoomForTheOpenDraft() {
    assertThrows(IllegalArgumentException.class, () -> new RecentDrafts(0));
  }

  private static ReportDraft draft(String studyUid) {
    return new ReportDraft(
        new CaseContext(studyUid, "DOE^JANE", "MRN123", "19800101", "ACC456", "20260808", "MRI"));
  }
}
