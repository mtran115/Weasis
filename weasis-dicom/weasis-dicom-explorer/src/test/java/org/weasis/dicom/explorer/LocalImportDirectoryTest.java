/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.imp.DicomZipMediaIO;

class LocalImportDirectoryTest {
  @TempDir Path temporaryDirectory;

  @Test
  void recursiveImportRetainsSelectedBatchFolderInsteadOfSeriesFolder() throws Exception {
    Path root = Files.createDirectory(temporaryDirectory.resolve("batch"));
    Path image = root.resolve("study/series/image.dcm");
    Fixture f = new Fixture();
    LoadLocalDicom task = f.loader(root);
    task.recordImportDirectory(f.study, sourceUri(image));
    assertEquals(Optional.of(root), LocalImportDirectory.find(f.series));
    assertFalse(Files.exists(root.resolve("notes")));
  }

  @Test
  void eachSelectedFolderIsAssociatedWithItsOwnStudy() throws Exception {
    Path first = Files.createDirectory(temporaryDirectory.resolve("first"));
    Path second = Files.createDirectory(temporaryDirectory.resolve("second"));
    Fixture a = new Fixture();
    Fixture b = new Fixture();
    LoadLocalDicom task = a.loader(first, second);
    task.recordImportDirectory(a.study, sourceUri(first.resolve("nested/one.dcm")));
    task.recordImportDirectory(b.study, sourceUri(second.resolve("nested/two.dcm")));
    assertEquals(Optional.of(first), LocalImportDirectory.find(a.series));
    assertEquals(Optional.of(second), LocalImportDirectory.find(b.series));
  }

  @Test
  void individualFileSelectionUsesItsParentAndOverlappingFoldersUseOuterSelectedRoot()
      throws Exception {
    Fixture f = new Fixture();
    Path image = Files.createFile(temporaryDirectory.resolve("image.dcm"));
    f.loader(image).recordImportDirectory(f.study, sourceUri(image));
    assertEquals(Optional.of(temporaryDirectory), LocalImportDirectory.find(f.series));
    Path nested = temporaryDirectory.resolve("nested");
    assertEquals(
        Optional.of(temporaryDirectory),
        LocalImportDirectory.forFile(
            nested.resolve("image.dcm"), List.of(nested, temporaryDirectory)));
    assertTrue(
        LocalImportDirectory.forFile(
                temporaryDirectory.resolveSibling("different/image.dcm"),
                List.of(temporaryDirectory))
            .isEmpty());
  }

  @Test
  void duplicateSourcesDoNotAccumulateButMultipleImportRootsRequireExplicitChoice() {
    Fixture f = new Fixture();
    LocalImportDirectory.record(f.study, temporaryDirectory.resolve("first"));
    LocalImportDirectory.record(f.study, temporaryDirectory.resolve("first/./"));
    assertEquals(
        Optional.of(temporaryDirectory.resolve("first")), LocalImportDirectory.find(f.series));
    LocalImportDirectory.record(f.study, temporaryDirectory.resolve("second"));
    assertTrue(LocalImportDirectory.find(f.series).isEmpty());
  }

  @Test
  void unknownRemoteAndTemporarySourcesHaveNoLocalDefault() {
    Fixture f = new Fixture();
    assertTrue(LocalImportDirectory.find(f.series).isEmpty());
    assertTrue(LocalImportDirectory.find(null).isEmpty());
    assertTrue(LocalImportDirectory.find(new DicomSeries("unattached")).isEmpty());
    LocalImportDirectory.record(f.study, AppProperties.APP_TEMP_DIR.resolve("cache/remote"));
    assertTrue(LocalImportDirectory.find(f.series).isEmpty());
  }

