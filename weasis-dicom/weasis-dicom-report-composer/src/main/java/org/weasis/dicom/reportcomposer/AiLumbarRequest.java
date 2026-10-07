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

import static org.weasis.dicom.reportcomposer.AiSchema.arrayOf;
import static org.weasis.dicom.reportcomposer.AiSchema.entry;
import static org.weasis.dicom.reportcomposer.AiSchema.enumOf;
import static org.weasis.dicom.reportcomposer.AiSchema.nullable;
import static org.weasis.dicom.reportcomposer.AiSchema.object;
import static org.weasis.dicom.reportcomposer.AiSchema.type;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.AlignmentFinding;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.Laterality;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.LevelSelection;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.ListhesisDirection;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.ListhesisSelection;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.MigrationDirection;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.OverviewFinding;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.ProtrusionLocation;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.Selection;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.Severity;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.SpineRegion;

/**
 * Lumbar spine request: instructions with the reading thresholds, an answer format that mirrors the
 * spine form's per-level fields, and the conversion of an answer into that form's selection, so the
 * composer, not the model, writes the report wording.
 */
final class AiLumbarRequest {
  static final String FORMAT_NAME = "lumbar_spine_findings";
  private static final List<String> LEVELS = SpineRegion.LUMBAR.levels();
  private static final List<String> LATERALITY = List.of("none", "left", "right", "bilateral");
  private static final List<String> SEVERITY = List.of("none", "mild", "moderate", "severe");
  private static final Pattern NERVE_ROOT = Pattern.compile("[TLS]\\d{1,2}");
  private static final Pattern NORMAL_STATEMENT =
      Pattern.compile("^(no|without|negative for|normal)\\b", Pattern.CASE_INSENSITIVE);
  // Never in the radiologist's lumbar reports; flagged if the model writes them in free text.
  private static final Pattern AVOIDED_WORDS =
      Pattern.compile(
          "degenerat\\w*|chronic\\w*|age[- ](related|appropriate)|wear and tear|desiccat\\w*"
              + "|dehydrat\\w*|height loss",
          Pattern.CASE_INSENSITIVE);

  static final String INSTRUCTIONS =
      """
      You are assisting a radiologist reading lumbar spine MRI, where small findings matter and \
      every statement must be accurate and defensible. The radiologist reviews and edits \
      everything you return.

      Reading the images
      - Each image follows a label in square brackets. Use the label, not the order of images, \
      for the image's series, plane, slice number, and position. The overview says which patient \
      directions face each series' right and bottom image edges.
      - Localizer images show where each axial slice cuts a sagittal image; each line's number is \
      that axial slice's number. Disc levels in labels and on localizers come from a separate \
      model or the radiologist, as the overview says. If the images disagree, number the levels \
      from the images and say so under limitations.

      What to report
      - Return exactly one entry for each level from T12-L1 to L5-S1, including normal levels \
      (every field none or false).
      - Mark every disc bulge, however small, including a 1 mm bulge. Never leave out a finding \
      because it is small, common, or expected for age.
      - Call displaced disc material a protrusion when its base is wider than its depth, and an \
      extrusion when any part is wider than its base or it migrates away from the disc space. \
      Give each herniation's locations; use broad_based when it spans 25-50% of the disc \
      circumference.
      - Give disc_size_mm for a bulge or herniation when you can measure it: its front-to-back \
      extent beyond the vertebral endplate margin.
      - Grade spinal canal stenosis and left and right neural foraminal stenosis separately. \
      Note lateral recess narrowing and any contact with, displacement of, or compression of a \
      nerve root, naming the root.
      - Always report annular fissures, endplate edema, facet effusions, facet arthrosis, \
      ligamentum flavum hypertrophy, listhesis (in mm when measurable), straightening of the \
      lordosis, and scoliosis.
      - Spondylosis: say whether it is absent, generalized through the lumbar spine, or present \
      at specific levels.
      - Report transitional anatomy (partial lumbarization of S1 or sacralization of L5), and \
      keep numbering levels as the labels do.
      - List reactive endplate changes (Modic-type endplate marrow signal change) by level, and \
      also set endplate_edema at a level when the change has an edema pattern.
      - Report vertebral body anterior wedging, compression deformity, fracture (whether \
      displaced, and whether there is marrow edema), and hemangiomas; pars defects, marked \
      possible when uncertain; dorsal epidural lipomatosis; perineural cysts; postsurgical \
      changes; and skeletal immaturity.
      - Put a level's other positive findings in its "other", as short phrases. Do not repeat \
      what its fields already say, do not state what is normal or absent, and do not hedge; use \
      confidence for uncertainty. Coverage and image-quality notes belong only in limitations.
      - Do not describe disc signal loss, desiccation, or disc height loss; mark spondylosis \
      instead.
      - Put other abnormal findings (the conus, paraspinal soft tissues, other marrow lesions, \
      incidental findings) in other_findings, each as one complete sentence that names the \
      location, worded like "T2 hyperintense lesion in the right hepatic lobe is nonspecific and \
      may represent a cyst." Leave out normal structures and follow-up recommendations.
      - Give limitations only when they limit what can be reported: motion, artifact (with a \
      detail naming only the area or sequence, such as "the axial images"), metal artifact, or \
      other. Do not comment on measurement precision or image size.

      Wording
      - Describe what the images show. Never write degeneration, degenerative, chronic, \
      age-related, age-appropriate, or wear and tear; use spondylosis for that concept.
      - Do not comment on cause, timing, or any relation to trauma; the radiologist decides that.
      - Cite the image IDs that best show each abnormal level and finding, with your confidence.
      - Do not speculate about the patient's identity, the institution, or dates.""";

