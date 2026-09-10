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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.AlignmentFinding;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.Laterality;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.LevelSelection;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.Selection;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.Severity;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.SpineRegion;

class LumbarTrainingLabelsTest {
  @Test
  void completedBlankLumbarUsesTheExplicitlyRecordedReportingConvention() {
    JsonNode labels = summarize(true);
    assertEquals("positive_only_reportable_v1", labels.path("reportingConvention").asText());
    assertEquals(25, labels.path("labels").size());
    assertEquals(5, labels.path("coveredLevels").size());
    assertTrue(labels.path("inferredNegativesEligible").asBoolean());
    for (JsonNode label : labels.path("labels")) {
      assertEquals("no_reportable_finding", label.path("value").asText());
      assertEquals("inferred_negative", label.path("provenance").asText());
    }
  }

  @Test
  void draftsAndNonLumbarExamsNeverInferNegatives() {
    assertEquals(0, summarize(false).path("labels").size());
    for (String exam :
        List.of("MRI BRAIN", "MRI CERVICAL SPINE", "MRI CERVICAL AND LUMBAR SPINE")) {
      JsonNode result = LumbarTrainingLabels.summarize(packet(), exam, true);
      assertFalse(result.path("applicable").asBoolean());
      assertEquals(0, result.path("labels").size());
    }
  }

  @Test
  void positiveLabelsAndExplicitNegativesHaveSeparateProvenanceFromOmissions() {
    JsonNode completed = summarize(true, "L4-5: moderate spinal canal stenosis");
    assertEquals(25, completed.path("labels").size());
    assertEquals(24, inferredCount(completed));
    JsonNode positive = completed.path("labels").get(0);
    assertEquals("L4-L5", positive.path("level").asText());
    assertEquals("moderate", positive.path("value").asText());
    assertEquals("explicit", positive.path("provenance").asText());

    JsonNode negative = summarize(false, "L4-5: no spinal canal stenosis");
    assertEquals(1, negative.path("labels").size());
    assertEquals("absent", negative.path("labels").get(0).path("value").asText());
    assertEquals("explicit", negative.path("labels").get(0).path("provenance").asText());
    JsonNode bilateral = summarize(false, "L4-5: no bilateral foraminal stenosis");
    assertEquals(2, bilateral.path("labels").size());
  }

  @Test
  void preservesObservationSeparatelyFromAnUncertainHemangiomaInterpretation() {
    String text = "T2 hyperint lesion L4 likely intraoss hemang";
    JsonNode result = summarize(true, text);
    JsonNode parse = result.path("findings").get(0).path("textParses").get(0);
    JsonNode observed = parse.path("observations").get(0);
    JsonNode interpretation = parse.path("observations").get(1);
    assertEquals("lesion", observed.path("concept").asText());
    assertEquals("L4", observed.path("level").asText());
    assertEquals("T2", observed.path("sequence").asText());
    assertEquals("hyperintense", observed.path("signal").asText());
    assertEquals("observed", observed.path("certainty").asText());
    assertEquals("intraosseous_hemangioma", interpretation.path("concept").asText());
    assertEquals("likely", interpretation.path("certainty").asText());
    assertFalse(observed.has("size"));
    assertFalse(observed.has("T1"));
    assertFalse(observed.has("vertebralBody"));
    assertTrue(result.path("needsReview").asBoolean());
    assertEquals(0, inferredCount(result));
    assertEquals(text, parse.path("rawInput").asText());
  }

