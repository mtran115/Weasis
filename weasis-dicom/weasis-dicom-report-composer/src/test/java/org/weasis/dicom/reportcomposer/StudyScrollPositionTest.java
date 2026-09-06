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

import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StudyScrollPositionTest {
  private ComposerFixture composer;

  @BeforeEach
  void createComposer() throws Exception {
    onEdt(() -> composer = new ComposerFixture());
    flushEdt();
  }

  @AfterEach
  void disposeComposer() throws Exception {
    if (composer != null) {
      onEdt(() -> composer.root.removeNotify());
      flushEdt();
    }
  }

  @Test
  void newlySelectedStudyStartsAtTopAfterPreviousStudyWasScrolled() throws Exception {
    onEdt(() -> composer.study("first", 3000));
    flushEdt();
    onEdt(
        () -> {
          composer.scrollTo(640);
          composer.study("new", 2500);
        });
    flushEdt();

    onEdt(() -> composer.assertPosition(0));
  }

  @Test
  void restoresEachStudyAfterAFormHeightChangeClampsTheViewport() throws Exception {
    onEdt(() -> composer.study("long-form", 4000));
    flushEdt();
    onEdt(
        () -> {
          composer.scrollTo(1100);
          composer.study("short-form", 900);
        });
    flushEdt();
    onEdt(
        () -> {
          composer.assertPosition(0);
          composer.scrollTo(170);
          composer.study("long-form", 4000);
        });
    flushEdt();
    onEdt(
        () -> {
          composer.assertPosition(1100);
          composer.study("short-form", 900);
        });
    flushEdt();

    onEdt(() -> composer.assertPosition(170));
  }

  @Test
  void tabRoundTripPreservesPositionDespiteHiddenContentUpdates() throws Exception {
    onEdt(() -> composer.study("first", 3000));
    flushEdt();
    onEdt(
        () -> {
          composer.scrollTo(680);
          composer.tab(1);
          assertFalse(composer.scrollPane.isShowing());
          composer.scrollTo(1250);
          composer.tab(2);
          composer.scrollTo(1600);
          composer.tab(0);
        });
    flushEdt();

    onEdt(() -> composer.assertPosition(680));
  }

  @Test
  void studySwitchesOnOtherTabsKeepEachStudysComposePosition() throws Exception {
    onEdt(() -> composer.study("first", 3000));
    flushEdt();
    onEdt(
        () -> {
          composer.scrollTo(760);
          composer.tab(1);
          composer.scrollTo(1200);
          composer.study("new", 1000);
          composer.scrollTo(300);
          composer.tab(2);
          composer.tab(0);
        });
    flushEdt();
    onEdt(
        () -> {
          composer.assertPosition(0);
          composer.scrollTo(210);
          composer.tab(1);
          composer.study("first", 3000);
          composer.scrollTo(1800);
          composer.tab(0);
        });
    flushEdt();
    onEdt(
        () -> {
          composer.assertPosition(760);
          composer.study("new", 1000);
        });
    flushEdt();

    onEdt(() -> composer.assertPosition(210));
  }

  @Test
  void rapidStudySwitchesDoNotReplaceSavedPositionsWithAnUnrestoredViewport() throws Exception {
    onEdt(() -> composer.study("first", 3000));
    flushEdt();
    onEdt(
        () -> {
          composer.scrollTo(720);
          composer.study("second", 2500);
        });
    flushEdt();
    onEdt(
        () -> {
          composer.scrollTo(260);
          composer.study("first", 3000);
          composer.study("second", 2500);
          composer.study("first", 3000);
        });
    flushEdt();
    onEdt(
        () -> {
          composer.assertPosition(720);
          composer.study("second", 2500);
        });
    flushEdt();

    onEdt(() -> composer.assertPosition(260));
  }

  @Test
  void sameStudySynchronizationDoesNotResetManualScrolling() throws Exception {
    onEdt(() -> composer.study("first", 3000));
    flushEdt();
    onEdt(
        () -> {
          composer.scrollTo(555);
          composer.study("first", 3000);
        });
    flushEdt();

    onEdt(() -> composer.assertPosition(555));
  }

  @Test
  void clearingTheActiveStudyDoesNotForgetItsComposePosition() throws Exception {
    onEdt(() -> composer.study("first", 3000));
    flushEdt();
    onEdt(
        () -> {
          composer.scrollTo(740);
          composer.study(null, 1000);
        });
    flushEdt();
    onEdt(
        () -> {
          composer.assertPosition(0);
          composer.study("first", 3000);
        });
    flushEdt();

    onEdt(() -> composer.assertPosition(740));
  }

  @Test
  void hiddenComposerRestoresWhenShownAndRetainsItsPositionWhenHiddenAgain() throws Exception {
    onEdt(
        () -> {
          composer.root.setVisible(false);
          composer.study("first", 3000);
          composer.scrollTo(950);
        });
    flushEdt();
    onEdt(
        () -> {
          composer.root.setVisible(true);
          composer.root.validate();
        });
    flushEdt();
    onEdt(
        () -> {
          composer.assertPosition(0);
          composer.scrollTo(630);
          composer.root.setVisible(false);
          composer.scrollTo(1400);
          composer.root.setVisible(true);
          composer.root.validate();
        });
    flushEdt();

    onEdt(() -> composer.assertPosition(630));
  }

  private static void onEdt(Runnable action) throws Exception {
    SwingUtilities.invokeAndWait(action);
  }

  private static void flushEdt() throws Exception {
    onEdt(() -> {});
    onEdt(() -> {});
  }

  private static final class ComposerFixture {
    private final JPanel root = new JPanel(new BorderLayout());
    private final JPanel content = new JPanel();
    private final JScrollPane scrollPane = new JScrollPane(content);
    private final JTabbedPane tabs = new JTabbedPane();
    private final StudyScrollPosition position = new StudyScrollPosition(scrollPane);

    private ComposerFixture() {
      content.setPreferredSize(new Dimension(350, 3000));
      tabs.addTab("Compose", scrollPane);
      tabs.addTab("Key Images", new JPanel());
      tabs.addTab("Preview", new JPanel());
      tabs.addChangeListener(event -> position.setComposeSelected(tabs.getSelectedIndex() == 0));
      root.add(tabs, BorderLayout.CENTER);
      root.setSize(440, 540);
      root.addNotify();
      root.validate();
      assertTrue(scrollPane.isShowing());
    }

    private void study(String studyKey, int formHeight) {
      position.selectStudy(studyKey);
      content.setPreferredSize(new Dimension(350, formHeight));
      content.revalidate();
      position.restorePosition();
      root.validate();
    }

    private void tab(int index) {
      tabs.setSelectedIndex(index);
      root.validate();
    }

    private void scrollTo(int value) {
      scrollPane.getVerticalScrollBar().setValue(value);
      assertPosition(value);
    }

    private void assertPosition(int expected) {
      assertEquals(expected, scrollPane.getVerticalScrollBar().getValue());
      assertEquals(expected, scrollPane.getViewport().getViewPosition().y);
    }
  }
}