  @Test
  void archiveOverrideKeepsOriginalFolderInsteadOfExtractionCache() {
    Fixture f = new Fixture();
    Path extracted = AppProperties.APP_TEMP_DIR.resolve("zip/extracted");
    LoadLocalDicom task = f.loader(extracted);
    task.setImportDirectory(temporaryDirectory);
    task.recordImportDirectory(f.study, sourceUri(extracted.resolve("series/image.dcm")));
    assertEquals(Optional.of(temporaryDirectory), LocalImportDirectory.find(f.series));
  }

  @Test
  void rootDirectoryIsNotAnAutomaticDestination() {
    Fixture f = new Fixture();
    LocalImportDirectory.record(f.study, temporaryDirectory.getRoot());
    assertTrue(LocalImportDirectory.find(f.series).isEmpty());
  }

  @Test
  void wordReportsAreNotReimportedAsDicomZipArchives() throws Exception {
    Path report = temporaryDirectory.resolve("TRANSCRIPTION_INSTRUCTIONS.docx");
    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(report))) {
      zip.putNextEntry(new ZipEntry("word/document.xml"));
      zip.write("<document/>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    assertTrue(
        MimeInspector.isMatchingMimeTypeFromMagicNumber(
            report.toFile(), DicomZipMediaIO.MIME_TYPE));
    assertFalse(LoadLocalDicom.isDicomZip(report.toFile()));
    Path archive = Files.copy(report, temporaryDirectory.resolve("study.zip"));
    assertTrue(LoadLocalDicom.isDicomZip(archive.toFile()));
    Path withoutExtension = Files.copy(report, temporaryDirectory.resolve("archive"));
    assertTrue(LoadLocalDicom.isDicomZip(withoutExtension.toFile()));
  }

  @Test
  void archiveDicomDirKeepsOriginalFolderAndClearsOldStudiesBeforeParsingNewOnes()
      throws Exception {
    Path archive = temporaryDirectory.resolve("study.zip");
    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
      zip.putNextEntry(new ZipEntry("DICOMDIR"));
      zip.write(new byte[] {0});
      zip.closeEntry();
    }
    DicomModel model = mock(DicomModel.class);
    java.util.concurrent.atomic.AtomicReference<Path> extraction =
        new java.util.concurrent.atomic.AtomicReference<>();
    try (var loaders =
        mockConstruction(
            org.weasis.dicom.explorer.imp.DicomDirLoader.class,
            (loader, context) -> {
              extraction.set(((File) context.arguments().getFirst()).toPath().getParent());
              assertEquals(temporaryDirectory, context.arguments().get(3));
              when(loader.readDicomDir()).thenReturn(List.of());
            })) {
      DicomZipMediaIO.loadDicomZip(
          archive.toFile(), model, HangingProtocols.OpeningViewer.NONE, null, true);
      assertEquals(1, loaders.constructed().size());
      var order = inOrder(model, loaders.constructed().getFirst());
      order.verify(model).removeAllPatientsAndCloseViewers();
      order.verify(loaders.constructed().getFirst()).readDicomDir();
      verify(model, times(1)).removeAllPatientsAndCloseViewers();
    } finally {
      if (extraction.get() != null) {
        try (var files = Files.walk(extraction.get())) {
          for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList())
            Files.deleteIfExists(file);
        }
      }
    }
  }

  private static java.net.URI sourceUri(Path file) {
    return file.toUri();
  }

  private static class Fixture {
    final DicomModel model = mock(DicomModel.class);
    final MediaSeriesGroup study =
        new MediaSeriesGroupNode(
            TagD.getUID(TagD.Level.STUDY), "1.2.3", DicomModel.study.tagView());
    final DicomSeries series = new DicomSeries("1.2.3.4");

    Fixture() {
      series.setTag(TagW.ExplorerModel, model);
      when(model.getParent(series, DicomModel.study)).thenReturn(study);
    }

    LoadLocalDicom loader(Path... paths) {
      File[] files = java.util.Arrays.stream(paths).map(Path::toFile).toArray(File[]::new);
      return new LoadLocalDicom(files, true, model, mock(PluginOpeningStrategy.class));
    }
  }
}
