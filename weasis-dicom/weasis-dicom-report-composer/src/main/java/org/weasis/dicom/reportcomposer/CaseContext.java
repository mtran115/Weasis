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

import java.util.Objects;

public record CaseContext(
    String studyInstanceUid,
    String patientName,
    String patientId,
    String patientBirthDate,
    String accessionNumber,
    String studyDate,
    String studyDescription) {

  public CaseContext {
    studyInstanceUid = ComposerText.clean(studyInstanceUid);
    patientName = ComposerText.clean(patientName);
    patientId = ComposerText.clean(patientId);
    patientBirthDate = ComposerText.clean(patientBirthDate);
    accessionNumber = ComposerText.clean(accessionNumber);
    studyDate = ComposerText.clean(studyDate);
    studyDescription = ComposerText.clean(studyDescription);
  }

  public static CaseContext empty() {
    return new CaseContext("", "", "", "", "", "", "MRI WRIST");
  }

  public boolean hasStudy() {
    return !studyInstanceUid.isBlank();
  }

  public String patientDisplayName() {
    String display = ComposerText.displayPersonName(patientName);
    return display.isBlank() ? "Unknown patient" : display;
  }

  public String examTitle() {
    return studyDescription.isBlank() ? "MRI WRIST" : studyDescription;
  }

  public String caseFolderStem() {
    String patient = ComposerText.fileSafeUpper(patientName, "UNKNOWN_PATIENT");
    String exam = ComposerText.fileSafeUpper(examTitle(), "MRI_WRIST");
    String date = ComposerText.fileSafeUpper(studyDate, "UNDATED");
    return patient + " - " + exam + " - " + date;
  }

  public String draftKey() {
    if (hasStudy()) {
      return studyInstanceUid;
    }
    return Objects.toString(patientName, "") + '|' + Objects.toString(accessionNumber, "");
  }
}
