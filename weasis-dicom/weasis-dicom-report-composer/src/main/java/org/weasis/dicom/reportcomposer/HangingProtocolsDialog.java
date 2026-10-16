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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.main.DicomExplorer;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.ExamTemplate;
import org.weasis.dicom.reportcomposer.ViewerMacros.SequenceOrder;

/**
 * Edits the hanging protocols in the macros file: for each, the exams it covers, the viewer layout
 * the next-study keys set, and the sequence order that fills the viewports and that the
 * next-sequence keys follow. A preview shows how the open study would be hung.
 */
final class HangingProtocolsDialog {
  static final List<String> LAYOUTS = List.of("1x1", "1x2", "2x1", "1x3", "1x4", "2x2");
  private static final String ANY = "Any";

  /** The study open in the viewer, for the preview. */
  record OpenStudy(Optional<ExamTemplate> exam, String description, List<String> series) {}

  // One protocol while it is edited.
  private static final class Draft {
    private String name;
    private final Set<String> exams = new LinkedHashSet<>();
    private String layout;
    private final List<String> order = new ArrayList<>();

    Draft(SequenceOrder protocol) {
      name = protocol.name();
      protocol.exams().forEach(exam -> exams.add(exam.toUpperCase(Locale.ROOT)));
      layout = protocol.layout();
      order.addAll(protocol.order());
    }

    SequenceOrder protocol() {
      return new SequenceOrder(name, List.copyOf(exams), layout, List.copyOf(order));
    }

    @Override
    public String toString() {
      return name.isBlank() ? "(unnamed)" : name;
    }
  }

  private final ViewerMacros macros;
  private final Optional<OpenStudy> openStudy;
  private final JDialog dialog;
  private final DefaultListModel<Draft> drafts = new DefaultListModel<>();
  private final JList<Draft> protocolList = new JList<>(drafts);
  private final JTextField nameField = new JTextField(18);
  private final Map<ExamTemplate, JCheckBox> examBoxes = new EnumMap<>(ExamTemplate.class);
  private final JComboBox<String> layoutCombo = new JComboBox<>(LAYOUTS.toArray(String[]::new));
  private final DefaultListModel<String> steps = new DefaultListModel<>();
  private final JList<String> stepList = new JList<>(steps);
  private final JComboBox<String> planeCombo = new JComboBox<>(withAny(SequenceStepper.PLANES));
  private final JComboBox<String> contrastCombo =
      new JComboBox<>(withAny(SequenceStepper.CONTRASTS));
  private final JTextArea preview = new JTextArea(7, 40);
  private final JPanel editor = new JPanel(new GridBagLayout());
  private boolean loading;

  private HangingProtocolsDialog(Component parent, ViewerMacros macros, Optional<OpenStudy> open) {
    this.macros = macros;
    this.openStudy = open;
    Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
    dialog = new JDialog(owner, "Hanging protocols", JDialog.ModalityType.APPLICATION_MODAL);
    macros.sequenceOrders().forEach(protocol -> drafts.addElement(new Draft(protocol)));
    dialog.setContentPane(content());
    dialog.pack();
    dialog.setLocationRelativeTo(owner);
    ComposerDialogSupport.keepInFront(dialog);
    protocolList.setSelectedIndex(drafts.isEmpty() ? -1 : 0);
    showSelected();
  }

  /** Opens the editor for the session's macros, previewing the study open in the viewer. */
  static void show(Component parent) {
    ViewerMacros.current()
        .ifPresentOrElse(
            macros ->
                new HangingProtocolsDialog(parent, macros, openStudy()).dialog.setVisible(true),
            () ->
                ComposerDialogSupport.showMessage(
                    parent,
                    "Open a study in the viewer first.",
                    "Hanging protocols",
                    JOptionPane.INFORMATION_MESSAGE));
  }

