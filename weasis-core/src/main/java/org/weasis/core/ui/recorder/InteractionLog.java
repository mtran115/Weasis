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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Turns recorded actions into JSON lines, merging a run of the same continuous action into one. */
final class InteractionLog {

  /**
   * One recorded action. A continuous action (scrolling, dragging the window, typing) has a {@code
   * key}; consecutive actions with the same key become one line with the values before the run and
   * after it, a count, and the duration. {@code before} holds the values replaced by this action.
   */
  record Action(
      Instant at,
      String type,
      String key,
      Map<String, Object> fields,
      Map<String, Object> before,
      Map<String, Object> value) {
    Action(
        Instant at,
        String type,
        String key,
        Map<String, Object> fields,
        Map<String, Object> value) {
      this(at, type, key, fields, Map.of(), value);
    }
  }

  private final Duration idle;
  private final ZoneId zone;
  private Run run;

  InteractionLog(Duration idle, ZoneId zone) {
    this.idle = idle;
    this.zone = zone;
  }

  /**
   * The lines to write for this action: a finished run first, then the action unless it continues a
   * run.
   */
  List<String> add(Action action) {
    List<String> lines = new ArrayList<>();
    if (run != null
        && (action.key() == null || !action.key().equals(run.key) || isIdle(action.at()))) {
      lines.add(run.line());
      run = null;
    }
    if (action.key() == null) {
      lines.add(line(action.at(), action.type(), action.fields()));
    } else if (run == null) {
      run = new Run(action);
    } else {
      run.extend(action);
    }
    return lines;
  }

  /** The current run, once nothing has extended it for the idle time. */
  List<String> flushIdle(Instant now) {
    return run != null && isIdle(now) ? flush() : List.of();
  }

  List<String> flush() {
    if (run == null) return List.of();
    String line = run.line();
    run = null;
    return List.of(line);
  }

  private boolean isIdle(Instant now) {
    return !run.last.plus(idle).isAfter(now);
  }

  private String line(Instant at, String type, Map<String, Object> fields) {
    Map<String, Object> object = new LinkedHashMap<>();
    object.put(
        "t",
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
            at.truncatedTo(ChronoUnit.MILLIS).atZone(zone)));
    object.put("type", type);
    object.putAll(fields);
    return json(object);
  }

  private final class Run {
    private final String key;
    private final String type;
    private final Map<String, Object> fields;
    private final Instant first;
    private final Map<String, Object> from;
    private final Map<String, Object> to;
    private Instant last;
    private int count = 1;

    Run(Action action) {
      key = action.key();
      type = action.type();
      fields = action.fields();
      first = action.at();
      last = action.at();
      from = new LinkedHashMap<>();
      to = new LinkedHashMap<>();
      merge(action);
    }

    void extend(Action action) {
      last = action.at();
      count++;
      merge(action);
    }

    // A value keeps what it was before the run started, even when window and level alternate.
    private void merge(Action action) {
      action
          .value()
          .forEach(
              (name, value) -> {
                if (!from.containsKey(name)) {
                  from.put(
                      name, action.before().containsKey(name) ? action.before().get(name) : value);
                }
              });
      to.putAll(action.value());
    }

    String line() {
      Map<String, Object> merged = new LinkedHashMap<>(fields);
      if (!from.isEmpty()) {
        merged.put("from", from);
        merged.put("to", to);
      }
      merged.put("count", count);
      merged.put("ms", Duration.between(first, last).toMillis());
      return InteractionLog.this.line(first, type, merged);
    }
  }

  static String json(Object value) {
    StringBuilder out = new StringBuilder();
    appendJson(out, value);
    return out.toString();
  }

  private static void appendJson(StringBuilder out, Object value) {
    switch (value) {
      case null -> out.append("null");
      case Boolean bool -> out.append(bool);
      case Double number when !Double.isFinite(number) -> out.append("null");
      case Float number when !Float.isFinite(number) -> out.append("null");
      case Number number -> out.append(number);
      case Map<?, ?> map -> {
        out.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
          if (!first) out.append(',');
          first = false;
          appendString(out, String.valueOf(entry.getKey()));
          out.append(':');
          appendJson(out, entry.getValue());
        }
        out.append('}');
      }
      case Collection<?> items -> {
        out.append('[');
        boolean first = true;
        for (Object item : items) {
          if (!first) out.append(',');
          first = false;
          appendJson(out, item);
        }
        out.append(']');
      }
      default -> appendString(out, value.toString());
    }
  }

  private static void appendString(StringBuilder out, String text) {
    out.append('"');
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
          else out.append(c);
        }
      }
    }
    out.append('"');
  }
}
