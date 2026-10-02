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

import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.service.WProperties;

/**
 * Whether exported cases are archived locally. The reader's own choice wins; without one, archiving
 * stays on only where an archive already exists, so a new install starts with it off.
 */
final class LocalArchiveSetting implements BooleanSupplier {
  static final String KEY = "report.composer.local.archive";
  private final WProperties persistence;
  private final boolean defaultValue;

  LocalArchiveSetting(WProperties persistence, boolean defaultValue) {
    this.persistence = persistence;
    this.defaultValue = defaultValue;
  }

  static LocalArchiveSetting load(Path trainingRoot) {
    return new LocalArchiveSetting(
        GuiUtils.getUICore().getLocalPersistence(),
        TrainingCaseStore.hasArchivedCases(trainingRoot));
  }

  @Override
  public boolean getAsBoolean() {
    String choice = persistence.getProperty(KEY, "");
    return choice.isBlank() ? defaultValue : Boolean.parseBoolean(choice);
  }

  void set(boolean enabled) {
    persistence.setProperty(KEY, Boolean.toString(enabled));
  }
}
