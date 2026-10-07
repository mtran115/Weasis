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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.weasis.dicom.reportcomposer.AiPacketPlan.Localizer;
import org.weasis.dicom.reportcomposer.AiPacketPlan.Series;
import org.weasis.dicom.reportcomposer.AiPacketPlan.Slice;

/**
 * Writes a dry-run AI packet (PNGs, labels, and the exact request) to a local folder; sends
 * nothing.
 */
final class AiPacketExporter {
  static final Duration KEEP = Duration.ofDays(5);
  static final String PREFIX = "ai-packet-";
  // Placeholders until a sender exists; check OpenAI's current model names and image options.
  static final String MODEL = "gpt-6-astra";
  static final String DETAIL = "original";
  static final int MIN_SIDE = 512;
  private static final int PATCH_PIXELS = 32;
  private static final double PATCH_MULTIPLIER = 1.2;
  private static final double[] DOLLARS_PER_MILLION = {10, 2};
  private static final DateTimeFormatter FOLDER_TIME =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final SecureRandom RANDOM = new SecureRandom();

  static final String INSTRUCTIONS =
      """
      You are assisting a radiologist who reviews every statement before it is used. Interpret the \
      examination provided as labelled images.
      - Each image follows a label in square brackets. Rely on the label for the image's series, \
      plane, slice number, and position, not on the order of the images. Each series' summary says \
      which patient directions face the image's right and bottom edges.
      - Localizer images show where each axial slice cuts a sagittal image; each line's number is \
      that axial slice's number.
      - Disc levels in labels come from a separate model or the radiologist's confirmation, as the \
      overview states. If the images disagree with them, say so.
      - List only abnormal findings. Leave out normal structures and normal levels.
      - For each finding give its location (spinal level when relevant), a concise description, \
      the severity when it applies, the image IDs that show it best, and your confidence.
      - If image quality, coverage, or missing sequences limit the assessment, say so under \
      limitations.
      - Do not speculate about the patient's identity, the institution, or dates.""";

  record Result(
      Path directory,
      int imageCount,
      long imageTokens,
      long textTokens,
      List<String> skipped,
      int markCount) {}

  static final String MARKED_REQUEST = "request-marked.json";

  static final String MARK_INSTRUCTIONS =
      """

      Marked findings
      - The radiologist ringed findings in red, each ring numbered. An image whose ID ends in \
      -marked is a copy of the image just before it with the rings drawn; use the unmarked copy to \
      see the anatomy under a ring.
      - For each ring, add one entry to marks with its number, its location (the spinal level when \
      relevant), what the ringed finding is, and your confidence. If the ringed area looks normal, \
      say so in that entry.
      - A ring asks for a closer look; it does not mean something is abnormal. Report every other \
      finding as usual.""";

  private AiPacketExporter() {}

