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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AiLumbarExtrasTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void writesFracturesSurgeryAndCurvatureInTheRadiologistsWording() throws Exception {
    var answer =
        JSON.readTree(
            """
            {
              "alignment": {"scoliosis": "s_shaped", "scoliosis_severity": "mild"},
              "transitional_anatomy": "sacralized_l5",
              "vertebral_bodies": [
                {"vertebra": "L5", "finding": "fracture", "severity": "none", "displaced": true,
                 "marrow_edema": true},
                {"vertebra": "L2", "finding": "hemangioma", "severity": "none",
                 "displaced": false, "marrow_edema": false},
                {"vertebra": "L1", "finding": "compression_deformity", "severity": "moderate",
                 "displaced": false, "marrow_edema": false}
              ],
              "pars_defects": [{"vertebra": "L5", "side": "left", "certainty": "definite"}],
              "dorsal_epidural_lipomatosis": ["L5-S1"],
              "postsurgical": [
                {"procedure": "posterior_fusion", "levels": ["L5-S1", "L4-5"], "side": "none"},
                {"procedure": "hemilaminectomy", "levels": ["L3-4"], "side": "right"}
              ],
              "perineural_cysts": ["S2"],
              "skeletally_immature": true
            }
            """);

    assertEquals(
        List.of(
            "Transitional anatomy with partial sacralization of L5.",
            "Mild S-shaped scoliotic curvature of the lumbar spine.",
            "Displaced fracture of L5 with marrow edema.",
            "T2 hyperintense lesion in the L2 vertebral body, likely an intraosseous hemangioma.",
            "Moderate compression deformity of L1.",
            "Left L5 pars defect.",
            "Dorsal epidural lipomatosis at L5-S1.",
            "Postsurgical changes of posterior decompression and instrumented fusion at L4-5 and"
                + " L5-S1, with metallic susceptibility artifact limiting evaluation of"
                + " surrounding structures.",
            "Right hemilaminectomy at L3-4.",
            "Perineural cyst at S2.",
            "The patient is skeletally immature."),
        AiLumbarExtras.sentences(answer, Set.of()));
  }

  @Test
  void limitationsUseTheRadiologistsPhrasesAndDoNotRepeatFusionArtifact() throws Exception {
    var limitations =
        JSON.readTree(
            """
            {"limitations": [
              {"kind": "motion", "detail": "axial images"},
              {"kind": "artifact", "detail": "The axial T2 sequence."},
              {"kind": "artifact", "detail": ""},
              {"kind": "metal_artifact", "detail": ""},
              {"kind": "other", "detail": "Sagittal STIR not performed"},
              {"kind": "motion", "detail": ""}
            ]}
            """);

    assertEquals(
        List.of(
            "Limited exam due to motion.",
            "Artifact limits evaluation of the axial T2 sequence.",
            "The study is limited due to artifact.",
            "Metallic susceptibility artifact limits evaluation of surrounding structures.",
            "Sagittal STIR not performed."),
        AiLumbarExtras.limitations(limitations));

    var withFusion =
        JSON.readTree(
            """
            {"postsurgical": [{"procedure": "posterior_fusion", "levels": ["L4-5"], "side": "none"}],
             "limitations": [{"kind": "metal_artifact", "detail": ""}]}
            """);
    assertEquals(List.of(), AiLumbarExtras.limitations(withFusion));
  }
}
