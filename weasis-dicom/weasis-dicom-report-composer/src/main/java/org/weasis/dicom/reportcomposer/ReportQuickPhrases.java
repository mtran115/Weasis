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

import java.util.List;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.ExamTemplate;

/**
 * Curated reusable wording from local reading patterns, without case identifiers or measurements.
 */
final class ReportQuickPhrases {
  record Phrase(String label, String text) {}

  static final Phrase MOTION = phrase("Motion limit", "Limited exam due to motion.");
  static final Phrase ARTIFACT =
      phrase("Artifact limit", "Artifact limits evaluation of [area/sequence].");
  static final List<Phrase> COMMON_MORE =
      List.of(
          phrase(
              "Metal artifact",
              "Metallic susceptibility artifact limits evaluation of surrounding structures."),
          phrase("Limited sequence", "The [sequence] sequence is limited due to artifact."));

  private ReportQuickPhrases() {}

  static List<Phrase> forExam(ExamTemplate exam) {
    return switch (exam) {
      case KNEE ->
          List.of(
              phrase("Prepatellar edema", "Mild prepatellar soft tissue edema."),
              phrase(
                  "Contusion / OA",
                  "Mild bone marrow edema involving the [location] may be related to bony contusion or osteoarthrosis."),
              phrase("Condyle friction", "Lateral femoral condyle friction changes."),
              phrase(
                  "Joint effusion",
                  "Small knee joint effusion, nonspecific and potentially inflammatory."),
              phrase("Pretibial edema", "Mild pretibial soft tissue edema."),
              phrase("Quad tendinosis", "Mild quadriceps tendinosis."),
              phrase("Prepatellar bursitis", "Mild prepatellar bursitis."),
              phrase("Popliteal cyst", "Small popliteal cyst."),
              phrase(
                  "Insertional traction",
                  "Cystic change and edema at the insertion of the [tendon/ligament] on the [bone], related to traction."),
              phrase(
                  "ACL reconstruction",
                  "Postsurgical changes of ACL reconstruction with metallic susceptibility artifact limiting evaluation of surrounding structures."));
      case ANKLE ->
          List.of(
              phrase("Post tib tenosyn", "Minimal posterior tibial tenosynovitis."),
              phrase(
                  "Post joint fluid",
                  "Small posterior ankle joint effusion, nonspecific and potentially inflammatory."),
              phrase("ATFL sprain", "Mild sprain of the anterior talofibular ligament."),
              phrase("AITFL sprain", "Mild sprain of the anterior inferior tibiofibular ligament."),
              phrase("Peroneal tenosyn", "Mild tenosynovitis of the [peroneal tendon]."),
              phrase("Soft tissue edema", "Soft tissue swelling along the [location] ankle."),
              phrase(
                  "Anterior joint fluid",
                  "Small anterior ankle joint effusion, nonspecific and potentially inflammatory."),
              phrase(
                  "AITFL partial tear",
                  "Partial tear of the anterior inferior tibiofibular ligament."),
              phrase(
                  "Contusion / reactive edema",
                  "Bone marrow edema of the [bone] could be related to bony contusion or reactive edema."));
      case LUMBAR_SPINE ->
          List.of(
              phrase("Lumbarized S1", "Transitional anatomy with partial lumbarization of S1."),
              phrase("Sacralized L5", "Transitional anatomy with partial sacralization of L5."),
              phrase("L5 pars defect", "L5 pars defect."),
              phrase(
                  "Dorsal lipo",
                  "Dorsal epidural lipomatosis at [levels] contributes to spinal canal stenosis."),
              phrase(
                  "Hemangioma",
                  "T2 hyperintense lesion in the [level] vertebral body, likely an intraosseous hemangioma."),
              phrase("Compression", "Mild compression deformity of [level]."),
              phrase("Possible pars defect", "Possible L5 pars defect."),
              phrase(
                  "Posterior fusion",
                  "Postsurgical changes of posterior decompression and instrumented fusion at [levels], with metallic susceptibility artifact limiting evaluation of surrounding structures."),
              phrase("Hemilaminectomy", "Hemilaminectomy at [level]."),
              phrase("Lateral recess", "Narrowing of the [side] lateral recess at [level]."),
              phrase("Perineural cysts", "Small perineural cysts at S1 and S2."));
      case CERVICAL_SPINE ->
          List.of(
              phrase(
                  "ACDF changes",
                  "Postsurgical changes of ACDF at [levels], with metallic susceptibility artifact limiting evaluation of surrounding structures."),
              phrase("Sag T2 limited", "Sagittal T2 sequence is limited due to artifact."),
              phrase(
                  "PLL thickening",
                  "Thickening of the posterior longitudinal ligament at [levels] causes [degree] spinal canal stenosis."),
              phrase(
                  "Congenital fusion",
                  "Congenital fusion of the [levels] vertebral bodies and posterior elements."),
              phrase("Oral artifact", "Oral cavity artifact obscures portions of [levels]."),
              phrase(
                  "Lipid-poor hemang",
                  "T2 hyperintense lesion in the [level] vertebral body is nonspecific and could represent a lipid-poor hemangioma."));
      case THORACIC_SPINE ->
          List.of(
              phrase("Mid/lower spondylosis", "Spondylosis of the mid to lower thoracic spine."));
      case SHOULDER ->
          List.of(
              phrase(
                  "Insertion traction",
                  "Cystic changes at the insertion of the [tendon], related to traction."),
              phrase("Cuff repair", "Postsurgical changes related to rotator cuff repair."),
              phrase("Cuff atrophy", "Atrophy of the [muscles] muscles."),
              phrase(
                  "Tear + tendinosis",
                  "Partial tear of the [tendon] on a background of tendinosis."),
              phrase(
                  "Ant supraspinatus",
                  "Tear of the anterior fibers of the supraspinatus at the footprint."),
              phrase(
                  "Paralabral cyst",
                  "A [size] [location] paralabral cyst suggests an occult tear of the adjacent labrum."),
              phrase(
                  "Insertional enthesitis",
                  "Bone marrow edema at the insertion of the [tendon], related to enthesitis."),
              phrase("Diffuse labral tear", "Diffuse tearing of the labrum."),
              phrase("AC ligament tear", "Partial tear of the acromioclavicular ligament."));
      case WRIST ->
          List.of(
              phrase("Lunate contusion", "Mild bony contusion of the lunate."),
              phrase("Carpal cyst", "A [size] cyst in the [carpal bone]."),
              phrase("Cystic changes", "Minimal cystic changes of the [carpal bones]."),
              phrase(
                  "Dorsal ganglion",
                  "A [size] ganglion or synovial cyst along the dorsal wrist at the level of [location]."),
              phrase("Extensor tenosyn", "Mild tenosynovitis of the [extensor tendons]."),
              phrase("Scaphoid contusion", "Minimal bony contusion of the scaphoid."),
              phrase(
                  "ECU split tear",
                  "Split tear of the extensor carpi ulnaris on a background of tendinosis."));
      case BRAIN ->
          List.of(
              phrase("Diffusion limited", "Diffusion sequences are limited due to artifact."),
              phrase("Sinus cyst", "A [size] mucous retention cyst in the [sinus name] sinus."),
              phrase(
                  "Sinus thickening",
                  "Mucosal thickening in the [side] maxillary sinus. Correlate for sinusitis."),
              phrase(
                  "Sinus fluid",
                  "Trace fluid in the [side] maxillary sinus. Correlate for sinusitis."),
              phrase(
                  "Skull base artifact", "Anterior skull base evaluation is limited by artifact."));
      case HIP ->
          List.of(
              phrase("Troch bursitis", "Minimal [side] greater trochanteric bursitis."),
              phrase(
                  "Paralabral cyst",
                  "A [size] [side] paralabral cyst suggests an underlying labral tear."),
              phrase(
                  "Acetabular cysts",
                  "Subchondral cystic change of the [side] acetabulum, related to osteoarthrosis."),
              phrase(
                  "Gluteus med tear",
                  "Partial tear of the [side] gluteus medius tendon insertion on the greater trochanter."));
      case ELBOW -> List.of(phrase("Extensor tendinosis", "Mild common extensor tendinosis."));
      case FOOT ->
          List.of(
              phrase(
                  "Metatarsal contusion", "Mild bony contusion of the [number] metatarsal head."),
              phrase("FHL tenosyn", "Mild tenosynovitis of the flexor hallucis longus tendon."),
              phrase("Dorsal swelling", "Soft tissue swelling along the dorsal forefoot."));
      case HAND ->
          List.of(
              phrase(
                  "Dorsal ganglion",
                  "A [size] ganglion or synovial cyst along the dorsal hand at the level of the [joint] joint."));
      case GENERAL -> List.of();
    };
  }

  private static Phrase phrase(String label, String text) {
    return new Phrase(label, text);
  }
}
