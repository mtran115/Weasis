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
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Predicate;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ShortcutManager;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ViewCanvasOverlay;
import org.weasis.dicom.viewer2d.EventManager;

/**
 * Marking mode for the AI: its shortcut toggles it, and each left click in a viewport rings a
 * finding (or removes the ring clicked inside). The viewer's left-button tool is set to none
 * meanwhile and restored afterwards, so clicks do not change window/level or start a measurement.
 */
final class AiMarkingMode implements KeyEventDispatcher, AWTEventListener {
  private static final String OVERLAY = "ai-marks";

  private final JComponent composer;
  private final Predicate<Component> acceptsFocus;
  private final AiMarks marks;
  private final Runnable onChange;
  private final ShortcutManager shortcuts = ShortcutManager.getInstance();
  private final Map<DefaultView2d<?>, Cursor> cursors = new IdentityHashMap<>();
  private boolean installed;
  private boolean active;
  private String previousLeftAction;

  AiMarkingMode(
      JComponent composer, Predicate<Component> acceptsFocus, AiMarks marks, Runnable onChange) {
    this.composer = composer;
    this.acceptsFocus = acceptsFocus;
    this.marks = marks;
    this.onChange = onChange;
  }

  void install() {
    if (installed) return;
    installed = true;
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);
    Toolkit.getDefaultToolkit().addAWTEventListener(this, AWTEvent.MOUSE_EVENT_MASK);
  }

  void uninstall() {
    if (!installed) return;
    exit();
    installed = false;
    KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(this);
    Toolkit.getDefaultToolkit().removeAWTEventListener(this);
  }

  boolean isActive() {
    return active;
  }

  void toggle() {
    if (active) exit();
    else enter();
  }

  /** Shows existing rings on the visible viewports, as after a study or layout change. */
  void showMarks() {
    for (var viewport : DicomContextReader.visibleCanvases()) {
      attach(viewport.canvas());
    }
  }

  private void enter() {
    EventManager manager = EventManager.getInstance();
    if (manager.getSelectedView2dContainer() == null) return;
    previousLeftAction = manager.getMouseActions().getLeft();
    manager.changeLeftMouseAction(ActionW.NO_ACTION.cmd());
    active = true;
    for (var viewport : DicomContextReader.visibleCanvases()) {
      DefaultView2d<?> canvas = viewport.canvas();
      cursors.put(canvas, canvas.getCursor());
      canvas.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
      attach(canvas);
    }
    onChange.run();
  }

  private void exit() {
    if (!active) return;
    active = false;
    if (previousLeftAction != null) {
      EventManager.getInstance().changeLeftMouseAction(previousLeftAction);
      previousLeftAction = null;
    }
    cursors.forEach(DefaultView2d::setCursor);
    cursors.clear();
    onChange.run();
  }

  private void attach(DefaultView2d<?> canvas) {
    if (!ComposerOverlays.has(canvas, OVERLAY)) {
      ComposerOverlays.set(canvas, OVERLAY, new MarksOverlay(marks));
    }
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    if (event.getID() != KeyEvent.KEY_PRESSED || event.isConsumed() || !composer.isShowing()) {
      return false;
    }
    Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (!acceptsFocus.test(focus)) return false;
    boolean typing =
        focus instanceof JTextComponent
            && (event.getModifiersEx() & (InputEvent.META_DOWN_MASK | InputEvent.CTRL_DOWN_MASK))
                == 0;
    if (shortcuts.matches(ShortcutManager.ID_REPORT_COMPOSER_MARKING_MODE, event) && !typing) {
      event.consume();
      toggle();
      return true;
    }
    if (active && event.getKeyCode() == KeyEvent.VK_ESCAPE && !typing) {
      event.consume();
      exit();
      return true;
    }
    return false;
  }

  @Override
  public void eventDispatched(AWTEvent event) {
    if (!active
        || !(event instanceof MouseEvent mouse)
        || mouse.getID() != MouseEvent.MOUSE_PRESSED
        || !SwingUtilities.isLeftMouseButton(mouse)) {
      return;
    }
    Component source = mouse.getComponent();
    DefaultView2d<?> canvas =
        source instanceof DefaultView2d<?> view
            ? view
            : (DefaultView2d<?>) SwingUtilities.getAncestorOfClass(DefaultView2d.class, source);
    if (canvas == null || !(canvas.getImage() instanceof ImageElement image)) return;
    var reference = DicomContextReader.currentReference(canvas).orElse(null);
    if (reference == null) return;
    Point point = SwingUtilities.convertPoint(source, mouse.getPoint(), canvas);
    Point2D imagePoint = canvas.getImageCoordinatesFromMouse(point.x, point.y);
    double column = imagePoint.getX() / image.getRescaleX();
    double row = imagePoint.getY() / image.getRescaleY();
    if (!reference.geometry().contains(column, row)) return;
    marks.toggle(SourceDicomMetadata.text(Tag.StudyInstanceUID, image), reference, column, row);
    attach(canvas);
    canvas.repaint();
    mouse.consume();
    onChange.run();
  }

  /** Paints the current image's rings; the image reference is cached until the slice changes. */
  private static final class MarksOverlay implements ViewCanvasOverlay {
    private final AiMarks marks;
    private Object lastImage;
    private ImageReference reference;

    MarksOverlay(AiMarks marks) {
      this.marks = marks;
    }

    @Override
    public void paint(Graphics2D g, DefaultView2d<?> canvas) {
      if (!(canvas.getImage() instanceof ImageElement image)) return;
      if (lastImage != image) {
        lastImage = image;
        reference = DicomContextReader.currentReference(canvas).orElse(null);
      }
      if (reference == null) return;
      var onImage =
          marks.forImage(
              SourceDicomMetadata.text(Tag.StudyInstanceUID, image),
              reference.sopInstanceUid(),
              reference.sourceFrameIndex());
      double[] radius = AiMarks.radiusPixels(reference.geometry());
      for (AiMarks.Mark mark : onImage) {
        Point2D center =
            canvas.getMouseCoordinatesFromImage(
                mark.column() * image.getRescaleX(), mark.row() * image.getRescaleY());
        Point2D edge =
            canvas.getMouseCoordinatesFromImage(
                (mark.column() + radius[0]) * image.getRescaleX(),
                mark.row() * image.getRescaleY());
        AiMarks.paintRing(g, center.getX(), center.getY(), center.distance(edge), mark.number());
      }
    }
  }
}
