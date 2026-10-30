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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.reportcomposer.ReadClock.Entry;
import org.weasis.dicom.reportcomposer.ReadClock.Session;
import org.weasis.dicom.reportcomposer.ReadClock.State;

class ReadClockTest {
  private static final Instant START = Instant.parse("2026-10-29T20:00:00Z");

  private static Instant at(long seconds) {
    return START.plusSeconds(seconds);
  }

  @Test
  void countsWhileThereIsInputAndPausesAfterTenIdleSeconds() {
    ReadClock clock = new ReadClock(Map.of());
    clock.input(at(0));
    clock.select("a", at(0));
    clock.tick(at(5));
    assertEquals(State.RUNNING, clock.state());
    assertEquals(Duration.ofSeconds(5), clock.elapsed(at(5)));

    clock.tick(at(25));
    assertEquals(State.PAUSED, clock.state());
    assertEquals(Duration.ofSeconds(10), clock.elapsed(at(25)));

    clock.input(at(40));
    clock.input(at(41));
    clock.tick(at(43));
    assertEquals(State.RUNNING, clock.state());
    assertEquals(Duration.ofSeconds(13), clock.elapsed(at(43)));
  }

  @Test
  void aStudyOpenedWithoutRecentInputWaitsForInput() {
    ReadClock clock = new ReadClock(Map.of());
    clock.select("a", at(0));
    assertEquals(State.PAUSED, clock.state());

    clock.input(at(3));
    clock.tick(at(6));
    assertEquals(Duration.ofSeconds(3), clock.elapsed(at(6)));
  }

  @Test
  void eachStudyKeepsItsOwnTimeAcrossSwitches() {
    ReadClock clock = new ReadClock(Map.of());
    clock.input(at(0));
    clock.select("a", at(0));
    clock.input(at(4));
    clock.select("b", at(4));
    clock.input(at(10));
    clock.select("a", at(10));

    assertEquals(Duration.ofSeconds(4), clock.elapsed(at(10)));
    clock.select("b", at(12));
    assertEquals(Duration.ofSeconds(6), clock.elapsed(at(12)));
    clock.select(null, at(12));
    assertEquals(State.NONE, clock.state());
  }

  @Test
  void exportStopsTheClockForGood() {
    ReadClock clock = new ReadClock(Map.of());
    clock.input(at(0));
    clock.select("a", at(0));
    clock.input(at(90));
    clock.export("a", at(90));
    assertEquals(State.EXPORTED, clock.state());

    clock.input(at(95));
    clock.select(null, at(96));
    clock.input(at(100));
    clock.select("a", at(100));
    clock.input(at(130));
    clock.export("a", at(130));

    assertEquals(State.EXPORTED, clock.state());
    assertEquals(Duration.ofSeconds(90), clock.elapsed(at(130)));
    assertEquals(at(90), clock.snapshot(at(130)).get("a").exportedAt());
  }

  @Test
  void sessionAveragesExportsWithoutABreakOverAnHour() {
    Map<String, Entry> history =
        Map.of(
            "old", exported(600, START.minus(Duration.ofHours(5))),
            "first", exported(300, START.minus(Duration.ofMinutes(90))),
            // Exported 80 minutes after "first", 30 of them spent reading it: a 50-minute break.
            "long", exported(1800, START.minus(Duration.ofMinutes(10))),
            "last", exported(240, START));
    ReadClock clock = new ReadClock(history);

    assertEquals(
        Optional.of(new Session(3, Duration.ofSeconds((300 + 1800 + 240) / 3))),
        clock.session(START.plus(Duration.ofMinutes(30))));
  }

  @Test
  void aBreakOverAnHourEndsTheSessionUnlessTheTimeWasSpentReading() {
    ReadClock clock = new ReadClock(Map.of("a", exported(300, START)));
    Instant later = START.plus(Duration.ofMinutes(70));
    assertTrue(clock.session(later).isEmpty());

    clock.input(START.plus(Duration.ofMinutes(50)));
    clock.select("b", START.plus(Duration.ofMinutes(50)));
    for (long minute = 50; minute <= 70; minute++) {
      clock.input(START.plus(Duration.ofMinutes(minute)));
    }
    assertEquals(Optional.of(new Session(1, Duration.ofSeconds(300))), clock.session(later));
  }

  @Test
  void snapshotIncludesTheRunningStudy() {
    ReadClock clock = new ReadClock(Map.of());
    clock.input(at(0));
    clock.select("a", at(0));
    clock.input(at(7));

    Entry entry = clock.snapshot(at(8)).get("a");
    assertEquals(Duration.ofSeconds(8), entry.active());
    assertNull(entry.exportedAt());
  }

  private static Entry exported(long seconds, Instant at) {
    return new Entry(Duration.ofSeconds(seconds), at, at);
  }
}
