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
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.ExamTemplate;
import org.weasis.dicom.reportcomposer.ShortcutLibrary.Shortcut;

/** Library dialogs edit reusable wording only; they never change the source study text. */
final class ShortcutDialogs {
  private static final String ALL_EXAMS = "All exams";
  private static final String ALL_PARTS = "All body parts";

  private ShortcutDialogs() {}

  static void edit(
      Component parent,
      ShortcutLibrary library,
      ExamTemplate exam,
      Shortcut existing,
      String selectedText,
      Runnable changed) {
    JDialog dialog = dialog(parent, existing == null ? "Add shortcut" : "Edit shortcut");
    JTextField label = new JTextField(32);
    JTextArea text = new JTextArea(7, 42);
    text.setLineWrap(true);
    text.setWrapStyleWord(true);
    JComboBox<Object> scope = scopes(false);
    JCheckBox pinned = new JCheckBox("Pin near the top");
    String[] editingId = {existing == null ? null : existing.id()};
    if (existing == null) {
      String initial = selectedText == null ? "" : selectedText;
      text.setText(initial);
      String suggestion = initial.strip().replaceAll("\\s+", " ");
      label.setText(suggestion.substring(0, Math.min(30, suggestion.length())));
      scope.setSelectedItem(exam);
      pinned.setSelected(true);
    } else {
      fill(existing, label, text, scope, pinned);
    }
    JPanel fields = new JPanel(new GridLayout(0, 1, 0, 4));
    fields.add(new JLabel("Button label"));
    fields.add(label);
    fields.add(new JLabel("MRI body part"));
    fields.add(scope);
    fields.add(pinned);
    JPanel center = new JPanel(new BorderLayout(0, 5));
    center.add(
        new JLabel("Shortcut text · keep shorthand or use [level], [side], [size]"),
        BorderLayout.NORTH);
    center.add(new JScrollPane(text), BorderLayout.CENTER);
    JLabel feedback = new JLabel("Saving leaves the selected study text unchanged.");
    JButton save = new JButton("Save");
    save.addActionListener(
        event -> {
          String selectedScope = scopeKey(scope.getSelectedItem());
          var duplicate = library.duplicate(selectedScope, text.getText(), editingId[0]);
          if (duplicate.isPresent()) {
            if (confirm(
                dialog,
                "This text already exists as “"
                    + duplicate.get().label()
                    + "”. Open that shortcut to edit it?",
                "Existing shortcut")) {
              Shortcut match = duplicate.get();
              editingId[0] = match.id();
              fill(match, label, text, scope, pinned);
              dialog.setTitle("Edit shortcut");
              feedback.setText("Editing the existing shortcut; its usage counts are retained.");
            }
            return;
          }
          try {
            library.save(
                editingId[0], selectedScope, label.getText(), text.getText(), pinned.isSelected());
            changed.run();
            dialog.dispose();
          } catch (IllegalArgumentException | IllegalStateException error) {
            ComposerDialogSupport.showMessage(
                dialog,
                error.getMessage(),
                "Shortcut could not be saved",
                JOptionPane.WARNING_MESSAGE);
          }
        });
    JButton cancel = new JButton("Cancel");
    cancel.addActionListener(event -> dialog.dispose());
    JPanel footer = new JPanel(new BorderLayout());
    footer.add(feedback, BorderLayout.CENTER);
    footer.add(row(cancel, save), BorderLayout.SOUTH);
    JPanel content = content();
    content.add(fields, BorderLayout.NORTH);
    content.add(center, BorderLayout.CENTER);
    content.add(footer, BorderLayout.SOUTH);
    dialog.setContentPane(content);
    dialog.getRootPane().setDefaultButton(save);
    show(dialog, parent);
  }