  static Result export(AiPacketPlan plan, Path root, Clock clock) throws IOException {
    Files.createDirectories(root);
    prune(root, clock.instant().minus(KEEP));
    byte[] suffix = new byte[3];
    RANDOM.nextBytes(suffix);
    String name =
        PREFIX
            + FOLDER_TIME.format(LocalDateTime.now(clock))
            + "-"
            + HexFormat.of().formatHex(suffix);
    Path partial = root.resolve("." + name);
    Files.createDirectories(partial.resolve("images"));
    try {
      Written written = writeImages(plan, partial);
      String overview = overview(plan);
      List<Map<String, Object>> content = new ArrayList<>();
      content.add(textPart(overview));
      content.addAll(written.content());
      boolean lumbar = isLumbar(plan);
      String instructions = lumbar ? AiLumbarRequest.INSTRUCTIONS : INSTRUCTIONS;
      long textTokens = (instructions.length() + written.labelCharacters() + overview.length()) / 4;

      JSON.writerWithDefaultPrettyPrinter()
          .writeValue(
              partial.resolve("request.json").toFile(),
              request(
                  instructions, content, lumbar ? AiLumbarRequest.outputFormat() : outputFormat()));
      if (!plan.marks().isEmpty()) {
        List<Map<String, Object>> marked = new ArrayList<>();
        marked.add(textPart(overview + "\n" + marksOverview(plan)));
        marked.addAll(written.markedContent());
        JSON.writerWithDefaultPrettyPrinter()
            .writeValue(
                partial.resolve(MARKED_REQUEST).toFile(),
                request(
                    instructions + MARK_INSTRUCTIONS,
                    marked,
                    lumbar ? AiLumbarRequest.outputFormat(true) : outputFormat(true)));
      }
      JSON.writerWithDefaultPrettyPrinter()
          .writeValue(partial.resolve("manifest.json").toFile(), manifest(plan, written));
      Files.writeString(
          partial.resolve("README.txt"),
          readme(plan, written, textTokens, LocalDateTime.now(clock)),
          StandardCharsets.UTF_8);
      Path directory = root.resolve(name);
      Files.move(partial, directory, StandardCopyOption.ATOMIC_MOVE);
      return new Result(
          directory,
          written.files().size(),
          written.imageTokens(),
          textTokens,
          written.skipped(),
          plan.marks().size());
    } catch (IOException | RuntimeException error) {
      deleteRecursively(partial);
      throw error;
    }
  }

  private static Map<String, Object> request(
      String instructions, List<Map<String, Object>> content, Map<String, Object> format) {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("model", MODEL);
    request.put("instructions", instructions);
    request.put("input", List.of(Map.of("role", "user", "content", content)));
    request.put("text", Map.of("format", format));
    return request;
  }

  private static String marksOverview(AiPacketPlan plan) {
    return "Rings: "
        + String.join(
            ", ",
            plan.marks().stream().map(m -> "ring " + m.number() + " on " + m.slice().id()).toList())
        + ".";
  }

  static boolean isLumbar(AiPacketPlan plan) {
    return plan.study().template() == MriFindingCatalog.ExamTemplate.LUMBAR_SPINE;
  }

  /** Estimated input tokens for one image: 32-pixel patches with a 1.2 multiplier. */
  static long imageTokens(int width, int height) {
    long patches =
        (long) Math.ceil(width / (double) PATCH_PIXELS)
            * (long) Math.ceil(height / (double) PATCH_PIXELS);
    return (long) Math.ceil(patches * PATCH_MULTIPLIER);
  }

  /**
   * Square pixels, as the viewer shows them, and at least {@link #MIN_SIDE} pixels on the short
   * side: a vision model sees a 256-pixel image in very few tokens and misses small findings.
   */
  static BufferedImage resize(BufferedImage image, ImageGeometry geometry) {
    double[] scale = scale(geometry, image.getWidth(), image.getHeight());
    if (scale[0] == 1 && scale[1] == 1) {
      return image;
    }
    int width = (int) Math.round(image.getWidth() * scale[0]);
    int height = (int) Math.round(image.getHeight() * scale[1]);
    BufferedImage scaled = new BufferedImage(width, height, imageType(image));
    Graphics2D g = scaled.createGraphics();
    g.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    g.drawImage(image, 0, 0, width, height, null);
    g.dispose();
    return scaled;
  }

  private record ImageFile(String id, String file, int width, int height, String label) {}

  private record Written(
      List<Map<String, Object>> content,
      List<Map<String, Object>> markedContent,
      List<ImageFile> files,
      List<ImageFile> markedFiles,
      long imageTokens,
      long labelCharacters,
      List<String> skipped) {}

