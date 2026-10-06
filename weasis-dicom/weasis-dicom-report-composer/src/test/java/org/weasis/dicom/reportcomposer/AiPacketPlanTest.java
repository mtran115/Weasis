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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.reportcomposer.AiPacketGeometry.Plane;
import org.weasis.dicom.reportcomposer.AiPacketPlan.Disc;
import org.weasis.dicom.reportcomposer.AiPacketPlan.DiscLevels;
import org.weasis.dicom.reportcomposer.AiPacketPlan.SourceSeries;
import org.weasis.dicom.reportcomposer.AiPacketPlan.SourceSlice;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.ExamTemplate;

class AiPacketPlanTest {
  static final String FRAME = "1.2.3.frame";
  static final AiPacketPlan.Study STUDY =
      new AiPacketPlan.Study(
          ExamTemplate.LUMBAR_SPINE, "MRI LUMBAR SPINE", "MR", "54 years", "male");

  @Test
  void excludesLocalizersCapturesAndTinySeriesAndOrdersTheRest() {
    List<SourceSeries> sources =
        List.of(
            series("601", "T2 SAG", sagittals(-4, -2, 0, 2, 4)),
            series("1", "Survey", sagittals(-2, 0, 2)),
            new SourceSeries(
                "2", "Screen", "1.2.840.10008.5.1.4.1.1.7", "", "", List.of(), sagittals(0, 1, 2)),
            new SourceSeries("3", "Burned", "", "", "YES", List.of(), sagittals(0, 1, 2)),
            series("4", "Mixed", List.of(sagittal(0), axial(0), sagittal(2))),
            series("5", "Pair", sagittals(0, 2)),
            series("801", "T2 AX", axials(30, 10, 20)));

    AiPacketPlan plan = AiPacketPlan.build(STUDY, sources, Optional.empty());

    assertEquals(List.of("S1", "S2"), plan.series().stream().map(AiPacketPlan.Series::id).toList());
    assertEquals(
        List.of("601", "801"),
        plan.series().stream().map(AiPacketPlan.Series::seriesNumber).toList());
    assertEquals(
        List.of(
            "localizer or scout",
            "secondary capture, which can carry burned-in text",
            "marked as having burned-in annotation",
            "slices in different planes, like a localizer",
            "fewer than 3 images"),
        plan.excluded().stream().map(AiPacketPlan.Exclusion::reason).toList());
    assertEquals("none", plan.levelSource());
  }

  @Test
  void stacksRunRightToLeftAndTopToBottomWithLabelledPositions() {
    List<SourceSlice> shuffled = new ArrayList<>(sagittals(-4, -2, 0, 2, 4));
    Collections.reverse(shuffled);

    AiPacketPlan plan =
        AiPacketPlan.build(
            STUDY,
            List.of(series("601", "T2 SAG", shuffled), series("801", "T2 AX", axials(10, 30, 20))),
            Optional.empty());

    AiPacketPlan.Series sagittal = plan.series().getFirst();
    assertEquals(Plane.SAGITTAL, sagittal.plane());
    assertEquals(
        "[S1-01] sagittal \"T2 SAG\" (series 601), slice 1 of 5, R 4.0 mm",
        sagittal.slices().getFirst().label());
    assertTrue(sagittal.slices().getLast().label().endsWith("slice 5 of 5, L 4.0 mm"));
    assertTrue(
        sagittal
            .summary()
            .contains(
                "5 slices ordered right to left; image right = posterior, image down = inferior"),
        sagittal.summary());

    AiPacketPlan.Series axial = plan.series().getLast();
    assertEquals(
        List.of("S 30.0 mm", "S 20.0 mm", "S 10.0 mm"),
        axial.slices().stream()
            .map(s -> s.label().substring(s.label().lastIndexOf(", ") + 2))
            .toList());
  }

  @Test
  void axialLabelsNameTheNearestDiscAndLocalizersDrawEveryAxialSlice() {
    DiscLevels levels =
        new DiscLevels(
            FRAME,
            List.of(
                new Disc("L3-L4", List.of(0.0, 0.0, 50.0)),
                new Disc("L4-L5", List.of(0.0, 0.0, 20.0))),
            true);

    AiPacketPlan plan =
        AiPacketPlan.build(
            STUDY,
            List.of(
                series("601", "T2 SAG", sagittals(-2, 0, 2)),
                series("801", "T2 AX", axials(22, 18, -40))),
            Optional.of(levels));

    List<String> axialLabels =
        plan.series().getLast().slices().stream().map(AiPacketPlan.Slice::label).toList();
    assertTrue(
        axialLabels.get(0).endsWith("S 22.0 mm, nearest disc L4-L5 (2 mm away)"),
        axialLabels.get(0));
    assertTrue(axialLabels.get(2).endsWith("I 40.0 mm"), axialLabels.get(2));
    assertEquals("confirmed by the radiologist", plan.levelSource());

    AiPacketPlan.Localizer localizer = plan.localizers().getFirst();
    assertEquals("LOC-S2", localizer.id());
    assertEquals("S1-02", localizer.slice().id());
    assertEquals(
        List.of(1, 2, 3), localizer.lines().stream().map(AiPacketPlan.Line::number).toList());
    assertEquals(
        List.of("L3-L4", "L4-L5"),
        localizer.levels().stream().map(AiPacketPlan.LevelMark::level).toList());
    assertEquals(7, plan.imageCount());
  }

