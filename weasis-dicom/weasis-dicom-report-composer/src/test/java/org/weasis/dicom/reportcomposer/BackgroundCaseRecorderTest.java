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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BackgroundCaseRecorderTest {
  @TempDir Path temporary;

  @Test
  void coalescesPerStudyAndFlushesBeforeRestoringSwitchedStudy() throws Exception {
    TrainingCaseStore store = new TrainingCaseStore(temporary.resolve("store"));
    try (BackgroundCaseRecorder recorder = new BackgroundCaseRecorder(store, Duration.ofHours(1))) {
      ReportDraft first = TrainingCaseStoreTest.draft("first");
      ReportDraft second = TrainingCaseStoreTest.draft("second");
      first.setReportInstructions("initial");
      var superseded = recorder.requestSave(snapshot(first));
      second.setReportInstructions("second study");
      var secondSave = recorder.requestSave(snapshot(second));
      first.setReportInstructions("latest first study");
      var firstSave = recorder.requestSave(snapshot(first));

      assertTrue(superseded.isDone());
      assertFalse(secondSave.isDone());
      assertEquals(
          "latest first study",
          recorder
              .load("first")
              .get(5, TimeUnit.SECONDS)
              .orElseThrow()
              .draft()
              .reportInstructions());
      assertTrue(firstSave.isDone());
      recorder.flush().get(5, TimeUnit.SECONDS);
      assertEquals("second study", store.load("second").orElseThrow().draft().reportInstructions());
      assertTrue(secondSave.isDone());
    }
  }

  @Test
  void completionCancelsOlderPendingDraftButLaterEditIsSavedAsDraft() throws Exception {
    TrainingCaseStore store = new TrainingCaseStore(temporary.resolve("store"));
    try (BackgroundCaseRecorder recorder = new BackgroundCaseRecorder(store, Duration.ofHours(1))) {
      ReportDraft draft = TrainingCaseStoreTest.draft("study");
      draft.setReportInstructions("older queued draft");
      recorder.requestSave(snapshot(draft));
      draft.setReportInstructions("exported findings");
      recorder.complete(snapshot(draft)).get(5, TimeUnit.SECONDS);
      recorder.flush().get(5, TimeUnit.SECONDS);
      assertTrue(store.load("study").orElseThrow().annotationComplete());
      assertEquals(
          "exported findings", store.load("study").orElseThrow().draft().reportInstructions());

      draft.setReportInstructions("later edit");
      recorder.requestSave(snapshot(draft));
      recorder.flush().get(5, TimeUnit.SECONDS);
      assertFalse(store.load("study").orElseThrow().annotationComplete());
      assertEquals("later edit", store.load("study").orElseThrow().draft().reportInstructions());
    }
  }

  @Test
  void lateCompletionDoesNotCancelNewerPendingEdits() throws Exception {
    TrainingCaseStore store = new TrainingCaseStore(temporary.resolve("store"));
    try (BackgroundCaseRecorder recorder = new BackgroundCaseRecorder(store, Duration.ofHours(1))) {
      ReportDraft draft = TrainingCaseStoreTest.draft("study");
      draft.setReportInstructions("exported content");
      TrainingCaseSnapshot exported = snapshot(draft);
      draft.setReportInstructions("newer edit");
      var pendingEdit = recorder.requestSave(snapshot(draft));

      recorder.complete(exported).get(5, TimeUnit.SECONDS);
      recorder.flush().get(5, TimeUnit.SECONDS);

      assertTrue(pendingEdit.isDone());
      assertEquals("newer edit", store.load("study").orElseThrow().draft().reportInstructions());
      assertFalse(store.load("study").orElseThrow().annotationComplete());
    }
  }

  @Test
  void studyRestoreRemainsAvailableWhileSourceArchiveIsBusy() throws Exception {
    TrainingCaseStore store = mock(TrainingCaseStore.class);
    CountDownLatch archiveStarted = new CountDownLatch(1);
    CountDownLatch releaseArchive = new CountDownLatch(1);
    when(store.complete(any()))
        .thenAnswer(
            invocation -> {
              archiveStarted.countDown();
              assertTrue(releaseArchive.await(5, TimeUnit.SECONDS));
              return temporary.resolve("revision.json");
            });
    when(store.load("next-study")).thenReturn(Optional.empty());
    try (BackgroundCaseRecorder recorder = new BackgroundCaseRecorder(store, Duration.ZERO)) {
      var completion = recorder.complete(snapshot(TrainingCaseStoreTest.draft("first-study")));
      try {
        assertTrue(archiveStarted.await(5, TimeUnit.SECONDS));
        assertTrue(recorder.load("next-study").get(5, TimeUnit.SECONDS).isEmpty());
        assertFalse(completion.isDone());
      } finally {
        releaseArchive.countDown();
      }
      completion.get(5, TimeUnit.SECONDS);
    }
  }

  @Test
  void reportsFailedStorageWithoutStoppingOtherStudies() throws Exception {
    TrainingCaseStore store = new TrainingCaseStore(temporary.resolve("store"));
    ReportDraft broken = TrainingCaseStoreTest.draft("broken");
    Path latest = store.save(snapshot(broken));
    Files.writeString(latest, "broken manifest");
    try (BackgroundCaseRecorder recorder = new BackgroundCaseRecorder(store, Duration.ZERO)) {
      var failure = recorder.requestSave(snapshot(broken));
      assertThrows(ExecutionException.class, () -> failure.get(5, TimeUnit.SECONDS));
      ReportDraft intact = TrainingCaseStoreTest.draft("intact");
      intact.setReportInstructions("still saved");
      recorder.requestSave(snapshot(intact)).get(5, TimeUnit.SECONDS);
      assertEquals(
          "still saved",
          recorder
              .load("intact")
              .get(5, TimeUnit.SECONDS)
              .orElseThrow()
              .draft()
              .reportInstructions());
    }
  }

  private static TrainingCaseSnapshot snapshot(ReportDraft draft) {
    return TrainingCaseSnapshot.capture(draft, "LUMBAR_SPINE", null);
  }
}
