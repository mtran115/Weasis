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

import bibliothek.gui.Dockable;
import bibliothek.gui.dock.ScreenDockStation;
import bibliothek.gui.dock.common.CControl;
import bibliothek.gui.dock.common.CStation;
import bibliothek.gui.dock.common.intern.CommonDockable;
import bibliothek.gui.dock.station.screen.ScreenDockWindow;
import bibliothek.gui.dock.station.screen.ScreenDockWindowConfiguration;
import bibliothek.gui.dock.station.screen.ScreenDockWindowFactory;
import bibliothek.gui.dock.station.screen.window.DefaultScreenDockWindowFactory;
import bibliothek.gui.dock.station.screen.window.DefaultScreenDockWindowFactory.Kind;
import bibliothek.gui.dock.station.screen.window.ScreenDockFrame;
import bibliothek.gui.dock.station.screen.window.WindowConfiguration;

final class ComposerWindowSupport {
  private ComposerWindowSupport() {}

  static boolean installNativePopOut(
      CControl control, CommonDockable composerDockable, String windowTitle) {
    CStation<?> externalizedArea = control.getStation(CControl.EXTERNALIZED_STATION_ID);
    if (externalizedArea == null
        || !(externalizedArea.getStation().getDockStation()
            instanceof ScreenDockStation screenStation)) {
      return false;
    }

    if (screenStation.getWindowFactory() instanceof ComposerWindowFactory factory
        && factory.targets(composerDockable)) {
      return true;
    }

    ScreenDockWindowFactory delegateFactory = screenStation.getWindowFactory();
    ScreenDockWindowConfiguration delegateConfiguration = screenStation.getWindowConfiguration();
    screenStation.setWindowFactory(
        new ComposerWindowFactory(delegateFactory, composerDockable, windowTitle));
    screenStation.setWindowConfiguration(
        new ComposerWindowConfiguration(delegateConfiguration, composerDockable));
    return true;
  }

  private static final class ComposerWindowConfiguration implements ScreenDockWindowConfiguration {
    private final ScreenDockWindowConfiguration delegate;
    private final CommonDockable composerDockable;

    private ComposerWindowConfiguration(
        ScreenDockWindowConfiguration delegate, CommonDockable composerDockable) {
      this.delegate = delegate;
      this.composerDockable = composerDockable;
    }

    @Override
    public WindowConfiguration getConfiguration(ScreenDockStation station, Dockable dockable) {
      WindowConfiguration configuration = delegate.getConfiguration(station, dockable);
      if (dockable == composerDockable) {
        return new ComposerFrameConfiguration(configuration);
      }
      return configuration;
    }
  }

  private static final class ComposerFrameConfiguration extends WindowConfiguration {
    private ComposerFrameConfiguration(WindowConfiguration source) {
      WindowConfiguration defaults = source == null ? new WindowConfiguration() : source;
      setMoveOnTitleGrab(false);
      setMoveOnBorder(false);
      setAllowDragAndDropOnTitle(false);
      setResetOnDropable(defaults.isResetOnDropable());
      setResizeable(defaults.isResizeable());
      setTransparent(false);
      setShape(null);
      setBorderFactory(defaults.getBorderFactory());
    }
  }

  private static final class ComposerWindowFactory implements ScreenDockWindowFactory {
    private final ScreenDockWindowFactory delegate;
    private final CommonDockable composerDockable;
    private final DefaultScreenDockWindowFactory frameFactory;

    private ComposerWindowFactory(
        ScreenDockWindowFactory delegate, CommonDockable composerDockable, String windowTitle) {
      this.delegate = delegate;
      this.composerDockable = composerDockable;
      this.frameFactory = new DefaultScreenDockWindowFactory();
      frameFactory.setKind(Kind.FRAME);
      frameFactory.setUndecorated(false);
      frameFactory.setShowDockTitle(false);
      frameFactory.setTitleText(windowTitle);
    }

    private boolean targets(CommonDockable dockable) {
      return composerDockable == dockable;
    }

    @Override
    public ScreenDockWindow createWindow(
        ScreenDockStation station, WindowConfiguration configuration) {
      if (configuration instanceof ComposerFrameConfiguration) {
        return frameFactory.createWindow(station, configuration);
      }
      return delegate.createWindow(station, configuration);
    }

    @Override
    public ScreenDockWindow updateWindow(
        ScreenDockWindow window, WindowConfiguration configuration, ScreenDockStation station) {
      if (configuration instanceof ComposerFrameConfiguration) {
        if (window instanceof ScreenDockFrame) {
          return window;
        }
        return frameFactory.updateWindow(window, configuration, station);
      }

      // Installing this targeted factory must not recreate unrelated external viewer windows.
      return window;
    }
  }
}
