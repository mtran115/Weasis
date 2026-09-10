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

import java.util.List;
import java.util.stream.Stream;

public record ReportPacket(
    CaseContext context,
    List<FindingEntry> findings,
    List<KeyImageCapture> keyImages,
    String reportInstructions) {
  public ReportPacket {
    findings = List.copyOf(findings);
    keyImages = keyImages.stream().map(KeyImageCapture::snapshot).toList();
    reportInstructions = ComposerText.clean(reportInstructions);
  }

  public ReportPacket(
      CaseContext context, List<FindingEntry> findings, List<KeyImageCapture> keyImages) {
    this(context, findings, keyImages, "");
  }

  public String findingsText() {
    return findings.stream()
        .map(FindingEntry::findingText)
        .reduce((a, b) -> a + "\n" + b)
        .orElse("");
  }

  public String impressionText() {
    return findings.stream()
        .filter(FindingEntry::includeInImpression)
        .map(FindingEntry::impressionText)
        .filter(ComposerText::hasText)
        .reduce((a, b) -> a + "\n" + b)
        .orElse("");
  }

  public String reportText() {
    return Stream.of(findingsText(), impressionText(), reportInstructions)
        .filter(ComposerText::hasText)
        .reduce((first, second) -> first + "\n" + second)
        .orElse("");
  }

  public boolean isEmpty() {
    return findings.isEmpty() && keyImages.isEmpty() && reportInstructions.isBlank();
  }
}
