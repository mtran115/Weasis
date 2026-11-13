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

/** A form's typed chords: fixed-length tokens that apply as soon as they are complete. */
interface ChordGrammar {
  enum Outcome {
    APPLIED,
    PENDING,
    REJECTED
  }

  /** Takes one digit or lower-case letter. A rejected key changes nothing. */
  Outcome type(char key);

  /** Removes the last key of an unfinished token, or else the line's last token. */
  boolean backspace();

  /** Abandons an unfinished token. */
  boolean escape();

  /** Ends a line that Enter closes; false when there is none open. */
  default boolean enter() {
    return false;
  }

  default boolean wantsEnter() {
    return false;
  }

  String pending();

  /** What the viewport shows: the current target, its tokens, and their plain reading. */
  String lineText();

  /** Forgets the current target and typing order, as for a new study. */
  void reset();

  boolean enabled();
}
