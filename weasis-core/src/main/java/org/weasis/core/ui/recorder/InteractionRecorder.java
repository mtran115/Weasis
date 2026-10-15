/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.recorder;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.text.JTextComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.Feature;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.ShortcutManager;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.SynchEvent;
import org.weasis.core.ui.editor.image.ViewCanvas;

/**
 * Records what the reader does in the viewer and the report composer, one JSON line per action in a
 * daily file, so frequent sequences can be turned into macros. Nothing identifies the patient:
 * studies are named by a salted hash, typed text is only counted, and files are deleted after
 * {@link #RETENTION}.
 */
public final class InteractionRecorder {
  private static final Logger LOGGER = LoggerFactory.getLogger(InteractionRecorder.class);

  public static final String P_ENABLED = "interaction.recorder.enabled";

  /** The describer's key for the raw study UID; only its salted hash is written. */
  public static final String STUDY_UID = "study_uid";

  static final Duration RETENTION = Duration.ofDays(30);
  static final Duration IDLE = Duration.ofMillis(800);
  // How long after a series opens the viewer's own display and slice changes are not recorded.
  static final Duration SETTLE = Duration.ofSeconds(1);
  private static final Pattern DAY_FILE = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})\\.jsonl");

  // Display settings worth recording; pan offsets, presentation states and the like are not.
  private static final List<Feature<?>> DISPLAY_FEATURES =
      List.of(
          ActionW.WINDOW,
          ActionW.LEVEL,
          ActionW.PRESET,
          ActionW.LUT_SHAPE,
          ActionW.LUT,
          ActionW.INVERT_LUT,
          ActionW.ZOOM,
          ActionW.ROTATION,
          ActionW.FLIP,
          ActionW.FILTER);

  // Window/level values that change together and form one run.
  private static final List<String> WINDOW_LEVEL =
      List.of(
          ActionW.WINDOW.cmd(), ActionW.LEVEL.cmd(), ActionW.PRESET.cmd(), ActionW.LUT_SHAPE.cmd());

  // Study-level fields, written once when the study in view changes rather than on every line.
  private static final List<String> STUDY_FIELDS = List.of("study_desc", "body_part");

  private static volatile InteractionRecorder instance;

  private final Path directory;
  private final Clock clock;
  private final InteractionLog log;
  private final Function<MediaSeries<?>, Map<String, Object>> describer;
  private final ScheduledExecutorService writer =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "interaction-recorder");
            thread.setDaemon(true);
            return thread;
          });
  private volatile boolean enabled;

  // Used on the Swing thread only.
  private final ViewBaseline displayBaseline = new ViewBaseline(SETTLE);
  private final ViewBaseline sliceBaseline = new ViewBaseline(SETTLE);
  private int selectedView;
  private final Map<JTextComponent, String> fieldNames = new WeakHashMap<>();

  // Used on the writer thread only.
  private String salt;
  private String currentStudy;
  private boolean writeFailed;

  InteractionRecorder(
      Path directory,
      ZoneId zone,
      Clock clock,
      Function<MediaSeries<?>, Map<String, Object>> describer,
      boolean enabled) {
    this.directory = directory;
    this.clock = clock.withZone(zone);
    this.log = new InteractionLog(IDLE, zone);
    this.describer = describer;
    this.enabled = enabled;
  }

  /**
   * Starts recording once per session. {@code describer} names a series (modality, descriptions,
   * numbers, and the study UID under {@link #STUDY_UID}) without anything that identifies the
   * patient.
   */
  public static synchronized void install(Function<MediaSeries<?>, Map<String, Object>> describer) {
    if (instance != null) return;
    String choice = GuiUtils.getUICore().getLocalPersistence().getProperty(P_ENABLED, "");
    InteractionRecorder recorder =
        new InteractionRecorder(
            AppProperties.WEASIS_PATH.resolve("data/report-composer/interaction-log"),
            ZoneId.systemDefault(),
            Clock.systemDefaultZone(),
            describer,
            choice.isBlank() || Boolean.parseBoolean(choice));
    recorder.writer.execute(recorder::deleteExpiredFiles);
    Toolkit.getDefaultToolkit()
        .addAWTEventListener(event -> recorder.keyEvent((KeyEvent) event), AWTEvent.KEY_EVENT_MASK);
    Runtime.getRuntime()
        .addShutdownHook(new Thread(recorder::finish, "interaction-recorder-flush"));
    instance = recorder;
  }

  public static boolean isEnabled() {
    InteractionRecorder recorder = instance;
    return recorder != null && recorder.enabled;
  }

  public static void setEnabled(boolean enabled) {
    InteractionRecorder recorder = instance;
    if (recorder == null) return;
    GuiUtils.getUICore().getLocalPersistence().setProperty(P_ENABLED, Boolean.toString(enabled));
    recorder.enabled = enabled;
    if (!enabled) recorder.writer.execute(() -> recorder.write(recorder.log.flush()));
  }

  /** Records a one-off action, such as a quick phrase inserted or a report copied. */
  public static void record(String type, Map<String, ?> fields) {
    InteractionRecorder recorder = instance;
    if (recorder != null) recorder.add(type, null, fields, Map.of());
  }

  /** Window/level, zoom, rotation and other display changes; a drag becomes one line. */
  public static void displayChanged(SynchEvent event) {
    InteractionRecorder recorder = instance;
    if (recorder == null || !recorder.enabled || event == null || event.getView() == null) return;
    Map<String, Object> values = new LinkedHashMap<>();
    for (Feature<?> feature : DISPLAY_FEATURES) {
      if (event.getEvents().containsKey(feature.cmd())) {
        values.put(feature.cmd(), simple(event.getEvents().get(feature.cmd())));
      }
    }
    if (values.isEmpty()) return;
    ViewCanvas<?> view = event.getView();
    String viewKey = viewKey(view);
    Optional<Map<String, Object>> before =
        recorder.displayBaseline.changes(viewKey, values, recorder.clock.instant());
    if (before.isEmpty()) return;
    Map<String, Object> changed = new LinkedHashMap<>();
    before.get().keySet().forEach(name -> changed.put(name, values.get(name)));
    String group =
        WINDOW_LEVEL.containsAll(changed.keySet()) ? "wl" : String.join(",", changed.keySet());
    recorder.add(
        "display",
        "display:" + viewKey + ":" + group,
        recorder.viewFields(view, view.getSeries()),
        before.get(),
        changed);
  }

  /** Slice scrolling (one line per run), layout changes, and selecting another viewport. */
  public static void viewerEvent(SeriesViewerEvent event) {
    InteractionRecorder recorder = instance;
    if (recorder == null || !recorder.enabled || event == null) return;
    if (!(event.getSeriesViewer() instanceof ImageViewerPlugin<?> plugin)) return;
    ViewCanvas<?> view = plugin.getSelectedViewCanvas();
    switch (event.getEventType()) {
      case SELECT -> {
        if (view instanceof DefaultView2d<?> canvas && view.getSeries() != null) {
          String sliceKey = sliceKey(view, view.getSeries());
          Map<String, Object> slice = Map.of("slice", canvas.getFrameIndex() + 1);
          recorder
              .sliceBaseline
              .changes(sliceKey, slice, recorder.clock.instant())
              .ifPresent(
                  before ->
                      recorder.add(
                          "scroll",
                          "scroll:" + sliceKey,
                          recorder.viewFields(view, view.getSeries()),
                          before,
                          slice));
        }
      }
      case LAYOUT ->
          recorder.add("layout", null, Map.of("layout", plugin.getLayoutModel().getId()), Map.of());
      case SELECT_VIEW -> {
        // The viewer reselects the same viewport while loading; only a real change is recorded.
        int index = view == null ? 0 : viewIndex(view);
        if (index > 0 && index != recorder.selectedView) {
          recorder.selectedView = index;
          recorder.add("select_view", null, recorder.viewFields(view, view.getSeries()), Map.of());
        }
      }
      default -> {
        // Other events are already covered by the display and series records.
      }
    }
  }

  public static void seriesShown(ViewCanvas<?> view, MediaSeries<?> series) {
    InteractionRecorder recorder = instance;
    if (recorder == null || !recorder.enabled || view == null || series == null) return;
    Instant now = recorder.clock.instant();
    recorder.displayBaseline.reset(viewKey(view), now);
    recorder.sliceBaseline.reset(sliceKey(view, series), now);
    recorder.add("series_shown", null, recorder.viewFields(view, series), Map.of());
  }

  /**
   * A one-off action in a viewport, such as a macro or Auto W/L, recorded with the viewport and its
   * series. Display values among {@code fields} (zoom, window, level...) become what later changes
   * there are recorded from, so the reader's corrections show up as such.
   */
  public static void recordInView(ViewCanvas<?> view, String type, Map<String, ?> fields) {
    InteractionRecorder recorder = instance;
    if (recorder == null || !recorder.enabled || view == null) return;
    Map<String, Object> values = new LinkedHashMap<>();
    fields.forEach((name, value) -> values.put(name, simple(value)));
    Map<String, Object> display = new LinkedHashMap<>();
    for (Feature<?> feature : DISPLAY_FEATURES) {
      if (values.containsKey(feature.cmd())) display.put(feature.cmd(), values.get(feature.cmd()));
    }
    recorder.displayBaseline.accept(viewKey(view), display);
    Map<String, Object> line = recorder.viewFields(view, view.getSeries());
    line.putAll(values);
    recorder.add(type, null, line, Map.of());
  }

  public static void mouseToolChanged(String command) {
    record("mouse_tool", Map.of("tool", command));
  }

  void add(String type, String key, Map<String, ?> fields, Map<String, ?> value) {
    add(type, key, fields, Map.of(), value);
  }

  void add(
      String type, String key, Map<String, ?> fields, Map<String, ?> before, Map<String, ?> value) {
    if (!enabled) return;
    Instant at = clock.instant();
    InteractionLog.Action action =
        new InteractionLog.Action(
            at,
            type,
            key,
            new LinkedHashMap<>(fields),
            new LinkedHashMap<>(before),
            new LinkedHashMap<>(value));
    writer.execute(() -> append(action));
    if (key != null) {
      writer.schedule(
          () -> write(log.flushIdle(clock.instant())), IDLE.toMillis() + 50, TimeUnit.MILLISECONDS);
    }
  }

  /** Waits until everything recorded so far is written; for tests and shutdown. */
  void flush() {
    try {
      writer.submit(() -> write(log.flush())).get(2, TimeUnit.SECONDS);
    } catch (Exception e) {
      LOGGER.debug("Interaction log flush did not finish", e);
    }
  }

  private void finish() {
    flush();
    writer.shutdown();
  }

  // Runs on the writer thread. Replaces the study UID with its hash and notes a change of study.
  private void append(InteractionLog.Action action) {
    Map<String, Object> fields = action.fields();
    Object uid = fields.remove(STUDY_UID);
    if (uid != null) {
      String study = hash(uid.toString());
      if (!study.equals(currentStudy)) {
        currentStudy = study;
        Map<String, Object> studyFields = new LinkedHashMap<>();
        studyFields.put("study", study);
        for (String name : List.of("modality", "study_desc", "body_part")) {
          if (fields.containsKey(name)) studyFields.put(name, fields.get(name));
        }
        write(
            log.add(new InteractionLog.Action(action.at(), "study", null, studyFields, Map.of())));
      }
      fields.put("study", study);
    }
    STUDY_FIELDS.forEach(fields::remove);
    write(log.add(action));
  }

  private void write(List<String> lines) {
    if (lines.isEmpty()) return;
    try {
      Files.createDirectories(directory);
      Files.writeString(
          directory.resolve(LocalDate.now(clock) + ".jsonl"),
          String.join("\n", lines) + "\n",
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (IOException e) {
      if (!writeFailed) LOGGER.warn("Cannot write the interaction log in {}", directory, e);
      writeFailed = true;
    }
  }

  void deleteExpiredFiles() {
    if (!Files.isDirectory(directory)) return;
    LocalDate oldest = LocalDate.now(clock).minusDays(RETENTION.toDays());
    try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.jsonl")) {
      for (Path file : files) {
        Matcher day = DAY_FILE.matcher(file.getFileName().toString());
        if (day.matches() && LocalDate.parse(day.group(1)).isBefore(oldest))
          Files.deleteIfExists(file);
      }
    } catch (IOException | DateTimeParseException e) {
      LOGGER.warn("Cannot delete old interaction logs in {}", directory, e);
    }
  }

  // A salted hash groups a study's actions without revealing its UID; the salt never leaves this
  // computer.
  private String hash(String uid) {
    try {
      if (salt == null) salt = loadSalt();
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest((salt + uid).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest, 0, 6);
    } catch (IOException | NoSuchAlgorithmException e) {
      LOGGER.warn("Cannot hash the study for the interaction log", e);
      return "unknown";
    }
  }

  private String loadSalt() throws IOException {
    Path file = directory.resolve(".salt");
    if (Files.exists(file)) return Files.readString(file, StandardCharsets.UTF_8).strip();
    byte[] bytes = new byte[16];
    new SecureRandom().nextBytes(bytes);
    String created = HexFormat.of().formatHex(bytes);
    Files.createDirectories(directory);
    Files.writeString(file, created, StandardCharsets.UTF_8);
    return created;
  }

  private Map<String, Object> viewFields(ViewCanvas<?> view, MediaSeries<?> series) {
    Map<String, Object> fields = new LinkedHashMap<>();
    int index = viewIndex(view);
    if (index > 0) fields.put("view", index);
    if (series != null) {
      try {
        fields.putAll(describer.apply(series));
      } catch (RuntimeException e) {
        LOGGER.debug("Cannot describe the series for the interaction log", e);
      }
    }
    return fields;
  }

  private static String viewKey(ViewCanvas<?> view) {
    int index = viewIndex(view);
    return index > 0 ? Integer.toString(index) : "view@" + System.identityHashCode(view);
  }

  private static String sliceKey(ViewCanvas<?> view, MediaSeries<?> series) {
    return viewKey(view) + ":series@" + System.identityHashCode(series);
  }

  private static int viewIndex(ViewCanvas<?> view) {
    ImageViewerEventManager<?> manager = view.getEventManager();
    ImageViewerPlugin<?> container = manager == null ? null : manager.getSelectedView2dContainer();
    return container == null ? 0 : container.getImagePanels().indexOf(view) + 1;
  }

  // Keys pressed in a viewport are recorded with the commands they trigger. In text boxes only the
  // number of typed characters is kept, plus shortcuts made with a modifier.
  private void keyEvent(KeyEvent event) {
    if (!enabled) return;
    Component source = event.getComponent();
    if (event.getID() == KeyEvent.KEY_TYPED) {
      if (source instanceof JTextComponent text
          && !(source instanceof JPasswordField)
          && text.isEditable()
          && !event.isControlDown()
          && !event.isMetaDown()) {
        String field = fieldName(text);
        add("typing", "typing:" + field, Map.of("field", field), Map.of());
      }
      return;
    }
    if (event.getID() != KeyEvent.KEY_PRESSED || isModifierKey(event.getKeyCode())) return;
    String combo = combo(event);
    ViewCanvas<?> view = viewCanvas(source);
    if (view != null) {
      Map<String, Object> fields = new LinkedHashMap<>();
      fields.put("key", combo);
      List<String> commands = shortcutCommands(event);
      if (!commands.isEmpty()) fields.put("commands", commands);
      int index = viewIndex(view);
      if (index > 0) fields.put("view", index);
      add("key", null, fields, Map.of());
    } else if (source instanceof JTextComponent text) {
      if (event.isControlDown() || event.isMetaDown() || event.isAltDown()) {
        add("key", null, Map.of("key", combo, "field", fieldName(text)), Map.of());
      }
    } else if (event.getModifiersEx() != 0
        || event.getKeyCode() == KeyEvent.VK_ESCAPE
        || (event.getKeyCode() >= KeyEvent.VK_F1 && event.getKeyCode() <= KeyEvent.VK_F24)) {
      String context = source == null ? "" : source.getClass().getSimpleName();
      add("key", null, Map.of("key", combo, "context", context), Map.of());
    }
  }

  private static ViewCanvas<?> viewCanvas(Component component) {
    for (Component c = component; c != null; c = c.getParent()) {
      if (c instanceof ViewCanvas<?> view) return view;
    }
    return null;
  }

  private static List<String> shortcutCommands(KeyEvent event) {
    ShortcutManager shortcuts = ShortcutManager.getInstance();
    return shortcuts.getShortcuts().keySet().stream()
        .filter(id -> shortcuts.matches(id, event))
        .sorted()
        .toList();
  }

  static String combo(KeyEvent event) {
    String key = KeyEvent.getKeyText(event.getKeyCode());
    if (event.getKeyLocation() == KeyEvent.KEY_LOCATION_NUMPAD && !key.startsWith("NumPad")) {
      key = "NumPad " + key;
    }
    String modifiers = InputEvent.getModifiersExText(event.getModifiersEx());
    return modifiers.isEmpty() ? key : modifiers + "+" + key;
  }

  private static boolean isModifierKey(int keyCode) {
    return keyCode == KeyEvent.VK_SHIFT
        || keyCode == KeyEvent.VK_CONTROL
        || keyCode == KeyEvent.VK_ALT
        || keyCode == KeyEvent.VK_META
        || keyCode == KeyEvent.VK_ALT_GRAPH
        || keyCode == KeyEvent.VK_CAPS_LOCK;
  }

  private String fieldName(JTextComponent text) {
    return fieldNames.computeIfAbsent(text, InteractionRecorder::describeField);
  }

  /**
   * Names a text box by its component name, its label, or the field that holds it in an enclosing
   * panel (such as "impressionText (ReportComposerTool)"), never by its contents.
   */
  static String describeField(JTextComponent text) {
    String name = text.getName();
    if (name == null || name.isBlank()) {
      name = text.getClientProperty("labeledBy") instanceof JLabel label ? label.getText() : null;
    }
    if (name == null || name.isBlank()) name = holderField(text);
    if (name == null || name.isBlank()) name = text.getAccessibleContext().getAccessibleName();
    if (name == null || name.isBlank()) name = text.getClass().getSimpleName();
    return name.length() > 60 ? name.substring(0, 60) : name;
  }

  private static String holderField(JTextComponent text) {
    for (Component owner = text.getParent(); owner != null; owner = owner.getParent()) {
      for (Class<?> type = owner.getClass();
          type != null && !type.getName().startsWith("java");
          type = type.getSuperclass()) {
        for (Field field : type.getDeclaredFields()) {
          if (Modifier.isStatic(field.getModifiers())
              || !JTextComponent.class.isAssignableFrom(field.getType())) continue;
          try {
            field.setAccessible(true);
            if (field.get(owner) == text) {
              return field.getName() + " (" + owner.getClass().getSimpleName() + ")";
            }
          } catch (ReflectiveOperationException | RuntimeException e) {
            // A field that cannot be read is skipped.
          }
        }
      }
    }
    return null;
  }

  // Numbers rounded for readability; anything else by name, kept short.
  static Object simple(Object value) {
    return switch (value) {
      case null -> null;
      case Double number -> Math.round(number * 1000) / 1000.0;
      case Float number -> Math.round(number * 1000) / 1000.0;
      case Number number -> number;
      case Boolean bool -> bool;
      case Enum<?> constant -> constant.name();
      default -> {
        String text = value.toString();
        yield text.length() > 80 ? text.substring(0, 80) : text;
      }
    };
  }
}
