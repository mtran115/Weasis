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

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;

public final class CasePacketExporter {
  public ExportResult export(ReportPacket packet, Path outputDirectory) throws IOException {
    if (packet == null || !packet.context().hasStudy()) {
      throw new IllegalArgumentException("An active DICOM study is required.");
    }
    if (packet.isEmpty()) {
      throw new IllegalArgumentException("Add a finding or key image before exporting.");
    }

    Path outputRoot = outputDirectory.toAbsolutePath().normalize();
    Files.createDirectories(outputRoot);
    if (!Files.isDirectory(outputRoot) || !Files.isWritable(outputRoot)) {
      throw new IOException("The selected transcription folder is not writable.");
    }

    Path finalDirectory = uniqueCaseDirectory(outputRoot, packet.context().caseFolderStem());
    Path temporaryDirectory =
        Files.createDirectory(outputRoot.resolve(".report-composer-" + UUID.randomUUID()));
    try {
      Files.writeString(
          temporaryDirectory.resolve("REPORT_TEXT.txt"),
          ReportTextFormatter.format(packet),
          StandardCharsets.UTF_8);

      List<ExportedKeyImage> exportedImages = exportKeyImages(packet, temporaryDirectory);
      DocxInstructionWriter.write(
          temporaryDirectory.resolve("TRANSCRIPTION_INSTRUCTIONS.docx"), packet, exportedImages);
      moveCompletedPacket(temporaryDirectory, finalDirectory);
      return new ExportResult(finalDirectory, exportedImages.size());
    } catch (IOException | RuntimeException error) {
      deleteRecursively(temporaryDirectory);
      throw error;
    }
  }

  private static List<ExportedKeyImage> exportKeyImages(
      ReportPacket packet, Path temporaryDirectory) throws IOException {
    if (packet.keyImages().isEmpty()) {
      return List.of();
    }

    Path keyImageDirectory = Files.createDirectory(temporaryDirectory.resolve("KEY_IMAGES"));
    List<ExportedKeyImage> exported = new ArrayList<>();
    for (int index = 0; index < packet.keyImages().size(); index++) {
      KeyImageCapture keyImage = packet.keyImages().get(index);
      int number = index + 1;
      String fileName =
          "KI-"
              + String.format("%02d", number)
              + " - "
              + keyImage.reference().fileReference()
              + ".png";
      Path imagePath = keyImageDirectory.resolve(fileName);
      BufferedImage rendered = keyImage.renderedImage();
      if (!ImageIO.write(rendered, "png", imagePath.toFile())) {
        throw new IOException("The key image PNG could not be written.");
      }
      exported.add(
          new ExportedKeyImage(
              number,
              imagePath,
              fileName,
              keyImage.reference().humanReference(),
              keyImage.caption(),
              rendered.getWidth(),
              rendered.getHeight()));
    }
    return List.copyOf(exported);
  }

  private static Path uniqueCaseDirectory(Path outputRoot, String requestedName) {
    String baseName =
        requestedName.length() > 180 ? requestedName.substring(0, 180).strip() : requestedName;
    Path candidate = outputRoot.resolve(baseName);
    int suffix = 2;
    while (Files.exists(candidate)) {
      candidate = outputRoot.resolve(baseName + " - " + suffix++);
    }
    return candidate;
  }

  private static void moveCompletedPacket(Path source, Path destination) throws IOException {
    try {
      Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException error) {
      Files.move(source, destination);
    }
  }

  private static void deleteRecursively(Path directory) {
    if (directory == null || !Files.exists(directory)) {
      return;
    }
    try (var paths = Files.walk(directory)) {
      paths.sorted(Comparator.reverseOrder()).forEach(CasePacketExporter::deleteQuietly);
    } catch (IOException ignored) {
      // Preserve the original export failure.
    }
  }

  private static void deleteQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException ignored) {
      // Preserve the original export failure.
    }
  }

  public record ExportResult(Path caseDirectory, int keyImageCount) {}

  record ExportedKeyImage(
      int number,
      Path path,
      String fileName,
      String imageReference,
      String caption,
      int width,
      int height) {}
}
