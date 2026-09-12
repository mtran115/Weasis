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
import java.awt.Container;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.weasis.dicom.reportcomposer.ShoulderFindingBuilder.CuffTearType;
import org.weasis.dicom.reportcomposer.ShoulderFindingBuilder.Degree;
import org.weasis.dicom.reportcomposer.ShoulderFindingBuilder.LabralLocation;
import org.weasis.dicom.reportcomposer.ShoulderFindingBuilder.RotatorCuffTendon;

class ShoulderFormShortcutsTest {
  @ParameterizedTest
  @EnumSource(RotatorCuffTendon.class)
  void tendonShortcutsOnlyEditHoveredTendonAndGenerateExpectedFinding(RotatorCuffTendon tendon)
      throws Exception {
    edt(
        () -> {
          Fixture f = new Fixture(tendon.toString());
          f.num(2);
          f.press('A');
          f.press('H');
          f.press('P');
          f.press('G');
          f.form.target(tendon.toString()).textField().setText("8 mm AP");
          var selection = f.form.selection();
          assertEquals(1, selection.rotatorCuffTears().size());
          assertEquals(1, selection.rotatorCuffTendinosis().size());
          assertEquals(Degree.MODERATE, selection.rotatorCuffTendinosis().get(tendon));
          assertEquals(
              List.of(CuffTearType.ARTICULAR_SURFACE),
              selection.rotatorCuffTears().get(tendon).types());
          assertEquals(
              "High-grade partial-thickness articular-surface tear of the "
                  + tendon.phrase()
                  + " tendon at the footprint with moderate background tendinosis. 8 mm AP.",
              ShoulderFindingBuilder.generate(selection).getFirst().findingText());
          f.press('A');
          assertTrue(f.form.selection().rotatorCuffTears().isEmpty());
        });
  }