  @Test
  void mainBoxShorthandIsParsedAndUnknownInstructionsRemainForReview() {
    String shorthand = "T2 hyperint lesion L4 likely intraoss hemang";
    JsonNode result =
        LumbarTrainingLabels.summarize(
            new ReportPacket(context(), List.of(), List.of(), shorthand), "MRI LUMBAR SPINE", true);
    JsonNode evidence = result.path("findings").get(0);
    assertEquals("report_instructions", evidence.path("source").asText());
    assertEquals(shorthand, evidence.path("rawInput").asText());
    assertEquals(
        "lesion",
        evidence.path("textParses").get(0).path("observations").get(0).path("concept").asText());
    assertEquals(
        "likely",
        evidence.path("textParses").get(0).path("observations").get(1).path("certainty").asText());
    assertEquals(0, inferredCount(result));

    JsonNode unknown =
        LumbarTrainingLabels.summarize(
            new ReportPacket(context(), List.of(), List.of(), "Compare with outside study"),
            "MRI LUMBAR SPINE",
            true);
    assertTrue(unknown.path("needsReview").asBoolean());
    assertEquals(
        "Compare with outside study",
        unknown
            .path("findings")
            .get(0)
            .path("textParses")
            .get(0)
            .path("unparsedText")
            .get(0)
            .asText());
    assertEquals(0, inferredCount(unknown));
  }

  @Test
  void uncertaintyNegationLimitationsAndUnknownTextBlockFalseNormalLabels() {
    for (String text :
        List.of(
            "L4-5: possible mild spinal canal stenosis",
            "L4-5: evaluation limited by motion",
            "L4-5: foraminal stenosis",
            "L4-5: no severe spinal canal stenosis",
            "No T2 hyperint lesion L4 likely intraoss hemang",
            "L4-5: mild canal stenosis and unexpected extra words")) {
      JsonNode result = summarize(true, text);
      assertTrue(result.path("needsReview").asBoolean(), text);
      assertEquals(0, inferredCount(result), text);
    }
    JsonNode unparsed = summarize(true, "Unknown lumbar finding description");
    assertEquals(
        "Unknown lumbar finding description",
        unparsed
            .path("findings")
            .get(0)
            .path("textParses")
            .get(0)
            .path("unparsedText")
            .get(0)
            .asText());
    assertEquals(0, unparsed.path("labels").size());
  }

  @Test
  void joinedClausesDoNotLoseAnAmbiguousNegationOrUncertaintyScope() {
    for (String qualifier : List.of("no", "possible")) {
      JsonNode result =
          summarize(true, "L4-5: " + qualifier + " canal stenosis and right foraminal stenosis");
      assertTrue(result.path("needsReview").asBoolean());
      assertEquals(1, result.path("labels").size());
      assertEquals("spinal_canal_stenosis", result.path("labels").get(0).path("task").asText());
      assertEquals(0, inferredCount(result));
    }
    JsonNode repeated = summarize(false, "L4-5: no canal stenosis and no right foraminal stenosis");
    assertEquals(2, repeated.path("labels").size());
    assertFalse(repeated.path("needsReview").asBoolean());
  }

  @Test
  void conflictingExplicitLabelsStayFlaggedInsteadOfSelectingASeverity() {
    JsonNode result = summarize(true, "L4-5: mild canal stenosis", "L4-5: severe canal stenosis");
    assertTrue(result.path("needsReview").asBoolean());
    assertTrue(result.path("labels").get(0).path("needsReview").asBoolean());
    assertEquals(0, inferredCount(result));
    assertEquals(1, result.path("labels").get(0).path("conflictingEvidence").size());
  }

  @Test
  void structuredSelectionIsCountedOnceAndUnconstrainedFreeTextBlocksOmissions() {
    ReportDraft draft = new ReportDraft(context());
    var tracker =
        new StructuredFindingDraftTracker<>(Selection::region, SpineFindingBuilder::generate);
    Selection selection = selection("");
    tracker.synchronize(draft, selection);
    assertEquals(2, draft.findings().size());
    JsonNode result = LumbarTrainingLabels.summarize(draft.snapshot(), "LUMBAR_SPINE", true);
    assertEquals(25, result.path("labels").size());
    assertEquals(23, inferredCount(result));
    assertTrue(result.path("findings").get(1).path("sharesStructuredEvidence").asBoolean());

    tracker.synchronize(draft, selection("unrecognized lateral recess assessment"));
    JsonNode unknown = LumbarTrainingLabels.summarize(draft.snapshot(), "MRI LUMBAR SPINE", true);
    assertTrue(unknown.path("needsReview").asBoolean());
    assertEquals(0, inferredCount(unknown));
  }