  private static Written writeImages(AiPacketPlan plan, Path directory) throws IOException {
    List<Map<String, Object>> content = new ArrayList<>();
    List<Map<String, Object>> markedContent = new ArrayList<>();
    List<ImageFile> files = new ArrayList<>();
    List<ImageFile> markedFiles = new ArrayList<>();
    List<String> skipped = new ArrayList<>();
    for (Series series : plan.series()) {
      for (Localizer localizer : plan.localizers()) {
        if (localizer.axial() == series) {
          var parts =
              write(localizerImage(localizer), localizer.id(), localizer.label(), directory, files);
          content.addAll(parts);
          markedContent.addAll(parts);
        }
      }
      for (Slice slice : series.slices()) {
        BufferedImage image;
        try {
          image = resize(slice.renderer().get(), slice.reference().geometry());
        } catch (RuntimeException error) {
          skipped.add(slice.id() + ": " + error.getMessage());
          continue;
        }
        var parts = write(image, slice.id(), slice.label(), directory, files);
        content.addAll(parts);
        markedContent.addAll(parts);
        List<AiPacketPlan.PlacedMark> rings =
            plan.marks().stream().filter(mark -> mark.slice() == slice).toList();
        if (!rings.isEmpty()) {
          String id = slice.id() + "-marked";
          String numbers =
              String.join(", ", rings.stream().map(m -> Integer.toString(m.number())).toList());
          String label =
              "["
                  + id
                  + "] The image above with the radiologist's ring"
                  + (rings.size() > 1 ? "s " : " ")
                  + numbers;
          markedContent.addAll(
              write(
                  markedCopy(image, slice.reference().geometry(), rings),
                  id,
                  label,
                  directory,
                  markedFiles));
        }
      }
    }
    long tokens = files.stream().mapToLong(f -> imageTokens(f.width(), f.height())).sum();
    long characters = files.stream().mapToLong(f -> f.label().length()).sum();
    return new Written(
        content,
        markedContent,
        List.copyOf(files),
        List.copyOf(markedFiles),
        tokens,
        characters,
        List.copyOf(skipped));
  }

  // Writes the PNG and returns its label and image parts.
  private static List<Map<String, Object>> write(
      BufferedImage image, String id, String label, Path directory, List<ImageFile> files)
      throws IOException {
    String file = "images/" + id + ".png";
    if (!ImageIO.write(image, "png", directory.resolve(file).toFile())) {
      throw new IOException("No PNG writer is available.");
    }
    Map<String, Object> part = new LinkedHashMap<>();
    part.put("type", "input_image");
    part.put("image_url", file);
    part.put("detail", DETAIL);
    files.add(new ImageFile(id, file, image.getWidth(), image.getHeight(), label));
    return List.of(textPart(label), part);
  }

  /** A color copy of the sent image with the radiologist's numbered rings drawn on it. */
  static BufferedImage markedCopy(
      BufferedImage image, ImageGeometry geometry, List<AiPacketPlan.PlacedMark> rings) {
    BufferedImage copy =
        new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
    Graphics2D g = copy.createGraphics();
    g.drawImage(image, 0, 0, null);
    double x = image.getWidth() / (double) Math.max(1, geometry.columns());
    double y = image.getHeight() / (double) Math.max(1, geometry.rows());
    double radius = AiMarks.radiusPixels(geometry)[0] * x;
    for (AiPacketPlan.PlacedMark ring : rings) {
      AiMarks.paintRing(g, ring.column() * x, ring.row() * y, radius, ring.number());
    }
    g.dispose();
    return copy;
  }

