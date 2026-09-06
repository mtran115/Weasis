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

import com.formdev.flatlaf.FlatClientProperties;
import javax.swing.JToggleButton;

final class FindingToggleButton extends JToggleButton {
  FindingToggleButton(String text) {
    super(text);
    putClientProperty(
        FlatClientProperties.STYLE,
        "selectedBackground:#2563eb; selectedForeground:#ffffff;"
            + "pressedBackground:#1d4ed8; pressedForeground:#ffffff;"
            + "selectedBorderColor:#2563eb; focusedSelectedBorderColor:#60a5fa;"
            + "hoverSelectedBorderColor:#3b82f6; pressedSelectedBorderColor:#1d4ed8");
  }
}
