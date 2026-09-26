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
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.ExamTemplate;
import org.weasis.dicom.reportcomposer.ReportQuickPhrases.Phrase;
import org.weasis.dicom.reportcomposer.ShortcutLibrary.Shortcut;

/**
 * Inserts ordinary editable instructions; it never submits a finding or changes form selections.
 */
final class QuickPhraseBar extends JPanel {
  private static final int VISIBLE_PHRASES = 6;
  private static final int SEARCH_RESULTS = 8;
  private static final Pattern DETAIL = Pattern.compile("\\[[^\\[\\]\\r\\n]+\\]");
  private final JTextArea target;
  private final ShortcutLibrary library;
  private ExamTemplate exam = ExamTemplate.GENERAL;
  private String studyKey;
  private long contextVersion;
  private final JButton add;
  private final JButton manage;
  private final JLabel saveStatus = new JLabel(" ");
  private final JButton retry = new JButton("Retry save");
  private CompletableFuture<Void> observedSave;
  private final List<JButton> examButtons = new ArrayList<>();
  private final List<JButton> allButtons = new ArrayList<>();
  private final JButton more;
  private List<Shortcut> phrases = List.of();
  private JPopupMenu popup;
  private boolean available;
  private Position snippetStart;
  private Position snippetEnd;
  private final JTextField search = new JTextField();
  private final DefaultListModel<Shortcut> searchModel = new DefaultListModel<>();
  private final JList<Shortcut> searchList = new JList<>(searchModel);
  private final JScrollPane searchScroll = new JScrollPane(searchList);
  // A Popup, not a JPopupMenu: menus capture arrow, Enter and Esc keys the search field needs.
  private Popup searchPopup;

  QuickPhraseBar(JTextArea target) {
    this(target, ShortcutLibrary.openDefault());
  }

  QuickPhraseBar(JTextArea target, ShortcutLibrary library) {
    super(new BorderLayout(0, 3));
    this.target = target;
    this.library = library;
    setOpaque(false);
    JPanel examRow = new JPanel(new GridBagLayout());
    examRow.setOpaque(false);
    for (int index = 0; index < VISIBLE_PHRASES; index++) {
      int phraseIndex = index;
      JButton button = button("");
      button.addActionListener(
          event -> {
            if (phraseIndex < phrases.size()) insertShortcut(phrases.get(phraseIndex));
          });
      examButtons.add(button);
      GridBagConstraints constraints = new GridBagConstraints();
      constraints.gridx = index % 3;
      constraints.gridy = index / 3;
      constraints.weightx = 1;
      constraints.fill = GridBagConstraints.HORIZONTAL;
      constraints.insets = new Insets(0, 0, 3, index % 3 == 2 ? 0 : 3);
      examRow.add(button, constraints);
    }
    configureSearch();
    JPanel top = new JPanel(new BorderLayout(0, 3));
    top.setOpaque(false);
    top.add(search, BorderLayout.NORTH);
    top.add(examRow, BorderLayout.CENTER);
    add(top, BorderLayout.NORTH);
    JPanel commonRow = new JPanel(new GridLayout(1, 3, 3, 0));
    commonRow.setOpaque(false);
    add = button("Add shortcut");
    add.setToolTipText("Highlight text in the instructions, then save it as a reusable shortcut");
    add.addActionListener(event -> addSelection(target));
    commonRow.add(add);
    more = button("More…");
    more.setToolTipText("All remaining shortcuts for this exam, including shared phrases");
    more.addActionListener(
        event -> {
          if (canInsert()) createMenu().show(more, 0, more.getHeight());
        });
    commonRow.add(more);
    manage = button("Manage…");
    manage.setToolTipText(
        "Search, edit, delete, pin, import or export shortcuts and view usage counts");
    manage.addActionListener(
        event -> {
          if (library.loadProblem() != null) {
            ComposerDialogSupport.showMessage(
                this, library.loadProblem(), "Shortcut library", JOptionPane.WARNING_MESSAGE);
          } else {
            ShortcutDialogs.manage(this, library, exam, this::libraryChanged);
          }
        });
    commonRow.add(manage);
    add(commonRow, BorderLayout.CENTER);
    JLabel hint = new JLabel("Quick add · edit highlighted details · Tab for next");
    hint.setFont(hint.getFont().deriveFont(11f));
    JPanel footer = new JPanel(new BorderLayout());
    footer.setOpaque(false);
    footer.add(hint, BorderLayout.NORTH);
    JPanel statusRow = new JPanel(new BorderLayout());
    statusRow.setOpaque(false);
    saveStatus.setFont(saveStatus.getFont().deriveFont(11f));
    statusRow.add(saveStatus, BorderLayout.CENTER);
    retry.setFocusable(false);
    retry.setVisible(false);
    retry.addActionListener(
        event -> {
          library.retrySave();
          observeSave();
        });
    statusRow.add(retry, BorderLayout.EAST);
    footer.add(statusRow, BorderLayout.SOUTH);
    add(footer, BorderLayout.SOUTH);
    target.addCaretListener(event -> updateAddButton());
    target.addPropertyChangeListener("enabled", event -> updateAddButton());
    target.addPropertyChangeListener("editable", event -> updateAddButton());
    target.addKeyListener(
        new KeyAdapter() {
          @Override
          public void keyPressed(KeyEvent event) {
            if (event.getKeyCode() == KeyEvent.VK_TAB
                && (event.getModifiersEx() & ~KeyEvent.SHIFT_DOWN_MASK) == 0
                && selectNextDetail(event.isShiftDown())) {
              event.consume();
            }
          }
        });
    setExam(ExamTemplate.GENERAL);
    setAvailable(false);
    if (library.loadProblem() != null) {
      saveStatus.setText("Shortcut library unavailable · see Manage…");
      saveStatus.setToolTipText(library.loadProblem());
    }
  }

