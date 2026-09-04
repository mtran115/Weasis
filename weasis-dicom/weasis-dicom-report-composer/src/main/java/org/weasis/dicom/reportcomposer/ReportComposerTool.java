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

import bibliothek.gui.dock.common.CLocation;
import bibliothek.gui.dock.common.mode.ExtendedMode;
import com.formdev.flatlaf.util.SystemFileChooser;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.ShortcutManager;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.editor.ExternalDisplay;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.reportcomposer.ArrowAnnotationDialog.AnnotationResult;
import org.weasis.dicom.reportcomposer.CasePacketExporter.ExportResult;
import org.weasis.dicom.reportcomposer.DicomContextReader.Selection;
import org.weasis.dicom.reportcomposer.DicomContextReader.ViewportCanvas;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.ExamTemplate;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.FindingChoice;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.GeneratedFinding;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.SpineRegion;

public class ReportComposerTool extends PluginTool implements SeriesViewerListener {
  public static final String BUTTON_NAME = "Report Composer";

  private static final String OUTPUT_DIRECTORY_KEY = "report.composer.output.directory";
  private static final int FIELD_COLUMNS = 28;

  private final Map<String, ReportDraft> drafts = new LinkedHashMap<>();
  private final Map<String, ExamTemplate> draftExamTemplates = new LinkedHashMap<>();
  private final StructuredFindingDraftTracker<SpineRegion, SpineFindingBuilder.Selection>
      spineFindingTracker =
          new StructuredFindingDraftTracker<>(
              SpineFindingBuilder.Selection::region, SpineFindingBuilder::generate);
  private final StructuredFindingDraftTracker<ExamTemplate, ShoulderFindingBuilder.Selection>
      shoulderFindingTracker =
          new StructuredFindingDraftTracker<>(
              selection -> ExamTemplate.SHOULDER, ShoulderFindingBuilder::generate);
  private final StructuredFindingDraftTracker<ExamTemplate, KneeFindingBuilder.Selection>
      kneeFindingTracker =
          new StructuredFindingDraftTracker<>(
              selection -> ExamTemplate.KNEE, KneeFindingBuilder::generate);
  private final StructuredFindingDraftTracker<ExamTemplate, BrainFindingBuilder.Selection>
      brainFindingTracker =
          new StructuredFindingDraftTracker<>(
              selection -> ExamTemplate.BRAIN, BrainFindingBuilder::generate);
  private final StructuredFindingDraftTracker<ExamTemplate, WristFindingBuilder.Selection>
      wristFindingTracker =
          new StructuredFindingDraftTracker<>(
              selection -> ExamTemplate.WRIST, WristFindingBuilder::generate);
  private final ViewerEventState viewerEventState = new ViewerEventState();
  private final AtomicBoolean studySynchronizationQueued = new AtomicBoolean();
  private final AtomicBoolean studySynchronizationRequested = new AtomicBoolean();
  private final CasePacketExporter packetExporter = new CasePacketExporter();
  private final ReportInstructionHistory instructionHistory = new ReportInstructionHistory();
  private final JTabbedPane tabs = new JTabbedPane();
  private final JLabel patientLabel = new JLabel("No active DICOM study");
  private final JLabel examLabel = new JLabel("Select an image to begin");
  private final JLabel accessionLabel = new JLabel(" ");
  private final JButton popOutButton =
      new JButton("Pop Out", ResourceUtil.getIcon(ActionIcon.OPEN_EXTERNAL));
  private final JComboBox<ExamTemplate> examCombo = new JComboBox<>();
  private final JComboBox<String> categoryCombo = new JComboBox<>();
  private final JComboBox<String> structureCombo = new JComboBox<>();
  private final JComboBox<FindingChoice> findingCombo = new JComboBox<>();
  private final Map<SpineRegion, SpineFormPanel> spineForms = new EnumMap<>(SpineRegion.class);
  private final ShoulderFormPanel shoulderForm = new ShoulderFormPanel();
  private final KneeFormPanel kneeForm = new KneeFormPanel();
  private final BrainFormPanel brainForm = new BrainFormPanel();
  private final WristFormPanel wristForm = new WristFormPanel();
  private final JTextArea reportInstructions = textArea(4);
  private final JTextArea findingText = textArea(3);
  private final JTextArea impressionText = textArea(2);
  private final JCheckBox includeInImpression = new JCheckBox("Include in impression", true);
  private final JButton saveFindingButton = new JButton("Add");
  private final JButton cancelEditButton = new JButton("Cancel Edit");
  private final JButton backToStructuredFormButton = new JButton("Structured Form");
  private final DefaultListModel<FindingEntry> findingModel = new DefaultListModel<>();
  private final JList<FindingEntry> findingList = new JList<>(findingModel);
  private final DefaultListModel<KeyImageCapture> keyImageModel = new DefaultListModel<>();
  private final JList<KeyImageCapture> keyImageList = new JList<>(keyImageModel);
  private final JComboBox<CaptureViewport> captureViewportCombo = new JComboBox<>();
  private final JButton captureButton = new JButton("Capture Selected View");
  private final JTextArea keyImageCaption = textArea(2);
  private final JTextArea previewArea = textArea(24);
  private final JLabel outputFolderLabel = new JLabel("No transcription folder selected");
  private final JLabel statusLabel = new JLabel(" ");
  private final JButton exportButton = new JButton("Export Instruction Packet");
  private final JButton openExportButton =
      iconButton(ActionIcon.OPEN_EXTERNAL, "Open exported case folder");
  private final AWTEventListener canvasInteractionListener = this::trackCanvasInteraction;

  private ReportDraft currentDraft;
  private String editingFindingId;
  private Path outputDirectory;
  private Path lastExportDirectory;
  private boolean updatingCatalogControls;
  private boolean updatingReportInstructions;
  private boolean structuredFormMode;
  private JPanel genericFindingBuilder;
  private DefaultView2d<DicomImageElement> lastActiveCanvas;
  private DefaultView2d<DicomImageElement> lastInteractedCanvas;
  private boolean canvasInteractionListenerInstalled;
  private boolean captureShortcutPending;
  private boolean nativePopOutInstalled;

  public ReportComposerTool() {
    super(BUTTON_NAME, POSITION.EAST, ExtendedMode.NORMALIZED, Insertable.Type.TOOL_EXT, 145);
    dockable.setTitleIcon(ResourceUtil.getIcon(OtherIcon.KEY_IMAGE));
    dockable.setMinimizable(true);
    dockable.setExternalizable(true);
    setDockableWidth(430);
    initializeOutputDirectory();
    buildInterface();
    updateOutputFolderLabel();
    initializeCatalogControls();
    GuiExecutor.execute(this::synchronizeStudy);
  }

  @Override
  public Component getToolComponent() {
    return this;
  }

  @Override
  public void addNotify() {
    super.addNotify();
    if (!canvasInteractionListenerInstalled) {
      Toolkit.getDefaultToolkit()
          .addAWTEventListener(
              canvasInteractionListener,
              AWTEvent.MOUSE_EVENT_MASK
                  | AWTEvent.MOUSE_WHEEL_EVENT_MASK
                  | AWTEvent.KEY_EVENT_MASK);
      canvasInteractionListenerInstalled = true;
    }
    SwingUtilities.invokeLater(this::installNativePopOutWindow);
  }

  @Override
  public void removeNotify() {
    if (canvasInteractionListenerInstalled) {
      Toolkit.getDefaultToolkit().removeAWTEventListener(canvasInteractionListener);
      canvasInteractionListenerInstalled = false;
    }
    super.removeNotify();
  }

