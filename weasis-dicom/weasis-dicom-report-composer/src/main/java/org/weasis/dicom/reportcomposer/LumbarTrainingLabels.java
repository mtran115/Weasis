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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Local annotation export, not a prediction or a claim of anatomical normality. */
public final class LumbarTrainingLabels {
  public static final String PARSER_VERSION = "lumbar_local_v1";
  public static final String REPORTING_CONVENTION = "positive_only_reportable_v1";
  static final List<String> LEVELS = List.of("L1-L2", "L2-L3", "L3-L4", "L4-L5", "L5-S1");
  private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

  private LumbarTrainingLabels() {}

  public static JsonNode summarize(ReportPacket packet, String exam, boolean completed) {
    ObjectNode result = JSON.objectNode();
    result.put("parserVersion", PARSER_VERSION);
    result.put("reportingConvention", REPORTING_CONVENTION);
    result.put("exam", exam == null ? "" : exam);
    result.put("completed", completed);
    result.put("scope", "lumbar_canal_foraminal_subarticular");
    result.put("negativeMeaning", "no_reportable_finding, not verified anatomical normality");
    ArrayNode reasons = result.putArray("reviewReasons");
    ArrayNode parsedFindings = result.putArray("findings");
    Map<String, ObjectNode> labels = new LinkedHashMap<>();
    boolean lumbar = isLumbarExam(exam);
    result.put("applicable", lumbar);
    Set<String> staleGroups = new HashSet<>();
    Map<String, Integer> groupCounts = new LinkedHashMap<>();
    packet
        .findings()
        .forEach(entry -> groupCounts.merge(entry.metadata().groupId(), 1, Integer::sum));
    for (FindingEntry entry : packet.findings()) {
      FindingMetadata metadata = entry.metadata();
      if (!metadata.groupId().isBlank()
          && (!metadata.structuredSelectionCurrent()
              || metadata.groupFindingCount() > 0
                  && metadata.groupFindingCount() != groupCounts.get(metadata.groupId()))) {
        staleGroups.add(metadata.groupId());
      }
    }
    Set<String> processedGroups = new HashSet<>();
    if (lumbar) {
      for (FindingEntry entry : packet.findings()) {
        FindingMetadata metadata = entry.metadata();
        ObjectNode evidence = parsedFindings.addObject();
        evidence.put("findingId", entry.id());
        evidence.put("groupId", metadata.groupId());
        evidence.put("source", metadata.source());
        evidence.put("rawInput", metadata.rawInput());
        evidence.put("currentInput", metadata.currentInput());
        boolean current =
            metadata.structuredSelectionCurrent() && !staleGroups.contains(metadata.groupId());
        if (current && !metadata.groupId().isBlank()) {
          if (!processedGroups.add(metadata.groupId())) {
            evidence.put("sharesStructuredEvidence", true);
            continue;
          }
          JsonNode selection = metadata.structuredSelection();
          evidence.set("structuredSelection", selection);
          if (metadata.selectionType().endsWith("SpineFindingBuilder$Selection")
              && selection.path("region").asText().equals("LUMBAR")) {
            parseStructured(selection, entry.id(), evidence, labels, reasons);
            continue;
          }
          reasons.add("unsupported_structured_selection:" + entry.id());
        } else if (metadata.structuredSelection() != null) {
          reasons.add("structured_text_edited:" + entry.id());
        }
        addParsed(
            LumbarFindingParser.parse(metadata.currentInput()),
            entry.id(),
            evidence,
            labels,
            reasons);
      }
      if (!packet.reportInstructions().isBlank()) {
        ObjectNode evidence = parsedFindings.addObject();
        evidence.put("source", "report_instructions");
        evidence.put("rawInput", packet.reportInstructions());
        evidence.put("currentInput", packet.reportInstructions());
        addParsed(
            LumbarFindingParser.parse(packet.reportInstructions()),
            "report_instructions",
            evidence,
            labels,
            reasons);
      }
    }
    boolean infer = lumbar && completed && reasons.isEmpty();
    result.put("inferredNegativesEligible", infer);
    result.put("needsReview", !reasons.isEmpty());
    ArrayNode coverage = result.putArray("coveredLevels");
    if (infer) {
      LEVELS.forEach(coverage::add);
      result.put("coverageProvenance", "completed_annotation_pass_under_reporting_convention");
      for (String level : LEVELS) {
        infer(labels, level, "spinal_canal_stenosis", "central");
        for (String side : List.of("left", "right")) {
          infer(labels, level, "neural_foraminal_stenosis", side);
          infer(labels, level, "subarticular_stenosis", side);
        }
      }
    }
    ArrayNode output = result.putArray("labels");
    labels.values().forEach(output::add);
    return result;
  }

