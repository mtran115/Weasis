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
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
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
import javax.swing.SwingWorker;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.dicom.reportcomposer.ArrowAnnotationDialog.AnnotationResult;
import org.weasis.dicom.reportcomposer.CasePacketExporter.ExportResult;
import org.weasis.dicom.reportcomposer.DicomContextReader.Selection;
import org.weasis.dicom.reportcomposer.WristFindingCatalog.FindingChoice;
import org.weasis.dicom.reportcomposer.WristFindingCatalog.GeneratedFinding;

public class ReportComposerTool extends PluginTool implements SeriesViewerListener {
  public static final String BUTTON_NAME = "Report Composer";

  private static final String OUTPUT_DIRECTORY_KEY = "report.composer.output.directory";
  private static final int FIELD_COLUMNS = 28;

  private final Map<String, ReportDraft> drafts = new LinkedHashMap<>();
  private final CasePacketExporter packetExporter = new CasePacketExporter();
  private final JTabbedPane tabs = new JTabbedPane();
  private final JLabel patientLabel = new JLabel("No active DICOM study");
  private final JLabel examLabel = new JLabel("Select an image to begin");
  private final JLabel accessionLabel = new JLabel(" ");
  private final JComboBox<String> categoryCombo = new JComboBox<>();
  private final JComboBox<String> structureCombo = new JComboBox<>();
  private final JComboBox<FindingChoice> findingCombo = new JComboBox<>();
  private final JTextArea findingText = textArea(3);
  private final JTextArea impressionText = textArea(2);
  private final JCheckBox includeInImpression = new JCheckBox("Include in impression", true);
  private final JButton saveFindingButton = new JButton("Add");
  private final JButton cancelEditButton = new JButton("Cancel Edit");
  private final DefaultListModel<FindingEntry> findingModel = new DefaultListModel<>();
  private final JList<FindingEntry> findingList = new JList<>(findingModel);
  private final DefaultListModel<KeyImageCapture> keyImageModel = new DefaultListModel<>();
  private final JList<KeyImageCapture> keyImageList = new JList<>(keyImageModel);
  private final JTextArea keyImageCaption = textArea(2);
  private final JTextArea previewArea = textArea(24);
  private final JLabel outputFolderLabel = new JLabel("No transcription folder selected");
  private final JLabel statusLabel = new JLabel(" ");
  private final JButton exportButton = new JButton("Export Instruction Packet");
  private final JButton openExportButton =
      iconButton(ActionIcon.OPEN_EXTERNAL, "Open exported case folder");

  private ReportDraft currentDraft;
  private String editingFindingId;
  private Path outputDirectory;
  private Path lastExportDirectory;

  public ReportComposerTool() {
    super(BUTTON_NAME, POSITION.EAST, ExtendedMode.NORMALIZED, Insertable.Type.TOOL_EXT, 145);
    dockable.setTitleIcon(ResourceUtil.getIcon(OtherIcon.KEY_IMAGE));
    setDockableWidth(390);
    initializeOutputDirectory();
    buildInterface();
    updateOutputFolderLabel();
    updateCatalogControls();
    GuiExecutor.execute(this::synchronizeStudy);
  }

  @Override
  public Component getToolComponent() {
    return this;
  }

  @Override
  public void changingViewContentEvent(SeriesViewerEvent event) {
    EVENT type = event.getEventType();
    if (EVENT.SELECT.equals(type) || EVENT.SELECT_VIEW.equals(type) || EVENT.LAYOUT.equals(type)) {
      GuiExecutor.execute(this::synchronizeStudy);
    }
  }

  @Override
  protected void changeToolWindowAnchor(CLocation clocation) {
    // The composer remains usable at any docking location.
  }

  private void buildInterface() {
    setLayout(new BorderLayout(0, 6));
    setBorder(GuiUtils.getEmptyBorder(8, 8, 8, 8));
    add(buildCaseHeader(), BorderLayout.NORTH);
    tabs.addTab("Compose", buildComposeTab());
    tabs.addTab("Key Images", buildKeyImageTab());
    tabs.addTab("Preview", buildPreviewTab());
    add(tabs, BorderLayout.CENTER);
  }

