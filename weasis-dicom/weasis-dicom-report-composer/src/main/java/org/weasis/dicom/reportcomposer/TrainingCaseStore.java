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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import javax.imageio.ImageIO;
import org.weasis.core.api.gui.util.AppProperties;

/** Local identifiable training records. This directory is never a clinical export destination. */
public final class TrainingCaseStore {
  static final int SCHEMA_VERSION = 1;
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final Path root;
  private final Map<Object, EncodedImage> imageCache = new WeakHashMap<>();

  public TrainingCaseStore() {
    this(AppProperties.WEASIS_PATH.resolve("data/report-composer/training-v1"));
  }

  public TrainingCaseStore(Path root) {
    this.root = root.toAbsolutePath().normalize();
  }

  public record RestoredCase(
      ReportDraft draft, String exam, JsonNode editorState, boolean annotationComplete) {}

  public synchronized Optional<RestoredCase> load(String studyKey) throws IOException {
    try {
      Path directory = studyDirectory(studyKey);
      Path latest = safeChild(directory, "latest.json");
      if (!Files.exists(latest, LinkOption.NOFOLLOW_LINKS)) {
        return Optional.empty();
      }
      return Optional.of(restore(readManifest(latest, studyKey), directory, studyKey));
    } catch (Exception error) {
      throw storageFailure("The saved local case could not be read; it has been preserved.");
    }
  }

  public synchronized Path save(TrainingCaseSnapshot snapshot) throws IOException {
    return write(snapshot, false, null);
  }

  public Path complete(TrainingCaseSnapshot snapshot) throws IOException {
    ObjectNode sourceArchive = null;
    if ("LUMBAR_SPINE".equals(snapshot.exam())) {
      try {
        Path directory = studyDirectory(snapshot.studyKey());
        privateDirectory(root);
        privateDirectory(directory);
        // Source files may be large. Do not hold the draft/load lock while copying them.
        sourceArchive = archiveSources(snapshot, directory);
      } catch (Exception error) {
        throw storageFailure(
            "The local source archive could not be saved; the draft is preserved.");
      }
    }
    synchronized (this) {
      return write(snapshot, true, sourceArchive);
    }
  }

