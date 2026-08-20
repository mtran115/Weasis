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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bibliothek.gui.dock.ScreenDockStation;
import bibliothek.gui.dock.common.CControl;
import bibliothek.gui.dock.common.CLocation;
import bibliothek.gui.dock.common.DefaultSingleCDockable;
import bibliothek.gui.dock.common.mode.ExtendedMode;
import bibliothek.gui.dock.station.screen.ScreenDockWindow;
import bibliothek.gui.dock.station.screen.window.ScreenDockFrame;
import java.awt.GraphicsEnvironment;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class ComposerWindowSupportTest {
  @Test
  void externalizesComposerAsNormalNativeFrame() throws Exception {
    Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());

    SwingUtilities.invokeAndWait(
        () -> {
          JFrame owner = new JFrame("Owner");
          CControl control = new CControl(owner);
          try {
            owner.setContentPane(control.getContentArea());
            DefaultSingleCDockable dockable =
                new DefaultSingleCDockable("report-composer-test", "Report Composer");
            dockable.add(new JPanel());
            dockable.setExternalizable(true);
            dockable.setDefaultLocation(
                ExtendedMode.NORMALIZED, CLocation.base().normalRectangle(0.7, 0.0, 0.3, 1.0));
            control.addDockable(dockable);
            dockable.setVisible(true);

            assertTrue(
                ComposerWindowSupport.installNativePopOut(
                    control, dockable.intern(), "Report Composer"));
            dockable.setLocation(CLocation.external(40, 40, 500, 700));

            ScreenDockStation station =
                (ScreenDockStation)
                    control
                        .getStation(CControl.EXTERNALIZED_STATION_ID)
                        .getStation()
                        .getDockStation();
            ScreenDockWindow externalWindow = station.getWindow(dockable.intern());
            ScreenDockFrame frame = assertInstanceOf(ScreenDockFrame.class, externalWindow);
            assertFalse(frame.getFrame().isAlwaysOnTop());
          } finally {
            control.destroy();
            owner.dispose();
          }
        });
  }
}
