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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.JComponent;
import javax.swing.JToggleButton;
import org.weasis.dicom.reportcomposer.ShoulderFindingBuilder.CuffTearSelection;
import org.weasis.dicom.reportcomposer.ShoulderFindingBuilder.CuffTearType;
import org.weasis.dicom.reportcomposer.ShoulderFindingBuilder.Degree;
import org.weasis.dicom.reportcomposer.ShoulderFindingBuilder.LabralLocation;
import org.weasis.dicom.reportcomposer.ShoulderFormPanel.ShortcutTarget;

/** The shoulder form as the HUD shows it: chips are the form's key letters and numpad keys. */
final class ShoulderHudModel implements FormHud.Model {
  private static final Map<String, String> SHORT_NAMES =
      Map.of(
          "Subcoracoid bursitis", "Subcoracoid",
          "Subacromial/subdeltoid bursitis", "SASD bursa",
          "AC joint osteoarthrosis", "AC joint",
          "Labral tear", "Labrum",
          "Long head of biceps", "Biceps",
          "Free text", "Text");
  private static final List<Degree> DEGREES =
      List.of(Degree.MINIMAL, Degree.MILD, Degree.MODERATE, Degree.SEVERE);

  private final ShoulderFormPanel form;

  ShoulderHudModel(ShoulderFormPanel form) {
    this.form = form;
  }

  @Override
  public JComponent form() {
    return form;
  }

  @Override
  public String title() {
    return "Shoulder";
  }

  @Override
  public List<FormHud.Row> rows() {
    return form.targets().stream()
        .map(
            target ->
                new FormHud.Row(
                    target,
                    SHORT_NAMES.getOrDefault(target.name(), target.name()),
                    summary(target)))
        .toList();
  }

  @Override
  public void chips(Object object, FormHud.Chips chips) {
    ShortcutTarget target = (ShortcutTarget) object;
    if (target.hasDegree()) {
      Degree current = target.currentDegree();
      for (Degree degree : DEGREES) {
        boolean selected = degree == current;
        chips.chip(
            degree == Degree.MINIMAL ? "." : Integer.toString(DEGREES.indexOf(degree)),
            selected,
            () -> target.setDegree(selected ? Degree.NONE : degree));
      }
    }
    String letters = target.hasTear() ? "ABIF HPG" : target.isLabrum() ? "ASPI C" : "";
    if (!letters.isEmpty()) chips.newLine();
    for (char letter : letters.toCharArray()) {
      if (letter == ' ') {
        chips.gap();
        continue;
      }
      JToggleButton button = target.button(letter);
      chips.chip(
          String.valueOf(letter), button.isSelected(), button.isEnabled(), () -> button.doClick(0));
    }
    chips.gap();
    chips.chip("T", false, target.textField().isEnabled(), target::editText);
  }

  @Override
  public Object pinned() {
    return form.shortcuts().pinned();
  }

  @Override
  public void pin(Object target) {
    form.shortcuts().pin((ShortcutTarget) target);
  }

  @Override
  public boolean hasPending() {
    return false;
  }

  @Override
  public String helpText() {
    return form.shortcuts().helpText();
  }

  @Override
  public void editPinned(Runnable action) {
    form.shortcuts().editPinned(action);
  }

  @Override
  public void setStateListener(Runnable listener) {
    form.shortcuts().setStateListener(listener);
  }

  static String summary(ShortcutTarget target) {
    List<String> parts = new ArrayList<>();
    Degree degree = target.currentDegree();
    if (degree != Degree.NONE)
      parts.add((target.hasTear() ? "Tendinosis " : "") + abbreviation(degree));
    if (target.hasTear()) {
      CuffTearSelection tear = target.tearSelection();
      if (tear.hasFinding()) {
        StringBuilder text =
            new StringBuilder(
                tear.types().stream()
                        .map(ShoulderHudModel::abbreviation)
                        .collect(Collectors.joining("+"))
                    + " tear");
        if (tear.types().isEmpty()) text.setLength(0);
        if (tear.highGrade()) text.append(tear.types().isEmpty() ? "HG tear" : " HG");
        if (tear.atFootprint()) text.append(" footprint");
        if (tear.backgroundTendinosis()) text.append(" bkgd tend");
        parts.add(text.toString());
        if (!tear.details().isBlank()) parts.add(SpineHudModel.quote(tear.details()));
      }
    } else if (target.isLabrum()) {
      List<String> locations = new ArrayList<>();
      for (LabralLocation location : LabralLocation.values()) {
        if (target.button(location.toString().charAt(0)).isSelected())
          locations.add(location.toString().substring(0, 1));
      }
      if (!locations.isEmpty()) parts.add(String.join("/", locations) + " tear");
      if (target.button('C').isSelected()) parts.add("Paralabral cyst");
    } else if (!target.hasDegree() && !target.textField().getText().isBlank()) {
      parts.add(SpineHudModel.quote(target.textField().getText()));
    }
    return String.join(" · ", parts);
  }

  private static String abbreviation(Degree degree) {
    return switch (degree) {
      case MINIMAL -> "min";
      case MILD -> "mild";
      case MODERATE -> "mod";
      case SEVERE -> "sev";
      case NONE -> "";
    };
  }

  private static String abbreviation(CuffTearType type) {
    return switch (type) {
      case INTERSTITIAL -> "Interstitial";
      case ARTICULAR_SURFACE -> "Articular";
      case BURSAL_SURFACE -> "Bursal";
      case FULL_THICKNESS -> "FT";
    };
  }
}
