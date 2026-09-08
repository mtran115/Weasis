/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.display;

import java.util.Map;
import java.util.Optional;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.img.data.PrDicomObject;
import org.dcm4che3.img.lut.PresetWindowLevel;
import org.dcm4che3.img.util.PaletteColorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.ImageOpEvent;
import org.weasis.core.api.image.ImageOpEvent.OpEvent;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.util.LangUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.PRSpecialElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.lut.DefaultWlPresentation;
import org.weasis.opencv.op.lut.LutShape;
import org.weasis.opencv.op.lut.WlPresentation;

public class WindowAndPresetsOp extends WindowOp {

  private static final Logger LOGGER = LoggerFactory.getLogger(WindowAndPresetsOp.class);
  private static final double MIN_USABLE_DISPLAY_SPAN = 0.35;
  private static final double DARK_DISPLAY_CEILING = 0.35;
  private static final double BRIGHT_DISPLAY_FLOOR = 0.65;

  public static final String P_PR_ELEMENT = "pr.element";

  @Override
  public void handleImageOpEvent(ImageOpEvent event) {
    OpEvent type = event.eventType();
    if (OpEvent.IMAGE_CHANGE.equals(type)) {
      ImageElement previousImage = (ImageElement) getParam(P_IMAGE_ELEMENT);
      ImageElement img = event.image();
      setParam(P_IMAGE_ELEMENT, img);
      removeParam(P_PR_ELEMENT);

      if (img != null && img != previousImage) {
        PresetWindowLevel preset = (PresetWindowLevel) getParam(ActionW.PRESET.cmd());
        if (preset != null && preset.isAutoLevel()) {
          applyAutoPreset(img);
        } else if (LangUtil.nullToTrue((Boolean) getParam(ActionW.DEFAULT_PRESET.cmd()))) {
          if (isMrSeries(event)) {
            applyAutoPresetIfImplausible(img, preset);
          } else {
            applyDefaultPreset(img, false, false);
          }
        }
      }
    } else if (OpEvent.RESET_DISPLAY.equals(type) || OpEvent.SERIES_CHANGE.equals(type)) {
      ImageElement img = event.image();
      setParam(P_IMAGE_ELEMENT, img);
      PrDicomObject pr = (PrDicomObject) getParam(P_PR_ELEMENT);
      removeParam(P_PR_ELEMENT);
      if (img != null) {
        applyDefaultPreset(img, pr != null, isMrSeries(event));
      }
    } else if (OpEvent.APPLY_PR.equals(type)) {
      ImageElement img = event.image();
      setParam(P_IMAGE_ELEMENT, img);
      if (img != null) {
        if (!img.isImageAvailable()) {
          // Ensure to load image before calling the default preset that requires pixel min and max
          img.getImage();
        }
        boolean pixelPadding =
            LangUtil.nullToTrue((Boolean) getParam(ActionW.IMAGE_PIX_PADDING.cmd()));
        Map<String, Object> p = event.params();
        if (p != null) {
          PRSpecialElement pr =
              Optional.ofNullable(p.get(ActionW.PR_STATE.cmd()))
                  .filter(PRSpecialElement.class::isInstance)
                  .map(PRSpecialElement.class::cast)
                  .orElse(null);
          setParam(P_PR_ELEMENT, pr == null ? null : pr.getPrDicomObject());

          PresetWindowLevel preset = (PresetWindowLevel) p.get(ActionW.PRESET.cmd());
          if (preset == null && img instanceof DicomImageElement imageElement) {
            DefaultWlPresentation wlp = new DefaultWlPresentation(null, pixelPadding);
            preset = imageElement.getDefaultPreset(wlp);
          }
          setPreset(preset, img, pixelPadding, true);
        }
      }
    }
  }

  private static boolean isMrSeries(ImageOpEvent event) {
    if (event.series() == null) {
      return false;
    }
    String modality = TagD.getTagValue(event.series(), Tag.Modality, String.class);
    return "MR".equalsIgnoreCase(modality);
  }

