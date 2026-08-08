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

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

final class ComposerText {
  private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\p{Cc}&&[^\\r\\n\\t]]");
  private static final Pattern FILE_UNSAFE = Pattern.compile("[^A-Z0-9._ -]");
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");
  private static final Pattern REPEATED_SEPARATOR = Pattern.compile("[ _-]{2,}");

  private ComposerText() {}

  static String clean(String value) {
    if (value == null) {
      return "";
    }
    return CONTROL_CHARACTERS.matcher(value).replaceAll("").strip();
  }

  static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  static String displayPersonName(String dicomName) {
    String value = clean(dicomName);
    if (!value.contains("^")) {
      return value;
    }
    String[] parts = value.split("\\^", -1);
    String family = parts.length > 0 ? clean(parts[0]) : "";
    String given = parts.length > 1 ? clean(parts[1]) : "";
    String middle = parts.length > 2 ? clean(parts[2]) : "";
    String givenNames = String.join(" ", given, middle).strip();
    if (!family.isBlank() && !givenNames.isBlank()) {
      return family + ", " + givenNames;
    }
    return family.isBlank() ? givenNames : family;
  }

  static String fileSafeUpper(String value, String fallback) {
    String normalized = Normalizer.normalize(clean(value), Normalizer.Form.NFKD);
    normalized = normalized.replace('^', '_').replace(',', '_');
    normalized = normalized.replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
    normalized = WHITESPACE.matcher(normalized).replaceAll(" ");
    normalized = FILE_UNSAFE.matcher(normalized).replaceAll("");
    normalized = REPEATED_SEPARATOR.matcher(normalized).replaceAll("_");
    normalized = normalized.replaceAll("^[._ -]+|[._ -]+$", "");
    return normalized.isBlank() ? fallback : normalized;
  }

  static String sentence(String value) {
    String text = clean(value);
    if (text.isBlank() || text.endsWith(".") || text.endsWith("!") || text.endsWith("?")) {
      return text;
    }
    return text + ".";
  }
}
