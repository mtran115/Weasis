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

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Coalesces typing without delaying study changes, and serializes all local disk operations. */
public final class BackgroundCaseRecorder implements AutoCloseable {
  private final TrainingCaseStore store;
  private final long debounceMillis;
  private final ScheduledThreadPoolExecutor executor;
  private final ExecutorService archiveExecutor;
  private final Thread shutdownHook;
  private final Map<String, PendingSave> pending = new HashMap<>();
  private boolean closed;

  public BackgroundCaseRecorder() {
    this(new TrainingCaseStore(), Duration.ofMillis(600));
  }

  BackgroundCaseRecorder(TrainingCaseStore store, Duration debounce) {
    this.store = store;
    debounceMillis = Math.max(0, debounce.toMillis());
    executor =
        new ScheduledThreadPoolExecutor(
            1,
            runnable -> {
              Thread thread = new Thread(runnable, "report-composer-local-recorder");
              thread.setDaemon(true);
              return thread;
            });
    executor.setRemoveOnCancelPolicy(true);
    archiveExecutor =
        Executors.newSingleThreadExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "report-composer-local-source-archive");
              thread.setDaemon(true);
              return thread;
            });
    shutdownHook = new Thread(this::flushAtShutdown, "report-composer-local-shutdown");
    Runtime.getRuntime().addShutdownHook(shutdownHook);
  }

  public synchronized CompletableFuture<Void> requestSave(TrainingCaseSnapshot snapshot) {
    if (closed) {
      return CompletableFuture.failedFuture(new IllegalStateException("Local recorder is closed."));
    }
    PendingSave previous = pending.get(snapshot.studyKey());
    if (previous != null && isAfter(previous.snapshot, snapshot)) {
      return CompletableFuture.completedFuture(null);
    }
    discardPending(snapshot.studyKey());
    PendingSave save = new PendingSave(snapshot);
    pending.put(snapshot.studyKey(), save);
    save.scheduled =
        executor.schedule(() -> writePending(save), debounceMillis, TimeUnit.MILLISECONDS);
    return save.result;
  }

  public synchronized CompletableFuture<Path> complete(TrainingCaseSnapshot snapshot) {
    if (closed) {
      return CompletableFuture.failedFuture(new IllegalStateException("Local recorder is closed."));
    }
    PendingSave previous = pending.get(snapshot.studyKey());
    if (previous != null && !isAfter(previous.snapshot, snapshot)) {
      discardPending(snapshot.studyKey());
    }
    return submit(() -> store.save(snapshot))
        .thenCompose(
            ignored -> {
              CompletableFuture<Path> result = new CompletableFuture<>();
              archiveExecutor.execute(
                  () -> {
                    try {
                      result.complete(store.complete(snapshot));
                    } catch (Exception error) {
                      result.completeExceptionally(error);
                    }
                  });
              return result;
            });
  }

  public synchronized CompletableFuture<Optional<TrainingCaseStore.RestoredCase>> load(
      String studyKey) {
    if (closed) {
      return CompletableFuture.failedFuture(new IllegalStateException("Local recorder is closed."));
    }
    enqueuePending(studyKey);
    return submit(() -> store.load(studyKey));
  }

  public synchronized CompletableFuture<Void> flush() {
    if (closed) {
      return CompletableFuture.failedFuture(new IllegalStateException("Local recorder is closed."));
    }
    CompletableFuture<?>[] results =
        pending.values().stream().map(save -> save.result).toArray(CompletableFuture[]::new);
    for (String key : pending.keySet().toArray(String[]::new)) {
      enqueuePending(key);
    }
    // This barrier also waits for a save which already began before flush was requested.
    CompletableFuture<Void> barrier = submit(() -> null);
    return CompletableFuture.allOf(CompletableFuture.allOf(results), barrier);
  }

  @Override
  public synchronized void close() {
    if (!closed) {
      flush();
      closed = true;
      executor.execute(
          () -> {
            archiveExecutor.execute(
                () -> {
                  try {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);
                  } catch (IllegalStateException ignored) {
                    // The running shutdown hook will wait for both executors to finish.
                  }
                });
            archiveExecutor.shutdown();
          });
      executor.shutdown();
    }
  }

  private void discardPending(String key) {
    PendingSave previous = pending.remove(key);
    if (previous != null) {
      previous.scheduled.cancel(false);
      previous.result.complete(null);
    }
  }

  private void flushAtShutdown() {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    close();
    try {
      executor.awaitTermination(10, TimeUnit.SECONDS);
      archiveExecutor.awaitTermination(
          Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
    }
  }

  private static boolean isAfter(TrainingCaseSnapshot first, TrainingCaseSnapshot second) {
    return Instant.parse(first.capturedAt()).isAfter(Instant.parse(second.capturedAt()));
  }

  private void enqueuePending(String key) {
    PendingSave save = pending.get(key);
    if (save != null) {
      save.scheduled.cancel(false);
      save.scheduled = executor.schedule(() -> writePending(save), 0, TimeUnit.MILLISECONDS);
    }
  }

  private void writePending(PendingSave save) {
    synchronized (this) {
      if (!pending.remove(save.snapshot.studyKey(), save)) {
        return;
      }
    }
    try {
      store.save(save.snapshot);
      save.result.complete(null);
    } catch (Exception error) {
      save.result.completeExceptionally(error);
    }
  }

  private <T> CompletableFuture<T> submit(StorageAction<T> action) {
    CompletableFuture<T> result = new CompletableFuture<>();
    executor.execute(
        () -> {
          try {
            result.complete(action.run());
          } catch (Exception error) {
            result.completeExceptionally(error);
          }
        });
    return result;
  }

  @FunctionalInterface
  private interface StorageAction<T> {
    T run() throws Exception;
  }

  private static final class PendingSave {
    private final TrainingCaseSnapshot snapshot;
    private final CompletableFuture<Void> result = new CompletableFuture<>();
    private ScheduledFuture<?> scheduled;

    private PendingSave(TrainingCaseSnapshot snapshot) {
      this.snapshot = snapshot;
    }
  }
}
