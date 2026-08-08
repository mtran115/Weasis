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

public record ArrowPlacement(double tailX, double tailY, double tipX, double tipY) {
  public ArrowPlacement {
    validate(tailX, "tailX");
    validate(tailY, "tailY");
    validate(tipX, "tipX");
    validate(tipY, "tipY");
  }

  private static void validate(double value, String name) {
    if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
      throw new IllegalArgumentException(name + " must be a normalized coordinate.");
    }
  }
}