  private static Optional<OpenStudy> openStudy() {
    DataExplorerView view = GuiUtils.getUICore().getExplorerPlugin(DicomExplorer.NAME);
    if (!(view instanceof DicomExplorer explorer)) return Optional.empty();
    DicomModel model = explorer.getDataExplorerModel();
    return SequenceStepper.studyBeingRead()
        .map(
            study ->
                new OpenStudy(
                    SequenceStepper.examOf(study),
                    Objects.toString(TagD.getTagValue(study, Tag.StudyDescription), ""),
                    SequenceStepper.imageSeries(model, study).stream()
                        .map(SequenceStepper::description)
                        .toList()));
  }

  /** How the open study would be hung with these protocols, one line per viewport and then. */
  static String preview(OpenStudy study, List<SequenceOrder> protocols) {
    Optional<SequenceOrder> protocol =
        study.exam().flatMap(exam -> protocols.stream().filter(p -> p.isFor(exam)).findFirst());
    SequenceOrder hanging = protocol.orElse(SequenceOrder.BY_SERIES_NUMBER);
    String exam = study.exam().map(ExamTemplate::toString).orElse("exam not recognized");
    StringBuilder text =
        new StringBuilder("Open study: ")
            .append(study.description())
            .append(" (")
            .append(exam)
            .append(")\n")
            .append(
                protocol
                    .map(p -> "Hung by \"" + p.name() + "\" in " + p.layout())
                    .orElse("No protocol covers it: series number order in 1x2"))
            .append('\n');
    List<String> ordered =
        SequenceStepper.ordered(study.series(), Function.identity(), hanging.order());
    int viewports = viewportCount(hanging.layout());
    for (int i = 0; i < ordered.size(); i++) {
      text.append(i < viewports ? "  Viewport " + (i + 1) + ": " : "  Then: ")
          .append(ordered.get(i));
      text.append('\n');
    }
    return text.toString().stripTrailing();
  }

  static int viewportCount(String layout) {
    String[] parts = layout.split("x");
    try {
      return Integer.parseInt(parts[0]) * Integer.parseInt(parts[1]);
    } catch (RuntimeException e) {
      return 2;
    }
  }

  private static String[] withAny(List<String> values) {
    List<String> items = new ArrayList<>(List.of(ANY));
    items.addAll(values);
    return items.toArray(String[]::new);
  }

  private JPanel content() {
    protocolList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    protocolList.addListSelectionListener(
        event -> {
          if (!event.getValueIsAdjusting()) showSelected();
        });
    JButton add = new JButton("Add");
    add.addActionListener(event -> addProtocol());
    JButton remove = new JButton("Remove");
    remove.addActionListener(event -> removeProtocol());
    JPanel listButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    listButtons.add(add);
    listButtons.add(remove);
    JPanel left = new JPanel(new BorderLayout(0, 4));
    left.add(new JLabel("Protocols (the first covering an exam is used)"), BorderLayout.NORTH);
    left.add(new JScrollPane(protocolList), BorderLayout.CENTER);
    left.add(listButtons, BorderLayout.SOUTH);

    buildEditor();
    preview.setEditable(false);
    preview.setLineWrap(true);
    preview.setWrapStyleWord(true);
    JPanel bottom = new JPanel(new BorderLayout(0, 4));
    bottom.add(new JScrollPane(preview), BorderLayout.CENTER);
    JButton cancel = new JButton("Cancel");
    cancel.addActionListener(event -> dialog.dispose());
    JButton save = new JButton("Save");
    save.addActionListener(event -> save());
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
    buttons.add(cancel);
    buttons.add(save);
    bottom.add(buttons, BorderLayout.SOUTH);

    JPanel content = new JPanel(new BorderLayout(10, 10));
    content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    content.add(left, BorderLayout.WEST);
    content.add(editor, BorderLayout.CENTER);
    content.add(bottom, BorderLayout.SOUTH);
    dialog.getRootPane().setDefaultButton(save);
    return content;
  }

