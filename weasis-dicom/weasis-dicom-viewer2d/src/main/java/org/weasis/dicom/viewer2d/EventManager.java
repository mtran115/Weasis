/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.DataBuffer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import javax.swing.BoundedRangeModel;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.data.PrDicomObject;
import org.dcm4che3.img.lut.PresetWindowLevel;
import org.osgi.framework.BundleContext;
import org.osgi.service.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.command.Option;
import org.weasis.core.api.command.Options;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.gui.Insertable.Type;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.gui.layout.MigLayoutModel;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.BasicActionState;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.Feature;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.RadioMenuItem;
import org.weasis.core.api.gui.util.ShortcutManager;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.api.gui.util.SliderCineListener.TIME;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.image.FilterOp;
import org.weasis.core.api.image.ImageOpNode;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.PseudoColorOp;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.image.op.ByteLutCollection;
import org.weasis.core.api.image.util.KernelData;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.SeriesComparator;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.editor.image.SynchCineEvent;
import org.weasis.core.ui.editor.image.SynchData;
import org.weasis.core.ui.editor.image.SynchEvent;
import org.weasis.core.ui.editor.image.SynchManager;
import org.weasis.core.ui.editor.image.SynchView;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerToolBar;
import org.weasis.core.ui.editor.image.ZoomToolBar;
import org.weasis.core.ui.launcher.Launcher;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.utils.bean.PanPoint;
import org.weasis.core.util.LangUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.PRSpecialElement;
import org.weasis.dicom.codec.PresentationStateReader;
import org.weasis.dicom.codec.SortSeriesStack;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.display.WindowAndPresetsOp;
import org.weasis.dicom.codec.geometry.ImageOrientation;
import org.weasis.dicom.codec.utils.DicomResource;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.exp.DicomExportAction;
import org.weasis.dicom.explorer.main.DicomExplorer;
import org.weasis.dicom.explorer.main.DicomExplorer.ListPosition;
import org.weasis.dicom.viewer2d.mip.MipView;
import org.weasis.dicom.viewer2d.mpr.MprAxis;
import org.weasis.dicom.viewer2d.mpr.MprContainer;
import org.weasis.dicom.viewer2d.mpr.MprController;
import org.weasis.dicom.viewer2d.mpr.MprView;
import org.weasis.dicom.viewer2d.mpr.Volume;
import org.weasis.opencv.op.ImageConversion;
import org.weasis.opencv.op.lut.ByteLut;
import org.weasis.opencv.op.lut.ColorLut;
import org.weasis.opencv.op.lut.DefaultWlPresentation;
import org.weasis.opencv.op.lut.LutShape;

/**
 * The event processing center for this application. This class responses for loading data sets,
 * processing the events from the utility menu that includes changing the operation scope, the
 * layout, window/level, rotation angle, zoom factor, starting/stoping the cining-loop etc.
 */
