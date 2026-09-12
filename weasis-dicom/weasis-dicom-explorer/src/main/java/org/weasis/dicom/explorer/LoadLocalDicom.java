/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BooleanSupplier;
import javax.swing.JOptionPane;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.media.data.*;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.serialize.XmlSerializer;
import org.weasis.core.util.FileUtil;
import org.weasis.dicom.codec.*;
import org.weasis.dicom.codec.DicomMediaIO.Reading;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.explorer.HangingProtocols.OpeningViewer;
import org.weasis.dicom.explorer.imp.DicomZipCodec;
import org.weasis.dicom.explorer.imp.DicomZipMediaIO;

public class LoadLocalDicom extends LoadDicom {

  private final File[] files;
  private final boolean recursive;
  private final boolean clearExistingStudies;
  private List<Path> importRoots;
  private Path importDirectory;

  public LoadLocalDicom(
      File[] files, boolean recursive, DataExplorerModel explorerModel, OpeningViewer openingMode) {
    this(files, recursive, explorerModel, openingMode, false);
  }

  public LoadLocalDicom(
      File[] files,
      boolean recursive,
      DataExplorerModel explorerModel,
      OpeningViewer openingMode,
      boolean clearExistingStudies) {
    this(
        files,
        recursive,
        explorerModel,
        new PluginOpeningStrategy(openingMode),
        clearExistingStudies);
  }

  public LoadLocalDicom(
      File[] files,
      boolean recursive,
      DataExplorerModel explorerModel,
      PluginOpeningStrategy openingStrategy) {
    this(files, recursive, explorerModel, openingStrategy, false);
  }

  public LoadLocalDicom(
      File[] files,
      boolean recursive,
      DataExplorerModel explorerModel,
      PluginOpeningStrategy openingStrategy,
      boolean clearExistingStudies) {
    super(explorerModel, false, openingStrategy);
    this.files = Objects.requireNonNull(files);
    this.recursive = recursive;
    this.clearExistingStudies = clearExistingStudies;
  }

  /** For extracted archives, retain the original archive folder rather than the temporary tree. */
  public void setImportDirectory(Path directory) {
    importDirectory = directory == null ? null : directory.toAbsolutePath().normalize();
  }

  @Override
  protected void recordImportDirectory(MediaSeriesGroup study, java.net.URI source) {
    if (source != null && "file".equalsIgnoreCase(source.getScheme())) {
      importDirectoryFor(Path.of(source))
          .ifPresent(directory -> LocalImportDirectory.record(study, directory));
    }
  }

  private Optional<Path> importDirectoryFor(Path file) {
    if (importDirectory != null) return Optional.of(importDirectory);
    if (importRoots == null) {
      importRoots =
          Arrays.stream(files)
              .filter(Objects::nonNull)
              .map(
                  value ->
                      value.isDirectory()
                          ? value.toPath()
                          : value.toPath().toAbsolutePath().getParent())
              .filter(Objects::nonNull)
              .map(path -> path.toAbsolutePath().normalize())
              .distinct()
              .toList();
    }
    return LocalImportDirectory.forFile(file, importRoots);
  }

  @Override
  protected Boolean doInBackground() throws Exception {
    startLoadingEvent();
    if (files.length > 0 && !isCancelled()) {
      if (clearExistingStudies) {
        dicomModel.removeAllPatientsAndCloseViewers();
      }
      openingStrategy.prepareImport();
      if (!isCancelled()) {
        addSelectionAndNotify(files, true);
      }
    }
    return !isCancelled();
  }

