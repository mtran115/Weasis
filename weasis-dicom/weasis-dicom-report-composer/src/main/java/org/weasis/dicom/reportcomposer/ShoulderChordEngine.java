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
import org.weasis.dicom.reportcomposer.ShoulderFindingBuilder.Degree;
import org.weasis.dicom.reportcomposer.ShoulderFormPanel.ShortcutTarget;

/**
 * Typed chords for the shoulder form. One digit picks a structure that stays until the next one;
 * the letters after it set that structure's findings, with the same letters as the hover shortcuts.
 */
final class ShoulderChordEngine implements ChordGrammar {
  static final String REFERENCE =
      """
      ~           chord mode on / off

      Structure; stays until the next one
        1 Subcoracoid bursitis
        2 Subacromial/subdeltoid bursitis
        3 AC joint osteoarthrosis
        4 Supraspinatus    5 Infraspinatus
        6 Subscapularis    7 Teres minor
        8 Labral tear
        9 Long head of biceps tenosynovitis
        0 Free text box

      d#          degree (tendinosis on a tendon)
                    1 mild  2 moderate  3 severe
                    4 minimal  0 clear

      On a tendon (4 to 7)
      a b i f     articular, bursal, interstitial,
                    full-thickness tear
      h           high-grade (partial tears)
      p           at footprint
      g           background tendinosis
      t           tear details text

      On the labrum (8)
      a s p i     anterior, superior, posterior,
                    inferior
      c           paralabral cyst

      Backspace   remove the last key or token
      Esc         drop a half-typed token

      M, the numpad and arrows still go to the viewer.
      """;

  private static final List<Degree> DEGREES =
      List.of(Degree.NONE, Degree.MILD, Degree.MODERATE, Degree.SEVERE, Degree.MINIMAL);
  private static final String FREE_TEXT = "Free text";

  private final ShoulderFormPanel form;
  private final List<ShortcutTarget> structures;
  private final Map<ShortcutTarget, List<String>> typed = new IdentityHashMap<>();
  private ShortcutTarget target;
  private String pending = "";

  ShoulderChordEngine(ShoulderFormPanel form) {
    this.form = form;
    structures =
        form.targets().stream().filter(structure -> !structure.name().equals(FREE_TEXT)).toList();
  }

  @Override
  public Outcome type(char key) {
    if (Character.isDigit(key) && pending.isEmpty()) return typeStructure(key - '0');
    if (target == null) return Outcome.REJECTED;
    String chord = pending + key;
    if (chord.equals("d")) {
      if (!target.hasDegree()) return Outcome.REJECTED;
      pending = chord;
      return Outcome.PENDING;
    }
    if (chord.equals("t")) {
      if (!target.textField().isEnabled()) return Outcome.REJECTED;
      target.editText();
      return Outcome.APPLIED;
    }
    if (!chord.matches("d[0-4]")) {
      JToggleButton button = chord.length() == 1 ? button(chord) : null;
      if (button == null || !button.isEnabled()) return Outcome.REJECTED;
    }
    pending = "";
    set(chord);
    return Outcome.APPLIED;
  }

  private Outcome typeStructure(int digit) {
    if (digit == 0) {
      form.target(FREE_TEXT).editText();
      return Outcome.APPLIED;
    }
    if (digit > structures.size()) return Outcome.REJECTED;
    target = structures.get(digit - 1);
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
    String removed = tokens.removeLast();
    if (removed.charAt(0) == 'd') {
      target.setDegree(Degree.NONE);
    } else {
      JToggleButton button = button(removed);
      // A modifier greyed out by its tear type no longer takes clicks.
      if (button.isEnabled()) button.doClick(0);
      else button.setSelected(false);
    }
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
    return tokens().stream().map(this::words).collect(Collectors.joining(", "));
  }

  @Override
  public String lineText() {
    String unfinished = pending.isEmpty() ? "" : pending + "_";
    if (target == null) return "chord · type a structure, 1 to 9";
    StringBuilder text = new StringBuilder(target.name()).append("  ");
    text.append(String.join(" ", tokens()));
    if (!unfinished.isEmpty()) text.append(' ').append(unfinished);
    String reading = reading();
    if (!reading.isEmpty()) text.append("   ·   ").append(reading);
    return text.toString().stripTrailing();
  }

  private void set(String token) {
    List<String> tokens = tokens();
    if (token.charAt(0) == 'd') {
      tokens.removeIf(other -> other.charAt(0) == 'd');
      target.setDegree(DEGREES.get(token.charAt(1) - '0'));
      if (!token.equals("d0")) tokens.add(token);
    } else {
      JToggleButton button = button(token);
      if (!button.isSelected()) button.doClick(0);
      if (!tokens.contains(token)) tokens.add(token);
    }
    typed.put(target, tokens);
  }

  private JToggleButton button(String token) {
    return target.button(Character.toUpperCase(token.charAt(0)));
  }

  private List<String> held() {
    List<String> tokens = new ArrayList<>();
    Degree degree = target.currentDegree();
    if (degree != Degree.NONE) tokens.add("d" + DEGREES.indexOf(degree));
    for (char key :
        (target.hasTear() ? "abifhpg" : target.isLabrum() ? "aspic" : "").toCharArray()) {
      if (button(String.valueOf(key)).isSelected()) tokens.add(String.valueOf(key));
    }
    return tokens;
  }

  private String words(String token) {
    if (token.charAt(0) == 'd') {
      String degree = List.of("mild", "mod", "severe", "minimal").get(token.charAt(1) - '1');
      return target.hasTear() ? degree + " tendinosis" : degree;
    }
    return button(token).getText().toLowerCase();
  }
}
