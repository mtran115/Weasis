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

import org.junit.jupiter.api.Test;

class ComposerTextTest {
  @Test
  void formatsDicomPersonNamesForDisplay() {
    assertEquals("DOE, JANE MARIE", ComposerText.displayPersonName("DOE^JANE^MARIE"));
  }

  @Test
  void producesUppercaseFilesystemSafeNames() {
    assertEquals("GARCIA_ANA_TEST", ComposerText.fileSafeUpper("García^Ana / Test", "UNKNOWN"));
    assertEquals("UNKNOWN", ComposerText.fileSafeUpper("///", "UNKNOWN"));
  }

  @Test
  void addsSentencePunctuationOnlyWhenNeeded() {
    assertEquals("Ligament tear.", ComposerText.sentence("Ligament tear"));
    assertEquals("Ligament intact.", ComposerText.sentence("Ligament intact."));
  }
}