  private static BufferedImage localizerImage(Localizer localizer) {
    ImageGeometry geometry = localizer.slice().reference().geometry();
    BufferedImage source = localizer.slice().renderer().get();
    double[] scale = scale(geometry, source.getWidth(), source.getHeight());
    BufferedImage gray = resize(source, geometry);
    BufferedImage image =
        new BufferedImage(gray.getWidth(), gray.getHeight(), BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    g.drawImage(gray, 0, 0, null);
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(
        RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(10, image.getHeight() / 55)));
    FontMetrics metrics = g.getFontMetrics();
    g.setStroke(new BasicStroke(1f));
    for (AiPacketPlan.Line line : localizer.lines()) {
      double[] c = line.coordinates();
      double x1 = c[0] * scale[0];
      double y1 = c[1] * scale[1];
      double x2 = c[2] * scale[0];
      double y2 = c[3] * scale[1];
      g.setColor(Color.YELLOW);
      g.draw(new Line2D.Double(x1, y1, x2, y2));
      // Number at the line's right end, or its left end when there is no room.
      String number = Integer.toString(line.number());
      double rightX = Math.max(x1, x2);
      double rightY = x1 >= x2 ? y1 : y2;
      int labelX = (int) Math.round(rightX) + 3;
      int labelY = (int) Math.round(rightY + metrics.getAscent() / 2.0 - 1);
      if (labelX + metrics.stringWidth(number) > image.getWidth()) {
        labelX = (int) Math.round(Math.min(x1, x2)) - metrics.stringWidth(number) - 3;
        labelY = (int) Math.round((x1 >= x2 ? y2 : y1) + metrics.getAscent() / 2.0 - 1);
      }
      g.drawString(number, Math.max(0, labelX), labelY);
    }
    g.setColor(Color.CYAN);
    for (AiPacketPlan.LevelMark mark : localizer.levels()) {
      int x = (int) Math.round(mark.column() * scale[0]);
      int y = (int) Math.round(mark.row() * scale[1]);
      g.fillOval(x - 3, y - 3, 6, 6);
      g.drawString(
          mark.level(),
          Math.max(0, x - metrics.stringWidth(mark.level()) - 8),
          y + metrics.getAscent() / 2);
    }
    g.dispose();
    return image;
  }

  private static String overview(AiPacketPlan plan) {
    AiPacketPlan.Study study = plan.study();
    StringBuilder text = new StringBuilder();
    text.append("Exam: ").append(study.examTitle()).append(". Patient: ");
    text.append("age ").append(study.age()).append(", sex ").append(study.sex()).append(".\n");
    text.append(
        "The images below are one examination, rendered from DICOM to PNG with an automatic"
            + " window/level for each slice and square pixels. Images smaller than 512 pixels were"
            + " enlarged; each series summary gives the acquired matrix. Each image follows its"
            + " label.\n");
    text.append("Series:\n");
    plan.series().forEach(s -> text.append(s.summary()).append('\n'));
    if (!plan.localizers().isEmpty()) {
      text.append("Localizers: ");
      text.append(
          String.join(
              "; ",
              plan.localizers().stream()
                  .map(
                      l ->
                          l.id()
                              + " shows where each "
                              + l.axial().id()
                              + " slice cuts sagittal slice "
                              + l.slice().id())
                  .toList()));
      text.append(".\n");
    }
    if (!"none".equals(plan.levelSource())) {
      text.append("Disc levels in labels: ").append(plan.levelSource()).append(".\n");
    } else if (isLumbar(plan)) {
      text.append("No disc levels are labelled; number the levels from the sagittal images.\n");
    }
    if (!plan.excluded().isEmpty()) {
      text.append("Not included: ");
      text.append(
          String.join(
              "; ",
              plan.excluded().stream().map(e -> e.series() + " (" + e.reason() + ")").toList()));
      text.append(".\n");
    }
    return text.toString().strip();
  }

  private static String answerFormat(AiPacketPlan plan) {
    return isLumbar(plan)
        ? AiLumbarRequest.FORMAT_NAME
            + " (the spine form's fields for every level T12-L1 to L5-S1, filled in by the"
            + " composer in your wording)"
        : "radiology_findings (abnormal findings with cited image IDs)";
  }

  static Map<String, Object> outputFormat() {
    return outputFormat(false);
  }

