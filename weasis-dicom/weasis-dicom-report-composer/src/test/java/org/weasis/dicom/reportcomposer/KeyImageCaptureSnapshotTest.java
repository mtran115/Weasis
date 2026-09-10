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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class KeyImageCaptureSnapshotTest {
  @Test
  void snapshotAndRelinkingPreserveCaptureWhileIsolatingCaptionEditsAndImageMutation() {
    BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
    ImageReference reference =
        new ImageReference(
            "series",
            "sop",
            "1",
            "T2",
            "5",
            2,
            10,
            4,
            "",
            new ImageGeometry(100, 100, List.of(), List.of(), List.of(), ""));
    CaptureGeometry geometry =
        CaptureGeometry.freeze(100, 100, new AffineTransform(), new Point2D.Double(), 1, 1);
    var captured =
        new DicomContextReader.CapturedView(reference, image, geometry, "2026-09-09T00:00:00Z");
    KeyImageCapture original =
        KeyImageCapture.createWithArrows(
            captured,
            List.of(new ArrowPlacement(0.1, 0.1, 0.7, 0.7)),
            "Original caption",
            "finding-1",
            "selected_finding");
    KeyImageCapture snapshot = original.snapshot();
    KeyImageCapture relinked = original.withFindingLink("", "removed_finding");
    image.setRGB(0, 0, 0xffffff);
    original.setCaption("Edited caption");

    assertEquals("Original caption.", snapshot.caption());
    assertEquals("Original caption.", relinked.caption());
    assertEquals("finding-1", snapshot.findingId());
    assertEquals("", relinked.findingId());
    assertEquals("removed_finding", relinked.findingLinkSource());
    assertEquals(original.id(), relinked.id());
    assertEquals(original.capturedAt(), relinked.capturedAt());
    assertEquals(original.captureGeometry(), relinked.captureGeometry());
    assertEquals(original.sourceArrows(), relinked.sourceArrows());
    assertEquals(70, snapshot.sourceArrows().getFirst().tipX(), 1e-9);
    assertSame(original.baseImageIdentity(), snapshot.baseImageIdentity());
    assertNotEquals(image.getRGB(0, 0), snapshot.baseImageCopy().getRGB(0, 0));
    BufferedImage copy = snapshot.baseImageCopy();
    copy.setRGB(1, 1, 0xffffff);
    assertNotEquals(copy.getRGB(1, 1), snapshot.baseImageCopy().getRGB(1, 1));
    assertFalse(
        Arrays.equals(
            snapshot.renderedImage().getRGB(0, 0, 100, 100, null, 0, 100),
            snapshot.baseImageCopy().getRGB(0, 0, 100, 100, null, 0, 100)));
  }

  @Test
  void restorationKeepsImageLinkProvenanceAndFrozenAnnotations() {
    KeyImageCapture original =
        KeyImageCapture.createWithArrows(
            new ImageReference("series", "sop", "1", "T2", "2", 2, 10),
            new BufferedImage(30, 30, BufferedImage.TYPE_INT_RGB),
            List.of(),
            "Caption");
    KeyImageCapture restored =
        KeyImageCapture.restore(
            original.id(),
            original.reference(),
            original.baseImageCopy(),
            original.arrows(),
            original.caption(),
            "finding-2",
            "last_finding_suggestion",
            original.capturedAt(),
            original.captureGeometry(),
            original.sourceArrows());

    assertEquals(original.id(), restored.id());
    assertEquals(original.reference(), restored.reference());
    assertEquals(original.capturedAt(), restored.capturedAt());
    assertEquals("finding-2", restored.findingId());
    assertEquals("last_finding_suggestion", restored.findingLinkSource());
  }
}