  @Override
  public void changingViewContentEvent(SeriesViewerEvent event) {
    EVENT type = event.getEventType();
    if (!EVENT.SELECT.equals(type)
        && !EVENT.SELECT_VIEW.equals(type)
        && !EVENT.LAYOUT.equals(type)) {
      return;
    }

    Optional<DefaultView2d<DicomImageElement>> selectedCanvas =
        DicomContextReader.selectedCanvas(event);
    DefaultView2d<DicomImageElement> canvas = selectedCanvas.orElse(null);
    Object series = canvas == null ? event.getSeries() : canvas.getSeries();
    boolean structureChanged =
        viewerEventState.observe(type, event.getSeriesViewer(), canvas, series);

    // EVENT.SELECT is emitted for every displayed slice. Nothing structural changed, so avoid all
    // DICOM metadata reads and Swing model updates on this hot path.
    if (EVENT.SELECT.equals(type) && !structureChanged && canvas == lastActiveCanvas) {
      return;
    }

    boolean pointerInsideComposer = isPointerInsideComposer();
    AWTEvent currentEvent = EventQueue.getCurrentEvent();
    boolean hoverActivation =
        currentEvent instanceof MouseEvent mouseEvent
            && mouseEvent.getID() == MouseEvent.MOUSE_ENTERED;
    boolean rememberedSelectionChanged = false;
    if (shouldRememberViewerSelection(type, pointerInsideComposer, hoverActivation)
        && canvas != null) {
      rememberedSelectionChanged = lastActiveCanvas != canvas;
      lastActiveCanvas = canvas;
    }
    if (structureChanged || rememberedSelectionChanged) {
      requestStudySynchronization();
    }
  }

  private void requestStudySynchronization() {
    studySynchronizationRequested.set(true);
    if (studySynchronizationQueued.compareAndSet(false, true)) {
      SwingUtilities.invokeLater(this::runQueuedStudySynchronization);
    }
  }

  private void runQueuedStudySynchronization() {
    try {
      do {
        studySynchronizationRequested.set(false);
        synchronizeStudy();
      } while (studySynchronizationRequested.get());
    } finally {
      studySynchronizationQueued.set(false);
      if (studySynchronizationRequested.get()) {
        requestStudySynchronization();
      }
    }
  }

  static boolean shouldRememberViewerSelection(
      EVENT type, boolean pointerInsideComposer, boolean hoverActivation) {
    return EVENT.LAYOUT.equals(type) || (!pointerInsideComposer && !hoverActivation);
  }

  static boolean isDeliberateCanvasMouseEvent(int eventId) {
    return eventId == MouseEvent.MOUSE_PRESSED
        || eventId == MouseEvent.MOUSE_RELEASED
        || eventId == MouseEvent.MOUSE_WHEEL;
  }

  private void trackCanvasInteraction(AWTEvent event) {
    if (event instanceof KeyEvent keyEvent && handleCaptureShortcut(keyEvent)) {
      return;
    }
    if (event instanceof MouseEvent mouseEvent
        && isDeliberateCanvasMouseEvent(mouseEvent.getID())) {
      Optional<DefaultView2d<DicomImageElement>> sourceCanvas = Optional.empty();
      if (mouseEvent.getSource() instanceof Component component) {
        sourceCanvas = DicomContextReader.canvasFor(component);
      }
      sourceCanvas
          .or(() -> DicomContextReader.canvasAt(mouseEvent.getLocationOnScreen()))
          .ifPresent(canvas -> lastInteractedCanvas = canvas);
    } else if (event instanceof KeyEvent keyEvent
        && keyEvent.getID() == KeyEvent.KEY_RELEASED
        && keyEvent.getSource() instanceof Component component) {
      DicomContextReader.canvasFor(component).ifPresent(canvas -> lastInteractedCanvas = canvas);
    }
  }

  private boolean handleCaptureShortcut(KeyEvent event) {
    if (event.getID() != KeyEvent.KEY_PRESSED
        || event.isConsumed()
        || !(event.getSource() instanceof Component component)
        || DicomContextReader.canvasFor(component).isEmpty()) {
      return false;
    }

    ShortcutManager shortcuts = ShortcutManager.getInstance();
    int viewportNumber;
    if (shortcuts.matches(ShortcutManager.ID_REPORT_COMPOSER_CAPTURE_VIEW_1, event)) {
      viewportNumber = 1;
    } else if (shortcuts.matches(ShortcutManager.ID_REPORT_COMPOSER_CAPTURE_VIEW_2, event)) {
      viewportNumber = 2;
    } else {
      return false;
    }

    event.consume();
    if (!captureShortcutPending) {
      captureShortcutPending = true;
      SwingUtilities.invokeLater(
          () -> {
            try {
              captureKeyImage(viewportNumber);
            } finally {
              captureShortcutPending = false;
            }
          });
    }
    return true;
  }

  private boolean isPointerInsideComposer() {
    var pointer = MouseInfo.getPointerInfo();
    if (pointer == null || !isShowing()) {
      return false;
    }
    Point point = new Point(pointer.getLocation());
    SwingUtilities.convertPointFromScreen(point, this);
    return contains(point);
  }

  @Override
  protected void changeToolWindowAnchor(CLocation clocation) {
    GuiExecutor.execute(this::updatePopOutButton);
  }

  private void buildInterface() {
    setLayout(new BorderLayout(0, 6));
    setBorder(GuiUtils.getEmptyBorder(8, 8, 8, 8));
    add(buildCaseHeader(), BorderLayout.NORTH);
    tabs.addTab("Compose", buildComposeTab());
    tabs.addTab("Key Images", buildKeyImageTab());
    tabs.addTab("Preview", buildPreviewTab());
    tabs.addChangeListener(
        event -> {
          savePendingStructuredFindings();
          if (tabs.getSelectedIndex() == 1) {
            refreshCaptureViewports();
          }
        });
    add(tabs, BorderLayout.CENTER);
  }

