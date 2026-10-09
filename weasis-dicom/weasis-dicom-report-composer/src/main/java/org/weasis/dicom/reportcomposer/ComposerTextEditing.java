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

import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.function.LongSupplier;
import javax.swing.AbstractAction;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.UndoableEditEvent;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.CompoundEdit;
import javax.swing.undo.UndoManager;

/**
 * Line-based Home and End, and undo and redo, for the composer's text boxes. On macOS the look and
 * feel maps Home and End to the whole box, and Swing text boxes have no undo of their own.
 */
final class ComposerTextEditing {
  /** Typing closer together than this undoes as one step. */
  static final long GROUP_MILLIS = 1000;

  // A replacement such as setText removes and inserts within the same moment; it is one step.
  private static final long SAME_ACTION_MILLIS = 50;
  private static final String UNDO = "composer-undo";
  private static final String REDO = "composer-redo";
  // Cmd on macOS, Ctrl elsewhere, as the composer's other shortcuts accept either.
  private static final int[] COMMAND_MASKS = {InputEvent.META_DOWN_MASK, InputEvent.CTRL_DOWN_MASK};

  private ComposerTextEditing() {}

  static <T extends JTextComponent> T install(T text) {
    return install(text, System::currentTimeMillis);
  }

  static <T extends JTextComponent> T install(T text, LongSupplier clock) {
    InputMap keys = text.getInputMap(JComponent.WHEN_FOCUSED);
    keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0), DefaultEditorKit.beginLineAction);
    keys.put(
        KeyStroke.getKeyStroke(KeyEvent.VK_HOME, InputEvent.SHIFT_DOWN_MASK),
        DefaultEditorKit.selectionBeginLineAction);
    keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_END, 0), DefaultEditorKit.endLineAction);
    keys.put(
        KeyStroke.getKeyStroke(KeyEvent.VK_END, InputEvent.SHIFT_DOWN_MASK),
        DefaultEditorKit.selectionEndLineAction);

    TypingHistory history = new TypingHistory(text, clock);
    text.getDocument().addUndoableEditListener(history);
    text.addPropertyChangeListener(
        "document",
        event -> {
          if (event.getOldValue() instanceof Document old) old.removeUndoableEditListener(history);
          if (event.getNewValue() instanceof Document replacement) {
            replacement.addUndoableEditListener(history);
          }
          history.discardAllEdits();
        });
    text.putClientProperty(TypingHistory.class, history);
    for (int mask : COMMAND_MASKS) {
      keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, mask), UNDO);
      keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, mask | InputEvent.SHIFT_DOWN_MASK), REDO);
    }
    text.getActionMap().put(UNDO, new HistoryAction(text, history, true));
    text.getActionMap().put(REDO, new HistoryAction(text, history, false));
    return text;
  }

  /** Drops a box's undo history, so Cmd+Z cannot bring back text from another study or finding. */
  static void forgetHistory(JTextComponent text) {
    if (text.getClientProperty(TypingHistory.class) instanceof TypingHistory history) {
      history.discardAllEdits();
    }
  }

  static void forgetHistoryWithin(Container root) {
    for (Component child : root.getComponents()) {
      if (child instanceof JTextComponent text) forgetHistory(text);
      if (child instanceof Container container) forgetHistoryWithin(container);
    }
  }

  private static final class HistoryAction extends AbstractAction {
    private final JTextComponent text;
    private final TypingHistory history;
    private final boolean undo;

    HistoryAction(JTextComponent text, TypingHistory history, boolean undo) {
      this.text = text;
      this.history = history;
      this.undo = undo;
    }

    @Override
    public void actionPerformed(ActionEvent event) {
      if (!text.isEditable() || !text.isEnabled()) return;
      try {
        if (undo && history.canUndo()) history.undo();
        else if (!undo && history.canRedo()) history.redo();
      } catch (CannotUndoException | CannotRedoException e) {
        // The text no longer matches the recorded steps; start over from what is there now.
        history.discardAllEdits();
      }
    }
  }

  /**
   * Undo steps that group a burst of typing (or of deleting) into one, and leave out changes to a
   * read-only box such as the preview.
   */
  static final class TypingHistory extends UndoManager {
    private final JTextComponent text;
    private final transient LongSupplier clock;
    private CompoundEdit group;
    private long lastEditAt;
    private transient DocumentEvent.EventType lastType;

    TypingHistory(JTextComponent text, LongSupplier clock) {
      this.text = text;
      this.clock = clock;
    }

    @Override
    public synchronized void undoableEditHappened(UndoableEditEvent event) {
      if (!text.isEditable()) {
        // Steps recorded before this change would no longer apply to the text.
        discardAllEdits();
        return;
      }
      long now = clock.getAsLong();
      DocumentEvent.EventType type =
          event.getEdit() instanceof DocumentEvent change ? change.getType() : null;
      long gap = now - lastEditAt;
      boolean sameStep =
          group != null && (gap < SAME_ACTION_MILLIS || (gap < GROUP_MILLIS && type == lastType));
      if (!sameStep) {
        endGroup();
        group = new CompoundEdit();
        addEdit(group);
      }
      group.addEdit(event.getEdit());
      lastEditAt = now;
      lastType = type;
    }

    private void endGroup() {
      if (group != null) {
        group.end();
        group = null;
      }
    }

    @Override
    public synchronized boolean canUndo() {
      endGroup();
      return super.canUndo();
    }

    @Override
    public synchronized boolean canRedo() {
      endGroup();
      return super.canRedo();
    }

    @Override
    public synchronized void undo() {
      endGroup();
      super.undo();
    }

    @Override
    public synchronized void redo() {
      endGroup();
      super.redo();
    }

    @Override
    public synchronized void discardAllEdits() {
      endGroup();
      super.discardAllEdits();
    }
  }
}
