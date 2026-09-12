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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

class CasePacketExporterTest {
  private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
  private static final String KEY_IMAGE_CAPTION = "Arrow points to the dorsal component.";

  @TempDir Path temporaryDirectory;

  @Test
  void exportsDriveReadyPacketWithValidDocxAndAnnotatedPng() throws Exception {
    ReportPacket packet = samplePacket();
    CasePacketExporter exporter = new CasePacketExporter();

    CasePacketExporter.ExportResult result = exporter.export(packet, temporaryDirectory);

    assertEquals(1, result.keyImageCount());
    assertEquals(
        "DOE_JANE - MRI WRIST LEFT - 20260808", result.caseDirectory().getFileName().toString());
    Path reportText = result.caseDirectory().resolve("REPORT_TEXT.txt");
    Path docx = result.caseDirectory().resolve("TRANSCRIPTION_INSTRUCTIONS.docx");
    Path png =
        result.caseDirectory().resolve("KEY_IMAGES").resolve("KI-01 - SERIES 8 IMAGE 23.png");
    assertTrue(Files.isRegularFile(reportText));
    assertTrue(Files.isRegularFile(docx));
    assertTrue(Files.isRegularFile(png));
    String exportedText = Files.readString(reportText);
    assertTrue(exportedText.contains("Patient: DOE, JANE"));
    assertTrue(exportedText.contains("REPORT TEXT / INSTRUCTIONS"));
    assertFalse(exportedText.contains("\nFINDINGS\n"));
    assertFalse(exportedText.contains("\nIMPRESSION\n"));
    assertFalse(exportedText.contains("ADDITIONAL REPORT TEXT / INSTRUCTIONS"));
    assertTrue(exportedText.contains("Series 8 (COR PD FS), image 23"));
    assertTrue(exportedText.contains("Compare directly with the prior MRI."));
    assertFalse(exportedText.contains(KEY_IMAGE_CAPTION));
    assertEquals(KEY_IMAGE_CAPTION, packet.keyImages().getFirst().caption());
    assertNotNull(ImageIO.read(png.toFile()));
    assertFalse(hasTemporaryExportDirectory(temporaryDirectory));

    verifyDocx(docx);

    CasePacketExporter.ExportResult duplicate = exporter.export(packet, temporaryDirectory);
    assertEquals(
        "DOE_JANE - MRI WRIST LEFT - 20260808 - 2",
        duplicate.caseDirectory().getFileName().toString());
  }

  @Test
  void exportsIntoAutomaticallyCreatedNotesWithoutChangingOtherNotes() throws Exception {
    TranscriptionDestination policy = new TranscriptionDestination();
    var destination =
        policy.resolve("study", java.util.Optional.of(temporaryDirectory)).orElseThrow();
    Path notes = TranscriptionDestination.prepare(destination);
    Path existing = Files.writeString(notes.resolve("existing.txt"), "keep me");
    var result = new CasePacketExporter().export(samplePacket(), notes);
    assertEquals(notes, result.caseDirectory().getParent());
    assertTrue(
        Files.isRegularFile(result.caseDirectory().resolve("TRANSCRIPTION_INSTRUCTIONS.docx")));
    assertEquals("keep me", Files.readString(existing));
  }

  private static ReportPacket samplePacket() {
    CaseContext context =
        new CaseContext(
            "1.2.840.10008.1",
            "DOE^JANE",
            "MRN123",
            "19800101",
            "ACC456",
            "20260808",
            "MRI WRIST LEFT");
    FindingEntry finding =
        new FindingEntry(
            "finding-1",
            "Tear of the lunotriquetral ligament",
            "Lunotriquetral ligament tear",
            true);
    ImageReference reference =
        new ImageReference("1.2.840.10008.2", "1.2.840.10008.3", "8", "COR PD FS", "23", 23, 36);
    BufferedImage image = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();
    graphics.setColor(new Color(18, 20, 24));
    graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
    graphics.setColor(new Color(150, 155, 160));
    graphics.fillOval(170, 70, 300, 340);
    graphics.dispose();
    KeyImageCapture keyImage =
        new KeyImageCapture(
            "key-image-1",
            reference,
            image,
            new ArrowPlacement(0.2, 0.75, 0.55, 0.45),
            KEY_IMAGE_CAPTION);
    return new ReportPacket(
        context, List.of(finding), List.of(keyImage), "Compare directly with the prior MRI.");
  }

  private static void verifyDocx(Path docx) throws Exception {
    try (ZipFile zip = new ZipFile(docx.toFile())) {
      Set<String> entries =
          Collections.list(zip.entries()).stream()
              .map(ZipEntry::getName)
              .collect(Collectors.toSet());
      assertTrue(entries.contains("word/document.xml"));
      assertTrue(entries.contains("word/styles.xml"));
      assertTrue(entries.contains("word/media/image1.png"));

      for (String name : entries) {
        if (name.endsWith(".xml") || name.endsWith(".rels")) {
          try (InputStream input = zip.getInputStream(zip.getEntry(name))) {
            parseXml(input);
          }
        }
      }

      String documentXml = read(zip, "word/document.xml");
      assertTrue(documentXml.contains("TRANSCRIPTION INSTRUCTIONS"));
      assertTrue(documentXml.contains("Tear of the lunotriquetral ligament."));
      assertTrue(documentXml.contains("Compare directly with the prior MRI."));
      assertTrue(documentXml.contains("r:embed=\"rId2\""));
      assertFalse(documentXml.contains(KEY_IMAGE_CAPTION));

      try (InputStream input = zip.getInputStream(zip.getEntry("word/styles.xml"))) {
        Document styles = parseXml(input);
        Element name = (Element) styles.getElementsByTagNameNS(W, "name").item(0);
        assertNotNull(name);
        assertEquals("Normal", name.getAttributeNS(W, "val"));
      }
    }
  }

  private static Document parseXml(InputStream input) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    return factory.newDocumentBuilder().parse(input);
  }

  private static String read(ZipFile zip, String name) throws IOException {
    try (InputStream input = zip.getInputStream(zip.getEntry(name))) {
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static boolean hasTemporaryExportDirectory(Path directory) throws IOException {
    try (var children = Files.list(directory)) {
      return children.anyMatch(
          child -> child.getFileName().toString().startsWith(".report-composer-"));
    }
  }
}
