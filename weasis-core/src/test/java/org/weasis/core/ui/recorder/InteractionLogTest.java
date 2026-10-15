/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.recorder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InteractionLogTest {
  private static final Instant START = Instant.parse("2026-10-13T20:00:00Z");
  private static final ZoneId ZONE = ZoneId.of("America/Los_Angeles");

  private final InteractionLog log = new InteractionLog(Duration.ofMillis(800), ZONE);

  private static InteractionLog.Action action(
      long millis, String type, String key, Map<String, Object> value) {
    return new InteractionLog.Action(
        START.plusMillis(millis), type, key, new LinkedHashMap<>(Map.of("view", 1)), value);
  }

  @Test
  void oneOffActionIsWrittenAtOnce() {
    List<String> lines = log.add(action(0, "layout", null, Map.of()));

    assertEquals(
        List.of("{\"t\":\"2026-10-13T13:00:00-07:00\",\"type\":\"layout\",\"view\":1}"), lines);
  }

  @Test
  void runOfTheSameContinuousActionBecomesOneLine() {
    assertEquals(List.of(), log.add(action(0, "scroll", "scroll:1", Map.of("slice", 3))));
    assertEquals(List.of(), log.add(action(300, "scroll", "scroll:1", Map.of("slice", 4))));
    assertEquals(List.of(), log.add(action(600, "scroll", "scroll:1", Map.of("slice", 9))));

    List<String> lines = log.add(action(700, "key", null, Map.of()));

    assertEquals(
        List.of(
            "{\"t\":\"2026-10-13T13:00:00-07:00\",\"type\":\"scroll\",\"view\":1,\"from\":{\"slice\":3},"
                + "\"to\":{\"slice\":9},\"count\":3,\"ms\":600}",
            "{\"t\":\"2026-10-13T13:00:00.7-07:00\",\"type\":\"key\",\"view\":1}"),
        lines);
  }

  @Test
  void anotherContinuousActionOrAPauseEndsTheRun() {
    log.add(action(0, "scroll", "scroll:1", Map.of("slice", 3)));
    List<String> switched = log.add(action(100, "display", "display:1", Map.of("window", 400)));
    assertEquals(1, switched.size());
    assertTrue(switched.getFirst().contains("\"type\":\"scroll\""));

    assertEquals(List.of(), log.flushIdle(START.plusMillis(899)));
    List<String> idle = log.flushIdle(START.plusMillis(900));
    assertEquals(1, idle.size());
    assertTrue(
        idle.getFirst().contains("\"from\":{\"window\":400},\"to\":{\"window\":400},\"count\":1"));
    assertEquals(List.of(), log.flush());
  }

  @Test
  void alternatingValuesKeepWhatTheyWereBeforeTheRun() {
    for (Object[] step :
        new Object[][] {
          {0, "window", 1416, 1200}, {10, "level", 708, 600}, {20, "window", 1200, 1000}
        }) {
      log.add(
          new InteractionLog.Action(
              START.plusMillis((Integer) step[0]),
              "display",
              "display:1:wl",
              new LinkedHashMap<>(),
              Map.of((String) step[1], step[2]),
              Map.of((String) step[1], step[3])));
    }

    assertEquals(
        List.of(
            "{\"t\":\"2026-10-13T13:00:00-07:00\",\"type\":\"display\",\"from\":{\"window\":1416,"
                + "\"level\":708},\"to\":{\"window\":1000,\"level\":600},\"count\":3,\"ms\":20}"),
        log.flush());
  }

  @Test
  void runWithoutValuesOnlyCounts() {
    log.add(action(0, "typing", "typing:Impression", Map.of()));
    log.add(action(150, "typing", "typing:Impression", Map.of()));

    assertEquals(
        List.of(
            "{\"t\":\"2026-10-13T13:00:00-07:00\",\"type\":\"typing\",\"view\":1,\"count\":2,\"ms\":150}"),
        log.flush());
  }

  @Test
  void jsonEscapesTextAndKeepsNumbersAndLists() {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("text", "say \"hi\"\n\\ok\u0001");
    value.put("zoom", 1.25);
    value.put("missing", null);
    value.put("nan", Double.NaN);
    value.put("commands", List.of("a", "b"));

    assertEquals(
        "{\"text\":\"say \\\"hi\\\"\\n\\\\ok\\u0001\",\"zoom\":1.25,\"missing\":null,\"nan\":null,"
            + "\"commands\":[\"a\",\"b\"]}",
        InteractionLog.json(value));
  }
}
