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

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

/** Source evidence is retained even when the displayed finding is edited. */
public record FindingMetadata(
    String source,
    String rawInput,
    String currentInput,
    String selectionType,
    String groupId,
    JsonNode structuredSelection,
    boolean structuredSelectionCurrent,
    int groupFindingCount) {

  public FindingMetadata {
    source = Objects.requireNonNullElse(source, "free_text");
    rawInput = Objects.requireNonNullElse(rawInput, "");
    currentInput = Objects.requireNonNullElse(currentInput, rawInput);
    selectionType = Objects.requireNonNullElse(selectionType, "");
    groupId = Objects.requireNonNullElse(groupId, "");
    structuredSelection =
        structuredSelection == null
                || structuredSelection.isNull()
                || structuredSelection.isMissingNode()
            ? null
            : structuredSelection.deepCopy();
    if (structuredSelection == null) {
      structuredSelectionCurrent = false;
    }
    groupFindingCount = Math.max(0, groupFindingCount);
  }

  public FindingMetadata(
      String source,
      String rawInput,
      String currentInput,
      String selectionType,
      String groupId,
      JsonNode structuredSelection,
      boolean structuredSelectionCurrent) {
    this(
        source,
        rawInput,
        currentInput,
        selectionType,
        groupId,
        structuredSelection,
        structuredSelectionCurrent,
        0);
  }

  public static FindingMetadata freeText(String input) {
    return new FindingMetadata("free_text", input, input, "", "", null, false);
  }

  public static FindingMetadata structured(
      String input, String selectionType, String groupId, JsonNode selection) {
    return structured(input, selectionType, groupId, selection, 1);
  }

  public static FindingMetadata structured(
      String input,
      String selectionType,
      String groupId,
      JsonNode selection,
      int groupFindingCount) {
    return new FindingMetadata(
        "structured", input, input, selectionType, groupId, selection, true, groupFindingCount);
  }

  @Override
  public JsonNode structuredSelection() {
    return structuredSelection == null ? null : structuredSelection.deepCopy();
  }

  FindingMetadata edited(String input, boolean textChanged) {
    return new FindingMetadata(
        source,
        rawInput,
        input,
        selectionType,
        groupId,
        structuredSelection,
        structuredSelectionCurrent && !textChanged,
        groupFindingCount);
  }
}
