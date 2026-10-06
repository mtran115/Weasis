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
import static org.weasis.dicom.reportcomposer.AiSchema.object;
import static org.weasis.dicom.reportcomposer.AiSchema.type;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.SpineRegion;

/**
 * Lumbar findings the spine form has no field for, written in the radiologist's quick-phrase
 * wording so they read as they do when added by hand.
 */
final class AiLumbarExtras {
  static final List<String> VERTEBRAE = List.of("T11", "T12", "L1", "L2", "L3", "L4", "L5", "S1");
  static final List<String> SEGMENTS =
      List.of("L1", "L2", "L3", "L4", "L5", "S1", "S2", "S3", "S4");
  private static final List<String> LEVELS = SpineRegion.LUMBAR.levels();
  private static final List<String> SEVERITY = List.of("none", "mild", "moderate", "severe");
  private static final String METAL_ARTIFACT =
      "metallic susceptibility artifact limiting evaluation of surrounding structures";

  private AiLumbarExtras() {}

  static List<Map.Entry<String, Object>> schema() {
    return List.of(
        entry("transitional_anatomy", enumOf(List.of("none", "lumbarized_s1", "sacralized_l5"))),
        entry("reactive_endplate_changes", arrayOf(enumOf(LEVELS))),
        entry(
            "vertebral_bodies",
            arrayOf(
                object(
                    entry("vertebra", enumOf(VERTEBRAE)),
                    entry(
                        "finding",
                        enumOf(
                            List.of(
                                "anterior_wedging",
                                "compression_deformity",
                                "fracture",
                                "hemangioma"))),
                    entry("severity", enumOf(SEVERITY)),
                    entry("displaced", type("boolean")),
                    entry("marrow_edema", type("boolean")),
                    entry("image_ids", arrayOf(type("string")))))),
        entry(
            "pars_defects",
            arrayOf(
                object(
                    entry("vertebra", enumOf(VERTEBRAE)),
                    entry("side", enumOf(List.of("left", "right", "bilateral"))),
                    entry("certainty", enumOf(List.of("possible", "definite"))),
                    entry("image_ids", arrayOf(type("string")))))),
        entry("dorsal_epidural_lipomatosis", arrayOf(enumOf(LEVELS))),
        entry(
            "postsurgical",
            arrayOf(
                object(
                    entry(
                        "procedure",
                        enumOf(
                            List.of(
                                "posterior_fusion",
                                "laminectomy",
                                "hemilaminectomy",
                                "discectomy"))),
                    entry("levels", arrayOf(enumOf(LEVELS))),
                    entry("side", enumOf(List.of("none", "left", "right", "bilateral")))))),
        entry("perineural_cysts", arrayOf(enumOf(SEGMENTS))),
        entry("skeletally_immature", type("boolean")));
  }

  static Map<String, Object> limitationsSchema() {
    return arrayOf(
        object(
            entry("kind", enumOf(List.of("motion", "artifact", "metal_artifact", "other"))),
            entry("detail", type("string"))));
  }

  /** Sentences in a stable reading order; canal levels decide whether lipomatosis narrows it. */
  static List<String> sentences(JsonNode answer, Set<String> canalStenosisLevels) {
    List<String> sentences = new ArrayList<>();
    switch (text(answer, "transitional_anatomy")) {
      case "lumbarized_s1" ->
          sentences.add("Transitional anatomy with partial lumbarization of S1.");
      case "sacralized_l5" ->
          sentences.add("Transitional anatomy with partial sacralization of L5.");
      default -> {
        // Standard segmentation.
      }
    }
    if ("s_shaped".equals(text(answer.path("alignment"), "scoliosis"))) {
      sentences.add(
          sentence(
              adjective(text(answer.path("alignment"), "scoliosis_severity"))
                  + "S-shaped scoliotic curvature of the lumbar spine"));
    }
    for (JsonNode node : answer.path("vertebral_bodies")) {
      vertebralBody(node).ifPresent(sentences::add);
    }
    for (JsonNode node : answer.path("pars_defects")) {
      parsDefect(node).ifPresent(sentences::add);
    }
    List<String> reactive = levels(answer.path("reactive_endplate_changes"));
    if (!reactive.isEmpty()) {
      sentences.add("Reactive endplate changes at " + joinList(reactive) + ".");
    }
    List<String> lipomatosis = levels(answer.path("dorsal_epidural_lipomatosis"));
    if (!lipomatosis.isEmpty()) {
      boolean narrows = lipomatosis.stream().anyMatch(canalStenosisLevels::contains);
      sentences.add(
          "Dorsal epidural lipomatosis at "
              + joinList(lipomatosis)
              + (narrows ? " contributes to spinal canal stenosis." : "."));
    }
    for (JsonNode node : answer.path("postsurgical")) {
      postsurgical(node).ifPresent(sentences::add);
    }
    List<String> cysts =
        SEGMENTS.stream().filter(strings(answer.path("perineural_cysts"))::contains).toList();
    if (!cysts.isEmpty()) {
      sentences.add(
          (cysts.size() == 1 ? "Perineural cyst at " : "Perineural cysts at ")
              + joinList(cysts)
              + ".");
    }
    if (answer.path("skeletally_immature").asBoolean(false)) {
      sentences.add("The patient is skeletally immature.");
    }
    return List.copyOf(sentences);
  }