  private static void parseStructured(
      JsonNode selection,
      String findingId,
      ObjectNode evidence,
      Map<String, ObjectNode> labels,
      ArrayNode reasons) {
    ArrayNode observations = evidence.putArray("observations");
    for (String field : List.of("alignments", "overviewLevels", "listheses")) {
      if (!selection.path(field).isEmpty()) {
        ObjectNode item = observations.addObject();
        item.put("concept", field);
        item.put("provenance", "explicit_structured");
        item.set("value", selection.path(field).deepCopy());
      }
    }
    checkMeasurement(selection.path("scoliosisDegrees").asText(), findingId, reasons);
    for (JsonNode listhesis : selection.path("listheses")) {
      checkMeasurement(listhesis.path("displacement").asText(), findingId, reasons);
    }
    if (!selection.path("degenerativeDetails").asText().isBlank()) {
      addParsed(
          LumbarFindingParser.parse(selection.path("degenerativeDetails").asText()),
          findingId,
          evidence,
          labels,
          reasons);
    }
    for (JsonNode levelSelection : selection.path("levelSelections")) {
      checkMeasurement(levelSelection.path("migrationDistance").asText(), findingId, reasons);
      String level = LumbarFindingParser.canonicalLevel(levelSelection.path("level").asText());
      if (level.isBlank()) {
        // The form also supports T12-L1, which is outside this five-level training scope.
        if (!levelSelection.path("freeText").asText().isBlank()) {
          reasons.add("unparsed_out_of_scope_level_text:" + findingId);
        }
        continue;
      }
      for (String field :
          List.of(
              "bulge",
              "annularFissure",
              "ventralEpiduralLipomatosis",
              "protrusionLocations",
              "extrusionLocations",
              "facetArthrosis",
              "posteriorElementHypertrophy",
              "migrationDirection")) {
        JsonNode value = levelSelection.path(field);
        if ((!value.isBoolean() || value.asBoolean())
            && !value.isMissingNode()
            && !(value.isArray() && value.isEmpty())
            && !value.asText().equals("NONE")) {
          ObjectNode item = observations.addObject();
          item.put("concept", field);
          item.put("level", level);
          item.put("provenance", "explicit_structured");
          item.set("value", value.deepCopy());
        }
      }
      addSeverity(
          levelSelection,
          "spinalCanalSeverity",
          "spinal_canal_stenosis",
          "central",
          level,
          findingId,
          labels,
          reasons);
      addSeverity(
          levelSelection,
          "leftForaminalSeverity",
          "neural_foraminal_stenosis",
          "left",
          level,
          findingId,
          labels,
          reasons);
      addSeverity(
          levelSelection,
          "rightForaminalSeverity",
          "neural_foraminal_stenosis",
          "right",
          level,
          findingId,
          labels,
          reasons);
      if (!levelSelection.path("freeText").asText().isBlank()) {
        addParsed(
            LumbarFindingParser.parse(levelSelection.path("freeText").asText(), level),
            findingId,
            evidence,
            labels,
            reasons);
      }
    }
  }

  private static void addSeverity(
      JsonNode levelSelection,
      String field,
      String task,
      String side,
      String level,
      String findingId,
      Map<String, ObjectNode> labels,
      ArrayNode reasons) {
    String severity = levelSelection.path(field).asText("NONE");
    if (severity.equals("NONE")) {
      return; // An untouched form control is not an explicit negative.
    }
    if (!List.of("MILD", "MODERATE", "SEVERE").contains(severity)) {
      reasons.add("unsupported_structured_severity:" + findingId);
      return;
    }
    ObjectNode label =
        LumbarFindingParser.label(
            level, task, side, severity.toLowerCase(Locale.ROOT), "asserted", "explicit", field);
    label.put("source", "structured");
    addLabel(label, findingId, labels, reasons);
  }

  private static void addParsed(
      ObjectNode parsed,
      String findingId,
      ObjectNode evidence,
      Map<String, ObjectNode> labels,
      ArrayNode reasons) {
    evidence.withArray("textParses").add(parsed);
    if (parsed.path("needsReview").asBoolean()) {
      reasons.add("text_needs_review:" + findingId);
    }
    for (JsonNode label : parsed.path("labels")) {
      ObjectNode copy = label.deepCopy();
      copy.put("source", "local_text_parser");
      addLabel(copy, findingId, labels, reasons);
    }
  }

  private static void addLabel(
      ObjectNode label, String findingId, Map<String, ObjectNode> labels, ArrayNode reasons) {
    label.putArray("sourceFindingIds").add(findingId);
    String key =
        key(
            label.path("level").asText(),
            label.path("task").asText(),
            label.path("laterality").asText());
    ObjectNode previous = labels.get(key);
    if (previous != null
        && (!previous.path("value").equals(label.path("value"))
            || !previous.path("certainty").equals(label.path("certainty")))) {
      reasons.add("conflicting_explicit_labels:" + key);
      previous.put("needsReview", true);
      previous.withArray("conflictingEvidence").add(label);
    } else if (previous != null) {
      previous.withArray("sourceFindingIds").add(findingId);
    } else {
      labels.put(key, label);
    }
  }

  private static void checkMeasurement(String value, String findingId, ArrayNode reasons) {
    if (!value.isBlank()
        && !value.trim().matches("[+-]?\\d+(?:\\.\\d+)?\\s*(?:mm|cm|degrees?|\u00b0)?")) {
      reasons.add("unparsed_structured_measurement:" + findingId);
    }
  }

  private static void infer(
      Map<String, ObjectNode> labels, String level, String task, String side) {
    labels.computeIfAbsent(
        key(level, task, side),
        ignored ->
            LumbarFindingParser.label(
                level,
                task,
                side,
                "no_reportable_finding",
                "inferred",
                "inferred_negative",
                REPORTING_CONVENTION));
  }

  private static String key(String level, String task, String side) {
    return level + ":" + task + ":" + side;
  }

  private static boolean isLumbarExam(String exam) {
    String normalized = exam == null ? "" : exam.toLowerCase(Locale.ROOT).replace('_', ' ');
    return normalized.matches(".*\\b(?:lumbar|l[- ]?spine)\\b.*")
        && !normalized.matches(".*\\b(?:cervical|thoracic|whole spine)\\b.*");
  }
}
