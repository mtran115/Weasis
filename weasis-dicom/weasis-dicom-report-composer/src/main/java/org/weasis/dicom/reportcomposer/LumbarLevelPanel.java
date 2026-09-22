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

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.prefs.Preferences;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.ExamTemplate;

/** A quiet, optional pilot: asynchronous results never focus, scroll, or change the report. */
final class LumbarLevelPanel extends JPanel implements AutoCloseable {
  private final LumbarLevelService service;
  private final Supplier<Optional<DicomContextReader.Selection>> selection;
  private final LumbarLevelNavigation navigation = new LumbarLevelNavigation();
  private final Preferences preferences = Preferences.userNodeForPackage(LumbarLevelPanel.class);
  private final JCheckBox automatic = new JCheckBox("Background mapping");
  private final JCheckBox overlay = new JCheckBox("Show levels");
  private final JLabel status = new JLabel(" ");
  private final JButton review = new JButton("Review…");
  private final JButton confirm = new JButton("Confirm");
  private final JButton skip = new JButton("Skip");
  private final JButton retry = new JButton("Retry");
  private final JButton[] levels = new JButton[LumbarLevelMap.MAX_POINTS];
  private final Timer debounce;
  private String studyKey;
  private String studyUid;
  private long generation;
  private LumbarLevelService.Result result;
  private LumbarLevelService.Feedback feedback;
  private boolean saving;
  private boolean closed;

  LumbarLevelPanel(Supplier<Optional<DicomContextReader.Selection>> selection) {
    this(selection, new LumbarLevelService());
  }

  LumbarLevelPanel(
      Supplier<Optional<DicomContextReader.Selection>> selection, LumbarLevelService service) {
    super(new BorderLayout(3, 3));
    this.selection = selection;
    this.service = service;
    setBorder(BorderFactory.createTitledBorder("Lumbar level mapping · pilot"));
    var top = new JPanel(new GridLayout(0, 1));
    var options = new JPanel(new FlowLayout(FlowLayout.LEADING, 3, 0));
    automatic.setSelected(preferences.getBoolean("lumbar.background", true));
    options.add(automatic);
    options.add(overlay);
    top.add(options);
    top.add(status);
    add(top, BorderLayout.NORTH);
    var actions = new JPanel(new FlowLayout(FlowLayout.LEADING, 3, 0));
    for (var button : List.of(review, confirm, skip, retry)) {
      button.setFocusable(false);
      actions.add(button);
    }
    confirm.setToolTipText(
        "Confirm the displayed map using your reporting convention; Review offers other numbering bases.");
    add(actions, BorderLayout.CENTER);
    var jumps = new JPanel(new GridLayout(2, 5, 3, 3));
    for (int i = 0; i < levels.length; i++) {
      int index = i;
      levels[i] = new JButton("—");
      levels[i].setFocusable(false);
      levels[i].setToolTipText("Jump to this level in an open axial series after confirmation");
      levels[i].addActionListener(
          event -> {
            if (result != null
                && isConfirmed()
                && index < points().size()
                && !navigation.jump(result.map(), points().get(index)))
              status.setText("Open a matching axial series to jump.");
          });
      jumps.add(levels[i]);
    }
    add(jumps, BorderLayout.SOUTH);
    debounce = new Timer(1500, event -> request());
    debounce.setRepeats(false);
    automatic.addActionListener(
        event -> {
          preferences.putBoolean("lumbar.background", automatic.isSelected());
          if (automatic.isSelected() && studyKey != null && result == null) debounce.restart();
          else if (!automatic.isSelected()) {
            debounce.stop();
            generation++;
            service.pause();
            if (result == null) status.setText("Background mapping paused.");
          }
        });
    overlay.addActionListener(
        event -> {
          refreshOverlay();
          updateControls();
        });
    retry.addActionListener(event -> request());
    review.addActionListener(
        event -> {
          if (result == null) return;
          LumbarLevelMap map = result.map();
          var decision =
              LumbarLevelReview.show(
                  this,
                  map,
                  result.preview(),
                  points(),
                  feedback == null ? "reporting_convention" : feedback.numberingBasis());
          if (decision != null && result != null && result.map() == map) save(decision);
        });
    confirm.addActionListener(
        event ->
            save(
                new LumbarLevelReview.Decision(
                    points().equals(result.map().points()) ? "accepted" : "corrected",
                    "reporting_convention",
                    points())));
    skip.addActionListener(
        event ->
            save(new LumbarLevelReview.Decision("skipped", "uncertain", result.map().points())));
    setVisible(false);
    updateControls();
  }

