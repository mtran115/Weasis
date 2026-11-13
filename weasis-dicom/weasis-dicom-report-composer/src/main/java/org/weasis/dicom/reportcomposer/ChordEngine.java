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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.Laterality;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.LevelSelection;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.ProtrusionLocation;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.Severity;
import org.weasis.dicom.reportcomposer.SpineFindingBuilder.SpineRegion;
import org.weasis.dicom.reportcomposer.SpineFormPanel.LevelControls;

/**
 * Typed chords for one spine form. Two digits pick a level that stays until the next one; every
 * later token has a fixed length, sets its finding on that level, and applies as soon as it is
 * complete. Nothing times out.
 *
 * <pre>
 * b p e a v      bulge, protrusion, extrusion, fissure, lipomatosis
 * f h            facet arthrosis, posterior hypertrophy (bilateral)
 * lf rf lh rh    the same on the left or right only
 * c# l# r#       canal, left and right foraminal stenosis: 1 mild, 2 moderate, 3 severe, 0 none
 * n#             both foramina at once
 * sp             opens the spondylosis line: levels typed there are added to it until Enter
 * ss sr          straightening, or reversal of the cervical lordosis; need no level
 * d#             disc location 1-8, after p or e
 * t              type the level's free text
 * </pre>
 */
final class ChordEngine implements ChordGrammar {
  /** The keys of a region's form, as shown to the reader while learning them. */
  static String reference(SpineRegion region) {
    StringBuilder levels = new StringBuilder();
    int column = 0;
    for (var code : levelCodes(region.levels()).entrySet()) {
      levels.append(column++ % 3 == 0 ? "\n  " : "   ").append(code.getKey()).append(' ');
      levels.append("%-6s".formatted(code.getValue()));
    }
    String alignment =
        switch (region) {
          case CERVICAL -> "ss          straightening\nsr          reversal of lordosis\n";
          case LUMBAR -> "ss          straightening\n";
          case THORACIC -> "";
        };
    return """
        ~           chord mode on / off

        Level; stays until the next one%s

        b p e       bulge, protrusion, extrusion
        d1 to d8    disc location, after p or e
                      1 central     2 broad-based
                      3 L central   4 R central
                      5 L subart.   6 R subart.
                      7 L foraminal 8 R foraminal
        a v         fissure, lipomatosis

        f           facets (both sides)
        h           %s (both sides)
        lf rf       f, left or right only
        lh rh       h, left or right only

        c# l# r#    canal, left foramen, right foramen
        n#          both foramina
                      1 mild  2 moderate  3 severe
                      0 clear

        t           free text for the level
        %ssp          spondylosis line: type levels
                      (or none), then Enter

        Backspace   remove the last key or token
        Esc         drop a half-typed token

        M, the numpad and arrows still go to the viewer.
        """
        .formatted(levels, region.posteriorElementLabel().toLowerCase(), alignment);
  }

  private static final Pattern COMPLETE =
      Pattern.compile("[bpeavfht]|[clrn][0-3]|d[1-8]|[lr][fh]|s[spr]");
  private static final Pattern PREFIX = Pattern.compile("[clrnds]");
  private static final Pattern VERTEBRA = Pattern.compile("\\d+");

  private final SpineFormPanel form;
  private final Map<String, String> levelByCode;
  private final Map<String, List<String>> typed = new HashMap<>();
  private LevelControls level;
  private String pending = "";
  private boolean spondylosisLine;

  ChordEngine(SpineFormPanel form) {
    this.form = form;
    levelByCode = levelCodes(form.levelNames());
  }

  /**
   * The two digits that pick each level: the last digit of both vertebrae (45 for L4-5, 51 for
   * L5-S1), or the upper vertebra's number (01 to 12) where those would repeat, as in the thoracic
   * spine.
   */
  static Map<String, String> levelCodes(List<String> levels) {
    Map<String, String> codes = new LinkedHashMap<>();
    for (String name : levels) {
      int[] vertebrae = vertebrae(name);
      codes.put("" + vertebrae[0] % 10 + vertebrae[1] % 10, name);
    }
    if (codes.size() == levels.size()) return codes;
    codes.clear();
    levels.forEach(name -> codes.put("%02d".formatted(vertebrae(name)[0]), name));
    return codes;
  }