  private void buildEditor() {
    nameField
        .getDocument()
        .addDocumentListener(
            new DocumentListener() {
              @Override
              public void insertUpdate(DocumentEvent e) {
                nameChanged();
              }

              @Override
              public void removeUpdate(DocumentEvent e) {
                nameChanged();
              }

              @Override
              public void changedUpdate(DocumentEvent e) {
                nameChanged();
              }
            });
    JPanel exams = new JPanel(new GridLayout(0, 3, 6, 2));
    for (ExamTemplate exam : ExamTemplate.values()) {
      JCheckBox box = new JCheckBox(exam.toString());
      box.addActionListener(event -> examToggled(exam, box.isSelected()));
      examBoxes.put(exam, box);
      exams.add(box);
    }
    layoutCombo.setEditable(false);
    layoutCombo.addActionListener(
        event -> selected().ifPresent(draft -> edit(() -> draft.layout = selectedLayout())));
    stepList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    stepList.setVisibleRowCount(7);
    contrastCombo.setToolTipText(
        "STIR also matches TIRM, IR, and fat-suppressed T2 or PD sequences");
    JButton addStep = new JButton("Add step");
    addStep.addActionListener(event -> addStep());
    JButton up = new JButton("Up");
    up.addActionListener(event -> moveStep(-1));
    JButton down = new JButton("Down");
    down.addActionListener(event -> moveStep(1));
    JButton removeStep = new JButton("Remove step");
    removeStep.addActionListener(event -> removeStep());
    JPanel stepButtons = new JPanel(new GridLayout(0, 1, 0, 4));
    stepButtons.add(up);
    stepButtons.add(down);
    stepButtons.add(removeStep);
    JPanel stepsPanel = new JPanel(new BorderLayout(6, 0));
    stepsPanel.add(new JScrollPane(stepList), BorderLayout.CENTER);
    stepsPanel.add(stepButtons, BorderLayout.EAST);
    JPanel newStep = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    newStep.add(new JLabel("Plane"));
    newStep.add(planeCombo);
    newStep.add(new JLabel("Contrast"));
    newStep.add(contrastCombo);
    newStep.add(addStep);

    row(0, "Name", nameField);
    row(1, "Exams", exams);
    row(2, "Layout", layoutCombo);
    row(3, "Sequence order", stepsPanel);
    row(4, "", newStep);
  }

  private void row(int row, String label, JComponent field) {
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridy = row;
    constraints.insets = new Insets(2, 0, 4, 8);
    constraints.anchor = GridBagConstraints.NORTHWEST;
    editor.add(new JLabel(label), constraints);
    constraints.gridx = 1;
    constraints.weightx = 1;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    editor.add(field, constraints);
  }

  private Optional<Draft> selected() {
    return Optional.ofNullable(protocolList.getSelectedValue());
  }

  // Applies an edit made in the controls, unless the controls are being filled in.
  private void edit(Runnable change) {
    if (loading) return;
    change.run();
    protocolList.repaint();
    refreshPreview();
  }

  private void showSelected() {
    loading = true;
    try {
      Optional<Draft> draft = selected();
      setEditorEnabled(draft.isPresent());
      nameField.setText(draft.map(value -> value.name).orElse(""));
      examBoxes.forEach(
          (exam, box) ->
              box.setSelected(
                  draft.filter(value -> value.exams.contains(exam.name())).isPresent()));
      String layout = draft.map(value -> value.layout).orElse(SequenceOrder.DEFAULT_LAYOUT);
      if (((DefaultComboBoxModel<String>) layoutCombo.getModel()).getIndexOf(layout) < 0) {
        layoutCombo.addItem(layout);
      }
      layoutCombo.setSelectedItem(layout);
      steps.clear();
      draft.ifPresent(value -> value.order.forEach(steps::addElement));
    } finally {
      loading = false;
    }
    refreshPreview();
  }

