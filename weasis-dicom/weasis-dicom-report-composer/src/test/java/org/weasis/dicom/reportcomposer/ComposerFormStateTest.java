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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.Severity;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.SpineRegion;

class ComposerFormStateTest {
  @Test
  void lumbarControlsRoundTripWithDependentEnabledStatesAndExactPendingText() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          SpineFormPanel source = new SpineFormPanel(SpineRegion.LUMBAR);
          button(source, "Dextroscoliosis").doClick(0);
          choices(source).getFirst().setSelectedItem(Severity.SEVERE);
          texts(source).getFirst().setText("18");
          JToggleButton spondylosis = button(source, "Spondylosis");
          spondylosis.doClick(0);
          button(spondylosis.getParent(), "L4-5").doClick(0);

          JPanel level = levelPanel(source, "L4-5");
          button(level, "Protrusion").doClick(0);
          button(level, "Central").doClick(0);
          button(level, "Left foraminal").doClick(0);
          button(level, "Anterolisthesis").doClick(0);
          texts(level).getFirst().setText("3.5");
          String shorthand = "  T2 hyperint lesion L4 likely intraoss hemang  ";
          texts(level).getLast().setText(shorthand);
          var selection = source.selection();
          JsonNode state = ComposerFormState.capture(source);

          SpineFormPanel restored = new SpineFormPanel(SpineRegion.LUMBAR);
          // Restore over a different prior form state, as when returning to a study.
          button(restored, "Levoscoliosis").doClick(0);
          button(levelPanel(restored, "L4-5"), "Extrusion").doClick(0);
          ComposerFormState.restore(restored, state);
          assertEquals(selection, restored.selection());
          assertEquals(state, ComposerFormState.capture(restored));
          assertEquals(enabledControls(source), enabledControls(restored));
          assertEquals(shorthand, texts(levelPanel(restored, "L4-5")).getLast().getText());
          assertTrue(button(levelPanel(restored, "L4-5"), "Left foraminal").isEnabled());
          assertTrue(texts(levelPanel(restored, "L4-5")).getFirst().isEnabled());
          assertFalse(button(levelPanel(restored, "L3-4"), "Left foraminal").isEnabled());

          ComposerFormState.restore(restored, state);
          assertEquals(selection, restored.selection());
          source.clearSelections();
          ComposerFormState.restore(restored, ComposerFormState.capture(source));
          assertEquals(source.selection(), restored.selection());
          assertEquals(enabledControls(source), enabledControls(restored));
        });
  }

  @Test
  void watchersObserveToggleChoiceAndTextChangesIncludingRestoration() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          SpineFormPanel form = new SpineFormPanel(SpineRegion.LUMBAR);
          AtomicInteger changes = new AtomicInteger();
          ComposerFormState.watch(form, changes::incrementAndGet);
          assertEquals(0, changes.get());
          button(form, "Dextroscoliosis").doClick(0);
          int afterToggle = changes.get();
          assertTrue(afterToggle > 0);
          choices(form).getFirst().setSelectedItem(Severity.MODERATE);
          int afterChoice = changes.get();
          assertTrue(afterChoice > afterToggle);
          texts(levelPanel(form, "L4-5")).getLast().setText("T2 hyperint lesion L4");
          int afterText = changes.get();
          assertTrue(afterText > afterChoice);
          JsonNode state = ComposerFormState.capture(form);
          assertEquals(afterText, changes.get(), "Capturing state must be read-only");
          form.clearSelections();
          int beforeRestore = changes.get();
          ComposerFormState.restore(form, state);
          assertTrue(changes.get() > beforeRestore);
          assertEquals(state, ComposerFormState.capture(form));
        });
  }

  @Test
  void everyStructuredFormCanBeCapturedWithoutChangingItsControls() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          List<Component> forms =
              List.of(
                  new SpineFormPanel(SpineRegion.CERVICAL),
                  new SpineFormPanel(SpineRegion.THORACIC),
                  new SpineFormPanel(SpineRegion.LUMBAR),
                  new BrainFormPanel(),
                  new KneeFormPanel(),
                  new ShoulderFormPanel(),
                  new WristFormPanel());
          AtomicInteger changes = new AtomicInteger();
          forms.forEach(form -> ComposerFormState.watch(form, changes::incrementAndGet));
          long start = System.nanoTime();
          List<JsonNode> states = forms.stream().map(ComposerFormState::capture).toList();
          long elapsed = System.nanoTime() - start;
          assertEquals(0, changes.get());
          assertTrue(states.stream().allMatch(state -> state.isArray() && !state.isEmpty()));
          for (int i = 0; i < forms.size(); i++) {
            assertEquals(states.get(i), ComposerFormState.capture(forms.get(i)));
          }
          System.out.printf(
              "Captured %d structured forms (%d controls) in %.2f ms%n",
              forms.size(), states.stream().mapToInt(JsonNode::size).sum(), elapsed / 1_000_000.0);
        });
  }

  private static JToggleButton button(Component root, String label) {
    return descendants(root).stream()
        .filter(JToggleButton.class::isInstance)
        .map(JToggleButton.class::cast)
        .filter(button -> label.equals(button.getText()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing button: " + label));
  }

  private static JPanel levelPanel(Component root, String level) {
    return descendants(root).stream()
        .filter(JPanel.class::isInstance)
        .map(JPanel.class::cast)
        .filter(
            panel ->
                java.util.Arrays.stream(panel.getComponents())
                    .anyMatch(
                        child -> child instanceof JLabel label && level.equals(label.getText())))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing level: " + level));
  }

  private static List<JTextField> texts(Component root) {
    return descendants(root).stream()
        .filter(JTextField.class::isInstance)
        .map(JTextField.class::cast)
        .toList();
  }

  private static List<JComboBox<?>> choices(Component root) {
    return descendants(root).stream()
        .filter(JComboBox.class::isInstance)
        .<JComboBox<?>>map(component -> (JComboBox<?>) component)
        .toList();
  }

  private static List<Boolean> enabledControls(Component root) {
    return descendants(root).stream()
        .filter(
            component ->
                component instanceof JToggleButton
                    || component instanceof JComboBox<?>
                    || component instanceof JTextField)
        .map(Component::isEnabled)
        .toList();
  }

  private static List<Component> descendants(Component root) {
    List<Component> result = new ArrayList<>();
    result.add(root);
    if (root instanceof Container container) {
      for (Component child : container.getComponents()) result.addAll(descendants(child));
    }
    return result;
  }
}