  protected void addSelectionAndNotify(File[] file, boolean firstLevel) {
    if (file == null || file.length < 1) {
      return;
    }

    Set<DicomSeries> uniqueSeriesSet = new LinkedHashSet<>();
    ArrayList<File> folders = new ArrayList<>();
    for (File value : file) {
      if (isCancelled()) {
        return;
      }
      if (value == null || !value.canRead()) {
        continue;
      }

      if (value.isDirectory()) {
        if (firstLevel || recursive) {
          folders.add(value);
        }
      } else if (FileUtil.isFileExtensionMatching(value.toPath(), DicomCodec.FILE_EXTENSIONS)
          || MimeInspector.isMatchingMimeTypeFromMagicNumber(value, DicomMediaIO.DICOM_MIMETYPE)) {
        DicomMediaIO loader = new DicomMediaIO(value);
        Reading reading = loader.getReadingStatus();
        if (reading == Reading.READABLE) {
          if (value.getPath().startsWith(AppProperties.APP_TEMP_DIR.toString())) {
            loader.getFileCache().setOriginalTempFile(value.toPath());
          }
          uniqueSeriesSet.add(buildDicomStructure(loader));

          File gpxFile = new File(value.getPath() + ".xml");
          GraphicModel graphicModel = XmlSerializer.readPresentationModel(gpxFile);
          if (graphicModel != null) {
            loader.setTag(TagW.PresentationModel, graphicModel);
          }
          if (isCancelled()) {
            return;
          }
        } else if (reading == Reading.ERROR) {
          errors.incrementAndGet();
        } else if (reading == Reading.UNSUPPORTED) {
          unsupported.incrementAndGet();
        }
      } else if (isDicomZip(value)) {
        if (isCancelled()) {
          return;
        }
        DicomZipMediaIO.loadDicomZip(
            value,
            dicomModel,
            HangingProtocols.OpeningViewer.ALL_PATIENTS,
            null,
            false,
            importDirectoryFor(value.toPath()).orElse(null));
        if (isCancelled()) {
          return;
        }
      }
    }

    if (isCancelled()) {
      return;
    }
    if (openingStrategy.isFullImportSession()) {
      updateSeriesThumbnail(uniqueSeriesSet, dicomModel, this::isCancelled);
    } else {
      for (DicomSeries series : uniqueSeriesSet) {
        if (isCancelled()) {
          return;
        }
        dicomModel.buildThumbnail(series);
      }
    }

    for (File folder : folders) {
      if (isCancelled()) {
        return;
      }
      addSelectionAndNotify(folder.listFiles(), false);
    }
  }

  static boolean isDicomZip(File file) {
    // Transcription Word documents live beside studies and have ZIP magic bytes too.
    return !file.getName().toLowerCase(Locale.ROOT).endsWith(".docx")
        && (FileUtil.isFileExtensionMatching(file.toPath(), DicomZipCodec.FILE_EXTENSIONS)
            || MimeInspector.isMatchingMimeTypeFromMagicNumber(file, DicomZipMediaIO.MIME_TYPE));
  }

  public static void updateSeriesThumbnail(Set<DicomSeries> seriesList, DicomModel dicomModel) {
    updateSeriesThumbnail(seriesList, dicomModel, () -> false);
  }

  private static void updateSeriesThumbnail(
      Set<DicomSeries> seriesList, DicomModel dicomModel, BooleanSupplier isCancelled) {
    if (dicomModel == null || seriesList == null) {
      return;
    }
    for (DicomSeries series : seriesList) {
      if (isCancelled.getAsBoolean()) {
        return;
      }
      if (series != null) {
        if (!DicomModel.isHiddenModality(series)) {
          boolean split = seriesPostProcessing(series, dicomModel);
          if (!split) {
            dicomModel.buildThumbnail(series);
          }

          if (series.isSuitableFor3d()) {
            dicomModel.firePropertyChange(
                new ObservableEvent(
                    ObservableEvent.BasicAction.UPDATE,
                    series,
                    null,
                    new SeriesEvent(SeriesEvent.Action.UPDATE, series, null)));
          }
        }
      }
    }
  }

  public static boolean seriesPostProcessing(DicomSeries dicomSeries, DicomModel dicomModel) {
    return seriesPostProcessing(dicomSeries, dicomModel, false);
  }

