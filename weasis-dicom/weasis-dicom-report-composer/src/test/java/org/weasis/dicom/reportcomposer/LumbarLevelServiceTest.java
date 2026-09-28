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

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LumbarLevelServiceTest {
  @TempDir Path root;

  @Test
  void unresolvedNumberingCannotBecomeConfirmedAndCorrectionsKeepRawModelLabels() throws Exception {
    var proposal = LumbarLevelMapTest.unexpectedMap();
    try (var service = new LumbarLevelService(root)) {
      assertThrows(
          IllegalArgumentException.class,
          () -> service.save(proposal, proposal.points(), "accepted", "reporting_convention"));
      var uncertain =
          service
              .save(proposal, proposal.points(), "uncertain", "uncertain")
              .get(5, TimeUnit.SECONDS);
      assertFalse(uncertain.confirmed());
      var corrected = LumbarLevelReview.numberUpward(proposal.points(), "L5-S1");
      service
          .save(proposal, corrected, "corrected", "reporting_convention")
          .get(5, TimeUnit.SECONDS);
    }
    try (var service = new LumbarLevelService(root)) {
      var saved = service.readFeedback(proposal);
      assertTrue(saved.confirmed());
      assertEquals("L5-S1", saved.points().getLast().level());
      assertEquals("T12-T13", saved.proposal().modelPoints().getFirst().level());
      assertTrue(
          saved.proposal().points().stream()
              .allMatch(p -> p.level().equals(LumbarLevelMap.UNASSIGNED)));
    }
  }

  @Test
  void oldProposalFeedbackIsPreservedWithoutRestoringObsoleteNumbering() throws Exception {
    var map = LumbarLevelMapTest.map("case", "a".repeat(64));
    Path file =
        root.resolve("feedback").resolve(LumbarLevelService.hash("case")).resolve("latest.json");
    Files.createDirectories(file.getParent());
    var node = LumbarLevelService.JSON.createObjectNode();
    node.put("schemaVersion", 1);
    node.put("decision", "accepted");
    node.set("proposal", LumbarLevelService.JSON.valueToTree(map));
    node.put("points", "legacy record that must not be reinterpreted");
    Files.writeString(file, node.toString());
    var before = Files.readAllBytes(file);
    try (var service = new LumbarLevelService(root)) {
      assertNull(service.readFeedback(LumbarLevelMapTest.map("case", "b".repeat(64))));
    }
    assertArrayEquals(before, Files.readAllBytes(file));
  }

  @Test
  void installedRuntimeSmokeOnlyWhenExplicitlyRequested() throws Exception {
    String requestPath = System.getProperty("weasis.lumbar.ai.smoke.request");
    String runtimePath = System.getProperty("weasis.lumbar.ai.smoke.root");
    org.junit.jupiter.api.Assumptions.assumeTrue(requestPath != null && runtimePath != null);
    var request = LumbarLevelService.JSON.readTree(Path.of(requestPath).toFile());
    var images =
        LumbarLevelService.JSON
            .readerForListOf(StudySourceInventory.SourceImageFile.class)
            .<List<StudySourceInventory.SourceImageFile>>readValue(request.get("images"));
    try (var service = new LumbarLevelService(Path.of(runtimePath))) {
      var result =
          service
              .request(
                  request.path("studyKey").asText(),
                  request.path("studyInstanceUid").asText(),
                  images)
              .get(10, TimeUnit.MINUTES);
      assertNotNull(result);
      assertNotNull(result.preview());
      assertTrue(result.map().points().size() >= 4);
      assertTrue(result.map().studyInstanceUid().equals(request.path("studyInstanceUid").asText()));
    }
  }

  @Test
  void localWorkerProtocolLoadsPreviewAndRejectsAnotherStudysResponse() throws Exception {
    Path python = Path.of("/usr/bin/python3");
    org.junit.jupiter.api.Assumptions.assumeTrue(Files.isExecutable(python));
    Files.createDirectories(root.resolve("venv/bin"));
    Files.writeString(root.resolve("venv/bin/python"), "#!/bin/sh\nexec /usr/bin/python3 \"$@\"\n");
    assertTrue(root.resolve("venv/bin/python").toFile().setExecutable(true));
    Files.createDirectories(root.resolve("proposals"));
    Path preview = root.resolve("proposals/test.png");
    javax.imageio.ImageIO.write(
        new java.awt.image.BufferedImage(256, 256, java.awt.image.BufferedImage.TYPE_BYTE_GRAY),
        "png",
        preview.toFile());
    var node = LumbarLevelService.JSON.valueToTree(LumbarLevelMapTest.map("case", "a".repeat(64)));
    ((com.fasterxml.jackson.databind.node.ObjectNode) node).put("previewPath", preview.toString());
    LumbarLevelService.JSON.writeValue(root.resolve("response.json").toFile(), node);
    Files.writeString(
        root.resolve("worker.py"),
        """
        import sys
        from pathlib import Path
        response = Path(__file__).with_name('response.json').read_text()
        for line in sys.stdin:
            print(response, flush=True)
        """);
    try (var service = new LumbarLevelService(root)) {
      var result = service.request("case", "study", List.of()).get(10, TimeUnit.SECONDS);
      assertEquals(256, result.preview().getWidth());
      assertNull(result.feedback());
      assertThrows(
          java.util.concurrent.ExecutionException.class,
          () ->
              service
                  .request("different-case", "other-study", List.of())
                  .get(10, TimeUnit.SECONDS));
    }
  }

  @Test
  void feedbackSurvivesRestartAndRetainsProposalAndEveryCorrection() throws Exception {
    var proposal = LumbarLevelMapTest.map("case", "a".repeat(64));
    var corrected =
        List.of(
            new LumbarLevelMap.Landmark("a", "L2-L3", proposal.points().get(0).lps()),
            new LumbarLevelMap.Landmark("b", "L3-L4", proposal.points().get(1).lps()));
    try (var service = new LumbarLevelService(root)) {
      service
          .save(proposal, proposal.points(), "accepted", "reporting_convention")
          .get(5, TimeUnit.SECONDS);
      service.save(proposal, corrected, "corrected", "prior_correlation").get(5, TimeUnit.SECONDS);
    }
    try (var service = new LumbarLevelService(root)) {
      var saved = service.readFeedback(proposal);
      assertTrue(saved.confirmed());
      assertEquals(corrected, saved.points());
      assertEquals(proposal, saved.proposal());
      assertNull(service.readFeedback(LumbarLevelMapTest.map("different-study", "a".repeat(64))));
      assertNull(service.readFeedback(LumbarLevelMapTest.map("case", "b".repeat(64))));
      try (var events =
          Files.list(
              root.resolve("feedback")
                  .resolve(LumbarLevelService.hash("case"))
                  .resolve("events"))) {
        assertEquals(2, events.count());
      }
    }
  }

  @Test
  void reviewQueueOffersEachStudysLatestUndecidedMapWithUncertainNumberingFirst() throws Exception {
    Path preview = root.resolve("proposals/preview.png");
    Files.createDirectories(preview.getParent());
    javax.imageio.ImageIO.write(
        new java.awt.image.BufferedImage(256, 256, java.awt.image.BufferedImage.TYPE_BYTE_GRAY),
        "png",
        preview.toFile());
    writeProposal(
        LumbarLevelMapTest.map("confident", "a".repeat(64)), "2026-09-01T00:00:00Z", preview);
    writeProposal(
        LumbarLevelMapTest.map("confident", "b".repeat(64)), "2026-09-02T00:00:00Z", preview);
    writeProposal(uncertainMap("transitional"), "2026-09-01T00:00:00Z", preview);
    var reviewed = LumbarLevelMapTest.map("reviewed", "c".repeat(64));
    writeProposal(reviewed, "2026-09-01T00:00:00Z", preview);
    writeProposal(
        LumbarLevelMapTest.map("open-now", "d".repeat(64)), "2026-09-01T00:00:00Z", preview);
    try (var service = new LumbarLevelService(root)) {
      service
          .save(reviewed, reviewed.points(), "accepted", "reporting_convention")
          .get(5, TimeUnit.SECONDS);

      var queue = service.reviewQueue(20, "open-now", 1).get(5, TimeUnit.SECONDS);

      assertEquals(
          List.of("transitional", "confident"),
          queue.stream().map(item -> item.map().studyKey()).toList());
      assertEquals("b".repeat(64), queue.get(1).map().proposalId());
      assertEquals(
          List.of("T11-T12", "T12-L1", "L1-L2", "L2-L3", "L3-L4", "L4-L5", "L5-S1"),
          queue.get(0).initialPoints().stream().map(LumbarLevelMap.Landmark::level).toList());
      assertEquals(queue.get(1).map().points(), queue.get(1).initialPoints());
      assertEquals(1, service.reviewQueue(1, null, 1).get(5, TimeUnit.SECONDS).size());
    }
  }

  private static LumbarLevelMap uncertainMap(String key) {
    var geometry = LumbarLevelMapTest.geometry();
    var labels = List.of("T12-L1", "L1-L2", "L2-L3", "L3-L4", "L4-L5", "L5-L6", "L6-S1");
    var model = new java.util.ArrayList<LumbarLevelMap.Landmark>();
    for (int i = 0; i < labels.size(); i++) {
      model.add(
          new LumbarLevelMap.Landmark(
              "d" + i, labels.get(i), geometry.patientPosition(60, 20 + 12 * i)));
    }
    var base = LumbarLevelMapTest.map(key, "e".repeat(64));
    return new LumbarLevelMap(
        base.schemaVersion(),
        key,
        base.studyInstanceUid(),
        base.proposalId(),
        base.modelId(),
        base.frameOfReferenceUid(),
        base.reference(),
        model,
        base.reviewReasons(),
        base.previewPath(),
        base.status(),
        model,
        List.of(),
        true);
  }

  private void writeProposal(LumbarLevelMap map, String createdAt, Path preview) throws Exception {
    var node =
        (com.fasterxml.jackson.databind.node.ObjectNode) LumbarLevelService.JSON.valueToTree(map);
    node.put("previewPath", preview.toString());
    node.put("createdAt", createdAt);
    Path file =
        root.resolve("proposals")
            .resolve(LumbarLevelService.hash(map.studyKey()))
            .resolve(map.proposalId())
            .resolve("proposal.json");
    Files.createDirectories(file.getParent());
    LumbarLevelService.JSON.writeValue(file.toFile(), node);
  }

  @Test
  void failedWriteIsReportedAndNeverAppearsConfirmed() throws Exception {
    Files.writeString(root.resolve("feedback"), "blocked destination");
    try (var service = new LumbarLevelService(root)) {
      var map = LumbarLevelMapTest.map("case", "a".repeat(64));
      assertThrows(
          java.util.concurrent.ExecutionException.class,
          () ->
              service
                  .save(map, map.points(), "accepted", "reporting_convention")
                  .get(5, TimeUnit.SECONDS));
    }
  }

  @Test
  void editedPointsCannotBeSavedAsUnchangedAcceptance() {
    var map = LumbarLevelMapTest.map("case", "a".repeat(64));
    var changed =
        List.of(
            new LumbarLevelMap.Landmark(
                "a", "L1-L2", LumbarLevelMapTest.geometry().patientPosition(61, 20)),
            map.points().get(1));
    try (var service = new LumbarLevelService(root)) {
      assertThrows(
          IllegalArgumentException.class,
          () -> service.save(map, changed, "accepted", "reporting_convention"));
    }
  }
}