  /** An answer converted for the spine form, with what the form has no place for. */
  record Answer(
      Selection selection,
      List<String> additionalFindings,
      List<String> limitations,
      Map<String, List<String>> imagesByLevel,
      Map<String, String> confidenceByLevel,
      List<String> warnings) {}

  private AiLumbarRequest() {}

  /** Strict JSON schema; every object lists all of its properties as required. */
  static Map<String, Object> outputFormat() {
    return outputFormat(false);
  }

  /** With marks, the answer also covers each numbered ring the radiologist drew. */
  static Map<String, Object> outputFormat(boolean marks) {
    Map<String, Object> level =
        object(
            entry("level", enumOf(LEVELS)),
            entry("bulge", type("boolean")),
            entry("herniation", enumOf(List.of("none", "protrusion", "extrusion"))),
            entry("herniation_locations", arrayOf(enumOf(locationNames()))),
            entry("disc_size_mm", nullable("number")),
            entry("migration", enumOf(List.of("none", "superior", "inferior"))),
            entry("migration_mm", nullable("number")),
            entry("annular_fissure", type("boolean")),
            entry("ventral_epidural_lipomatosis", type("boolean")),
            entry("facet_arthrosis", enumOf(LATERALITY)),
            entry("facet_effusion", enumOf(LATERALITY)),
            entry("ligamentum_flavum_hypertrophy", enumOf(LATERALITY)),
            entry("endplate_edema", type("boolean")),
            entry("spinal_canal_stenosis", enumOf(SEVERITY)),
            entry("left_foraminal_stenosis", enumOf(SEVERITY)),
            entry("right_foraminal_stenosis", enumOf(SEVERITY)),
            entry("lateral_recess_narrowing", enumOf(LATERALITY)),
            entry(
                "nerve_roots",
                arrayOf(
                    object(
                        entry("side", enumOf(List.of("left", "right"))),
                        entry("root", type("string")),
                        entry("effect", enumOf(List.of("contacts", "displaces", "compresses")))))),
            entry("other", type("string")),
            entry("image_ids", arrayOf(type("string"))),
            entry("confidence", enumOf(List.of("low", "medium", "high"))));
    List<Map.Entry<String, Object>> properties = new ArrayList<>();
    properties.addAll(
        List.of(
            entry(
                "alignment",
                object(
                    entry("lordosis", enumOf(List.of("normal", "straightened"))),
                    entry(
                        "scoliosis",
                        enumOf(List.of("none", "dextroscoliosis", "levoscoliosis", "s_shaped"))),
                    entry("scoliosis_severity", enumOf(SEVERITY)),
                    entry("scoliosis_degrees", nullable("number")))),
            entry(
                "listheses",
                arrayOf(
                    object(
                        entry("level", enumOf(LEVELS)),
                        entry("direction", enumOf(List.of("anterolisthesis", "retrolisthesis"))),
                        entry("displacement_mm", nullable("number")),
                        entry("image_ids", arrayOf(type("string")))))),
            entry(
                "spondylosis",
                object(
                    entry("extent", enumOf(List.of("none", "generalized", "specific_levels"))),
                    entry("levels", arrayOf(enumOf(LEVELS))))),
            entry("levels", arrayOf(level))));
    properties.addAll(AiLumbarExtras.schema());
    properties.addAll(
        List.of(
            entry(
                "other_findings",
                arrayOf(
                    object(
                        entry("finding", type("string")),
                        entry("image_ids", arrayOf(type("string"))),
                        entry("confidence", enumOf(List.of("low", "medium", "high")))))),
            entry("limitations", AiLumbarExtras.limitationsSchema())));
    if (marks) {
      List<String> locations = new ArrayList<>(LEVELS);
      locations.add("other");
      properties.add(AiSchema.marks(enumOf(locations)));
    }
    Map<String, Object> schema = object(properties);

    Map<String, Object> format = new LinkedHashMap<>();
    format.put("type", "json_schema");
    format.put("name", FORMAT_NAME);
    format.put("strict", true);
    format.put("schema", schema);
    return format;
  }

