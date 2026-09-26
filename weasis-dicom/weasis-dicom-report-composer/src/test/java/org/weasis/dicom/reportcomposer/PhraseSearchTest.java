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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PhraseSearchTest {
  private record Item(String label, String text) {}

  private static final List<Item> ITEMS =
      List.of(
          new Item("Motion limit", "Limited exam due to motion."),
          new Item("Foraminal stenosis", "Mild left neural foraminal stenosis at [level]."),
          new Item("Prepatellar edema", "Mild prepatellar soft tissue edema."),
          new Item("Canal stenosis", "Moderate spinal canal stenosis at [level]."));

  private static List<String> labels(String query) {
    return PhraseSearch.rank(query, ITEMS, Item::label, Item::text, 8).stream()
        .map(Item::label)
        .toList();
  }

  @Test
  void everyWordMustMatchAndLabelHitsOutrankTextHits() {
    assertEquals(
        java.util.Set.of("Foraminal stenosis", "Canal stenosis"),
        java.util.Set.copyOf(labels("stenosis")));
    assertEquals(List.of("Canal stenosis"), labels("canal sten"));
    assertEquals(List.of("Motion limit"), labels("MOTION"));
    assertTrue(labels("stenosis knee").isEmpty());
  }

  @Test
  void lettersInOrderFindAbbreviatedQueriesButRankBelowSubstrings() {
    assertEquals(List.of("Foraminal stenosis"), labels("frmnl"));
    assertEquals(List.of("Prepatellar edema"), labels("ppat"));
    assertTrue(
        PhraseSearch.score("edema", "soft tissue edema")
            > PhraseSearch.score("edma", "soft tissue edema"));
  }

  @Test
  void blankQueriesMatchNothingAndTiesKeepUsageOrder() {
    assertTrue(labels("   ").isEmpty());
    List<Item> equal = List.of(new Item("Edema knee", "a"), new Item("Edema hip", "b"));
    assertEquals(equal, PhraseSearch.rank("edema", equal, Item::label, Item::text, 8));
    assertEquals(
        equal.reversed(), PhraseSearch.rank("edema", equal.reversed(), Item::label, Item::text, 8));
    assertEquals(1, PhraseSearch.rank("edema", equal, Item::label, Item::text, 1).size());
  }
}
