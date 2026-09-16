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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.KeyEventDispatcher;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.weasis.dicom.reportcomposer.ShoulderFindingBuilder.CuffTearType;
import org.weasis.dicom.reportcomposer.ShoulderFindingBuilder.RotatorCuffTendon;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.SpineRegion;

class ComposerHoverFocusTest {
  @ParameterizedTest
  @ValueSource(strings = {"spine", "shoulder"})
  void movingFromInstructionsToSectionActivatesShortcutsWithoutClicking(String kind)
      throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          JPanel root = new JPanel();
          JTextArea instructions = new JTextArea("Keep these instructions.");
          root.add(instructions);
          AtomicReference<Component> focus = new AtomicReference<>(instructions);
          JPanel form;
          KeyEventDispatcher dispatcher;
          Consumer<AWTEvent> observe;
          Runnable assertFinding;
          if (kind.equals("spine")) {
            var spine =
                mock(
                    SpineFormPanel.class,
                    withSettings()
                        .useConstructor(SpineRegion.CERVICAL)
                        .defaultAnswer(CALLS_REAL_METHODS));
            var commands = spy(new SpineFormShortcuts(spine));
            commands.setTextRoot(root);
            doReturn(true).when(commands).activeWindow();
            doReturn(spine.level("C2-3")).when(commands).targetUnderPointer();
            doAnswer(invocation -> focus.get()).when(commands).focusOwner();
            form = spine;
            dispatcher = commands;
            observe = commands::observeInteraction;
            assertFinding = () -> assertTrue(spine.level("C2-3").selection().bulge());
          } else {
            var shoulder =
                mock(
                    ShoulderFormPanel.class,
                    withSettings().useConstructor().defaultAnswer(CALLS_REAL_METHODS));
            var commands = spy(new ShoulderFormShortcuts(shoulder));
            commands.setTextRoot(root);
            doReturn(true).when(commands).activeWindow();
            doReturn(shoulder.target("Supraspinatus")).when(commands).targetUnderPointer();
            doAnswer(invocation -> focus.get()).when(commands).focusOwner();
            form = shoulder;
            dispatcher = commands;
            observe = commands::observeInteraction;
            assertFinding =
                () ->
                    assertTrue(
                        shoulder
                            .selection()
                            .rotatorCuffTears()
                            .get(RotatorCuffTendon.SUPRASPINATUS)
                            .types()
                            .contains(CuffTearType.BURSAL_SURFACE));
          }
          root.add(form);
          doAnswer(
                  invocation -> {
                    focus.set(form);
                    return true;
                  })
              .when(form)
              .requestFocusInWindow();

          // A focused text field still owns typing until there is actual pointer interaction.
          assertFalse(dispatcher.dispatchKeyEvent(press(instructions, KeyEvent.VK_B, 'b')));
          observe.accept(new FocusEvent(instructions, FocusEvent.FOCUS_GAINED));
          verify(form, never()).requestFocusInWindow();
          observe.accept(mouse(form, MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK));
          verify(form, never()).requestFocusInWindow();

          observe.accept(mouse(form, MouseEvent.MOUSE_MOVED, 0));
          verify(form).requestFocusInWindow();
          assertTrue(dispatcher.dispatchKeyEvent(press(form, KeyEvent.VK_B, 'b')));
          assertTrue(
              dispatcher.dispatchKeyEvent(
                  new KeyEvent(form, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, 'b')));
          dispatcher.dispatchKeyEvent(
              new KeyEvent(form, KeyEvent.KEY_RELEASED, 0, 0, KeyEvent.VK_B, 'b'));
          assertFinding.run();
          assertEquals("Keep these instructions.", instructions.getText());

          // Explicit text focus (click or T) stays usable with the pointer stationary.
          JTextField notes = new JTextField();
          form.add(notes);
          focus.set(notes);
          observe.accept(new FocusEvent(notes, FocusEvent.FOCUS_GAINED));
          assertFalse(dispatcher.dispatchKeyEvent(press(notes, KeyEvent.VK_B, 'b')));
          observe.accept(mouse(notes, MouseEvent.MOUSE_MOVED, 0));
          verify(form, times(1)).requestFocusInWindow();
        });
  }

  @Test
  void textEditorsScrollbarsAndUnrelatedFieldsKeepTheirFocus() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          JPanel root = new JPanel();
          JPanel form = spy(new JPanel());
          JTextArea instructions = new JTextArea();
          JTextArea notes = new JTextArea();
          JScrollPane scroll = new JScrollPane(notes);
          root.add(instructions);
          root.add(form);
          form.add(scroll);
          for (Component pointer : new Component[] {notes, scroll, scroll.getVerticalScrollBar()}) {
            ComposerHoverFocus.activate(form, root, pointer, instructions);
          }
          ComposerHoverFocus.activate(form, root, form, new JTextField());
          ComposerHoverFocus.activate(form, root, instructions, instructions);
          verify(form, never()).requestFocusInWindow();
          ComposerHoverFocus.activate(form, root, form, instructions);
          verify(form).requestFocusInWindow();
        });
  }

  @Test
  void onlyUnmodifiedPointerMotionCanLeaveTextEntry() {
    JPanel source = new JPanel();
    assertTrue(ComposerHoverFocus.isHoverMotion(mouse(source, MouseEvent.MOUSE_ENTERED, 0)));
    assertTrue(ComposerHoverFocus.isHoverMotion(mouse(source, MouseEvent.MOUSE_MOVED, 0)));
    for (int id :
        new int[] {MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_EXITED, MouseEvent.MOUSE_DRAGGED})
      assertFalse(ComposerHoverFocus.isHoverMotion(mouse(source, id, 0)));
    for (int modifiers :
        new int[] {
          InputEvent.BUTTON1_DOWN_MASK, InputEvent.SHIFT_DOWN_MASK, InputEvent.META_DOWN_MASK
        })
      assertFalse(
          ComposerHoverFocus.isHoverMotion(mouse(source, MouseEvent.MOUSE_MOVED, modifiers)));
  }

  private static MouseEvent mouse(Component source, int id, int modifiers) {
    return new MouseEvent(source, id, 0, modifiers, 5, 5, 0, false);
  }

  private static KeyEvent press(Component source, int code, char character) {
    return new KeyEvent(source, KeyEvent.KEY_PRESSED, 0, 0, code, character);
  }
}