  private static int[] vertebrae(String level) {
    Matcher matcher = VERTEBRA.matcher(level);
    int[] numbers = new int[2];
    for (int index = 0; index < 2 && matcher.find(); index++) {
      numbers[index] = Integer.parseInt(matcher.group());
    }
    return numbers;
  }

  /** Takes one digit or lower-case letter. A rejected key changes nothing. */
  @Override
  public Outcome type(char key) {
    String chord = pending + key;
    if (Character.isDigit(chord.charAt(0))) return typeLevel(chord);
    if (spondylosisLine) return Outcome.REJECTED;
    if (level == null && !chord.matches("s[spr]?")) return Outcome.REJECTED;
    if (chord.equals("ss") && !form.hasStraightening()) return Outcome.REJECTED;
    if (chord.equals("sr") && !form.hasReversal()) return Outcome.REJECTED;
    if (chord.equals("d") && !level.hasDiscHerniation()) return Outcome.REJECTED;
    if (PREFIX.matcher(chord).matches()) {
      pending = chord;
      return Outcome.PENDING;
    }
    if (!COMPLETE.matcher(chord).matches()) return Outcome.REJECTED;
    pending = "";
    if (chord.equals("t")) {
      level.editText();
    } else if (chord.equals("sp")) {
      form.setSpondylosis(true);
      spondylosisLine = true;
    } else {
      set(chord);
    }
    return Outcome.APPLIED;
  }

  private Outcome typeLevel(String chord) {
    if (chord.length() == 1) {
      if (levelByCode.keySet().stream().noneMatch(code -> code.startsWith(chord))) {
        return Outcome.REJECTED;
      }
      pending = chord;
      return Outcome.PENDING;
    }
    String name = levelByCode.get(chord);
    if (name == null) return Outcome.REJECTED;
    if (spondylosisLine) form.setSpondylosisAt(name, true);
    else level = form.level(name);
    pending = "";
    return Outcome.APPLIED;
  }

  /** Removes the last key of an unfinished token, or else the line's last token. */
  @Override
  public boolean backspace() {
    if (!pending.isEmpty()) {
      pending = pending.substring(0, pending.length() - 1);
      return true;
    }
    if (spondylosisLine) {
      List<String> levels = form.spondylosisLevels();
      if (levels.isEmpty()) {
        form.setSpondylosis(false);
        spondylosisLine = false;
      } else {
        form.setSpondylosisAt(levels.getLast(), false);
      }
      return true;
    }
    List<String> tokens = tokens();
    if (tokens.isEmpty()) return false;
    write(tokens.removeLast(), false, tokens);
    typed.put(levelName(), tokens);
    return true;
  }

  /** Abandons an unfinished token, or else ends the spondylosis line. */
  @Override
  public boolean escape() {
    if (pending.isEmpty()) return enter();
    pending = "";
    return true;
  }

  /** Ends the spondylosis line, returning to the level that was current before it. */
  @Override
  public boolean enter() {
    if (!spondylosisLine) return false;
    spondylosisLine = false;
    pending = "";
    return true;
  }

  @Override
  public boolean wantsEnter() {
    return spondylosisLine;
  }

  @Override
  public boolean enabled() {
    return form.isEnabled();
  }

  @Override
  public String lineText() {
    String unfinished = pending.isEmpty() ? "" : pending + "_";
    if (spondylosisLine) {
      String levels = String.join(" ", form.spondylosisLevels());
      String shown = levels.isEmpty() && unfinished.isEmpty() ? "no levels" : levels;
      return ("Spondylosis  " + shown + " " + unfinished).stripTrailing()
          + "   ·   Enter to finish";
    }
    List<String> tokens = tokens();
    if (level == null && tokens.isEmpty()) {
      return unfinished.isEmpty() ? "chord · type a level" : unfinished;
    }
    StringBuilder text = new StringBuilder(level == null ? "no level" : level.name());
    text.append("  ").append(String.join(" ", tokens));
    if (!unfinished.isEmpty()) text.append(' ').append(unfinished);
    String reading = reading();
    if (!reading.isEmpty()) text.append("   ·   ").append(reading);
    return text.toString().stripTrailing();
  }