  static Map<String, Object> outputFormat(boolean marks) {
    Map<String, Object> finding = new LinkedHashMap<>();
    finding.put("type", "object");
    finding.put("additionalProperties", false);
    finding.put("required", List.of("location", "finding", "severity", "image_ids", "confidence"));
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("location", Map.of("type", "string"));
    properties.put("finding", Map.of("type", "string"));
    properties.put(
        "severity",
        Map.of("type", "string", "enum", List.of("mild", "moderate", "severe", "not_applicable")));
    properties.put("image_ids", Map.of("type", "array", "items", Map.of("type", "string")));
    properties.put(
        "confidence", Map.of("type", "string", "enum", List.of("low", "medium", "high")));
    finding.put("properties", properties);

    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    schema.put("additionalProperties", false);
    Map<String, Object> top = new LinkedHashMap<>();
    top.put("findings", Map.of("type", "array", "items", finding));
    top.put("limitations", Map.of("type", "array", "items", Map.of("type", "string")));
    if (marks) {
      var ringAnswers = AiSchema.marks(Map.of("type", "string"));
      top.put(ringAnswers.getKey(), ringAnswers.getValue());
    }
    schema.put("required", List.copyOf(top.keySet()));
    schema.put("properties", top);

    Map<String, Object> format = new LinkedHashMap<>();
    format.put("type", "json_schema");
    format.put("name", "radiology_findings");
    format.put("strict", true);
    format.put("schema", schema);
    return format;
  }

