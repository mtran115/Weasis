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
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
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
  private static final String UNSORTED = "Unsorted";
  private static final String ALL_CATEGORIES = "All categories";

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
    JTextArea text = ComposerTextEditing.install(new JTextArea(7, 42));
    text.setLineWrap(true);
    text.setWrapStyleWord(true);
    JComboBox<Object> scope = scopes(false);
    JComboBox<Object> category = categories(false);
    JCheckBox pinned = new JCheckBox("Pin near the top");
    String[] editingId = {existing == null ? null : existing.id()};
    if (existing == null) {
      String initial = selectedText == null ? "" : selectedText;
      text.setText(initial);
      String suggestion = initial.strip().replaceAll("\\s+", " ");
      label.setText(suggestion.substring(0, Math.min(30, suggestion.length())));
      scope.setSelectedItem(exam);
      pinned.setSelected(true);
      // Until the reader picks a category, it follows the wording as it is typed.
      boolean[] categoryChosen = {false};
      boolean[] suggesting = {false};
      Runnable suggest =
          () -> {
            if (categoryChosen[0]) return;
            suggesting[0] = true;
            category.setSelectedItem(
                categoryItem(suggestedCategory(label.getText(), text.getText())));
            suggesting[0] = false;
          };
      suggest.run();
      category.addActionListener(
          event -> {
            if (!suggesting[0]) categoryChosen[0] = true;
          });
      ComposerFormState.watch(text, suggest);
      ComposerFormState.watch(label, suggest);
    } else {
      fill(existing, label, text, scope, category, pinned);
    }
    JPanel fields = new JPanel(new GridLayout(0, 1, 0, 4));
    fields.add(new JLabel("Button label"));
    fields.add(label);
    fields.add(new JLabel("MRI body part"));
    fields.add(scope);
    fields.add(new JLabel("Category"));
    fields.add(category);
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
              fill(match, label, text, scope, category, pinned);
              dialog.setTitle("Edit shortcut");
              feedback.setText("Editing the existing shortcut; its usage counts are retained.");
            }
            return;
          }
          try {
            library.save(
                editingId[0],
                selectedScope,
                label.getText(),
                text.getText(),
                pinned.isSelected(),
                categoryKey(category.getSelectedItem()));
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
    JComboBox<Object> categoryFilter = categories(true);
    JButton sort = new JButton();
    sort.setToolTipText(
        "File every unsorted shortcut, for all body parts, under the category its wording suggests."
            + " Ones without a clear suggestion stay unsorted.");
    ShortcutTable model = new ShortcutTable();
    JTable table = new JTable(model);
    table.setAutoCreateRowSorter(true);
    table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    table.setRowHeight(Math.max(24, table.getRowHeight()));
    table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
    table.getColumnModel().getColumn(0).setPreferredWidth(155);
    table.getColumnModel().getColumn(1).setPreferredWidth(110);
    table.getColumnModel().getColumn(2).setPreferredWidth(150);
    table.getColumnModel().getColumn(3).setPreferredWidth(50);
    table.getColumnModel().getColumn(4).setPreferredWidth(45);
    table.getColumnModel().getColumn(5).setPreferredWidth(300);
    table.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(scopes(false)));
    table.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(categories(false)));
    JTextArea preview = new JTextArea(4, 40);
    preview.setLineWrap(true);
    preview.setWrapStyleWord(true);
    JButton saveText = new JButton("Save text");
    JButton revertText = new JButton("Revert");
    Shortcut[] previewed = {null};
    JLabel status =
        new JLabel(
            "Click a body part, category, or pin to change it; double-click a label to rename it.");
    JButton edit = new JButton("Edit…");
    JButton remove = new JButton("Delete");
    JButton change = new JButton("Change selected…");
    change.setToolTipText("Move, pin, or delete the selected shortcuts");
    JButton add = new JButton("New…");
    JButton export = new JButton("Export…");
    JButton imports = new JButton("Import…");
    JButton close = new JButton("Close");
    List<JButton> actions = List.of(edit, remove, change, add, export, imports, close, sort);
    Runnable reload =
        () -> {
          List<String> selectedIds = selectedAll(table, model).stream().map(Shortcut::id).toList();
          Object bodyPart = filter.getSelectedItem();
          Object shownCategory = categoryFilter.getSelectedItem();
          model.exam = bodyPart instanceof ExamTemplate e ? e : null;
          String query = search.getText().strip().toLowerCase(Locale.ROOT);
          model.rows =
              library.all().stream()
                  .filter(
                      entry ->
                          ALL_PARTS.equals(bodyPart)
                              || (ALL_EXAMS.equals(bodyPart)
                                  ? ShortcutLibrary.ALL.equals(entry.scope())
                                  : entry.appliesTo((ExamTemplate) bodyPart)))
                  .filter(
                      entry ->
                          ALL_CATEGORIES.equals(shownCategory)
                              || entry.category().equals(categoryKey(shownCategory)))
                  .filter(
                      entry ->
                          (entry.label() + " " + entry.text())
                              .toLowerCase(Locale.ROOT)
                              .contains(query))
                  .toList();
          model.fireTableDataChanged();
          int sortable = suggestions(library).size();
          sort.setText("Sort " + sortable + " unsorted by suggestion");
          sort.setEnabled(sortable > 0 && table.isEnabled());
          for (int i = 0; i < model.rows.size(); i++) {
            if (selectedIds.contains(model.rows.get(i).id())) {
              int view = table.convertRowIndexToView(i);
              table.addRowSelectionInterval(view, view);
            }
          }
        };
    Runnable refresh =
        () -> {
          changed.run();
          reload.run();
        };
    Runnable updatePreviewButtons =
        () -> {
          boolean dirty = previewed[0] != null && !preview.getText().equals(previewed[0].text());
          saveText.setEnabled(dirty);
          revertText.setEnabled(dirty);
        };
    // Unsaved wording in the preview is offered for saving before it is replaced or closed.
    Runnable settlePreview =
        () -> {
          Shortcut entry = previewed[0];
          if (entry == null || preview.getText().equals(entry.text())) return;
          if (confirm(
              dialog, "Save your text changes to “" + entry.label() + "”?", "Shortcut text")) {
            saveTextChange(dialog, library, entry, preview.getText(), status);
          }
          previewed[0] = null;
        };
    Runnable showSelection =
        () -> {
          settlePreview.run();
          List<Shortcut> chosen = selectedAll(table, model);
          edit.setEnabled(chosen.size() == 1);
          remove.setEnabled(!chosen.isEmpty());
          change.setEnabled(!chosen.isEmpty());
          previewed[0] = chosen.size() == 1 ? chosen.getFirst() : null;
          preview.setEditable(previewed[0] != null);
          preview.setText(
              switch (chosen.size()) {
                case 0 -> "Select a shortcut to see and edit its full text.";
                case 1 -> chosen.getFirst().text();
                default ->
                    chosen.size()
                        + " shortcuts selected. Use Change selected… or right-click to move, pin,"
                        + " or delete them together.";
              });
          preview.setCaretPosition(0);
          updatePreviewButtons.run();
        };
    Runnable editSelected =
        () -> {
          List<Shortcut> chosen = selectedAll(table, model);
          if (chosen.size() == 1) edit(dialog, library, exam, chosen.getFirst(), null, refresh);
        };
    Runnable deleteSelected =
        () -> {
          List<Shortcut> chosen = selectedAll(table, model);
          if (chosen.isEmpty()) return;
          boolean single = chosen.size() == 1;
          String message =
              single
                  ? "Delete “" + chosen.getFirst().label() + "”? Existing study text will be kept."
                  : "Delete " + chosen.size() + " shortcuts? Existing study text will be kept.";
          if (confirm(dialog, message, single ? "Delete shortcut" : "Delete shortcuts")) {
            previewed[0] = null;
            chosen.forEach(entry -> library.remove(entry.id()));
            refresh.run();
            status.setText(single ? "Shortcut deleted." : chosen.size() + " shortcuts deleted.");
          }
        };
    model.onEdit =
        (row, column, value) -> {
          // Rows refresh after each change, so start from the library's latest copy.
          Shortcut entry = library.find(row.id()).orElse(null);
          if (entry == null) return;
          try {
            switch (column) {
              case 0 ->
                  library.save(
                      entry.id(),
                      entry.scope(),
                      String.valueOf(value),
                      entry.text(),
                      entry.pinned());
              case 1 ->
                  library.save(
                      entry.id(), scopeKey(value), entry.label(), entry.text(), entry.pinned());
              case 2 -> library.categorize(Map.of(entry.id(), categoryKey(value)));
              case 3 ->
                  library.save(
                      entry.id(),
                      entry.scope(),
                      entry.label(),
                      entry.text(),
                      Boolean.TRUE.equals(value));
              default -> {
                return;
              }
            }
            status.setText("Saved “" + entry.label() + "”.");
          } catch (IllegalArgumentException | IllegalStateException error) {
            ComposerDialogSupport.showMessage(
                dialog,
                error.getMessage(),
                "Shortcut could not be saved",
                JOptionPane.WARNING_MESSAGE);
          }
          // A cell editor is still closing while it commits; rebuild the rows once it has.
          if (table.isEditing()) SwingUtilities.invokeLater(refresh);
          else refresh.run();
        };
    Supplier<JPopupMenu> selectionMenu =
        () -> {
          List<Shortcut> chosen =
              selectedAll(table, model).stream()
                  .map(entry -> library.find(entry.id()))
                  .flatMap(Optional::stream)
                  .toList();
          JPopupMenu menu = new JPopupMenu();
          JMenu toCategory = new JMenu("Move to category");
          for (Object option : categoryOptions()) {
            JMenuItem item = new JMenuItem(option.toString());
            item.addActionListener(
                event -> {
                  Map<String, String> assignments = new LinkedHashMap<>();
                  chosen.forEach(entry -> assignments.put(entry.id(), categoryKey(option)));
                  int filed = library.categorize(assignments);
                  refresh.run();
                  status.setText(
                      "Moved "
                          + filed
                          + (filed == 1 ? " shortcut" : " shortcuts")
                          + " to "
                          + option
                          + ".");
                });
            toCategory.add(item);
          }
          menu.add(toCategory);
          JMenu toBodyPart = new JMenu("Move to body part");
          for (Object option : scopeOptions()) {
            JMenuItem item = new JMenuItem(option.toString());
            item.addActionListener(
                event -> {
                  String scope = scopeKey(option);
                  int moved = 0;
                  int skipped = 0;
                  for (Shortcut entry : chosen) {
                    if (entry.scope().equals(scope)) continue;
                    try {
                      library.save(entry.id(), scope, entry.label(), entry.text(), entry.pinned());
                      moved++;
                    } catch (IllegalArgumentException duplicate) {
                      skipped++;
                    }
                  }
                  refresh.run();
                  status.setText(
                      "Moved "
                          + moved
                          + (moved == 1 ? " shortcut" : " shortcuts")
                          + " to "
                          + option
                          + (skipped > 0
                              ? "; "
                                  + skipped
                                  + " skipped because that body part already has the same text."
                              : "."));
                });
            toBodyPart.add(item);
          }
          menu.add(toBodyPart);
          boolean allPinned = chosen.stream().allMatch(Shortcut::pinned);
          JMenuItem pin = new JMenuItem(allPinned ? "Unpin" : "Pin near the top");
          pin.addActionListener(
              event -> {
                chosen.forEach(
                    entry ->
                        library.save(
                            entry.id(), entry.scope(), entry.label(), entry.text(), !allPinned));
                refresh.run();
                status.setText((allPinned ? "Unpinned " : "Pinned ") + chosen.size() + ".");
              });
          menu.add(pin);
          menu.addSeparator();
          JMenuItem delete =
              new JMenuItem(chosen.size() == 1 ? "Delete…" : "Delete " + chosen.size() + "…");
          delete.addActionListener(event -> deleteSelected.run());
          menu.add(delete);
          return menu;
        };
    table
        .getSelectionModel()
        .addListSelectionListener(
            event -> {
              if (!event.getValueIsAdjusting()) showSelection.run();
            });
    ComposerFormState.watch(preview, updatePreviewButtons);
    saveText.addActionListener(
        event -> {
          Shortcut entry = previewed[0];
          if (entry != null && saveTextChange(dialog, library, entry, preview.getText(), status)) {
            previewed[0] = null;
            refresh.run();
          }
        });
    revertText.addActionListener(
        event -> {
          if (previewed[0] != null) preview.setText(previewed[0].text());
        });
    table.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent event) {
            showMenu(event);
          }

          @Override
          public void mouseReleased(MouseEvent event) {
            showMenu(event);
          }

          @Override
          public void mouseClicked(MouseEvent event) {
            int column = table.convertColumnIndexToModel(table.columnAtPoint(event.getPoint()));
            // Editable cells handle their own clicks; the uses and text columns open the editor.
            if (table.isEnabled()
                && event.getClickCount() == 2
                && SwingUtilities.isLeftMouseButton(event)
                && !model.isCellEditable(0, column)) editSelected.run();
          }

          private void showMenu(MouseEvent event) {
            if (!event.isPopupTrigger() || !table.isEnabled()) return;
            int row = table.rowAtPoint(event.getPoint());
            if (row < 0) return;
            if (!table.isRowSelected(row)) table.setRowSelectionInterval(row, row);
            selectionMenu.get().show(table, event.getX(), event.getY());
          }
        });
    change.addActionListener(event -> selectionMenu.get().show(change, 0, change.getHeight()));
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
    remove.addActionListener(event -> deleteSelected.run());
    filter.addActionListener(event -> reload.run());
    categoryFilter.addActionListener(event -> reload.run());
    sort.addActionListener(
        event -> {
          int filed = library.categorize(suggestions(library));
          refresh.run();
          long left = library.all().stream().filter(entry -> entry.category().isEmpty()).count();
          status.setText(
              "Filed "
                  + filed
                  + (filed == 1 ? " shortcut" : " shortcuts")
                  + "; "
                  + left
                  + " unsorted without a clear suggestion. Click their category to file them.");
        });
    ComposerFormState.watch(search, reload);
    close.addActionListener(
        event -> {
          settlePreview.run();
          dialog.dispose();
        });
    dialog.addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent event) {
            if (dialog.getDefaultCloseOperation() == WindowConstants.DISPOSE_ON_CLOSE) {
              settlePreview.run();
            }
          }
        });
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
                categoryFilter.setEnabled(false);
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
                    categoryFilter.setEnabled(true);
                    search.setEnabled(true);
                    dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                    refresh.run();
                    showSelection.run();
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
    JPanel filters = new JPanel(new GridLayout(1, 2, 6, 0));
    filters.add(filter);
    filters.add(categoryFilter);
    JPanel top = new JPanel(new BorderLayout(8, 4));
    top.add(filters, BorderLayout.WEST);
    top.add(search, BorderLayout.CENTER);
    top.add(sort, BorderLayout.EAST);
    JPanel lower = new JPanel(new BorderLayout(0, 5));
    lower.add(new JScrollPane(preview), BorderLayout.CENTER);
    lower.add(row(revertText, saveText), BorderLayout.EAST);
    JPanel footer = new JPanel(new BorderLayout());
    footer.add(row(add, edit, change, remove, imports, export, close), BorderLayout.NORTH);
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
    showSelection.run();
    show(dialog, parent);
  }

  private static List<Shortcut> selectedAll(JTable table, ShortcutTable model) {
    List<Shortcut> result = new ArrayList<>();
    for (int view : table.getSelectedRows()) {
      int row = table.convertRowIndexToModel(view);
      if (row < model.rows.size()) result.add(model.rows.get(row));
    }
    return result;
  }

  /** Saves edited wording, explaining why when it cannot be saved (for example a duplicate). */
  private static boolean saveTextChange(
      Component parent, ShortcutLibrary library, Shortcut entry, String text, JLabel status) {
    try {
      library.save(entry.id(), entry.scope(), entry.label(), text, entry.pinned());
      status.setText("Saved the text of “" + entry.label() + "”.");
      return true;
    } catch (IllegalArgumentException | IllegalStateException error) {
      ComposerDialogSupport.showMessage(
          parent, error.getMessage(), "Shortcut could not be saved", JOptionPane.WARNING_MESSAGE);
      return false;
    }
  }

  private static void fill(
      Shortcut entry,
      JTextField label,
      JTextArea text,
      JComboBox<Object> scope,
      JComboBox<Object> category,
      JCheckBox pinned) {
    label.setText(entry.label());
    text.setText(entry.text());
    text.setCaretPosition(0);
    scope.setSelectedItem(
        ShortcutLibrary.ALL.equals(entry.scope())
            ? ALL_EXAMS
            : ExamTemplate.valueOf(entry.scope()));
    category.setSelectedItem(categoryItem(entry.category()));
    pinned.setSelected(entry.pinned());
  }

  private static JComboBox<Object> categories(boolean includeAll) {
    List<Object> options = new ArrayList<>();
    if (includeAll) options.add(ALL_CATEGORIES);
    options.addAll(categoryOptions());
    return new JComboBox<>(options.toArray());
  }

  private static Object categoryItem(String stored) {
    return ShortcutCategory.of(stored).<Object>map(category -> category).orElse(UNSORTED);
  }

  private static String categoryKey(Object selected) {
    return selected instanceof ShortcutCategory category ? category.name() : ShortcutCategory.NONE;
  }

  private static String suggestedCategory(String label, String text) {
    return ShortcutCategory.suggest(label, text)
        .map(ShortcutCategory::name)
        .orElse(ShortcutCategory.NONE);
  }

  /** Unsorted shortcuts whose wording suggests a category, by id. */
  static Map<String, String> suggestions(ShortcutLibrary library) {
    Map<String, String> result = new LinkedHashMap<>();
    for (Shortcut entry : library.all()) {
      if (!entry.category().isEmpty()) continue;
      String suggestion = suggestedCategory(entry.label(), entry.text());
      if (!suggestion.isEmpty()) result.put(entry.id(), suggestion);
    }
    return result;
  }

  private static JComboBox<Object> scopes(boolean includeAllParts) {
    List<Object> options = new ArrayList<>();
    if (includeAllParts) options.add(ALL_PARTS);
    options.addAll(scopeOptions());
    return new JComboBox<>(options.toArray());
  }

  private static List<Object> scopeOptions() {
    List<Object> options = new ArrayList<>();
    options.add(ALL_EXAMS);
    options.addAll(List.of(ExamTemplate.values()));
    return options;
  }

  private static List<Object> categoryOptions() {
    List<Object> options = new ArrayList<>(List.of(ShortcutCategory.values()));
    options.add(UNSORTED);
    return options;
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
    /** Applies an in-place change to a shortcut: its label, body part, category, or pin. */
    @FunctionalInterface
    interface CellEdit {
      void apply(Shortcut entry, int column, Object value);
    }

    private List<Shortcut> rows = List.of();
    private ExamTemplate exam;
    private CellEdit onEdit;
    private static final String[] COLUMNS = {
      "Label", "Body part", "Category", "Pinned", "Uses", "Text"
    };

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
      return switch (column) {
        case 1, 2 -> Object.class;
        case 3 -> Boolean.class;
        case 4 -> Long.class;
        default -> String.class;
      };
    }

    @Override
    public boolean isCellEditable(int row, int column) {
      return column <= 3;
    }

    @Override
    public void setValueAt(Object value, int row, int column) {
      if (onEdit != null && row < rows.size() && !Objects.equals(value, getValueAt(row, column))) {
        onEdit.apply(rows.get(row), column, value);
      }
    }

    @Override
    public Object getValueAt(int row, int column) {
      Shortcut entry = rows.get(row);
      return switch (column) {
        case 0 -> entry.label();
        case 1 ->
            ShortcutLibrary.ALL.equals(entry.scope())
                ? ALL_EXAMS
                : ExamTemplate.valueOf(entry.scope());
        case 2 -> categoryItem(entry.category());
        case 3 -> entry.pinned();
        case 4 -> exam == null ? entry.totalUses() : entry.usesFor(exam);
        default -> entry.text();
      };
    }
  }
}
