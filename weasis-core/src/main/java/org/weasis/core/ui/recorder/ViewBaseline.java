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

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Remembers each viewport's last values (window, zoom, slice...) so only real changes are recorded.
 * Right after a series opens, the viewer sets these values itself; they become the baseline without
 * being recorded.
 */
final class ViewBaseline {
  private final Duration settle;
  private final Map<String, Map<String, Object>> values = new HashMap<>();
  private final Map<String, Instant> settlingUntil = new HashMap<>();

  ViewBaseline(Duration settle) {
    this.settle = settle;
  }

  /** A new series in {@code view}: forget its values and let the viewer settle. */
  void reset(String view, Instant now) {
    values.remove(view);
    settlingUntil.put(view, now.plus(settle));
  }

  /** Takes {@code next} as the view's current values without counting them as changes. */
  void accept(String view, Map<String, Object> next) {
    values.computeIfAbsent(view, key -> new HashMap<>()).putAll(next);
  }

  /**
   * The values the changed entries had before, or empty when nothing changed or the view is still
   * settling. A value seen for the first time outside the settling time counts as a change from
   * null.
   */
  Optional<Map<String, Object>> changes(String view, Map<String, Object> next, Instant now) {
    Map<String, Object> known = values.computeIfAbsent(view, key -> new HashMap<>());
    boolean settling = now.isBefore(settlingUntil.getOrDefault(view, Instant.MIN));
    Map<String, Object> before = new LinkedHashMap<>();
    next.forEach(
        (name, value) -> {
          boolean seen = known.containsKey(name);
          Object previous = known.put(name, value);
          if (seen ? !Objects.equals(previous, value) : !settling) before.put(name, previous);
        });
    return before.isEmpty() || settling ? Optional.empty() : Optional.of(before);
  }
}
