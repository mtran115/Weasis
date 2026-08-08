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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ReportDraftTest {
  @Test
  void preservesFindingOrderAndBuildsSelectedImpression() {
    ReportDraft draft = new ReportDraft(context("1.2.3", "MRI WRIST LEFT"));
    FindingEntry ligament =
        new FindingEntry(
            "finding-1",
            "Tear of the lunotriquetral ligament",
            "Lunotriquetral ligament tear",
            true);
    FindingEntry edema = new FindingEntry("finding-2", "Mild dorsal soft-tissue edema", "", false);

    draft.addFinding(ligament);
    draft.addFinding(edema);
    draft.moveFinding(edema.id(), -1);

    ReportPacket packet = draft.snapshot();
    assertEquals(List.of(edema, ligament), packet.findings());
    assertEquals("Lunotriquetral ligament tear.", packet.impressionText());
  }

  @Test
  void acceptsMetadataRefreshOnlyForTheSameStudy() {
    ReportDraft draft = new ReportDraft(context("1.2.3", "MRI WRIST"));
    CaseContext refreshed = context("1.2.3", "MRI WRIST LEFT");

    draft.updateContext(refreshed);

    assertEquals(refreshed, draft.context());
    assertThrows(
        IllegalArgumentException.class,
        () -> draft.updateContext(context("9.8.7", "MRI WRIST RIGHT")));
  }

  private static CaseContext context(String studyUid, String description) {
    return new CaseContext(
        studyUid, "DOE^JANE", "MRN123", "19800101", "ACC456", "20260808", description);
  }
}