  static Answer read(JsonNode answer) {
    List<String> warnings = new ArrayList<>();
    JsonNode alignment = answer.path("alignment");
    List<AlignmentFinding> alignments = new ArrayList<>();
    if ("straightened".equals(text(alignment, "lordosis"))) {
      alignments.add(AlignmentFinding.LUMBAR_STRAIGHTENING);
    }
    switch (text(alignment, "scoliosis")) {
      case "dextroscoliosis" -> alignments.add(AlignmentFinding.LUMBAR_DEXTROSCOLIOSIS);
      case "levoscoliosis" -> alignments.add(AlignmentFinding.LUMBAR_LEVOSCOLIOSIS);
      default -> {
        // No scoliosis.
      }
    }

    Map<String, ListhesisSelection> listheses = new LinkedHashMap<>();
    for (JsonNode node : answer.path("listheses")) {
      String level = level(node);
      ListhesisDirection direction =
          switch (text(node, "direction")) {
            case "anterolisthesis" -> ListhesisDirection.ANTEROLISTHESIS;
            case "retrolisthesis" -> ListhesisDirection.RETROLISTHESIS;
            default -> ListhesisDirection.NONE;
          };
      if (LEVELS.contains(level) && direction != ListhesisDirection.NONE) {
        listheses.putIfAbsent(
            level, new ListhesisSelection(level, direction, millimeters(node, "displacement_mm")));
      }
    }

    Map<OverviewFinding, List<String>> overview = new EnumMap<>(OverviewFinding.class);
    JsonNode spondylosis = answer.path("spondylosis");
    List<String> spondylosisLevels = levels(spondylosis.path("levels"));
    switch (text(spondylosis, "extent")) {
      case "generalized" -> overview.put(OverviewFinding.SPONDYLOSIS, List.of());
      case "specific_levels" -> overview.put(OverviewFinding.SPONDYLOSIS, spondylosisLevels);
      default -> {
        // No spondylosis.
      }
    }

    Map<String, LevelSelection> levels = new LinkedHashMap<>();
    Map<String, List<String>> imagesByLevel = new LinkedHashMap<>();
    Map<String, String> confidenceByLevel = new LinkedHashMap<>();
    for (JsonNode node : answer.path("levels")) {
      String level = level(node);
      if (!LEVELS.contains(level) || levels.containsKey(level)) {
        continue;
      }
      levels.put(level, levelSelection(level, node));
      List<String> images = strings(node.path("image_ids"));
      if (levels.get(level).hasFinding() && !images.isEmpty()) {
        imagesByLevel.put(level, images);
      }
      if (levels.get(level).hasFinding() && !text(node, "confidence").isEmpty()) {
        confidenceByLevel.put(level, text(node, "confidence"));
      }
      flagAvoidedWords(level, node.path("other").asText(""), warnings);
    }

    Set<String> canalStenosisLevels = new LinkedHashSet<>();
    levels.values().stream()
        .filter(l -> l.spinalCanalSeverity() != Severity.NONE)
        .forEach(l -> canalStenosisLevels.add(l.level()));
    List<String> additionalFindings =
        new ArrayList<>(AiLumbarExtras.sentences(answer, canalStenosisLevels));
    for (JsonNode node : answer.path("other_findings")) {
      String finding = withoutNormalStatements(node.path("finding").asText(""));
      if (!finding.isEmpty()) {
        additionalFindings.add(ComposerText.sentence(capitalize(finding)));
        flagAvoidedWords("other findings", finding, warnings);
      }
    }
    for (JsonNode node : answer.path("limitations")) {
      flagAvoidedWords("limitations", node.path("detail").asText(""), warnings);
    }

    Selection selection =
        new Selection(
            SpineRegion.LUMBAR,
            alignments,
            severity(text(alignment, "scoliosis_severity")),
            number(alignment, "scoliosis_degrees").map(AiLumbarRequest::format).orElse(""),
            List.copyOf(listheses.values()),
            overview,
            "",
            LEVELS.stream().filter(levels::containsKey).map(levels::get).toList());
    return new Answer(
        selection,
        List.copyOf(additionalFindings),
        AiLumbarExtras.limitations(answer),
        imagesByLevel,
        confidenceByLevel,
        List.copyOf(warnings));
  }