  List<String> spondylosisLevels() {
    return form.spondylosisLevels();
  }

  /** Forgets the level and typing order, as for a new study. */
  @Override
  public void reset() {
    level = null;
    pending = "";
    spondylosisLine = false;
    typed.clear();
  }

  String levelName() {
    return level == null ? "" : level.name();
  }

  @Override
  public String pending() {
    return pending;
  }

  /**
   * The level's findings and straightening as tokens, in the order typed. Findings set in the form
   * follow in form order, and tokens the form no longer holds are left out.
   */
  List<String> tokens() {
    LevelSelection selection = level == null ? null : level.selection();
    List<String> held = new ArrayList<>();
    if (selection != null) held.addAll(held(selection));
    if (form.straightening()) held.add("ss");
    if (form.reversal()) held.add("sr");
    List<String> tokens = new ArrayList<>(typed.getOrDefault(levelName(), List.of()));
    tokens.removeIf(
        token ->
            !held.contains(token) && !(selection != null && isDefaultLocation(token, selection)));
    held.stream().filter(token -> !tokens.contains(token)).forEach(tokens::add);
    // Alignment belongs to no level, so it leads the line and is the last to be backspaced.
    for (String alignment : List.of("sr", "ss")) {
      if (tokens.remove(alignment)) tokens.addFirst(alignment);
    }
    return tokens;
  }

  /** The tokens in plain words, e.g. "bulge, facets, mod canal". */
  String reading() {
    return tokens().stream().map(this::words).collect(Collectors.joining(", "));
  }

  private void set(String token) {
    List<String> tokens = tokens();
    boolean cleared = token.matches("[clrn]0");
    if (token.charAt(0) == 'n') tokens.removeIf(other -> other.matches("[lr]\\d"));
    int index = slotIndex(tokens, token);
    if (cleared && index >= 0) tokens.remove(index);
    else if (!cleared && index >= 0) tokens.set(index, token);
    else if (!cleared) tokens.add(token);
    write(token, !cleared, tokens);
    typed.put(levelName(), tokens);
  }

  private void write(String token, boolean on, List<String> tokens) {
    if (token.equals("ss") || token.equals("sr")) {
      // Straightening and reversal exclude each other.
      form.setStraightening(on && token.equals("ss"));
      form.setReversal(on && token.equals("sr"));
      return;
    }
    char kind = token.charAt(isOneSided(token) ? 1 : 0);
    int severity = on && token.length() == 2 ? token.charAt(1) - '0' : 0;
    switch (kind) {
      case 'f', 'h' -> {
        char side = isOneSided(token) ? Character.toUpperCase(token.charAt(0)) : 'B';
        level.setLaterality(Character.toUpperCase(kind), on ? side : '0');
      }
      case 'c', 'l', 'r' -> level.setSeverity(Character.toUpperCase(kind), severity);
      case 'n' -> {
        level.setSeverity('L', severity);
        level.setSeverity('R', severity);
      }
      case 'd' -> writeLocations(tokens);
      default -> level.set(Character.toUpperCase(kind), on);
    }
  }

  private void writeLocations(List<String> tokens) {
    EnumSet<ProtrusionLocation> locations = EnumSet.noneOf(ProtrusionLocation.class);
    for (String token : tokens) {
      if (token.charAt(0) == 'd') {
        locations.add(ProtrusionLocation.values()[token.charAt(1) - '1']);
      }
    }
    if (locations.isEmpty()) locations.add(ProtrusionLocation.CENTRAL);
    level.setDiscLocations(locations);
  }