  /** The radiologist's limitation phrases; metal artifact is already stated with a fusion. */
  static List<String> limitations(JsonNode answer) {
    boolean fusion = false;
    for (JsonNode node : answer.path("postsurgical")) {
      fusion |= "posterior_fusion".equals(text(node, "procedure"));
    }
    List<String> limitations = new ArrayList<>();
    for (JsonNode node : answer.path("limitations")) {
      String detail = ComposerText.clean(node.path("detail").asText("")).replaceAll("\\.+$", "");
      String limitation =
          switch (text(node, "kind")) {
            case "motion" -> "Limited exam due to motion.";
            case "artifact" ->
                detail.isEmpty()
                    ? "The study is limited due to artifact."
                    : "Artifact limits evaluation of " + lowercaseFirst(detail) + ".";
            case "metal_artifact" ->
                fusion
                    ? ""
                    : "Metallic susceptibility artifact limits evaluation of surrounding structures.";
            default -> detail.isEmpty() ? "" : sentence(detail);
          };
      if (!limitation.isEmpty() && !limitations.contains(limitation)) {
        limitations.add(limitation);
      }
    }
    return List.copyOf(limitations);
  }

  private static Optional<String> vertebralBody(JsonNode node) {
    String vertebra = text(node, "vertebra").toUpperCase(Locale.ROOT);
    if (!VERTEBRAE.contains(vertebra)) {
      return Optional.empty();
    }
    String edema = node.path("marrow_edema").asBoolean(false) ? " with marrow edema" : "";
    String severity = adjective(text(node, "severity"));
    return switch (text(node, "finding")) {
      case "anterior_wedging" ->
          Optional.of(sentence(severity + "anterior wedging of " + vertebra + edema));
      case "compression_deformity" ->
          Optional.of(sentence(severity + "compression deformity of " + vertebra + edema));
      case "fracture" ->
          Optional.of(
              sentence(
                  (node.path("displaced").asBoolean(false) ? "displaced " : "")
                      + "fracture of "
                      + vertebra
                      + edema));
      case "hemangioma" ->
          Optional.of(
              "T2 hyperintense lesion in the "
                  + vertebra
                  + " vertebral body, likely an intraosseous hemangioma.");
      default -> Optional.empty();
    };
  }

  private static Optional<String> parsDefect(JsonNode node) {
    String vertebra = text(node, "vertebra").toUpperCase(Locale.ROOT);
    String side = text(node, "side");
    if (!VERTEBRAE.contains(vertebra) || !List.of("left", "right", "bilateral").contains(side)) {
      return Optional.empty();
    }
    String defect =
        side + " " + vertebra + ("bilateral".equals(side) ? " pars defects" : " pars defect");
    return Optional.of(
        sentence(("possible".equals(text(node, "certainty")) ? "possible " : "") + defect));
  }

  private static Optional<String> postsurgical(JsonNode node) {
    List<String> levels = levels(node.path("levels"));
    if (levels.isEmpty()) {
      return Optional.empty();
    }
    String at = " at " + joinList(levels);
    String side = text(node, "side");
    String sided = List.of("left", "right", "bilateral").contains(side) ? side + " " : "";
    return switch (text(node, "procedure")) {
      case "posterior_fusion" ->
          Optional.of(
              "Postsurgical changes of posterior decompression and instrumented fusion"
                  + at
                  + ", with "
                  + METAL_ARTIFACT
                  + ".");
      case "laminectomy" -> Optional.of(sentence("laminectomy" + at));
      case "hemilaminectomy" -> Optional.of(sentence(sided + "hemilaminectomy" + at));
      case "discectomy" ->
          Optional.of(sentence("postsurgical changes of " + sided + "discectomy" + at));
      default -> Optional.empty();
    };
  }

  private static String adjective(String severity) {
    return List.of("mild", "moderate", "severe").contains(severity) ? severity + " " : "";
  }

  private static List<String> levels(JsonNode values) {
    List<String> requested =
        strings(values).stream().map(value -> value.toUpperCase(Locale.ROOT)).toList();
    return LEVELS.stream().filter(requested::contains).toList();
  }

  private static List<String> strings(JsonNode values) {
    List<String> strings = new ArrayList<>();
    values.forEach(value -> strings.add(value.asText("").strip()));
    return strings;
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isTextual() ? value.asText().strip().toLowerCase(Locale.ROOT) : "";
  }

  private static String sentence(String text) {
    return ComposerText.sentence(
        text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1));
  }

  private static String lowercaseFirst(String value) {
    return value.isEmpty() ? value : Character.toLowerCase(value.charAt(0)) + value.substring(1);
  }

  static String joinList(List<String> values) {
    return switch (values.size()) {
      case 0 -> "";
      case 1 -> values.getFirst();
      case 2 -> values.getFirst() + " and " + values.getLast();
      default ->
          String.join(", ", values.subList(0, values.size() - 1)) + ", and " + values.getLast();
    };
  }
}
