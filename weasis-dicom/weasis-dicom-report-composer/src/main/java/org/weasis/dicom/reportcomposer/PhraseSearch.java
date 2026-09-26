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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Fuzzy ranking for quick phrases. Every query word must appear in the label or text, either as a
 * substring or as letters in order ("ntfr" finds "neural foramina"). Label matches, word starts,
 * and contiguous matches rank higher; ties keep the caller's order, which is usage-ranked.
 */
final class PhraseSearch {
  private PhraseSearch() {}

  static <T> List<T> rank(
      String query,
      List<T> candidates,
      Function<T, String> label,
      Function<T, String> text,
      int limit) {
    List<String> words = words(query);
    if (words.isEmpty()) return List.of();
    record Scored<T>(T value, int score, int order) {}
    List<Scored<T>> matches = new ArrayList<>();
    for (int order = 0; order < candidates.size(); order++) {
      T candidate = candidates.get(order);
      String labelText = normalize(label.apply(candidate));
      String bodyText = normalize(text.apply(candidate));
      int total = 0;
      for (String word : words) {
        int best = Math.max(score(word, labelText) * 2, score(word, bodyText));
        if (best == 0) {
          total = -1;
          break;
        }
        total += best;
      }
      if (total > 0) matches.add(new Scored<>(candidate, total, order));
    }
    return matches.stream()
        .sorted(
            Comparator.comparingInt((Scored<T> match) -> match.score())
                .reversed()
                .thenComparingInt(Scored::order))
        .limit(limit)
        .map(Scored::value)
        .toList();
  }

  /** 0 when the word does not match; substring matches always outrank letters-in-order ones. */
  static int score(String word, String field) {
    int index = field.indexOf(word);
    if (index >= 0) {
      boolean wordStart = index == 0 || !Character.isLetterOrDigit(field.charAt(index - 1));
      return 100 + (wordStart ? 50 : 0) + (index == 0 ? 25 : 0) - Math.min(index, 20);
    }
    int position = -1;
    int first = -1;
    for (char letter : word.toCharArray()) {
      position = field.indexOf(letter, position + 1);
      if (position < 0) return 0;
      if (first < 0) first = position;
    }
    int span = position - first + 1;
    return 10 + Math.min(40, 40 * word.length() / span);
  }

  private static List<String> words(String query) {
    String normalized = normalize(query);
    return normalized.isEmpty() ? List.of() : List.of(normalized.split(" "));
  }

  private static String normalize(String value) {
    return value == null ? "" : value.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }
}
