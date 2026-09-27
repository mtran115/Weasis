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
import java.awt.Dimension;
import java.awt.Font;
import java.util.Comparator;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import org.weasis.dicom.reportcomposer.TrainingCaseStore.ExportStatus;

/** Lists loaded studies whose instruction packet has not been exported. */
final class UnexportedStudiesDialog {
  private UnexportedStudiesDialog() {}

  record Row(CaseContext study, ExportStatus status) {}

  /** Likeliest missed reads first: started drafts, then edits after export, then untouched. */
  static List<Row> visibleRows(List<Row> rows, boolean includeExported) {
    return rows.stream()
        .filter(row -> includeExported || row.status() != ExportStatus.EXPORTED)
        .sorted(Comparator.comparingInt(row -> priority(row.status())))
        .toList();
  }

  static String summary(List<Row> rows) {
    long missing = rows.stream().filter(row -> row.status() != ExportStatus.EXPORTED).count();
    return missing == 0
        ? "All " + rows.size() + " loaded studies have been exported."
        : missing + " of " + rows.size() + " loaded studies not exported";
  }

  static String label(ExportStatus status) {
    return switch (status) {
      case DRAFT -> "Draft started · not exported";
      case CHANGED_SINCE_EXPORT -> "Changed since export";
      case NOT_STARTED -> "Not opened in composer";
      case UNREADABLE -> "Saved draft unreadable";
      case EXPORTED -> "Exported";
    };
  }

  private static int priority(ExportStatus status) {
    return switch (status) {
      case DRAFT -> 0;
      case CHANGED_SINCE_EXPORT -> 1;
      case UNREADABLE -> 2;
      case NOT_STARTED -> 3;
      case EXPORTED -> 4;
    };
  }

  static void show(Component parent, List<Row> rows) {
    JDialog dialog =
        new JDialog(
            SwingUtilities.getWindowAncestor(parent),
            "Studies not exported",
            JDialog.ModalityType.MODELESS);
    DefaultTableModel model =
        new DefaultTableModel(
            new Object[] {"Status", "Patient", "Exam", "Study date", "Accession"}, 0) {
          @Override
          public boolean isCellEditable(int row, int column) {
            return false;
          }
        };
    JTable table = new JTable(model);
    table.setAutoCreateRowSorter(true);
    table.setFillsViewportHeight(true);
    JCheckBox showExported = new JCheckBox("Show exported studies");
    Runnable refresh =
        () -> {
          model.setRowCount(0);
          for (Row row : visibleRows(rows, showExported.isSelected())) {
            CaseContext study = row.study();
            model.addRow(
                new Object[] {
                  label(row.status()),
                  study.patientDisplayName(),
                  study.examTitle(),
                  study.studyDate(),
                  study.accessionNumber()
                });
          }
        };
    showExported.addActionListener(event -> refresh.run());
    refresh.run();

    JLabel heading = new JLabel(summary(rows));
    heading.setFont(heading.getFont().deriveFont(Font.BOLD));
    JLabel hint =
        new JLabel("Priors loaded only for comparison also appear as \"Not opened in composer\".");
    hint.setFont(hint.getFont().deriveFont(11f));
    JPanel header = new JPanel(new BorderLayout(0, 2));
    header.add(heading, BorderLayout.NORTH);
    header.add(hint, BorderLayout.CENTER);
    header.add(showExported, BorderLayout.SOUTH);
    JPanel content = new JPanel(new BorderLayout(0, 6));
    content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    content.add(header, BorderLayout.NORTH);
    JScrollPane scroll = new JScrollPane(table);
    scroll.setPreferredSize(new Dimension(760, 320));
    content.add(scroll, BorderLayout.CENTER);
    dialog.setContentPane(content);
    dialog.pack();
    dialog.setLocationRelativeTo(parent);
    dialog.setVisible(true);
  }
}
