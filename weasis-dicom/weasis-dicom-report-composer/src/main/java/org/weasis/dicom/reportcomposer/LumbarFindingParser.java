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

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deliberately bounded grammar. Unrecognized material remains evidence, never a normal label. */
final class LumbarFindingParser {
  private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
  private static final String LEVEL = "(L[1-5]\\s*[-/]\\s*(?:L?[2-5]|S1))";
  private static final Pattern PREFIX_LEVEL =
      Pattern.compile("^" + LEVEL + "\\s*:?\\s*", Pattern.CASE_INSENSITIVE);
  private static final Pattern SUFFIX_LEVEL =
      Pattern.compile("\\s+(?:at\\s+)?" + LEVEL + "$", Pattern.CASE_INSENSITIVE);
  private static final Pattern LESION =
      Pattern.compile(
          "^T2\\s+hyper(?:int|intense)\\s+lesion\\s+(L[1-5])"
              + "(?:\\s+(likely|possible|possibly|suspected)\\s+intraoss(?:eous)?\\s+hemang(?:ioma)?)?$",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern STENOSIS =
      Pattern.compile(
          "^(?:(mild|moderate|severe)\\s+)?(?:(left|right|bilateral)\\s+)?"
              + "(?:(mild|moderate|severe)\\s+)?"
              + "(spinal canal|central canal|canal|neural foraminal|foraminal|subarticular|lateral recess)"
              + "\\s+(?:stenosis|narrowing)$",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern UNCERTAINTY =
      Pattern.compile(
          "\\b(?:likely|possible|possibly|suspected|questionable|uncertain|cannot exclude|may represent)\\b|\\?",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern LIMITATION =
      Pattern.compile(
          "\\b(?:limited|motion|artifact|artefact|nondiagnostic|not assessed|not visualized)\\b",
          Pattern.CASE_INSENSITIVE);

  private LumbarFindingParser() {}

  static ObjectNode parse(String text) {
    return parse(text, "");
  }

  static ObjectNode parse(String text, String defaultLevel) {
    ObjectNode result = JSON.objectNode();
    ArrayNode observations = result.putArray("observations");
    ArrayNode labels = result.putArray("labels");
    ArrayNode unparsed = result.putArray("unparsedText");
    String input = text == null ? "" : text;
    result.put("rawInput", input);
    boolean uncertain = UNCERTAINTY.matcher(input).find();
    boolean limited = LIMITATION.matcher(input).find();
    String level = canonicalLevel(defaultLevel);
    for (String part :
        input.replace('\u2013', '-').replace('\u2014', '-').split("[;\\n]+|(?<!\\d)\\.(?!\\d)")) {
      String clause = part.trim().replaceAll("[.\\s]+$", "");
      if (clause.isBlank()) {
        continue;
      }
      Matcher lesion = LESION.matcher(clause);
      if (lesion.matches()) {
        ObjectNode observation = observations.addObject();
        observation.put("concept", "lesion");
        observation.put("level", lesion.group(1).toUpperCase(Locale.ROOT));
        observation.put("sequence", "T2");
        observation.put("signal", "hyperintense");
        observation.put("certainty", "observed");
        observation.put("provenance", "explicit");
        observation.put("evidenceText", clause);
        if (lesion.group(2) != null) {
          ObjectNode interpretation = observations.addObject();
          interpretation.put("concept", "intraosseous_hemangioma");
          interpretation.put("level", lesion.group(1).toUpperCase(Locale.ROOT));
          interpretation.put("certainty", lesion.group(2).toLowerCase(Locale.ROOT));
          interpretation.put("role", "interpretation");
          interpretation.put("provenance", "explicit");
          interpretation.put("evidenceText", clause);
        }
        continue;
      }
      Matcher prefix = PREFIX_LEVEL.matcher(clause);
      if (prefix.find()) {
        level = canonicalLevel(prefix.group(1));
        clause = clause.substring(prefix.end()).trim();
      } else {
        Matcher suffix = SUFFIX_LEVEL.matcher(clause);
        if (suffix.find()) {
          level = canonicalLevel(suffix.group(1));
          clause = clause.substring(0, suffix.start()).trim();
        }
      }
      boolean accounted = true;
      boolean qualifiedEarlierDetail = false;
      for (String detail : clause.split("\\s+and\\s+|\\s*,\\s*")) {
        String normalized = detail.trim().toLowerCase(Locale.ROOT);
        String certainty = "asserted";
        Matcher qualifier =
            Pattern.compile("^(likely|possible|possibly|suspected|questionable|cannot exclude)\\s+")
                .matcher(normalized);
        if (qualifier.find()) {
          certainty = qualifier.group(1);
          normalized = normalized.substring(qualifier.end());
        }
        boolean negative = normalized.startsWith("no ") || normalized.startsWith("without ");
        boolean reportable = normalized.startsWith("no significant ");
        boolean qualified = negative || !certainty.equals("asserted");
        if (qualifiedEarlierDetail && !qualified) {
          // A leading "no" or "possible" may also qualify this joined clause.
          accounted = false;
          continue;
        }
        qualifiedEarlierDetail |= qualified;
        if (negative) {
          normalized = normalized.replaceFirst("^(?:no(?: significant)?|without)\\s+", "");
        }
        Matcher stenosis = STENOSIS.matcher(normalized);
        if (stenosis.matches() && !level.isBlank()) {
          String severity = stenosis.group(1) == null ? stenosis.group(3) : stenosis.group(1);
          if (stenosis.group(1) != null && stenosis.group(3) != null) {
            accounted = false;
            continue;
          }
          String anatomy = stenosis.group(4).toLowerCase(Locale.ROOT);
          String task =
              anatomy.contains("canal")
                  ? "spinal_canal_stenosis"
                  : anatomy.contains("foraminal")
                      ? "neural_foraminal_stenosis"
                      : "subarticular_stenosis";
          String side = stenosis.group(2);
          if ((task.equals("spinal_canal_stenosis") && side != null)
              || (!task.equals("spinal_canal_stenosis") && side == null)
              || (negative && severity != null)) {
            accounted = false;
            continue;
          }
          List<String> sides =
              side == null
                  ? List.of("central")
                  : side.equalsIgnoreCase("bilateral")
                      ? List.of("left", "right")
                      : List.of(side.toLowerCase(Locale.ROOT));
          for (String laterality : sides) {
            labels.add(
                label(
                    level,
                    task,
                    laterality,
                    negative
                        ? reportable ? "no_reportable_finding" : "absent"
                        : severity == null ? "present_ungraded" : severity.toLowerCase(Locale.ROOT),
                    certainty,
                    "explicit",
                    part.trim()));
          }
        } else if (!negative
            && !level.isBlank()
            && normalized.matches(
                "(?:disc bulge|disc desiccation|disc degeneration|annular fissure|ventral epidural lipomatosis|(?:left |right |bilateral )?(?:facet arthrosis|ligamentum flavum hypertrophy))")) {
          ObjectNode observation = observations.addObject();
          observation.put("concept", normalized.replace(' ', '_'));
          observation.put("level", level);
          observation.put("certainty", certainty);
          observation.put("provenance", "explicit");
          observation.put("evidenceText", part.trim());
        } else {
          accounted = false;
        }
      }
      if (!accounted || clause.isBlank()) {
        unparsed.add(part.trim());
      }
    }
    result.put("fullyParsed", unparsed.isEmpty() && !limited);
    result.put("uncertain", uncertain);
    result.put("limited", limited);
    result.put("needsReview", !unparsed.isEmpty() || uncertain || limited);
    return result;
  }

  static ObjectNode label(
      String level,
      String task,
      String laterality,
      String value,
      String certainty,
      String provenance,
      String evidence) {
    ObjectNode label = JSON.objectNode();
    label.put("level", level);
    label.put("task", task);
    label.put("laterality", laterality);
    label.put("value", value);
    label.put("certainty", certainty);
    label.put("provenance", provenance);
    label.put("evidenceText", evidence);
    return label;
  }

  static String canonicalLevel(String value) {
    String level =
        value == null ? "" : value.toUpperCase(Locale.ROOT).replaceAll("\\s", "").replace('/', '-');
    if (level.matches("L[1-4]-[2-5]")) {
      level = level.substring(0, 3) + "L" + level.substring(3);
    }
    return LumbarTrainingLabels.LEVELS.contains(level) ? level : "";
  }
}
