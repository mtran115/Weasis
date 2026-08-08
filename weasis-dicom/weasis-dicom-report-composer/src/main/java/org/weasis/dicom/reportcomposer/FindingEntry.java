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

import java.util.UUID;

public record FindingEntry(
    String id, String findingText, String impressionText, boolean includeInImpression) {

  public FindingEntry {
    id = ComposerText.clean(id);
    findingText = ComposerText.sentence(findingText);
    impressionText = ComposerText.sentence(impressionText);
    if (id.isBlank()) {
      throw new IllegalArgumentException("A finding ID is required.");
    }
    if (findingText.isBlank()) {
      throw new IllegalArgumentException("Finding text cannot be blank.");
    }
    if (includeInImpression && impressionText.isBlank()) {
      impressionText = findingText;
    }
  }

  public static FindingEntry create(
      String findingText, String impressionText, boolean includeInImpression) {
    return new FindingEntry(
        UUID.randomUUID().toString(), findingText, impressionText, includeInImpression);
  }

  public FindingEntry withText(
      String newFindingText, String newImpressionText, boolean newIncludeInImpression) {
    return new FindingEntry(id, newFindingText, newImpressionText, newIncludeInImpression);
  }

  @Override
  public String toString() {
    return findingText;
  }
}