  private void applyDefaultPreset(
      ImageElement img, boolean reloadPresetList, boolean validateMrPreset) {
    if (!img.isImageAvailable()) {
      // Ensure to load image before calling the default preset that requires pixel min and max
      img.getImage();
    }

    boolean pixelPadding = LangUtil.nullToTrue((Boolean) getParam(ActionW.IMAGE_PIX_PADDING.cmd()));
    PresetWindowLevel preset = null;
    boolean defaultPreset = true;
    if (img instanceof DicomImageElement imageElement) {
      DefaultWlPresentation wlp = new DefaultWlPresentation(null, pixelPadding);
      if (reloadPresetList) {
        imageElement.getPresetList(wlp, true);
      }
      preset = imageElement.getDefaultPreset(wlp);
      double imageMin = imageElement.getMinValue(wlp);
      double imageMax = imageElement.getMaxValue(wlp);
      if (validateMrPreset && isImplausiblePreset(preset, imageElement, wlp)) {
        PresetWindowLevel autoPreset = findAutoPreset(imageElement, wlp);
        if (autoPreset != null) {
          LOGGER.warn(
              "Ignoring implausible MR DICOM window/level preset W:{}, L:{} for image range [{}, {}]",
              preset.getWindow(),
              preset.getLevel(),
              imageMin,
              imageMax);
          preset = autoPreset;
          defaultPreset = false;
        }
      }
    }
    setPreset(preset, img, pixelPadding, defaultPreset);
  }

  private static PresetWindowLevel findAutoPreset(
      DicomImageElement image, DefaultWlPresentation wlp) {
    return image.getPresetList(wlp).stream()
        .filter(PresetWindowLevel::isAutoLevel)
        .findFirst()
        .orElse(null);
  }

  private void applyAutoPreset(ImageElement img) {
    if (!img.isImageAvailable()) {
      img.getImage();
    }

    boolean pixelPadding = LangUtil.nullToTrue((Boolean) getParam(ActionW.IMAGE_PIX_PADDING.cmd()));
    if (img instanceof DicomImageElement imageElement) {
      DefaultWlPresentation wlp = new DefaultWlPresentation(null, pixelPadding);
      PresetWindowLevel autoPreset = findAutoPreset(imageElement, wlp);
      if (autoPreset != null) {
        setPreset(autoPreset, img, pixelPadding, false);
      }
    }
  }

  private void applyAutoPresetIfImplausible(ImageElement img, PresetWindowLevel preset) {
    if (!img.isImageAvailable()) {
      img.getImage();
    }
    if (!(img instanceof DicomImageElement imageElement)) {
      return;
    }

    boolean pixelPadding = LangUtil.nullToTrue((Boolean) getParam(ActionW.IMAGE_PIX_PADDING.cmd()));
    DefaultWlPresentation wlp = new DefaultWlPresentation(null, pixelPadding);
    double window =
        preset == null ? getNumberParam(ActionW.WINDOW.cmd(), Double.NaN) : preset.getWindow();
    double level =
        preset == null ? getNumberParam(ActionW.LEVEL.cmd(), Double.NaN) : preset.getLevel();
    double imageMin = imageElement.getMinValue(wlp);
    double imageMax = imageElement.getMaxValue(wlp);
    LutShape shape =
        preset == null ? (LutShape) getParam(ActionW.LUT_SHAPE.cmd()) : preset.getLutShape();
    if (!isImplausibleWindowLevel(window, level, shape, imageElement, wlp)) {
      return;
    }

    PresetWindowLevel autoPreset = findAutoPreset(imageElement, wlp);
    if (autoPreset != null) {
      LOGGER.warn(
          "Switching implausible MR default window/level W:{}, L:{} to Auto Level for image range [{}, {}]",
          window,
          level,
          imageMin,
          imageMax);
      setPreset(autoPreset, img, pixelPadding, false);
    }
  }

  private double getNumberParam(String key, double defaultValue) {
    Object value = getParam(key);
    return value instanceof Number number ? number.doubleValue() : defaultValue;
  }

  public static boolean isImplausiblePreset(
      PresetWindowLevel preset, double imageMin, double imageMax) {
    if (preset == null
        || preset.isAutoLevel()
        || preset.getLutShape() == null
        || preset.getLutShape().getFunctionType() != LutShape.Function.LINEAR) {
      return false;
    }
    return isImplausibleWindowLevel(preset.getWindow(), preset.getLevel(), imageMin, imageMax);
  }

