/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.pref;

import java.awt.Color;
import org.osgi.service.prefs.Preferences;
import org.weasis.core.api.image.ZoomOp.Interpolation;
import org.weasis.core.api.service.BundlePreferences;

public class ZoomSetting {

  public static final String PREFERENCE_NODE = "zoom"; // NON-NLS
  public static final int DEFAULT_ACTUAL_PIXEL_ZOOM_PERCENT = 50;
  public static final int MIN_ACTUAL_PIXEL_ZOOM_PERCENT = 5;
  public static final int MAX_ACTUAL_PIXEL_ZOOM_PERCENT = 200;
  public static final int DEFAULT_KEYBOARD_ZOOM_PERCENT = 5;
  public static final int MIN_KEYBOARD_ZOOM_PERCENT = 1;
  public static final int MAX_KEYBOARD_ZOOM_PERCENT = 100;
  // public static final String P_ZOOM_SYNCH = "zoom.synch";
  // public static final String P_SHOW_DRAWINGS = "show.drawings";
  // public static final String P_ROUND = "round";

  private boolean lensShowDrawings = true;
  private boolean lensSynchronize = false;
  private int lensWidth = 200;
  private int lensHeight = 200;
  private int interpolation = 1;
  private int actualPixelZoomPercent = DEFAULT_ACTUAL_PIXEL_ZOOM_PERCENT;
  private int keyboardZoomPercent = DEFAULT_KEYBOARD_ZOOM_PERCENT;
  private boolean lensRound = false;
  private int lensLineWidth = 2;
  private Color lensLineColor = new Color(195, 109, 254);

  public void applyPreferences(Preferences prefs) {
    if (prefs != null) {
      Preferences p = prefs.node(ZoomSetting.PREFERENCE_NODE);
      setInterpolation(p.getInt("interpolation", Interpolation.BILINEAR.ordinal())); // NON-NLS
      setActualPixelZoomPercent(
          p.getInt("actualPixelZoomPercent", DEFAULT_ACTUAL_PIXEL_ZOOM_PERCENT)); // NON-NLS
      setKeyboardZoomPercent(
          p.getInt("keyboardZoomPercent", DEFAULT_KEYBOARD_ZOOM_PERCENT)); // NON-NLS
    }
  }

  public void savePreferences(Preferences prefs) {
    if (prefs != null) {
      Preferences p = prefs.node(ZoomSetting.PREFERENCE_NODE);
      BundlePreferences.putIntPreferences(p, "interpolation", interpolation); // NON-NLS
      BundlePreferences.putIntPreferences(
          p, "actualPixelZoomPercent", actualPixelZoomPercent); // NON-NLS
      BundlePreferences.putIntPreferences(p, "keyboardZoomPercent", keyboardZoomPercent); // NON-NLS
    }
  }

  public boolean isLensShowDrawings() {
    return lensShowDrawings;
  }

  public void setLensShowDrawings(boolean lensShowDrawings) {
    this.lensShowDrawings = lensShowDrawings;
  }

  public boolean isLensSynchronize() {
    return lensSynchronize;
  }

  public void setLensSynchronize(boolean lensSynchronize) {
    this.lensSynchronize = lensSynchronize;
  }

  public int getLensWidth() {
    return lensWidth;
  }

  public void setLensWidth(int lensWidth) {
    this.lensWidth = lensWidth;
  }

  public int getLensHeight() {
    return lensHeight;
  }

  public void setLensHeight(int lensHeight) {
    this.lensHeight = lensHeight;
  }

  /**
   * @return ordinal value of ZoomOp.Interpolation
   */
  public int getInterpolation() {
    return interpolation;
  }

  /**
   * Set the zoom interpolation
   *
   * @param interpolation ordinal value of ZoomOp.Interpolation
   */
  public void setInterpolation(int interpolation) {
    if (interpolation < 0 || interpolation >= Interpolation.values().length) {
      this.interpolation = Interpolation.BILINEAR.ordinal();
    } else {
      this.interpolation = interpolation;
    }
  }

  public int getActualPixelZoomPercent() {
    return actualPixelZoomPercent;
  }

  public void setActualPixelZoomPercent(int actualPixelZoomPercent) {
    this.actualPixelZoomPercent =
        Math.max(
            MIN_ACTUAL_PIXEL_ZOOM_PERCENT,
            Math.min(MAX_ACTUAL_PIXEL_ZOOM_PERCENT, actualPixelZoomPercent));
  }

  public double getActualPixelZoomScale() {
    return actualPixelZoomPercent / 100.0;
  }

  public int getKeyboardZoomPercent() {
    return keyboardZoomPercent;
  }

  public void setKeyboardZoomPercent(int keyboardZoomPercent) {
    this.keyboardZoomPercent =
        Math.max(
            MIN_KEYBOARD_ZOOM_PERCENT, Math.min(MAX_KEYBOARD_ZOOM_PERCENT, keyboardZoomPercent));
  }

  public double getKeyboardZoomFactor() {
    return 1.0 + keyboardZoomPercent / 100.0;
  }

  public boolean isLensRound() {
    return lensRound;
  }

  public void setLensRound(boolean lensRound) {
    this.lensRound = lensRound;
  }

  public int getLensLineWidth() {
    return lensLineWidth;
  }

  public void setLensLineWidth(int lensLineWidth) {
    this.lensLineWidth = lensLineWidth;
  }

  public Color getLensLineColor() {
    return lensLineColor;
  }

  public void setLensLineColor(Color lensLineColor) {
    this.lensLineColor = lensLineColor;
  }
}
