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

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.text.JTextComponent;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.ui.editor.image.DefaultView2d;

/**
 * A see-through copy of the visible structured form over viewport 2. Clicking a row pins it so the
 * form's keys reach it while the pointer is on an image; the wheel scrolls the HUD only over it.
 */
final class FormHud extends JComponent {
  /** One structured form as the HUD shows and edits it. */
  interface Model {
    JComponent form();

    String title();

    List<Row> rows();

    /** Adds the chips for the pinned {@code target}. */
    void chips(Object target, Chips chips);

    Object pinned();

    void pin(Object target);

    boolean hasPending();

    String helpText();

    /** Runs a chip action on the pinned section with keyboard undo. */
    void editPinned(Runnable action);

    void setStateListener(Runnable listener);
  }

  record Row(Object target, String name, String summary) {}

  /** Chip rows for the pinned section, laid out left to right and wrapped at the HUD width. */
  interface Chips {
    void chip(String text, boolean selected, boolean enabled, Runnable action);

    default void chip(String text, boolean selected, Runnable action) {
      chip(text, selected, true, action);
    }

    void label(String text);

    /** Extra space between groups on one line. */
    void gap();

    void newLine();
  }

  private static final String POSITION_KEY = "reportcomposer.spineHud.position";
  private static final int VIEWPORT = 2;
  private static final int WIDTH = 260;
  private static final int MARGIN = 10;
  private static final int PAD = 6;
  private static final int HEADER = 17;
  private static final int ROW = 15;
  private static final int CHIP = 16;
  private static final int GAP = 3;
  private static final int INDENT = 8;
  private static final int FLASH_MS = 900;
  private static final Color BLUE = new Color(59, 130, 246);
  private static final Color TEXT = new Color(236, 240, 245);
  private static final Color DIM = new Color(150, 160, 175);