  public static boolean seriesPostProcessing(
      DicomSeries dicomSeries, DicomModel dicomModel, boolean force) {
    Integer step = (Integer) dicomSeries.getTagValue(TagW.stepNDimensions);
    if (step == null || step < 1 || force) {
      int imageCount = dicomSeries.size(null);
      if (imageCount == 0) {
        return false;
      }
      List<DicomImageElement> imageList =
          dicomSeries.copyOfMedias(null, SortSeriesStack.slicePosition);
      int samplingRate = calculateSamplingRateFor4d(imageList);
      dicomSeries.setTag(TagW.stepNDimensions, samplingRate);
      if (samplingRate < 2 || (samplingRate > 7 && !force)) {
        return false;
      }
      for (int i = 0; i < samplingRate; i++) {
        DicomImageElement image = imageList.get(i);
        if (image.getMediaReader() instanceof DicomMediaIO dicomReader) {
          MediaSeries<DicomImageElement> newSeries;
          if (i == 0) {
            dicomSeries.removeAllMedias();
            newSeries = dicomSeries;
          } else {
            newSeries = dicomModel.splitSeries(dicomReader, dicomSeries);
          }
          newSeries.setTag(TagW.stepNDimensions, 1);
          Filter<DicomImageElement> samplingFilter = getDicomImageElementFilter(i, samplingRate);
          newSeries.addAll(Filter.makeList(samplingFilter.filter(imageList)));
          if (i == 0) {
            SeriesThumbnail thumbnail = (SeriesThumbnail) dicomSeries.getTagValue(TagW.Thumbnail);
            if (thumbnail != null) {
              thumbnail.reBuildThumbnail(null, MediaSeries.MEDIA_POSITION.MIDDLE);
            }
          }
          dicomModel.firePropertyChange(
              new ObservableEvent(ObservableEvent.BasicAction.UPDATE, dicomModel, null, newSeries));
        }
      }
      return true;
    }
    return false;
  }

  /**
   * Checks if a series is a multi-phase series that can be separated.
   *
   * @param series the series to check
   * @return true if the series has multiple phases (step > 1) and is a DicomSeries
   */
  public static boolean isMultiPhaseSeries(MediaSeries<?> series) {
    return series.getTagValue(TagW.stepNDimensions) instanceof Integer step && step > 1;
  }

  public static MediaSeries<DicomImageElement> confirmSplittingMultiPhaseSeries(
      MediaSeries<DicomImageElement> series) {
    if (isMultiPhaseSeries(series) && series instanceof DicomSeries dicomSeries) {
      int result =
          JOptionPane.showConfirmDialog(
              GuiUtils.getUICore().getApplicationWindow(),
              Messages.getString("msg.multi.phase"),
              Messages.getString("multi.phase.title"),
              JOptionPane.OK_CANCEL_OPTION,
              JOptionPane.QUESTION_MESSAGE);

      if (result == JOptionPane.OK_OPTION) {
        DicomModel dicomModel =
            (DicomModel) dicomSeries.getTagValue(org.weasis.core.api.media.data.TagW.ExplorerModel);
        if (dicomModel != null) {
          LoadLocalDicom.seriesPostProcessing(dicomSeries, dicomModel, true);
          return dicomSeries;
        }
      }
      return null;
    }
    return series;
  }

  private static Filter<DicomImageElement> getDicomImageElementFilter(int index, int size) {
    return new Filter<>() {
      private final int samplingRate = size;
      private int currentIndex = index;

      @Override
      public boolean passes(DicomImageElement item) {
        boolean pass = (currentIndex % samplingRate) == 0;
        currentIndex++;
        return pass;
      }
    };
  }

  static int calculateSamplingRateFor4d(List<DicomImageElement> imageList) {
    try {
      if (imageList.size() >= 2) {
        double firstPosSum = DicomMediaUtils.getSlicePositionValue(imageList.getFirst());

        int samePositionCount = 1;
        for (int i = 1; i < imageList.size(); i++) {
          double posSum = DicomMediaUtils.getSlicePositionValue(imageList.get(i));
          if (Math.abs(posSum - firstPosSum) < 0.05) {
            samePositionCount++;
          } else {
            break;
          }
        }

        // If we found multiple images at the same position, that's likely our phase count
        if (samePositionCount > 1 && samePositionCount < imageList.size() / 2) {
          return samePositionCount;
        }
      }
    } catch (Exception e) {
      return 1;
    }

    return 1;
  }
}
