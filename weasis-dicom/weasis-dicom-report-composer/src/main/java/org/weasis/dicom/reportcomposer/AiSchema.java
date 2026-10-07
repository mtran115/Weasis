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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builders for strict JSON schemas: every object is closed and requires all its properties. */
final class AiSchema {
  private AiSchema() {}

  static Map.Entry<String, Object> entry(String name, Object schema) {
    return Map.entry(name, schema);
  }

  @SafeVarargs
  static Map<String, Object> object(Map.Entry<String, Object>... properties) {
    return object(List.of(properties));
  }

  static Map<String, Object> object(List<Map.Entry<String, Object>> properties) {
    Map<String, Object> props = new LinkedHashMap<>();
    for (Map.Entry<String, Object> property : properties) {
      props.put(property.getKey(), property.getValue());
    }
    Map<String, Object> object = new LinkedHashMap<>();
    object.put("type", "object");
    object.put("additionalProperties", false);
    object.put("required", List.copyOf(props.keySet()));
    object.put("properties", props);
    return object;
  }

  static Map<String, Object> type(String type) {
    return Map.of("type", type);
  }

  static Map<String, Object> nullable(String type) {
    return Map.of("type", List.of(type, "null"));
  }

  static Map<String, Object> enumOf(List<String> values) {
    return Map.of("type", "string", "enum", values);
  }

  static Map<String, Object> arrayOf(Map<String, Object> items) {
    return Map.of("type", "array", "items", items);
  }

  /** One answer per numbered ring the radiologist drew: where it is and what it shows. */
  static Map.Entry<String, Object> marks(Map<String, Object> location) {
    return entry(
        "marks",
        arrayOf(
            object(
                entry("number", type("integer")),
                entry("location", location),
                entry("finding", type("string")),
                entry("confidence", enumOf(List.of("low", "medium", "high"))))));
  }
}
