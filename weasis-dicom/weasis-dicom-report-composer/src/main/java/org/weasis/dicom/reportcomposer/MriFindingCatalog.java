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
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public final class MriFindingCatalog {
  /** Bump when structured finding categories, selections, or their meaning change. */
  public static final String CATALOG_VERSION = "mri_findings_v1";

  private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^A-Z0-9]+");

  private static final List<String> CERVICAL_LEVELS =
      List.of("C2-3", "C3-4", "C4-5", "C5-6", "C6-7", "C7-T1");
  private static final List<String> THORACIC_LEVELS =
      List.of(
          "T1-2", "T2-3", "T3-4", "T4-5", "T5-6", "T6-7", "T7-8", "T8-9", "T9-10", "T10-11",
          "T11-12", "T12-L1");
  private static final List<String> LUMBAR_LEVELS =
      List.of("T12-L1", "L1-2", "L2-3", "L3-4", "L4-5", "L5-S1");

  private static final Map<ExamTemplate, Map<String, Category>> CATALOGS = buildCatalogs();

  private MriFindingCatalog() {}

  public static List<ExamTemplate> exams() {
    return List.of(ExamTemplate.values());
  }

  public static ExamTemplate defaultExam() {
    return ExamTemplate.GENERAL;
  }

  public static List<String> categories(ExamTemplate exam) {
    Map<String, Category> catalog = CATALOGS.get(exam);
    return catalog == null ? List.of() : List.copyOf(catalog.keySet());
  }

  public static List<String> structures(ExamTemplate exam, String category) {
    Category value = category(exam, category);
    return value == null ? List.of() : value.structures();
  }

  public static List<FindingChoice> findings(ExamTemplate exam, String category) {
    Category value = category(exam, category);
    return value == null ? List.of() : value.findings();
  }

  public static GeneratedFinding generate(
      ExamTemplate exam, String category, String structure, FindingChoice findingChoice) {
    if (findingChoice == null
        || !ComposerText.hasText(structure)
        || !findings(exam, category).contains(findingChoice)) {
      return new GeneratedFinding("", "");
    }
    String finding = findingChoice.findingTemplate().replace("{structure}", structure);
    String impression = findingChoice.impressionTemplate().replace("{structure}", structure);
    return new GeneratedFinding(ComposerText.sentence(finding), ComposerText.sentence(impression));
  }

  static Optional<ExamTemplate> inferExam(String studyDescription) {
    String normalized =
        " "
            + NON_ALPHANUMERIC
                .matcher(ComposerText.clean(studyDescription).toUpperCase(Locale.ROOT))
                .replaceAll(" ")
                .strip()
            + " ";
    if (containsAny(normalized, " CERVICAL ", " C SPINE ", " CSPINE ")) {
      return Optional.of(ExamTemplate.CERVICAL_SPINE);
    }
    if (containsAny(normalized, " THORACIC ", " T SPINE ", " TSPINE ")) {
      return Optional.of(ExamTemplate.THORACIC_SPINE);
    }
    if (containsAny(normalized, " LUMBAR ", " L SPINE ", " LSPINE ")) {
      return Optional.of(ExamTemplate.LUMBAR_SPINE);
    }
    if (normalized.contains(" BRAIN ")) {
      return Optional.of(ExamTemplate.BRAIN);
    }
    if (normalized.contains(" SHOULDER ")) {
      return Optional.of(ExamTemplate.SHOULDER);
    }
    if (normalized.contains(" ELBOW ")) {
      return Optional.of(ExamTemplate.ELBOW);
    }
    if (normalized.contains(" WRIST ")) {
      return Optional.of(ExamTemplate.WRIST);
    }
    if (normalized.contains(" HAND ")) {
      return Optional.of(ExamTemplate.HAND);
    }
    if (normalized.contains(" HIP ")) {
      return Optional.of(ExamTemplate.HIP);
    }
    if (normalized.contains(" KNEE ")) {
      return Optional.of(ExamTemplate.KNEE);
    }
    if (normalized.contains(" ANKLE ")) {
      return Optional.of(ExamTemplate.ANKLE);
    }
    if (normalized.contains(" FOOT ")) {
      return Optional.of(ExamTemplate.FOOT);
    }
    return Optional.empty();
  }

  private static boolean containsAny(String value, String... candidates) {
    for (String candidate : candidates) {
      if (value.contains(candidate)) {
        return true;
      }
    }
    return false;
  }

  private static Category category(ExamTemplate exam, String category) {
    Map<String, Category> catalog = CATALOGS.get(exam);
    return catalog == null ? null : catalog.get(category);
  }

  private static Map<ExamTemplate, Map<String, Category>> buildCatalogs() {
    Map<ExamTemplate, Map<String, Category>> catalogs = new EnumMap<>(ExamTemplate.class);
    catalogs.put(ExamTemplate.GENERAL, generalCatalog());
    catalogs.put(ExamTemplate.BRAIN, brainCatalog());
    catalogs.put(ExamTemplate.CERVICAL_SPINE, cervicalSpineCatalog());
    catalogs.put(ExamTemplate.THORACIC_SPINE, thoracicSpineCatalog());
    catalogs.put(ExamTemplate.LUMBAR_SPINE, lumbarSpineCatalog());
    catalogs.put(ExamTemplate.SHOULDER, shoulderCatalog());
    catalogs.put(ExamTemplate.ELBOW, elbowCatalog());
    catalogs.put(ExamTemplate.WRIST, wristCatalog());
    catalogs.put(ExamTemplate.HAND, handCatalog());
    catalogs.put(ExamTemplate.HIP, hipCatalog());
    catalogs.put(ExamTemplate.KNEE, kneeCatalog());
    catalogs.put(ExamTemplate.ANKLE, ankleCatalog());
    catalogs.put(ExamTemplate.FOOT, footCatalog());
    return Collections.unmodifiableMap(catalogs);
  }

  private static Map<String, Category> generalCatalog() {
    return catalog(
        definition("Bones", List.of("selected bone"), boneFindings()),
        definition("Tendons", List.of("selected tendon"), tendonFindings()),
        definition("Ligaments", List.of("selected ligament"), ligamentFindings()),
        definition("Joints and cartilage", List.of("selected joint"), cartilageFindings()),
        definition(
            "Soft tissues",
            List.of("selected soft-tissue region"),
            choices(
                choice("No abnormality", "No abnormality at the {structure}", ""),
                choice("Edema", "Edema at the {structure}", "Soft-tissue edema"),
                choice(
                    "Fluid collection", "Fluid collection at the {structure}", "Fluid collection"),
                choice("Mass", "Mass at the {structure}", "Soft-tissue mass"))));
  }

  private static Map<String, Category> cervicalSpineCatalog() {
    return catalog(
        definition("Discs", CERVICAL_LEVELS, spineDiscFindings()),
        definition("Spinal canal", CERVICAL_LEVELS, spineCanalFindings()),
        definition("Neural foramina", CERVICAL_LEVELS, spineForaminalFindings()),
        definition("Facets", CERVICAL_LEVELS, facetFindings()),
        definition(
            "Spinal cord",
            List.of(
                "cervical spinal cord",
                "cord at C3-4",
                "cord at C4-5",
                "cord at C5-6",
                "cord at C6-7"),
            cordFindings()),
        definition(
            "Vertebral bodies",
            List.of(
                "C2 vertebral body",
                "C3 vertebral body",
                "C4 vertebral body",
                "C5 vertebral body",
                "C6 vertebral body",
                "C7 vertebral body"),
            spineBoneFindings()),
        definition(
            "Alignment",
            CERVICAL_LEVELS,
            choices(
                choice("Maintained", "Alignment is maintained at {structure}", ""),
                choice(
                    "Anterolisthesis",
                    "Anterolisthesis at {structure}",
                    "Anterolisthesis at {structure}"),
                choice(
                    "Retrolisthesis",
                    "Retrolisthesis at {structure}",
                    "Retrolisthesis at {structure}"),
                choice(
                    "Focal kyphosis",
                    "Focal kyphosis centered at {structure}",
                    "Focal cervical kyphosis"))),
        definition(
            "Curvature",
            List.of("cervical spine"),
            choices(
                choice("Normal lordosis", "Normal cervical lordosis", ""),
                choice(
                    "Straightened lordosis",
                    "Straightening of the cervical lordosis",
                    "Straightened cervical lordosis"),
                choice(
                    "Reversed lordosis",
                    "Reversal of the cervical lordosis",
                    "Reversed cervical lordosis"),
                choice("Scoliosis", "Cervical scoliosis", "Cervical scoliosis"))));
  }

  private static Map<String, Category> thoracicSpineCatalog() {
    return catalog(
        definition("Discs", THORACIC_LEVELS, spineDiscFindings()),
        definition("Spinal canal", THORACIC_LEVELS, spineCanalFindings()),
        definition("Neural foramina", THORACIC_LEVELS, spineForaminalFindings()),
        definition("Facets", THORACIC_LEVELS, facetFindings()),
        definition(
            "Spinal cord",
            List.of(
                "thoracic spinal cord",
                "upper thoracic cord",
                "mid thoracic cord",
                "lower thoracic cord"),
            cordFindings()),
        definition(
            "Vertebral bodies",
            List.of(
                "upper thoracic vertebral bodies",
                "mid thoracic vertebral bodies",
                "lower thoracic vertebral bodies",
                "selected thoracic vertebral body"),
            spineBoneFindings()),
        definition(
            "Alignment",
            THORACIC_LEVELS,
            choices(
                choice("Maintained", "Alignment is maintained at {structure}", ""),
                choice(
                    "Anterolisthesis",
                    "Anterolisthesis at {structure}",
                    "Anterolisthesis at {structure}"),
                choice(
                    "Retrolisthesis",
                    "Retrolisthesis at {structure}",
                    "Retrolisthesis at {structure}"),
                choice(
                    "Focal kyphosis",
                    "Focal kyphosis centered at {structure}",
                    "Focal thoracic kyphosis"))),
        definition(
            "Curvature",
            List.of("thoracic spine"),
            choices(
                choice("Normal kyphosis", "Normal thoracic kyphosis", ""),
                choice(
                    "Exaggerated kyphosis",
                    "Exaggeration of the thoracic kyphosis",
                    "Exaggerated thoracic kyphosis"),
                choice("Scoliosis", "Thoracic scoliosis", "Thoracic scoliosis"))));
  }

  private static Map<String, Category> lumbarSpineCatalog() {
    return catalog(
        definition("Discs", LUMBAR_LEVELS, spineDiscFindings()),
        definition("Spinal canal and recesses", LUMBAR_LEVELS, lumbarCanalFindings()),
        definition("Neural foramina", LUMBAR_LEVELS, spineForaminalFindings()),
        definition("Facets", LUMBAR_LEVELS, facetFindings()),
        definition(
            "Vertebral bodies",
            List.of(
                "L1 vertebral body",
                "L2 vertebral body",
                "L3 vertebral body",
                "L4 vertebral body",
                "L5 vertebral body",
                "S1 vertebral body"),
            spineBoneFindings()),
        definition(
            "Alignment",
            LUMBAR_LEVELS,
            choices(
                choice("Maintained", "Alignment is maintained at {structure}", ""),
                choice(
                    "Anterolisthesis",
                    "Anterolisthesis at {structure}",
                    "Anterolisthesis at {structure}"),
                choice(
                    "Retrolisthesis",
                    "Retrolisthesis at {structure}",
                    "Retrolisthesis at {structure}"),
                choice(
                    "Pars defects",
                    "Bilateral pars defects at {structure}",
                    "Bilateral pars defects at {structure}"))),
        definition(
            "Conus medullaris",
            List.of("conus medullaris"),
            choices(
                choice("Normal", "The {structure} is normal in signal and position", ""),
                choice("Low-lying", "The {structure} is low lying", "Low-lying conus medullaris"),
                choice(
                    "Signal abnormality",
                    "Signal abnormality in the {structure}",
                    "Abnormal conus medullaris signal"))),
        definition(
            "Cauda equina",
            List.of("cauda equina nerve roots"),
            choices(
                choice("Normal", "The {structure} are normal", ""),
                choice(
                    "Crowding",
                    "Crowding of the {structure}",
                    "Crowding of the cauda equina nerve roots"),
                choice(
                    "Clumping",
                    "Clumping of the {structure}",
                    "Clumping of the cauda equina nerve roots"),
                choice(
                    "Compression",
                    "Compression of the {structure}",
                    "Cauda equina nerve-root compression"))));
  }

  private static Map<String, Category> brainCatalog() {
    List<String> regions =
        List.of(
            "right cerebral hemisphere",
            "left cerebral hemisphere",
            "corpus callosum",
            "brainstem",
            "right cerebellar hemisphere",
            "left cerebellar hemisphere");
    return catalog(
        definition(
            "Diffusion",
            regions,
            choices(
                choice("No restricted diffusion", "No restricted diffusion in the {structure}", ""),
                choice(
                    "Acute infarct",
                    "Acute infarct in the {structure}",
                    "Acute infarct in the {structure}"),
                choice(
                    "Subacute infarct",
                    "Subacute infarct in the {structure}",
                    "Subacute infarct in the {structure}"),
                choice(
                    "Restricted diffusion",
                    "Restricted diffusion in the {structure}",
                    "Restricted diffusion in the {structure}"))),
        definition(
            "Hemorrhage and susceptibility",
            regions,
            choices(
                choice("No hemorrhage", "No hemorrhage in the {structure}", ""),
                choice(
                    "Microhemorrhage",
                    "Microhemorrhage in the {structure}",
                    "Microhemorrhage in the {structure}"),
                choice(
                    "Intraparenchymal hemorrhage",
                    "Intraparenchymal hemorrhage in the {structure}",
                    "Intraparenchymal hemorrhage in the {structure}"),
                choice(
                    "Susceptibility focus",
                    "Susceptibility focus in the {structure}",
                    "Susceptibility focus in the {structure}"))),
        definition(
            "Parenchyma",
            regions,
            choices(
                choice("Normal signal", "Normal signal in the {structure}", ""),
                choice("Edema", "Edema in the {structure}", "Edema in the {structure}"),
                choice(
                    "Encephalomalacia and gliosis",
                    "Encephalomalacia and gliosis in the {structure}",
                    "Encephalomalacia and gliosis in the {structure}"),
                choice("Mass", "Mass in the {structure}", "Mass in the {structure}"),
                choice(
                    "Demyelinating lesion",
                    "Demyelinating lesion in the {structure}",
                    "Demyelinating lesion in the {structure}"))),
        definition(
            "White matter",
            List.of("supratentorial white matter"),
            choices(
                choice(
                    "No significant disease", "No significant white-matter signal abnormality", ""),
                choice(
                    "Mild chronic microvascular change",
                    "Mild chronic microvascular white-matter change",
                    "Mild chronic microvascular white-matter change"),
                choice(
                    "Moderate chronic microvascular change",
                    "Moderate chronic microvascular white-matter change",
                    "Moderate chronic microvascular white-matter change"),
                choice(
                    "Severe chronic microvascular change",
                    "Severe chronic microvascular white-matter change",
                    "Severe chronic microvascular white-matter change"),
                choice(
                    "Nonspecific foci",
                    "Scattered nonspecific white-matter T2/FLAIR hyperintense foci",
                    "Nonspecific white-matter signal abnormalities"))),
        definition(
            "Ventricles and volume",
            List.of("intracranial compartment"),
            choices(
                choice("Normal", "Ventricles and sulci are within normal limits", ""),
                choice(
                    "Mild volume loss",
                    "Mild generalized cerebral volume loss",
                    "Mild generalized cerebral volume loss"),
                choice(
                    "Moderate volume loss",
                    "Moderate generalized cerebral volume loss",
                    "Moderate generalized cerebral volume loss"),
                choice("Hydrocephalus", "Hydrocephalus", "Hydrocephalus"),
                choice("Ventriculomegaly", "Ventriculomegaly", "Ventriculomegaly"))),
        definition(
            "Extra-axial spaces",
            List.of("extra-axial spaces"),
            choices(
                choice("No collection", "No extra-axial collection", ""),
                choice("Subdural collection", "Subdural collection", "Subdural collection"),
                choice("Subdural hematoma", "Subdural hematoma", "Subdural hematoma"),
                choice("Arachnoid cyst", "Arachnoid cyst", "Arachnoid cyst"))),
        definition(
            "Sinuses and mastoids",
            List.of("paranasal sinuses", "mastoid air cells"),
            choices(
                choice("Clear", "The {structure} are clear", ""),
                choice(
                    "Mild inflammatory change",
                    "Mild inflammatory change in the {structure}",
                    "Mild inflammatory change in the {structure}"),
                choice(
                    "Fluid opacification",
                    "Fluid opacification of the {structure}",
                    "Fluid opacification of the {structure}"))));
  }

  private static Map<String, Category> kneeCatalog() {
    return catalog(
        definition(
            "Menisci",
            List.of("medial meniscus", "lateral meniscus"),
            choices(
                choice("Intact", "The {structure} is intact", ""),
                choice(
                    "Intrasubstance degeneration",
                    "Intrasubstance degeneration of the {structure}",
                    "{structure} degeneration"),
                choice(
                    "Horizontal tear",
                    "Horizontal tear of the {structure}",
                    "Horizontal {structure} tear"),
                choice(
                    "Vertical tear",
                    "Vertical tear of the {structure}",
                    "Vertical {structure} tear"),
                choice("Radial tear", "Radial tear of the {structure}", "Radial {structure} tear"),
                choice("Root tear", "Root tear of the {structure}", "{structure} root tear"),
                choice(
                    "Complex tear", "Complex tear of the {structure}", "Complex {structure} tear"),
                choice("Extrusion", "Extrusion of the {structure}", "{structure} extrusion"))),
        definition(
            "Ligaments",
            List.of(
                "anterior cruciate ligament",
                "posterior cruciate ligament",
                "medial collateral ligament",
                "lateral collateral ligament complex"),
            ligamentFindings()),
        definition(
            "Cartilage",
            List.of(
                "patellofemoral compartment",
                "medial femorotibial compartment",
                "lateral femorotibial compartment"),
            cartilageFindings()),
        definition(
            "Extensor mechanism",
            List.of(
                "quadriceps tendon",
                "patellar tendon",
                "medial patellar retinaculum",
                "lateral patellar retinaculum"),
            tendonFindings()),
        definition(
            "Bones",
            List.of(
                "patella",
                "medial femoral condyle",
                "lateral femoral condyle",
                "medial tibial plateau",
                "lateral tibial plateau",
                "fibular head"),
            boneFindings()),
        definition(
            "Joint",
            List.of("knee joint"),
            choices(
                choice("No effusion", "No knee joint effusion", ""),
                choice("Small effusion", "Small knee joint effusion", "Small knee joint effusion"),
                choice(
                    "Moderate effusion",
                    "Moderate knee joint effusion",
                    "Moderate knee joint effusion"),
                choice("Large effusion", "Large knee joint effusion", "Large knee joint effusion"),
                choice("Synovitis", "Knee joint synovitis", "Knee joint synovitis"),
                choice(
                    "Intra-articular body",
                    "Intra-articular body in the knee joint",
                    "Intra-articular body"))),
        definition(
            "Cysts and bursae",
            List.of("popliteal fossa", "prepatellar bursa", "pes anserine bursa"),
            choices(
                choice("No fluid collection", "No fluid collection at the {structure}", ""),
                choice("Cyst", "Cyst at the {structure}", "Cyst at the {structure}"),
                choice("Bursitis", "Bursitis at the {structure}", "Bursitis at the {structure}"),
                choice(
                    "Fluid collection",
                    "Fluid collection at the {structure}",
                    "Fluid collection at the {structure}"))));
  }

  private static Map<String, Category> shoulderCatalog() {
    return catalog(
        definition(
            "Rotator cuff",
            List.of(
                "supraspinatus tendon",
                "infraspinatus tendon",
                "subscapularis tendon",
                "teres minor tendon"),
            tendonFindings()),
        definition(
            "Labrum",
            List.of(
                "superior labrum", "anterior labrum", "anteroinferior labrum", "posterior labrum"),
            labrumFindings()),
        definition(
            "Long head biceps",
            List.of("long head of the biceps tendon"),
            choices(
                choice("Intact", "The {structure} is intact and normally positioned", ""),
                choice(
                    "Tendinosis", "Tendinosis of the {structure}", "Long-head biceps tendinosis"),
                choice(
                    "Tenosynovitis",
                    "Tenosynovitis of the {structure}",
                    "Long-head biceps tenosynovitis"),
                choice(
                    "Partial tear",
                    "Partial-thickness tear of the {structure}",
                    "Partial-thickness long-head biceps tear"),
                choice(
                    "Complete tear",
                    "Complete tear of the {structure}",
                    "Complete long-head biceps tear"),
                choice(
                    "Medial subluxation",
                    "Medial subluxation of the {structure}",
                    "Medial subluxation of the long-head biceps tendon"))),
        definition(
            "Glenohumeral cartilage",
            List.of("humeral head cartilage", "glenoid cartilage"),
            cartilageFindings()),
        definition(
            "Acromioclavicular joint",
            List.of("acromioclavicular joint"),
            choices(
                choice("No arthrosis", "No significant acromioclavicular joint arthrosis", ""),
                choice(
                    "Mild arthrosis",
                    "Mild acromioclavicular joint arthrosis",
                    "Mild acromioclavicular joint arthrosis"),
                choice(
                    "Moderate arthrosis",
                    "Moderate acromioclavicular joint arthrosis",
                    "Moderate acromioclavicular joint arthrosis"),
                choice(
                    "Severe arthrosis",
                    "Severe acromioclavicular joint arthrosis",
                    "Severe acromioclavicular joint arthrosis"),
                choice(
                    "Inferior osteophytes",
                    "Inferiorly directed acromioclavicular joint osteophytes",
                    "Inferior acromioclavicular joint osteophytes"))),
        definition(
            "Bursae",
            List.of("subacromial-subdeltoid bursa", "subcoracoid bursa"),
            choices(
                choice("No fluid", "No significant fluid in the {structure}", ""),
                choice("Trace fluid", "Trace fluid in the {structure}", ""),
                choice("Bursitis", "Bursitis of the {structure}", "{structure} bursitis"))),
        definition(
            "Bones",
            List.of("humeral head", "greater tuberosity", "glenoid", "acromion", "distal clavicle"),
            boneFindings()));
  }

  private static Map<String, Category> elbowCatalog() {
    return catalog(
        definition(
            "Tendons",
            List.of(
                "common extensor tendon",
                "common flexor tendon",
                "distal biceps tendon",
                "distal triceps tendon"),
            tendonFindings()),
        definition(
            "Ligaments",
            List.of(
                "ulnar collateral ligament",
                "radial collateral ligament",
                "lateral ulnar collateral ligament",
                "annular ligament"),
            ligamentFindings()),
        definition(
            "Bones and cartilage",
            List.of("capitellum", "trochlea", "radial head", "olecranon", "coronoid process"),
            boneAndCartilageFindings()),
        definition(
            "Joint",
            List.of("elbow joint"),
            choices(
                choice("No effusion", "No elbow joint effusion", ""),
                choice(
                    "Small effusion", "Small elbow joint effusion", "Small elbow joint effusion"),
                choice(
                    "Moderate effusion",
                    "Moderate elbow joint effusion",
                    "Moderate elbow joint effusion"),
                choice("Synovitis", "Elbow joint synovitis", "Elbow joint synovitis"),
                choice(
                    "Intra-articular body",
                    "Intra-articular body in the elbow joint",
                    "Intra-articular body"))),
        definition(
            "Nerves", List.of("ulnar nerve", "median nerve", "radial nerve"), nerveFindings()),
        definition(
            "Bursae and soft tissues",
            List.of("olecranon bursa", "antecubital fossa", "posterior elbow soft tissues"),
            choices(
                choice("No abnormality", "No abnormality at the {structure}", ""),
                choice("Bursitis", "Bursitis at the {structure}", "Bursitis at the {structure}"),
                choice(
                    "Edema",
                    "Soft-tissue edema at the {structure}",
                    "Soft-tissue edema at the {structure}"),
                choice("Mass", "Mass at the {structure}", "Mass at the {structure}"))));
  }

  private static Map<String, Category> wristCatalog() {
    return catalog(
        definition(
            "Intrinsic ligaments",
            List.of("scapholunate ligament", "lunotriquetral ligament"),
            ligamentFindings()),
        definition(
            "TFCC",
            List.of("triangular fibrocartilage complex"),
            choices(
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
                choice("Tear", "Tear of the {structure}", "TFCC tear"))),
        definition(
            "Tendons",
            List.of(
                "extensor carpi ulnaris tendon",
                "first extensor compartment tendons",
                "remaining extensor tendons",
                "flexor tendons",
                "flexor carpi radialis tendon",
                "flexor carpi ulnaris tendon"),
            tendonFindings()),
        definition(
            "Bones",
            List.of(
                "distal radius",
                "distal ulna",
                "scaphoid",
                "lunate",
                "triquetrum",
                "remaining carpal bones"),
            boneFindings()),
        definition(
            "Joints and cartilage",
            List.of(
                "radiocarpal joint",
                "midcarpal joint",
                "distal radioulnar joint",
                "first carpometacarpal joint"),
            cartilageFindings()),
        definition("Nerves", List.of("median nerve", "ulnar nerve"), nerveFindings()),
        definition(
            "Other",
            List.of("dorsal wrist", "volar wrist", "carpal tunnel", "soft tissues"),
            choices(
                choice(
                    "Ganglion cyst",
                    "Ganglion cyst at the {structure}",
                    "Ganglion cyst at the {structure}"),
                choice(
                    "Soft-tissue edema",
                    "Soft-tissue edema at the {structure}",
                    "Soft-tissue edema at the {structure}"),
                choice("Mass", "Mass at the {structure}", "Mass at the {structure}"))));
  }

  private static Map<String, Category> handCatalog() {
    return catalog(
        definition(
            "Tendons",
            List.of(
                "flexor tendons",
                "extensor tendons",
                "flexor pollicis longus tendon",
                "extensor pollicis longus tendon"),
            tendonFindings()),
        definition(
            "Ligaments",
            List.of(
                "thumb MCP ulnar collateral ligament",
                "thumb MCP radial collateral ligament",
                "selected MCP collateral ligament",
                "selected interphalangeal collateral ligament"),
            ligamentFindings()),
        definition(
            "Pulleys and volar plates",
            List.of("selected flexor pulley", "selected volar plate"),
            choices(
                choice("Intact", "The {structure} is intact", ""),
                choice("Sprain", "Sprain of the {structure}", "{structure} sprain"),
                choice(
                    "Partial tear",
                    "Partial-thickness tear of the {structure}",
                    "Partial-thickness tear of the {structure}"),
                choice(
                    "Full tear",
                    "Full-thickness tear of the {structure}",
                    "Full-thickness tear of the {structure}"))),
        definition(
            "Bones",
            List.of(
                "selected metacarpal",
                "selected proximal phalanx",
                "selected middle phalanx",
                "selected distal phalanx"),
            boneFindings()),
        definition(
            "Joints and cartilage",
            List.of(
                "selected MCP joint",
                "selected PIP joint",
                "selected DIP joint",
                "thumb interphalangeal joint"),
            cartilageFindings()),
        definition(
            "Soft tissues",
            List.of("palmar soft tissues", "dorsal soft tissues", "selected digit", "webspace"),
            choices(
                choice("No abnormality", "No abnormality at the {structure}", ""),
                choice(
                    "Edema",
                    "Soft-tissue edema at the {structure}",
                    "Soft-tissue edema at the {structure}"),
                choice(
                    "Ganglion cyst",
                    "Ganglion cyst at the {structure}",
                    "Ganglion cyst at the {structure}"),
                choice("Mass", "Mass at the {structure}", "Mass at the {structure}"))));
  }

  private static Map<String, Category> hipCatalog() {
    return catalog(
        definition(
            "Labrum",
            List.of(
                "anterosuperior labrum", "anterior labrum", "superior labrum", "posterior labrum"),
            labrumFindings()),
        definition(
            "Cartilage",
            List.of("femoral head cartilage", "acetabular cartilage"),
            cartilageFindings()),
        definition(
            "Tendons",
            List.of(
                "gluteus medius tendon",
                "gluteus minimus tendon",
                "hamstring tendon origin",
                "iliopsoas tendon",
                "rectus femoris tendon origin"),
            tendonFindings()),
        definition(
            "Bones",
            List.of(
                "femoral head", "femoral neck", "acetabulum", "pubic rami", "visualized pelvis"),
            boneFindings()),
        definition(
            "Joint",
            List.of("hip joint"),
            choices(
                choice("No effusion", "No hip joint effusion", ""),
                choice("Small effusion", "Small hip joint effusion", "Small hip joint effusion"),
                choice(
                    "Moderate effusion",
                    "Moderate hip joint effusion",
                    "Moderate hip joint effusion"),
                choice("Synovitis", "Hip joint synovitis", "Hip joint synovitis"),
                choice(
                    "Intra-articular body",
                    "Intra-articular body in the hip joint",
                    "Intra-articular body"))),
        definition(
            "Bursae",
            List.of("greater trochanteric bursa", "iliopsoas bursa", "ischial bursa"),
            choices(
                choice("No fluid", "No significant fluid in the {structure}", ""),
                choice("Trace fluid", "Trace fluid in the {structure}", ""),
                choice("Bursitis", "Bursitis of the {structure}", "{structure} bursitis"))));
  }

  private static Map<String, Category> ankleCatalog() {
    return catalog(
        definition(
            "Ligaments",
            List.of(
                "anterior talofibular ligament",
                "calcaneofibular ligament",
                "posterior talofibular ligament",
                "deltoid ligament complex",
                "anterior inferior tibiofibular ligament",
                "posterior inferior tibiofibular ligament"),
            ligamentFindings()),
        definition(
            "Tendons",
            List.of(
                "peroneus longus tendon",
                "peroneus brevis tendon",
                "posterior tibial tendon",
                "flexor digitorum longus tendon",
                "flexor hallucis longus tendon",
                "anterior tibial tendon",
                "extensor tendons"),
            tendonFindings()),
        definition(
            "Achilles tendon",
            List.of("Achilles tendon"),
            choices(
                choice("Intact", "The {structure} is intact", ""),
                choice("Tendinosis", "Tendinosis of the {structure}", "Achilles tendinosis"),
                choice(
                    "Insertional tendinopathy",
                    "Insertional tendinopathy of the {structure}",
                    "Insertional Achilles tendinopathy"),
                choice(
                    "Partial tear",
                    "Partial-thickness tear of the {structure}",
                    "Partial-thickness Achilles tear"),
                choice(
                    "Complete tear",
                    "Complete tear of the {structure}",
                    "Complete Achilles tendon tear"))),
        definition(
            "Cartilage",
            List.of("medial talar dome", "lateral talar dome", "tibial plafond", "subtalar joint"),
            cartilageFindings()),
        definition(
            "Bones",
            List.of("distal tibia", "distal fibula", "talus", "calcaneus", "navicular"),
            boneFindings()),
        definition(
            "Joints and soft tissues",
            List.of("tibiotalar joint", "subtalar joint", "sinus tarsi", "ankle soft tissues"),
            choices(
                choice("No abnormality", "No abnormality at the {structure}", ""),
                choice("Effusion", "Effusion at the {structure}", "Effusion at the {structure}"),
                choice("Synovitis", "Synovitis at the {structure}", "Synovitis at the {structure}"),
                choice("Edema", "Edema at the {structure}", "Edema at the {structure}"),
                choice(
                    "Ganglion cyst",
                    "Ganglion cyst at the {structure}",
                    "Ganglion cyst at the {structure}"))));
  }

  private static Map<String, Category> footCatalog() {
    return catalog(
        definition(
            "Tendons",
            List.of(
                "flexor hallucis longus tendon",
                "flexor digitorum longus tendons",
                "extensor hallucis longus tendon",
                "extensor digitorum longus tendons",
                "peroneus longus tendon",
                "peroneus brevis tendon"),
            tendonFindings()),
        definition(
            "Ligaments and plantar plates",
            List.of(
                "Lisfranc ligament",
                "first MTP plantar plate",
                "second MTP plantar plate",
                "third MTP plantar plate",
                "selected MTP collateral ligament"),
            ligamentFindings()),
        definition(
            "Plantar fascia",
            List.of("central cord of the plantar fascia"),
            choices(
                choice("Normal", "The {structure} is normal", ""),
                choice(
                    "Plantar fasciitis",
                    "Thickening and edema of the {structure}",
                    "Plantar fasciitis"),
                choice(
                    "Partial tear",
                    "Partial-thickness tear of the {structure}",
                    "Partial-thickness plantar fascia tear"),
                choice(
                    "Complete tear",
                    "Complete tear of the {structure}",
                    "Complete plantar fascia tear"))),
        definition(
            "Bones",
            List.of(
                "selected metatarsal",
                "selected phalanx",
                "navicular",
                "cuboid",
                "cuneiforms",
                "sesamoids"),
            boneFindings()),
        definition(
            "Joints and cartilage",
            List.of(
                "first MTP joint",
                "selected MTP joint",
                "tarsometatarsal joints",
                "midfoot joints"),
            cartilageFindings()),
        definition(
            "Intermetatarsal spaces",
            List.of(
                "first intermetatarsal space",
                "second intermetatarsal space",
                "third intermetatarsal space"),
            choices(
                choice("No abnormality", "No abnormality in the {structure}", ""),
                choice(
                    "Morton neuroma",
                    "Morton neuroma in the {structure}",
                    "Morton neuroma in the {structure}"),
                choice(
                    "Intermetatarsal bursitis",
                    "Intermetatarsal bursitis in the {structure}",
                    "Intermetatarsal bursitis in the {structure}"),
                choice(
                    "Edema",
                    "Soft-tissue edema in the {structure}",
                    "Soft-tissue edema in the {structure}"))));
  }

  private static List<FindingChoice> spineDiscFindings() {
    return choices(
        choice("No significant abnormality", "No significant disc abnormality at {structure}", ""),
        choice("Desiccation", "Disc desiccation at {structure}", "Disc desiccation at {structure}"),
        choice(
            "Height loss",
            "Disc-space height loss at {structure}",
            "Disc-space height loss at {structure}"),
        choice("Disc bulge", "Disc bulge at {structure}", "Disc bulge at {structure}"),
        choice(
            "Central protrusion",
            "Central disc protrusion at {structure}",
            "Central disc protrusion at {structure}"),
        choice(
            "Right paracentral protrusion",
            "Right paracentral disc protrusion at {structure}",
            "Right paracentral disc protrusion at {structure}"),
        choice(
            "Left paracentral protrusion",
            "Left paracentral disc protrusion at {structure}",
            "Left paracentral disc protrusion at {structure}"),
        choice("Extrusion", "Disc extrusion at {structure}", "Disc extrusion at {structure}"),
        choice(
            "Annular fissure", "Annular fissure at {structure}", "Annular fissure at {structure}"));
  }

  private static List<FindingChoice> spineCanalFindings() {
    return choices(
        choice("No stenosis", "No significant spinal canal stenosis at {structure}", ""),
        choice(
            "Mild stenosis",
            "Mild spinal canal stenosis at {structure}",
            "Mild spinal canal stenosis at {structure}"),
        choice(
            "Moderate stenosis",
            "Moderate spinal canal stenosis at {structure}",
            "Moderate spinal canal stenosis at {structure}"),
        choice(
            "Severe stenosis",
            "Severe spinal canal stenosis at {structure}",
            "Severe spinal canal stenosis at {structure}"),
        choice(
            "Cord indentation",
            "Indentation of the ventral cord at {structure}",
            "Ventral cord indentation at {structure}"),
        choice(
            "Cord compression",
            "Spinal cord compression at {structure}",
            "Spinal cord compression at {structure}"));
  }

  private static List<FindingChoice> lumbarCanalFindings() {
    return choices(
        choice(
            "No stenosis",
            "No significant spinal canal or lateral recess stenosis at {structure}",
            ""),
        choice(
            "Mild canal stenosis",
            "Mild spinal canal stenosis at {structure}",
            "Mild spinal canal stenosis at {structure}"),
        choice(
            "Moderate canal stenosis",
            "Moderate spinal canal stenosis at {structure}",
            "Moderate spinal canal stenosis at {structure}"),
        choice(
            "Severe canal stenosis",
            "Severe spinal canal stenosis at {structure}",
            "Severe spinal canal stenosis at {structure}"),
        choice(
            "Right lateral recess stenosis",
            "Right lateral recess stenosis at {structure}",
            "Right lateral recess stenosis at {structure}"),
        choice(
            "Left lateral recess stenosis",
            "Left lateral recess stenosis at {structure}",
            "Left lateral recess stenosis at {structure}"),
        choice(
            "Bilateral lateral recess stenosis",
            "Bilateral lateral recess stenosis at {structure}",
            "Bilateral lateral recess stenosis at {structure}"));
  }

  private static List<FindingChoice> spineForaminalFindings() {
    return choices(
        choice("No narrowing", "No significant neural foraminal narrowing at {structure}", ""),
        choice(
            "Mild bilateral",
            "Mild bilateral neural foraminal narrowing at {structure}",
            "Mild bilateral neural foraminal narrowing at {structure}"),
        choice(
            "Moderate bilateral",
            "Moderate bilateral neural foraminal narrowing at {structure}",
            "Moderate bilateral neural foraminal narrowing at {structure}"),
        choice(
            "Severe bilateral",
            "Severe bilateral neural foraminal narrowing at {structure}",
            "Severe bilateral neural foraminal narrowing at {structure}"),
        choice(
            "Moderate right",
            "Moderate right neural foraminal narrowing at {structure}",
            "Moderate right neural foraminal narrowing at {structure}"),
        choice(
            "Severe right",
            "Severe right neural foraminal narrowing at {structure}",
            "Severe right neural foraminal narrowing at {structure}"),
        choice(
            "Moderate left",
            "Moderate left neural foraminal narrowing at {structure}",
            "Moderate left neural foraminal narrowing at {structure}"),
        choice(
            "Severe left",
            "Severe left neural foraminal narrowing at {structure}",
            "Severe left neural foraminal narrowing at {structure}"));
  }

  private static List<FindingChoice> facetFindings() {
    return choices(
        choice("No arthropathy", "No significant facet arthropathy at {structure}", ""),
        choice(
            "Mild arthropathy",
            "Mild facet arthropathy at {structure}",
            "Mild facet arthropathy at {structure}"),
        choice(
            "Moderate arthropathy",
            "Moderate facet arthropathy at {structure}",
            "Moderate facet arthropathy at {structure}"),
        choice(
            "Severe arthropathy",
            "Severe facet arthropathy at {structure}",
            "Severe facet arthropathy at {structure}"),
        choice(
            "Facet effusion",
            "Facet-joint effusion at {structure}",
            "Facet-joint effusion at {structure}"),
        choice(
            "Facet edema",
            "Periarticular facet edema at {structure}",
            "Active facet arthropathy at {structure}"));
  }

  private static List<FindingChoice> cordFindings() {
    return choices(
        choice("Normal", "The {structure} is normal in signal and caliber", ""),
        choice(
            "T2 hyperintensity",
            "T2 hyperintense signal in the {structure}",
            "Abnormal T2 signal in the {structure}"),
        choice("Cord edema", "Edema in the {structure}", "Spinal cord edema"),
        choice(
            "Myelomalacia", "Myelomalacia in the {structure}", "Myelomalacia in the {structure}"),
        choice("Compression", "Compression of the {structure}", "Spinal cord compression"),
        choice("Syrinx", "Syrinx in the {structure}", "Spinal cord syrinx"));
  }

  private static List<FindingChoice> spineBoneFindings() {
    return choices(
        choice("Normal marrow signal", "Normal marrow signal in the {structure}", ""),
        choice(
            "Marrow edema", "Marrow edema in the {structure}", "Marrow edema in the {structure}"),
        choice(
            "Acute compression fracture",
            "Acute compression fracture of the {structure}",
            "Acute compression fracture of the {structure}"),
        choice(
            "Chronic compression deformity",
            "Chronic compression deformity of the {structure}",
            "Chronic compression deformity of the {structure}"),
        choice(
            "Focal marrow lesion",
            "Focal marrow lesion in the {structure}",
            "Focal marrow lesion in the {structure}"),
        choice("Hemangioma", "Hemangioma in the {structure}", ""));
  }

  private static List<FindingChoice> tendonFindings() {
    return choices(
        choice("Intact", "The {structure} is intact", ""),
        choice("Tendinosis", "Tendinosis of the {structure}", "{structure} tendinosis"),
        choice("Tenosynovitis", "Tenosynovitis of the {structure}", "{structure} tenosynovitis"),
        choice(
            "Partial-thickness tear",
            "Partial-thickness tear of the {structure}",
            "Partial-thickness {structure} tear"),
        choice(
            "Full-thickness tear",
            "Full-thickness tear of the {structure}",
            "Full-thickness {structure} tear"),
        choice("Subluxation", "Subluxation of the {structure}", "{structure} subluxation"));
  }

  private static List<FindingChoice> ligamentFindings() {
    return choices(
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
        choice("Tear", "Tear of the {structure}", "{structure} tear"));
  }

  private static List<FindingChoice> cartilageFindings() {
    return choices(
        choice("Preserved", "Cartilage is preserved at the {structure}", ""),
        choice(
            "Mild chondrosis", "Mild chondrosis of the {structure}", "Mild {structure} chondrosis"),
        choice(
            "Moderate chondrosis",
            "Moderate chondrosis of the {structure}",
            "Moderate {structure} chondrosis"),
        choice(
            "Severe chondrosis",
            "Severe chondrosis of the {structure}",
            "Severe {structure} chondrosis"),
        choice(
            "Full-thickness chondral loss",
            "Full-thickness chondral loss at the {structure}",
            "Full-thickness chondral loss at the {structure}"),
        choice(
            "Focal chondral defect",
            "Focal chondral defect at the {structure}",
            "Focal chondral defect at the {structure}"));
  }

  private static List<FindingChoice> boneFindings() {
    return choices(
        choice("Normal marrow signal", "Normal marrow signal in the {structure}", ""),
        choice("Marrow edema", "Marrow edema in the {structure}", "{structure} marrow edema"),
        choice(
            "Stress reaction",
            "Stress reaction in the {structure}",
            "Stress reaction in the {structure}"),
        choice("Fracture", "Fracture of the {structure}", "{structure} fracture"),
        choice(
            "Subchondral cyst",
            "Subchondral cystic change in the {structure}",
            "Subchondral cystic change in the {structure}"),
        choice(
            "Osteonecrosis",
            "Osteonecrosis of the {structure}",
            "Osteonecrosis of the {structure}"));
  }

  private static List<FindingChoice> boneAndCartilageFindings() {
    return choices(
        choice("Normal", "Normal marrow signal and preserved cartilage at the {structure}", ""),
        choice(
            "Marrow edema", "Marrow edema in the {structure}", "Marrow edema in the {structure}"),
        choice("Fracture", "Fracture of the {structure}", "Fracture of the {structure}"),
        choice("Chondrosis", "Chondrosis at the {structure}", "Chondrosis at the {structure}"),
        choice(
            "Osteochondral lesion",
            "Osteochondral lesion at the {structure}",
            "Osteochondral lesion at the {structure}"));
  }

  private static List<FindingChoice> labrumFindings() {
    return choices(
        choice("Intact", "The {structure} is intact", ""),
        choice("Degeneration", "Degeneration of the {structure}", "{structure} degeneration"),
        choice("Tear", "Tear of the {structure}", "{structure} tear"),
        choice("Detachment", "Detachment of the {structure}", "Detachment of the {structure}"),
        choice(
            "Paralabral cyst",
            "Paralabral cyst adjacent to the {structure}",
            "Paralabral cyst adjacent to the {structure}"));
  }

  private static List<FindingChoice> nerveFindings() {
    return choices(
        choice("Normal", "The {structure} is normal in signal and caliber", ""),
        choice("Enlarged", "Enlargement of the {structure}", "Enlarged {structure}"),
        choice(
            "Increased signal",
            "Increased signal in the {structure}",
            "Abnormal signal in the {structure}"),
        choice("Compression", "Compression of the {structure}", "Compression of the {structure}"),
        choice(
            "Neuritis",
            "Signal abnormality and enlargement of the {structure}",
            "Neuritis of the {structure}"));
  }

  private static Map<String, Category> catalog(CategoryDefinition... definitions) {
    Map<String, Category> categories = new LinkedHashMap<>();
    for (CategoryDefinition definition : definitions) {
      categories.put(
          definition.name(), new Category(definition.structures(), definition.findings()));
    }
    return Collections.unmodifiableMap(categories);
  }

  private static CategoryDefinition definition(
      String name, List<String> structures, List<FindingChoice> findings) {
    return new CategoryDefinition(name, List.copyOf(structures), List.copyOf(findings));
  }

  private static List<FindingChoice> choices(FindingChoice... choices) {
    return List.of(choices);
  }

  private static FindingChoice choice(String label, String finding, String impression) {
    return new FindingChoice(label, finding, impression);
  }

  private record CategoryDefinition(
      String name, List<String> structures, List<FindingChoice> findings) {}

  private record Category(List<String> structures, List<FindingChoice> findings) {}

  public enum ExamTemplate {
    GENERAL("General MRI"),
    BRAIN("Brain MRI"),
    CERVICAL_SPINE("Cervical spine MRI"),
    THORACIC_SPINE("Thoracic spine MRI"),
    LUMBAR_SPINE("Lumbar spine MRI"),
    SHOULDER("Shoulder MRI"),
    ELBOW("Elbow MRI"),
    WRIST("Wrist MRI"),
    HAND("Hand MRI"),
    HIP("Hip MRI"),
    KNEE("Knee MRI"),
    ANKLE("Ankle MRI"),
    FOOT("Foot MRI");

    private final String displayName;

    ExamTemplate(String displayName) {
      this.displayName = displayName;
    }

    @Override
    public String toString() {
      return displayName;
    }
  }

  public record FindingChoice(String label, String findingTemplate, String impressionTemplate) {
    @Override
    public String toString() {
      return label;
    }
  }

  public record GeneratedFinding(String findingText, String impressionText) {}
}
