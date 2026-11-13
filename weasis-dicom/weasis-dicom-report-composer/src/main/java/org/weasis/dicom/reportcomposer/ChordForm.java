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

import java.util.List;
import java.util.Map;
import javax.swing.JToggleButton;
import javax.swing.text.JTextComponent;

/** A structured form whose frequent findings take the chords of {@link FormChordEngine}. */
interface ChordForm {
  /**
   * A structure the chord keys address. {@code degrees} holds the buttons for d1 onwards, {@code
   * keys} the lettered buttons in form order, and {@code text} the box that x opens, if any. A
   * button under {@link FormChordEngine#WHOLE} is the whole finding, set by the digit itself.
   */
  record Target(
      String name,
      List<JToggleButton> degrees,
      Map<Character, JToggleButton> keys,
      JTextComponent text) {}

  /** The structures picked by one digit, 1 to 9 in order. */
  List<Target> chordTargets();

  /**
   * For a two-sided form, the same structures per side under the key that picks the side. The side
   * stays until the other key, and {@link #chordTargets()} is then unused.
   */
  default Map<Character, List<Target>> sidedTargets() {
    return Map.of();
  }

  /** Groups such as bones, picked by the group's letter and a digit, 1 to 9 and then 0. */
  default Map<Character, List<Target>> prefixedTargets() {
    return Map.of();
  }

  JTextComponent freeText();

  boolean isEnabled();
}
