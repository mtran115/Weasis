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

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.JToggleButton;
import org.weasis.dicom.reportcomposer.ChordForm.Target;

/**
 * Typed chords for a form's frequent findings. One digit, or a group letter and a digit, picks a
 * structure that stays until the next one; the keys after it set that structure's findings.
 */
final class FormChordEngine implements ChordGrammar {
  /** Keys a one-button structure, which its digit sets without a further key. */
  static final char WHOLE = '+';

  private final ChordForm form;
  private final List<Target> structures;
  private final Map<Character, List<Target>> groups;
  private final Map<Character, List<Target>> sides;
  private final Map<Target, List<String>> typed = new IdentityHashMap<>();
  private Target target;
  private char side;
  private String pending = "";

  FormChordEngine(ChordForm form) {
    this.form = form;
    sides = form.sidedTargets();
    structures = sides.isEmpty() ? form.chordTargets() : sides.values().iterator().next();
    groups = form.prefixedTargets();
  }

  @Override
  public Outcome type(char key) {
    if (Character.isDigit(key) && pending.isEmpty()) return typeStructure(key - '0');
    if (pending.length() == 1 && groups.containsKey(pending.charAt(0))) return typeGrouped(key);
    if (pending.isEmpty() && sides.containsKey(key)) return typeSide(key);
    if (pending.isEmpty() && groups.containsKey(key)) {
      pending = String.valueOf(key);
      return Outcome.PENDING;
    }
    if (target == null) return Outcome.REJECTED;
    if (pending.equals("d")) {
      if (key < '0' || key > '0' + target.degrees().size()) return Outcome.REJECTED;
      pending = "";
      set("d" + key);
      return Outcome.APPLIED;
    }
    // Without degrees, d is free to be one of the structure's letters.
    if (key == 'd' && !target.degrees().isEmpty()) {
      pending = "d";
      return Outcome.PENDING;
    }
    if (key == 'x') {
      if (target.text() == null || !target.text().isEnabled()) return Outcome.REJECTED;
      ComposerTextEditing.edit(target.text());
      return Outcome.APPLIED;
    }
    JToggleButton button = target.keys().get(key);
    if (button == null || !button.isEnabled()) return Outcome.REJECTED;
    set(String.valueOf(key));
    return Outcome.APPLIED;
  }

  private Outcome typeStructure(int digit) {
    if (digit == 0) {
      ComposerTextEditing.edit(form.freeText());
      return Outcome.APPLIED;
    }
    if (digit > structures.size() || (!sides.isEmpty() && side == 0)) return Outcome.REJECTED;
    target = (sides.isEmpty() ? structures : sides.get(side)).get(digit - 1);
    if (target.keys().containsKey(WHOLE)) set(String.valueOf(WHOLE));
    return Outcome.APPLIED;
  }

  /** The current structure follows to the new side. */
  private Outcome typeSide(char key) {
    if (target != null) target = sides.get(key).get(sides.get(side).indexOf(target));
    side = key;
    return Outcome.APPLIED;
  }

  private Outcome typeGrouped(char key) {
    List<Target> group = groups.get(pending.charAt(0));
    int index = key == '0' ? 9 : key - '1';
    if (!Character.isDigit(key) || index >= group.size()) return Outcome.REJECTED;
    pending = "";
    target = group.get(index);
    return Outcome.APPLIED;
  }

  @Override
  public boolean backspace() {
    if (!pending.isEmpty()) {
      pending = "";
      return true;
    }
    List<String> tokens = tokens();
    if (tokens.isEmpty()) return false;
    release(button(tokens.removeLast()));
    typed.put(target, tokens);
    return true;
  }

  @Override
  public boolean escape() {
    if (pending.isEmpty()) return false;
    pending = "";
    return true;
  }

  @Override
  public void reset() {
    target = null;
    side = 0;
    pending = "";
    typed.clear();
  }

  @Override
  public boolean enabled() {
    return form.isEnabled();
  }

  @Override
  public String pending() {
    return pending;
  }

  String structureName() {
    return target == null ? "" : target.name();
  }

  /**
   * The structure's findings as tokens, in the order typed. Findings set in the form follow in form
   * order, and tokens the form no longer holds are left out.
   */
  List<String> tokens() {
    if (target == null) return new ArrayList<>();
    List<String> held = held();
    List<String> tokens = new ArrayList<>(typed.getOrDefault(target, List.of()));
    tokens.retainAll(held);
    held.stream().filter(token -> !tokens.contains(token)).forEach(tokens::add);
    return tokens;
  }

  String reading() {
    return tokens().stream()
        .map(token -> token.charAt(0) == WHOLE ? "set" : button(token).getText().toLowerCase())
        .collect(Collectors.joining(", "));
  }

  @Override
  public String lineText() {
    if (!sides.isEmpty() && side == 0) return "chord · type a side, r or l";
    if (target == null) return "chord · type a structure, 1 to " + structures.size();
    StringBuilder text = new StringBuilder(target.name()).append("  ");
    text.append(String.join(" ", tokens()));
    if (!pending.isEmpty()) text.append(' ').append(pending).append('_');
    String reading = reading();
    if (!reading.isEmpty()) text.append("   ·   ").append(reading);
    return text.toString().stripTrailing();
  }

  private void set(String token) {
    List<String> tokens = tokens();
    if (isDegree(token)) {
      tokens.removeIf(other -> isDegree(other));
      target.degrees().stream().filter(JToggleButton::isSelected).forEach(FormChordEngine::release);
    }
    if (!token.equals("d0")) {
      JToggleButton button = button(token);
      if (!button.isSelected()) button.doClick(0);
      if (!tokens.contains(token)) tokens.add(token);
    }
    typed.put(target, tokens);
  }

  /** A button greyed out by another choice no longer takes clicks. */
  private static void release(JToggleButton button) {
    if (!button.isSelected()) return;
    if (button.isEnabled()) button.doClick(0);
    else button.setSelected(false);
  }

  private static boolean isDegree(String token) {
    return token.length() == 2 && token.charAt(0) == 'd';
  }

  private JToggleButton button(String token) {
    return isDegree(token)
        ? target.degrees().get(token.charAt(1) - '1')
        : target.keys().get(token.charAt(0));
  }

  private List<String> held() {
    List<String> tokens = new ArrayList<>();
    for (int i = 0; i < target.degrees().size(); i++) {
      if (target.degrees().get(i).isSelected()) tokens.add("d" + (i + 1));
    }
    target
        .keys()
        .forEach(
            (key, button) -> {
              if (button.isSelected()) tokens.add(String.valueOf(key));
            });
    return tokens;
  }
}