  private Path write(TrainingCaseSnapshot snapshot, boolean completed, ObjectNode sourceArchive)
      throws IOException {
    try {
      UUID.fromString(snapshot.revisionId());
      Instant.parse(snapshot.capturedAt());
      Path directory = studyDirectory(snapshot.studyKey());
      privateDirectory(root);
      privateDirectory(directory);
      Path latest = safeChild(directory, "latest.json");
      ObjectNode previous = null;
      if (Files.exists(latest, LinkOption.NOFOLLOW_LINKS)) {
        previous = readManifest(latest, snapshot.studyKey());
        // Never replace an unreadable case, including one with missing or damaged key images.
        validateSavedImages(previous.get("content"), directory);
      }

      ObjectNode content = content(snapshot, directory);
      String fingerprint = digest(MAPPER.writeValueAsBytes(content));
      String annotationFingerprint = annotationFingerprint(content);
      boolean newerDraftExists =
          previous != null
              && Instant.parse(previous.path("capturedAt").asText())
                  .isAfter(Instant.parse(snapshot.capturedAt()));
      if (!completed && newerDraftExists) {
        return latest;
      }
      if (!completed
          && previous != null
          && fingerprint.equals(previous.path("contentFingerprint").asText())) {
        return latest;
      }
      boolean sameAnnotation =
          previous != null
              && annotationFingerprint.equals(previous.path("annotationFingerprint").asText());
      boolean annotationComplete =
          completed || sameAnnotation && previous.path("annotationComplete").asBoolean();

      ObjectNode manifest = MAPPER.createObjectNode();
      manifest.put("schemaVersion", SCHEMA_VERSION);
      manifest.put("catalogVersion", MriFindingCatalog.CATALOG_VERSION);
      manifest.put("revisionId", snapshot.revisionId());
      manifest.put("capturedAt", snapshot.capturedAt());
      manifest.put("savedAt", Instant.now().toString());
      manifest.put("annotationComplete", annotationComplete);
      manifest.put("status", annotationComplete ? "annotation_complete" : "draft");
      manifest.put("dataClassification", "local_identifiable_clinical_data");
      manifest.put("clinicalValidation", "not_validated_for_ai_diagnosis");
      manifest.put("contentFingerprint", fingerprint);
      manifest.put("annotationFingerprint", annotationFingerprint);
      if (annotationComplete) {
        manifest.put(
            "completionRevisionId",
            completed ? snapshot.revisionId() : previous.path("completionRevisionId").asText());
      }
      manifest.set("content", content);
      manifest.set(
          "trainingLabels",
          !completed && annotationComplete
              ? previous.get("trainingLabels")
              : LumbarTrainingLabels.summarize(
                  snapshot.packet(), snapshot.exam(), annotationComplete));
      if (sourceArchive != null) {
        manifest.set("sourceArchive", sourceArchive);
      } else if (previous != null && previous.has("sourceArchive")) {
        manifest.set("sourceArchive", previous.get("sourceArchive"));
      }

      byte[] bytes = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest);
      if (completed) {
        Path revisions = safeChild(directory, "revisions");
        privateDirectory(revisions);
        Path revision = safeChild(revisions, snapshot.revisionId() + ".json");
        // A retry of this snapshot must not change its immutable completed revision.
        if (Files.exists(revision, LinkOption.NOFOLLOW_LINKS)) {
          ObjectNode existing = readManifest(revision, snapshot.studyKey());
          if (!fingerprint.equals(existing.path("contentFingerprint").asText())
              || !existing.path("annotationComplete").asBoolean()) {
            throw storageFailure("A local revision already exists with different content.");
          }
          bytes = Files.readAllBytes(revision);
        } else {
          atomicWrite(revision, bytes);
        }
        if (!newerDraftExists) {
          atomicWrite(latest, bytes);
        } else if (sameAnnotation) {
          // Export completed while only editor presentation changed: retain that newer UI state.
          ObjectNode latestState = previous.deepCopy();
          JsonNode completion = MAPPER.readTree(bytes);
          latestState.put("annotationComplete", true);
          latestState.put("status", "annotation_complete");
          latestState.put("completionRevisionId", snapshot.revisionId());
          latestState.set("trainingLabels", completion.get("trainingLabels"));
          if (completion.has("sourceArchive")) {
            latestState.set("sourceArchive", completion.get("sourceArchive"));
          }
          atomicWrite(
              latest, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(latestState));
        }
        return revision;
      }
      atomicWrite(latest, bytes);
      return latest;
    } catch (Exception error) {
      // Paths, report text, and patient identifiers from nested exceptions must not reach logs/UI.
      throw storageFailure(
          "The local case could not be saved; any previous record has been preserved.");
    }
  }

  private ObjectNode content(TrainingCaseSnapshot snapshot, Path directory) throws IOException {
    ReportPacket packet = snapshot.packet();
    ObjectNode node = MAPPER.createObjectNode();
    node.put("studyKey", snapshot.studyKey());
    node.put("exam", snapshot.exam());
    node.set("context", MAPPER.valueToTree(packet.context()));
    node.set("findings", MAPPER.valueToTree(packet.findings()));
    node.put("reportInstructions", packet.reportInstructions());
    node.set("editorState", snapshot.editorState());
    ArrayNode images = node.putArray("keyImages");
    for (KeyImageCapture image : packet.keyImages()) {
      EncodedImage encoded = imageCache.get(image.baseImageIdentity());
      if (encoded == null) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image.baseImageCopy(), "png", output)) {
          throw storageFailure("The key image could not be encoded.");
        }
        byte[] pixels = output.toByteArray();
        encoded = new EncodedImage(digest(pixels), pixels);
        imageCache.put(image.baseImageIdentity(), encoded);
      }
      String imageDigest = encoded.checksum();
      Path imagesDirectory = safeChild(directory, "images");
      privateDirectory(imagesDirectory);
      String imagePath = "images/" + imageDigest + ".png";
      Path destination = safeChild(directory, imagePath);
      ensureAsset(destination, encoded.pixels(), imageDigest);
      ObjectNode entry = images.addObject();
      entry.put("id", image.id());
      entry.set("reference", MAPPER.valueToTree(image.reference()));
      entry.put("baseImagePath", imagePath);
      entry.put("baseImageSha256", imageDigest);
      entry.put("baseImageContainsComposerArrows", false);
      entry.set("arrows", MAPPER.valueToTree(image.arrows()));
      entry.put("caption", image.caption());
      entry.put("findingId", image.findingId());
      entry.put("findingLinkSource", image.findingLinkSource());
      entry.put("capturedAt", image.capturedAt());
      entry.set("captureGeometry", MAPPER.valueToTree(image.captureGeometry()));
      entry.set("sourceArrows", MAPPER.valueToTree(image.sourceArrows()));
    }
    return node;
  }

  private ObjectNode readManifest(Path path, String studyKey) throws IOException {
    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
      throw storageFailure("The local case manifest is not a regular file.");
    }
    JsonNode node = MAPPER.readTree(Files.readAllBytes(path));
    if (!(node instanceof ObjectNode manifest)
        || manifest.path("schemaVersion").asInt(-1) != SCHEMA_VERSION
        || !manifest.path("content").isObject()
        || !studyKey.equals(manifest.path("content").path("studyKey").asText())
        || !manifest.path("annotationComplete").isBoolean()
        || !digest(MAPPER.writeValueAsBytes(manifest.get("content")))
            .equals(manifest.path("contentFingerprint").asText())) {
      throw storageFailure("The saved local case is invalid or uses an unsupported version.");
    }
    return manifest;
  }

  private static String annotationFingerprint(ObjectNode content) throws IOException {
    ObjectNode semantic = content.deepCopy();
    JsonNode editor = semantic.remove("editorState");
    if (editor != null && editor.path("pendingGenericInput").asBoolean()) {
      // Unadded text is still a real edit, even though it has not reached the report packet yet.
      semantic.set("pendingEditorInput", editor);
    }
    return digest(MAPPER.writeValueAsBytes(semantic));
  }

  private RestoredCase restore(ObjectNode manifest, Path directory, String studyKey)
      throws IOException {
    JsonNode content = manifest.get("content");
    CaseContext context = MAPPER.treeToValue(content.get("context"), CaseContext.class);
    if (!studyKey.equals(context.draftKey())
        || !content.path("findings").isArray()
        || !content.path("keyImages").isArray()) {
      throw storageFailure("The saved local case has inconsistent study data.");
    }
    ReportDraft draft = new ReportDraft(context);
    for (JsonNode finding : content.get("findings")) {
      draft.addFinding(MAPPER.treeToValue(finding, FindingEntry.class));
    }
    draft.setReportInstructions(content.path("reportInstructions").asText());
    for (JsonNode entry : content.get("keyImages")) {
      Path imagePath = validateImage(entry, directory);
      BufferedImage image = ImageIO.read(imagePath.toFile());
      if (image == null) {
        throw storageFailure("A saved key image cannot be decoded.");
      }
      draft.addKeyImage(
          KeyImageCapture.restore(
              entry.path("id").asText(),
              MAPPER.treeToValue(entry.get("reference"), ImageReference.class),
              image,
              MAPPER.convertValue(
                  entry.get("arrows"), new TypeReference<List<ArrowPlacement>>() {}),
              entry.path("caption").asText(),
              entry.path("findingId").asText(),
              entry.path("findingLinkSource").asText(),
              entry.path("capturedAt").asText(),
              MAPPER.treeToValue(entry.get("captureGeometry"), CaptureGeometry.class),
              MAPPER.convertValue(
                  entry.get("sourceArrows"), new TypeReference<List<SourceArrowPlacement>>() {})));
    }
    return new RestoredCase(
        draft,
        content.path("exam").asText(),
        content.path("editorState").deepCopy(),
        manifest.path("annotationComplete").asBoolean());
  }

  private static void validateSavedImages(JsonNode content, Path directory) throws IOException {
    if (!content.path("keyImages").isArray() || !content.path("findings").isArray()) {
      throw storageFailure("The saved local case has inconsistent data.");
    }
    for (JsonNode image : content.get("keyImages")) {
      validateImage(image, directory);
    }
  }

  private static Path validateImage(JsonNode entry, Path directory) throws IOException {
    String expectedDigest = entry.path("baseImageSha256").asText();
    if (!expectedDigest.matches("[a-f0-9]{64}")
        || !("images/" + expectedDigest + ".png").equals(entry.path("baseImagePath").asText())) {
      throw storageFailure("The saved key image path is invalid.");
    }
    Path imagePath = safeChild(directory, entry.get("baseImagePath").asText());
    if (!Files.isRegularFile(imagePath, LinkOption.NOFOLLOW_LINKS)
        || !expectedDigest.equals(digestFile(imagePath))) {
      throw storageFailure("A saved key image is missing or damaged.");
    }
    return imagePath;
  }

  private ObjectNode archiveSources(TrainingCaseSnapshot snapshot, Path directory)
      throws IOException {
    ObjectNode archive = MAPPER.createObjectNode();
    archive.put("scope", snapshot.sourceInventoryScope());
    archive.put("originalStudyCompleteness", "not_verified");
    archive.put("capturedAt", snapshot.capturedAt());
    ArrayNode entries = archive.putArray("instances");
    Map<String, ObjectNode> copied = new HashMap<>();
    List<StudySourceInventory.SourceImageFile> sources =
        new ArrayList<>(snapshot.sourceInventory());
    // A captured image remains represented even if its sequence is no longer loaded at export.
    for (KeyImageCapture image : snapshot.packet().keyImages()) {
      if (sources.stream()
          .noneMatch(source -> sameSourceFrame(source.reference(), image.reference()))) {
        sources.add(
            new StudySourceInventory.SourceImageFile(
                snapshot.packet().context().studyInstanceUid(), image.reference()));
      }
    }
    int archived = 0;
    for (StudySourceInventory.SourceImageFile source : sources) {
      ObjectNode entry = entries.addObject();
      entry.put("studyInstanceUid", source.studyInstanceUid());
      entry.set("reference", MAPPER.valueToTree(source.reference()));
      if (!snapshot.packet().context().studyInstanceUid().equals(source.studyInstanceUid())) {
        entry.put("status", "study_mismatch");
        continue;
      }
      String uri = source.reference().sourceUri();
      ObjectNode result = copied.get(uri);
      if (result == null) {
        result = archiveSource(uri, directory);
        copied.put(uri, result);
      }
      entry.setAll(result);
      if ("archived".equals(result.path("status").asText())) {
        archived++;
      }
    }
    archive.put("referenceCount", sources.size());
    archive.put("archivedReferenceCount", archived);
    archive.put("availableSourcesArchived", !sources.isEmpty() && archived == sources.size());
    archive.put(
        "status",
        sources.isEmpty()
            ? "no_sources"
            : archived == sources.size() ? "available_sources_archived" : "partial");
    return archive;
  }

  private static boolean sameSourceFrame(ImageReference first, ImageReference second) {
    return !first.sopInstanceUid().isBlank()
        && first.seriesInstanceUid().equals(second.seriesInstanceUid())
        && first.sopInstanceUid().equals(second.sopInstanceUid())
        && java.util.Objects.equals(first.sourceFrameIndex(), second.sourceFrameIndex());
  }

  private ObjectNode archiveSource(String sourceUri, Path directory) throws IOException {
    ObjectNode result = MAPPER.createObjectNode();
    if (sourceUri == null || sourceUri.isBlank()) {
      return result.put("status", "missing_source_reference");
    }
    Path source;
    try {
      URI uri = URI.create(sourceUri);
      if (!"file".equalsIgnoreCase(uri.getScheme())
          || uri.getRawAuthority() != null
          || uri.getFragment() != null
          || uri.getQuery() != null) {
        return result.put("status", "not_a_local_file");
      }
      source = Path.of(uri);
    } catch (IllegalArgumentException error) {
      return result.put("status", "invalid_source_reference");
    }
    if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
      return result.put("status", "source_unavailable");
    }
    Path dicomDirectory = safeChild(directory, "dicom");
    privateDirectory(dicomDirectory);
    Path temporary = privateTempFile(dicomDirectory);
    try {
      BasicFileAttributes before =
          Files.readAttributes(source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
      MessageDigest hash = sha256();
      long count = 0;
      try (InputStream input =
              Files.newInputStream(source, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
          FileChannel output = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
        byte[] buffer = new byte[128 * 1024];
        int length;
        while ((length = input.read(buffer)) >= 0) {
          hash.update(buffer, 0, length);
          ByteBuffer bytes = ByteBuffer.wrap(buffer, 0, length);
          while (bytes.hasRemaining()) {
            output.write(bytes);
          }
          count += length;
        }
        output.force(true);
      }
      BasicFileAttributes after =
          Files.readAttributes(source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
      if (count != before.size()
          || before.size() != after.size()
          || !before.lastModifiedTime().equals(after.lastModifiedTime())) {
        return result.put("status", "source_changed_during_copy");
      }
      String checksum = HexFormat.of().formatHex(hash.digest());
      Path destination = safeChild(dicomDirectory, checksum + ".dcm");
      if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
        if (!Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)
            || !checksum.equals(digestFile(destination))) {
          throw storageFailure("An archived source file is damaged.");
        }
      } else {
        Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
      }
      result.put("status", "archived");
      result.put("archivedPath", "dicom/" + checksum + ".dcm");
      result.put("sha256", checksum);
      result.put("bytes", count);
      return result;
    } catch (IOException error) {
      return result.put("status", "source_copy_failed");
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  Path studyDirectory(String studyKey) throws IOException {
    return safeChild(root, digest(studyKey.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  }

  private static Path safeChild(Path parent, String relative) throws IOException {
    Path child = parent.resolve(relative).normalize();
    if (!child.startsWith(parent) || child.equals(parent)) {
      throw storageFailure("A local record path is invalid.");
    }
    Path current = parent;
    if (Files.isSymbolicLink(current)) {
      throw storageFailure("A local record path cannot be a symbolic link.");
    }
    for (Path component : parent.relativize(child)) {
      current = current.resolve(component);
      if (Files.isSymbolicLink(current)) {
        throw storageFailure("A local record path cannot be a symbolic link.");
      }
    }
    return child;
  }

  private static void privateDirectory(Path directory) throws IOException {
    if (Files.isSymbolicLink(directory)) {
      throw storageFailure("A local record directory cannot be a symbolic link.");
    }
    Files.createDirectories(directory);
    try {
      Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
    } catch (UnsupportedOperationException ignored) {
      // Windows uses the account's inherited ACL instead of POSIX mode bits.
    }
  }

  private static Path privateTempFile(Path directory) throws IOException {
    try {
      return Files.createTempFile(
          directory,
          ".writing-",
          ".tmp",
          PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
    } catch (UnsupportedOperationException ignored) {
      return Files.createTempFile(directory, ".writing-", ".tmp");
    }
  }

  private static void atomicWrite(Path destination, byte[] bytes) throws IOException {
    Path temporary = privateTempFile(destination.getParent());
    try {
      try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) {
          channel.write(buffer);
        }
        channel.force(true);
      }
      Files.move(
          temporary,
          destination,
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private static void ensureAsset(Path destination, byte[] bytes, String checksum)
      throws IOException {
    if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
      if (!Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)
          || !checksum.equals(digestFile(destination))) {
        throw storageFailure("A saved image is damaged.");
      }
    } else {
      atomicWrite(destination, bytes);
    }
  }

  private static String digestFile(Path path) throws IOException {
    MessageDigest hash = sha256();
    try (InputStream stream =
        Files.newInputStream(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
      byte[] buffer = new byte[128 * 1024];
      int length;
      while ((length = stream.read(buffer)) >= 0) {
        hash.update(buffer, 0, length);
      }
    }
    return HexFormat.of().formatHex(hash.digest());
  }

  private static String digest(byte[] bytes) {
    return HexFormat.of().formatHex(sha256().digest(bytes));
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException("SHA-256 is unavailable.");
    }
  }

  private static IOException storageFailure(String message) {
    return new IOException(message);
  }

  private record EncodedImage(String checksum, byte[] pixels) {}
}
