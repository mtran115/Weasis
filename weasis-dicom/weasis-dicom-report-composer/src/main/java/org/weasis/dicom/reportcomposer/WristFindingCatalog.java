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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WristFindingCatalog {
  private static final Map<String, Category> CATEGORIES = buildCategories();

  private WristFindingCatalog() {}

  public static List<String> categories() {
    return List.copyOf(CATEGORIES.keySet());
  }

  public static List<String> structures(String category) {
    Category value = CATEGORIES.get(category);
    return value == null ? List.of() : value.structures();
  }

  public static List<FindingChoice> findings(String category) {
    Category value = CATEGORIES.get(category);
    return value == null ? List.of() : value.findings();
  }

  public static GeneratedFinding generate(
      String category, String structure, FindingChoice findingChoice) {
    if (findingChoice == null || !ComposerText.hasText(structure)) {
      return new GeneratedFinding("", "");
    }
    String finding = findingChoice.findingTemplate().replace("{structure}", structure);
    String impression = findingChoice.impressionTemplate().replace("{structure}", structure);
    return new GeneratedFinding(ComposerText.sentence(finding), ComposerText.sentence(impression));
  }

  private static Map<String, Category> buildCategories() {
    Map<String, Category> categories = new LinkedHashMap<>();
    categories.put(
        "Intrinsic ligaments",
        new Category(
            List.of("scapholunate ligament", "lunotriquetral ligament"),
            List.of(
                choice("Intact", "The {structure} is intact", ""),
                choice("Sprain", "Sprain of the {structure}", "{structure} sprain"),
                choice(
                    "Partial-thickness tear",
                    "Partial-thickness tear of the {structure}",
                    "Partial-thickness {structure} tear"),
                choice(
                    "Full-thickness tear",
                    "Full-thickness tear of the {structure}",
                    "Full-thickness {structure} tear"),
                choice("Tear", "Tear of the {structure}", "{structure} tear"))));
    categories.put(
        "TFCC",
        new Category(
            List.of("triangular fibrocartilage complex"),
            List.of(
                choice("Intact", "The {structure} is intact", ""),
                choice("Degeneration", "Degeneration of the {structure}", "TFCC degeneration"),
                choice(
                    "Central perforation",
                    "Central perforation of the {structure}",
                    "Central TFCC perforation"),
                choice(
                    "Peripheral tear",
                    "Peripheral tear of the {structure}",
                    "Peripheral TFCC tear"),
                choice("Tear", "Tear of the {structure}", "TFCC tear"))));
    categories.put(
        "Tendons",
        new Category(
            List.of(
                "extensor carpi ulnaris tendon",
                "first extensor compartment tendons",
                "remaining extensor tendons",
                "flexor tendons",
                "flexor carpi radialis tendon",
                "flexor carpi ulnaris tendon"),
            List.of(
                choice("Intact", "The {structure} is intact", ""),
                choice("Tendinosis", "Tendinosis of the {structure}", "{structure} tendinosis"),
                choice(
                    "Tenosynovitis",
                    "Tenosynovitis of the {structure}",
                    "{structure} tenosynovitis"),
                choice(
                    "Partial-thickness tear",
                    "Partial-thickness tear of the {structure}",
                    "Partial-thickness {structure} tear"),
                choice(
                    "Full-thickness tear",
                    "Full-thickness tear of the {structure}",
                    "Full-thickness {structure} tear"),
                choice(
                    "Subluxation", "Subluxation of the {structure}", "{structure} subluxation"))));
    categories.put(
        "Bones",
        new Category(
            List.of(
                "distal radius",
                "distal ulna",
                "scaphoid",
                "lunate",
                "triquetrum",
                "remaining carpal bones"),
            List.of(
                choice("Normal marrow signal", "Normal marrow signal in the {structure}", ""),
                choice(
                    "Marrow edema", "Marrow edema in the {structure}", "{structure} marrow edema"),
                choice("Fracture", "Fracture of the {structure}", "{structure} fracture"),
                choice(
                    "Subchondral cyst",
                    "Subchondral cystic change in the {structure}",
                    "Subchondral cystic change in the {structure}"),
                choice(
                    "Osteonecrosis",
                    "Osteonecrosis of the {structure}",
                    "Osteonecrosis of the {structure}"))));
    categories.put(
        "Joints and cartilage",
        new Category(
            List.of(
                "radiocarpal joint",
                "midcarpal joint",
                "distal radioulnar joint",
                "first carpometacarpal joint"),
            List.of(
                choice("Preserved cartilage", "Cartilage is preserved at the {structure}", ""),
                choice("Chondrosis", "Chondrosis of the {structure}", "{structure} chondrosis"),
                choice(
                    "Full-thickness chondral loss",
                    "Full-thickness chondral loss at the {structure}",
                    "Full-thickness chondral loss at the {structure}"),
                choice(
                    "Joint effusion", "Joint effusion at the {structure}", "{structure} effusion"),
                choice("Synovitis", "Synovitis at the {structure}", "{structure} synovitis"))));
    categories.put(
        "Nerves",
        new Category(
            List.of("median nerve", "ulnar nerve"),
            List.of(
                choice("Normal", "The {structure} is normal in signal and caliber", ""),
                choice("Enlarged", "Enlargement of the {structure}", "Enlarged {structure}"),
                choice(
                    "Increased signal",
                    "Increased signal in the {structure}",
                    "Abnormal signal in the {structure}"))));
    categories.put(
        "Other",
        new Category(
            List.of("dorsal wrist", "volar wrist", "carpal tunnel", "soft tissues"),
            List.of(
                choice(
                    "Ganglion cyst",
                    "Ganglion cyst at the {structure}",
                    "Ganglion cyst at the {structure}"),
                choice(
                    "Soft-tissue edema",
                    "Soft-tissue edema at the {structure}",
                    "Soft-tissue edema at the {structure}"),
                choice("Mass", "Mass at the {structure}", "Mass at the {structure}"))));
    return Collections.unmodifiableMap(categories);
  }

  private static FindingChoice choice(String label, String finding, String impression) {
    return new FindingChoice(label, finding, impression);
  }

  private record Category(List<String> structures, List<FindingChoice> findings) {}

  public record FindingChoice(String label, String findingTemplate, String impressionTemplate) {
    @Override
    public String toString() {
      return label;
    }
  }

  public record GeneratedFinding(String findingText, String impressionText) {}
}