  void setContext(ExamTemplate exam, String key, String uid) {
    boolean lumbar =
        exam == ExamTemplate.LUMBAR_SPINE && key != null && uid != null && !uid.isBlank();
    setVisible(lumbar);
    String next = lumbar ? key : null;
    if (Objects.equals(studyKey, next) && Objects.equals(studyUid, lumbar ? uid : null)) return;
    clear();
    studyKey = next;
    studyUid = lumbar ? uid : null;
    if (lumbar) {
      status.setText(
          !service.installed()
              ? "Local model setup is required."
              : automatic.isSelected()
                  ? "Waiting for loaded series…"
                  : "Background mapping paused.");
      if (service.installed() && automatic.isSelected()) debounce.restart();
    }
    updateControls();
  }

  void clear() {
    generation++;
    service.invalidate();
    debounce.stop();
    navigation.clear();
    result = null;
    feedback = null;
    studyKey = null;
    studyUid = null;
    saving = false;
    overlay.setSelected(false);
    status.setText(" ");
    updateControls();
  }

  private void request() {
    if (closed || studyKey == null || saving) return;
    if (!service.installed()) {
      status.setText("Local model setup is required.");
      return;
    }
    var active = selection.get().filter(s -> studyUid.equals(s.context().studyInstanceUid()));
    if (active.isEmpty()) {
      status.setText("Select a lumbar image, then Retry.");
      return;
    }
    var images = StudySourceInventory.collect(active.get().canvas().getSeries());
    long token = ++generation;
    result = null;
    feedback = null;
    navigation.clear();
    updateControls();
    status.setText("Mapping locally in the background…");
    service
        .request(studyKey, studyUid, images)
        .whenComplete(
            (value, error) ->
                SwingUtilities.invokeLater(
                    () -> {
                      if (closed || token != generation) return;
                      result = value;
                      feedback = value == null ? null : value.feedback();
                      status.setText(
                          error != null || value == null
                              ? "Map unavailable — continue reading or Retry."
                              : reviewStatus());
                      updateControls();
                      refreshOverlay();
                    }));
  }

  private void save(LumbarLevelReview.Decision decision) {
    if (result == null || saving) return;
    long token = generation;
    saving = true;
    updateControls();
    status.setText("Saving review locally…");
    service
        .save(result.map(), decision.points(), decision.decision(), decision.basis())
        .whenComplete(
            (value, error) ->
                SwingUtilities.invokeLater(
                    () -> {
                      if (closed || token != generation) return;
                      saving = false;
                      if (error == null) {
                        feedback = value;
                        if (!value.confirmed()) overlay.setSelected(false);
                        status.setText(reviewStatus());
                      } else status.setText("Review was not saved — please retry.");
                      updateControls();
                      refreshOverlay();
                    }));
  }

  private String reviewStatus() {
    if (feedback == null)
      return result.map().numberingUncertain()
          ? "Numbering uncertain—review required."
          : "Provisional map ready — review numbering.";
    return switch (feedback.decision()) {
      case "accepted", "corrected" -> "Confirmed locally · click a level to jump.";
      case "uncertain" -> "Numbering uncertain · excluded from confirmed labels.";
      default -> "Map skipped · no confirmed label recorded.";
    };
  }

  private boolean isConfirmed() {
    return feedback != null && feedback.confirmed();
  }

  private List<LumbarLevelMap.Landmark> points() {
    return feedback == null ? result.map().points() : feedback.points();
  }

  void refreshOverlay() {
    if (result != null && overlay.isSelected() && isVisible())
      navigation.show(result.map(), points(), isConfirmed());
    else navigation.clear();
  }

  private void updateControls() {
    boolean ready = result != null && !saving;
    review.setEnabled(ready);
    skip.setEnabled(ready);
    overlay.setEnabled(ready);
    confirm.setEnabled(
        ready
            && overlay.isSelected()
            && !isConfirmed()
            && !result.map().numberingUncertain()
            && result.map().reviewReasons().size() <= 1);
    retry.setEnabled(studyKey != null && !saving);
    for (int i = 0; i < levels.length; i++)
      if (levels[i] != null) {
        boolean present = result != null && i < points().size();
        levels[i].setText(present ? points().get(i).displayLabel(i) : "—");
        levels[i].setEnabled(present && isConfirmed() && !saving);
      }
  }

  @Override
  public void removeNotify() {
    navigation.clear();
    super.removeNotify();
  }

  @Override
  public void close() {
    closed = true;
    clear();
    service.close();
  }
}
