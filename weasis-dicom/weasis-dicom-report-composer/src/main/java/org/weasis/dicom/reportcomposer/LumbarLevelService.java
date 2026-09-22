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
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;
import org.weasis.core.api.gui.util.AppProperties;

/** Isolated serial inference; no model code, pixel decoding, or disk writes on the Swing thread. */
final class LumbarLevelService implements AutoCloseable {
  static final ObjectMapper JSON = new ObjectMapper();
  private final Path root;
  private final ExecutorService inference = executor("lumbar-local-model");
  private final ExecutorService writer = executor("lumbar-feedback");
  private final ScheduledExecutorService watchdog =
      Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "lumbar-timeout"));
  private final AtomicLong generation = new AtomicLong();
  private volatile Process process;
  private volatile boolean closed;
  private BufferedReader input;
  private BufferedWriter output;
  private final Thread shutdownHook;

  record Feedback(
      int schemaVersion,
      String eventId,
      String recordedAt,
      String reader,
      String decision,
      String numberingBasis,
      LumbarLevelMap proposal,
      List<LumbarLevelMap.Landmark> points) {
    Feedback {
      if (schemaVersion != 1
          || !List.of("accepted", "corrected", "uncertain", "skipped").contains(decision)
          || !List.of("reporting_convention", "whole_spine_count", "prior_correlation", "uncertain")
              .contains(numberingBasis))
        throw new IllegalArgumentException("Invalid review decision");
      LumbarLevelMap.validateGeometry(points, proposal.reference().geometry());
      if (decision.equals("accepted") || decision.equals("corrected"))
        LumbarLevelMap.validatePoints(points, proposal.reference().geometry());
      points = List.copyOf(points);
      if ((decision.equals("accepted") || decision.equals("corrected"))
          && numberingBasis.equals("uncertain"))
        throw new IllegalArgumentException("Uncertain numbering cannot be a confirmed label");
      if (decision.equals("accepted") && !points.equals(proposal.points()))
        throw new IllegalArgumentException("Edited points must be recorded as corrected");
    }

    boolean confirmed() {
      return decision.equals("accepted") || decision.equals("corrected");
    }
  }

  record Result(LumbarLevelMap map, BufferedImage preview, Feedback feedback) {}

  LumbarLevelService() {
    this(
        Path.of(
            System.getProperty(
                "weasis.lumbar.ai.root",
                AppProperties.WEASIS_PATH.resolve("ai/lumbar").toString())));
  }

  LumbarLevelService(Path root) {
    this.root = root.toAbsolutePath().normalize();
    shutdownHook =
        new Thread(
            () -> {
              stopProcess();
              writer.shutdown();
              try {
                writer.awaitTermination(5, TimeUnit.SECONDS);
              } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
              }
            },
            "lumbar-shutdown");
    Runtime.getRuntime().addShutdownHook(shutdownHook);
  }

  boolean installed() {
    return Files.isExecutable(root.resolve("venv/bin/python"))
        && Files.isRegularFile(root.resolve("worker.py"));
  }

  void invalidate() {
    generation.incrementAndGet();
  }

  void pause() {
    invalidate();
    stopProcess();
  }

  CompletableFuture<Result> request(
      String key, String studyUid, List<StudySourceInventory.SourceImageFile> images) {
    long ticket = generation.incrementAndGet();
    return CompletableFuture.supplyAsync(
        () -> {
          if (closed || ticket != generation.get()) return null;
          try {
            start();
            Process current = process;
            var timeout = watchdog.schedule(current::destroyForcibly, 10, TimeUnit.MINUTES);
            String line;
            try {
              output.write(
                  JSON.writeValueAsString(
                      Map.of(
                          "schemaVersion",
                          1,
                          "studyKey",
                          key,
                          "studyInstanceUid",
                          studyUid,
                          "images",
                          images)));
              output.newLine();
              output.flush();
              line = input.readLine();
            } finally {
              timeout.cancel(false);
            }
            if (line == null) throw new IOException("Worker stopped");
            var node = JSON.readTree(line);
            if (!key.equals(node.path("studyKey").asText())
                || !studyUid.equals(node.path("studyInstanceUid").asText())
                || !"provisional".equals(node.path("status").asText()))
              throw new IOException("No usable map");
            LumbarLevelMap map = JSON.treeToValue(node, LumbarLevelMap.class);
            Path preview = Path.of(map.previewPath()).toAbsolutePath().normalize();
            if (!preview.startsWith(root.resolve("proposals")))
              throw new IOException("Invalid preview");
            BufferedImage image = ImageIO.read(preview.toFile());
            if (image == null
                || image.getWidth() != map.reference().geometry().columns()
                || image.getHeight() != map.reference().geometry().rows())
              throw new IOException("Invalid preview geometry");
            Feedback feedback = readFeedback(map);
            return new Result(map, image, feedback);
          } catch (Exception error) {
            stopProcess();
            throw new java.util.concurrent.CompletionException(
                new IOException("Local level map unavailable"));
          }
        },
        inference);
  }

  private synchronized void start() throws IOException {
    if (closed) throw new IOException("Service closed");
    if (process != null && process.isAlive()) return;
    if (!installed()) throw new IOException("Local runtime not installed");
    ProcessBuilder builder =
        new ProcessBuilder(
            root.resolve("venv/bin/python").toString(),
            "-u",
            root.resolve("worker.py").toString(),
            "--root",
            root.toString());
    builder.environment().put("OMP_NUM_THREADS", "2");
    builder.environment().put("OPENBLAS_NUM_THREADS", "2");
    builder.environment().put("ITK_GLOBAL_DEFAULT_NUMBER_OF_THREADS", "2");
    builder.environment().put("SPINEPS_NO_CITATION_REMINDER", "1");
    // Third-party libraries can log paths; do not forward them into general application logs.
    builder.redirectError(ProcessBuilder.Redirect.DISCARD);
    process = builder.start();
    input =
        new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
    output =
        new BufferedWriter(
            new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
  }

  Feedback readFeedback(LumbarLevelMap map) throws IOException {
    Path path = feedbackDirectory(map.studyKey()).resolve("latest.json");
    if (!Files.exists(path)) return null;
    var saved = JSON.readTree(path.toFile());
    var proposal = saved.path("proposal");
    // Preserve old decisions as audit history. Never relabel or restore them onto a new proposal.
    if (!map.studyKey().equals(proposal.path("studyKey").asText())
        || !map.studyInstanceUid().equals(proposal.path("studyInstanceUid").asText())
        || !map.proposalId().equals(proposal.path("proposalId").asText())) return null;
    return JSON.treeToValue(saved, Feedback.class);
  }

  CompletableFuture<Feedback> save(
      LumbarLevelMap map, List<LumbarLevelMap.Landmark> points, String decision, String basis) {
    Feedback feedback =
        new Feedback(
            1,
            UUID.randomUUID().toString(),
            Instant.now().toString(),
            System.getProperty("user.name", "local-reader"),
            decision,
            basis,
            map,
            points);
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            Path directory = feedbackDirectory(map.studyKey());
            atomicWrite(
                directory.resolve("events").resolve(feedback.eventId() + ".json"), feedback);
            atomicWrite(directory.resolve("latest.json"), feedback);
            return feedback;
          } catch (IOException error) {
            throw new java.util.concurrent.CompletionException(error);
          }
        },
        writer);
  }

  private Path feedbackDirectory(String key) {
    return root.resolve("feedback").resolve(hash(key));
  }

  static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException(error);
    }
  }

  static void atomicWrite(Path path, Object value) throws IOException {
    Files.createDirectories(path.getParent());
    Path temp = Files.createTempFile(path.getParent(), ".pending-", ".json");
    try {
      if (Files.getFileStore(temp).supportsFileAttributeView("posix")) {
        Files.setPosixFilePermissions(
            path.getParent(), PosixFilePermissions.fromString("rwx------"));
        Files.setPosixFilePermissions(temp, PosixFilePermissions.fromString("rw-------"));
      }
      byte[] data = JSON.writeValueAsBytes(value);
      try (var channel =
          java.nio.channels.FileChannel.open(temp, java.nio.file.StandardOpenOption.WRITE)) {
        var buffer = java.nio.ByteBuffer.wrap(data);
        while (buffer.hasRemaining()) channel.write(buffer);
        channel.force(true);
      }
      Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  private synchronized void stopProcess() {
    if (process != null) process.destroyForcibly();
    process = null;
  }

  @Override
  public void close() {
    if (closed) return;
    closed = true;
    invalidate();
    stopProcess();
    inference.shutdownNow();
    watchdog.shutdownNow();
    writer.execute(
        () -> {
          try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
          } catch (IllegalStateException ignored) {
            /* JVM shutdown is already flushing the writer. */
          }
        });
    writer.shutdown();
  }

  private static ExecutorService executor(String name) {
    return Executors.newSingleThreadExecutor(r -> daemon(r, name));
  }

  private static Thread daemon(Runnable task, String name) {
    Thread t = new Thread(task, name);
    t.setDaemon(true);
    return t;
  }
}