  static void manage(
      Component parent, ShortcutLibrary library, ExamTemplate exam, Runnable changed) {
    JDialog dialog = dialog(parent, "Manage shortcuts");
    JTextField search = new JTextField();
    search.putClientProperty("JTextField.placeholderText", "Search labels and text");
    JComboBox<Object> filter = scopes(true);
    filter.setSelectedItem(exam);
    ShortcutTable model = new ShortcutTable();
    JTable table = new JTable(model);
    table.setAutoCreateRowSorter(true);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.setRowHeight(Math.max(24, table.getRowHeight()));
    table.getColumnModel().getColumn(0).setPreferredWidth(155);
    table.getColumnModel().getColumn(1).setPreferredWidth(130);
    table.getColumnModel().getColumn(2).setPreferredWidth(50);
    table.getColumnModel().getColumn(3).setPreferredWidth(50);
    table.getColumnModel().getColumn(4).setPreferredWidth(320);
    JTextArea preview = new JTextArea(4, 40);
    preview.setEditable(false);
    preview.setLineWrap(true);
    preview.setWrapStyleWord(true);
    JLabel status =
        new JLabel("Counts are for the selected body part; broader filters show totals.");
    JButton edit = new JButton("Edit…");
    JButton remove = new JButton("Delete");
    JButton add = new JButton("New…");
    JButton export = new JButton("Export…");
    JButton imports = new JButton("Import…");
    JButton close = new JButton("Close");
    List<JButton> actions = List.of(edit, remove, add, export, imports, close);
    Runnable reload =
        () -> {
          String selectedId = selected(table, model) == null ? null : selected(table, model).id();
          Object category = filter.getSelectedItem();
          model.exam = category instanceof ExamTemplate e ? e : null;
          String query = search.getText().strip().toLowerCase(Locale.ROOT);
          model.rows =
              library.all().stream()
                  .filter(
                      entry ->
                          ALL_PARTS.equals(category)
                              || (ALL_EXAMS.equals(category)
                                  ? ShortcutLibrary.ALL.equals(entry.scope())
                                  : entry.appliesTo((ExamTemplate) category)))
                  .filter(
                      entry ->
                          (entry.label() + " " + entry.text())
                              .toLowerCase(Locale.ROOT)
                              .contains(query))
                  .toList();
          model.fireTableDataChanged();
          for (int i = 0; i < model.rows.size(); i++) {
            if (model.rows.get(i).id().equals(selectedId)) {
              int view = table.convertRowIndexToView(i);
              table.setRowSelectionInterval(view, view);
              break;
            }
          }
        };
    Runnable refresh =
        () -> {
          changed.run();
          reload.run();
        };
    Runnable editSelected =
        () -> {
          Shortcut entry = selected(table, model);
          if (entry != null) edit(dialog, library, exam, entry, null, refresh);
        };
    table
        .getSelectionModel()
        .addListSelectionListener(
            event -> {
              Shortcut entry = selected(table, model);
              edit.setEnabled(entry != null);
              remove.setEnabled(entry != null);
              preview.setText(
                  entry == null ? "Select a shortcut to see its full text." : entry.text());
              preview.setCaretPosition(0);
            });
    table.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent event) {
            if (table.isEnabled()
                && event.getClickCount() == 2
                && SwingUtilities.isLeftMouseButton(event)) editSelected.run();
          }
        });
    edit.addActionListener(event -> editSelected.run());
    add.addActionListener(
        event ->
            edit(
                dialog,
                library,
                filter.getSelectedItem() instanceof ExamTemplate e ? e : exam,
                null,
                "",
                refresh));
    remove.addActionListener(
        event -> {
          Shortcut entry = selected(table, model);
          if (entry != null
              && confirm(
                  dialog,
                  "Delete “" + entry.label() + "”? Existing study text will be kept.",
                  "Delete shortcut")) {
            library.remove(entry.id());
            refresh.run();
          }
        });
    filter.addActionListener(event -> reload.run());
    ComposerFormState.watch(search, reload);
    close.addActionListener(event -> dialog.dispose());
    for (boolean importing : List.of(true, false)) {
      (importing ? imports : export)
          .addActionListener(
              event -> {
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle(importing ? "Import shortcuts" : "Export shortcuts");
                chooser.setFileFilter(
                    new FileNameExtensionFilter("Shortcut library (*.json)", "json"));
                if (!importing)
                  chooser.setSelectedFile(new java.io.File("report-composer-shortcuts.json"));
                if ((importing ? chooser.showOpenDialog(dialog) : chooser.showSaveDialog(dialog))
                    != JFileChooser.APPROVE_OPTION) return;
                Path chosen = chooser.getSelectedFile().toPath();
                if (!importing
                    && !chosen.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                  chosen = chosen.resolveSibling(chosen.getFileName() + ".json");
                final Path file = chosen;
                if (!importing
                    && Files.exists(file)
                    && !confirm(dialog, "Replace the existing backup file?", "Export shortcuts"))
                  return;
                actions.forEach(button -> button.setEnabled(false));
                table.setEnabled(false);
                filter.setEnabled(false);
                search.setEnabled(false);
                dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
                status.setText(importing ? "Importing…" : "Exporting…");
                new SwingWorker<String, Void>() {
                  @Override
                  protected String doInBackground() throws Exception {
                    if (importing) {
                      var result = library.importFrom(file);
                      library.pendingSave().get();
                      return "Added "
                          + result.added()
                          + "; matched "
                          + result.matched()
                          + ". Local edits kept; counts merged without double counting.";
                    }
                    library.exportTo(file);
                    return "Shortcut backup exported.";
                  }

                  @Override
                  protected void done() {
                    actions.forEach(button -> button.setEnabled(true));
                    table.setEnabled(true);
                    filter.setEnabled(true);
                    search.setEnabled(true);
                    dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                    refresh.run();
                    edit.setEnabled(selected(table, model) != null);
                    remove.setEnabled(selected(table, model) != null);
                    try {
                      status.setText(get());
                    } catch (Exception error) {
                      status.setText(
                          importing
                              ? "Import could not be completed. Check the shortcut save status."
                              : "Export failed.");
                      ComposerDialogSupport.showMessage(
                          dialog,
                          "The shortcut file could not be "
                              + (importing
                                  ? "imported. Check that it is a valid shortcut backup."
                                  : "exported. Check the destination is writable."),
                          "Shortcut library",
                          JOptionPane.WARNING_MESSAGE);
                    }
                  }
                }.execute();
              });
    }
    JPanel top = new JPanel(new BorderLayout(8, 4));
    top.add(filter, BorderLayout.WEST);
    top.add(search, BorderLayout.CENTER);
    JPanel lower = new JPanel(new BorderLayout(0, 5));
    lower.add(new JScrollPane(preview), BorderLayout.CENTER);
    JPanel footer = new JPanel(new BorderLayout());
    footer.add(row(add, edit, remove, imports, export, close), BorderLayout.NORTH);
    footer.add(status, BorderLayout.SOUTH);
    lower.add(footer, BorderLayout.SOUTH);
    JPanel content = content();
    content.add(top, BorderLayout.NORTH);
    JScrollPane list = new JScrollPane(table);
    list.setPreferredSize(new Dimension(740, 270));
    content.add(list, BorderLayout.CENTER);
    content.add(lower, BorderLayout.SOUTH);
    dialog.setContentPane(content);
    reload.run();
    edit.setEnabled(false);
    remove.setEnabled(false);
    preview.setText("Select a shortcut to see its full text.");
    show(dialog, parent);
  }

  private static Shortcut selected(JTable table, ShortcutTable model) {
    int selected = table.getSelectedRow();
    if (selected < 0 || selected >= table.getRowCount()) return null;
    int row = table.convertRowIndexToModel(selected);
    return row < model.rows.size() ? model.rows.get(row) : null;
  }

  private static void fill(
      Shortcut entry, JTextField label, JTextArea text, JComboBox<Object> scope, JCheckBox pinned) {
    label.setText(entry.label());
    text.setText(entry.text());
    text.setCaretPosition(0);
    scope.setSelectedItem(
        ShortcutLibrary.ALL.equals(entry.scope())
            ? ALL_EXAMS
            : ExamTemplate.valueOf(entry.scope()));
    pinned.setSelected(entry.pinned());
  }

  private static JComboBox<Object> scopes(boolean includeAllParts) {
    List<Object> options = new ArrayList<>();
    if (includeAllParts) options.add(ALL_PARTS);
    options.add(ALL_EXAMS);
    options.addAll(List.of(ExamTemplate.values()));
    return new JComboBox<>(options.toArray());
  }

  private static String scopeKey(Object selected) {
    return selected instanceof ExamTemplate exam ? exam.name() : ShortcutLibrary.ALL;
  }

  private static JDialog dialog(Component parent, String title) {
    JDialog dialog =
        new JDialog(
            SwingUtilities.getWindowAncestor(parent), title, Dialog.ModalityType.APPLICATION_MODAL);
    dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    ComposerDialogSupport.keepInFront(dialog);
    return dialog;
  }

  private static void show(JDialog dialog, Component parent) {
    dialog.pack();
    dialog.setLocationRelativeTo(parent);
    dialog.setVisible(true);
    dialog.dispose();
  }

  private static JPanel content() {
    JPanel panel = new JPanel(new BorderLayout(8, 8));
    panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    return panel;
  }

  private static JPanel row(Component... components) {
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.TRAILING, 6, 4));
    for (Component component : components) panel.add(component);
    return panel;
  }

  private static boolean confirm(Component parent, String message, String title) {
    JOptionPane pane =
        new JOptionPane(message, JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_OPTION);
    JDialog dialog = pane.createDialog(parent, title);
    ComposerDialogSupport.keepInFront(dialog);
    dialog.setVisible(true);
    dialog.dispose();
    return Integer.valueOf(JOptionPane.YES_OPTION).equals(pane.getValue());
  }

  private static final class ShortcutTable extends AbstractTableModel {
    private List<Shortcut> rows = List.of();
    private ExamTemplate exam;
    private static final String[] COLUMNS = {"Label", "Body part", "Pinned", "Uses", "Text"};

    @Override
    public int getRowCount() {
      return rows.size();
    }

    @Override
    public int getColumnCount() {
      return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
      return COLUMNS[column];
    }

    @Override
    public Class<?> getColumnClass(int column) {
      return column == 2 ? Boolean.class : column == 3 ? Long.class : String.class;
    }

    @Override
    public Object getValueAt(int row, int column) {
      Shortcut entry = rows.get(row);
      return switch (column) {
        case 0 -> entry.label();
        case 1 ->
            ShortcutLibrary.ALL.equals(entry.scope())
                ? ALL_EXAMS
                : ExamTemplate.valueOf(entry.scope()).toString();
        case 2 -> entry.pinned();
        case 3 -> exam == null ? entry.totalUses() : entry.usesFor(exam);
        default -> entry.text();
      };
    }
  }
}
