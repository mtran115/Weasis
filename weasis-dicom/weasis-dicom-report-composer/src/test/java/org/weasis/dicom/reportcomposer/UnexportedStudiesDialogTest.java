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

import java.util.List;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.reportcomposer.TrainingCaseStore.ExportStatus;
import org.weasis.dicom.reportcomposer.UnexportedStudiesDialog.Row;

class UnexportedStudiesDialogTest {
  private static Row row(String uid, ExportStatus status) {
    return new Row(new CaseContext(uid, "DOE^JANE", "MRN", "", "ACC-" + uid, "", "MRI"), status);
  }

  private static final List<Row> ROWS =
      List.of(
          row("a", ExportStatus.EXPORTED),
          row("b", ExportStatus.NOT_STARTED),
          row("c", ExportStatus.DRAFT),
          row("d", ExportStatus.CHANGED_SINCE_EXPORT),
          row("e", ExportStatus.EXPORTED));

  @Test
  void likeliestMissedReadsComeFirstAndExportedStudiesAreHiddenByDefault() {
    assertEquals(
        List.of("c", "d", "b"),
        UnexportedStudiesDialog.visibleRows(ROWS, false).stream()
            .map(row -> row.study().studyInstanceUid())
            .toList());
    assertEquals(5, UnexportedStudiesDialog.visibleRows(ROWS, true).size());
  }

  @Test
  void summaryCountsEverythingNotExported() {
    assertEquals("3 of 5 loaded studies not exported", UnexportedStudiesDialog.summary(ROWS));
    assertEquals(
        "All 2 loaded studies have been exported.",
        UnexportedStudiesDialog.summary(List.of(ROWS.get(0), ROWS.get(4))));
  }
}
