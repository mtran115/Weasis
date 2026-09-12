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

import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.Laterality;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.ProtrusionLocation;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.Severity;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.SpineRegion;

class SpineFormShortcutsTest {
  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9})
  void recognizesNumpadWithoutTreatingTopRowAsNumbers(int digit) {
    JPanel source = new JPanel();
    assertEquals(
        digit,
        SpineFormShortcuts.numpadDigit(
            key(source, KeyEvent.VK_NUMPAD0 + digit, KeyEvent.KEY_LOCATION_NUMPAD, 0)));
    // macOS keyboards can report ordinary digit key codes with NUMPAD location.
    assertEquals(
        digit,
        SpineFormShortcuts.numpadDigit(
            key(source, KeyEvent.VK_0 + digit, KeyEvent.KEY_LOCATION_NUMPAD, 0)));
    assertEquals(
        -1,
        SpineFormShortcuts.numpadDigit(
            key(source, KeyEvent.VK_0 + digit, KeyEvent.KEY_LOCATION_STANDARD, 0)));
    assertTrue(
        SpineFormShortcuts.isTopRowDigit(
            key(source, KeyEvent.VK_0 + digit, KeyEvent.KEY_LOCATION_STANDARD, 0)));
  }

  @Test
  void numLockOffNavigationCodesRequireNumpadLocation() {
    int[] codes = {
      KeyEvent.VK_INSERT,
      KeyEvent.VK_END,
      KeyEvent.VK_DOWN,
      KeyEvent.VK_PAGE_DOWN,
      KeyEvent.VK_LEFT,
      KeyEvent.VK_CLEAR,
      KeyEvent.VK_RIGHT,
      KeyEvent.VK_HOME,
      KeyEvent.VK_UP,
      KeyEvent.VK_PAGE_UP
    };
    for (int digit = 0; digit < codes.length; digit++) {
      assertEquals(
          digit,
          SpineFormShortcuts.numpadDigit(
              key(new JPanel(), codes[digit], KeyEvent.KEY_LOCATION_NUMPAD, 0)));
      assertEquals(
          -1,
          SpineFormShortcuts.numpadDigit(
              key(new JPanel(), codes[digit], KeyEvent.KEY_LOCATION_STANDARD, 0)));
    }
  }

  @Test
  void appliesOnlyToHoveredLevelAndKeepsExistingHerniationRules() throws Exception {
    edt(
        () -> {
          Fixture f = new Fixture(SpineRegion.CERVICAL, "C2-3");
          f.press('B');
          f.press('P');
          assertTrue(f.level().bulge());
          assertTrue(f.level().protrusion());
          assertEquals(List.of(ProtrusionLocation.CENTRAL), f.level().protrusionLocations());
          assertFalse(f.form.level("C3-4").selection().hasDiscHerniation());
          f.press('E');
          assertFalse(f.level().protrusion());
          assertTrue(f.level().extrusion());
          f.press('E');
          assertFalse(f.level().extrusion());
          f.press('A');
          assertTrue(f.level().annularFissure());
          f.press('V');
          assertTrue(f.level().ventralEpiduralLipomatosis());
          assertFalse(f.form.level("C3-4").selection().ventralEpiduralLipomatosis());
          f.command(KeyEvent.VK_Z);
          assertFalse(f.level().ventralEpiduralLipomatosis());
          assertTrue(f.level().annularFissure());
          f.press('V');
          f.press('V');
          assertFalse(f.level().ventralEpiduralLipomatosis());
          f.press('B');
          assertFalse(f.level().bulge());
        });
  }

  @Test
  void severityAndClearUseOnlyNumpadEvenWhileChoiceIsPending() throws Exception {
    edt(
        () -> {
          Fixture f = new Fixture(SpineRegion.LUMBAR, "L4-5");
          f.press('C');
          for (int digit = 0; digit <= 9; digit++) {
            assertFalse(
                f.commands.handleKey(
                    key(f.form, KeyEvent.VK_0 + digit, KeyEvent.KEY_LOCATION_STANDARD, 0), f.form));
            assertEquals(Severity.NONE, f.level().spinalCanalSeverity());
          }
          f.num(2);
          f.press('L');
          f.num(3);
          f.press('R');
          f.num(1);
          assertEquals(Severity.MODERATE, f.level().spinalCanalSeverity());
          assertEquals(Severity.SEVERE, f.level().leftForaminalSeverity());
          assertEquals(Severity.MILD, f.level().rightForaminalSeverity());
          f.press('L');
          f.num(0);
          assertEquals(Severity.NONE, f.level().leftForaminalSeverity());
          assertEquals(Severity.MODERATE, f.level().spinalCanalSeverity());
        });
  }

  @Test
  void lateralityCommandsDoNotToggleBulgeOrStartForaminalCommands() throws Exception {
    edt(
        () -> {
          Fixture f = new Fixture(SpineRegion.LUMBAR, "L4-5");
          f.press('F');
          f.press('B');
          f.press('H');
          f.press('L');
          assertEquals(Laterality.BILATERAL, f.level().facetArthrosis());
          assertEquals(Laterality.LEFT, f.level().posteriorElementHypertrophy());
          assertFalse(f.level().bulge());
          f.press('F');
          f.num(0);
          assertEquals(Laterality.NONE, f.level().facetArthrosis());
        });
  }

  @Test
  void locationPickerStagesMultipleChoicesAndCancelsWithoutChangingReport() throws Exception {
    edt(
        () -> {
          Fixture f = new Fixture(SpineRegion.CERVICAL, "C2-3");
          f.press('P');
          f.press('D');
          assertFalse(
              f.commands.handleKey(
                  key(f.form, KeyEvent.VK_5, KeyEvent.KEY_LOCATION_STANDARD, 0), f.form));
          f.num(5);
          f.num(7);
          assertEquals(List.of(ProtrusionLocation.CENTRAL), f.level().protrusionLocations());
          f.press(KeyEvent.VK_ENTER);
          assertEquals(
              List.of(ProtrusionLocation.LEFT_SUBARTICULAR, ProtrusionLocation.LEFT_FORAMINAL),
              f.level().protrusionLocations());
          f.press('D');
          f.num(6);
          f.press(KeyEvent.VK_ESCAPE);
          assertEquals(
              List.of(ProtrusionLocation.LEFT_SUBARTICULAR, ProtrusionLocation.LEFT_FORAMINAL),
              f.level().protrusionLocations());
          f.press('E');
          assertEquals(
              List.of(ProtrusionLocation.LEFT_SUBARTICULAR, ProtrusionLocation.LEFT_FORAMINAL),
              f.level().extrusionLocations());
        });
  }

  @Test
  void emptyLocationSelectionKeepsExistingLocationInsteadOfImplicitCentral() throws Exception {
    edt(
        () -> {
          Fixture f = new Fixture(SpineRegion.LUMBAR, "L4-5");
          f.press('P');
          f.press('D');
          f.num(7);
          f.press(KeyEvent.VK_ENTER);
          f.press('D');
          f.num(5);
          f.num(5);
          f.press(KeyEvent.VK_ENTER);
          assertEquals(List.of(ProtrusionLocation.LEFT_FORAMINAL), f.level().protrusionLocations());
        });
  }

  @Test
  void changingHoverOrClearingForNewStudyCancelsCommandsAndUndo() throws Exception {
    edt(
        () -> {
          Fixture f = new Fixture(SpineRegion.LUMBAR, "L4-5");
          f.press('B');
          f.press('L');
          f.commands.setHovered(f.form.level("L5-S1"));
          assertFalse(
              f.commands.handleKey(
                  key(f.form, KeyEvent.VK_NUMPAD3, KeyEvent.KEY_LOCATION_NUMPAD, 0), f.form));
          assertEquals(Severity.NONE, f.form.level("L5-S1").selection().leftForaminalSeverity());
          f.form.clearSelections();
          f.commands.setHovered(f.form.level("L4-5"));
          f.command(KeyEvent.VK_Z);
          assertFalse(f.level().bulge());
        });
  }

  @Test
  void typingAlwaysWinsAndEscapeOnlyExitsComposerText() throws Exception {
    edt(
        () -> {
          Fixture f = new Fixture(SpineRegion.LUMBAR, "L4-5");
          JTextField field = new JTextField();
          f.form.add(field);
          f.press('L');
          for (int code :
              new int[] {
                'B', 'P', 'E', 'A', 'V', 'C', 'L', 'R', 'F', 'H', 'D', 'T', KeyEvent.VK_NUMPAD2
              }) {
            assertFalse(
                f.commands.handleKey(
                    key(
                        field,
                        code,
                        code == KeyEvent.VK_NUMPAD2
                            ? KeyEvent.KEY_LOCATION_NUMPAD
                            : KeyEvent.KEY_LOCATION_STANDARD,
                        0),
                    field));
          }
          assertFalse(
              f.commands.handleKey(
                  key(
                      field,
                      KeyEvent.VK_Z,
                      KeyEvent.KEY_LOCATION_STANDARD,
                      InputEvent.META_DOWN_MASK),
                  field));
          assertTrue(
              f.commands.handleKey(
                  key(field, KeyEvent.VK_ESCAPE, KeyEvent.KEY_LOCATION_STANDARD, 0), field));
          assertFalse(
              f.commands.handleKey(
                  key(new JTextField(), KeyEvent.VK_ESCAPE, KeyEvent.KEY_LOCATION_STANDARD, 0),
                  new JTextField()));
          assertEquals(Severity.NONE, f.level().leftForaminalSeverity());
          assertFalse(f.level().ventralEpiduralLipomatosis());
          assertFalse(f.level().bulge());
        });
  }

  @Test
  void undoRestoresExactControlsAndTriggersExistingDraftWatchers() throws Exception {
    edt(
        () -> {
          Fixture f = new Fixture(SpineRegion.LUMBAR, "L4-5");
          AtomicInteger edits = new AtomicInteger();
          ComposerFormState.watch(f.form, edits::incrementAndGet);
          f.press('P');
          var before = ComposerFormState.capture(f.form);
          f.press('E');
          int afterExtrusion = edits.get();
          f.command(KeyEvent.VK_Z);
          assertEquals(before, ComposerFormState.capture(f.form));
          assertTrue(edits.get() > afterExtrusion);
          SpineFormPanel restored = new SpineFormPanel(SpineRegion.LUMBAR);
          ComposerFormState.restore(restored, ComposerFormState.capture(f.form));
          assertEquals(f.form.selection(), restored.selection());
        });
  }

  @Test
  void undoDoesNotOverwriteInterveningMouseChanges() throws Exception {
    edt(
        () -> {
          Fixture f = new Fixture(SpineRegion.LUMBAR, "L4-5");
          f.press('B');
          f.form.level("L4-5").toggle('A');
          f.command(KeyEvent.VK_Z);
          assertTrue(f.level().annularFissure());
          assertTrue(f.level().bulge());
        });
  }

  @Test
  void unavailableControlsAndDisabledFormsNeverReceiveHiddenFindings() throws Exception {
    edt(
        () -> {
          Fixture f = new Fixture(SpineRegion.THORACIC, "T2-3");
          f.press('C');
          assertFalse(
              f.commands.handleKey(
                  key(f.form, KeyEvent.VK_NUMPAD3, KeyEvent.KEY_LOCATION_NUMPAD, 0), f.form));
          assertEquals(Severity.NONE, f.level().spinalCanalSeverity());
          f.press('D');
          assertFalse(
              f.commands.handleKey(
                  key(f.form, KeyEvent.VK_NUMPAD5, KeyEvent.KEY_LOCATION_NUMPAD, 0), f.form));
          f.form.setEnabled(false);
          assertFalse(
              f.commands.handleKey(key(f.form, 'B', KeyEvent.KEY_LOCATION_STANDARD, 0), f.form));
          assertFalse(f.level().bulge());
        });
  }

  @Test
  void commandEnterUsesExistingAddSelectedFindingsAction() throws Exception {
    edt(
        () -> {
          Fixture f = new Fixture(SpineRegion.LUMBAR, "L4-5");
          AtomicInteger submits = new AtomicInteger();
          f.form.addSubmitListener(event -> submits.incrementAndGet());
          f.command(KeyEvent.VK_ENTER);
          assertEquals(1, submits.get());
          assertFalse(
              f.commands.handleKey(
                  key(f.form, KeyEvent.VK_ENTER, KeyEvent.KEY_LOCATION_STANDARD, 0), f.form));
        });
  }

  @Test
  void dispatcherConsumesRepeatsAndTypedCharactersWithoutSwallowingTopRowOrArrows()
      throws Exception {
    edt(
        () -> {
          Fixture f = new Fixture(SpineRegion.CERVICAL, "C2-3");
          SpineFormShortcuts dispatcher = spy(f.commands);
          doReturn(true).when(dispatcher).activeWindow();
          doReturn(f.form.level("C2-3")).when(dispatcher).levelUnderPointer();
          doReturn(f.form).when(dispatcher).focusOwner();
          AtomicInteger forwarded = new AtomicInteger();
          dispatcher.setViewerShortcutHandler(
              event -> {
                if (SpineFormShortcuts.isTopRowDigit(event)) {
                  forwarded.incrementAndGet();
                  return true;
                }
                return false;
              });
          assertTrue(
              dispatcher.dispatchKeyEvent(key(f.form, 'B', KeyEvent.KEY_LOCATION_STANDARD, 0)));
          assertTrue(
              dispatcher.dispatchKeyEvent(key(f.form, 'B', KeyEvent.KEY_LOCATION_STANDARD, 0)));
          assertTrue(f.level().bulge());
          assertTrue(
              dispatcher.dispatchKeyEvent(
                  new KeyEvent(f.form, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, 'b')));
          assertTrue(
              dispatcher.dispatchKeyEvent(
                  new KeyEvent(f.form, KeyEvent.KEY_RELEASED, 0, 0, KeyEvent.VK_B, 'b')));
          assertTrue(
              dispatcher.dispatchKeyEvent(key(f.form, 'B', KeyEvent.KEY_LOCATION_STANDARD, 0)));
          assertFalse(f.level().bulge());
          assertTrue(
              dispatcher.dispatchKeyEvent(
                  key(f.form, KeyEvent.VK_0, KeyEvent.KEY_LOCATION_STANDARD, 0)));
          assertEquals(1, forwarded.get());
          assertFalse(
              dispatcher.dispatchKeyEvent(
                  key(f.form, KeyEvent.VK_LEFT, KeyEvent.KEY_LOCATION_STANDARD, 0)));
          assertFalse(
              dispatcher.dispatchKeyEvent(
                  key(f.form, KeyEvent.VK_RIGHT, KeyEvent.KEY_LOCATION_STANDARD, 0)));
        });
  }

  @Test
  void unrelatedWindowAndTextFocusBlockGlobalDispatch() throws Exception {
    edt(
        () -> {
          Fixture f = new Fixture(SpineRegion.CERVICAL, "C2-3");
          SpineFormShortcuts dispatcher = spy(f.commands);
          doReturn(false).when(dispatcher).activeWindow();
          assertFalse(
              dispatcher.dispatchKeyEvent(key(f.form, 'P', KeyEvent.KEY_LOCATION_STANDARD, 0)));
          assertFalse(f.level().protrusion());
          doReturn(true).when(dispatcher).activeWindow();
          doReturn(f.form.level("C2-3")).when(dispatcher).levelUnderPointer();
          doReturn(new JTextField()).when(dispatcher).focusOwner();
          dispatcher.setViewerShortcutHandler(
              event -> {
                fail("Typing must not call the viewer");
                return true;
              });
          assertFalse(
              dispatcher.dispatchKeyEvent(
                  key(f.form, KeyEvent.VK_0, KeyEvent.KEY_LOCATION_STANDARD, 0)));
          assertFalse(
              dispatcher.dispatchKeyEvent(key(f.form, 'B', KeyEvent.KEY_LOCATION_STANDARD, 0)));
        });
  }

  private static KeyEvent key(Component source, int code, int location, int modifiers) {
    return new KeyEvent(
        source,
        KeyEvent.KEY_PRESSED,
        0,
        modifiers,
        code,
        code >= 'A' && code <= 'Z' ? Character.toLowerCase((char) code) : KeyEvent.CHAR_UNDEFINED,
        location);
  }

  private static void edt(Runnable runnable) throws Exception {
    SwingUtilities.invokeAndWait(runnable);
  }

  private static class Fixture {
    final SpineFormPanel form;
    final SpineFormShortcuts commands;
    final String level;

    Fixture(SpineRegion region, String level) {
      form = new SpineFormPanel(region);
      commands = form.shortcuts();
      this.level = level;
      commands.setHovered(form.level(level));
    }

    SpineFindingBuilder.LevelSelection level() {
      return form.level(level).selection();
    }

    void press(int code) {
      assertTrue(commands.handleKey(key(form, code, KeyEvent.KEY_LOCATION_STANDARD, 0), form));
    }

    void num(int digit) {
      assertTrue(
          commands.handleKey(
              key(form, KeyEvent.VK_NUMPAD0 + digit, KeyEvent.KEY_LOCATION_NUMPAD, 0), form));
    }

    void command(int code) {
      assertTrue(
          commands.handleKey(
              key(form, code, KeyEvent.KEY_LOCATION_STANDARD, InputEvent.META_DOWN_MASK), form));
    }
  }
}
