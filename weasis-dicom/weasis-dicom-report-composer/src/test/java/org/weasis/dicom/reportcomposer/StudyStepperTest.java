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

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StudyStepperTest {
  private static final List<String> STUDIES = List.of("A", "B", "C");

  @Test
  void theNextStudyIsTheOneAfterAndStopsAtEitherEnd() {
    assertEquals(Optional.of("B"), StudyStepper.adjacent(STUDIES, "A", false));
    assertEquals(Optional.of("A"), StudyStepper.adjacent(STUDIES, "B", true));
    assertEquals(Optional.empty(), StudyStepper.adjacent(STUDIES, "C", false));
    assertEquals(Optional.empty(), StudyStepper.adjacent(STUDIES, "A", true));
    // Nothing open yet: the next key opens the first study.
    assertEquals(Optional.of("A"), StudyStepper.adjacent(STUDIES, null, false));
    assertEquals(Optional.empty(), StudyStepper.adjacent(STUDIES, null, true));
  }

  @Test
  void viewportsFillInOrderAndExtraViewportsAreEmptied() {
    assertEquals(
        List.of(Optional.of("SAG T2"), Optional.of("COR T2")),
        StudyStepper.slots(List.of("SAG T2", "COR T2", "SAG STIR"), 2));
    assertEquals(
        List.of(Optional.of("SAG T2"), Optional.empty()), StudyStepper.slots(List.of("SAG T2"), 2));
  }
}
