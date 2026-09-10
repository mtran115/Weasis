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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class FindingMetadataTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void keepsExactRawInputSeparateFromFormattedProseAndLaterEdits() {
    FindingEntry finding = FindingEntry.create("  L4-5: mild canal stenosis  ", "", false);
    FindingEntry edited = finding.withText("L4-5: no canal stenosis", "", false);
    assertEquals("  L4-5: mild canal stenosis  ", finding.rawInput());
    assertEquals("L4-5: mild canal stenosis.", finding.findingText());
    assertEquals(finding.rawInput(), edited.rawInput());
    assertEquals("L4-5: no canal stenosis", edited.metadata().currentInput());
    assertEquals(finding.id(), edited.id());
  }

  @Test
  void copiesStructuredEvidenceAndInvalidatesItsInterpretationAfterAnEdit() {
    ObjectNode selection = MAPPER.createObjectNode().put("severity", "MILD");
    FindingEntry finding =
        new FindingEntry(
            "id",
            "Mild stenosis",
            "",
            false,
            FindingMetadata.structured("Mild stenosis", "Selection", "group", selection));
    selection.put("severity", "SEVERE");
    ((ObjectNode) finding.metadata().structuredSelection()).put("severity", "MODERATE");
    assertEquals("MILD", finding.metadata().structuredSelection().path("severity").asText());
    assertTrue(finding.metadata().structuredSelectionCurrent());
    FindingEntry edited = finding.withText("No stenosis", "", false);
    assertFalse(edited.metadata().structuredSelectionCurrent());
    assertEquals(finding.metadata().structuredSelection(), edited.metadata().structuredSelection());
    assertEquals("group", edited.metadata().groupId());
  }

  @Test
  void roundTripsMetadataAndReadsLegacyFourFieldFindings() throws Exception {
    FindingEntry finding =
        new FindingEntry(
            "id",
            "Mild stenosis",
            "",
            false,
            FindingMetadata.structured(
                "Mild stenosis",
                "Selection",
                "group",
                MAPPER.createObjectNode().put("severity", "MILD")));
    assertEquals(finding, MAPPER.readValue(MAPPER.writeValueAsBytes(finding), FindingEntry.class));
    FindingEntry legacy =
        MAPPER.readValue(
            "{\"id\":\"old\",\"findingText\":\"Old finding\",\"impressionText\":\"\",\"includeInImpression\":false}",
            FindingEntry.class);
    assertEquals("Old finding.", legacy.findingText());
    assertEquals("Old finding", legacy.rawInput());
    assertEquals("free_text", legacy.metadata().source());
  }

  @Test
  void freeTextRoundTripsWithoutAcquiringNullStructuredEvidence() throws Exception {
    FindingEntry finding = FindingEntry.create("T2 hyperint lesion L4", "", false);
    FindingEntry restored = MAPPER.readValue(MAPPER.writeValueAsBytes(finding), FindingEntry.class);
    assertEquals(finding, restored);
    assertNull(restored.metadata().structuredSelection());
    assertFalse(restored.metadata().structuredSelectionCurrent());
    FindingMetadata nullSelection =
        MAPPER.readValue(
            "{\"source\":\"free_text\",\"rawInput\":\"text\",\"structuredSelection\":null,"
                + "\"structuredSelectionCurrent\":true}",
            FindingMetadata.class);
    assertNull(nullSelection.structuredSelection());
    assertFalse(nullSelection.structuredSelectionCurrent());
  }
}