  private void setEditorEnabled(boolean enabled) {
    for (Component component : editor.getComponents()) setEnabledDeep(component, enabled);
  }

  private static void setEnabledDeep(Component component, boolean enabled) {
    component.setEnabled(enabled);
    if (component instanceof Container container) {
      for (Component child : container.getComponents()) setEnabledDeep(child, enabled);
    }
  }

  private String selectedLayout() {
    return String.valueOf(layoutCombo.getSelectedItem());
  }

  private void nameChanged() {
    selected().ifPresent(draft -> edit(() -> draft.name = nameField.getText().strip()));
  }

  // An exam belongs to one protocol: checking it here takes it from any other.
  private void examToggled(ExamTemplate exam, boolean checked) {
    selected()
        .ifPresent(
            draft ->
                edit(
                    () -> {
                      if (checked) {
                        for (int i = 0; i < drafts.size(); i++)
                          drafts.get(i).exams.remove(exam.name());
                        draft.exams.add(exam.name());
                      } else {
                        draft.exams.remove(exam.name());
                      }
                    }));
  }

  private void addProtocol() {
    Draft draft = new Draft(new SequenceOrder("New protocol", List.of(), null, List.of()));
    drafts.addElement(draft);
    protocolList.setSelectedValue(draft, true);
    nameField.requestFocusInWindow();
    nameField.selectAll();
  }

  private void removeProtocol() {
    int index = protocolList.getSelectedIndex();
    if (index < 0) return;
    drafts.remove(index);
    protocolList.setSelectedIndex(Math.min(index, drafts.size() - 1));
    refreshPreview();
  }

  private void addStep() {
    String plane = String.valueOf(planeCombo.getSelectedItem());
    String contrast = String.valueOf(contrastCombo.getSelectedItem());
    String step =
        String.join(" ", ANY.equals(plane) ? "" : plane, ANY.equals(contrast) ? "" : contrast)
            .strip();
    if (step.isEmpty()) {
      ComposerDialogSupport.showMessage(
          dialog, "Choose a plane, a contrast, or both.", "Add step", JOptionPane.WARNING_MESSAGE);
      return;
    }
    selected()
        .ifPresent(
            draft ->
                edit(
                    () -> {
                      draft.order.add(step);
                      steps.addElement(step);
                      stepList.setSelectedIndex(steps.size() - 1);
                    }));
  }

  private void moveStep(int direction) {
    int index = stepList.getSelectedIndex();
    int target = index + direction;
    if (index < 0 || target < 0 || target >= steps.size()) return;
    selected()
        .ifPresent(
            draft ->
                edit(
                    () -> {
                      String step = draft.order.remove(index);
                      draft.order.add(target, step);
                      steps.remove(index);
                      steps.add(target, step);
                      stepList.setSelectedIndex(target);
                    }));
  }

  private void removeStep() {
    int index = stepList.getSelectedIndex();
    if (index < 0) return;
    selected()
        .ifPresent(
            draft ->
                edit(
                    () -> {
                      draft.order.remove(index);
                      steps.remove(index);
                      stepList.setSelectedIndex(Math.min(index, steps.size() - 1));
                    }));
  }

  private List<SequenceOrder> protocols() {
    List<SequenceOrder> protocols = new ArrayList<>();
    for (int i = 0; i < drafts.size(); i++) protocols.add(drafts.get(i).protocol());
    return protocols;
  }

  private void refreshPreview() {
    preview.setText(
        openStudy
            .map(study -> preview(study, protocols()))
            .orElse("Open a study in the viewer to preview how it would be hung."));
    preview.setCaretPosition(0);
  }

  private void save() {
    try {
      macros.saveSequenceOrders(protocols());
      dialog.dispose();
    } catch (IOException | RuntimeException e) {
      ComposerDialogSupport.showMessage(
          dialog,
          "The protocols could not be saved: " + e.getMessage(),
          "Hanging protocols",
          JOptionPane.ERROR_MESSAGE);
    }
  }
}
