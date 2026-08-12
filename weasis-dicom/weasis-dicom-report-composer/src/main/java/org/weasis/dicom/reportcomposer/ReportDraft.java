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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ReportDraft {
  private CaseContext context;
  private String reportInstructions = "";
  private final List<FindingEntry> findings = new ArrayList<>();
  private final List<KeyImageCapture> keyImages = new ArrayList<>();

  public ReportDraft(CaseContext context) {
    this.context = Objects.requireNonNull(context);
  }

  public CaseContext context() {
    return context;
  }

  public void updateContext(CaseContext context) {
    if (!this.context.draftKey().equals(context.draftKey())) {
      throw new IllegalArgumentException("Cannot replace a draft with a different study.");
    }
    this.context = context;
  }

  public List<FindingEntry> findings() {
    return Collections.unmodifiableList(findings);
  }

  public List<KeyImageCapture> keyImages() {
    return Collections.unmodifiableList(keyImages);
  }

  public String reportInstructions() {
    return reportInstructions;
  }

  public void setReportInstructions(String reportInstructions) {
    this.reportInstructions = ComposerText.clean(reportInstructions);
  }

  public void addFinding(FindingEntry finding) {
    findings.add(Objects.requireNonNull(finding));
  }

  public void replaceFinding(String id, FindingEntry replacement) {
    int index = indexOfFinding(id);
    if (index >= 0) {
      findings.set(index, Objects.requireNonNull(replacement));
    }
  }

  public void removeFinding(String id) {
    findings.removeIf(finding -> finding.id().equals(id));
  }

  public void moveFinding(String id, int direction) {
    int index = indexOfFinding(id);
    int destination = index + Integer.signum(direction);
    if (index >= 0 && destination >= 0 && destination < findings.size()) {
      Collections.swap(findings, index, destination);
    }
  }

  public void addKeyImage(KeyImageCapture keyImage) {
    keyImages.add(Objects.requireNonNull(keyImage));
  }

  public void removeKeyImage(String id) {
    keyImages.removeIf(keyImage -> keyImage.id().equals(id));
  }

  public ReportPacket snapshot() {
    return new ReportPacket(context, findings, keyImages, reportInstructions);
  }

  private int indexOfFinding(String id) {
    for (int index = 0; index < findings.size(); index++) {
      if (findings.get(index).id().equals(id)) {
        return index;
      }
    }
    return -1;
  }
}
