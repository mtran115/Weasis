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

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Active reading time per study: it runs while there is input, pauses after {@link #IDLE} without
 * any, and stops for good when the study is exported.
 */
final class ReadClock {
  static final Duration IDLE = Duration.ofSeconds(10);
  static final Duration SESSION_BREAK = Duration.ofHours(1);

  enum State {
    NONE,
    RUNNING,
    PAUSED,
    EXPORTED
  }

  record Entry(Duration active, Instant exportedAt, Instant updatedAt) {
    boolean exported() {
      return exportedAt != null;
    }
  }

  /** The studies exported in the current session and their mean reading time. */
  record Session(int studies, Duration average) {}

  private final Map<String, Entry> entries;
  private String current;
  private Instant segmentStart;
  private Instant lastInput = Instant.MIN;

  ReadClock(Map<String, Entry> entries) {
    this.entries = new HashMap<>(entries);
  }

  void input(Instant now) {
    lastInput = now;
    if (segmentStart == null && current != null && !isExported(current)) segmentStart = now;
  }

  void tick(Instant now) {
    Instant idleFrom = lastInput.plus(IDLE);
    if (segmentStart != null && !now.isBefore(idleFrom)) closeSegment(idleFrom);
  }

  void select(String key, Instant now) {
    if (Objects.equals(key, current)) return;
    closeSegment(now);
    current = key;
    if (key != null && !isExported(key) && now.isBefore(lastInput.plus(IDLE))) segmentStart = now;
  }

  /** Stops the study's clock; a study exported again keeps its first time. */
  void export(String key, Instant now) {
    if (key.equals(current)) closeSegment(now);
    Entry entry = entries.getOrDefault(key, new Entry(Duration.ZERO, null, now));
    if (!entry.exported()) entries.put(key, new Entry(entry.active(), now, now));
  }

  State state() {
    if (current == null) return State.NONE;
    if (isExported(current)) return State.EXPORTED;
    return segmentStart == null ? State.PAUSED : State.RUNNING;
  }

  Duration elapsed(Instant now) {
    if (current == null) return Duration.ZERO;
    Duration active = entries.containsKey(current) ? entries.get(current).active() : Duration.ZERO;
    return segmentStart == null ? active : active.plus(nonNegative(segmentStart, now));
  }

  /**
   * The exported studies read without a break longer than {@link #SESSION_BREAK}. A break is the
   * time between two exports that was not spent reading the second study.
   */
  Optional<Session> session(Instant now) {
    List<Entry> exported =
        entries.values().stream()
            .filter(Entry::exported)
            .sorted(Comparator.comparing(Entry::exportedAt).reversed())
            .toList();
    if (exported.isEmpty()) return Optional.empty();
    Duration reading = state() == State.EXPORTED ? Duration.ZERO : elapsed(now);
    if (isBreak(exported.getFirst().exportedAt(), now, reading)) return Optional.empty();
    int count = 1;
    while (count < exported.size()) {
      Entry later = exported.get(count - 1);
      if (isBreak(exported.get(count).exportedAt(), later.exportedAt(), later.active())) break;
      count++;
    }
    Duration total =
        exported.subList(0, count).stream()
            .map(Entry::active)
            .reduce(Duration.ZERO, Duration::plus);
    return Optional.of(new Session(count, total.dividedBy(count)));
  }

  /** Every study's time, the running one included, for saving. */
  Map<String, Entry> snapshot(Instant now) {
    Map<String, Entry> copy = new HashMap<>(entries);
    if (segmentStart != null) copy.put(current, new Entry(elapsed(now), null, now));
    return copy;
  }

  private boolean isExported(String key) {
    Entry entry = entries.get(key);
    return entry != null && entry.exported();
  }

  private void closeSegment(Instant end) {
    if (segmentStart == null) return;
    Instant stop = end.isBefore(lastInput.plus(IDLE)) ? end : lastInput.plus(IDLE);
    Entry entry = entries.getOrDefault(current, new Entry(Duration.ZERO, null, stop));
    entries.put(
        current, new Entry(entry.active().plus(nonNegative(segmentStart, stop)), null, stop));
    segmentStart = null;
  }

  private static boolean isBreak(Instant earlier, Instant later, Duration reading) {
    return nonNegative(earlier, later).minus(reading).compareTo(SESSION_BREAK) > 0;
  }

  private static Duration nonNegative(Instant start, Instant end) {
    Duration duration = Duration.between(start, end);
    return duration.isNegative() ? Duration.ZERO : duration;
  }
}