  @Test
  void keepsDiscParallelAxialBlocksAndOrdersThemTopToBottom() {
    // Two blocks tilted 15 degrees each way, as when axials are angled to different discs.
    double tilt = Math.toRadians(15);
    List<Double> up = List.of(1.0, 0.0, 0.0, 0.0, Math.cos(tilt), Math.sin(tilt));
    List<Double> down = List.of(1.0, 0.0, 0.0, 0.0, Math.cos(tilt), -Math.sin(tilt));
    List<SourceSlice> blocks =
        List.of(
            slice(down, List.of(-100.0, -100.0, -5.0)),
            slice(up, List.of(-100.0, -100.0, 25.0)),
            slice(down, List.of(-100.0, -100.0, 0.0)),
            slice(up, List.of(-100.0, -100.0, 30.0)));

    AiPacketPlan plan =
        AiPacketPlan.build(STUDY, List.of(series("801", "T2 AX", blocks)), Optional.empty());

    assertTrue(plan.excluded().isEmpty(), plan.excluded().toString());
    assertEquals(
        List.of("S 55.9 mm", "S 50.9 mm", "I 25.9 mm", "I 30.9 mm"),
        plan.series().getFirst().slices().stream()
            .map(s -> s.label().substring(s.label().lastIndexOf(", ") + 2))
            .toList());
  }

  @Test
  void crossLineFollowsTheAxialPlaneAcrossTheSagittalImage() {
    double[] line =
        AiPacketGeometry.crossLine(
                sagittal(0).reference().geometry(), axial(20).reference().geometry())
            .orElseThrow();

    assertEquals(80, line[1], 1e-6);
    assertEquals(80, line[3], 1e-6);
    assertEquals(200, Math.abs(line[2] - line[0]), 1e-6);
    assertTrue(
        AiPacketGeometry.crossLine(
                sagittal(0).reference().geometry(), sagittal(2).reference().geometry())
            .isEmpty());
  }

  @Test
  void agesAreWholeYearsAndCappedAtNinety() {
    assertEquals("54 years", AiPacketSources.age("054Y", null, null));
    assertEquals("90 or older", AiPacketSources.age("095Y", null, null));
    assertEquals("18 months", AiPacketSources.age("018M", null, null));
    assertEquals(
        "41 years", AiPacketSources.age(null, LocalDate.of(1985, 6, 2), LocalDate.of(2026, 10, 1)));
    assertEquals("unknown", AiPacketSources.age("", null, null));
    assertEquals("female", AiPacketSources.sex("F"));
    assertEquals("unknown", AiPacketSources.sex(""));
  }

  static SourceSeries series(String number, String description, List<SourceSlice> slices) {
    return new SourceSeries(
        number,
        description,
        "1.2.840.10008.5.1.4.1.1.4",
        "ORIGINAL\\PRIMARY",
        "",
        List.of("TR 3000 ms"),
        slices);
  }

  static List<SourceSlice> sagittals(double... xs) {
    return java.util.Arrays.stream(xs).mapToObj(AiPacketPlanTest::sagittal).toList();
  }

  static List<SourceSlice> axials(double... zs) {
    return java.util.Arrays.stream(zs).mapToObj(AiPacketPlanTest::axial).toList();
  }

  // Image right = posterior, down = inferior; covers y -100..100, z 100..-100.
  static SourceSlice sagittal(double x) {
    return slice(List.of(0.0, 1.0, 0.0, 0.0, 0.0, -1.0), List.of(x, -100.0, 100.0));
  }

  // Image right = patient left, down = posterior; covers x -100..100, y -100..100.
  static SourceSlice axial(double z) {
    return slice(List.of(1.0, 0.0, 0.0, 0.0, 1.0, 0.0), List.of(-100.0, -100.0, z));
  }

  static SourceSlice slice(List<Double> orientation, List<Double> position) {
    ImageGeometry geometry =
        new ImageGeometry(200, 200, orientation, position, List.of(1.0, 1.0), FRAME);
    ImageReference reference =
        new ImageReference("series", "sop" + position, "1", "", "1", 1, 1, null, "", geometry);
    return new SourceSlice(
        reference, () -> new BufferedImage(200, 200, BufferedImage.TYPE_BYTE_GRAY));
  }
}
