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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.GeneratedFinding;

class AiLumbarRequestTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void answerFormatsAreValidStrictSchemas() {
    assertStrict(JSON.valueToTree(AiLumbarRequest.outputFormat()).path("schema"), "lumbar");
    assertStrict(JSON.valueToTree(AiPacketExporter.outputFormat()).path("schema"), "generic");
  }

  @Test
  void lumbarFormatOffersOnlySpondylosisAndEveryFormLevel() throws Exception {
    String schema = JSON.writeValueAsString(AiLumbarRequest.outputFormat());

    assertFalse(schema.toLowerCase().contains("degenerat"), schema);
    assertEquals(
        List.of("T12-L1", "L1-2", "L2-3", "L3-4", "L4-5", "L5-S1"),
        JSON.convertValue(
            JSON.readTree(schema).at("/schema/properties/levels/items/properties/level/enum"),
            List.class));
  }

  @Test
  void answerFillsTheSpineFormWhichWritesTheWording() throws Exception {
    AiLumbarRequest.Answer answer = AiLumbarRequest.read(JSON.readTree(SAMPLE));

    List<String> findings =
        SpineFindingBuilder.generate(answer.selection()).stream()
            .map(GeneratedFinding::findingText)
            .toList();

    assertEquals(
        List.of(
            "Straightening of the lumbar lordosis.",
            "3 mm anterolisthesis of L4 on L5.",
            "Lumbar spondylosis at L4-5 and L5-S1.",
            "L4-5: Disc bulge with superimposed left subarticular protrusion. Annular fissure."
                + " Bilateral facet arthrosis. Mild spinal canal stenosis. Mild left neural"
                + " foraminal stenosis. 3 mm protrusion. Narrowing of the left lateral recess."
                + " Displaces the left L5 nerve root. Left facet effusion.",
            "L5-S1: Disc bulge. 1 mm bulge. Endplate edema. Chronic-appearing signal"
                + " in the facets."),
        findings);
    assertEquals(
        Map.of("L4-5", List.of("S3-12", "S1-07"), "L5-S1", List.of("S1-08")),
        answer.imagesByLevel());
    assertEquals(
        List.of(
            "Transitional anatomy with partial lumbarization of S1.",
            "Mild anterior wedging of L1.",
            "Possible bilateral L5 pars defects.",
            "Reactive endplate changes at L4-5 and L5-S1.",
            "Dorsal epidural lipomatosis at L4-5 contributes to spinal canal stenosis.",
            "Perineural cysts at S1 and S2.",
            "Small hemangioma in the L2 vertebral body."),
        answer.additionalFindings());
    assertEquals(List.of("Limited exam due to motion."), answer.limitations());
    assertEquals(List.of("L5-S1 uses chronic"), answer.warnings());
    assertEquals(Map.of("L4-5", "high", "L5-S1", "medium"), answer.confidenceByLevel());
  }

  @Test
  void generalizedSpondylosisAndEmptyAnswersStayValid() throws Exception {
    AiLumbarRequest.Answer answer =
        AiLumbarRequest.read(
            JSON.readTree("{\"spondylosis\": {\"extent\": \"generalized\", \"levels\": []}}"));

    assertEquals(
        List.of("Lumbar spondylosis."),
        SpineFindingBuilder.generate(answer.selection()).stream()
            .map(GeneratedFinding::findingText)
            .toList());
    assertTrue(AiLumbarRequest.read(JSON.readTree("{}")).selection().levelSelections().isEmpty());
  }

  @Test
  void normalStatementsAreDroppedAndDiscDesiccationIsFlagged() throws Exception {
    assertEquals(
        "Small osteophyte. Left foraminal disc material",
        AiLumbarRequest.withoutNormalStatements(
            "No nerve root contact. Small osteophyte; normal conus. Left foraminal disc material."));

    AiLumbarRequest.Answer answer =
        AiLumbarRequest.read(
            JSON.readTree(
                """
                {"levels": [{"level": "L4-5", "bulge": true,
                             "other": "Disc desiccation. No stenosis."}],
                 "other_findings": [{"finding": "Normal conus."}]}
                """));

    assertEquals(
        List.of("L4-5: Disc bulge. Disc desiccation."),
        SpineFindingBuilder.generate(answer.selection()).stream()
            .map(GeneratedFinding::findingText)
            .toList());
    assertEquals(List.of(), answer.additionalFindings());
    assertEquals(List.of("L4-5 uses desiccation"), answer.warnings());
  }

  @Test
  void mapLevelsUseTheFormsNames() {
    assertEquals("L4-5", LumbarLevelMap.formLevel("L4-L5"));
    assertEquals("T12-L1", LumbarLevelMap.formLevel("T12-L1"));
    assertEquals("L5-S1", LumbarLevelMap.formLevel("L5-S1"));
    assertEquals("T11-12", LumbarLevelMap.formLevel("T11-T12"));
  }

  // Strict structured output needs every object closed and every property required.
  private static void assertStrict(JsonNode schema, String path) {
    if ("object".equals(schema.path("type").asText())) {
      assertFalse(schema.path("additionalProperties").asBoolean(true), path);
      List<String> properties = List.copyOf(fieldNames(schema.path("properties")));
      assertEquals(
          java.util.Set.copyOf(properties),
          java.util.Set.copyOf(JSON.convertValue(schema.path("required"), List.class)),
          path);
      schema
          .path("properties")
          .fields()
          .forEachRemaining(e -> assertStrict(e.getValue(), path + "." + e.getKey()));
    } else if ("array".equals(schema.path("type").asText())) {
      assertStrict(schema.path("items"), path + "[]");
    }
  }

  private static List<String> fieldNames(JsonNode node) {
    List<String> names = new java.util.ArrayList<>();
    node.fieldNames().forEachRemaining(names::add);
    return names;
  }

  private static final String NORMAL_LEVEL =
      """
      "bulge": false, "herniation": "none", "herniation_locations": [], "disc_size_mm": null,
      "migration": "none", "migration_mm": null, "annular_fissure": false,
      "ventral_epidural_lipomatosis": false, "facet_arthrosis": "none", "facet_effusion": "none",
      "ligamentum_flavum_hypertrophy": "none", "endplate_edema": false,
      "spinal_canal_stenosis": "none", "left_foraminal_stenosis": "none",
      "right_foraminal_stenosis": "none", "lateral_recess_narrowing": "none", "nerve_roots": [],
      "other": "", "image_ids": [], "confidence": "high"
      """;

  private static final String SAMPLE =
      """
      {
        "alignment": {"lordosis": "straightened", "scoliosis": "none",
                      "scoliosis_severity": "none", "scoliosis_degrees": null},
        "listheses": [{"level": "L4-5", "direction": "anterolisthesis", "displacement_mm": 3,
                       "image_ids": ["S1-07"]}],
        "spondylosis": {"extent": "specific_levels", "levels": ["L5-S1", "L4-5"]},
        "levels": [
          {"level": "T12-L1", %1$s},
          {"level": "L4-5", "bulge": true, "herniation": "protrusion",
           "herniation_locations": ["left_subarticular"], "disc_size_mm": 3, "migration": "none",
           "migration_mm": null, "annular_fissure": true, "ventral_epidural_lipomatosis": false,
           "facet_arthrosis": "bilateral", "facet_effusion": "left",
           "ligamentum_flavum_hypertrophy": "none", "endplate_edema": false,
           "spinal_canal_stenosis": "mild", "left_foraminal_stenosis": "mild",
           "right_foraminal_stenosis": "none", "lateral_recess_narrowing": "left",
           "nerve_roots": [{"side": "left", "root": "L5", "effect": "displaces"}], "other": "",
           "image_ids": ["S3-12", "S1-07"], "confidence": "high"},
          {"level": "L5-S1", "bulge": true, "herniation": "none", "herniation_locations": [],
           "disc_size_mm": 1, "migration": "none", "migration_mm": null,
           "annular_fissure": false, "ventral_epidural_lipomatosis": false,
           "facet_arthrosis": "none", "facet_effusion": "none",
           "ligamentum_flavum_hypertrophy": "none", "endplate_edema": true,
           "spinal_canal_stenosis": "none", "left_foraminal_stenosis": "none",
           "right_foraminal_stenosis": "none", "lateral_recess_narrowing": "none",
           "nerve_roots": [], "other": "Chronic-appearing signal in the facets.",
           "image_ids": ["S1-08"], "confidence": "medium"},
          {"level": "L4-5", %1$s}
        ],
        "transitional_anatomy": "lumbarized_s1",
        "reactive_endplate_changes": ["L5-S1", "L4-5"],
        "vertebral_bodies": [{"vertebra": "L1", "finding": "anterior_wedging", "severity": "mild",
                              "displaced": false, "marrow_edema": false, "image_ids": ["S1-06"]}],
        "pars_defects": [{"vertebra": "L5", "side": "bilateral", "certainty": "possible",
                          "image_ids": ["S1-02"]}],
        "dorsal_epidural_lipomatosis": ["L4-5"],
        "postsurgical": [],
        "perineural_cysts": ["S2", "S1"],
        "skeletally_immature": false,
        "other_findings": [{"finding": "small hemangioma in the L2 vertebral body",
                            "image_ids": ["S1-05"], "confidence": "high"}],
        "limitations": [{"kind": "motion", "detail": ""}]
      }
      """
          .formatted(NORMAL_LEVEL);
}