  void setContext(ExamTemplate exam, String studyKey) {
    if (this.exam != exam || !Objects.equals(this.studyKey, studyKey)) {
      this.studyKey = studyKey;
      setExam(exam);
    }
  }

  void setExam(ExamTemplate exam) {
    dismissMenu();
    clearSnippet();
    clearSearch();
    this.exam = exam;
    contextVersion++;
    phrases = library.ranked(exam);
    for (int index = 0; index < examButtons.size(); index++) {
      JButton button = examButtons.get(index);
      boolean visible = index < phrases.size();
      button.setVisible(visible);
      if (visible) {
        Shortcut phrase = phrases.get(index);
        button.setText((phrase.pinned() ? "★ " : "") + shortLabel(phrase.label()));
        button.setToolTipText(
            phrase.label() + " · " + phrase.usesFor(exam) + " uses · " + phrase.text());
      }
    }
    revalidate();
    repaint();
  }

  void setAvailable(boolean available) {
    if (this.available && !available) contextVersion++;
    this.available = available;
    setEnabled(available);
    allButtons.forEach(button -> button.setEnabled(available));
    search.setEnabled(available);
    updateAddButton();
    if (!available) {
      dismissMenu();
      clearSnippet();
      clearSearch();
    }
  }

  private void configureSearch() {
    search.putClientProperty("JTextField.placeholderText", "Search phrases…");
    search.putClientProperty("JTextField.showClearButton", true);
    search.setFont(search.getFont().deriveFont(12f));
    search.setToolTipText(
        "Type any part of a phrase (letters in order work too) · ↑↓ choose · Enter insert · Esc"
            + " clear");
    search
        .getDocument()
        .addDocumentListener(
            new DocumentListener() {
              @Override
              public void insertUpdate(DocumentEvent event) {
                updateSearchResults();
              }

              @Override
              public void removeUpdate(DocumentEvent event) {
                updateSearchResults();
              }

              @Override
              public void changedUpdate(DocumentEvent event) {
                updateSearchResults();
              }
            });
    search.addKeyListener(
        new KeyAdapter() {
          @Override
          public void keyPressed(KeyEvent event) {
            switch (event.getKeyCode()) {
              case KeyEvent.VK_DOWN -> moveSearchSelection(1);
              case KeyEvent.VK_UP -> moveSearchSelection(-1);
              case KeyEvent.VK_ENTER -> insertSearchSelection();
              case KeyEvent.VK_ESCAPE -> {
                clearSearch();
                target.requestFocusInWindow();
              }
              default -> {
                return;
              }
            }
            event.consume();
          }
        });
    search.addFocusListener(
        new FocusAdapter() {
          @Override
          public void focusLost(FocusEvent event) {
            // Deferred so a click on a result is handled before the list closes.
            SwingUtilities.invokeLater(
                () -> {
                  if (!search.isFocusOwner()) hideSearchResults();
                });
          }
        });
    searchList.setFocusable(false);
    searchList.setFont(searchList.getFont().deriveFont(12f));
    searchList.setCellRenderer(
        new DefaultListCellRenderer() {
          @Override
          public Component getListCellRendererComponent(
              JList<?> list, Object value, int index, boolean selected, boolean focused) {
            super.getListCellRendererComponent(list, value, index, selected, focused);
            if (value instanceof Shortcut shortcut) {
              String text = shortcut.text().replace('\n', ' ');
              setText(
                  (shortcut.pinned() ? "★ " : "")
                      + shortcut.label()
                      + "  —  "
                      + (text.length() <= 70 ? text : text.substring(0, 69) + "…"));
            }
            return this;
          }
        });
    searchList.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent event) {
            int index = searchList.locationToIndex(event.getPoint());
            if (index >= 0) {
              searchList.setSelectedIndex(index);
              insertSearchSelection();
            }
          }
        });
    searchScroll.setFocusable(false);
  }

  private void updateSearchResults() {
    List<Shortcut> matches =
        canInsert()
            ? PhraseSearch.rank(
                search.getText(), phrases, Shortcut::label, Shortcut::text, SEARCH_RESULTS)
            : List.of();
    searchModel.clear();
    matches.forEach(searchModel::addElement);
    boolean noMatch = matches.isEmpty() && !search.getText().isBlank();
    search.putClientProperty("JComponent.outline", noMatch ? "error" : null);
    if (matches.isEmpty()) {
      hideSearchResults();
      return;
    }
    searchList.setSelectedIndex(0);
    showSearchResults();
  }

  private void showSearchResults() {
    hideSearchResults();
    if (!search.isShowing()) return;
    int rows = Math.min(searchModel.size(), SEARCH_RESULTS);
    Dimension cell =
        searchList
            .getCellRenderer()
            .getListCellRendererComponent(searchList, searchModel.get(0), 0, false, false)
            .getPreferredSize();
    searchScroll.setPreferredSize(
        new Dimension(Math.max(search.getWidth(), 320), cell.height * rows + 6));
    Point location = search.getLocationOnScreen();
    searchPopup =
        PopupFactory.getSharedInstance()
            .getPopup(search, searchScroll, location.x, location.y + search.getHeight());
    searchPopup.show();
  }

  private void hideSearchResults() {
    if (searchPopup != null) {
      searchPopup.hide();
      searchPopup = null;
    }
  }

  private void moveSearchSelection(int delta) {
    if (searchModel.isEmpty()) return;
    int next =
        Math.floorMod(Math.max(searchList.getSelectedIndex(), 0) + delta, searchModel.size());
    searchList.setSelectedIndex(next);
    searchList.ensureIndexIsVisible(next);
  }

  private void insertSearchSelection() {
    Shortcut chosen = searchList.getSelectedValue();
    if (chosen == null && !searchModel.isEmpty()) chosen = searchModel.get(0);
    if (chosen == null) return;
    clearSearch();
    insertShortcut(chosen);
  }

  private void clearSearch() {
    hideSearchResults();
    if (!search.getText().isEmpty()) search.setText("");
    search.putClientProperty("JComponent.outline", null);
  }

  JTextField searchField() {
    return search;
  }

  List<Shortcut> searchResults() {
    return java.util.Collections.list(searchModel.elements());
  }

  private JButton button(String label) {
    JButton button = new JButton(label);
    button.setFont(button.getFont().deriveFont(12f));
    button.setMargin(new Insets(3, 5, 3, 5));
    button.setMinimumSize(new Dimension(0, button.getPreferredSize().height));
    // Keep the editor selection and caret when inserting with the mouse.
    button.setFocusable(false);
    allButtons.add(button);
    return button;
  }

  JPopupMenu createMenu() {
    dismissMenu();
    JPopupMenu menu = new JPopupMenu();
    for (Shortcut phrase : phrases.stream().skip(VISIBLE_PHRASES).toList()) {
      addMenuPhrase(menu, phrase);
    }
    if (menu.getComponentCount() == 0) {
      JMenuItem empty = new JMenuItem("All shortcuts are shown above");
      empty.setEnabled(false);
      menu.add(empty);
    }
    popup = menu;
    return menu;
  }

  private void addMenuPhrase(JPopupMenu menu, Shortcut phrase) {
    JMenuItem item = new JMenuItem((phrase.pinned() ? "★ " : "") + phrase.label());
    Shortcut latest = library.find(phrase.id()).orElse(phrase);
    item.setToolTipText(latest.usesFor(exam) + " uses · " + latest.text());
    item.addActionListener(
        event -> {
          // A menu opened for a previous study or exam must not edit the new study.
          if (popup == menu) insertShortcut(phrase);
        });
    menu.add(item);
  }

  private static String shortLabel(String label) {
    return label.length() <= 20 ? label : label.substring(0, 19) + "…";
  }

  private void updateAddButton() {
    if (add != null)
      add.setEnabled(
          canInsert()
              && library.loadProblem() == null
              && target.getSelectedText() != null
              && !target.getSelectedText().isBlank());
  }

  private void addSelection(JTextComponent source) {
    String selected = source.getSelectedText();
    if (!available
        || !source.isEnabled()
        || !source.isEditable()
        || selected == null
        || selected.isBlank()
        || library.loadProblem() != null) return;
    ShortcutDialogs.edit(this, library, exam, null, selected, this::libraryChanged);
  }

  private void libraryChanged() {
    // Explicit library edits can refresh the buttons immediately; mere use never reorders them.
    setExam(exam);
    observeSave();
  }

  private void insertShortcut(Shortcut shortcut) {
    if (!canInsert()) return;
    var current = library.find(shortcut.id());
    if (current.isEmpty() || !current.get().appliesTo(exam)) return;
    insert(current.get().phrase());
    library.recordUse(shortcut.id(), exam);
    for (int i = 0; i < Math.min(examButtons.size(), phrases.size()); i++) {
      Shortcut latest = library.find(phrases.get(i).id()).orElse(phrases.get(i));
      examButtons
          .get(i)
          .setToolTipText(
              latest.label() + " · " + latest.usesFor(exam) + " uses · " + latest.text());
    }
    observeSave();
  }

  private void observeSave() {
    CompletableFuture<Void> saving = library.pendingSave();
    observedSave = saving;
    saveStatus.setText("Saving shortcuts…");
    retry.setVisible(false);
    saving.whenComplete(
        (ignored, error) ->
            SwingUtilities.invokeLater(
                () -> {
                  if (observedSave != saving) return;
                  saveStatus.setText(
                      error == null
                          ? "Shortcuts saved locally"
                          : "Shortcut save failed · kept in memory");
                  retry.setVisible(error != null);
                }));
  }

  void close() {
    hideSearchResults();
    library.close();
  }

  @Override
  public void removeNotify() {
    hideSearchResults();
    super.removeNotify();
  }

  void installTextMenus(Component root) {
    if (root instanceof JTextComponent text
        && text.isEditable()
        && !(text instanceof javax.swing.JPasswordField)
        && SwingUtilities.getAncestorOfClass(JSpinner.class, text) == null
        && SwingUtilities.getAncestorOfClass(JComboBox.class, text) == null
        && text.getClientProperty(QuickPhraseBar.class) == null) {
      text.putClientProperty(QuickPhraseBar.class, Boolean.TRUE);
      JPopupMenu menu = text.getComponentPopupMenu();
      if (menu == null) {
        menu = new JPopupMenu();
        JMenuItem cut = new JMenuItem("Cut");
        cut.addActionListener(event -> text.cut());
        JMenuItem copy = new JMenuItem("Copy");
        copy.addActionListener(event -> text.copy());
        JMenuItem paste = new JMenuItem("Paste");
        paste.addActionListener(event -> text.paste());
        JMenuItem select = new JMenuItem("Select all");
        select.addActionListener(event -> text.selectAll());
        menu.add(cut);
        menu.add(copy);
        menu.add(paste);
        menu.add(select);
        menu.addPopupMenuListener(
            new PopupMenuListener() {
              @Override
              public void popupMenuWillBecomeVisible(PopupMenuEvent event) {
                boolean selection = text.getSelectionStart() != text.getSelectionEnd();
                cut.setEnabled(text.isEnabled() && text.isEditable() && selection);
                copy.setEnabled(selection);
                paste.setEnabled(text.isEnabled() && text.isEditable());
                select.setEnabled(text.isEnabled());
              }

              @Override
              public void popupMenuWillBecomeInvisible(PopupMenuEvent event) {}

              @Override
              public void popupMenuCanceled(PopupMenuEvent event) {}
            });
        text.setComponentPopupMenu(menu);
      }
      if (menu.getComponentCount() > 0) menu.addSeparator();
      JMenuItem save = new JMenuItem("Add shortcut…");
      long[] openedContext = {-1};
      menu.addPopupMenuListener(
          new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent event) {
              openedContext[0] = contextVersion;
              String selected = text.getSelectedText();
              save.setEnabled(
                  available
                      && text.isEnabled()
                      && text.isEditable()
                      && library.loadProblem() == null
                      && selected != null
                      && !selected.isBlank());
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent event) {}

            @Override
            public void popupMenuCanceled(PopupMenuEvent event) {}
          });
      save.addActionListener(
          event -> {
            if (openedContext[0] == contextVersion) addSelection(text);
          });
      menu.add(save);
    }
    if (root instanceof Container container) {
      for (Component child : container.getComponents()) installTextMenus(child);
    }
  }

  private void dismissMenu() {
    if (popup != null) {
      popup.setVisible(false);
      popup = null;
    }
  }

  private boolean canInsert() {
    return available && isEnabled() && target.isEnabled() && target.isEditable();
  }

  void insert(Phrase phrase) {
    if (!canInsert()) return;
    clearSnippet();
    String text = target.getText();
    int start = target.getSelectionStart();
    int end = target.getSelectionEnd();
    String before = start > 0 && text.charAt(start - 1) != '\n' ? "\n" : "";
    String after = end < text.length() && text.charAt(end) != '\n' ? "\n" : "";
    target.replaceSelection(before + phrase.text() + after);
    int insertedStart = start + before.length();
    try {
      snippetStart = target.getDocument().createPosition(insertedStart);
      snippetEnd = target.getDocument().createPosition(insertedStart + phrase.text().length());
    } catch (BadLocationException error) {
      throw new IllegalStateException("Inserted quick phrase is outside the editor", error);
    }
    target.requestFocusInWindow();
    target.setCaretPosition(insertedStart);
    if (!selectNextDetail(false)) {
      target.setCaretPosition(insertedStart + phrase.text().length());
    }
  }

  boolean selectNextDetail(boolean backwards) {
    if (!canInsert() || snippetStart == null || snippetEnd == null) return false;
    Matcher matcher = DETAIL.matcher(target.getText());
    matcher.region(snippetStart.getOffset(), snippetEnd.getOffset());
    int start = -1;
    int end = -1;
    while (matcher.find()) {
      if (backwards && matcher.end() <= target.getSelectionStart()) {
        start = matcher.start();
        end = matcher.end();
      } else if (!backwards && matcher.start() >= target.getSelectionEnd()) {
        start = matcher.start();
        end = matcher.end();
        break;
      }
    }
    if (start < 0) return false;
    target.select(start, end);
    return true;
  }

  private void clearSnippet() {
    snippetStart = null;
    snippetEnd = null;
  }
}