  private static LevelSelection levelSelection(String level, JsonNode node) {
    List<ProtrusionLocation> locations = locations(node.path("herniation_locations"));
    String herniation = text(node, "herniation");
    if (!"none".equals(herniation) && !herniation.isEmpty() && locations.isEmpty()) {
      locations = List.of(ProtrusionLocation.CENTRAL);
    }
    MigrationDirection migration =
        switch (text(node, "migration")) {
          case "superior" -> MigrationDirection.SUPERIOR;
          case "inferior" -> MigrationDirection.INFERIOR;
          default -> MigrationDirection.NONE;
        };
    return new LevelSelection(
        level,
        node.path("bulge").asBoolean(false),
        "protrusion".equals(herniation) ? locations : List.of(),
        "extrusion".equals(herniation) ? locations : List.of(),
        node.path("annular_fissure").asBoolean(false),
        node.path("ventral_epidural_lipomatosis").asBoolean(false),
        migration,
        millimeters(node, "migration_mm"),
        freeText(node, herniation),
        laterality(text(node, "facet_arthrosis")),
        laterality(text(node, "ligamentum_flavum_hypertrophy")),
        severity(text(node, "spinal_canal_stenosis")),
        severity(text(node, "left_foraminal_stenosis")),
        severity(text(node, "right_foraminal_stenosis")));
  }

  // Findings the form has no field for, as plain sentences in its per-level free text.
  private static String freeText(JsonNode node, String herniation) {
    List<String> parts = new ArrayList<>();
    Optional<Double> size = number(node, "disc_size_mm");
    if (size.isPresent()) {
      String subject =
          switch (herniation) {
            case "protrusion" -> "Protrusion";
            case "extrusion" -> "Extrusion";
            default -> node.path("bulge").asBoolean(false) ? "Bulge" : "";
          };
      if (!subject.isEmpty()) {
        parts.add(format(size.get()) + " mm " + subject.toLowerCase(Locale.ROOT));
      }
    }
    switch (text(node, "lateral_recess_narrowing")) {
      case "left", "right" ->
          parts.add(
              "Narrowing of the " + text(node, "lateral_recess_narrowing") + " lateral recess");
      case "bilateral" -> parts.add("Narrowing of the bilateral lateral recesses");
      default -> {
        // No lateral recess narrowing.
      }
    }
    for (JsonNode root : node.path("nerve_roots")) {
      String effect = text(root, "effect");
      String side = text(root, "side");
      String name = text(root, "root").toUpperCase(Locale.ROOT);
      if (List.of("contacts", "displaces", "compresses").contains(effect)
          && List.of("left", "right").contains(side)) {
        parts.add(
            capitalize(effect)
                + " the "
                + side
                + (NERVE_ROOT.matcher(name).matches() ? " " + name : "")
                + " nerve root");
      }
    }
    if (node.path("endplate_edema").asBoolean(false)) {
      parts.add("Endplate edema");
    }
    switch (text(node, "facet_effusion")) {
      case "left", "right" ->
          parts.add(capitalize(text(node, "facet_effusion")) + " facet effusion");
      case "bilateral" -> parts.add("Bilateral facet effusions");
      default -> {
        // No facet effusion.
      }
    }
    String other = withoutNormalStatements(node.path("other").asText(""));
    if (!other.isEmpty()) {
      parts.add(capitalize(other));
    }
    return String.join(". ", parts);
  }