  private JPanel buildCaseHeader() {
    JPanel header = new JPanel(new BorderLayout(8, 0));
    JPanel caseLabels = new JPanel();
    caseLabels.setLayout(new javax.swing.BoxLayout(caseLabels, javax.swing.BoxLayout.Y_AXIS));
    patientLabel.setFont(patientLabel.getFont().deriveFont(Font.BOLD, 14f));
    examLabel.setFont(examLabel.getFont().deriveFont(Font.PLAIN, 12f));
    accessionLabel.setForeground(
        GuiUtils.getUICore()
            .getSystemPreferences()
            .getColorProperty("Label.disabledForeground", Color.GRAY));
    caseLabels.add(patientLabel);
    caseLabels.add(examLabel);
    caseLabels.add(accessionLabel);
    header.add(caseLabels, BorderLayout.CENTER);

    popOutButton.setToolTipText("Move Report Composer to another monitor");
    popOutButton.setVerticalTextPosition(SwingConstants.CENTER);
    popOutButton.setHorizontalTextPosition(SwingConstants.RIGHT);
    popOutButton.addActionListener(event -> togglePopOut());
    header.add(popOutButton, BorderLayout.EAST);
    header.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Color.GRAY),
            GuiUtils.getEmptyBorder(0, 2, 8, 2)));
    return header;
  }

  private void togglePopOut() {
    if (ExtendedMode.EXTERNALIZED.equals(dockable.getExtendedMode())) {
      dockable.setResizeLocked(true);
      dockable.setExtendedMode(ExtendedMode.NORMALIZED);
      return;
    }

    installNativePopOutWindow();
    Rectangle bounds = preferredPopOutBounds(targetDisplayBounds());
    CLocation externalLocation =
        CLocation.external(bounds.x, bounds.y, bounds.width, bounds.height);
    dockable.setDefaultLocation(ExtendedMode.EXTERNALIZED, externalLocation);
    dockable.setResizeLocked(false);
    dockable.setLocation(externalLocation);
  }

  private Rectangle targetDisplayBounds() {
    GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
    GraphicsConfiguration currentConfiguration = getGraphicsConfiguration();
    GraphicsDevice currentDevice =
        currentConfiguration == null
            ? environment.getDefaultScreenDevice()
            : currentConfiguration.getDevice();
    GraphicsDevice targetDevice = currentDevice;
    for (GraphicsDevice device : environment.getScreenDevices()) {
      if (!device.equals(currentDevice)) {
        targetDevice = device;
        break;
      }
    }
    return ExternalDisplay.onScreen(targetDevice).screenBounds();
  }

  static Rectangle preferredPopOutBounds(Rectangle displayBounds) {
    int margin =
        Math.min(24, Math.max(0, Math.min(displayBounds.width, displayBounds.height) / 20));
    int availableWidth = Math.max(1, displayBounds.width - margin * 2);
    int availableHeight = Math.max(1, displayBounds.height - margin * 2);
    int width = Math.min(600, availableWidth);
    int height = Math.min(1100, availableHeight);
    return new Rectangle(displayBounds.x + margin, displayBounds.y + margin, width, height);
  }

  private void updatePopOutButton() {
    boolean poppedOut = ExtendedMode.EXTERNALIZED.equals(dockable.getExtendedMode());
    dockable.setResizeLocked(!poppedOut);
    popOutButton.setText(poppedOut ? "Dock Back" : "Pop Out");
    popOutButton.setToolTipText(
        poppedOut
            ? "Return Report Composer to the Weasis window"
            : "Move Report Composer to another monitor");
  }

  private void installNativePopOutWindow() {
    if (!nativePopOutInstalled && dockable.getControl() != null) {
      nativePopOutInstalled =
          ComposerWindowSupport.installNativePopOut(
              dockable.getControl(), dockable.intern(), BUTTON_NAME);
    }
  }

  private Component buildComposeTab() {
    JPanel content = verticalPanel();
    content.add(fillWidth(buildExamSelector()));
    content.add(fillWidth(buildReportInstructions()));
    for (SpineRegion region : SpineRegion.values()) {
      SpineFormPanel form = new SpineFormPanel(region);
      form.setVisible(false);
      form.addSubmitListener(event -> saveSpineFindings(form));
      form.addOtherFindingListener(event -> showOtherStructuredFinding());
      spineForms.put(region, form);
      content.add(fillWidth(form));
    }
    shoulderForm.setVisible(false);
    shoulderForm.addSubmitListener(event -> saveShoulderFindings());
    shoulderForm.addOtherFindingListener(event -> showOtherStructuredFinding());
    content.add(fillWidth(shoulderForm));
    kneeForm.setVisible(false);
    kneeForm.addSubmitListener(event -> saveKneeFindings());
    kneeForm.addOtherFindingListener(event -> showOtherStructuredFinding());
    content.add(fillWidth(kneeForm));
    brainForm.setVisible(false);
    brainForm.addSubmitListener(event -> saveBrainFindings());
    brainForm.addOtherFindingListener(event -> showOtherStructuredFinding());
    content.add(fillWidth(brainForm));
    wristForm.setVisible(false);
    wristForm.addSubmitListener(event -> saveWristFindings());
    wristForm.addOtherFindingListener(event -> showOtherStructuredFinding());
    content.add(fillWidth(wristForm));
    genericFindingBuilder = buildFindingBuilder();
    content.add(fillWidth(genericFindingBuilder));
    content.add(fillWidth(buildFindingList()));
    return scroll(content);
  }

  private JPanel buildReportInstructions() {
    JPanel panel = new JPanel(new BorderLayout(4, 4));
    panel.setBorder(BorderFactory.createTitledBorder("Report text / instructions"));
    reportInstructions.setEnabled(false);
    reportInstructions
        .getDocument()
        .addDocumentListener(
            new DocumentListener() {
              @Override
              public void insertUpdate(DocumentEvent event) {
                reportInstructionsChanged();
              }

              @Override
              public void removeUpdate(DocumentEvent event) {
                reportInstructionsChanged();
              }

              @Override
              public void changedUpdate(DocumentEvent event) {
                reportInstructionsChanged();
              }
            });
    panel.add(new JScrollPane(reportInstructions), BorderLayout.CENTER);
    return panel;
  }

  private JPanel buildExamSelector() {
    JPanel panel = new JPanel(new BorderLayout(6, 3));
    panel.setBorder(BorderFactory.createTitledBorder("MRI exam"));
    panel.add(new JLabel("Exam"), BorderLayout.WEST);
    panel.add(examCombo, BorderLayout.CENTER);
    examCombo.addActionListener(event -> examTemplateChanged());
    return panel;
  }

  private JPanel buildFindingBuilder() {
    JPanel builder = new JPanel(new GridBagLayout());
    builder.setBorder(BorderFactory.createTitledBorder("Finding phrase"));
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = 0;
    constraints.anchor = GridBagConstraints.LINE_START;
    constraints.insets = new Insets(3, 4, 3, 4);
    builder.add(new JLabel("Category"), constraints);
    constraints.gridy++;
    builder.add(new JLabel("Structure"), constraints);
    constraints.gridy++;
    builder.add(new JLabel("Finding"), constraints);
    constraints.gridy++;
    constraints.anchor = GridBagConstraints.FIRST_LINE_START;
    builder.add(new JLabel("Text"), constraints);
    constraints.gridy++;
    builder.add(new JLabel("Impression"), constraints);

    constraints.gridx = 1;
    constraints.gridy = 0;
    constraints.weightx = 1.0;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    builder.add(categoryCombo, constraints);
    constraints.gridy++;
    builder.add(structureCombo, constraints);
    constraints.gridy++;
    builder.add(findingCombo, constraints);
    constraints.gridy++;
    constraints.fill = GridBagConstraints.BOTH;
    constraints.weighty = 0.5;
    builder.add(new JScrollPane(findingText), constraints);
    constraints.gridy++;
    builder.add(new JScrollPane(impressionText), constraints);
    constraints.gridy++;
    constraints.weighty = 0;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    builder.add(includeInImpression, constraints);

    categoryCombo.addActionListener(event -> updateCategoryControls());
    structureCombo.addActionListener(event -> applyCatalogPhrase());
    findingCombo.addActionListener(event -> applyCatalogPhrase());
    includeInImpression.addActionListener(
        event -> impressionText.setEnabled(includeInImpression.isSelected()));

    saveFindingButton.setIcon(ResourceUtil.getIcon(ActionIcon.PLUS));
    saveFindingButton.setToolTipText("Add finding to the current study");
    saveFindingButton.addActionListener(event -> saveFinding());
    backToStructuredFormButton.setIcon(ResourceUtil.getIcon(ActionIcon.PREVIOUS));
    backToStructuredFormButton.setVisible(false);
    backToStructuredFormButton.addActionListener(event -> showStructuredForm());
    cancelEditButton.setVisible(false);
    cancelEditButton.addActionListener(event -> cancelFindingEdit());
    JPanel buttons =
        GuiUtils.getFlowLayoutPanel(
            FlowLayout.TRAILING,
            6,
            4,
            backToStructuredFormButton,
            cancelEditButton,
            saveFindingButton);
    constraints.gridy++;
    builder.add(buttons, constraints);
    return builder;
  }

  private JPanel buildFindingList() {
    JPanel panel = new JPanel(new BorderLayout(4, 4));
    panel.setBorder(BorderFactory.createTitledBorder("Current findings"));
    findingList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    findingList.setVisibleRowCount(8);
    findingList.setCellRenderer(new TooltipListRenderer());
    findingList.addListSelectionListener(event -> refreshPreview());
    panel.add(new JScrollPane(findingList), BorderLayout.CENTER);

    JButton edit = iconButton(ActionIcon.DRAW_TEXT, "Edit selected finding");
    edit.addActionListener(event -> editSelectedFinding());
    JButton remove = iconButton(ActionIcon.SELECTION_DELETE, "Delete selected finding");
    remove.addActionListener(event -> removeSelectedFinding());
    JButton up = new JButton(GuiUtils.getUpArrowIcon());
    up.setToolTipText("Move finding up");
    up.addActionListener(event -> moveSelectedFinding(-1));
    JButton down = new JButton(GuiUtils.getDownArrowIcon());
    down.setToolTipText("Move finding down");
    down.addActionListener(event -> moveSelectedFinding(1));
    panel.add(
        GuiUtils.getFlowLayoutPanel(FlowLayout.TRAILING, 4, 2, edit, remove, up, down),
        BorderLayout.SOUTH);
    return panel;
  }

  private Component buildKeyImageTab() {
    JPanel content = new JPanel(new BorderLayout(5, 5));
    JPanel captureControls = verticalPanel();
    JPanel viewportSelector = new JPanel(new BorderLayout(5, 3));
    JLabel viewportLabel = new JLabel("Viewport");
    viewportLabel.setLabelFor(captureViewportCombo);
    viewportSelector.add(viewportLabel, BorderLayout.WEST);
    viewportSelector.add(captureViewportCombo, BorderLayout.CENTER);
    captureControls.add(fillWidth(viewportSelector));
    captureButton.setIcon(ResourceUtil.getIcon(ActionIcon.EDIT_KEY_IMAGE));
    captureButton.setEnabled(false);
    captureButton.addActionListener(event -> captureKeyImage());
    captureViewportCombo.setMaximumRowCount(4);
    captureViewportCombo.addActionListener(event -> updateCaptureViewportTooltip());
    captureControls.add(GuiUtils.getFlowLayoutPanel(FlowLayout.TRAILING, 0, 4, captureButton));
    content.add(captureControls, BorderLayout.NORTH);

    keyImageList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    keyImageList.setCellRenderer(new TooltipListRenderer());
    keyImageList.addListSelectionListener(
        event -> {
          KeyImageCapture selected = keyImageList.getSelectedValue();
          keyImageCaption.setText(selected == null ? "" : selected.caption());
        });
    content.add(new JScrollPane(keyImageList), BorderLayout.CENTER);

    JPanel captionPanel = new JPanel(new BorderLayout(4, 4));
    captionPanel.setBorder(BorderFactory.createTitledBorder("Arrow instruction"));
    captionPanel.add(new JScrollPane(keyImageCaption), BorderLayout.CENTER);
    JButton updateCaption = new JButton("Update Caption");
    updateCaption.addActionListener(event -> updateKeyImageCaption());
    JButton remove = iconButton(ActionIcon.SELECTION_DELETE, "Delete selected key image");
    remove.addActionListener(event -> removeSelectedKeyImage());
    captionPanel.add(
        GuiUtils.getFlowLayoutPanel(FlowLayout.TRAILING, 4, 2, remove, updateCaption),
        BorderLayout.SOUTH);
    content.add(captionPanel, BorderLayout.SOUTH);
    return content;
  }

  private Component buildPreviewTab() {
    JPanel content = new JPanel(new BorderLayout(5, 5));
    previewArea.setEditable(false);
    JScrollPane previewScroll = new JScrollPane(previewArea);
    previewScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    content.add(previewScroll, BorderLayout.CENTER);

    JPanel controls = verticalPanel();
    outputFolderLabel.setToolTipText(outputDirectory == null ? null : outputDirectory.toString());
    controls.add(outputFolderLabel);
    JButton chooseFolder = new JButton("Choose Transcription Folder");
    chooseFolder.setIcon(ResourceUtil.getIcon(ActionIcon.OPEN_EXTERNAL));
    chooseFolder.addActionListener(event -> chooseOutputDirectory());
    JButton copy = new JButton("Copy Report Text");
    copy.setIcon(ResourceUtil.getIcon(ActionIcon.EXPORT_CLIPBOARD));
    copy.addActionListener(event -> copyReportText());
    exportButton.setIcon(ResourceUtil.getIcon(ActionIcon.EXPORT_ANNOTATIONS));
    exportButton.addActionListener(event -> exportPacket());
    openExportButton.setEnabled(false);
    openExportButton.addActionListener(event -> openLastExport());
    controls.add(GuiUtils.getFlowLayoutPanel(FlowLayout.TRAILING, 4, 3, chooseFolder));
    controls.add(GuiUtils.getFlowLayoutPanel(FlowLayout.TRAILING, 4, 3, copy));
    controls.add(
        GuiUtils.getFlowLayoutPanel(FlowLayout.TRAILING, 4, 3, exportButton, openExportButton));
    controls.add(statusLabel);
    content.add(controls, BorderLayout.SOUTH);
    return content;
  }

  private void initializeCatalogControls() {
    updatingCatalogControls = true;
    for (ExamTemplate exam : MriFindingCatalog.exams()) {
      examCombo.addItem(exam);
    }
    examCombo.setSelectedItem(MriFindingCatalog.defaultExam());
    updatingCatalogControls = false;
    updateCatalogControls();
  }

  private void examTemplateChanged() {
    if (updatingCatalogControls) {
      return;
    }
    if (currentDraft != null && examCombo.getSelectedItem() instanceof ExamTemplate exam) {
      draftExamTemplates.put(currentDraft.context().draftKey(), exam);
    }
    structuredFormMode = selectedExamHasStructuredForm();
    updateCatalogControls();
  }

  private void updateCatalogControls() {
    if (updatingCatalogControls) {
      return;
    }
    ExamTemplate exam = selectedExamTemplate();
    updatingCatalogControls = true;
    categoryCombo.removeAllItems();
    for (String category : MriFindingCatalog.categories(exam)) {
      categoryCombo.addItem(category);
    }
    populateCategoryControls(exam, (String) categoryCombo.getSelectedItem());
    updatingCatalogControls = false;
    applyCatalogPhrase();
    updateFindingBuilderVisibility();
  }

  private void updateCategoryControls() {
    if (updatingCatalogControls) {
      return;
    }
    updatingCatalogControls = true;
    populateCategoryControls(selectedExamTemplate(), (String) categoryCombo.getSelectedItem());
    updatingCatalogControls = false;
    applyCatalogPhrase();
  }

  private void populateCategoryControls(ExamTemplate exam, String category) {
    structureCombo.removeAllItems();
    for (String structure : MriFindingCatalog.structures(exam, category)) {
      structureCombo.addItem(structure);
    }
    findingCombo.removeAllItems();
    for (FindingChoice finding : MriFindingCatalog.findings(exam, category)) {
      findingCombo.addItem(finding);
    }
  }

  private ExamTemplate selectedExamTemplate() {
    return examCombo.getSelectedItem() instanceof ExamTemplate exam
        ? exam
        : MriFindingCatalog.defaultExam();
  }

  private void selectExamTemplate(ExamTemplate exam) {
    updatingCatalogControls = true;
    examCombo.setSelectedItem(exam);
    updatingCatalogControls = false;
    structuredFormMode = hasStructuredForm(exam);
    updateCatalogControls();
  }

  private void updateFindingBuilderVisibility() {
    if (genericFindingBuilder == null) {
      return;
    }
    Optional<SpineRegion> selectedRegion = selectedSpineRegion();
    boolean showStructuredSpine =
        selectedRegion.isPresent() && structuredFormMode && editingFindingId == null;
    boolean showStructuredShoulder =
        selectedExamTemplate() == ExamTemplate.SHOULDER
            && structuredFormMode
            && editingFindingId == null;
    boolean showStructuredKnee =
        selectedExamTemplate() == ExamTemplate.KNEE
            && structuredFormMode
            && editingFindingId == null;
    boolean showStructuredBrain =
        selectedExamTemplate() == ExamTemplate.BRAIN
            && structuredFormMode
            && editingFindingId == null;
    boolean showStructuredWrist =
        selectedExamTemplate() == ExamTemplate.WRIST
            && structuredFormMode
            && editingFindingId == null;
    spineForms.forEach(
        (region, form) ->
            form.setVisible(showStructuredSpine && region == selectedRegion.orElse(null)));
    shoulderForm.setVisible(showStructuredShoulder);
    kneeForm.setVisible(showStructuredKnee);
    brainForm.setVisible(showStructuredBrain);
    wristForm.setVisible(showStructuredWrist);
    genericFindingBuilder.setVisible(
        !showStructuredSpine
            && !showStructuredShoulder
            && !showStructuredKnee
            && !showStructuredBrain
            && !showStructuredWrist);
    backToStructuredFormButton.setVisible(
        selectedExamHasStructuredForm()
            && !showStructuredSpine
            && !showStructuredShoulder
            && !showStructuredKnee
            && !showStructuredBrain
            && !showStructuredWrist
            && editingFindingId == null);
    if (selectedRegion.isPresent()) {
      backToStructuredFormButton.setText(selectedRegion.get().formLabel() + " Form");
    } else if (selectedExamTemplate() == ExamTemplate.SHOULDER) {
      backToStructuredFormButton.setText("Shoulder Form");
    } else if (selectedExamTemplate() == ExamTemplate.KNEE) {
      backToStructuredFormButton.setText("Knee Form");
    } else if (selectedExamTemplate() == ExamTemplate.BRAIN) {
      backToStructuredFormButton.setText("Brain Form");
    } else if (selectedExamTemplate() == ExamTemplate.WRIST) {
      backToStructuredFormButton.setText("Wrist Form");
    }
    revalidate();
    repaint();
  }

  private Optional<SpineRegion> selectedSpineRegion() {
    return SpineRegion.fromExam(selectedExamTemplate());
  }

  private boolean selectedExamHasStructuredForm() {
    return hasStructuredForm(selectedExamTemplate());
  }

  private static boolean hasStructuredForm(ExamTemplate exam) {
    return exam == ExamTemplate.SHOULDER
        || exam == ExamTemplate.KNEE
        || exam == ExamTemplate.BRAIN
        || exam == ExamTemplate.WRIST
        || SpineRegion.fromExam(exam).isPresent();
  }

  private void showOtherStructuredFinding() {
    structuredFormMode = false;
    updateFindingBuilderVisibility();
    applyCatalogPhrase();
  }

  private void showStructuredForm() {
    structuredFormMode = true;
    updateFindingBuilderVisibility();
  }

  private void applyCatalogPhrase() {
    if (editingFindingId != null) {
      return;
    }
    GeneratedFinding generated =
        MriFindingCatalog.generate(
            selectedExamTemplate(),
            (String) categoryCombo.getSelectedItem(),
            (String) structureCombo.getSelectedItem(),
            (FindingChoice) findingCombo.getSelectedItem());
    findingText.setText(generated.findingText());
    impressionText.setText(generated.impressionText());
    boolean include = !generated.impressionText().isBlank();
    includeInImpression.setSelected(include);
    impressionText.setEnabled(include);
  }

  private void synchronizeStudy() {
    synchronizeStudy(activeSelection());
    refreshCaptureViewports();
  }

  private Optional<Selection> activeSelection() {
    Optional<Selection> selection = DicomContextReader.fromVisible(lastInteractedCanvas);
    if (selection.isEmpty()) {
      selection = DicomContextReader.fromVisible(lastActiveCanvas);
    }
    if (selection.isEmpty()) {
      selection = DicomContextReader.selected();
    }
    if (selection.isEmpty()) {
      String preferredStudyUid =
          currentDraft == null ? "" : currentDraft.context().studyInstanceUid();
      selection = DicomContextReader.visibleStudy(preferredStudyUid);
    }
    return selection;
  }

  private void synchronizeStudy(Optional<Selection> selection) {
    if (selection.isEmpty() || !selection.get().context().hasStudy()) {
      boolean draftChanged = currentDraft != null;
      if (currentDraft != null) {
        spineFindingTracker.finalizeDraft(currentDraft);
        shoulderFindingTracker.finalizeDraft(currentDraft);
        kneeFindingTracker.finalizeDraft(currentDraft);
        brainFindingTracker.finalizeDraft(currentDraft);
        wristFindingTracker.finalizeDraft(currentDraft);
      }
      currentDraft = null;
      patientLabel.setText("No active DICOM study");
      examLabel.setText("Select an image to begin");
      accessionLabel.setText(" ");
      if (draftChanged) {
        refreshAll();
      }
      return;
    }

    CaseContext context = selection.get().context();
    ReportDraft selectedDraft =
        drafts.computeIfAbsent(context.draftKey(), ignored -> new ReportDraft(context));
    boolean draftChanged = currentDraft != selectedDraft;
    if (draftChanged && currentDraft != null) {
      spineFindingTracker.finalizeDraft(currentDraft);
      shoulderFindingTracker.finalizeDraft(currentDraft);
      kneeFindingTracker.finalizeDraft(currentDraft);
      brainFindingTracker.finalizeDraft(currentDraft);
      wristFindingTracker.finalizeDraft(currentDraft);
    }
    boolean contextChanged = !selectedDraft.context().equals(context);
    currentDraft = selectedDraft;
    if (contextChanged) {
      currentDraft.updateContext(context);
    }
    patientLabel.setText(context.patientDisplayName());
    examLabel.setText(context.examTitle());
    accessionLabel.setText(
        context.accessionNumber().isBlank()
            ? "Accession not available"
            : "Accession: " + context.accessionNumber());
    if (draftChanged) {
      spineForms.values().forEach(SpineFormPanel::clearSelections);
      shoulderForm.clearSelections();
      kneeForm.clearSelections();
      brainForm.clearSelections();
      wristForm.clearSelections();
      ExamTemplate template =
          draftExamTemplates.computeIfAbsent(
              context.draftKey(),
              ignored ->
                  MriFindingCatalog.inferExam(context.examTitle())
                      .orElse(MriFindingCatalog.defaultExam()));
      selectExamTemplate(template);
    }
    if (draftChanged || contextChanged) {
      refreshAll();
    }
  }

  private void saveFinding() {
    if (!requireActiveDraft()) {
      return;
    }
    try {
      if (editingFindingId == null) {
        FindingEntry finding =
            FindingEntry.create(
                findingText.getText(), impressionText.getText(), includeInImpression.isSelected());
        currentDraft.addFinding(finding);
        refreshAll();
        findingList.setSelectedValue(finding, true);
      } else {
        FindingEntry existing = selectedFindingById(editingFindingId);
        if (existing != null) {
          FindingEntry replacement =
              existing.withText(
                  findingText.getText(),
                  impressionText.getText(),
                  includeInImpression.isSelected());
          currentDraft.replaceFinding(editingFindingId, replacement);
        }
        cancelFindingEdit();
      }
    } catch (IllegalArgumentException error) {
      showWarning(error.getMessage());
    }
  }

  private void reportInstructionsChanged() {
    if (updatingReportInstructions || currentDraft == null) {
      return;
    }
    currentDraft.setReportInstructions(reportInstructions.getText());
    refreshPreview();
  }

  private void saveSpineFindings(SpineFormPanel form) {
    if (!requireActiveDraft()) {
      return;
    }
    synchronizeSpineFindings(form, true, true);
  }

  private void saveShoulderFindings() {
    if (!requireActiveDraft()) {
      return;
    }
    synchronizeShoulderFindings(true, true);
  }

  private void saveKneeFindings() {
    if (!requireActiveDraft()) {
      return;
    }
    synchronizeKneeFindings(true, true);
  }

  private void saveBrainFindings() {
    if (!requireActiveDraft()) {
      return;
    }
    synchronizeBrainFindings(true, true);
  }

  private void saveWristFindings() {
    if (!requireActiveDraft()) {
      return;
    }
    synchronizeWristFindings(true, true);
  }

  private void savePendingStructuredFindings() {
    Optional<SpineRegion> region = selectedSpineRegion();
    if (tabs.getSelectedIndex() == 0 || currentDraft == null || editingFindingId != null) {
      return;
    }
    if (region.isPresent()) {
      synchronizeSpineFindings(spineForms.get(region.get()), false, false);
    } else if (selectedExamTemplate() == ExamTemplate.SHOULDER) {
      synchronizeShoulderFindings(false, false);
    } else if (selectedExamTemplate() == ExamTemplate.KNEE) {
      synchronizeKneeFindings(false, false);
    } else if (selectedExamTemplate() == ExamTemplate.BRAIN) {
      synchronizeBrainFindings(false, false);
    } else if (selectedExamTemplate() == ExamTemplate.WRIST) {
      synchronizeWristFindings(false, false);
    }
  }

  private boolean synchronizeSpineFindings(
      SpineFormPanel form, boolean warnWhenEmpty, boolean clearAfterSave) {
    var result = spineFindingTracker.synchronize(currentDraft, form.selection());
    if (!result.hasFindings()) {
      if (warnWhenEmpty) {
        showWarning("Select at least one " + form.region().formLabel() + " finding.");
      }
      if (result.changed()) {
        refreshAll();
      }
      return false;
    }

    if (clearAfterSave) {
      spineFindingTracker.finalizeSelection(currentDraft, form.region());
      form.clearSelections();
    }
    if (result.changed()) {
      refreshAll();
    }
    if (result.lastFinding() != null) {
      findingList.setSelectedValue(result.lastFinding(), true);
    }
    return true;
  }

  private boolean synchronizeShoulderFindings(boolean warnWhenEmpty, boolean clearAfterSave) {
    var result = shoulderFindingTracker.synchronize(currentDraft, shoulderForm.selection());
    if (!result.hasFindings()) {
      if (warnWhenEmpty) {
        showWarning("Select at least one shoulder finding.");
      }
      if (result.changed()) {
        refreshAll();
      }
      return false;
    }

    if (clearAfterSave) {
      shoulderFindingTracker.finalizeSelection(currentDraft, ExamTemplate.SHOULDER);
      shoulderForm.clearSelections();
    }
    if (result.changed()) {
      refreshAll();
    }
    if (result.lastFinding() != null) {
      findingList.setSelectedValue(result.lastFinding(), true);
    }
    return true;
  }

  private boolean synchronizeKneeFindings(boolean warnWhenEmpty, boolean clearAfterSave) {
    var result = kneeFindingTracker.synchronize(currentDraft, kneeForm.selection());
    if (!result.hasFindings()) {
      if (warnWhenEmpty) {
        showWarning("Select at least one knee finding.");
      }
      if (result.changed()) {
        refreshAll();
      }
      return false;
    }

    if (clearAfterSave) {
      kneeFindingTracker.finalizeSelection(currentDraft, ExamTemplate.KNEE);
      kneeForm.clearSelections();
    }
    if (result.changed()) {
      refreshAll();
    }
    if (result.lastFinding() != null) {
      findingList.setSelectedValue(result.lastFinding(), true);
    }
    return true;
  }

  private boolean synchronizeBrainFindings(boolean warnWhenEmpty, boolean clearAfterSave) {
    var result = brainFindingTracker.synchronize(currentDraft, brainForm.selection());
    if (!result.hasFindings()) {
      if (warnWhenEmpty) {
        showWarning("Select at least one brain finding.");
      }
      if (result.changed()) {
        refreshAll();
      }
      return false;
    }

    if (clearAfterSave) {
      brainFindingTracker.finalizeSelection(currentDraft, ExamTemplate.BRAIN);
      brainForm.clearSelections();
    }
    if (result.changed()) {
      refreshAll();
    }
    if (result.lastFinding() != null) {
      findingList.setSelectedValue(result.lastFinding(), true);
    }
    return true;
  }

  private boolean synchronizeWristFindings(boolean warnWhenEmpty, boolean clearAfterSave) {
    var result = wristFindingTracker.synchronize(currentDraft, wristForm.selection());
    if (!result.hasFindings()) {
      if (warnWhenEmpty) {
        showWarning("Select at least one wrist finding.");
      }
      if (result.changed()) {
        refreshAll();
      }
      return false;
    }

    if (clearAfterSave) {
      wristFindingTracker.finalizeSelection(currentDraft, ExamTemplate.WRIST);
      wristForm.clearSelections();
    }
    if (result.changed()) {
      refreshAll();
    }
    if (result.lastFinding() != null) {
      findingList.setSelectedValue(result.lastFinding(), true);
    }
    return true;
  }

  static FindingEntry structuredSpineFinding(GeneratedFinding finding) {
    return StructuredFindingDraftTracker.createEntry(finding);
  }

  private void editSelectedFinding() {
    FindingEntry selected = findingList.getSelectedValue();
    if (selected == null) {
      return;
    }
    editingFindingId = selected.id();
    findingText.setText(selected.findingText());
    impressionText.setText(selected.impressionText());
    includeInImpression.setSelected(selected.includeInImpression());
    impressionText.setEnabled(selected.includeInImpression());
    saveFindingButton.setText("Update");
    saveFindingButton.setIcon(ResourceUtil.getIcon(ActionIcon.DRAW_TEXT));
    saveFindingButton.setToolTipText("Update the selected finding");
    cancelEditButton.setVisible(true);
    structuredFormMode = false;
    updateFindingBuilderVisibility();
  }

  private void cancelFindingEdit() {
    editingFindingId = null;
    saveFindingButton.setText("Add");
    saveFindingButton.setIcon(ResourceUtil.getIcon(ActionIcon.PLUS));
    saveFindingButton.setToolTipText("Add finding to the current study");
    cancelEditButton.setVisible(false);
    structuredFormMode = selectedExamHasStructuredForm();
    applyCatalogPhrase();
    updateFindingBuilderVisibility();
    refreshAll();
  }

  private void removeSelectedFinding() {
    FindingEntry selected = findingList.getSelectedValue();
    if (currentDraft != null && selected != null) {
      currentDraft.removeFinding(selected.id());
      if (selected.id().equals(editingFindingId)) {
        cancelFindingEdit();
      }
      refreshAll();
    }
  }

  private void moveSelectedFinding(int direction) {
    FindingEntry selected = findingList.getSelectedValue();
    if (currentDraft != null && selected != null) {
      currentDraft.moveFinding(selected.id(), direction);
      refreshAll();
      findingList.setSelectedValue(selected, true);
    }
  }

  private void captureKeyImage() {
    captureKeyImage(
        selectedCaptureSelection(),
        "Choose a viewport containing a DICOM image before capturing a key image.");
  }

  private void captureKeyImage(int viewportNumber) {
    captureKeyImage(
        captureSelectionForViewport(viewportNumber),
        "Viewport " + viewportNumber + " does not contain a DICOM image.");
  }

  private void captureKeyImage(Optional<Selection> selection, String missingSelectionMessage) {
    if (selection.isEmpty() || !selection.get().context().hasStudy()) {
      showWarning(missingSelectionMessage);
      return;
    }
    synchronizeStudy(selection);
    if (currentDraft == null) {
      return;
    }

    BufferedImage viewImage = selection.get().captureView();
    AnnotationResult annotation = ArrowAnnotationDialog.showDialog(this, viewImage);
    if (!annotation.accepted()) {
      return;
    }
    FindingEntry selectedFinding = findingList.getSelectedValue();
    if (selectedFinding == null && !currentDraft.findings().isEmpty()) {
      selectedFinding = currentDraft.findings().getLast();
    }
    String caption = selectedFinding == null ? "" : selectedFinding.findingText();
    KeyImageCapture keyImage =
        KeyImageCapture.createWithArrows(
            selection.get().reference(), viewImage, annotation.placements(), caption);
    currentDraft.addKeyImage(keyImage);
    refreshAll();
    keyImageList.setSelectedValue(keyImage, true);
    tabs.setSelectedIndex(1);
  }

  private Optional<Selection> captureSelectionForViewport(int viewportNumber) {
    refreshCaptureViewports();
    for (int index = 0; index < captureViewportCombo.getItemCount(); index++) {
      CaptureViewport viewport = captureViewportCombo.getItemAt(index);
      if (viewport.viewportNumber() == viewportNumber) {
        captureViewportCombo.setSelectedIndex(index);
        return DicomContextReader.fromVisible(viewport.canvas());
      }
    }
    return Optional.empty();
  }

  private Optional<Selection> selectedCaptureSelection() {
    CaptureViewport viewport =
        captureViewportCombo.getSelectedItem() instanceof CaptureViewport selected
            ? selected
            : null;
    if (viewport == null) {
      refreshCaptureViewports();
      if (!(captureViewportCombo.getSelectedItem() instanceof CaptureViewport refreshed)) {
        return Optional.empty();
      }
      viewport = refreshed;
    }
    return DicomContextReader.fromVisible(viewport.canvas());
  }

  private void refreshCaptureViewports() {
    DefaultView2d<DicomImageElement> previousCanvas =
        captureViewportCombo.getSelectedItem() instanceof CaptureViewport viewport
            ? viewport.canvas()
            : null;
    List<ViewportCanvas> viewports = DicomContextReader.visibleCanvases();
    List<DefaultView2d<DicomImageElement>> availableCanvases =
        viewports.stream().map(ViewportCanvas::canvas).toList();
    DefaultView2d<DicomImageElement> selectedCanvas =
        DicomContextReader.selected().map(Selection::canvas).orElse(null);
    DefaultView2d<DicomImageElement> preferredCanvas =
        preferredCaptureCanvas(
            availableCanvases,
            previousCanvas,
            lastInteractedCanvas,
            lastActiveCanvas,
            selectedCanvas);

    List<CaptureViewport> existingViewports = new ArrayList<>();
    for (int index = 0; index < captureViewportCombo.getItemCount(); index++) {
      existingViewports.add(captureViewportCombo.getItemAt(index));
    }
    if (sameViewportConfiguration(existingViewports, viewports)) {
      captureButton.setEnabled(!existingViewports.isEmpty());
      captureViewportCombo.repaint();
      updateCaptureViewportTooltip();
      return;
    }

    captureViewportCombo.removeAllItems();
    CaptureViewport preferred = null;
    for (ViewportCanvas viewport : viewports) {
      CaptureViewport choice = new CaptureViewport(viewport.viewportNumber(), viewport.canvas());
      captureViewportCombo.addItem(choice);
      if (choice.canvas() == preferredCanvas) {
        preferred = choice;
      }
    }
    if (preferred != null) {
      captureViewportCombo.setSelectedItem(preferred);
    }
    captureButton.setEnabled(captureViewportCombo.getItemCount() > 0);
    updateCaptureViewportTooltip();
  }

  static boolean sameViewportConfiguration(
      List<CaptureViewport> existingViewports, List<ViewportCanvas> visibleViewports) {
    if (existingViewports.size() != visibleViewports.size()) {
      return false;
    }
    for (int index = 0; index < existingViewports.size(); index++) {
      CaptureViewport existing = existingViewports.get(index);
      ViewportCanvas visible = visibleViewports.get(index);
      if (existing.viewportNumber() != visible.viewportNumber()
          || existing.canvas() != visible.canvas()) {
        return false;
      }
    }
    return true;
  }

  static DefaultView2d<DicomImageElement> preferredCaptureCanvas(
      List<DefaultView2d<DicomImageElement>> availableCanvases,
      DefaultView2d<DicomImageElement> previousCanvas,
      DefaultView2d<DicomImageElement> lastInteractedCanvas,
      DefaultView2d<DicomImageElement> lastActiveCanvas,
      DefaultView2d<DicomImageElement> selectedCanvas) {
    if (containsCanvas(availableCanvases, previousCanvas)) {
      return previousCanvas;
    }
    if (containsCanvas(availableCanvases, lastInteractedCanvas)) {
      return lastInteractedCanvas;
    }
    if (containsCanvas(availableCanvases, lastActiveCanvas)) {
      return lastActiveCanvas;
    }
    if (containsCanvas(availableCanvases, selectedCanvas)) {
      return selectedCanvas;
    }
    return availableCanvases.isEmpty() ? null : availableCanvases.getFirst();
  }

  private static boolean containsCanvas(
      List<DefaultView2d<DicomImageElement>> availableCanvases,
      DefaultView2d<DicomImageElement> candidate) {
    return candidate != null && availableCanvases.stream().anyMatch(canvas -> canvas == candidate);
  }

  private void updateCaptureViewportTooltip() {
    Object selected = captureViewportCombo.getSelectedItem();
    captureViewportCombo.setToolTipText(selected == null ? null : selected.toString());
  }

  private void updateKeyImageCaption() {
    KeyImageCapture selected = keyImageList.getSelectedValue();
    if (selected != null) {
      selected.setCaption(keyImageCaption.getText());
      refreshAll();
      keyImageList.setSelectedValue(selected, true);
    }
  }

  private void removeSelectedKeyImage() {
    KeyImageCapture selected = keyImageList.getSelectedValue();
    if (currentDraft != null && selected != null) {
      currentDraft.removeKeyImage(selected.id());
      refreshAll();
    }
  }

  private void copyReportText() {
    if (!requireActiveDraft()) {
      return;
    }
    String text = reportBody(currentDraft.snapshot());
    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    statusLabel.setText("Report text copied to the clipboard.");
  }

  private void chooseOutputDirectory() {
    String initial = outputDirectory == null ? "" : outputDirectory.toString();
    SystemFileChooser chooser = new SystemFileChooser(initial);
    chooser.setDialogTitle("Choose Google Drive transcription folder");
    chooser.setFileSelectionMode(SystemFileChooser.DIRECTORIES_ONLY);
    chooser.setMultiSelectionEnabled(false);
    if (chooser.showOpenDialog(this) == SystemFileChooser.APPROVE_OPTION) {
      File selected = chooser.getSelectedFile();
      if (selected != null) {
        outputDirectory = selected.toPath().toAbsolutePath().normalize();
        WProperties persistence = GuiUtils.getUICore().getLocalPersistence();
        persistence.setProperty(OUTPUT_DIRECTORY_KEY, outputDirectory.toString());
        updateOutputFolderLabel();
      }
    }
  }

  private void exportPacket() {
    if (!requireActiveDraft()) {
      return;
    }
    if (outputDirectory == null) {
      chooseOutputDirectory();
      if (outputDirectory == null) {
        return;
      }
    }
    ReportPacket packet = currentDraft.snapshot();
    ExamTemplate historyExam = selectedExamTemplate();
    Path destination = outputDirectory;
    exportButton.setEnabled(false);
    statusLabel.setText("Building transcription packet...");
    new SwingWorker<ExportResult, Void>() {
      @Override
      protected ExportResult doInBackground() throws Exception {
        ExportResult result = packetExporter.export(packet, destination);
        instructionHistory.record(historyExam, packet.reportInstructions());
        return result;
      }

      @Override
      protected void done() {
        exportButton.setEnabled(true);
        try {
          ExportResult result = get();
          lastExportDirectory = result.caseDirectory();
          openExportButton.setEnabled(true);
          statusLabel.setText(
              "Exported "
                  + result.keyImageCount()
                  + " key image"
                  + (result.keyImageCount() == 1 ? "" : "s")
                  + ".");
          ComposerDialogSupport.showMessage(
              ReportComposerTool.this,
              "Transcription packet saved to:\n" + result.caseDirectory(),
              BUTTON_NAME,
              JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception error) {
          Throwable cause = error.getCause() == null ? error : error.getCause();
          showWarning(
              cause.getMessage() == null
                  ? "The transcription packet could not be exported."
                  : cause.getMessage());
          statusLabel.setText("Export failed.");
        }
      }
    }.execute();
  }

  private void openLastExport() {
    if (lastExportDirectory == null || !Desktop.isDesktopSupported()) {
      return;
    }
    try {
      Desktop.getDesktop().open(lastExportDirectory.toFile());
    } catch (IOException error) {
      showWarning("The exported folder could not be opened.");
    }
  }

  private void refreshAll() {
    String selectedFindingId =
        findingList.getSelectedValue() == null ? null : findingList.getSelectedValue().id();
    String selectedKeyImageId =
        keyImageList.getSelectedValue() == null ? null : keyImageList.getSelectedValue().id();
    findingModel.clear();
    keyImageModel.clear();
    if (currentDraft != null) {
      currentDraft.findings().forEach(findingModel::addElement);
      currentDraft.keyImages().forEach(keyImageModel::addElement);
    }
    refreshReportInstructions();
    selectFinding(selectedFindingId);
    selectKeyImage(selectedKeyImageId);
    refreshPreview();
    exportButton.setEnabled(currentDraft != null);
  }

  private void refreshReportInstructions() {
    String value = currentDraft == null ? "" : currentDraft.reportInstructions();
    reportInstructions.setEnabled(currentDraft != null);
    if (reportInstructions.getText().equals(value)) {
      return;
    }
    updatingReportInstructions = true;
    try {
      reportInstructions.setText(value);
      reportInstructions.setCaretPosition(0);
    } finally {
      updatingReportInstructions = false;
    }
  }

  private void refreshPreview() {
    previewArea.setText(
        currentDraft == null
            ? "Select a DICOM study to begin."
            : ReportTextFormatter.format(currentDraft.snapshot()));
    previewArea.setCaretPosition(0);
  }

  private boolean requireActiveDraft() {
    if (currentDraft == null) {
      showWarning("Select a DICOM image before composing a report.");
      return false;
    }
    return true;
  }

  private FindingEntry selectedFindingById(String id) {
    if (currentDraft == null) {
      return null;
    }
    return currentDraft.findings().stream()
        .filter(finding -> finding.id().equals(id))
        .findFirst()
        .orElse(null);
  }

  private void selectFinding(String id) {
    if (id == null) {
      return;
    }
    for (int index = 0; index < findingModel.size(); index++) {
      if (findingModel.get(index).id().equals(id)) {
        findingList.setSelectedIndex(index);
        return;
      }
    }
  }

  private void selectKeyImage(String id) {
    if (id == null) {
      return;
    }
    for (int index = 0; index < keyImageModel.size(); index++) {
      if (keyImageModel.get(index).id().equals(id)) {
        keyImageList.setSelectedIndex(index);
        return;
      }
    }
  }

  private void initializeOutputDirectory() {
    String saved = GuiUtils.getUICore().getLocalPersistence().getProperty(OUTPUT_DIRECTORY_KEY, "");
    if (!saved.isBlank()) {
      outputDirectory = Path.of(saved).toAbsolutePath().normalize();
    }
  }

  private void updateOutputFolderLabel() {
    if (outputDirectory == null) {
      outputFolderLabel.setText("No transcription folder selected");
      outputFolderLabel.setToolTipText(null);
    } else {
      outputFolderLabel.setText("Destination: " + outputDirectory.getFileName());
      outputFolderLabel.setToolTipText(outputDirectory.toString());
    }
  }

  private static String reportBody(ReportPacket packet) {
    String reportText = packet.reportText();
    return "REPORT TEXT / INSTRUCTIONS\n"
        + (reportText.isBlank() ? "[No report text entered]" : reportText);
  }

  private void showWarning(String message) {
    ComposerDialogSupport.showMessage(this, message, BUTTON_NAME, JOptionPane.WARNING_MESSAGE);
  }

  private static JPanel verticalPanel() {
    return new WidthTrackingPanel();
  }

  private static <T extends JPanel> T fillWidth(T panel) {
    panel.setAlignmentX(Component.LEFT_ALIGNMENT);
    Dimension maximum = panel.getMaximumSize();
    panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, maximum.height));
    return panel;
  }

  private static JScrollPane scroll(Component component) {
    JScrollPane scrollPane = new JScrollPane(component);
    scrollPane.setBorder(BorderFactory.createEmptyBorder());
    scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.getVerticalScrollBar().setUnitIncrement(16);
    return scrollPane;
  }

  private static JTextArea textArea(int rows) {
    JTextArea area = new JTextArea(rows, FIELD_COLUMNS);
    area.setLineWrap(true);
    area.setWrapStyleWord(true);
    return area;
  }

  private static JButton iconButton(ActionIcon icon, String tooltip) {
    JButton button = new JButton(ResourceUtil.getIcon(icon));
    button.setToolTipText(tooltip);
    button.setPreferredSize(new Dimension(30, 28));
    return button;
  }

  private static final class WidthTrackingPanel extends JPanel implements Scrollable {
    WidthTrackingPanel() {
      setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS));
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
      return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
      return 16;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
      int extent = orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
      return Math.max(16, extent - 16);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
      return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
      return false;
    }
  }

  private static final class TooltipListRenderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(
        JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      JLabel component =
          (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      component.setToolTipText(value == null ? null : value.toString());
      return component;
    }
  }

  record CaptureViewport(int viewportNumber, DefaultView2d<DicomImageElement> canvas) {
    @Override
    public String toString() {
      String prefix = "Viewport " + viewportNumber;
      return DicomContextReader.currentReference(canvas)
          .map(reference -> prefix + " - " + reference.humanReference())
          .orElse(prefix + " - No image");
    }
  }
}
