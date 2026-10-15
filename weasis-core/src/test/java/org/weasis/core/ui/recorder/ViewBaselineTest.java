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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ViewBaselineTest {
  private static final Instant START = Instant.parse("2026-10-13T20:00:00Z");

  private final ViewBaseline baseline = new ViewBaseline(Duration.ofSeconds(1));

  private Optional<Map<String, Object>> at(long millis, Map<String, Object> values) {
    return baseline.changes("1", values, START.plusMillis(millis));
  }

  @Test
  void valuesSetWhileTheSeriesOpensBecomeTheBaseline() {
    baseline.reset("1", START);

    assertTrue(at(100, Map.of("window", 1416, "level", 708)).isEmpty());
    assertTrue(at(200, Map.of("window", 1300)).isEmpty());
    assertTrue(at(1500, Map.of("window", 1300)).isEmpty());
    assertEquals(
        Optional.of(Map.of("window", 1300)), at(1600, Map.of("window", 900, "level", 708)));
  }

  @Test
  void acceptedValuesAreWhereTheNextChangeStarts() {
    baseline.reset("1", START);
    assertTrue(at(100, Map.of("zoom", 2.367)).isEmpty());

    baseline.accept("1", Map.of("zoom", 2.959));

    assertTrue(at(1500, Map.of("zoom", 2.959)).isEmpty());
    assertEquals(Optional.of(Map.of("zoom", 2.959)), at(1600, Map.of("zoom", 3.699)));
  }

  @Test
  void aValueSeenFirstAfterSettlingIsAChangeFromNull() {
    Map<String, Object> before = new HashMap<>();
    before.put("zoom", null);

    assertEquals(Optional.of(before), at(0, Map.of("zoom", 1.5)));
    assertTrue(at(10, Map.of("zoom", 1.5)).isEmpty());
  }
}
