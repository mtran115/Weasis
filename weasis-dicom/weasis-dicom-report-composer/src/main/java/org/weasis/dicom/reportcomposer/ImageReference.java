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

public record ImageReference(
    String seriesInstanceUid,
    String sopInstanceUid,
    String seriesNumber,
    String seriesDescription,
    String instanceNumber,
    int displayIndex,
    int seriesSize) {

  public ImageReference {
    seriesInstanceUid = ComposerText.clean(seriesInstanceUid);
    sopInstanceUid = ComposerText.clean(sopInstanceUid);
    seriesNumber = ComposerText.clean(seriesNumber);
    seriesDescription = ComposerText.clean(seriesDescription);
    instanceNumber = ComposerText.clean(instanceNumber);
    displayIndex = Math.max(1, displayIndex);
    seriesSize = Math.max(displayIndex, seriesSize);
  }

  public String humanReference() {
    String series = seriesNumber.isBlank() ? "Series ?" : "Series " + seriesNumber;
    if (!seriesDescription.isBlank()) {
      series += " (" + seriesDescription + ")";
    }
    String image = instanceNumber.isBlank() ? Integer.toString(displayIndex) : instanceNumber;
    return series + ", image " + image;
  }

  public String fileReference() {
    String series = seriesNumber.isBlank() ? "UNKNOWN" : seriesNumber;
    String image = instanceNumber.isBlank() ? Integer.toString(displayIndex) : instanceNumber;
    return "SERIES "
        + ComposerText.fileSafeUpper(series, "UNKNOWN")
        + " IMAGE "
        + ComposerText.fileSafeUpper(image, "UNKNOWN");
  }
}