  private static Map<String, Object> manifest(AiPacketPlan plan, Written written) {
    Map<String, Object> manifest = new LinkedHashMap<>();
    manifest.put("schema_version", 1);
    manifest.put("dry_run", true);
    manifest.put(
        "study",
        Map.of(
            "exam", plan.study().examTitle(),
            "modality", plan.study().modality(),
            "age", plan.study().age(),
            "sex", plan.study().sex()));
    manifest.put("disc_levels", plan.levelSource());
    manifest.put("answer_format", answerFormat(plan));
    List<Map<String, Object>> series = new ArrayList<>();
    for (Series s : plan.series()) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("id", s.id());
      entry.put("series_number", s.seriesNumber());
      entry.put("description", s.description());
      entry.put("plane", s.plane().label());
      entry.put("summary", s.summary());
      entry.put("slice_count", s.slices().size());
      series.add(entry);
    }
    manifest.put("series", series);
    manifest.put(
        "images",
        written.files().stream()
            .map(
                f -> {
                  Map<String, Object> entry = new LinkedHashMap<>();
                  entry.put("id", f.id());
                  entry.put("file", f.file());
                  entry.put("width", f.width());
                  entry.put("height", f.height());
                  entry.put("label", f.label());
                  return entry;
                })
            .toList());
    manifest.put(
        "excluded_series",
        plan.excluded().stream()
            .map(e -> Map.of("series", e.series(), "reason", e.reason()))
            .toList());
    manifest.put("skipped_images", written.skipped());
    manifest.put(
        "marks",
        plan.marks().stream()
            .map(
                m ->
                    Map.of(
                        "number", m.number(),
                        "image_id", m.slice().id(),
                        "marked_image_id", m.slice().id() + "-marked"))
            .toList());
    manifest.put("marks_not_in_packet", plan.unplacedMarks());
    manifest.put("estimated_image_tokens", written.imageTokens());
    return manifest;
  }

  private static String readme(
      AiPacketPlan plan, Written written, long textTokens, LocalDateTime created) {
    long total = written.imageTokens() + textTokens;
    StringBuilder text = new StringBuilder();
    text.append("AI packet (dry run) — nothing was sent\n\n");
    text.append("Created ")
        .append(created.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
        .append('\n');
    text.append("Exam: ").append(plan.study().examTitle());
    text.append(" (age ")
        .append(plan.study().age())
        .append(", sex ")
        .append(plan.study().sex())
        .append(")\n");
    text.append("Images: ").append(written.files().size());
    text.append(" (").append(plan.localizers().size()).append(" localizers)\n");
    text.append(
        String.format(
            Locale.ROOT,
            "Estimated input: about %,d image tokens + %,d text tokens%n",
            written.imageTokens(),
            textTokens));
    text.append(
        String.format(
            Locale.ROOT,
            "  about $%.2f at $%.0f per million input tokens, $%.2f at $%.0f per million%n",
            total * DOLLARS_PER_MILLION[0] / 1_000_000,
            DOLLARS_PER_MILLION[0],
            total * DOLLARS_PER_MILLION[1] / 1_000_000,
            DOLLARS_PER_MILLION[1]));
    text.append(
        "  The estimate uses 32-pixel patches x 1.2 per image and 4 characters per text token;"
            + " check OpenAI's current pricing and image docs. Output tokens are extra.\n\n");
    text.append("Included series\n");
    plan.series().forEach(s -> text.append("  ").append(s.summary()).append('\n'));
    text.append("\nNot included\n");
    if (plan.excluded().isEmpty()) {
      text.append("  (nothing)\n");
    }
    plan.excluded()
        .forEach(
            e -> text.append("  ").append(e.series()).append(": ").append(e.reason()).append('\n'));
    written.skipped().forEach(s -> text.append("  Image ").append(s).append('\n'));
    text.append("\nDisc levels in labels: ").append(plan.levelSource()).append('\n');
    text.append("Answer format: ").append(answerFormat(plan)).append('\n');
    if (!plan.marks().isEmpty()) {
      text.append("Rings: ").append(plan.marks().size());
      text.append(
          " (sent as a second request, request-marked.json, after the blind read in request.json)");
      if (!plan.unplacedMarks().isEmpty()) {
        text.append("; not in this packet: ring ").append(plan.unplacedMarks());
      }
      text.append('\n');
    }
    text.append('\n');
    text.append(
        """
        Files
          request.json   The request body as it would be sent. Each "image_url" is a file in
                         images/; a sender would replace it with that PNG's base64 data.
          manifest.json  The same information as structured data.
          images/        PNGs with per-slice auto window/level and square pixels, enlarged to
                         at least 512 pixels on the short side. Localizer images (LOC-*) have
                         numbered axial lines and disc levels drawn on them.

        Before anything is sent: look through the images for burned-in names, dates, or other
        identifiers, and make sure a BAA and zero data retention are in place with the provider.
        Labels and request.json carry no names, IDs, dates, or institution.

        This folder is deleted automatically 5 days after it was made.
        """);
    return text.toString();
  }

  private static Map<String, Object> textPart(String text) {
    Map<String, Object> part = new LinkedHashMap<>();
    part.put("type", "input_text");
    part.put("text", text);
    return part;
  }

  private static double[] scale(ImageGeometry geometry, int width, int height) {
    double rowSpacing = geometry.pixelSpacing().get(0);
    double columnSpacing = geometry.pixelSpacing().get(1);
    double smallest = Math.min(rowSpacing, columnSpacing);
    double x = 1;
    double y = 1;
    if (Math.abs(rowSpacing - columnSpacing) > 0.01 * smallest) {
      x = columnSpacing / smallest;
      y = rowSpacing / smallest;
    }
    double shortSide = Math.min(width * x, height * y);
    double enlarge = shortSide < MIN_SIDE ? MIN_SIDE / shortSide : 1;
    return new double[] {x * enlarge, y * enlarge};
  }

  private static int imageType(BufferedImage image) {
    return image.getType() == BufferedImage.TYPE_CUSTOM
        ? BufferedImage.TYPE_INT_RGB
        : image.getType();
  }

  private static void prune(Path root, Instant cutoff) {
    try (Stream<Path> children = Files.list(root)) {
      for (Path child : children.toList()) {
        String name = child.getFileName().toString();
        if ((name.startsWith(PREFIX) || name.startsWith("." + PREFIX))
            && Files.isDirectory(child)
            && Files.getLastModifiedTime(child).toInstant().isBefore(cutoff)) {
          deleteRecursively(child);
        }
      }
    } catch (IOException ignored) {
      // Old packets are retried on the next export.
    }
  }

  private static void deleteRecursively(Path path) {
    if (!Files.exists(path)) {
      return;
    }
    try (Stream<Path> walk = Files.walk(path)) {
      for (Path entry : walk.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(entry);
      }
    } catch (IOException ignored) {
      // A leftover folder is pruned with the next export.
    }
  }
}
