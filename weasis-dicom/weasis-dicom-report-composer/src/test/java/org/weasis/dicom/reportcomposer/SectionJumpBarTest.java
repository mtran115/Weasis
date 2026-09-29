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

import java.awt.Component;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.SpineRegion;

class SectionJumpBarTest {
  @Test
  void sectionsAreMarkedByTheirGeneratedFindingKeys() {
    Set<String> keys = Set.of("level:C6-7", "overview:SPONDYLOSIS");
    assertTrue(SectionJumpBar.hasFindings(keys, List.of("level:C6-7", "listhesis:C6-7")));
    assertFalse(SectionJumpBar.hasFindings(keys, List.of("level:C5-6", "listhesis:C5-6")));
    assertTrue(SectionJumpBar.hasFindings(keys, List.of("overview:", "degenerative")));
  }

  @Test
  void refreshAddsADotOnlyToSectionsWithFindings() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          var bar =
              new SectionJumpBar(
                  List.of(
                      new SectionJumpBar.Section("C5-6", new JPanel(), List.of("level:C5-6")),
                      new SectionJumpBar.Section("C6-7", new JPanel(), List.of("level:C6-7"))),
                  () -> Set.of("level:C6-7"));
          bar.refresh();
          assertEquals(List.of("C5-6", "C6-7 •"), bar.labels());
        });
  }

  @Test
  void jumpingPutsTheSectionAtTheTopWithoutScrollingPastTheEnd() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          JPanel view = new JPanel(null);
          view.setSize(300, 1000);
          JPanel middle = new JPanel();
          middle.setBounds(0, 600, 300, 50);
          JPanel last = new JPanel();
          last.setBounds(0, 950, 300, 50);
          view.add(middle);
          view.add(last);
          JViewport viewport = new JViewport();
          viewport.setView(view);
          viewport.setSize(300, 200);
          var bar =
              new SectionJumpBar(
                  List.of(
                      new SectionJumpBar.Section("Middle", middle, List.of()),
                      new SectionJumpBar.Section("Last", last, List.of())),
                  Set::of);

          bar.jumpTo(new SectionJumpBar.Section("Middle", middle, List.of()));
          assertEquals(600, viewport.getViewPosition().y);
          bar.jumpTo(new SectionJumpBar.Section("Last", last, List.of()));
          assertEquals(800, viewport.getViewPosition().y);
        });
  }

  @Test
  void buttonsWrapIntoMoreRowsAsTheHeaderNarrows() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          var bar = new SpineFormPanel(SpineRegion.CERVICAL).jumpBar();
          var pane = new JScrollPane(new JPanel());
          pane.setColumnHeaderView(bar);
          int wide = bar.getPreferredSize().width + 40;

          assertEquals(1, rowsAt(pane, bar, wide));
          int narrowRows = rowsAt(pane, bar, wide / 3);
          assertTrue(narrowRows > 1);
          assertEquals(bar.getPreferredSize().height, bar.getHeight());
          for (Component button : bar.getComponents()) {
            assertTrue(button.getX() + button.getWidth() <= bar.getWidth());
            assertTrue(button.getY() + button.getHeight() <= bar.getHeight());
          }
          assertEquals(4, bar.columnsFor(wide * 4 / 7));
        });
  }

  /** Lays the pane out at {@code width}, including the pass a resize event would request. */
  private static int rowsAt(JScrollPane pane, SectionJumpBar bar, int width) {
    pane.setSize(width, 400);
    layOut(pane, bar);
    bar.invalidate();
    layOut(pane, bar);
    return (int) Arrays.stream(bar.getComponents()).mapToInt(Component::getY).distinct().count();
  }

  // validate() needs a native peer, which headless tests do not have.
  private static void layOut(JScrollPane pane, SectionJumpBar bar) {
    pane.doLayout();
    pane.getColumnHeader().doLayout();
    bar.doLayout();
  }

  @Test
  void spineShoulderAndKneeFormsOfferTheirSections() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          var cervical = new SpineFormPanel(SpineRegion.CERVICAL).jumpBar();
          cervical.refresh();
          assertEquals(
              List.of("Alignment", "Degenerative", "C2-3", "C3-4", "C4-5", "C5-6", "C6-7", "C7-T1"),
              cervical.labels());
          var shoulder = new ShoulderFormPanel().jumpBar();
          shoulder.refresh();
          assertEquals(
              List.of("Bursae/AC", "Rotator cuff", "Labrum", "Biceps", "Free text"),
              shoulder.labels());
          var knee = new KneeFormPanel().jumpBar();
          knee.refresh();
          assertEquals(8, knee.labels().size());
        });
  }
}