  private JPanel buildCaseHeader() {
    JPanel header = new JPanel();
    header.setLayout(new javax.swing.BoxLayout(header, javax.swing.BoxLayout.Y_AXIS));
    patientLabel.setFont(patientLabel.getFont().deriveFont(Font.BOLD, 14f));
    examLabel.setFont(examLabel.getFont().deriveFont(Font.PLAIN, 12f));
    accessionLabel.setForeground(
        GuiUtils.getUICore()
            .getSystemPreferences()
            .getColorProperty("Label.disabledForeground", Color.GRAY));
    header.add(patientLabel);
    header.add(examLabel);
    header.add(accessionLabel);
    header.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Color.GRAY),
            GuiUtils.getEmptyBorder(0, 2, 8, 2)));
    return header;
  }

  private Component buildComposeTab() {
    JPanel content = verticalPanel();
    content.add(buildFindingBuilder());
    content.add(buildFindingList());
    return scroll(content);
  }

  private JPanel buildFindingBuilder() {
    JPanel builder = new JPanel(new GridBagLayout());
    builder.setBorder(BorderFactory.createTitledBorder("Wrist MRI finding"));
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

    categoryCombo.addActionListener(event -> updateCatalogControls());
    structureCombo.addActionListener(event -> applyCatalogPhrase());
    findingCombo.addActionListener(event -> applyCatalogPhrase());
    includeInImpression.addActionListener(
        event -> impressionText.setEnabled(includeInImpression.isSelected()));

    saveFindingButton.setIcon(ResourceUtil.getIcon(ActionIcon.PLUS));
    saveFindingButton.setToolTipText("Add finding to the current study");
    saveFindingButton.addActionListener(event -> saveFinding());
    cancelEditButton.setVisible(false);
    cancelEditButton.addActionListener(event -> cancelFindingEdit());
    JPanel buttons =
        GuiUtils.getFlowLayoutPanel(FlowLayout.TRAILING, 6, 4, cancelEditButton, saveFindingButton);
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
    JButton capture = new JButton("Capture Current View");
    capture.setIcon(ResourceUtil.getIcon(ActionIcon.EDIT_KEY_IMAGE));
    capture.addActionListener(event -> captureKeyImage());
    content.add(GuiUtils.getFlowLayoutPanel(FlowLayout.LEADING, 0, 4, capture), BorderLayout.NORTH);

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

  private void updateCatalogControls() {
    String selectedCategory = (String) categoryCombo.getSelectedItem();
    if (categoryCombo.getItemCount() == 0) {
      for (String category : WristFindingCatalog.categories()) {
        categoryCombo.addItem(category);
      }
      selectedCategory = (String) categoryCombo.getSelectedItem();
    }
    structureCombo.removeAllItems();
    for (String structure : WristFindingCatalog.structures(selectedCategory)) {
      structureCombo.addItem(structure);
    }
    findingCombo.removeAllItems();
    for (FindingChoice finding : WristFindingCatalog.findings(selectedCategory)) {
      findingCombo.addItem(finding);
    }
    applyCatalogPhrase();
  }

  private void applyCatalogPhrase() {
    if (editingFindingId != null) {
      return;
    }
    GeneratedFinding generated =
        WristFindingCatalog.generate(
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
    Optional<Selection> selection = DicomContextReader.selected();
    if (selection.isEmpty() || !selection.get().context().hasStudy()) {
      boolean draftChanged = currentDraft != null;
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
  }

  private void cancelFindingEdit() {
    editingFindingId = null;
    saveFindingButton.setText("Add");
    saveFindingButton.setIcon(ResourceUtil.getIcon(ActionIcon.PLUS));
    saveFindingButton.setToolTipText("Add finding to the current study");
    cancelEditButton.setVisible(false);
    applyCatalogPhrase();
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
    Optional<Selection> selection = DicomContextReader.selected();
    if (selection.isEmpty() || !selection.get().context().hasStudy()) {
      showWarning("Select a DICOM image before capturing a key image.");
      return;
    }
    synchronizeStudy();
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
        KeyImageCapture.create(
            selection.get().reference(), viewImage, annotation.placement(), caption);
    currentDraft.addKeyImage(keyImage);
    refreshAll();
    keyImageList.setSelectedValue(keyImage, true);
    tabs.setSelectedIndex(1);
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
    Path destination = outputDirectory;
    exportButton.setEnabled(false);
    statusLabel.setText("Building transcription packet...");
    new SwingWorker<ExportResult, Void>() {
      @Override
      protected ExportResult doInBackground() throws Exception {
        return packetExporter.export(packet, destination);
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
          JOptionPane.showMessageDialog(
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
    selectFinding(selectedFindingId);
    selectKeyImage(selectedKeyImageId);
    refreshPreview();
    exportButton.setEnabled(currentDraft != null);
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
    StringBuilder text = new StringBuilder("FINDINGS\n");
    text.append(packet.findingsText().isBlank() ? "[No findings entered]" : packet.findingsText());
    text.append("\n\nIMPRESSION\n");
    text.append(
        packet.impressionText().isBlank() ? "[No impression entered]" : packet.impressionText());
    return text.toString();
  }

  private void showWarning(String message) {
    JOptionPane.showMessageDialog(this, message, BUTTON_NAME, JOptionPane.WARNING_MESSAGE);
  }

  private static JPanel verticalPanel() {
    return new WidthTrackingPanel();
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
}
