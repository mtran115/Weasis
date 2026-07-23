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

public class WindowAndPresetsOp extends WindowOp {

  private static final Logger LOGGER = LoggerFactory.getLogger(WindowAndPresetsOp.class);
  private static final double IMPLAUSIBLE_LEVEL_DISTANCE_RATIO = 2.0;
  private static final double IMPLAUSIBLE_WINDOW_RATIO = 4.0;

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
        } else if (LangUtil.nullToTrue((Boolean) getParam(ActionW.DEFAULT_PRESET.cmd()))
            && !isMrSeries(event)) {
          applyDefaultPreset(img, false, false);
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
      if (validateMrPreset && isImplausiblePreset(preset, imageMin, imageMax)) {
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

  public static boolean isImplausiblePreset(
      PresetWindowLevel preset, double imageMin, double imageMax) {
    if (preset == null
        || preset.isAutoLevel()
        || !Double.isFinite(imageMin)
        || !Double.isFinite(imageMax)
        || imageMax <= imageMin) {
      return false;
    }

    double imageRange = imageMax - imageMin;
    double imageMidpoint = imageMin + imageRange / 2.0;
    double levelDistanceRatio = Math.abs(preset.getLevel() - imageMidpoint) / imageRange;
    double windowRatio = Math.abs(preset.getWindow()) / imageRange;

    // Requiring both large offsets keeps intentionally broad clinical presets valid.
    return levelDistanceRatio >= IMPLAUSIBLE_LEVEL_DISTANCE_RATIO
        && windowRatio >= IMPLAUSIBLE_WINDOW_RATIO;
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