  private final Timer attachTimer = new Timer(400, event -> attach());
  private final Timer flashTimer = new Timer(FLASH_MS + 50, event -> repaint());
  private final Set<Model> watched = new HashSet<>();
  private final ComponentListener canvasResize =
      new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent event) {
          place();
        }
      };
  private final Font font;
  private final Font bold;
  private Model model;
  private DefaultView2d<?> canvas;
  private List<Item> items = List.of();
  private int contentHeight;
  private double scroll;
  private boolean hover;
  private boolean collapsed;
  private boolean revealPinned;
  private boolean refreshQueued;
  private long flashUntil;
  private Point offset;
  private Point dragStart;
  private String stateSignature = "";
  private long version;
  private BufferedImage cache;
  private Look cachedLook;

  FormHud() {
    Font base = UIManager.getFont("Label.font");
    font = (base == null ? new Font(Font.SANS_SERIF, Font.PLAIN, 10) : base).deriveFont(10f);
    bold = font.deriveFont(Font.BOLD);
    setOpaque(false);
    setFocusable(false);
    flashTimer.setRepeats(false);
    MouseAdapter mouse = new Mouse();
    addMouseListener(mouse);
    addMouseMotionListener(mouse);
    addMouseWheelListener(mouse);
  }

  /** Shows the HUD for {@code next}, or hides it when null. */
  void setModel(Model next) {
    if (model == next) return;
    model = next;
    scroll = 0;
    if (next != null && watched.add(next)) {
      ComposerFormState.watch(next.form(), () -> formChanged(next));
      next.setStateListener(() -> stateChanged(next));
    }
    if (next == null) attachTimer.stop();
    else attachTimer.start();
    attach();
    refresh();
  }

  private void formChanged(Model source) {
    if (source != model) return;
    flashUntil = System.currentTimeMillis() + FLASH_MS;
    flashTimer.restart();
    refresh();
  }

  private void stateChanged(Model source) {
    if (source != model) return;
    String signature =
        System.identityHashCode(source.pinned())
            + "|"
            + (source.hasPending() ? source.helpText() : "");
    if (signature.equals(stateSignature)) return;
    stateSignature = signature;
    revealPinned = true;
    refresh();
  }

  private void attach() {
    DefaultView2d<?> target = model != null && model.form().isVisible() ? targetCanvas() : null;
    if (target == canvas) return;
    if (canvas != null) {
      canvas.removeComponentListener(canvasResize);
      canvas.remove(this);
      canvas.repaint();
    }
    canvas = target;
    if (canvas != null) {
      canvas.add(this);
      canvas.addComponentListener(canvasResize);
      place();
    }
  }

  private static DefaultView2d<?> targetCanvas() {
    var canvases = DicomContextReader.visibleCanvases();
    return canvases.stream()
        .filter(viewport -> viewport.viewportNumber() == VIEWPORT)
        .findFirst()
        .or(() -> canvases.stream().findFirst())
        .map(DicomContextReader.ViewportCanvas::canvas)
        .orElse(null);
  }

  private void refresh() {
    if (refreshQueued) return;
    refreshQueued = true;
    SwingUtilities.invokeLater(
        () -> {
          refreshQueued = false;
          place();
          repaint();
        });
  }

  private void place() {
    if (canvas == null || model == null) return;
    if (offset == null) offset = savedOffset();
    int width = Math.min(WIDTH, Math.max(120, canvas.getWidth() - 2 * MARGIN));
    items = collapsed ? List.of() : layout(width - 2 * PAD);
    contentHeight =
        items.stream().mapToInt(item -> item.bounds.y + item.bounds.height).max().orElse(0);
    int x = clamp(offset.x, 0, Math.max(0, canvas.getWidth() - width));
    int y = clamp(offset.y, 0, Math.max(0, canvas.getHeight() - HEADER));
    int wanted = collapsed ? HEADER : HEADER + contentHeight + PAD;
    int height = Math.max(HEADER, Math.min(wanted, canvas.getHeight() - y - MARGIN));
    setBounds(x, y, width, height);
    if (revealPinned) revealPinnedRow();
    revealPinned = false;
    scroll = Math.max(0, Math.min(scroll, contentHeight - bodyHeight()));
    version++;
  }

  private int bodyHeight() {
    return Math.max(0, getHeight() - HEADER - PAD);
  }

  private void revealPinnedRow() {
    Object pinned = model.pinned();
    int top = Integer.MAX_VALUE;
    int bottom = 0;
    for (Item item : items) {
      if (pinned != null && item.owner == pinned) {
        top = Math.min(top, item.bounds.y);
        bottom = Math.max(bottom, item.bounds.y + item.bounds.height);
      }
    }
    if (bottom == 0) return;
    if (top < scroll) scroll = top;
    else if (bottom > scroll + bodyHeight()) scroll = Math.min(top, bottom - bodyHeight());
  }

  private List<Item> layout(int width) {
    List<Item> laid = new ArrayList<>();
    FontMetrics boldMetrics = getFontMetrics(bold);
    Object pinned = model.pinned();
    List<Row> rows = model.rows();
    int nameWidth =
        rows.stream().mapToInt(row -> boldMetrics.stringWidth(row.name())).max().orElse(0) + 10;
    int y = 0;
    for (Row row : rows) {
      boolean active = row.target() == pinned;
      laid.add(
          new Item(
              new Rectangle(0, y, width, ROW),
              Kind.ROW,
              row.name(),
              row.summary(),
              nameWidth,
              active,
              true,
              row.target(),
              () -> model.pin(active ? null : row.target())));
      y += ROW;
      if (active) {
        Flow flow = new Flow(laid, row.target(), width, y + 2, getFontMetrics(font));
        model.chips(row.target(), flow);
        y = flow.end() + 3;
      }
    }
    if (pinned != null && model.hasPending()) {
      for (String line : model.helpText().split("\n")) {
        laid.add(
            new Item(
                new Rectangle(0, y, width, 13), Kind.HINT, line, "", 0, false, true, pinned, null));
        y += 13;
      }
    }
    return laid;
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    if (model == null || getWidth() <= 0 || getHeight() <= 0) return;
    Graphics2D g = (Graphics2D) graphics;
    boolean bright = hover || System.currentTimeMillis() < flashUntil;
    Look look =
        new Look(getWidth(), getHeight(), g.getTransform().getScaleX(), bright, scroll, version);
    // Drawing faint antialiased text straight onto the viewport stalls the GPU on every slice;
    // render it off-screen only when the HUD changes and blit the image instead.
    if (!look.equals(cachedLook)) {
      cache = render(look);
      cachedLook = look;
    }
    g.drawImage(cache, 0, 0, getWidth(), getHeight(), null);
  }

  private BufferedImage render(Look look) {
    BufferedImage image =
        new BufferedImage(
            (int) Math.ceil(look.width() * look.scale()),
            (int) Math.ceil(look.height() * look.scale()),
            BufferedImage.TYPE_INT_ARGB_PRE);
    Graphics2D g = image.createGraphics();
    try {
      g.scale(look.scale(), look.scale());
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g.setColor(new Color(10, 12, 16, look.bright() ? 205 : 13));
      g.fillRoundRect(0, 0, look.width(), look.height(), 9, 9);
      g.setComposite(AlphaComposite.SrcOver.derive(look.bright() ? 1f : 0.1f));
      paintHeader(g);
      Graphics2D body = (Graphics2D) g.create(PAD, HEADER, look.width() - 2 * PAD, bodyHeight());
      try {
        body.translate(0, -(int) Math.round(scroll));
        items.forEach(item -> paintItem(body, item));
      } finally {
        body.dispose();
      }
      if (contentHeight > bodyHeight()) paintScrollbar(g);
    } finally {
      g.dispose();
    }
    return image;
  }

  /** Everything the rendered HUD image depends on. */
  private record Look(
      int width, int height, double scale, boolean bright, double scroll, long version) {}

  private void paintHeader(Graphics2D g) {
    Object pinned = model.pinned();
    String pinnedName =
        model.rows().stream()
            .filter(row -> row.target() == pinned)
            .map(Row::name)
            .findFirst()
            .orElse(null);
    g.setFont(bold);
    g.setColor(TEXT);
    g.drawString(model.title() + (pinnedName == null ? "" : "  ·  " + pinnedName), PAD, HEADER - 5);
    g.setColor(DIM);
    g.setFont(font);
    String hint = collapsed ? "+" : pinned == null ? "click a row   –" : "Esc release   –";
    g.drawString(hint, getWidth() - PAD - g.getFontMetrics().stringWidth(hint), HEADER - 5);
  }

  private void paintItem(Graphics2D g, Item item) {
    Rectangle r = item.bounds;
    FontMetrics fm = g.getFontMetrics(font);
    switch (item.kind) {
      case ROW -> {
        if (item.selected) {
          g.setColor(new Color(BLUE.getRed(), BLUE.getGreen(), BLUE.getBlue(), 80));
          g.fillRoundRect(r.x, r.y, r.width, r.height, 5, 5);
          g.setColor(BLUE);
          g.fillRect(r.x, r.y + 2, 2, r.height - 4);
        }
        g.setFont(bold);
        g.setColor(TEXT);
        g.drawString(item.text, r.x + 5, r.y + 11);
        g.setFont(font);
        int textX = r.x + 5 + item.nameWidth;
        boolean empty = item.detail.isBlank();
        g.setColor(empty ? DIM : TEXT);
        g.drawString(
            ellipsize(empty ? "—" : item.detail, r.width - (textX - r.x) - 4, fm), textX, r.y + 11);
      }
      case CHIP -> {
        Color fill = item.selected ? BLUE : new Color(255, 255, 255, item.enabled ? 34 : 12);
        g.setColor(fill);
        g.fillRoundRect(r.x, r.y, r.width, r.height, 7, 7);
        g.setColor(
            item.selected ? BLUE.brighter() : new Color(255, 255, 255, item.enabled ? 70 : 25));
        g.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1, 7, 7);
        g.setFont(font);
        g.setColor(item.enabled ? TEXT : DIM);
        g.drawString(item.text, r.x + (r.width - fm.stringWidth(item.text)) / 2, r.y + 11);
      }
      case LABEL -> {
        g.setFont(bold);
        g.setColor(DIM);
        g.drawString(item.text, r.x, r.y + 11);
      }
      case HINT -> {
        g.setFont(font);
        g.setColor(new Color(147, 197, 253));
        g.drawString(ellipsize(item.text, r.width, fm), r.x + 5, r.y + 10);
      }
    }
  }

  private void paintScrollbar(Graphics2D g) {
    int track = bodyHeight();
    int thumb = Math.max(16, track * track / Math.max(1, contentHeight));
    int top = HEADER + (int) ((track - thumb) * (scroll / Math.max(1, contentHeight - track)));
    g.setColor(new Color(255, 255, 255, 90));
    g.fillRoundRect(getWidth() - 4, top, 3, thumb, 3, 3);
  }

  private static String ellipsize(String text, int width, FontMetrics fm) {
    if (fm.stringWidth(text) <= width) return text;
    String cut = text;
    while (!cut.isEmpty() && fm.stringWidth(cut + "…") > width)
      cut = cut.substring(0, cut.length() - 1);
    return cut + "…";
  }

  private Item itemAt(Point point) {
    if (point.y < HEADER || point.y > HEADER + bodyHeight()) return null;
    Point content = new Point(point.x - PAD, point.y - HEADER + (int) Math.round(scroll));
    return items.stream()
        .filter(item -> item.action != null && item.enabled && item.bounds.contains(content))
        .findFirst()
        .orElse(null);
  }

  private void run(Item item) {
    // Keys must reach the pinned section, not a composer text box that kept focus.
    if (KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner()
            instanceof JTextComponent
        && canvas != null) canvas.requestFocusInWindow();
    if (item.kind == Kind.CHIP) model.editPinned(item.action);
    else item.action.run();
    refresh();
  }

  private static Point savedOffset() {
    String saved = GuiUtils.getUICore().getLocalPersistence().getProperty(POSITION_KEY, "");
    String[] parts = saved.split(",");
    try {
      if (parts.length == 2)
        return new Point(Integer.parseInt(parts[0].strip()), Integer.parseInt(parts[1].strip()));
    } catch (NumberFormatException ignored) {
      // Fall back to the default corner.
    }
    return new Point(MARGIN, MARGIN);
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  private enum Kind {
    ROW,
    CHIP,
    LABEL,
    HINT
  }

  private record Item(
      Rectangle bounds,
      Kind kind,
      String text,
      String detail,
      int nameWidth,
      boolean selected,
      boolean enabled,
      Object owner,
      Runnable action) {}

  private static final class Flow implements Chips {
    private final List<Item> items;
    private final Object owner;
    private final int width;
    private final FontMetrics fm;
    private int x = INDENT;
    private int y;
    private boolean used;

    Flow(List<Item> items, Object owner, int width, int y, FontMetrics fm) {
      this.items = items;
      this.owner = owner;
      this.width = width;
      this.y = y;
      this.fm = fm;
    }

    @Override
    public void chip(String text, boolean selected, boolean enabled, Runnable action) {
      place(text, Kind.CHIP, selected, enabled, action, Math.max(16, fm.stringWidth(text) + 9));
    }

    @Override
    public void label(String text) {
      place(text, Kind.LABEL, false, true, null, fm.stringWidth(text) + 5);
    }

    @Override
    public void gap() {
      x += 7;
    }

    @Override
    public void newLine() {
      if (x > INDENT) {
        x = INDENT;
        y += CHIP + GAP;
      }
    }

    private void place(
        String text, Kind kind, boolean selected, boolean enabled, Runnable action, int w) {
      if (x + w > width && x > INDENT) newLine();
      items.add(
          new Item(
              new Rectangle(x, y, w, CHIP), kind, text, "", 0, selected, enabled, owner, action));
      x += w + GAP;
      used = true;
    }

    int end() {
      return used ? y + CHIP : y;
    }
  }

  private final class Mouse extends MouseAdapter {
    @Override
    public void mouseEntered(MouseEvent event) {
      hover = true;
      repaint();
    }

    @Override
    public void mouseExited(MouseEvent event) {
      if (dragStart != null) return;
      hover = false;
      repaint();
    }

    @Override
    public void mousePressed(MouseEvent event) {
      if (!SwingUtilities.isLeftMouseButton(event)) return;
      if (event.getY() < HEADER) {
        if (event.getX() > getWidth() - PAD - 14) {
          collapsed = !collapsed;
          refresh();
        } else {
          dragStart = event.getPoint();
        }
        return;
      }
      Item item = itemAt(event.getPoint());
      if (item != null) run(item);
    }

    @Override
    public void mouseDragged(MouseEvent event) {
      if (dragStart == null || canvas == null) return;
      offset =
          new Point(
              clamp(getX() + event.getX() - dragStart.x, 0, canvas.getWidth() - getWidth()),
              clamp(getY() + event.getY() - dragStart.y, 0, canvas.getHeight() - HEADER));
      place();
      canvas.repaint();
    }

    @Override
    public void mouseReleased(MouseEvent event) {
      if (dragStart == null) return;
      dragStart = null;
      GuiUtils.getUICore()
          .getLocalPersistence()
          .setProperty(POSITION_KEY, offset.x + "," + offset.y);
      if (!contains(event.getPoint())) {
        hover = false;
        repaint();
      }
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent event) {
      double max = Math.max(0, contentHeight - bodyHeight());
      scroll = Math.max(0, Math.min(max, scroll + event.getPreciseWheelRotation() * ROW));
      repaint();
    }
  }
}
