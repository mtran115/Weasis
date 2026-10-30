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

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.UIManager;
import org.weasis.core.api.gui.util.GuiUtils;

/** A small clock of the active study's reading time and the session average. */
final class ReadTimerLabel extends JLabel {
  private static final Duration SAVE_INTERVAL = Duration.ofSeconds(30);

  private final ReadTimeStore store;
  private final ReadClock clock;
  private final Runnable inputListener = this::onInput;
  private final Timer ticker = new Timer(250, event -> tick());
  private Instant lastSave = Instant.now();

  ReadTimerLabel(ReadTimeStore store) {
    this.store = store;
    clock = new ReadClock(store.load(Instant.now()));
    setHorizontalAlignment(SwingConstants.RIGHT);
    setFont(getFont().deriveFont(11f));
    setBorder(GuiUtils.getEmptyBorder(4, 0, 0, 4));
    setToolTipText(
        "<html>Reading time for this study. It counts while you use the keyboard, mouse or scroll"
            + " wheel in Weasis,<br>pauses after "
            + ReadClock.IDLE.toSeconds()
            + " seconds without input, and stops when the study is exported.<br>The average covers"
            + " the studies exported this session; a break over an hour starts a new one.</html>");
    InputActivity.addListener(inputListener);
    ticker.start();
    refresh(Instant.now());
  }

  /** Switches the clock to this study, or pauses it when no study is active. */
  void selectStudy(String studyUid) {
    Instant now = Instant.now();
    clock.select(studyUid == null ? null : ReadTimeStore.key(studyUid), now);
    save(now);
    refresh(now);
  }

  void studyExported(String studyUid) {
    Instant now = Instant.now();
    clock.export(ReadTimeStore.key(studyUid), now);
    save(now);
    refresh(now);
  }

  void dispose() {
    ticker.stop();
    InputActivity.removeListener(inputListener);
    save(Instant.now());
  }

  static String format(Duration duration) {
    long seconds = duration.toSeconds();
    return seconds >= 3600
        ? "%d:%02d:%02d".formatted(seconds / 3600, seconds / 60 % 60, seconds % 60)
        : "%d:%02d".formatted(seconds / 60, seconds % 60);
  }

  private void onInput() {
    clock.input(Instant.now());
  }

  private void tick() {
    Instant now = Instant.now();
    clock.tick(now);
    if (clock.state() == ReadClock.State.RUNNING
        && Duration.between(lastSave, now).compareTo(SAVE_INTERVAL) >= 0) save(now);
    refresh(now);
  }

  private void save(Instant now) {
    lastSave = now;
    store.save(clock.snapshot(now));
  }

  private void refresh(Instant now) {
    ReadClock.State state = clock.state();
    String study =
        switch (state) {
          case NONE -> "";
          case RUNNING -> format(clock.elapsed(now));
          case PAUSED -> format(clock.elapsed(now)) + " paused";
          case EXPORTED -> format(clock.elapsed(now)) + " ✓";
        };
    String session =
        clock
            .session(now)
            .map(value -> "avg " + format(value.average()) + " (" + value.studies() + ")")
            .orElse("");
    String text =
        study.isEmpty() || session.isEmpty() ? study + session : study + "  ·  " + session;
    // A blank keeps the label's height so the header does not jump.
    if (text.isEmpty()) text = " ";
    if (!text.equals(getText())) setText(text);
    Color color =
        UIManager.getColor(
            state == ReadClock.State.RUNNING ? "Label.foreground" : "Label.disabledForeground");
    if (color != null && !color.equals(getForeground())) setForeground(color);
  }

  /** Sees every key, mouse and wheel event, including keys a shortcut dispatcher later consumes. */
  private static final class InputActivity extends EventQueue {
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
    private static boolean installed;

    static synchronized void addListener(Runnable listener) {
      LISTENERS.add(listener);
      if (installed) return;
      installed = true;
      EventQueue.invokeLater(
          () -> Toolkit.getDefaultToolkit().getSystemEventQueue().push(new InputActivity()));
    }

    static void removeListener(Runnable listener) {
      LISTENERS.remove(listener);
    }

    @Override
    protected void dispatchEvent(AWTEvent event) {
      if (event instanceof InputEvent) LISTENERS.forEach(Runnable::run);
      super.dispatchEvent(event);
    }
  }
}
