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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.reportcomposer.ShoulderFindingBuilder.Degree;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.Laterality;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.ProtrusionLocation;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.SpineRegion;

class FormHudModelTest {
  @Test
  void spineLevelRowsAbbreviateFindingsInFormOrder() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          SpineFormPanel form = new SpineFormPanel(SpineRegion.LUMBAR);
          var level = form.level("L4-5");
          assertEquals("", SpineHudModel.summary(level, form.selection()));
          level.toggle('P');
          level.toggle('B');
          level.setDiscLocations(
              EnumSet.of(ProtrusionLocation.LEFT_FORAMINAL, ProtrusionLocation.LEFT_SUBARTICULAR));
          level.setSeverity('L', 2);
          level.setLaterality('F', 'B');
          level.setLaterality('H', 'L');
          assertEquals(
              "Bulge · Prot LSA/LF · Facet B · Lig flavum L · L foramen mod",
              SpineHudModel.summary(level, form.selection()));
        });
  }

  @Test
  void spineSectionRowsListTheirSelections() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          SpineFormPanel form = new SpineFormPanel(SpineRegion.LUMBAR);
          form.alignmentSection().toggle().doClick(0);
          form.degenerativeSection().toggle().doClick(0);
          assertEquals(
              "Straightening", SpineHudModel.summary(form.alignmentSection(), form.selection()));
          assertEquals(
              "Spondylosis", SpineHudModel.summary(form.degenerativeSection(), form.selection()));
        });
  }

  @Test
  void spineChipsUseKeyLettersWithBilateralFirst() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          SpineFormPanel form = new SpineFormPanel(SpineRegion.LUMBAR);
          var level = form.level("L4-5");
          Recorder chips = new Recorder();
          new SpineHudModel(form).chips(level, chips);
          assertEquals(
              List.of(
                  "B", "P", "E", "A", "V", "|", "[C]", "1", "2", "3", "[L]", "1", "2", "3", "[R]",
                  "1", "2", "3", "|", "[F]", "B", "L", "R", "[H]", "B", "L", "R", "T"),
              chips.texts);
          chips.actions.get("B").run();
          assertTrue(level.selection().bulge());
          chips.actions.get("[F]B").run();
          assertEquals(Laterality.BILATERAL, level.selection().facetArthrosis());
        });
  }

  @Test
  void shoulderRowsShowDegreesTearsAndLabrum() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          ShoulderFormPanel form = new ShoulderFormPanel();
          var supraspinatus = form.target("Supraspinatus");
          supraspinatus.setDegree(Degree.MODERATE);
          supraspinatus.button('A').doClick(0);
          supraspinatus.button('H').doClick(0);
          assertEquals(
              "Tendinosis mod · Articular tear HG", ShoulderHudModel.summary(supraspinatus));
          var labrum = form.target("Labral tear");
          labrum.button('S').doClick(0);
          labrum.button('C').doClick(0);
          assertEquals("S tear · Paralabral cyst", ShoulderHudModel.summary(labrum));
          var model = new ShoulderHudModel(form);
          assertEquals("Labrum", model.rows().get(7).name());
          Recorder chips = new Recorder();
          model.chips(form.target("Teres minor"), chips);
          assertEquals(
              List.of(".", "1", "2", "3", "|", "A", "B", "I", "F", "H", "P", "G", "T"),
              chips.texts);
          assertTrue(chips.enabled.get("H"));
          assertFalse(chips.enabled.get("P"));
        });
  }

  /** Records chip texts; labels appear in brackets and line breaks as "|". */
  private static final class Recorder implements FormHud.Chips {
    final List<String> texts = new ArrayList<>();
    final Map<String, Runnable> actions = new LinkedHashMap<>();
    final Map<String, Boolean> enabled = new LinkedHashMap<>();
    private String label = "";

    @Override
    public void chip(String text, boolean selected, boolean isEnabled, Runnable action) {
      texts.add(text);
      actions.putIfAbsent(label + text, action);
      enabled.putIfAbsent(text, isEnabled);
    }

    @Override
    public void label(String text) {
      label = "[" + text + "]";
      texts.add(label);
    }

    @Override
    public void gap() {}

    @Override
    public void newLine() {
      label = "";
      texts.add("|");
    }
  }
}
