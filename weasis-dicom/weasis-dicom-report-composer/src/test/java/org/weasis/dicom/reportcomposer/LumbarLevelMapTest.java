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

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class LumbarLevelMapTest {
  static LumbarLevelMap unexpectedMap() {
    var source = map("case", "b".repeat(64));
    var raw =
        List.of(
            new LumbarLevelMap.Landmark("a", "T12-T13", geometry().patientPosition(60, 20)),
            new LumbarLevelMap.Landmark("b", "T13-L1", geometry().patientPosition(60, 40)),
            new LumbarLevelMap.Landmark("c", "L1-L2", geometry().patientPosition(60, 60)));
    // Legacy map compatibility must not leak raw research names back into the UI.
    return new LumbarLevelMap(
        1,
        source.studyKey(),
        source.studyInstanceUid(),
        source.proposalId(),
        source.modelId(),
        source.frameOfReferenceUid(),
        source.reference(),
        raw,
        List.of("Review numbering", "Possible T13 variant"),
        source.previewPath(),
        "provisional");
  }

  @Test
  void unexpectedModelNamesRemainInAuditOnlyAndTheWholeMapIsUnassigned() throws Exception {
    var map = unexpectedMap();
    assertTrue(map.numberingUncertain());
    assertTrue(map.points().stream().allMatch(p -> p.level().equals(LumbarLevelMap.UNASSIGNED)));
    assertEquals(
        List.of("T12-T13", "T13-L1", "L1-L2"),
        map.modelPoints().stream().map(LumbarLevelMap.Landmark::level).toList());
    assertEquals("Disc 1 ?", map.points().getFirst().displayLabel(0));
    assertFalse(LumbarLevelMap.LEVELS.stream().anyMatch(level -> level.contains("T13")));
    assertEquals(
        map,
        LumbarLevelService.JSON.readValue(
            LumbarLevelService.JSON.writeValueAsBytes(map), LumbarLevelMap.class));
    assertThrows(
        IllegalArgumentException.class,
        () -> LumbarLevelMap.validatePoints(map.points(), geometry()));
    assertThrows(
        IllegalArgumentException.class,
        () -> LumbarLevelMap.validatePoints(map.modelPoints(), geometry()));
  }

  @Test
  void numberingSevenDiscsUpwardIncludesT11T12AndPreservesOriginalLocations() {
    var points = new java.util.ArrayList<LumbarLevelMap.Landmark>();
    for (int i = 0; i < 7; i++)
      points.add(
          new LumbarLevelMap.Landmark(
              "p" + i, LumbarLevelMap.UNASSIGNED, geometry().patientPosition(60, 20 + i * 20)));
    var numbered = LumbarLevelReview.numberUpward(points, "L5-S1");
    assertEquals(
        List.of("T11-T12", "T12-L1", "L1-L2", "L2-L3", "L3-L4", "L4-L5", "L5-S1"),
        numbered.stream().map(LumbarLevelMap.Landmark::level).toList());
    assertEquals(
        points.stream().map(LumbarLevelMap.Landmark::lps).toList(),
        numbered.stream().map(LumbarLevelMap.Landmark::lps).toList());
    LumbarLevelMap.validatePoints(numbered, geometry());
    assertTrue(points.stream().allMatch(p -> p.level().equals(LumbarLevelMap.UNASSIGNED)));
    points.add(
        new LumbarLevelMap.Landmark(
            "extra", LumbarLevelMap.UNASSIGNED, geometry().patientPosition(60, 170)));
    assertThrows(
        IllegalArgumentException.class, () -> LumbarLevelReview.numberUpward(points, "L5-S1"));
  }

  static ImageGeometry geometry() {
    return new ImageGeometry(
        256,
        256,
        List.of(0., 1., 0., 0., 0., -1.),
        List.of(5., -100., 100.),
        List.of(2., 1.),
        "frame");
  }

  static LumbarLevelMap map(String key, String proposal) {
    var geometry = geometry();
    return new LumbarLevelMap(
        1,
        key,
        "study",
        proposal,
        "test-model",
        "frame",
        new ImageReference(
            "series", "instance", "1", "T2 sagittal", "5", 5, 10, 0, "file:///unused", geometry),
        List.of(
            new LumbarLevelMap.Landmark("a", "L1-L2", geometry.patientPosition(60, 20)),
            new LumbarLevelMap.Landmark("b", "L2-L3", geometry.patientPosition(65, 40))),
        List.of("Review numbering"),
        "/unused.png",
        "provisional");
  }

  @Test
  void nonSquarePixelsRoundTripInObliquePatientCoordinates() {
    double c = Math.sqrt(.5);
    var g =
        new ImageGeometry(
            256,
            512,
            List.of(0., c, c, 0., c, -c),
            List.of(17., -32., 104.),
            List.of(.7, 1.4),
            "frame");
    var lps = g.patientPosition(121.25, 63.5);
    var pixel = LumbarLevelMap.pixel(g, lps);
    assertEquals(121.25, pixel.getX(), 1e-8);
    assertEquals(63.5, pixel.getY(), 1e-8);
    assertEquals(0, LumbarLevelMap.planeDistance(g, lps), 1e-8);
    assertEquals(
        9, LumbarLevelMap.planeDistance(g, List.of(lps.get(0) + 9, lps.get(1), lps.get(2))), 1e-8);
  }

  @Test
  void axialPlaneDistanceIsIndependentOfInPlaneLocation() {
    var axial =
        new ImageGeometry(
            256,
            256,
            List.of(1., 0., 0., 0., 1., 0.),
            List.of(-100., -100., 30.),
            List.of(1., 1.),
            "frame");
    assertEquals(4, LumbarLevelMap.planeDistance(axial, List.of(5., -40., 34.)), 1e-9);
    assertEquals(105, LumbarLevelMap.pixel(axial, List.of(5., -40., 34.)).getX());
  }

  @Test
  void invalidOrMissingGeometryCannotProduceCoordinates() {
    assertNull(LumbarLevelMap.pixel(ImageGeometry.empty(), List.of(1., 2., 3.)));
    var g =
        new ImageGeometry(
            256, 256, List.of(1., 0., 0., 1., 0., 0.), List.of(0., 0., 0.), List.of(1., 1.), "f");
    assertFalse(LumbarLevelMap.validGeometry(g));
    assertThrows(
        IllegalArgumentException.class,
        () -> new LumbarLevelMap.Landmark("a", "L1-L2", List.of(Double.NaN, 0., 0.)));
  }

  @Test
  void duplicateLevelsWrongOrderAndOutOfImagePointsAreRejected() {
    var first = new LumbarLevelMap.Landmark("a", "L1-L2", geometry().patientPosition(60, 20));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            LumbarLevelMap.validatePoints(
                List.of(
                    first,
                    new LumbarLevelMap.Landmark("b", "L1-L2", geometry().patientPosition(60, 40))),
                geometry()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            LumbarLevelMap.validatePoints(
                List.of(
                    first,
                    new LumbarLevelMap.Landmark("b", "L2-L3", geometry().patientPosition(60, 10))),
                geometry()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            LumbarLevelMap.validatePoints(
                List.of(
                    first,
                    new LumbarLevelMap.Landmark("b", "L2-L3", geometry().patientPosition(999, 40))),
                geometry()));
  }

  @Test
  void proposalAndFeedbackJsonRoundTripWithoutPromotingPredictions() throws Exception {
    var map = map("case", "a".repeat(64));
    var restored =
        LumbarLevelService.JSON.readValue(
            LumbarLevelService.JSON.writeValueAsBytes(map), LumbarLevelMap.class);
    assertEquals(map, restored);
    for (String decision : List.of("uncertain", "skipped")) {
      var feedback =
          new LumbarLevelService.Feedback(
              1, "event", "now", "reader", decision, "uncertain", map, map.points());
      assertFalse(feedback.confirmed());
    }
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LumbarLevelService.Feedback(
                1, "event", "now", "reader", "accepted", "uncertain", map, map.points()));
  }
}
