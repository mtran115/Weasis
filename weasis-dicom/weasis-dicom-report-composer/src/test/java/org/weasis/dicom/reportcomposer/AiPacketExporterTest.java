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
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AiPacketExporterTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-04T17:00:00Z"), ZoneId.of("America/Los_Angeles"));

  @TempDir Path root;

  @Test
  void writesLabelledImagesManifestAndRequestWithoutSendingAnything() throws Exception {
    AiPacketPlan plan =
        AiPacketPlan.build(
            AiPacketPlanTest.STUDY,
            List.of(
                AiPacketPlanTest.series("601", "T2 SAG", AiPacketPlanTest.sagittals(-2, 0, 2)),
                AiPacketPlanTest.series("801", "T2 AX", AiPacketPlanTest.axials(30, 20, 10))),
            Optional.empty());

    AiPacketExporter.Result result = AiPacketExporter.export(plan, root, CLOCK);

    Path directory = result.directory();
    assertTrue(directory.getFileName().toString().startsWith("ai-packet-20261004-100000-"));
    assertEquals(7, result.imageCount());
    // The 200-pixel test images are enlarged to 512 pixels.
    assertEquals(7 * AiPacketExporter.imageTokens(512, 512), result.imageTokens());
    try (Stream<Path> children = Files.list(root)) {
      assertEquals(List.of(directory), children.toList());
    }

    JsonNode request = new ObjectMapper().readTree(directory.resolve("request.json").toFile());
    JsonNode content = request.at("/input/0/content");
    assertTrue(
        content
            .get(0)
            .get("text")
            .asText()
            .startsWith("Exam: MRI LUMBAR SPINE. Patient: age 54 years, sex male."));
    // After the overview, every image follows its own label.
    assertEquals(1 + 2 * 7, content.size());
    for (int index = 1; index < content.size(); index += 2) {
      assertEquals("input_text", content.get(index).get("type").asText());
      assertEquals("input_image", content.get(index + 1).get("type").asText());
      String file = content.get(index + 1).get("image_url").asText();
      assertTrue(
          content
              .get(index)
              .get("text")
              .asText()
              .contains("[" + file.substring(7, file.length() - 4) + "]"));
      assertTrue(Files.isRegularFile(directory.resolve(file)), file);
    }
    assertEquals(
        "[S1-01] sagittal \"T2 SAG\" (series 601), slice 1 of 3, R 2.0 mm",
        content.get(1).get("text").asText());
    assertEquals("images/LOC-S2.png", content.get(8).get("image_url").asText());
    assertEquals("json_schema", request.at("/text/format/type").asText());
    // A lumbar study asks for the spine form's fields, with the lumbar reading rules.
    assertEquals(AiLumbarRequest.FORMAT_NAME, request.at("/text/format/name").asText());
    assertEquals(AiLumbarRequest.INSTRUCTIONS, request.get("instructions").asText());

    JsonNode manifest = new ObjectMapper().readTree(directory.resolve("manifest.json").toFile());
    assertTrue(manifest.get("dry_run").asBoolean());
    assertEquals(7, manifest.get("images").size());
    String readme = Files.readString(directory.resolve("README.txt"));
    assertTrue(readme.startsWith("AI packet (dry run) — nothing was sent"));
  }

  @Test
  void ringsGoInASecondRequestAfterTheBlindOne() throws Exception {
    AiPacketPlan plan =
        AiPacketPlan.build(
                AiPacketPlanTest.STUDY,
                List.of(
                    AiPacketPlanTest.series("601", "T2 SAG", AiPacketPlanTest.sagittals(-2, 0, 2)),
                    AiPacketPlanTest.series("801", "T2 AX", AiPacketPlanTest.axials(30, 20, 10))),
                Optional.empty())
            .withMarks(
                List.of(
                    new AiMarks.Mark(1, "sop[0.0, -100.0, 100.0]", null, 100, 80),
                    new AiMarks.Mark(2, "not-in-packet", null, 10, 10)));
    assertEquals(1, plan.marks().size());
    assertEquals(List.of(2), plan.unplacedMarks());

    AiPacketExporter.Result result = AiPacketExporter.export(plan, root, CLOCK);
    Path directory = result.directory();
    assertEquals(1, result.markCount());

    ObjectMapper json = new ObjectMapper();
    JsonNode blind = json.readTree(directory.resolve("request.json").toFile());
    JsonNode marked = json.readTree(directory.resolve(AiPacketExporter.MARKED_REQUEST).toFile());
    assertFalse(blind.toString().contains("-marked"));
    assertFalse(blind.at("/text/format/schema/properties").has("marks"));
    assertTrue(marked.at("/text/format/schema/properties").has("marks"));
    assertTrue(marked.get("instructions").asText().endsWith(AiPacketExporter.MARK_INSTRUCTIONS));
    assertTrue(marked.at("/input/0/content/0/text").asText().endsWith("Rings: ring 1 on S1-02."));

    // The ringed copy follows the clean image of the same slice.
    JsonNode content = marked.at("/input/0/content");
    int clean = -1;
    for (int index = 0; index < content.size(); index++) {
      if ("images/S1-02.png".equals(content.get(index).path("image_url").asText())) clean = index;
    }
    assertEquals(
        "[S1-02-marked] The image above with the radiologist's ring 1",
        content.get(clean + 1).get("text").asText());
    assertEquals("images/S1-02-marked.png", content.get(clean + 2).get("image_url").asText());
    assertEquals(blind.at("/input/0/content").size() + 2, content.size());

    // A red ring is drawn around the marked point (100, 80 in the 200-pixel source, enlarged to
    // 512).
    BufferedImage ringed = ImageIO.read(directory.resolve("images/S1-02-marked.png").toFile());
    double scale = 512 / 200.0;
    double radius = 7.5 * scale;
    int ringPixel =
        ringed.getRGB((int) Math.round(100 * scale + radius), (int) Math.round(80 * scale));
    assertTrue(
        ((ringPixel >> 16) & 0xff) > 150 && ((ringPixel >> 8) & 0xff) < 100,
        Integer.toHexString(ringPixel));
    assertTrue(
        Files.readString(directory.resolve("README.txt")).contains("not in this packet: ring [2]"));
  }

  @Test
  void squaresPixelsEnlargesSmallImagesAndEstimatesPatchTokens() {
    ImageGeometry geometry =
        new ImageGeometry(
            100,
            100,
            List.of(1.0, 0.0, 0.0, 0.0, 1.0, 0.0),
            List.of(0.0, 0.0, 0.0),
            List.of(1.0, 0.5),
            "f");

    BufferedImage squared =
        AiPacketExporter.resize(
            new BufferedImage(100, 100, BufferedImage.TYPE_BYTE_GRAY), geometry);

    // Square pixels make it 100 x 200; the short side is then enlarged to 512.
    assertEquals(512, squared.getWidth());
    assertEquals(1024, squared.getHeight());
    ImageGeometry square =
        new ImageGeometry(
            600,
            600,
            List.of(1.0, 0.0, 0.0, 0.0, 1.0, 0.0),
            List.of(0.0, 0.0, 0.0),
            List.of(0.5, 0.5),
            "f");
    BufferedImage large = new BufferedImage(600, 600, BufferedImage.TYPE_BYTE_GRAY);
    assertTrue(large == AiPacketExporter.resize(large, square));
    assertEquals(308, AiPacketExporter.imageTokens(512, 512));
    assertEquals(1229, AiPacketExporter.imageTokens(1024, 1024));
  }

  @Test
  void removesPacketsOlderThanFiveDaysAndKeepsOtherFolders() throws Exception {
    Path old = Files.createDirectories(root.resolve("ai-packet-20260920-080000-abcdef"));
    Path recent = Files.createDirectories(root.resolve("ai-packet-20261003-080000-abcdef"));
    Path unrelated = Files.createDirectories(root.resolve("notes"));
    Files.setLastModifiedTime(old, FileTime.from(CLOCK.instant().minus(Duration.ofDays(10))));
    Files.setLastModifiedTime(unrelated, FileTime.from(CLOCK.instant().minus(Duration.ofDays(10))));
    Files.setLastModifiedTime(recent, FileTime.from(CLOCK.instant().minus(Duration.ofDays(1))));

    AiPacketPlan plan =
        AiPacketPlan.build(
            AiPacketPlanTest.STUDY,
            List.of(AiPacketPlanTest.series("601", "T2 SAG", AiPacketPlanTest.sagittals(-2, 0, 2))),
            Optional.empty());
    AiPacketExporter.Result result = AiPacketExporter.export(plan, root, CLOCK);

    assertFalse(Files.exists(old));
    assertTrue(Files.exists(recent));
    assertTrue(Files.exists(unrelated));
    BufferedImage written = ImageIO.read(result.directory().resolve("images/S1-01.png").toFile());
    assertEquals(512, written.getWidth());
  }
}