  public static boolean isImplausiblePreset(
      PresetWindowLevel preset, DicomImageElement image, WlPresentation wlp) {
    return preset != null
        && !preset.isAutoLevel()
        && isImplausibleWindowLevel(
            preset.getWindow(), preset.getLevel(), preset.getLutShape(), image, wlp);
  }

  public static boolean isImplausibleWindowLevel(
      double window, double level, LutShape shape, DicomImageElement image, WlPresentation wlp) {
    // A linear grayscale estimate cannot assess a custom VOI LUT or presentation state.
    if ((shape != null && shape.getFunctionType() != LutShape.Function.LINEAR)
        || (wlp != null && wlp.getPresentationState() != null)) {
      return false;
    }
    MrWindowLevelRange range = image.getMrWindowLevelRange(wlp);
    return isImplausibleWindowLevel(
        window,
        level,
        range == null ? image.getMinValue(wlp) : range.min(),
        range == null ? image.getMaxValue(wlp) : range.max());
  }

  public static boolean isImplausibleWindowLevel(
      double window, double level, double imageMin, double imageMax) {
    double windowWidth = Math.abs(window);
    if (!Double.isFinite(windowWidth)
        || windowWidth <= 0.0
        || !Double.isFinite(level)
        || !Double.isFinite(imageMin)
        || !Double.isFinite(imageMax)
        || imageMax <= imageMin) {
      return false;
    }

    double windowMin = level - windowWidth / 2.0;
    double displayedMin = clampToDisplay((imageMin - windowMin) / windowWidth);
    double displayedMax = clampToDisplay((imageMax - windowMin) / windowWidth);
    double displayedSpan = displayedMax - displayedMin;

    // Keep broad, centered clinical presets. Fall back only when the entire pixel range is
    // compressed into roughly the bottom or top third of the available grayscale.
    return displayedSpan < MIN_USABLE_DISPLAY_SPAN
        && (displayedMax <= DARK_DISPLAY_CEILING || displayedMin >= BRIGHT_DISPLAY_FLOOR);
  }

  private static double clampToDisplay(double value) {
    return Math.max(0.0, Math.min(1.0, value));
  }

  private void setPreset(
      PresetWindowLevel preset, ImageElement img, boolean pixelPadding, boolean defaultPreset) {
    boolean p = preset != null;
    PrDicomObject pr = (PrDicomObject) getParam(P_PR_ELEMENT);
    setParam(ActionW.PRESET.cmd(), preset);
    setParam(ActionW.DEFAULT_PRESET.cmd(), defaultPreset);
    DefaultWlPresentation wlp = new DefaultWlPresentation(pr, pixelPadding);
    setParam(ActionW.WINDOW.cmd(), p ? preset.getWindow() : img.getDefaultWindow(wlp));
    setParam(ActionW.LEVEL.cmd(), p ? preset.getLevel() : img.getDefaultLevel(wlp));
    setParam(ActionW.LEVEL_MIN.cmd(), img.getMinValue(wlp));
    setParam(ActionW.LEVEL_MAX.cmd(), img.getMaxValue(wlp));
    setParam(ActionW.LUT_SHAPE.cmd(), p ? preset.getLutShape() : img.getDefaultShape(wlp));
  }

  public void process() throws Exception {
    PlanarImage source = getSourceImage();
    PlanarImage result = source;
    ImageElement imageElement = (ImageElement) params.get(P_IMAGE_ELEMENT);

    if (imageElement != null) {
      PrDicomObject pr = (PrDicomObject) params.get(P_PR_ELEMENT);
      if (pr != null
          && UID.PseudoColorSoftcopyPresentationStateStorage.equals(
              pr.getDicomObject().getString(Tag.SOPClassUID))) {
        var lookup = PaletteColorUtils.getPaletteColorLookupTable(pr.getDicomObject());
        source = PaletteColorUtils.getRGBImageFromPaletteColorModel(source, lookup);
      }
      result = imageElement.getRenderedImage(source, params);
    }

    params.put(Param.OUTPUT_IMG, result);
  }
}
