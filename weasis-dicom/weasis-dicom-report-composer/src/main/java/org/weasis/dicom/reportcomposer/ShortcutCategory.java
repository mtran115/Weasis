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
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** What a shortcut is for; the body part is already its scope. Groups the More… menu. */
enum ShortcutCategory {
  LIMITATIONS("Limitations"),
  NORMAL_VARIANTS("Normal & variants"),
  BONE_MARROW("Bone & marrow"),
  TENDONS_LIGAMENTS("Tendons, ligaments & muscle"),
  JOINT_DEGENERATIVE("Joint & degenerative"),
  POSTOP_HARDWARE("Post-op & hardware"),
  SOFT_TISSUE_INCIDENTAL("Soft tissue & incidental");

  /** The stored category of a shortcut that has not been sorted. */
  static final String NONE = "";

  private record Rule(ShortcutCategory category, Pattern words) {}

  // Checked in order. Post-op wording comes first because it often mentions metal artifact, and
  // named findings (a ganglion, a tendon) win over generic words (cyst, edema, tear) checked last.
  private static final List<Rule> RULES =
      List.of(
          rule(
              POSTOP_HARDWARE,
              "post-?surgical|post-?operative|post-?op\\b|\\brepair|reconstruction|arthroplasty"
                  + "|hardware|instrumented|(?<!congenital )\\bfusion|\\bacdf\\b|\\w+ectomy\\b"
                  + "|tenodesis|\\bgraft|\\banchors?\\b|\\bscrews?\\b|prosthe"),
          rule(
              LIMITATIONS,
              "artifact|\\bmotion\\b|degraded|suboptimal|nondiagnostic|obscur"
                  + "|\\blimit(s|ed|ing|ation)?\\b"),
          rule(
              NORMAL_VARIANTS,
              "\\bnormal\\b|unremarkable|\\bvariant|transitional|lumbariz|sacraliz"
                  + "|skeletally immature|red marrow|congenital|physiolog"),
          rule(
              SOFT_TISSUE_INCIDENTAL,
              "lipomatosis|\\blipo\\b|thyroid|\\bsinus|\\bliver\\b|hepatic|\\brenal\\b|kidney"
                  + "|perineural|tarlov|ganglion|syrinx|incidental|lymph node|mucous"
                  + "|fat pad|infrapatellar fat|hoffa"),
          rule(
              TENDONS_LIGAMENTS,
              "\\btend(on|in)|tenosyn|enthes|ligament|\\blig\\b|sprain|strain|muscle|muscul"
                  + "|\\bcuff\\b|supraspinatus|infraspinatus|subscap|\\bteres\\b|biceps|triceps"
                  + "|\\bquad|hamstring|glute|peroneal|tibialis|post tib|posterior tibial|\\bfhl\\b"
                  + "|flexor|extensor|\\becu\\b|\\b(acl|pcl|mcl|lcl|atfl|aitfl|cfl|tfcc|pll)\\b"
                  + "|scapholunate|lunotriquetral|plantar fasci|insertion|traction"),
          rule(
              BONE_MARROW,
              "contusion|fracture|wedging|compression|\\bpars\\b|spondylolysis|marrow"
                  + "|\\bbon(e|es|y)\\b|osseous|hemang|enchondroma|osteonecrosis|avascular"
                  + "|stress reaction"),
          rule(
              JOINT_DEGENERATIVE,
              "effusion|joint|osteoarthr|arthrosis|arthrit|\\boa\\b|chondr|cartilage|friction"
                  + "|labr(um|al)|menisc|\\bhorn\\b|spondylosis|endplate|\\bendp\\b|\\bdisc"
                  + "|bulge|protrusion|extrusion|herniat|stenosis|narrowing|recess|foramin|facet"
                  + "|listhesis|scolio|kyphos|\\bdextro|\\blevo|synovi|impinge|subchondral"
                  + "|popliteal|baker"),
          rule(
              SOFT_TISSUE_INCIDENTAL,
              "edema|swelling|bursa|bursitis|\\bcysts?\\b|soft tissue|\\bmass\\b|lesion|nodul"),
          rule(TENDONS_LIGAMENTS, "\\btears?\\b|\\btorn\\b"));

  private final String label;

  ShortcutCategory(String label) {
    this.label = label;
  }

  String label() {
    return label;
  }

  @Override
  public String toString() {
    return label;
  }

  /** The category stored as {@code value}, empty when the shortcut is unsorted. */
  static Optional<ShortcutCategory> of(String value) {
    return value == null || value.isEmpty() ? Optional.empty() : Optional.of(valueOf(value));
  }

  /** A guess from the label and text; empty when no wording fits well enough to file it. */
  static Optional<ShortcutCategory> suggest(String label, String text) {
    String words = (label + " " + text).toLowerCase(Locale.ROOT);
    return RULES.stream()
        .filter(rule -> rule.words().matcher(words).find())
        .map(Rule::category)
        .findFirst();
  }

  private static Rule rule(ShortcutCategory category, String words) {
    return new Rule(category, Pattern.compile(words));
  }
}