  /** Tokens sharing a slot replace each other: one severity per site, protrusion or extrusion. */
  private static int slotIndex(List<String> tokens, String token) {
    String slot = slot(token);
    for (int index = 0; index < tokens.size(); index++) {
      if (slot(tokens.get(index)).equals(slot)) return index;
    }
    return -1;
  }

  /** A facet or hypertrophy token for one side: lf, rf, lh or rh. */
  private static boolean isOneSided(String token) {
    return token.matches("[lr][fh]");
  }

  private static String slot(String token) {
    if (isOneSided(token)) return token.substring(1);
    if (token.equals("sr")) return "ss";
    return switch (token.charAt(0)) {
      case 'c', 'l', 'r', 'n' -> token.substring(0, 1);
      case 'e' -> "p";
      default -> token;
    };
  }

  private static boolean isDefaultLocation(String token, LevelSelection selection) {
    return token.equals("d1") && locations(selection).equals(List.of(ProtrusionLocation.CENTRAL));
  }

  private static List<ProtrusionLocation> locations(LevelSelection selection) {
    return selection.protrusion()
        ? selection.protrusionLocations()
        : selection.extrusionLocations();
  }

  private static List<String> held(LevelSelection selection) {
    List<String> tokens = new ArrayList<>();
    if (selection.bulge()) tokens.add("b");
    if (selection.protrusion()) tokens.add("p");
    if (selection.extrusion()) tokens.add("e");
    List<ProtrusionLocation> locations = locations(selection);
    // Central alone is what a herniation starts with, so it is not shown as typed.
    if (!locations.equals(List.of(ProtrusionLocation.CENTRAL))) {
      locations.forEach(location -> tokens.add("d" + (location.ordinal() + 1)));
    }
    if (selection.annularFissure()) tokens.add("a");
    if (selection.ventralEpiduralLipomatosis()) tokens.add("v");
    addSide(tokens, "f", selection.facetArthrosis());
    addSide(tokens, "h", selection.posteriorElementHypertrophy());
    addSeverity(tokens, "c", selection.spinalCanalSeverity());
    Severity left = selection.leftForaminalSeverity();
    Severity right = selection.rightForaminalSeverity();
    if (left == right) {
      addSeverity(tokens, "n", left);
    } else {
      addSeverity(tokens, "l", left);
      addSeverity(tokens, "r", right);
    }
    return tokens;
  }

  private static void addSide(List<String> tokens, String key, Laterality side) {
    switch (side) {
      case BILATERAL -> tokens.add(key);
      case LEFT -> tokens.add("l" + key);
      case RIGHT -> tokens.add("r" + key);
      case NONE -> {}
    }
  }

  private static void addSeverity(List<String> tokens, String key, Severity severity) {
    if (severity != Severity.NONE) tokens.add(key + severity.ordinal());
  }

  private String words(String token) {
    // The h finding is uncovertebral in the cervical spine and ligamentum flavum elsewhere.
    String hypertrophy = form.region() == SpineRegion.CERVICAL ? "uncovertebral" : "lig. flavum";
    if (token.equals("ss")) return "straightening";
    if (token.equals("sr")) return "reversal";
    if (isOneSided(token)) {
      return (token.charAt(0) == 'l' ? "L " : "R ")
          + (token.charAt(1) == 'f' ? "facet" : hypertrophy);
    }
    return switch (token.charAt(0)) {
      case 'b' -> "bulge";
      case 'p' -> "protrusion";
      case 'e' -> "extrusion";
      case 'a' -> "fissure";
      case 'v' -> "lipomatosis";
      case 'f' -> "facets";
      case 'h' -> hypertrophy;
      case 'd' -> ProtrusionLocation.values()[token.charAt(1) - '1'].toString().toLowerCase();
      default ->
          List.of("mild", "mod", "severe").get(token.charAt(1) - '1')
              + switch (token.charAt(0)) {
                case 'c' -> " canal";
                case 'l' -> " L foramen";
                case 'r' -> " R foramen";
                default -> " foramina";
              };
    };
  }
}