  @Test
  void multipleTearTypesAndModifiersRemainIndependent() throws Exception {
    edt(
        () -> {
          Fixture f = new Fixture("Supraspinatus");
          for (int key : new int[] {'I', 'A', 'B', 'F'}) f.press(key);
          assertEquals(
              List.of(CuffTearType.values()),
              f.form.selection().rotatorCuffTears().get(RotatorCuffTendon.SUPRASPINATUS).types());
          f.press('A');
          f.press('F');
          f.press('H');
          f.press('P');
          f.press('G');
          var tear = f.form.selection().rotatorCuffTears().get(RotatorCuffTendon.SUPRASPINATUS);
          assertEquals(
              List.of(CuffTearType.INTERSTITIAL, CuffTearType.BURSAL_SURFACE), tear.types());
          assertTrue(tear.highGrade());
          assertTrue(tear.atFootprint());
          assertTrue(tear.backgroundTendinosis());
          f.press('H');
          f.press('P');
          f.press('G');
          tear = f.form.selection().rotatorCuffTears().get(RotatorCuffTendon.SUPRASPINATUS);
          assertFalse(tear.highGrade());
          assertFalse(tear.atFootprint());
          assertFalse(tear.backgroundTendinosis());
        });
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Subcoracoid bursitis",
        "Subacromial/subdeltoid bursitis",
        "AC joint osteoarthrosis",
        "Long head of biceps"
      })
  void gradedRowsSupportSeverityMinimalAndClearWithoutEditingNeighbors(String target)
      throws Exception {
    edt(
        () -> {
          Fixture f = new Fixture(target);
          for (int digit = 1; digit <= 3; digit++) {
            f.num(digit);
            var findings = ShoulderFindingBuilder.generate(f.form.selection());
            assertEquals(1, findings.size());
            assertTrue(
                findings
                    .getFirst()
                    .findingText()
                    .startsWith(List.of("Mild", "Moderate", "Severe").get(digit - 1)));
          }
          f.numpad(KeyEvent.VK_DECIMAL);
          assertTrue(
              ShoulderFindingBuilder.generate(f.form.selection())
                  .getFirst()
                  .findingText()
                  .startsWith("Minimal"));
          f.num(0);
          assertFalse(f.form.selection().hasFinding());
        });
  }

  @Test
  void clearingTendinosisPreservesTearAndMinimalUsesNumpadDecimal() throws Exception {
    edt(
        () -> {
          Fixture f = new Fixture("Subscapularis");
          f.press('I');
          f.numpad(KeyEvent.VK_PERIOD);
          assertEquals(
              Degree.MINIMAL,
              f.form.selection().rotatorCuffTendinosis().get(RotatorCuffTendon.SUBSCAPULARIS));
          f.num(0);
          assertTrue(f.form.selection().rotatorCuffTendinosis().isEmpty());
          assertEquals(
              List.of(CuffTearType.INTERSTITIAL),
              f.form.selection().rotatorCuffTears().get(RotatorCuffTendon.SUBSCAPULARIS).types());
        });
  }

  @Test
  void topRowDigitsOrdinaryPunctuationAndArrowsNeverEditFindings() throws Exception {
    edt(
        () -> {
          Fixture f = new Fixture("Supraspinatus");
          f.num(2);
          var before = f.form.selection();
          for (int digit = 0; digit <= 9; digit++)
            assertFalse(f.handle(KeyEvent.VK_0 + digit, KeyEvent.KEY_LOCATION_STANDARD, 0, f.form));
          for (int code :
              new int[] {
                KeyEvent.VK_PERIOD,
                KeyEvent.VK_COMMA,
                KeyEvent.VK_DELETE,
                KeyEvent.VK_LEFT,
                KeyEvent.VK_RIGHT
              }) {
            assertFalse(f.handle(code, KeyEvent.KEY_LOCATION_STANDARD, 0, f.form));
          }
          f.num(9); // An unavailable numpad choice must not leak into viewer actions.
          assertEquals(before, f.form.selection());
          assertTrue(f.handle(KeyEvent.VK_3, KeyEvent.KEY_LOCATION_NUMPAD, 0, f.form));
          assertEquals(
              Degree.SEVERE,
              f.form.selection().rotatorCuffTendinosis().get(RotatorCuffTendon.SUPRASPINATUS));
          f.numpad(KeyEvent.VK_DOWN); // Num Lock off.
          assertEquals(
              Degree.MODERATE,
              f.form.selection().rotatorCuffTendinosis().get(RotatorCuffTendon.SUPRASPINATUS));
          f.numpad(KeyEvent.VK_DELETE);
          assertEquals(
              Degree.MINIMAL,
              f.form.selection().rotatorCuffTendinosis().get(RotatorCuffTendon.SUPRASPINATUS));
        });
  }

  @Test
  void labralKeysCombineLocationsWithoutChangingTendons() throws Exception {
    edt(
        () -> {
          Fixture f = new Fixture("Labral tear");
          f.press('S');
          f.press('P');
          f.press('C');
          assertEquals(
              List.of(LabralLocation.SUPERIOR, LabralLocation.POSTERIOR),
              f.form.selection().labralTearLocations());
          assertEquals(
              "Superior and posterior labral tear with an adjacent paralabral cyst.",
              ShoulderFindingBuilder.generate(f.form.selection()).getFirst().findingText());
          f.press('A');
          f.press('I');
          f.press('S');
          f.press('C');
          assertEquals(
              List.of(LabralLocation.ANTERIOR, LabralLocation.POSTERIOR, LabralLocation.INFERIOR),
              f.form.selection().labralTearLocations());
          assertFalse(f.form.selection().paralabralCyst());
          assertTrue(f.form.selection().rotatorCuffTears().isEmpty());
          assertFalse(f.handle(KeyEvent.VK_NUMPAD2, KeyEvent.KEY_LOCATION_NUMPAD, 0, f.form));
        });
  }

  @Test
  void modifiersRequireTearAndHighGradeRequiresPartialOrInterstitialTear() throws Exception {
    edt(
        () -> {
          Fixture f = new Fixture("Infraspinatus");
          for (int key : new int[] {'H', 'P', 'G', 'T'}) f.press(key);
          assertFalse(f.form.selection().hasFinding());
          assertFalse(f.target().textField().isEnabled());
          f.press('F');
          f.press('H');
          assertFalse(
              f.form
                  .selection()
                  .rotatorCuffTears()
                  .get(RotatorCuffTendon.INFRASPINATUS)
                  .highGrade());
          assertTrue(f.target().textField().isEnabled());
          f.press('B');
          f.press('H');
          assertTrue(
              f.form
                  .selection()
                  .rotatorCuffTears()
                  .get(RotatorCuffTendon.INFRASPINATUS)
                  .highGrade());
        });
  }

  @Test
  void textShortcutTargetsTendonDetailsOrSharedShoulderText() throws Exception {
    edt(
        () -> {
          Fixture f = new Fixture("Supraspinatus");
          f.press('A');
          var detail = f.target().textField();
          detail.setText("8 mm AP");
          detail.setCaretPosition(0);
          f.press('T');
          assertEquals(detail.getDocument().getLength(), detail.getCaretPosition());
          f.hover("Labral tear");
          var global = f.target().textField();
          assertNotSame(detail, global);
          global.setText("Labral degeneration");
          global.setCaretPosition(0);
          f.press('T');
          assertEquals(global.getDocument().getLength(), global.getCaretPosition());
          assertEquals("Labral degeneration", f.form.selection().freeText());
        });
  }

  @Test
  void typingAlwaysWinsAndEscapeOnlyLeavesComposerText() throws Exception {
    edt(
        () -> {
          Fixture f = new Fixture("Supraspinatus");
          f.press('A');
          var text = f.target().textField();
          for (int code :
              new int[] {
                'A', 'B', 'I', 'F', 'H', 'P', 'G', 'T', KeyEvent.VK_NUMPAD2, KeyEvent.VK_DECIMAL
              }) {
            assertFalse(f.handle(code, KeyEvent.KEY_LOCATION_STANDARD, 0, text));
          }
          assertFalse(
              f.handle(
                  KeyEvent.VK_Z, KeyEvent.KEY_LOCATION_STANDARD, InputEvent.META_DOWN_MASK, text));
          assertFalse(
              f.handle(
                  KeyEvent.VK_ENTER,
                  KeyEvent.KEY_LOCATION_STANDARD,
                  InputEvent.CTRL_DOWN_MASK,
                  text));
          assertTrue(f.handle(KeyEvent.VK_ESCAPE, KeyEvent.KEY_LOCATION_STANDARD, 0, text));
          assertFalse(
              f.handle(KeyEvent.VK_ESCAPE, KeyEvent.KEY_LOCATION_STANDARD, 0, new JTextField()));
          assertTrue(f.form.selection().rotatorCuffTendinosis().isEmpty());
        });
  }

  @Test
  void undoRestoresDependentControlsAndDraftWatchersAcrossTargets() throws Exception {
    edt(
        () -> {
          Fixture f = new Fixture("Supraspinatus");
          AtomicInteger changes = new AtomicInteger();
          ComposerFormState.watch(f.form, changes::incrementAndGet);
          f.press('A');
          f.press('H');
          f.hover("Labral tear");
          f.press('P');
          f.command(KeyEvent.VK_Z);
          assertTrue(f.form.selection().labralTearLocations().isEmpty());
          f.command(KeyEvent.VK_Z);
          assertFalse(
              f.form
                  .selection()
                  .rotatorCuffTears()
                  .get(RotatorCuffTendon.SUPRASPINATUS)
                  .highGrade());
          int beforeUndo = changes.get();
          f.command(KeyEvent.VK_Z);
          assertFalse(f.form.selection().hasFinding());
          assertFalse(f.form.target("Supraspinatus").textField().isEnabled());
          assertTrue(changes.get() > beforeUndo);
        });
  }

  @Test
  void undoDoesNotOverwriteInterveningTextOrMouseEdits() throws Exception {
    edt(
        () -> {
          Fixture f = new Fixture("Supraspinatus");
          f.press('A');
          f.target().textField().setText("8 mm AP");
          f.command(KeyEvent.VK_Z);
          assertEquals(
              "8 mm AP",
              f.form.selection().rotatorCuffTears().get(RotatorCuffTendon.SUPRASPINATUS).details());
          f.press('H');
          f.target().button('P').doClick(0);
          f.command(KeyEvent.VK_Z);
          assertTrue(
              f.form
                  .selection()
                  .rotatorCuffTears()
                  .get(RotatorCuffTendon.SUPRASPINATUS)
                  .highGrade());
          assertTrue(
              f.form
                  .selection()
                  .rotatorCuffTears()
                  .get(RotatorCuffTendon.SUPRASPINATUS)
                  .atFootprint());
        });
  }

  @Test
  void undoOfBursaDoesNotOverwriteAnotherRow() throws Exception {
    edt(
        () -> {
          Fixture f = new Fixture("Subcoracoid bursitis");
          f.num(2);
          f.form.target("AC joint osteoarthrosis").setDegree(Degree.SEVERE);
          f.command(KeyEvent.VK_Z);
          assertEquals(Degree.NONE, f.form.selection().subcoracoidBursitis());
          assertEquals(Degree.SEVERE, f.form.selection().acJointOsteoarthrosis());
        });
  }

  @Test
  void stateRoundTripRetainsFindingsDetailsAndDependentEnabledState() throws Exception {
    edt(
        () -> {
          Fixture f = new Fixture("Supraspinatus");
          f.press('B');
          f.press('H');
          f.press('P');
          f.num(3);
          f.target().textField().setText("8 mm AP");
          f.hover("Labral tear");
          f.press('S');
          f.press('C');
          ShoulderFormPanel restored = new ShoulderFormPanel();
          ComposerFormState.restore(restored, ComposerFormState.capture(f.form));
          assertEquals(f.form.selection(), restored.selection());
          assertTrue(restored.target("Supraspinatus").textField().isEnabled());
          assertTrue(restored.target("Supraspinatus").button('H').isEnabled());
          assertFalse(restored.target("Infraspinatus").textField().isEnabled());
        });
  }

  @Test
  void submitAndStudyResetClearUndoHistory() throws Exception {
    edt(
        () -> {
          Fixture f = new Fixture("Supraspinatus");
          AtomicInteger submits = new AtomicInteger();
          f.form.addSubmitListener(
              event -> {
                submits.incrementAndGet();
                f.form.clearSelections();
              });
          f.press('F');
          f.command(KeyEvent.VK_ENTER);
          assertEquals(1, submits.get());
          f.hover("Supraspinatus");
          f.command(KeyEvent.VK_Z);
          assertFalse(f.form.selection().hasFinding());
          f.press('A');
          f.form.clearSelections();
          f.hover("Supraspinatus");
          f.command(KeyEvent.VK_Z);
          assertFalse(f.form.selection().hasFinding());
        });
  }

  @Test
  void noTargetDisabledFormOrModifiedLetterNeverEditsFindings() throws Exception {
    edt(
        () -> {
          Fixture f = new Fixture("Supraspinatus");
          for (int modifier :
              new int[] {
                InputEvent.ALT_DOWN_MASK,
                InputEvent.SHIFT_DOWN_MASK,
                InputEvent.META_DOWN_MASK,
                InputEvent.CTRL_DOWN_MASK
              }) {
            assertFalse(f.handle('A', KeyEvent.KEY_LOCATION_STANDARD, modifier, f.form));
          }
          f.commands.setHovered(null);
          assertFalse(f.handle('A', KeyEvent.KEY_LOCATION_STANDARD, 0, f.form));
          f.hover("Supraspinatus");
          f.form.setEnabled(false);
          assertFalse(f.handle('A', KeyEvent.KEY_LOCATION_STANDARD, 0, f.form));
          assertFalse(f.form.selection().hasFinding());
        });
  }

  @Test
  void hoverTargetsRespectSeparateRowsAndScrollClipping() throws Exception {
    edt(
        () -> {
          Fixture f = new Fixture("Supraspinatus");
          f.form.setSize(600, f.form.getPreferredSize().height);
          layoutTree(f.form);
          for (String name :
              List.of(
                  "Subcoracoid bursitis",
                  "Subacromial/subdeltoid bursitis",
                  "AC joint osteoarthrosis",
                  "Supraspinatus",
                  "Infraspinatus",
                  "Labral tear")) {
            Rectangle bounds = f.form.target(name).bounds();
            assertSame(
                f.form.target(name), f.form.targetAt(new Point(bounds.x + 3, bounds.y + 3)), name);
          }
          JScrollPane scroll = new JScrollPane(f.form);
          scroll.setSize(620, 100);
          layoutTree(scroll);
          Rectangle cuff = f.form.target("Supraspinatus").bounds();
          Point cuffPoint = new Point(cuff.x + 3, cuff.y + 3);
          assertNull(f.form.targetAt(cuffPoint));
          scroll.getViewport().setViewPosition(new Point(0, cuff.y));
          assertSame(f.form.target("Supraspinatus"), f.form.targetAt(cuffPoint));
          assertNull(f.form.targetAt(new Point(0, 0)));
        });
  }

  @Test
  void labralButtonsIncludingCystFitWithinNarrowPanel() throws Exception {
    edt(
        () -> {
          Fixture f = new Fixture("Labral tear");
          f.form.setSize(390, f.form.getPreferredSize().height);
          layoutTree(f.form);
          var labrum = f.target();
          for (char key : new char[] {'A', 'S', 'P', 'I', 'C'}) {
            var button = labrum.button(key);
            Rectangle bounds =
                SwingUtilities.convertRectangle(
                    button.getParent(), button.getBounds(), labrum.panel());
            assertTrue(
                new Rectangle(0, 0, labrum.panel().getWidth(), labrum.panel().getHeight())
                    .contains(bounds),
                button.getText());
            assertTrue(button.getWidth() >= button.getPreferredSize().width, button.getText());
          }
        });
  }

  @Test
  void dispatcherConsumesRepeatAndTypedEventsAndPreservesViewerShortcuts() throws Exception {
    edt(
        () -> {
          Fixture f = new Fixture("Supraspinatus");
          ShoulderFormShortcuts dispatcher = spy(f.commands);
          doReturn(true).when(dispatcher).activeWindow();
          doReturn(f.target()).when(dispatcher).targetUnderPointer();
          doReturn(f.form).when(dispatcher).focusOwner();
          AtomicInteger forwarded = new AtomicInteger();
          dispatcher.setViewerShortcutHandler(
              event -> {
                if (SpineFormShortcuts.isTopRowDigit(event)
                    || event.getKeyCode() == KeyEvent.VK_LEFT
                    || event.getKeyCode() == KeyEvent.VK_RIGHT) {
                  forwarded.incrementAndGet();
                  return true;
                }
                return false;
              });
          assertTrue(
              dispatcher.dispatchKeyEvent(key(f.form, 'A', KeyEvent.KEY_LOCATION_STANDARD, 0)));
          assertTrue(
              dispatcher.dispatchKeyEvent(key(f.form, 'A', KeyEvent.KEY_LOCATION_STANDARD, 0)));
          assertEquals(1, f.form.selection().rotatorCuffTears().size());
          assertTrue(
              dispatcher.dispatchKeyEvent(
                  new KeyEvent(f.form, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, 'a')));
          assertTrue(
              dispatcher.dispatchKeyEvent(
                  new KeyEvent(f.form, KeyEvent.KEY_RELEASED, 0, 0, KeyEvent.VK_A, 'a')));
          assertTrue(
              dispatcher.dispatchKeyEvent(key(f.form, 'A', KeyEvent.KEY_LOCATION_STANDARD, 0)));
          assertTrue(f.form.selection().rotatorCuffTears().isEmpty());
          for (int code : new int[] {KeyEvent.VK_0, KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT}) {
            assertTrue(
                dispatcher.dispatchKeyEvent(key(f.form, code, KeyEvent.KEY_LOCATION_STANDARD, 0)));
          }
          assertEquals(3, forwarded.get());
          doReturn(new JPanel())
              .when(dispatcher)
              .focusOwner(); // Actual viewer focus handles its own keys.
          assertFalse(
              dispatcher.dispatchKeyEvent(
                  key(f.form, KeyEvent.VK_1, KeyEvent.KEY_LOCATION_STANDARD, 0)));
        });
  }

  @Test
  void unrelatedWindowAndTextFocusBlockGlobalDispatch() throws Exception {
    edt(
        () -> {
          Fixture f = new Fixture("Supraspinatus");
          ShoulderFormShortcuts dispatcher = spy(f.commands);
          doReturn(false).when(dispatcher).activeWindow();
          assertFalse(
              dispatcher.dispatchKeyEvent(key(f.form, 'B', KeyEvent.KEY_LOCATION_STANDARD, 0)));
          assertFalse(f.form.selection().hasFinding());
          doReturn(true).when(dispatcher).activeWindow();
          doReturn(f.target()).when(dispatcher).targetUnderPointer();
          doReturn(new JTextField()).when(dispatcher).focusOwner();
          dispatcher.setViewerShortcutHandler(
              event -> {
                fail("Typing must not reach viewer shortcuts");
                return true;
              });
          assertFalse(
              dispatcher.dispatchKeyEvent(key(f.form, 'B', KeyEvent.KEY_LOCATION_STANDARD, 0)));
          assertFalse(
              dispatcher.dispatchKeyEvent(
                  key(f.form, KeyEvent.VK_0, KeyEvent.KEY_LOCATION_STANDARD, 0)));
        });
  }

  private static void layoutTree(Container container) {
    container.doLayout();
    for (Component child : container.getComponents())
      if (child instanceof Container nested) layoutTree(nested);
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
    final ShoulderFormPanel form = new ShoulderFormPanel();
    final ShoulderFormShortcuts commands = form.shortcuts();
    String name;

    Fixture(String name) {
      hover(name);
    }

    void hover(String name) {
      this.name = name;
      commands.setHovered(target());
    }

    ShoulderFormPanel.ShortcutTarget target() {
      return form.target(name);
    }

    boolean handle(int code, int location, int modifiers, Component focus) {
      return commands.handleKey(key(form, code, location, modifiers), focus);
    }

    void press(int code) {
      assertTrue(handle(code, KeyEvent.KEY_LOCATION_STANDARD, 0, form));
    }

    void num(int digit) {
      numpad(KeyEvent.VK_NUMPAD0 + digit);
    }

    void numpad(int code) {
      assertTrue(handle(code, KeyEvent.KEY_LOCATION_NUMPAD, 0, form));
    }

    void command(int code) {
      assertTrue(handle(code, KeyEvent.KEY_LOCATION_STANDARD, InputEvent.META_DOWN_MASK, form));
    }
  }
}
