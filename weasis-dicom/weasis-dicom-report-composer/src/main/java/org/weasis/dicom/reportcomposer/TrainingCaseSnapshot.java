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
import com.fasterxml.jackson.databind.node.NullNode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** An immutable handoff from the Swing event thread to the local recorder. */
public record TrainingCaseSnapshot(
    String revisionId,
    String capturedAt,
    ReportPacket packet,
    String exam,
    JsonNode editorState,
    List<StudySourceInventory.SourceImageFile> sourceInventory,
    String sourceInventoryScope) {
  public TrainingCaseSnapshot {
    Objects.requireNonNull(revisionId);
    Objects.requireNonNull(capturedAt);
    Objects.requireNonNull(packet);
    exam = ComposerText.clean(exam);
    editorState = editorState == null ? NullNode.instance : editorState.deepCopy();
    sourceInventory = sourceInventory == null ? List.of() : List.copyOf(sourceInventory);
    sourceInventoryScope = ComposerText.clean(sourceInventoryScope);
  }

  public static TrainingCaseSnapshot capture(ReportDraft draft, String exam, JsonNode editorState) {
    return new TrainingCaseSnapshot(
        UUID.randomUUID().toString(),
        Instant.now().toString(),
        draft.snapshot(),
        exam,
        editorState,
        null,
        "not_collected");
  }

  public TrainingCaseSnapshot withSources(List<StudySourceInventory.SourceImageFile> sources) {
    return withSources(sources, "available_loaded_study");
  }

  public TrainingCaseSnapshot withSources(
      List<StudySourceInventory.SourceImageFile> sources, String scope) {
    return new TrainingCaseSnapshot(
        revisionId, capturedAt, packet, exam, editorState, sources, scope);
  }

  public String studyKey() {
    return packet.context().draftKey();
  }

  @Override
  public JsonNode editorState() {
    return editorState.deepCopy();
  }
}