  // Normal is reported by omission, so sentences stating an absence or a normal finding go.
  static String withoutNormalStatements(String text) {
    return java.util.Arrays.stream(ComposerText.clean(text).split("(?<=[.;])\\s+"))
        .map(sentence -> sentence.strip().replaceAll("[.;]+$", ""))
        .filter(sentence -> !sentence.isEmpty() && !NORMAL_STATEMENT.matcher(sentence).find())
        .reduce((a, b) -> a + ". " + b)
        .orElse("");
  }

  private static void flagAvoidedWords(String where, String text, List<String> warnings) {
    var matcher = AVOIDED_WORDS.matcher(text);
    Set<String> words = new LinkedHashSet<>();
    while (matcher.find()) {
      words.add(matcher.group().toLowerCase(Locale.ROOT));
    }
    if (!words.isEmpty()) {
      warnings.add(where + " uses " + String.join(", ", words));
    }
  }

  private static List<String> locationNames() {
    return List.of(ProtrusionLocation.values()).stream()
        .map(location -> location.name().toLowerCase(Locale.ROOT))
        .toList();
  }

  private static List<ProtrusionLocation> locations(JsonNode values) {
    List<ProtrusionLocation> locations = new ArrayList<>();
    for (JsonNode value : values) {
      for (ProtrusionLocation location : ProtrusionLocation.values()) {
        if (location.name().equalsIgnoreCase(value.asText(""))) {
          locations.add(location);
        }
      }
    }
    return locations;
  }

  private static List<String> levels(JsonNode values) {
    List<String> requested = strings(values);
    return LEVELS.stream().filter(requested::contains).toList();
  }

  private static Laterality laterality(String value) {
    return switch (value) {
      case "left" -> Laterality.LEFT;
      case "right" -> Laterality.RIGHT;
      case "bilateral" -> Laterality.BILATERAL;
      default -> Laterality.NONE;
    };
  }

  private static Severity severity(String value) {
    return switch (value) {
      case "mild" -> Severity.MILD;
      case "moderate" -> Severity.MODERATE;
      case "severe" -> Severity.SEVERE;
      default -> Severity.NONE;
    };
  }

  private static String millimeters(JsonNode node, String field) {
    return number(node, field).map(value -> format(value) + " mm").orElse("");
  }

  private static Optional<Double> number(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isNumber() && value.asDouble() > 0
        ? Optional.of(value.asDouble())
        : Optional.empty();
  }

  private static String format(double value) {
    return String.format(Locale.ROOT, "%.1f", value).replaceAll("\\.0$", "");
  }

  private static List<String> strings(JsonNode values) {
    List<String> strings = new ArrayList<>();
    for (JsonNode value : values) {
      String text = ComposerText.clean(value.asText(""));
      if (!text.isEmpty()) {
        strings.add(text);
      }
    }
    return List.copyOf(strings);
  }

  private static String level(JsonNode node) {
    return node.path("level").asText("").strip().toUpperCase(Locale.ROOT);
  }

  // Enum-like values, lower-cased; anything that is not a string reads as empty.
  private static String text(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isTextual() ? value.asText().strip().toLowerCase(Locale.ROOT) : "";
  }

  private static String capitalize(String value) {
    return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
  }
}