  @Test
  void editingStructuredProseRetainsEvidenceWithoutReusingTheOldSeverity() {
    ReportDraft draft = new ReportDraft(context());
    var tracker =
        new StructuredFindingDraftTracker<>(Selection::region, SpineFindingBuilder::generate);
    tracker.synchronize(
        draft,
        new Selection(
            SpineRegion.LUMBAR, AlignmentFinding.NONE, Map.of(), List.of(level("L4-5", ""))));
    FindingEntry original = draft.findings().getFirst();
    FindingEntry edited = original.withText("L4-5: no spinal canal stenosis", "", false);
    draft.replaceFinding(original.id(), edited);
    JsonNode result = LumbarTrainingLabels.summarize(draft.snapshot(), "MRI LUMBAR SPINE", true);
    assertEquals(
        original.metadata().structuredSelection(), edited.metadata().structuredSelection());
    assertFalse(edited.metadata().structuredSelectionCurrent());
    assertEquals(1, result.path("labels").size());
    assertEquals("absent", result.path("labels").get(0).path("value").asText());
    assertTrue(result.path("needsReview").asBoolean());
    assertEquals(0, inferredCount(result));
  }

  @Test
  void aDeletedStructuredFindingIsNotReassertedFromItsGroupsRemainingMetadata() {
    ReportDraft draft = new ReportDraft(context());
    var tracker =
        new StructuredFindingDraftTracker<>(Selection::region, SpineFindingBuilder::generate);
    tracker.synchronize(draft, selection(""));
    draft.removeFinding(draft.findings().getFirst().id());
    JsonNode result = LumbarTrainingLabels.summarize(draft.snapshot(), "MRI LUMBAR SPINE", true);
    assertTrue(result.path("needsReview").asBoolean());
    assertEquals(1, result.path("labels").size());
    assertEquals("L4-L5", result.path("labels").get(0).path("level").asText());
    assertEquals(0, inferredCount(result));
  }

  @Test
  void arrowCaptionIsNotParsedAsAnIndependentDiagnosis() {
    KeyImageCapture image =
        KeyImageCapture.create(
            new ImageReference("series", "image", "1", "T2", "1", 1, 1),
            new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB),
            null,
            "L4-5: severe spinal canal stenosis");
    ReportPacket packet = new ReportPacket(context(), List.of(), List.of(image));
    JsonNode result = LumbarTrainingLabels.summarize(packet, "MRI LUMBAR SPINE", false);
    assertEquals(0, result.path("labels").size());
    assertEquals(0, result.path("findings").size());
  }

  private static Selection selection(String freeText) {
    return new Selection(
        SpineRegion.LUMBAR,
        AlignmentFinding.NONE,
        Map.of(),
        List.of(level("L3-4", ""), level("L4-5", freeText)));
  }

  private static LevelSelection level(String level, String freeText) {
    return new LevelSelection(
        level,
        false,
        List.of(),
        freeText,
        Laterality.NONE,
        Laterality.NONE,
        Severity.MODERATE,
        Severity.NONE,
        Severity.NONE);
  }

  private static JsonNode summarize(boolean completed, String... findings) {
    return LumbarTrainingLabels.summarize(packet(findings), "MRI LUMBAR SPINE", completed);
  }

  private static long inferredCount(JsonNode summary) {
    return StreamSupport.stream(summary.path("labels").spliterator(), false)
        .filter(label -> label.path("provenance").asText().equals("inferred_negative"))
        .count();
  }

  private static ReportPacket packet(String... findings) {
    return new ReportPacket(
        context(),
        java.util.Arrays.stream(findings)
            .map(text -> FindingEntry.create(text, "", false))
            .toList(),
        List.of());
  }

  private static CaseContext context() {
    return new CaseContext("test-study", "", "", "", "", "", "MRI LUMBAR SPINE");
  }
}
