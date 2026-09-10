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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrainingCaseStoreTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  @TempDir Path temporary;

  @Test
  void roundTripsDraftEditorStateAndUnannotatedImageSeparatelyFromArrows() throws Exception {
    TrainingCaseStore store = store();
    ReportDraft draft = draft("study-one");
    FindingEntry finding =
        FindingEntry.create("T2 hyperint lesion L4 likely intraoss hemang", "", false);
    draft.addFinding(finding);
    draft.setReportInstructions("Keep exact shorthand here");
    BufferedImage pixels = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
    pixels.setRGB(16, 16, Color.GREEN.getRGB());
    KeyImageCapture image =
        KeyImageCapture.create(
                reference("", 4), pixels, new ArrowPlacement(.1, .1, .5, .5), "L4 lesion")
            .withFindingLink(finding.id(), "selected_finding");
    draft.addKeyImage(image);
    ObjectNode editor = MAPPER.createObjectNode().put("pendingText", "not yet added");
    editor.putObject("levels").put("L4-L5", true);
    TrainingCaseSnapshot snapshot = TrainingCaseSnapshot.capture(draft, "LUMBAR_SPINE", editor);
    editor.put("pendingText", "changed after snapshot");
    image.setCaption("changed after snapshot");

    Path latest = store.save(snapshot);
    TrainingCaseStore.RestoredCase restored =
        new TrainingCaseStore(temporary.resolve("store")).load("study-one").orElseThrow();

    assertEquals("not yet added", restored.editorState().path("pendingText").asText());
    assertEquals("LUMBAR_SPINE", restored.exam());
    assertFalse(restored.annotationComplete());
    assertEquals(draft.findings(), restored.draft().findings());
    assertEquals("Keep exact shorthand here", restored.draft().reportInstructions());
    KeyImageCapture restoredImage = restored.draft().keyImages().getFirst();
    assertEquals("L4 lesion.", restoredImage.caption());
    assertEquals(image.arrows(), restoredImage.arrows());
    assertEquals(image.reference(), restoredImage.reference());
    assertEquals(finding.id(), restoredImage.findingId());
    assertEquals(image.capturedAt(), restoredImage.capturedAt());
    JsonNode manifest = MAPPER.readTree(latest.toFile());
    assertEquals(MriFindingCatalog.CATALOG_VERSION, manifest.path("catalogVersion").asText());
    Path png =
        latest.getParent().resolve(manifest.at("/content/keyImages/0/baseImagePath").asText());
    assertEquals(Color.GREEN.getRGB(), ImageIO.read(png.toFile()).getRGB(16, 16));
    assertFalse(manifest.at("/content/keyImages/0/baseImageContainsComposerArrows").asBoolean());
    assertTrue(latest.getParent().getFileName().toString().matches("[a-f0-9]{64}"));
    assertFalse(latest.toString().contains("PATIENT"));
  }

  @Test
  void completedRevisionsAreImmutableAndOnlyChangedContentBecomesDraft() throws Exception {
    TrainingCaseStore store = store();
    ReportDraft draft = draft("study-one");
    TrainingCaseSnapshot original = TrainingCaseSnapshot.capture(draft, "LUMBAR_SPINE", null);
    Path revision = store.complete(original);
    byte[] completedBytes = Files.readAllBytes(revision);

    store.save(TrainingCaseSnapshot.capture(draft, "LUMBAR_SPINE", null));
    assertTrue(store.load("study-one").orElseThrow().annotationComplete());
    assertEquals(revision, store.complete(original));
    assertArrayEquals(completedBytes, Files.readAllBytes(revision));

    draft.setReportInstructions("New reviewed instruction");
    store.save(TrainingCaseSnapshot.capture(draft, "LUMBAR_SPINE", null));
    assertFalse(store.load("study-one").orElseThrow().annotationComplete());
    assertArrayEquals(completedBytes, Files.readAllBytes(revision));
    Path next = store.complete(TrainingCaseSnapshot.capture(draft, "LUMBAR_SPINE", null));
    assertNotEquals(revision, next);
    try (var revisions = Files.list(revision.getParent())) {
      assertEquals(2, revisions.count());
    }
  }

  @Test
  void editorPresentationChangesPreserveCompletionButPendingTextInvalidatesIt() throws Exception {
    TrainingCaseStore store = store();
    ReportDraft draft = draft("study-one");
    store.complete(
        TrainingCaseSnapshot.capture(
            draft, "LUMBAR_SPINE", MAPPER.createObjectNode().put("structuredFormMode", true)));
    ObjectNode editor = MAPPER.createObjectNode().put("structuredFormMode", false);
    editor.put("editingFindingId", "selected-finding");
    store.save(TrainingCaseSnapshot.capture(draft, "LUMBAR_SPINE", editor));
    assertTrue(store.load("study-one").orElseThrow().annotationComplete());
    assertEquals(editor, store.load("study-one").orElseThrow().editorState());

    editor.put("pendingGenericInput", true);
    editor.put("findingText", "new unadded finding");
    store.save(TrainingCaseSnapshot.capture(draft, "LUMBAR_SPINE", editor));
    assertFalse(store.load("study-one").orElseThrow().annotationComplete());
  }

  @Test
  void lateExportPreservesNewerDraftAndLateDraftCannotOverwriteIt() throws Exception {
    TrainingCaseStore store = store();
    ReportDraft draft = draft("study-one");
    draft.setReportInstructions("exported content");
    TrainingCaseSnapshot exported = TrainingCaseSnapshot.capture(draft, "LUMBAR_SPINE", null);
    draft.setReportInstructions("newer content typed during export");
    TrainingCaseSnapshot newer = TrainingCaseSnapshot.capture(draft, "LUMBAR_SPINE", null);
    store.save(newer);

    Path revision = store.complete(exported);
    assertEquals(
        "exported content",
        MAPPER.readTree(revision.toFile()).at("/content/reportInstructions").asText());
    assertEquals(
        "newer content typed during export",
        store.load("study-one").orElseThrow().draft().reportInstructions());
    assertFalse(store.load("study-one").orElseThrow().annotationComplete());
    store.save(exported);
    assertEquals(
        "newer content typed during export",
        store.load("study-one").orElseThrow().draft().reportInstructions());
  }

  @Test
  void lateExportCompletesMatchingAnnotationsWithoutLosingNewerEditorState() throws Exception {
    TrainingCaseStore store = store();
    ReportDraft draft = draft("study-one");
    TrainingCaseSnapshot exported = TrainingCaseSnapshot.capture(draft, "LUMBAR_SPINE", null);
    ObjectNode editor = MAPPER.createObjectNode().put("structuredFormMode", false);
    store.save(TrainingCaseSnapshot.capture(draft, "LUMBAR_SPINE", editor));

    store.complete(exported);

    assertTrue(store.load("study-one").orElseThrow().annotationComplete());
    assertEquals(editor, store.load("study-one").orElseThrow().editorState());
  }

  @Test
  void corruptManifestIsPreservedAndCannotBeSilentlyReplaced() throws Exception {
    TrainingCaseStore store = store();
    ReportDraft draft = draft("study-one");
    Path latest = store.save(TrainingCaseSnapshot.capture(draft, "LUMBAR_SPINE", null));
    byte[] broken =
        "{corrupt record containing private patient text"
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    Files.write(latest, broken);

    IOException readError = assertThrows(IOException.class, () -> store.load("study-one"));
    assertFalse(readError.getMessage().contains("private patient"));
    assertEquals(null, readError.getCause());
    assertThrows(
        IOException.class,
        () ->
            new TrainingCaseStore(temporary.resolve("store"))
                .save(TrainingCaseSnapshot.capture(draft, "LUMBAR_SPINE", null)));
    assertArrayEquals(broken, Files.readAllBytes(latest));
  }

  @Test
  void missingSavedImagePreventsDestructiveOverwrite() throws Exception {
    TrainingCaseStore store = store();
    ReportDraft draft = draft("study-one");
    draft.addKeyImage(
        KeyImageCapture.create(
            reference("", 0), new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), null, ""));
    Path latest = store.save(TrainingCaseSnapshot.capture(draft, "LUMBAR_SPINE", null));
    byte[] original = Files.readAllBytes(latest);
    JsonNode manifest = MAPPER.readTree(original);
    Files.delete(
        latest.getParent().resolve(manifest.at("/content/keyImages/0/baseImagePath").asText()));
    draft.setReportInstructions("new draft text");

    assertThrows(IOException.class, () -> store.load("study-one"));
    assertThrows(
        IOException.class,
        () -> store.save(TrainingCaseSnapshot.capture(draft, "LUMBAR_SPINE", null)));
    assertArrayEquals(original, Files.readAllBytes(latest));
  }

  @Test
  void archivesEachSourceFileOnceAndKeepsFrameMappingsAndMissingReferences() throws Exception {
    TrainingCaseStore store = store();
    Path original = temporary.resolve("original.dcm");
    byte[] pixels = {0, 1, 2, 3, 4, 5};
    Files.write(original, pixels);
    ReportDraft draft = draft("study-one");
    List<StudySourceInventory.SourceImageFile> sources =
        List.of(
            new StudySourceInventory.SourceImageFile(
                "study-one", reference(original.toUri().toString(), 0)),
            new StudySourceInventory.SourceImageFile(
                "study-one", reference(original.toUri().toString(), 1)),
            new StudySourceInventory.SourceImageFile(
                "study-one", reference(temporary.resolve("missing.dcm").toUri().toString(), 2)));
    TrainingCaseSnapshot snapshot =
        TrainingCaseSnapshot.capture(draft, "LUMBAR_SPINE", null).withSources(sources);
    Path latest = store.save(snapshot);
    assertFalse(Files.exists(latest.getParent().resolve("dicom")));

    Path revision = store.complete(snapshot);
    JsonNode archive = MAPPER.readTree(revision.toFile()).get("sourceArchive");
    assertEquals("partial", archive.path("status").asText());
    assertEquals("not_verified", archive.path("originalStudyCompleteness").asText());
    assertEquals(3, archive.path("referenceCount").asInt());
    assertEquals(2, archive.path("archivedReferenceCount").asInt());
    assertEquals(1, archive.at("/instances/1/reference/sourceFrameIndex").asInt());
    assertEquals(archive.at("/instances/0/archivedPath"), archive.at("/instances/1/archivedPath"));
    Path archived = latest.getParent().resolve(archive.at("/instances/0/archivedPath").asText());
    Files.delete(original);
    assertArrayEquals(pixels, Files.readAllBytes(archived));
    try (var files = Files.list(archived.getParent())) {
      assertEquals(1, files.count());
    }
  }

  @Test
  void doesNotArchiveNonLumbarExamsOrFollowSourceSymlinks() throws Exception {
    TrainingCaseStore store = store();
    Path source = temporary.resolve("source.dcm");
    Files.writeString(source, "pixels");
    Path symbolic = temporary.resolve("linked.dcm");
    Files.createSymbolicLink(symbolic, source);
    ReportDraft draft = draft("study-one");
    var inventory =
        List.of(
            new StudySourceInventory.SourceImageFile(
                "study-one", reference(symbolic.toUri().toString(), 0)));
    Path brain =
        store.complete(TrainingCaseSnapshot.capture(draft, "BRAIN", null).withSources(inventory));
    assertFalse(MAPPER.readTree(brain.toFile()).has("sourceArchive"));

    Path lumbar =
        store.complete(
            TrainingCaseSnapshot.capture(draft, "LUMBAR_SPINE", null).withSources(inventory));
    assertEquals(
        "source_unavailable",
        MAPPER.readTree(lumbar.toFile()).at("/sourceArchive/instances/0/status").asText());
  }

  @Test
  void refusesLinkedStudyDirectoryAndRestrictsLocalFilePermissions() throws Exception {
    TrainingCaseStore store = store();
    ReportDraft draft = draft("study-one");
    Path latest = store.save(TrainingCaseSnapshot.capture(draft, "LUMBAR_SPINE", null));
    if (Files.getFileStore(latest).supportsFileAttributeView("posix")) {
      assertEquals(
          PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(latest));
      assertEquals(
          PosixFilePermissions.fromString("rwx------"),
          Files.getPosixFilePermissions(latest.getParent()));
    }
    Path other = store.studyDirectory("other-study");
    Files.createSymbolicLink(other, latest.getParent());
    assertThrows(IOException.class, () -> store.load("other-study"));
    assertThrows(
        IOException.class,
        () -> store.save(TrainingCaseSnapshot.capture(draft("other-study"), "LUMBAR_SPINE", null)));
  }

  private TrainingCaseStore store() {
    return new TrainingCaseStore(temporary.resolve("store"));
  }

  static ReportDraft draft(String uid) {
    return new ReportDraft(
        new CaseContext(uid, "PATIENT^TEST", "identifier", "", "", "", "MR LUMBAR"));
  }

  private static ImageReference reference(String uri, int frame) {
    return new ImageReference(
        "series", "instance", "1", "T2", "1", frame + 1, 4, frame, uri, ImageGeometry.empty());
  }
}