public class EventManager extends ImageViewerEventManager<DicomImageElement>
    implements ActionListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(EventManager.class);
  public static final double DEFAULT_ZOOM_MOUSE_SENSITIVITY = 4.0;
  private static final double LEGACY_ZOOM_MOUSE_SENSITIVITY = 0.1;
  private static final double PREVIOUS_ZOOM_MOUSE_SENSITIVITY = 0.5;
  private static final double PREVIOUS_FAST_ZOOM_MOUSE_SENSITIVITY = 1.0;
  private static final double CURRENT_ZOOM_MOUSE_SENSITIVITY = 2.0;
  private static final double ZOOM_SENSITIVITY_MIGRATION_TOLERANCE = 0.03;
  private static final String ZOOM_SENSITIVITY_MIGRATED_KEY = "zoomSensitivityMigratedV5";
  private static final Set<String> XRAY_MODALITIES =
      Set.of("CR", "DX", "DR", "MG", "RF", "XA", "IO", "PX");

  public static final List<String> functions =
      List.of(
          "zoom", // NON-NLS
          "wl", // NON-NLS
          "move", // NON-NLS
          "scroll", // NON-NLS
          "layout", // NON-NLS
          "mouseLeftAction", // NON-NLS
          "synch", // NON-NLS
          "reset"); // NON-NLS

  /** The single instance of this singleton class. */
  private static EventManager instance;

  private final WindowLevelSetting windowLevelSetting = new WindowLevelSetting();
  private final Map<ViewCanvas<DicomImageElement>, KeyboardWindowLevelState> previousWindowLevels =
      new WeakHashMap<>();

  /**
   * Return the single instance of this class. This method guarantees the singleton property of this
   * class.
   */
  public static synchronized EventManager getInstance() {
    if (instance == null) {
      instance = new EventManager();
    }
    return instance;
  }

  /** The default private constructor to guarantee the singleton property of this class. */
  private EventManager() {
    // Initialize actions with a null value. These are used by mouse or keyevent actions.
    setAction(new BasicActionState(ActionW.WINLEVEL));
    setAction(new BasicActionState(ActionW.CONTEXTMENU));
    setAction(new BasicActionState(ActionW.NO_ACTION));
    setAction(new BasicActionState(ActionW.DRAW));
    setAction(new BasicActionState(ActionW.MEASURE));
    setAction(new BasicActionState(ActionW.VOLUME));

    setAction(getMoveTroughSliceAction(20.0, TIME.SECOND, 0.1));
    setAction(newLoopSweepAction());
    setAction(newWindowAction());
    setAction(newLevelAction());
    setAction(newRotateAction());
    setAction(newZoomAction());

    setAction(newFlipAction());
    setAction(newInverseLutAction());
    setAction(newInverseStackAction());
    setAction(newLensAction());
    setAction(newLensZoomAction());
    setAction(newDrawOnlyOnceAction());
    setAction(newDefaultPresetAction());

    setAction(newPresetAction());
    setAction(newLutShapeAction());
    setAction(newLutAction());
    setAction(newFilterAction());
    setAction(newSortStackAction());
    setAction(newLayoutAction(View2dContainer.DEFAULT_LAYOUT_LIST.toArray(new MigLayoutModel[0])));
    setAction(newSynchAction(View2dContainer.DEFAULT_SYNCH_LIST.toArray(new SynchView[0])));
    getAction(ActionW.SYNCH)
        .ifPresent(a -> a.setSelectedItemWithoutTriggerAction(SynchView.DEFAULT_STACK));
    setAction(newSynchModeAction());
    setAction(newMeasurementAction(MeasureToolBar.getMeasureGraphicList().toArray(new Graphic[0])));
    setAction(newDrawAction(MeasureToolBar.getDrawGraphicList().toArray(new Graphic[0])));
    setAction(newSpatialUnit(Unit.values()));
    setAction(newPanAction());
    setAction(newCrosshairAction());
    setAction(new BasicActionState(ActionW.RESET));
    setAction(new BasicActionState(ActionW.SHOW_HEADER));

    setAction(newKOToggleAction());
    setAction(newKOFilterAction());
    setAction(newKOSelectionAction());

    final BundleContext context = AppProperties.getBundleContext(this.getClass());
    Preferences prefs = BundlePreferences.getDefaultPreferences(context);
    zoomSetting.applyPreferences(prefs);
    windowLevelSetting.applyPreferences(prefs);
    mouseActions.applyPreferences(prefs);

    if (prefs != null) {
      Preferences prefNode = prefs.node("mouse.sensivity");
      getSliderPreference(prefNode, ActionW.WINDOW, 1.25);
      getSliderPreference(prefNode, ActionW.LEVEL, 1.25);
      getSliderPreference(prefNode, ActionW.SCROLL_SERIES, 0.1);
      getSliderPreference(prefNode, ActionW.ROTATION, 0.25);
      getSliderPreference(prefNode, ActionW.ZOOM, DEFAULT_ZOOM_MOUSE_SENSITIVITY);

      /*
       * Get first the local value if existed, otherwise try to get the default server configuration and finally if
       * no value take the default value in parameter.
       */
      prefNode = prefs.node("other"); // NON-NLS
      WProperties.setProperty(
          options, WindowOp.P_APPLY_WL_COLOR, prefNode, Boolean.TRUE.toString());
      WProperties.setProperty(options, WindowOp.P_INVERSE_LEVEL, prefNode, Boolean.TRUE.toString());
      WProperties.setProperty(options, PRManager.PR_APPLY, prefNode, Boolean.FALSE.toString());

      WProperties.setProperty(options, View2d.P_CROSSHAIR_MODE, prefNode, "1");
      WProperties.setProperty(options, View2d.P_CROSSHAIR_CENTER_GAP, prefNode, "40");
    }

    initializeParameters();
  }

  private void initializeParameters() {
    enableActions(false);
  }

  private ComboItemListener<KernelData> newFilterAction() {
    return new ComboItemListener<>(
        ActionW.FILTER, KernelData.getAllFilters().toArray(new KernelData[0])) {

      @Override
      public void itemStateChanged(Object object) {
        if (object instanceof KernelData) {
          firePropertyChange(
              ActionW.SYNCH.cmd(),
              null,
              new SynchEvent(getSelectedViewPane(), action.cmd(), object));
        }
      }
    };
  }

  protected SynchManager<DicomImageElement> createSynchManager() {
    return new DicomSynchManager(this);
  }

  @Override
  protected SliderCineListener getMoveTroughSliceAction(
      double speed, TIME time, double mouseSensitivity) {
    return new SliderCineListener(ActionW.SCROLL_SERIES, 1, 2, 1, speed, time, mouseSensitivity) {

      @Override
      public void stateChanged(BoundedRangeModel model) {

        ViewCanvas<DicomImageElement> view2d = null;
        Series<DicomImageElement> series = null;
        SynchCineEvent mediaEvent = null;
        DicomImageElement image = null;
        Optional<ToggleButtonListener> defaultPresetAction = getAction(ActionW.DEFAULT_PRESET);
        boolean isDefaultPresetSelected =
            defaultPresetAction.isEmpty() || defaultPresetAction.get().isSelected();

        if (selectedView2dContainer != null) {
          view2d = selectedView2dContainer.getSelectedViewCanvas();
        }

        if (view2d instanceof MprView mprView) {
          MprContainer mprContainer = (MprContainer) selectedView2dContainer;
          MprController controller = mprContainer.getMprController();
          MprAxis axis = controller.getMprAxis(mprView.getPlane());
          int index = model.getValue() - 1;
          axis.setSliceIndex(index);
          boolean oldAdjusting = controller.isAdjusting();
          controller.setAdjusting(model.getValueIsAdjusting());
          axis.updateImage();
          image = axis.getImageElement();
          controller.setAdjusting(oldAdjusting);
          controller.fireCrossHairChanged();
          mediaEvent = new SynchCineEvent(view2d, image, index);
        } else if (view2d != null && view2d.getSeries() instanceof Series) {
          series = (Series<DicomImageElement>) view2d.getSeries();
          if (series != null) {
            // Model contains display value, value-1 is the index value of a sequence
            int index = model.getValue() - 1;
            image =
                series.getMedia(
                    index,
                    (Filter<DicomImageElement>)
                        view2d.getActionValue(ActionW.FILTERED_SERIES.cmd()),
                    view2d.getCurrentSortComparator());
            mediaEvent = new SynchCineEvent(view2d, image, index);
            // Ensure to load image before calling the default preset (requires pixel min and max)
            if (image != null && !image.isImageAvailable()) {
              image.getImage();
            }
          }
        }
        if (image != null) {
          double[] frameTimes = (double[]) image.getTagValue(TagD.get(Tag.FrameTimeVector));
          if (frameTimes != null && frameTimes.length > 1) {
            Double cineRate = TagD.getTagValue(image, Tag.CineRate, Double.class);
            if (cineRate != null) {
              setSpeed(cineRate);
            }
          }
        }

        Optional<ComboItemListener<MigLayoutModel>> layoutAction = getAction(ActionW.LAYOUT);
        Optional<ComboItemListener<SynchView>> synchAction = getAction(ActionW.SYNCH);

        if (image != null
            && layoutAction.isPresent()
            && View2dFactory.getViewTypeNumber(
                    (MigLayoutModel) layoutAction.get().getSelectedItem(), ViewCanvas.class)
                > 1
            && synchAction.isPresent()) {

          SynchView synchview = (SynchView) synchAction.get().getSelectedItem();
          if (synchview.getSynchData().isActionEnable(ActionW.SCROLL_SERIES.cmd())) {
            Double val = (Double) image.getTagValue(TagW.SlicePosition);
            if (val != null) {
              mediaEvent.setLocation(val);
            }
          } else {
            if (selectedView2dContainer != null) {
              final List<ViewCanvas<DicomImageElement>> panes =
                  selectedView2dContainer.getImagePanels();
              for (ViewCanvas<DicomImageElement> p : panes) {
                Boolean cutlines = (Boolean) p.getActionValue(ActionW.SYNCH_CROSSLINE.cmd());
                if (cutlines != null && cutlines) {
                  Double val = (Double) image.getTagValue(TagW.SlicePosition);
                  if (val != null) {
                    mediaEvent.setLocation(val);
                  } else {
                    return; // Do not throw event
                  }
                  break;
                }
              }
            }
          }
        }

        Optional<SliderChangeListener> windowAction = getAction(ActionW.WINDOW);
        Optional<SliderChangeListener> levelAction = getAction(ActionW.LEVEL);
        if (view2d != null
            && image != view2d.getImage()
            && image != null
            && windowAction.isPresent()
            && levelAction.isPresent()) {
          Optional<? extends ComboItemListener<?>> presetAction = getAction(ActionW.PRESET);
          PresetWindowLevel oldPreset =
              presetAction
                  .map(comboItemListener -> (PresetWindowLevel) comboItemListener.getSelectedItem())
                  .orElse(null);
          PresetWindowLevel newPreset = null;
          boolean pixelPadding =
              view2d
                  .getDisplayOpManager()
                  .getParamValue(WindowOp.OP_NAME, ActionW.IMAGE_PIX_PADDING.cmd(), Boolean.class)
                  .orElse(Boolean.TRUE);
          PRSpecialElement pr =
              Optional.ofNullable(view2d.getActionValue(ActionW.PR_STATE.cmd()))
                  .filter(PRSpecialElement.class::isInstance)
                  .map(PRSpecialElement.class::cast)
                  .orElse(null);
          if (pr != null && !PresentationStateReader.isImageApplicable(pr, image)) {
            // Remove the calibration included in the PR
            view2d.getImage().initPixelConfiguration();
            pr = null;
          }
          DefaultWlPresentation wlp =
              new DefaultWlPresentation(pr == null ? null : pr.getPrDicomObject(), pixelPadding);

          List<PresetWindowLevel> newPresetList = image.getPresetList(wlp);
          double imageMin = image.getMinValue(wlp);
          double imageMax = image.getMaxValue(wlp);

          if (isDefaultPresetSelected
              && WindowLevelMemory.isMrSeries(series)
              && WindowAndPresetsOp.isImplausibleWindowLevel(
                  windowAction.get().getRealValue(),
                  levelAction.get().getRealValue(),
                  imageMin,
                  imageMax)) {
            newPreset =
                newPresetList.stream()
                    .filter(PresetWindowLevel::isAutoLevel)
                    .findFirst()
                    .orElse(null);
            if (newPreset != null) {
              LOGGER.warn(
                  "Switching implausible MR default window/level W:{}, L:{} to Auto Level for image range [{}, {}]",
                  windowAction.get().getRealValue(),
                  levelAction.get().getRealValue(),
                  imageMin,
                  imageMax);
              isDefaultPresetSelected = false;
            }
          }

          // Assume the image cannot display when win =1 and level = 0
          boolean invalidWindowLevel =
              windowAction.get().getSliderValue() <= 1 && levelAction.get().getSliderValue() == 0;
          // Keep manual and DICOM MR baselines stable, but let Auto Level follow each image.
          if (newPreset == null
              && WindowLevelMemory.shouldRefreshPreset(
                  view2d.getSeries(), oldPreset, invalidWindowLevel)) {
            if (isDefaultPresetSelected) {
              newPreset = image.getDefaultPreset(wlp);
            } else {
              if (oldPreset != null) {
                for (PresetWindowLevel preset : newPresetList) {
                  if (preset.getName().equals(oldPreset.getName())) {
                    newPreset = preset;
                    break;
                  }
                }
              }
              // set default preset when the old preset is not available anymore
              if (newPreset == null) {
                newPreset = image.getDefaultPreset(wlp);
                isDefaultPresetSelected = true;
              }
            }
          }

          Optional<? extends ComboItemListener<?>> lutShapeAction = getAction(ActionW.LUT_SHAPE);
          double windowValue =
              newPreset == null ? windowAction.get().getRealValue() : newPreset.getWindow();
          double levelValue =
              newPreset == null ? levelAction.get().getRealValue() : newPreset.getLevel();
          LutShape lutShapeItem =
              newPreset == null
                  ? lutShapeAction.map(c -> (LutShape) c.getSelectedItem()).orElse(null)
                  : newPreset.getLutShape();

          Double levelMin =
              view2d
                  .getDisplayOpManager()
                  .getParamValue(WindowOp.OP_NAME, ActionW.LEVEL_MIN.cmd(), Double.class)
                  .orElse(null);
          Double levelMax =
              view2d
                  .getDisplayOpManager()
                  .getParamValue(WindowOp.OP_NAME, ActionW.LEVEL_MAX.cmd(), Double.class)
                  .orElse(null);

          if (levelMin == null || levelMax == null) {
            levelMin = Math.min(levelValue - windowValue / 2.0, imageMin);
            levelMax = Math.max(levelValue + windowValue / 2.0, imageMax);
          } else {
            levelMin = Math.min(levelMin, imageMin);
            levelMax = Math.max(levelMax, imageMax);
          }

          // FIX : setting actionInView here without firing a propertyChange avoid another call to
          // imageLayer.updateImageOperation(WindowOp.name.....
          // TODO pass to mediaEvent with PR and KO

          Optional<ImageOpNode> node = view2d.getDisplayOpManager().getNode(WindowOp.OP_NAME);
          if (node.isPresent()) {
            ImageOpNode n = node.get();
            n.setParam(ActionW.PRESET.cmd(), newPreset);
            n.setParam(ActionW.DEFAULT_PRESET.cmd(), isDefaultPresetSelected);
            n.setParam(ActionW.WINDOW.cmd(), windowValue);
            n.setParam(ActionW.LEVEL.cmd(), levelValue);
            n.setParam(ActionW.LEVEL_MIN.cmd(), levelMin);
            n.setParam(ActionW.LEVEL_MAX.cmd(), levelMax);
            n.setParam(ActionW.LUT_SHAPE.cmd(), lutShapeItem);
          }
          updateWindowLevelComponentsListener(image, view2d);
        }

        // SynchData synchData = (SynchData)
        // getSelectedViewPane().getActionsInView().get(ActionW.SYNCH_LINK.cmd());
        // if (synchData != null && synchData.isSynch()) {
        firePropertyChange(ActionW.SYNCH.cmd(), null, mediaEvent);
        // }
        if (image != null) {
          fireSeriesViewerListeners(
              new SeriesViewerEvent(selectedView2dContainer, series, image, EVENT.SELECT));
        }

        updateKeyObjectComponentsListener(view2d);
      }

      private double getImageCineRate(
          ViewCanvas<DicomImageElement> view2d, Series<DicomImageElement> series, int index) {
        DicomImageElement image =
            series.getMedia(
                index,
                (Filter<DicomImageElement>) view2d.getActionValue(ActionW.FILTERED_SERIES.cmd()),
                view2d.getCurrentSortComparator());
        if (image != null) {
          Double cineRate = TagD.getTagValue(image, Tag.CineRate, Double.class);
          if (cineRate != null) {
            return cineRate;
          }
        }
        return 0.0;
      }

      @Override
      public void mouseWheelMoved(MouseWheelEvent e) {
        if (isActionEnabled() && !e.isConsumed()) {
          setSliderValue(getSliderValue() + getWheelRotationDirection(e));
        }
      }
    };
  }

  @Override
  protected SliderChangeListener newWindowAction() {

    return new SliderChangeListener(
        ActionW.WINDOW, WINDOW_SMALLEST, WINDOW_LARGEST, WINDOW_DEFAULT, true, 1.25) {

      @Override
      public void stateChanged(BoundedRangeModel model) {
        updatePreset(getActionW().cmd(), toModelValue(model.getValue()));
      }
    };
  }

  @Override
  protected SliderChangeListener newLevelAction() {
    return new SliderChangeListener(
        ActionW.LEVEL, LEVEL_SMALLEST, LEVEL_LARGEST, LEVEL_DEFAULT, true, 1.25) {

      @Override
      public void stateChanged(BoundedRangeModel model) {
        updatePreset(getActionW().cmd(), toModelValue(model.getValue()));
      }
    };
  }

  protected void updatePreset(String cmd, Object object) {
    boolean isDefaultPresetSelected = false;
    Optional<? extends ComboItemListener<?>> presetAction = getAction(ActionW.PRESET);
    if (ActionW.PRESET.cmd().equals(cmd) && object instanceof PresetWindowLevel preset) {
      getAction(ActionW.WINDOW)
          .ifPresent(a -> a.setSliderValue(a.toSliderValue(preset.getWindow()), false));
      getAction(ActionW.LEVEL)
          .ifPresent(a -> a.setSliderValue(a.toSliderValue(preset.getLevel()), false));
      getAction(ActionW.LUT_SHAPE)
          .ifPresent(a -> a.setSelectedItemWithoutTriggerAction(preset.getLutShape()));

      PresetWindowLevel defaultPreset =
          presetAction
              .map(comboItemListener -> (PresetWindowLevel) comboItemListener.getFirstItem())
              .orElse(null);
      isDefaultPresetSelected = preset.equals(defaultPreset);
    } else {
      presetAction.ifPresent(
          a ->
              a.setSelectedItemWithoutTriggerAction(
                  object instanceof PresetWindowLevel ? object : null));
    }

    Optional<ToggleButtonListener> defaultPresetAction = getAction(ActionW.DEFAULT_PRESET);
    if (defaultPresetAction.isPresent()) {
      defaultPresetAction.get().setSelectedWithoutTriggerAction(isDefaultPresetSelected);
      SynchEvent evt =
          new SynchEvent(
              getSelectedViewPane(),
              ActionW.DEFAULT_PRESET.cmd(),
              defaultPresetAction.get().isSelected());
      evt.put(cmd, object);
      firePropertyChange(ActionW.SYNCH.cmd(), null, evt);
    }

    if (selectedView2dContainer != null) {
      fireSeriesViewerListeners(
          new SeriesViewerEvent(selectedView2dContainer, null, null, EVENT.WIN_LEVEL));
    }
  }

  private ComboItemListener<PresetWindowLevel> newPresetAction() {
    return new ComboItemListener<>(ActionW.PRESET, null) {

      @Override
      public void itemStateChanged(Object object) {
        updatePreset(getActionW().cmd(), object);
      }
    };
  }

  private ComboItemListener<LutShape> newLutShapeAction() {
    return new ComboItemListener<>(
        ActionW.LUT_SHAPE, DicomImageElement.DEFAULT_LUT_FUNCTIONS.toArray(new LutShape[0])) {

      @Override
      public void itemStateChanged(Object object) {
        updatePreset(action.cmd(), object);
      }
    };
  }

  private ToggleButtonListener newDefaultPresetAction() {
    return new ToggleButtonListener(ActionW.DEFAULT_PRESET, true) {
      @Override
      public void actionPerformed(boolean selected) {
        firePropertyChange(
            ActionW.SYNCH.cmd(),
            null,
            new SynchEvent(getSelectedViewPane(), action.cmd(), selected));
      }
    };
  }

  private ToggleButtonListener newKOToggleAction() {
    return new ToggleButtonListener(ActionW.KO_TOGGLE_STATE, false) {
      @Override
      public void actionPerformed(boolean newSelectedState) {

        boolean hasKeyObjectReferenceChanged =
            KOManager.setKeyObjectReference(newSelectedState, getSelectedViewPane());

        if (!hasKeyObjectReferenceChanged) {
          // If KO Toggle State hasn't changed, this action should be reset to its previous state
          this.setSelectedWithoutTriggerAction(
              (Boolean) getSelectedViewPane().getActionValue(ActionW.KO_TOGGLE_STATE.cmd()));
        }
      }
    };
  }

  private ComboItemListener<Object> newKOSelectionAction() {
    return new ComboItemListener<>(
        ActionW.KO_SELECTION, new ActionState.NoneLabel[] {ActionState.NoneLabel.NONE}) {
      @Override
      public void itemStateChanged(Object object) {
        koAction(action, object);
      }
    };
  }

  private ToggleButtonListener newKOFilterAction() {
    return new ToggleButtonListener(ActionW.KO_FILTER, false) {
      @Override
      public void actionPerformed(boolean selected) {
        koAction(action, selected);
      }
    };
  }

  private void koAction(Feature<?> action, Object selected) {
    Optional<ComboItemListener<SynchView>> synchAction = getAction(ActionW.SYNCH);
    SynchView synchView =
        synchAction
            .map(comboItemListener -> (SynchView) comboItemListener.getSelectedItem())
            .orElse(null);
    boolean tileMode =
        synchView != null && SynchData.Mode.TILE.equals(synchView.getSynchData().getMode());
    ViewCanvas<DicomImageElement> selectedView = getSelectedViewPane();
    if (tileMode) {
      if (selectedView2dContainer instanceof View2dContainer container && selectedView != null) {
        boolean filterSelection = selected instanceof Boolean;
        Object selectedKO =
            filterSelection ? selectedView.getActionValue(ActionW.KO_SELECTION.cmd()) : selected;
        Boolean enableFilter =
            (Boolean)
                (filterSelection ? selected : selectedView.getActionValue(ActionW.KO_FILTER.cmd()));
        ViewCanvas<DicomImageElement> viewPane = container.getSelectedViewCanvas();
        int frameIndex =
            LangUtil.nullToFalse(enableFilter)
                ? 0
                : viewPane.getFrameIndex() - viewPane.getTileOffset();

        for (ViewCanvas<DicomImageElement> view : container.getImagePanels(true)) {
          if (!(view.getSeries() instanceof DicomSeries) || !(view instanceof View2d)) {
            continue;
          }

          KOManager.updateKOFilter(view, selectedKO, enableFilter, frameIndex, false);
        }

        container.updateTileOffset();
        if (!(selectedView.getSeries() instanceof DicomSeries)) {
          List<ViewCanvas<DicomImageElement>> panes = selectedView2dContainer.getImagePanels(false);
          if (!panes.isEmpty()) {
            selectedView2dContainer.setSelectedImagePane(panes.get(0));
            return;
          }
        }
      }
    }

    firePropertyChange(
        ActionW.SYNCH.cmd(), null, new SynchEvent(selectedView, action.cmd(), selected));

    if (tileMode) {
      Optional<SliderCineListener> cineAction = getAction(ActionW.SCROLL_SERIES);
      if (cineAction.isPresent() && cineAction.get().isActionEnabled()) {
        SliderCineListener moveTroughSliceAction = cineAction.get();
        if (moveTroughSliceAction.getSliderValue() == 1) {
          moveTroughSliceAction.stateChanged(moveTroughSliceAction.getSliderModel());
        } else {
          moveTroughSliceAction.setSliderValue(1);
        }
      }
    }
  }

  private ComboItemListener<ByteLut> newLutAction() {
    List<ByteLut> lutEntries = new ArrayList<>();
    lutEntries.add(ColorLut.GRAY.getByteLut());
    ByteLutCollection.readLutFilesFromResourcesDir(
        lutEntries, ResourceUtil.getResource(DicomResource.LUTS).toPath());
    // Set default first as the list has been sorted
    lutEntries.addFirst(ColorLut.IMAGE.getByteLut());

    return new ComboItemListener<>(ActionW.LUT, lutEntries.toArray(new ByteLut[0])) {

      @Override
      public void itemStateChanged(Object object) {
        firePropertyChange(
            ActionW.SYNCH.cmd(), null, new SynchEvent(getSelectedViewPane(), action.cmd(), object));
        if (selectedView2dContainer != null) {
          fireSeriesViewerListeners(
              new SeriesViewerEvent(selectedView2dContainer, null, null, EVENT.LUT));
        }
      }
    };
  }

  private ComboItemListener<SeriesComparator<DicomImageElement>> newSortStackAction() {
    return new ComboItemListener<>(ActionW.SORT_STACK, SortSeriesStack.getValues()) {

      @Override
      public void itemStateChanged(Object object) {
        firePropertyChange(
            ActionW.SYNCH.cmd(), null, new SynchEvent(getSelectedViewPane(), action.cmd(), object));
      }
    };
  }

  @Override
  public Optional<Feature<? extends ActionState>> getLeftMouseActionFromKeyEvent(
      int keyEvent, int modifier) {
    Optional<Feature<? extends ActionState>> feature =
        super.getLeftMouseActionFromKeyEvent(keyEvent, modifier);
    if (feature.isEmpty()) {
      return feature;
    }
    // Only return the action if it is enabled
    if (getAction(feature.get()).filter(ActionState::isActionEnabled).isPresent()) {
      return feature;
    } else if (ActionW.KO_TOGGLE_STATE.equals(feature.get())
        && keyEvent == ActionW.KO_TOGGLE_STATE.getKeyCode()) {
      getAction(ActionW.KO_TOGGLE_STATE).ifPresent(b -> b.setSelected(!b.isSelected()));
    }
    return Optional.empty();
  }

  @Override
  public void keyTyped(KeyEvent e) {
    // Do nothing
  }

  @Override
  public void keyPressed(KeyEvent e) {
    if (!commonDisplayShortcuts(e)) {
      int keyEvent = e.getKeyCode();
      int modifiers = e.getModifiers();
      boolean isMpr = selectedView2dContainer instanceof MprContainer;
      ShortcutManager sm = ShortcutManager.getInstance();

      if (handleKeyboardWindowLevelShortcut(sm, keyEvent, modifiers)) {
        // Handled by the selected X-ray/MR view.
      } else if (sm.matches(ShortcutManager.ID_DICOM_PREV_STUDY, keyEvent, modifiers)) {
        moveStudy(ListPosition.PREVIOUS);
      } else if (sm.matches(ShortcutManager.ID_DICOM_NEXT_STUDY, keyEvent, modifiers)) {
        moveStudy(ListPosition.NEXT);
      } else if (sm.matches(ShortcutManager.ID_DICOM_PREV_IMAGE_CONTINUOUS, keyEvent, modifiers)) {
        moveImageContinuously(ListPosition.PREVIOUS);
      } else if (sm.matches(ShortcutManager.ID_DICOM_NEXT_IMAGE_CONTINUOUS, keyEvent, modifiers)) {
        moveImageContinuously(ListPosition.NEXT);
      } else if (sm.matches(ShortcutManager.ID_DICOM_PREV_SERIES, keyEvent, modifiers)) {
        moveSeries(ListPosition.PREVIOUS);
      } else if (sm.matches(ShortcutManager.ID_DICOM_NEXT_SERIES, keyEvent, modifiers)) {
        moveSeries(ListPosition.NEXT);
      } else if (sm.matches(ShortcutManager.ID_DICOM_PREV_PATIENT, keyEvent, modifiers)) {
        movePatient(ListPosition.PREVIOUS);
      } else if (sm.matches(ShortcutManager.ID_DICOM_NEXT_PATIENT, keyEvent, modifiers)) {
        movePatient(ListPosition.NEXT);
      } else if (sm.matches(ShortcutManager.ID_DICOM_FIRST_STUDY, keyEvent, modifiers)) {
        moveStudy(ListPosition.FIRST);
      } else if (sm.matches(ShortcutManager.ID_DICOM_LAST_STUDY, keyEvent, modifiers)) {
        moveStudy(ListPosition.LAST);
      } else if (sm.matches(ShortcutManager.ID_DICOM_FIRST_SERIES, keyEvent, modifiers)) {
        moveSeries(ListPosition.FIRST);
      } else if (sm.matches(ShortcutManager.ID_DICOM_LAST_SERIES, keyEvent, modifiers)) {
        moveSeries(ListPosition.LAST);
      } else if (sm.matches(ShortcutManager.ID_DICOM_FIRST_PATIENT, keyEvent, modifiers)) {
        movePatient(ListPosition.FIRST);
      } else if (sm.matches(ShortcutManager.ID_DICOM_LAST_PATIENT, keyEvent, modifiers)) {
        movePatient(ListPosition.LAST);
      } else if (isMpr
          && (sm.matches(ShortcutManager.ID_MPR_RECENTER, keyEvent, modifiers)
              || sm.matches(ShortcutManager.ID_MPR_RECENTER_ALL, keyEvent, modifiers))) {
        if (selectedView2dContainer.getSelectedViewCanvas() instanceof MprView mprView) {
          mprView.recenterAxis(
              sm.matches(ShortcutManager.ID_MPR_RECENTER_ALL, keyEvent, modifiers));
        }
      } else if (isMpr
          && (sm.matches(ShortcutManager.ID_MPR_TOGGLE_CENTER, keyEvent, modifiers)
              || sm.matches(ShortcutManager.ID_MPR_TOGGLE_CENTER_ALL, keyEvent, modifiers))) {
        if (selectedView2dContainer.getSelectedViewCanvas() instanceof MprView mprView) {
          boolean showCenter = MprView.getViewProperty(mprView, MprView.SHOW_CROSS_CENTER);
          mprView.showCrossCenter(
              !showCenter,
              sm.matches(ShortcutManager.ID_MPR_TOGGLE_CENTER_ALL, keyEvent, modifiers));
        }
      } else if (isMpr
          && (sm.matches(ShortcutManager.ID_MPR_TOGGLE_CROSS_LINES, keyEvent, modifiers)
              || sm.matches(ShortcutManager.ID_MPR_TOGGLE_CROSS_LINES_ALL, keyEvent, modifiers))) {
        if (selectedView2dContainer.getSelectedViewCanvas() instanceof MprView mprView) {
          boolean showCrossLines = MprView.getViewProperty(mprView, MprView.HIDE_CROSSLINES);
          mprView.showCrossLines(
              showCrossLines,
              sm.matches(ShortcutManager.ID_MPR_TOGGLE_CROSS_LINES_ALL, keyEvent, modifiers));
        }
      } else if (isMpr && sm.matches(ShortcutManager.ID_MPR_CYCLE_MIP, keyEvent, modifiers)) {
        if (selectedView2dContainer.getSelectedViewCanvas() instanceof MprView mprView) {
          MprController controller = mprView.getMprController();
          if (controller != null) {
            ComboItemListener<MipView.Type> mipCombo = controller.getMipTypeOption();
            MipView.Type currentType = (MipView.Type) mipCombo.getSelectedItem();
            MipView.Type[] types = MipView.Type.values();
            int nextIndex = (currentType.ordinal() + 1) % types.length;
            mipCombo.setSelectedItemWithoutTriggerAction(types[nextIndex]);
            controller.updateAllViews();
          }
        }
      } else {
        if (!isManagedWindowLevelPresetKey(keyEvent, modifiers)) {
          keyPreset(keyEvent, modifiers);
        }
        triggerDrawingToolKeyEvent(keyEvent, modifiers);
      }
    }
  }

  private boolean handleKeyboardWindowLevelShortcut(
      ShortcutManager shortcutManager, int keyEvent, int modifiers) {
    ViewCanvas<DicomImageElement> view = getSelectedViewPane();
    if (view == null
        || view.getImage() == null
        || !isKeyboardWindowLevelModality(view.getSeries())) {
      return false;
    }

    if (shortcutManager.matches(ShortcutManager.ID_DICOM_WINDOW_DECREASE, keyEvent, modifiers)) {
      adjustKeyboardWindowLevel(view, true, -1);
    } else if (shortcutManager.matches(
        ShortcutManager.ID_DICOM_WINDOW_INCREASE, keyEvent, modifiers)) {
      adjustKeyboardWindowLevel(view, true, 1);
    } else if (shortcutManager.matches(
        ShortcutManager.ID_DICOM_LEVEL_DECREASE, keyEvent, modifiers)) {
      adjustKeyboardWindowLevel(view, false, -1);
    } else if (shortcutManager.matches(
        ShortcutManager.ID_DICOM_LEVEL_INCREASE, keyEvent, modifiers)) {
      adjustKeyboardWindowLevel(view, false, 1);
    } else if (shortcutManager.matches(
        ShortcutManager.ID_DICOM_WINDOW_LEVEL_RESET, keyEvent, modifiers)) {
      getResetWindowLevelPreset(view).ifPresent(preset -> applyKeyboardPreset(view, preset));
    } else if (shortcutManager.matches(
        ShortcutManager.ID_DICOM_WINDOW_LEVEL_PREVIOUS, keyEvent, modifiers)) {
      restorePreviousKeyboardWindowLevel(view);
    } else if (shortcutManager.matches(
        ShortcutManager.ID_DICOM_WINDOW_LEVEL_AUTO, keyEvent, modifiers)) {
      getWindowLevelPreset(KeyEvent.VK_0).ifPresent(preset -> applyKeyboardPreset(view, preset));
    } else {
      return false;
    }
    return true;
  }

  private void adjustKeyboardWindowLevel(
      ViewCanvas<DicomImageElement> view, boolean adjustWindow, int direction) {
    Optional<KeyboardWindowLevelState> currentState = captureWindowLevelState(view);
    Optional<SliderChangeListener> action =
        getAction(adjustWindow ? ActionW.WINDOW : ActionW.LEVEL);
    if (currentState.isEmpty() || action.isEmpty() || !action.get().isActionEnabled()) {
      return;
    }

    KeyboardWindowLevelState current = currentState.get();
    int step =
        adjustWindow
            ? windowLevelSetting.getKeyboardWindowStep()
            : windowLevelSetting.getKeyboardLevelStep();
    double currentValue = adjustWindow ? current.window() : current.level();
    double target = clampToSliderRange(action.get(), currentValue + (double) direction * step);
    if (Double.compare(currentValue, target) == 0) {
      return;
    }

    KeyboardWindowLevelState updated =
        new KeyboardWindowLevelState(
            current.series(),
            adjustWindow ? target : current.window(),
            adjustWindow ? current.level() : target,
            current.lutShape(),
            null,
            false);
    if (applyKeyboardWindowLevelState(view, updated)) {
      previousWindowLevels.put(view, current);
    }
  }

  private void applyKeyboardPreset(ViewCanvas<DicomImageElement> view, PresetWindowLevel preset) {
    captureWindowLevelState(view)
        .ifPresent(
            current -> {
              getAction(ActionW.PRESET)
                  .filter(ActionState::isActionEnabled)
                  .ifPresent(
                      action -> {
                        action.setSelectedItemAndTriggerAction(preset);
                        previousWindowLevels.put(view, current);
                      });
            });
  }

  private void restorePreviousKeyboardWindowLevel(ViewCanvas<DicomImageElement> view) {
    KeyboardWindowLevelState previous = previousWindowLevels.get(view);
    if (previous == null || previous.series() != view.getSeries()) {
      previousWindowLevels.remove(view);
      return;
    }

    captureWindowLevelState(view)
        .ifPresent(
            current -> {
              if (applyKeyboardWindowLevelState(view, previous)) {
                previousWindowLevels.put(view, current);
              }
            });
  }

  private Optional<KeyboardWindowLevelState> captureWindowLevelState(
      ViewCanvas<DicomImageElement> view) {
    Optional<ImageOpNode> node = view.getDisplayOpManager().getNode(WindowOp.OP_NAME);
    if (view.getSeries() == null || node.isEmpty()) {
      return Optional.empty();
    }

    ImageOpNode windowNode = node.get();
    Optional<Number> window =
        view.getDisplayOpManager()
            .getParamValue(WindowOp.OP_NAME, ActionW.WINDOW.cmd(), Number.class);
    Optional<Number> level =
        view.getDisplayOpManager()
            .getParamValue(WindowOp.OP_NAME, ActionW.LEVEL.cmd(), Number.class);
    if (window.isEmpty() || level.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(
        new KeyboardWindowLevelState(
            view.getSeries(),
            window.get().doubleValue(),
            level.get().doubleValue(),
            (LutShape) windowNode.getParam(ActionW.LUT_SHAPE.cmd()),
            (PresetWindowLevel) windowNode.getParam(ActionW.PRESET.cmd()),
            LangUtil.nullToTrue((Boolean) windowNode.getParam(ActionW.DEFAULT_PRESET.cmd()))));
  }

  private boolean applyKeyboardWindowLevelState(
      ViewCanvas<DicomImageElement> view, KeyboardWindowLevelState state) {
    if (state.series() != view.getSeries()) {
      return false;
    }

    Optional<ImageOpNode> node = view.getDisplayOpManager().getNode(WindowOp.OP_NAME);
    if (node.isEmpty()) {
      return false;
    }

    ImageOpNode windowNode = node.get();
    windowNode.setParam(ActionW.PRESET.cmd(), state.preset());
    windowNode.setParam(ActionW.DEFAULT_PRESET.cmd(), state.defaultPreset());
    windowNode.setParam(ActionW.WINDOW.cmd(), state.window());
    windowNode.setParam(ActionW.LEVEL.cmd(), state.level());
    if (state.lutShape() != null) {
      windowNode.setParam(ActionW.LUT_SHAPE.cmd(), state.lutShape());
    }
    view.getActionsInView().put(ActionW.PRESET.cmd(), state.preset());
    view.getImageLayer().updateDisplayOperations();
    updateWindowLevelComponentsListener(view.getImage(), view);
    fireSeriesViewerListeners(
        new SeriesViewerEvent(selectedView2dContainer, null, null, EVENT.WIN_LEVEL));
    return true;
  }

  private Optional<PresetWindowLevel> getFirstWindowLevelPreset() {
    return getAction(ActionW.PRESET)
        .map(ComboItemListener::getFirstItem)
        .filter(PresetWindowLevel.class::isInstance)
        .map(PresetWindowLevel.class::cast);
  }

  private Optional<PresetWindowLevel> getResetWindowLevelPreset(
      ViewCanvas<DicomImageElement> view) {
    Optional<PresetWindowLevel> defaultPreset = getFirstWindowLevelPreset();
    if (defaultPreset.isEmpty() || !WindowLevelMemory.isMrSeries(view.getSeries())) {
      return defaultPreset;
    }

    DicomImageElement image = view.getImage();
    if (image == null) {
      return defaultPreset;
    }
    boolean pixelPadding =
        view.getDisplayOpManager()
            .getParamValue(WindowOp.OP_NAME, ActionW.IMAGE_PIX_PADDING.cmd(), Boolean.class)
            .orElse(Boolean.TRUE);
    DefaultWlPresentation wlp = new DefaultWlPresentation(null, pixelPadding);
    if (WindowAndPresetsOp.isImplausiblePreset(
        defaultPreset.get(), image.getMinValue(wlp), image.getMaxValue(wlp))) {
      return getWindowLevelPreset(KeyEvent.VK_0).or(() -> defaultPreset);
    }
    return defaultPreset;
  }

  private Optional<PresetWindowLevel> getWindowLevelPreset(int dicomKeyCode) {
    Optional<? extends ComboItemListener<?>> presetAction = getAction(ActionW.PRESET);
    if (presetAction.isEmpty() || !presetAction.get().isActionEnabled()) {
      return Optional.empty();
    }

    DefaultComboBoxModel<?> model = presetAction.get().getModel();
    for (int i = 0; i < model.getSize(); i++) {
      Object item = model.getElementAt(i);
      if (item instanceof PresetWindowLevel preset && preset.getKeyCode() == dicomKeyCode) {
        return Optional.of(preset);
      }
    }
    return Optional.empty();
  }

  private boolean isManagedWindowLevelPresetKey(int keyEvent, int modifiers) {
    ViewCanvas<DicomImageElement> view = getSelectedViewPane();
    return modifiers == 0
        && view != null
        && isKeyboardWindowLevelModality(view.getSeries())
        && (keyEvent == KeyEvent.VK_0 || (keyEvent >= KeyEvent.VK_4 && keyEvent <= KeyEvent.VK_9));
  }

  private static boolean isKeyboardWindowLevelModality(MediaSeries<?> series) {
    return series != null
        && isKeyboardWindowLevelModality(TagD.getTagValue(series, Tag.Modality, String.class));
  }

  static boolean isKeyboardWindowLevelModality(String modality) {
    return modality != null
        && ("MR".equalsIgnoreCase(modality.strip()) || isXrayModality(modality));
  }

  static boolean isXrayModality(String modality) {
    return modality != null && XRAY_MODALITIES.contains(modality.strip().toUpperCase(Locale.ROOT));
  }

  static KernelData getDefaultImageFilter(String modality) {
    return isXrayModality(modality) ? KernelData.SHARPEN_MORE : KernelData.NONE;
  }

  private static double clampToSliderRange(SliderChangeListener action, double value) {
    double min = action.toModelValue(action.getSliderMin());
    double max = action.toModelValue(action.getSliderMax());
    return Math.max(Math.min(min, max), Math.min(Math.max(min, max), value));
  }

  private record KeyboardWindowLevelState(
      MediaSeries<DicomImageElement> series,
      double window,
      double level,
      LutShape lutShape,
      PresetWindowLevel preset,
      boolean defaultPreset) {}

  private DicomExplorer getDicomExplorer() {
    DataExplorerView dicomView = GuiUtils.getUICore().getExplorerPlugin(DicomExplorer.NAME);
    if (dicomView instanceof DicomExplorer dicom) {
      return dicom;
    }
    return null;
  }

  private void moveEntity(
      BiFunction<DicomExplorer, ViewCanvas<DicomImageElement>, MediaSeries<? extends MediaElement>>
          moveFunction) {
    ImageViewerPlugin<DicomImageElement> container = getSelectedView2dContainer();
    if (container == null) {
      return;
    }
    ViewCanvas<DicomImageElement> view = getSelectedViewPane();
    if (view != null) {
      DicomExplorer dicom = getDicomExplorer();
      if (dicom != null) {
        MediaSeries<? extends MediaElement> series = moveFunction.apply(dicom, view);
        if (series != null) {
          fireSeriesViewerListeners(
              new SeriesViewerEvent(container, series, null, EVENT.SELECT_VIEW));
        }
      }
    }
  }

  private void movePatient(ListPosition position) {
    moveEntity((dicom, view) -> dicom.movePatient(view, position));
  }

  private void moveStudy(ListPosition position) {
    moveEntity((dicom, view) -> dicom.moveStudy(view, position));
  }

  private void moveSeries(ListPosition position) {
    moveEntity((dicom, view) -> dicom.moveSeries(view, position));
  }

  private void moveImageContinuously(ListPosition position) {
    if (selectedView2dContainer instanceof MprContainer) {
      moveWithinCurrentSeries(position);
      return;
    }
    if (moveWithinCurrentSeries(position)) {
      return;
    }
    moveEntity((dicom, view) -> dicom.moveAcrossVisibleImages(view, position));
  }

  private boolean moveWithinCurrentSeries(ListPosition position) {
    Optional<SliderCineListener> cineAction = getAction(ActionW.SCROLL_SERIES);
    if (cineAction.isEmpty() || !cineAction.get().isActionEnabled()) {
      return false;
    }

    SliderCineListener slider = cineAction.get();
    int delta = position == ListPosition.PREVIOUS ? -1 : 1;
    int target = slider.getSliderValue() + delta;
    if (target >= slider.getSliderMin() && target <= slider.getSliderMax()) {
      slider.setSliderValue(target);
      return true;
    }
    return false;
  }

  @Override
  public void keyReleased(KeyEvent e) {
    // Do nothing
  }

  private void keyPreset(int keyEvent, int modifiers) {
    Optional<? extends ComboItemListener<?>> presetAction = getAction(ActionW.PRESET);
    if (modifiers == 0 && presetAction.isPresent() && presetAction.get().isActionEnabled()) {
      ComboItemListener<?> presetComboListener = presetAction.get();
      DefaultComboBoxModel<?> model = presetComboListener.getModel();
      for (int i = 0; i < model.getSize(); i++) {
        PresetWindowLevel val = (PresetWindowLevel) model.getElementAt(i);
        if (val.getKeyCode() == keyEvent) {
          presetComboListener.setSelectedItem(val);
          return;
        }
      }
    }
  }

  @Override
  public void setSelectedView2dContainer(
      ImageViewerPlugin<DicomImageElement> selectedView2dContainer) {
    if (this.selectedView2dContainer != null) {
      this.selectedView2dContainer.setMouseActions(null);
    }

    ImageViewerPlugin<DicomImageElement> oldContainer = this.selectedView2dContainer;
    this.selectedView2dContainer = selectedView2dContainer;

    if (selectedView2dContainer != null) {
      Optional<ComboItemListener<SynchView>> synchAction = getAction(ActionW.SYNCH);
      Optional<ComboItemListener<MigLayoutModel>> layoutAction = getAction(ActionW.LAYOUT);
      if (oldContainer == null
          || !oldContainer.getClass().equals(selectedView2dContainer.getClass())) {
        synchAction.ifPresent(
            a ->
                a.setDataListWithoutTriggerAction(
                    selectedView2dContainer.getSynchList().toArray(new SynchView[0])));
        layoutAction.ifPresent(
            a ->
                a.setDataListWithoutTriggerAction(
                    selectedView2dContainer.getLayoutList().toArray(new MigLayoutModel[0])));
      }
      if (oldContainer != null) {
        ViewCanvas<DicomImageElement> pane = oldContainer.getSelectedViewCanvas();
        if (pane != null) {
          pane.setFocused(false);
        }
      }
      synchAction.ifPresent(
          a -> a.setSelectedItemWithoutTriggerAction(selectedView2dContainer.getSynchView()));
      layoutAction.ifPresent(
          a ->
              a.setSelectedItemWithoutTriggerAction(
                  selectedView2dContainer.getOriginalLayoutModel()));
      updateComponentsListener(selectedView2dContainer.getSelectedViewCanvas());
      selectedView2dContainer.setMouseActions(mouseActions);
      ViewCanvas<DicomImageElement> pane = selectedView2dContainer.getSelectedViewCanvas();
      if (pane != null) {
        pane.setFocused(true);
      }
    }
  }

  /** process the action events. */
  @Override
  public void actionPerformed(ActionEvent evt) {
    cinePlay(evt.getActionCommand());
  }

  private void cinePlay(String command) {
    if (command != null) {
      if (command.equals(ActionW.CINESTART.cmd())) {
        getAction(ActionW.SCROLL_SERIES).ifPresent(SliderCineListener::start);
      } else if (command.equals(ActionW.CINESTOP.cmd())) {
        getAction(ActionW.SCROLL_SERIES).ifPresent(SliderCineListener::stop);
      }
    }
  }

  @Override
  public String resolvePlaceholders(String template) {
    return DicomExportAction.resolvePlaceholders(template, this);
  }

  @Override
  public void dicomExportAction(Launcher launcher) {
    if (launcher != null && launcher.getConfiguration().isDicomSelectionAction()) {
      DicomExplorer dicom = getDicomExplorer();
      if (dicom != null) {
        DicomModel dicomModel = (DicomModel) dicom.getDataExplorerModel();
        DicomExportAction action = new DicomExportAction(launcher, dicomModel);
        try {
          action.execute();
        } catch (IOException e) {
          LOGGER.error("Copy DICOM failed", e);
        }
      }
    }
  }

  @Override
  public void resetDisplay() {
    reset(ResetTools.ALL);
  }

  public void reset(ResetTools action) {
    AuditLog.LOGGER.info("reset action:{}", action.name());
    if (ResetTools.ALL.equals(action)) {
      firePropertyChange(
          ActionW.SYNCH.cmd(),
          null,
          new SynchEvent(getSelectedViewPane(), ActionW.RESET.cmd(), true));
    } else if (ResetTools.ZOOM.equals(action)) {
      // Pass the value 0.0 (convention: default value according the zoom type) directly to the
      // property change,
      // otherwise the value is adjusted by the BoundedRangeModel
      firePropertyChange(
          ActionW.SYNCH.cmd(),
          null,
          new SynchEvent(getSelectedViewPane(), ActionW.ZOOM.cmd(), 0.0));
    } else if (ResetTools.WL.equals(action)) {
      getAction(ActionW.PRESET).ifPresent(a -> a.setSelectedItem(a.getFirstItem()));
    } else if (ResetTools.PAN.equals(action)) {
      if (selectedView2dContainer != null) {
        ViewCanvas viewPane = selectedView2dContainer.getSelectedViewCanvas();
        if (viewPane != null) {
          viewPane.resetPan();
        }
      }
    }
  }

  @Override
  public synchronized boolean updateComponentsListener(ViewCanvas<DicomImageElement> view2d) {
    if (view2d == null) {
      return false;
    }

    if (selectedView2dContainer == null
        || view2d != selectedView2dContainer.getSelectedViewCanvas()) {
      return false;
    }

    clearAllPropertyChangeListeners();
    Optional<SliderCineListener> cineAction = getAction(ActionW.SCROLL_SERIES);

    if (view2d.getSourceImage() == null) {
      enableActions(false);
      if (view2d.getSeries() != null) {
        // Let scrolling if only one image is corrupted in the series
        cineAction.ifPresent(a -> a.enableAction(true));
      }
      View2dContainer.UI.updateDynamicTools(view2d.getSeries());
      return false;
    }

    if (!enabledAction) {
      enableActions(true);
    }

    View2dContainer.UI.updateDynamicTools(view2d.getSeries());

    OpManager dispOp = view2d.getDisplayOpManager();

    updateWindowLevelComponentsListener(view2d.getImage(), view2d);

    getAction(ActionW.LUT)
        .ifPresent(
            a ->
                a.setSelectedItemWithoutTriggerAction(
                    dispOp
                        .getParamValue(PseudoColorOp.OP_NAME, PseudoColorOp.P_LUT)
                        .orElse(ColorLut.IMAGE.getByteLut())));
    getAction(ActionW.INVERT_LUT)
        .ifPresent(
            a ->
                a.setSelectedWithoutTriggerAction(
                    dispOp
                        .getParamValue(
                            PseudoColorOp.OP_NAME, PseudoColorOp.P_LUT_INVERSE, Boolean.class)
                        .orElse(Boolean.FALSE)));
    getAction(ActionW.FILTER)
        .ifPresent(
            a ->
                a.setSelectedItemWithoutTriggerAction(
                    dispOp
                        .getParamValue(FilterOp.OP_NAME, FilterOp.P_KERNEL_DATA)
                        .orElse(KernelData.NONE)));
    getAction(ActionW.ROTATION)
        .ifPresent(
            a -> a.setSliderValue((Integer) view2d.getActionValue(ActionW.ROTATION.cmd()), false));
    getAction(ActionW.FLIP)
        .ifPresent(
            a ->
                a.setSelectedWithoutTriggerAction(
                    LangUtil.nullToFalse((Boolean) view2d.getActionValue(ActionW.FLIP.cmd()))));

    getAction(ActionW.ZOOM)
        .ifPresent(
            a ->
                a.setRealValue(
                    Math.abs((Double) view2d.getActionValue(ActionW.ZOOM.cmd())), false));
    getAction(ActionW.SPATIAL_UNIT)
        .ifPresent(
            a ->
                a.setSelectedItemWithoutTriggerAction(
                    view2d.getActionValue(ActionW.SPATIAL_UNIT.cmd())));

    getAction(ActionW.LENS)
        .ifPresent(
            a ->
                a.setSelectedWithoutTriggerAction(
                    (Boolean) view2d.getActionValue(ActionW.LENS.cmd())));
    Double lensZoom = (Double) view2d.getLensActionValue(ActionW.ZOOM.cmd());
    if (lensZoom != null) {
      getAction(ActionW.LENS_ZOOM).ifPresent(a -> a.setRealValue(Math.abs(lensZoom), false));
    }

    boolean isMprOrOblique =
        selectedView2dContainer instanceof MprContainer c
            && c.getMprController().getVolume() != null;
    MediaSeries<DicomImageElement> series = view2d.getSeries();
    int maxSlice;
    int currentSlice;
    if (isMprOrOblique && view2d instanceof MprView mprView) {
      MprContainer mprContainer = (MprContainer) selectedView2dContainer;
      MprController controller = mprContainer.getMprController();
      Volume<?, ?> volume = controller.getVolume();
      maxSlice = volume.getSliceSize();
      MprAxis axis = controller.getMprAxis(mprView.getPlane());
      currentSlice = axis.getSliceIndex();
    } else {
      maxSlice =
          series.size(
              (Filter<DicomImageElement>) view2d.getActionValue(ActionW.FILTERED_SERIES.cmd()));
      currentSlice = view2d.getFrameIndex() + 1;
    }
    cineAction.ifPresent(a -> a.setSliderMinMaxValue(1, maxSlice, currentSlice, false));

    Double cineRate = TagD.getTagValue(view2d.getImage(), Tag.CineRate, Double.class);
    cineAction.ifPresent(
        a -> {
          a.setSpeed(cineRate == null ? 20.0 : cineRate);
        });
    int playbackSequencing = getPlaybackSequencing(view2d);
    getAction(ActionW.CINE_SWEEP).ifPresent(a -> a.setSelected(playbackSequencing == 1));

    getAction(ActionW.SORT_STACK)
        .ifPresent(
            a ->
                a.setSelectedItemWithoutTriggerAction(
                    view2d.getActionValue(ActionW.SORT_STACK.cmd())));
    getAction(ActionW.INVERSE_STACK)
        .ifPresent(
            a ->
                a.setSelectedWithoutTriggerAction(
                    (Boolean) view2d.getActionValue(ActionW.INVERSE_STACK.cmd())));
    getAction(ActionW.VOLUME)
        .ifPresent(a -> a.enableAction(isMprOrOblique || series.isSuitableFor3d()));

    getAction(ActionW.CROSSHAIR).ifPresent(a -> a.enableAction(!isMprOrOblique));
    updateKeyObjectComponentsListener(view2d);

    // register all actions for the selected view and for the other views register according to
    // synchview.
    ComboItemListener<SynchView> synchAction = getAction(ActionW.SYNCH).orElse(null);
    updateAllListeners(
        selectedView2dContainer,
        synchAction == null ? SynchView.DEFAULT_STACK : (SynchView) synchAction.getSelectedItem());

    view2d.updateGraphicSelectionListener(selectedView2dContainer);
    return true;
  }

  private static int getPlaybackSequencing(ViewCanvas<DicomImageElement> view2d) {
    Integer playbackSequencing =
        TagD.getTagValue(view2d.getImage(), Tag.PreferredPlaybackSequencing, Integer.class);
    return playbackSequencing == null ? 0 : playbackSequencing;
  }

  public void updateKeyObjectComponentsListener(ViewCanvas<DicomImageElement> view2d) {
    if (view2d != null) {
      Optional<? extends ComboItemListener<Object>> koSelectionAction =
          getAction(ActionW.KO_SELECTION);
      Optional<ToggleButtonListener> koToggleAction = getAction(ActionW.KO_TOGGLE_STATE);
      Optional<ToggleButtonListener> koFilterAction = getAction(ActionW.KO_FILTER);
      if (LangUtil.nullToFalse((Boolean) view2d.getActionValue("no.ko"))) {
        koToggleAction.ifPresent(a -> a.enableAction(false));
        koFilterAction.ifPresent(a -> a.enableAction(false));
        koSelectionAction.ifPresent(a -> a.enableAction(false));
      } else {
        koToggleAction.ifPresent(
            a ->
                a.setSelectedWithoutTriggerAction(
                    (Boolean) view2d.getActionValue(ActionW.KO_TOGGLE_STATE.cmd())));
        koFilterAction.ifPresent(
            a ->
                a.setSelectedWithoutTriggerAction(
                    (Boolean) view2d.getActionValue(ActionW.KO_FILTER.cmd())));

        Object[] kos = KOManager.getKOElementListWithNone(view2d).toArray();
        boolean enable = kos.length > 1;
        if (enable) {
          koSelectionAction.ifPresent(a -> a.setDataListWithoutTriggerAction(kos));
          koSelectionAction.ifPresent(
              a ->
                  a.setSelectedItemWithoutTriggerAction(
                      view2d.getActionValue(ActionW.KO_SELECTION.cmd())));
        }
        koFilterAction.ifPresent(a -> a.enableAction(enable));
        koSelectionAction.ifPresent(a -> a.enableAction(enable));
      }
    }
  }

  private void updateWindowLevelComponentsListener(
      DicomImageElement image, ViewCanvas<DicomImageElement> view2d) {

    Optional<ImageOpNode> node = view2d.getDisplayOpManager().getNode(WindowOp.OP_NAME);
    if (node.isPresent()) {
      ImageOpNode n = node.get();
      int imageDataType = ImageConversion.convertToDataType(image.getImage().type());
      PresetWindowLevel preset = (PresetWindowLevel) n.getParam(ActionW.PRESET.cmd());
      boolean defaultPreset =
          LangUtil.nullToTrue((Boolean) n.getParam(ActionW.DEFAULT_PRESET.cmd()));
      Double windowValue = (Double) n.getParam(ActionW.WINDOW.cmd());
      Double levelValue = (Double) n.getParam(ActionW.LEVEL.cmd());
      LutShape lutShapeItem = (LutShape) n.getParam(ActionW.LUT_SHAPE.cmd());
      boolean pixelPadding =
          LangUtil.nullToTrue((Boolean) n.getParam(ActionW.IMAGE_PIX_PADDING.cmd()));
      PrDicomObject prDicomObject =
          PRManager.getPrDicomObject(view2d.getActionValue(ActionW.PR_STATE.cmd()));
      DefaultWlPresentation wlp = new DefaultWlPresentation(prDicomObject, pixelPadding);

      getAction(ActionW.DEFAULT_PRESET)
          .ifPresent(a -> a.setSelectedWithoutTriggerAction(defaultPreset));

      Optional<SliderChangeListener> windowAction = getAction(ActionW.WINDOW);
      Optional<SliderChangeListener> levelAction = getAction(ActionW.LEVEL);
      if (windowAction.isPresent() && levelAction.isPresent()) {
        double window;
        double minLevel;
        double maxLevel;
        if (windowValue == null) {
          windowValue = windowAction.get().getRealValue();
        }
        if (levelValue == null) {
          levelValue = levelAction.get().getRealValue();
        }
        Double levelMin = (Double) n.getParam(ActionW.LEVEL_MIN.cmd());
        Double levelMax = (Double) n.getParam(ActionW.LEVEL_MAX.cmd());
        double levelLow = Math.min(levelValue - windowValue / 2.0, image.getMinValue(wlp));
        double levelHigh = Math.max(levelValue + windowValue / 2.0, image.getMaxValue(wlp));
        if (levelMin == null || levelMax == null) {
          minLevel = levelLow;
          maxLevel = levelHigh;
        } else {
          minLevel = Math.min(levelMin, levelLow);
          maxLevel = Math.max(levelMax, levelHigh);
        }
        window = Math.max(windowValue, maxLevel - minLevel);

        windowAction
            .get()
            .setRealMinMaxValue(
                imageDataType >= DataBuffer.TYPE_FLOAT ? 0.00001 : 1.0, window, windowValue, false);
        levelAction.get().setRealMinMaxValue(minLevel, maxLevel, levelValue, false);
      }

      List<PresetWindowLevel> presetList = image.getPresetList(wlp);
      if (prDicomObject != null) {
        List<PresetWindowLevel> prPresets =
            (List<PresetWindowLevel>) view2d.getActionValue(PRManager.PR_PRESETS);
        if (prPresets != null && !prPresets.isEmpty()) {
          presetList = prPresets;
        }
      }

      Optional<ComboItemListener<Object>> presetAction = getAction(ActionW.PRESET);
      if (presetAction.isPresent()) {
        presetAction
            .get()
            .setDataListWithoutTriggerAction(presetList == null ? null : presetList.toArray());
        presetAction.get().setSelectedItemWithoutTriggerAction(preset);
      }

      Optional<? extends ComboItemListener<Object>> lutShapeAction = getAction(ActionW.LUT_SHAPE);
      if (lutShapeAction.isPresent()) {
        Collection<LutShape> lutShapeList =
            imageDataType >= DataBuffer.TYPE_INT
                ? Collections.singletonList(LutShape.LINEAR)
                : image.getLutShapeCollection(wlp);
        if (prDicomObject != null
            && lutShapeList != null
            && lutShapeItem != null
            && !lutShapeList.contains(lutShapeItem)) {
          // Make a copy of the image list
          ArrayList<LutShape> newList = new ArrayList<>(lutShapeList.size() + 1);
          newList.add(lutShapeItem);
          newList.addAll(lutShapeList);
          lutShapeList = newList;
        }
        lutShapeAction
            .get()
            .setDataListWithoutTriggerAction(lutShapeList == null ? null : lutShapeList.toArray());
        lutShapeAction.get().setSelectedItemWithoutTriggerAction(lutShapeItem);
      }
    }
  }

  @Override
  protected boolean isCompatible(
      MediaSeries<DicomImageElement> series1, MediaSeries<DicomImageElement> series2) {
    // Have the two series the same image plane orientation
    return ImageOrientation.hasSameOrientation(series1, series2);
  }

  public void savePreferences(BundleContext bundleContext) {
    Preferences prefs = BundlePreferences.getDefaultPreferences(bundleContext);
    zoomSetting.savePreferences(prefs);
    windowLevelSetting.savePreferences(prefs);
    // Mouse buttons preferences
    mouseActions.savePreferences(prefs);
    if (prefs != null) {
      // Mouse sensitivity
      Preferences prefNode = prefs.node("mouse.sensivity");
      setSliderPreference(prefNode, ActionW.WINDOW);
      setSliderPreference(prefNode, ActionW.LEVEL);
      setSliderPreference(prefNode, ActionW.SCROLL_SERIES);
      setSliderPreference(prefNode, ActionW.ROTATION);
      setSliderPreference(prefNode, ActionW.ZOOM);

      prefNode = prefs.node("other"); // NON-NLS
      BundlePreferences.putBooleanPreferences(
          prefNode,
          WindowOp.P_APPLY_WL_COLOR,
          options.getBooleanProperty(WindowOp.P_APPLY_WL_COLOR, true));
      BundlePreferences.putBooleanPreferences(
          prefNode,
          WindowOp.P_INVERSE_LEVEL,
          options.getBooleanProperty(WindowOp.P_INVERSE_LEVEL, true));
      BundlePreferences.putBooleanPreferences(
          prefNode, PRManager.PR_APPLY, options.getBooleanProperty(PRManager.PR_APPLY, false));

      BundlePreferences.putIntPreferences(
          prefNode, View2d.P_CROSSHAIR_MODE, options.getIntProperty(View2d.P_CROSSHAIR_MODE, 1));
      BundlePreferences.putIntPreferences(
          prefNode,
          View2d.P_CROSSHAIR_CENTER_GAP,
          options.getIntProperty(View2d.P_CROSSHAIR_CENTER_GAP, 40));

      Preferences containerNode =
          prefs.node(View2dContainer.UI.clazz.getSimpleName().toLowerCase());
      InsertableUtil.savePreferences(View2dContainer.UI.toolBars, containerNode, Type.TOOLBAR);
      InsertableUtil.savePreferences(View2dContainer.UI.tools, containerNode, Type.TOOL);

      InsertableUtil.savePreferences(
          MprContainer.UI.toolBars,
          prefs.node(MprContainer.class.getSimpleName().toLowerCase()),
          Type.TOOLBAR);
    }
  }

  private void setSliderPreference(
      Preferences prefNode, Feature<? extends SliderChangeListener> action) {
    getAction(action)
        .ifPresent(
            s ->
                BundlePreferences.putDoublePreferences(
                    prefNode, action.cmd(), s.getMouseSensitivity()));
  }

  private void getSliderPreference(
      Preferences prefNode, Feature<? extends SliderChangeListener> action, double defVal) {
    getAction(action)
        .ifPresent(
            s -> s.setMouseSensitivity(getMouseSensitivityPreference(prefNode, action, defVal)));
  }

  private static double getMouseSensitivityPreference(
      Preferences prefNode, Feature<? extends SliderChangeListener> action, double defVal) {
    double sensitivity = prefNode.getDouble(action.cmd(), defVal);
    if (ActionW.ZOOM.cmd().equals(action.cmd())
        && !prefNode.getBoolean(ZOOM_SENSITIVITY_MIGRATED_KEY, false)) {
      if (isPreviousZoomMouseSensitivity(sensitivity)) {
        sensitivity = DEFAULT_ZOOM_MOUSE_SENSITIVITY;
        BundlePreferences.putDoublePreferences(prefNode, action.cmd(), sensitivity);
      }
      prefNode.putBoolean(ZOOM_SENSITIVITY_MIGRATED_KEY, true);
    }
    return sensitivity;
  }

  static boolean isPreviousZoomMouseSensitivity(double sensitivity) {
    return isSensitivityCloseTo(sensitivity, LEGACY_ZOOM_MOUSE_SENSITIVITY)
        || isSensitivityCloseTo(sensitivity, PREVIOUS_ZOOM_MOUSE_SENSITIVITY)
        || isSensitivityCloseTo(sensitivity, PREVIOUS_FAST_ZOOM_MOUSE_SENSITIVITY)
        || isSensitivityCloseTo(sensitivity, CURRENT_ZOOM_MOUSE_SENSITIVITY);
  }

  public WindowLevelSetting getWindowLevelSetting() {
    return windowLevelSetting;
  }

  private static boolean isSensitivityCloseTo(double sensitivity, double expected) {
    return Math.abs(sensitivity - expected) <= expected * ZOOM_SENSITIVITY_MIGRATION_TOLERANCE;
  }

  public MediaSeries<DicomImageElement> getSelectedSeries() {
    ViewCanvas<DicomImageElement> pane = getSelectedViewPane();
    if (pane != null) {
      return pane.getSeries();
    }
    return null;
  }

  public MediaSeries<DicomImageElement> getSelectedOriginalSeries() {
    ViewCanvas<DicomImageElement> pane = getSelectedViewPane();
    if (pane instanceof MprView mprView) {
      return mprView.getMprController().getVolume().getStack().getSeries();
    }
    if (pane == null) {
      return null;
    }
    return pane.getSeries();
  }

  public JMenu getResetMenu(String prop) {
    JMenu menu = null;
    if (GuiUtils.getUICore().getSystemPreferences().getBooleanProperty(prop, true)) {
      ButtonGroup group = new ButtonGroup();
      menu = new JMenu(ActionW.RESET.getTitle());
      menu.setIcon(ResourceUtil.getIcon(ActionIcon.RESET));
      GuiUtils.applySelectedIconEffect(menu);
      menu.setEnabled(getSelectedSeries() != null);

      if (menu.isEnabled()) {
        for (final ResetTools action : ResetTools.values()) {
          final JMenuItem item = new JMenuItem(action.toString());
          if (ResetTools.ALL.equals(action)) {
            item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
          }
          item.addActionListener(e -> reset(action));
          menu.add(item);
          group.add(item);
        }
      }
    }
    return menu;
  }

  public JMenu getPresetMenu(String prop) {
    JMenu menu = null;
    if (GuiUtils.getUICore().getSystemPreferences().getBooleanProperty(prop, true)) {
      Optional<? extends ComboItemListener<?>> presetAction = getAction(ActionW.PRESET);
      if (presetAction.isPresent()) {
        menu =
            presetAction
                .get()
                .createUnregisteredRadioMenu(ActionW.PRESET.getTitle(), ActionW.WINLEVEL.getIcon());
        GuiUtils.applySelectedIconEffect(menu);
        for (Component mitem : menu.getMenuComponents()) {
          RadioMenuItem ritem = (RadioMenuItem) mitem;
          PresetWindowLevel preset = (PresetWindowLevel) ritem.getUserObject();
          if (preset.getKeyCode() > 0) {
            ritem.setAccelerator(KeyStroke.getKeyStroke(preset.getKeyCode(), 0));
          }
        }
      }
    }
    return menu;
  }

  public JMenu getLutShapeMenu(String prop) {
    JMenu menu = null;
    if (GuiUtils.getUICore().getSystemPreferences().getBooleanProperty(prop, true)) {
      Optional<? extends ComboItemListener<?>> lutShapeAction = getAction(ActionW.LUT_SHAPE);
      if (lutShapeAction.isPresent()) {
        menu = lutShapeAction.get().createUnregisteredRadioMenu(ActionW.LUT_SHAPE.getTitle());
      }
    }
    return menu;
  }

  public JMenu getZoomMenu(String prop) {
    JMenu menu = null;
    if (GuiUtils.getUICore().getSystemPreferences().getBooleanProperty(prop, true)) {
      Optional<SliderChangeListener> zoomAction = getAction(ActionW.ZOOM);
      if (zoomAction.isPresent()) {
        menu = new JMenu(ActionW.ZOOM.getTitle());
        menu.setIcon(ActionW.ZOOM.getIcon());
        GuiUtils.applySelectedIconEffect(menu);
        menu.setEnabled(zoomAction.get().isActionEnabled());

        if (zoomAction.get().isActionEnabled()) {
          for (JMenuItem jMenuItem : ZoomToolBar.getZoomListMenuItems(this)) {
            menu.add(jMenuItem);
          }
        }
      }
    }
    return menu;
  }

  public JMenu getOrientationMenu(String prop) {
    JMenu menu = null;
    if (GuiUtils.getUICore().getSystemPreferences().getBooleanProperty(prop, true)) {
      Optional<SliderChangeListener> rotateAction = getAction(ActionW.ROTATION);
      if (rotateAction.isPresent()) {
        menu = new JMenu(Messages.getString("View2dContainer.orientation"));
        menu.setIcon(ActionW.ROTATION.getIcon());
        GuiUtils.applySelectedIconEffect(menu);
        menu.setEnabled(rotateAction.get().isActionEnabled());

        if (rotateAction.get().isActionEnabled()) {
          JMenuItem menuItem = new JMenuItem(ActionW.RESET.getTitle());
          menuItem.addActionListener(e -> rotateAction.get().setSliderValue(0));
          menu.add(menuItem);
          menuItem = new JMenuItem(Messages.getString("View2dContainer.-90"));
          menuItem.setIcon(ResourceUtil.getIcon(ActionIcon.ROTATE_COUNTERCLOCKWISE));
          GuiUtils.applySelectedIconEffect(menuItem);
          menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.ALT_DOWN_MASK));
          menuItem.addActionListener(
              e ->
                  rotateAction
                      .get()
                      .setSliderValue((rotateAction.get().getSliderValue() + 270) % 360));
          menu.add(menuItem);
          menuItem = new JMenuItem(Messages.getString("View2dContainer.+90"));
          menuItem.setIcon(ResourceUtil.getIcon(ActionIcon.ROTATE_CLOCKWISE));
          GuiUtils.applySelectedIconEffect(menuItem);
          menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.ALT_DOWN_MASK));
          menuItem.addActionListener(
              e ->
                  rotateAction
                      .get()
                      .setSliderValue((rotateAction.get().getSliderValue() + 90) % 360));
          menu.add(menuItem);
          menuItem = new JMenuItem(Messages.getString("View2dContainer.+180"));
          menuItem.addActionListener(
              e ->
                  rotateAction
                      .get()
                      .setSliderValue((rotateAction.get().getSliderValue() + 180) % 360));
          menu.add(menuItem);

          Optional<ToggleButtonListener> flipAction = getAction(ActionW.FLIP);
          if (flipAction.isPresent()) {
            menu.add(new JSeparator());
            menuItem =
                flipAction
                    .get()
                    .createUnregisteredJCCheckBoxMenuItem(
                        Messages.getString("View2dContainer.flip_h"),
                        ResourceUtil.getIcon(ActionIcon.FLIP));
            GuiUtils.applySelectedIconEffect(menuItem);
            menuItem.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.ALT_DOWN_MASK));
            menu.add(menuItem);
          }
        }
      }
    }
    return menu;
  }

  public JMenu getCineMenu(String prop) {
    JMenu menu = null;
    if (GuiUtils.getUICore().getSystemPreferences().getBooleanProperty(prop, true)) {
      Optional<SliderCineListener> scrollAction = getAction(ActionW.SCROLL_SERIES);
      if (scrollAction.isPresent()) {
        menu = new JMenu(Messages.getString("cine"));
        GuiUtils.applySelectedIconEffect(menu);
        menu.setEnabled(scrollAction.get().isActionEnabled());

        if (scrollAction.get().isActionEnabled()) {
          JMenuItem menuItem = new JMenuItem(ActionW.CINESTART.getTitle());
          menuItem.setIcon(ResourceUtil.getIcon(ActionIcon.EXECUTE));
          GuiUtils.applySelectedIconEffect(menuItem);
          menuItem.setActionCommand(ActionW.CINESTART.cmd());
          menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, 0));
          menuItem.addActionListener(EventManager.getInstance());
          menu.add(menuItem);

          menuItem = new JMenuItem(ActionW.CINESTOP.getTitle());
          menuItem.setIcon(ResourceUtil.getIcon(ActionIcon.SUSPEND));
          GuiUtils.applySelectedIconEffect(menuItem);
          menuItem.setActionCommand(ActionW.CINESTOP.cmd());
          menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, 0));
          menuItem.addActionListener(EventManager.getInstance());
          menu.add(menuItem);

          Optional<ToggleButtonListener> sweepAction = getAction(ActionW.CINE_SWEEP);
          if (sweepAction.isPresent()) {
            menu.add(new JSeparator());
            menuItem =
                sweepAction
                    .get()
                    .createUnregisteredJCCheckBoxMenuItem(
                        ActionW.CINE_SWEEP.getTitle(), ResourceUtil.getIcon(ActionIcon.LOOP));
            GuiUtils.applySelectedIconEffect(menuItem);
            menu.add(menuItem);
          }
        }
      }
    }
    return menu;
  }

  public JMenu getSortStackMenu(String prop) {
    JMenu menu = null;
    if (GuiUtils.getUICore().getSystemPreferences().getBooleanProperty(prop, true)) {
      Optional<ComboItemListener<SeriesComparator<?>>> sortStackAction =
          getAction(ActionW.SORT_STACK);
      if (sortStackAction.isPresent()) {
        menu =
            sortStackAction
                .get()
                .createUnregisteredRadioMenu(Messages.getString("View2dContainer.sort_stack"));
        Optional<ToggleButtonListener> inverseStackAction = getAction(ActionW.INVERSE_STACK);
        if (inverseStackAction.isPresent()) {
          menu.add(new JSeparator());
          menu.add(
              inverseStackAction
                  .get()
                  .createUnregisteredJCCheckBoxMenuItem(
                      Messages.getString("View2dContainer.inv_stack")));
        }
      }
    }
    return menu;
  }

  public JMenu getLutMenu(String prop) {
    JMenu menu = null;
    if (GuiUtils.getUICore().getSystemPreferences().getBooleanProperty(prop, true)) {
      Optional<ComboItemListener<ByteLut>> lutAction = getAction(ActionW.LUT);
      if (lutAction.isPresent()) {
        menu =
            lutAction
                .get()
                .createUnregisteredRadioMenu(
                    ActionW.LUT.getTitle(), ResourceUtil.getIcon(ActionIcon.LUT));
      }
    }
    return menu;
  }

  public JCheckBoxMenuItem getLutInverseMenu(String prop) {
    JCheckBoxMenuItem menu = null;
    if (GuiUtils.getUICore().getSystemPreferences().getBooleanProperty(prop, true)) {
      Optional<ToggleButtonListener> inverseLutAction = getAction(ActionW.INVERT_LUT);
      if (inverseLutAction.isPresent()) {
        menu =
            inverseLutAction
                .get()
                .createUnregisteredJCCheckBoxMenuItem(
                    ActionW.INVERT_LUT.getTitle(), ResourceUtil.getIcon(ActionIcon.INVERSE_LUT));
      }
    }
    return menu;
  }

  public JMenu getFilterMenu(String prop) {
    JMenu menu = null;
    if (GuiUtils.getUICore().getSystemPreferences().getBooleanProperty(prop, true)) {
      Optional<ComboItemListener<KernelData>> filterAction = getAction(ActionW.FILTER);
      if (filterAction.isPresent()) {
        menu =
            filterAction
                .get()
                .createUnregisteredRadioMenu(
                    Messages.getString("ImageTool.filter"),
                    ResourceUtil.getIcon(ActionIcon.FILTER));
      }
    }
    return menu;
  }

  // ***** OSGI commands: dcmview2d:cmd ***** //

  public void zoom(String[] argv) throws IOException {
    final String[] usage = {
      "Change the zoom value of the selected image", // NON-NLS
      "Usage: dcmview2d:zoom (set VALUE | increase NUMBER | decrease NUMBER)", // NON-NLS
      "  -s --set=VALUE        [decimal value]  set a new value from 0.0 to 12.0 (zoom magnitude, 0.0 => default, -400.0 => actual pixel preset, -300.0 => fit height, -200.0 => best fit, -100.0 => real size)", // NON-NLS
      "  -i --increase=NUMBER  increase of some amount", // NON-NLS
      "  -d --decrease=NUMBER  decrease of some amount", // NON-NLS
      "  -? --help             show help" // NON-NLS
    };
    final Option opt = Options.compile(usage).parse(argv);
    final List<String> args = opt.args();

    if (opt.isSet("help") // NON-NLS
        || !opt.isOnlyOneOptionActivate("set", "increase", "decrease")) { // NON-NLS
      opt.usage();
      return;
    }

    GuiExecutor.execute(() -> zoomCommand(opt, args));
  }

  private void zoomCommand(Option opt, List<String> args) {
    try {
      Optional<SliderChangeListener> zoomAction = getAction(ActionW.ZOOM);
      if (zoomAction.isPresent()) {
        if (opt.isSet("increase")) { // NON-NLS
          zoomAction
              .get()
              .setSliderValue(
                  zoomAction.get().getSliderValue() + opt.getNumber("increase")); // NON-NLS
        } else if (opt.isSet("decrease")) { // NON-NLS
          zoomAction
              .get()
              .setSliderValue(
                  zoomAction.get().getSliderValue() - opt.getNumber("decrease")); // NON-NLS
        } else if (opt.isSet("set")) { // NON-NLS
          double val3 = Double.parseDouble(opt.get("set")); // NON-NLS
          if (val3 <= 0.0) {
            firePropertyChange(
                ActionW.SYNCH.cmd(),
                null,
                new SynchEvent(getSelectedViewPane(), ActionW.ZOOM.cmd(), val3));
          } else {
            zoomAction.get().setRealValue(val3);
          }
        }
      }
    } catch (Exception e) {
      LOGGER.error("Zoom command: {}", args.get(0), e);
    }
  }

  public void wl(String[] argv) throws IOException {
    final String[] usage = {
      "Change the window/level values of the selected image (increase or decrease into a normalized range of 4096)", // NON-NLS
      "Usage: dcmview2d:wl -- WIN LEVEL", // NON-NLS
      "WIN and LEVEL are Integer. It is mandatory to have '--' (end of options) for negative values", // NON-NLS
      "  -? --help       show help" // NON-NLS
    };
    final Option opt = Options.compile(usage).parse(argv);
    final List<String> args = opt.args();

    if (opt.isSet("help") || args.size() != 2) { // NON-NLS
      opt.usage();
      return;
    }

    GuiExecutor.execute(
        () -> {
          try {
            Optional<SliderChangeListener> windowAction = getAction(ActionW.WINDOW);
            Optional<SliderChangeListener> levelAction = getAction(ActionW.LEVEL);
            if (windowAction.isPresent() && levelAction.isPresent()) {
              int win = windowAction.get().getSliderValue() + Integer.parseInt(args.get(0));
              int level = levelAction.get().getSliderValue() + Integer.parseInt(args.get(1));
              windowAction.get().setSliderValue(win);
              levelAction.get().setSliderValue(level);
            }
          } catch (Exception e) {
            LOGGER.error("Window/level command: {} {}", args.get(0), args.get(1), e);
          }
        });
  }

  public void move(String[] argv) throws IOException {
    final String[] usage = {
      "Pan the selected image", // NON-NLS
      "Usage: dcmview2d:move -- X Y", // NON-NLS
      "X and Y are Integer. It is mandatory to have '--' (end of options) for negative values", // NON-NLS
      "  -? --help       show help" // NON-NLS
    };
    final Option opt = Options.compile(usage).parse(argv);
    final List<String> args = opt.args();

    if (opt.isSet("help") || args.size() != 2) { // NON-NLS
      opt.usage();
      return;
    }

    GuiExecutor.execute(
        () -> {
          try {
            int valx = Integer.parseInt(args.get(0));
            int valy = Integer.parseInt(args.get(1));
            getAction(ActionW.PAN)
                .ifPresent(a -> a.setPoint(new PanPoint(PanPoint.State.MOVE, valx, valy)));

          } catch (Exception e) {
            LOGGER.error("Move (x,y) command: {} {}", args.get(0), args.get(1), e);
          }
        });
  }

  public void scroll(String[] argv) throws IOException {
    final String[] usage = {
      "Scroll into the images of the selected series", // NON-NLS
      "Usage: dcmview2d:scroll ( -s NUMBER | -i NUMBER | -d NUMBER)", // NON-NLS
      "  -s --set=NUMBER       set a new value from 1 to series size", // NON-NLS
      "  -i --increase=NUMBER  increase of some amount", // NON-NLS
      "  -d --decrease=NUMBER  decrease of some amount", // NON-NLS
      "  -? --help             show help" // NON-NLS
    };
    final Option opt = Options.compile(usage).parse(argv);

    if (opt.isSet("help") // NON-NLS
        || !opt.isOnlyOneOptionActivate("set", "increase", "decrease")) { // NON-NLS
      opt.usage();
      return;
    }

    GuiExecutor.execute(
        () -> {
          try {
            Optional<SliderCineListener> cineAction = getAction(ActionW.SCROLL_SERIES);
            if (cineAction.isPresent() && cineAction.get().isActionEnabled()) {
              SliderCineListener moveTroughSliceAction = cineAction.get();
              if (opt.isSet("increase")) { // NON-NLS
                moveTroughSliceAction.setSliderValue(
                    moveTroughSliceAction.getSliderValue() + opt.getNumber("increase")); // NON-NLS
              } else if (opt.isSet("decrease")) { // NON-NLS
                moveTroughSliceAction.setSliderValue(
                    moveTroughSliceAction.getSliderValue() - opt.getNumber("decrease")); // NON-NLS
              } else if (opt.isSet("set")) { // NON-NLS
                moveTroughSliceAction.setSliderValue(opt.getNumber("set")); // NON-NLS
              }
            }
          } catch (Exception e) {
            LOGGER.error("Scroll command error:", e);
          }
        });
  }

  public void layout(String[] argv) throws IOException {
    final String[] usage = {
      "Select a split-screen layout", // NON-NLS
      "Usage: dcmview2d:layout ( -n NUMBER | -i ID )", // NON-NLS
      "  -n --number=NUMBER  select the best matching number of views", // NON-NLS
      "  -i --id=ID          select the layout from its identifier", // NON-NLS
      "  -? --help           show help" // NON-NLS
    };
    final Option opt = Options.compile(usage).parse(argv);

    if (opt.isSet("help") || !opt.isOnlyOneOptionActivate("number", "id")) { // NON-NLS
      opt.usage();
      return;
    }
    GuiExecutor.execute(
        () -> {
          try {
            if (opt.isSet("number")) { // NON-NLS
              if (selectedView2dContainer != null) {
                MigLayoutModel val1 =
                    selectedView2dContainer.getBestDefaultViewLayout(
                        opt.getNumber("number")); // NON-NLS
                getAction(ActionW.LAYOUT).ifPresent(a -> a.setSelectedItem(val1));
              }
            } else if (opt.isSet("id")) {
              if (selectedView2dContainer != null) {
                MigLayoutModel val2 = selectedView2dContainer.getViewLayout(opt.get("id"));
                if (val2 != null) {
                  getAction(ActionW.LAYOUT).ifPresent(a -> a.setSelectedItem(val2));
                }
              }
            }
          } catch (Exception e) {
            LOGGER.error("Layout command error", e);
          }
        });
  }

  public void mouseLeftAction(String[] argv) throws IOException {
    final String[] usage = {
      "Change the mouse left action", // NON-NLS
      "Usage: dcmview2d:mouseLeftAction COMMAND", // NON-NLS
      "COMMAND is (sequence|winLevel|zoom|pan|rotation|crosshair|measure|draw|contextMenu|none)", // NON-NLS
      "  -? --help       show help" // NON-NLS
    };
    final Option opt = Options.compile(usage).parse(argv);
    final List<String> args = opt.args();

    if (opt.isSet("help") || args.size() != 1) { // NON-NLS
      opt.usage();
      return;
    }

    GuiExecutor.execute(
        () -> {
          String command = args.get(0);
          if (command != null) {
            try {
              if (command.startsWith("session")) { // NON-NLS
                AuditLog.LOGGER.info("source:telnet {}", command);
              } else {
                AuditLog.LOGGER.info(
                    "source:telnet mouse:{} action:{}", MouseActions.T_LEFT, command);
                excecuteMouseAction(command);
              }
            } catch (Exception e) {
              LOGGER.error("Mouse command: {}", command, e);
            }
          }
        });
  }

  private void excecuteMouseAction(String command) {
    if (!command.equals(mouseActions.getAction(MouseActions.T_LEFT))) {
      mouseActions.setAction(MouseActions.T_LEFT, command);
      ImageViewerPlugin<DicomImageElement> view = getSelectedView2dContainer();
      if (view != null) {
        view.setMouseActions(mouseActions);
        final ViewerToolBar toolBar = view.getViewerToolBar();
        if (toolBar != null) {
          // Test if mouse action exist and if not NO_ACTION is set
          Feature<?> action = toolBar.getToolBarAction(command);
          if (action == null) {
            command = ActionW.NO_ACTION.cmd();
          }
          toolBar.changeButtonState(MouseActions.T_LEFT, command);
        }
      }
    }
  }

  public void synch(String[] argv) throws IOException {
    final String[] usage = {
      "Set a synchronization mode", // NON-NLS
      "Usage: dcmview2d:synch VALUE", // NON-NLS
      "VALUE is " // NON-NLS
          + View2dContainer.DEFAULT_SYNCH_LIST.stream()
              .map(SynchView::getCommand) // NON-NLS
              .collect(Collectors.joining("|", "(", ")")),
      "  -? --help       show help" // NON-NLS
    };
    final Option opt = Options.compile(usage).parse(argv);
    final List<String> args = opt.args();

    if (opt.isSet("help") || args.size() != 1) { // NON-NLS
      opt.usage();
      return;
    }
    GuiExecutor.execute(
        () -> {
          String command = args.getFirst();
          if (command != null) {
            ImageViewerPlugin<DicomImageElement> view = getSelectedView2dContainer();
            if (view != null) {
              try {
                Optional<ComboItemListener<SynchView>> synchAction = getAction(ActionW.SYNCH);
                if (synchAction.isPresent()) {
                  for (SynchView synch : view.getSynchList()) {
                    if (synch.getCommand().equals(command)) {
                      synchAction.get().setSelectedItem(synch);
                      return;
                    }
                  }
                  throw new IllegalArgumentException(command + " not found!");
                }
              } catch (Exception e) {
                LOGGER.error("Synch command: {}", command, e);
              }
            }
          }
        });
  }

  public void reset(String[] argv) throws IOException {
    final String[] usage = {
      "Reset image display", // NON-NLS
      "Usage: dcmview2d:reset (-a | COMMAND...)", // NON-NLS
      "COMMAND is (winLevel|zoom|pan|rotation)", // NON-NLS
      "  -a --all        reset to original display", // NON-NLS
      "  -? --help       show help" // NON-NLS
    };
    final Option opt = Options.compile(usage).parse(argv);
    final List<String> args = opt.args();

    if (opt.isSet("help") || args.isEmpty() && !opt.isSet("all")) { // NON-NLS
      opt.usage();
      return;
    }

    GuiExecutor.execute(
        () -> {
          if (opt.isSet("all")) { // NON-NLS
            reset(ResetTools.ALL);
          } else {
            for (String command : args) {
              try {
                if (ActionW.WINLEVEL.cmd().equals(command)) {
                  reset(ResetTools.WL);
                } else if (ActionW.ZOOM.cmd().equals(command)) {
                  reset(ResetTools.ZOOM);
                } else if (ActionW.PAN.cmd().equals(command)) {
                  reset(ResetTools.PAN);
                } else {
                  LOGGER.warn("Reset command not found: {}", command);
                }
              } catch (Exception e) {
                LOGGER.error("Reset command: {}", command, e);
              }
            }
          }
        });
  }
}
