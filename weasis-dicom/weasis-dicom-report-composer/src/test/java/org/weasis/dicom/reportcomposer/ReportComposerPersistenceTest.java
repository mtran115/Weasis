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
import static org.junit.jupiter.api.Assertions.assertSame;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests the draft handoff used by asynchronous autosave and completion. */
class ReportComposerPersistenceTest {
  @Test
  void deletingAFindingUnlinksOnlyItsImagesAndCannotReassociateThemByReusingTheId() {
    ReportDraft draft = draft();
    FindingEntry first = new FindingEntry("first", "L3-4 disc protrusion", "", false);
    FindingEntry second = new FindingEntry("second", "L4-5 disc protrusion", "", false);
    draft.addFinding(first);
    draft.addFinding(second);
    KeyImageCapture firstImage = image("first-image", first.id());
    KeyImageCapture secondImage = image("second-image", second.id());
    draft.addKeyImage(firstImage);
    draft.addKeyImage(secondImage);
    ReportPacket beforeRemoval = draft.snapshot();

    draft.removeFinding(first.id());
    draft.addFinding(new FindingEntry(first.id(), "A different finding", "", false));

    KeyImageCapture unlinked = draft.keyImages().getFirst();
    assertEquals("", unlinked.findingId());
    assertEquals("removed_finding", unlinked.findingLinkSource());
    assertEquals(firstImage.id(), unlinked.id());
    assertEquals(firstImage.reference(), unlinked.reference());
    assertEquals(firstImage.caption(), unlinked.caption());
    assertEquals(firstImage.sourceArrows(), unlinked.sourceArrows());
    assertSame(firstImage.baseImageIdentity(), unlinked.baseImageIdentity());
    assertSame(secondImage, draft.keyImages().getLast());
    assertEquals(first.id(), beforeRemoval.keyImages().getFirst().findingId());
    assertEquals(List.of(first, second), beforeRemoval.findings());
  }

  @Test
  void editsAfterCompletionSnapshotCannotChangeTheQueuedPacketOrFindingAssociations() {
    ReportDraft draft = draft();
    FindingEntry original = new FindingEntry("finding", "L4-5 protrusion", "", false);
    draft.addFinding(original);
    KeyImageCapture capture = image("image", original.id());
    draft.addKeyImage(capture);
    draft.setReportInstructions("Original instructions");
    var editor = JsonNodeFactory.instance.objectNode().put("findingText", "Pending text");
    TrainingCaseSnapshot completion = TrainingCaseSnapshot.capture(draft, "LUMBAR_SPINE", editor);

    capture.setCaption("Changed caption");
    draft.replaceFinding(original.id(), original.withText("Edited finding", "", false));
    draft.replaceKeyImage(capture.id(), capture.withFindingLink("", "unlinked"));
    draft.setReportInstructions("Edited instructions");
    editor.put("findingText", "Different pending text");
    ((ObjectNode) completion.editorState()).put("findingText", "Caller mutation");

    assertEquals("L4-5 protrusion.", completion.packet().findings().getFirst().findingText());
    assertEquals("Original caption.", completion.packet().keyImages().getFirst().caption());
    assertEquals(original.id(), completion.packet().keyImages().getFirst().findingId());
    assertEquals("Original instructions", completion.packet().reportInstructions());
    assertEquals("Pending text", completion.editorState().path("findingText").asText());
    assertEquals("", draft.keyImages().getFirst().findingId());
    assertEquals("Changed caption.", draft.keyImages().getFirst().caption());
  }

  @Test
  void manualFindingEditAndReorderingPreserveTheExplicitImageLink() {
    ReportDraft draft = draft();
    FindingEntry first = new FindingEntry("first", "L3-4 protrusion", "", false);
    FindingEntry second = new FindingEntry("second", "L4-5 protrusion", "", false);
    draft.addFinding(first);
    draft.addFinding(second);
    draft.addKeyImage(image("image", first.id()));

    draft.replaceFinding(first.id(), first.withText("Small L3-4 protrusion", "", false));
    draft.moveFinding(first.id(), 1);
    ReportPacket packet = draft.snapshot();

    assertEquals(first.id(), packet.findings().getLast().id());
    assertEquals(first.id(), packet.keyImages().getFirst().findingId());
    assertEquals("Small L3-4 protrusion", packet.findings().getLast().metadata().currentInput());
  }

  private static ReportDraft draft() {
    return new ReportDraft(new CaseContext("test-study", "", "", "", "", "", "MRI LUMBAR"));
  }

  private static KeyImageCapture image(String id, String findingId) {
    return KeyImageCapture.restore(
        id,
        new ImageReference("series", "sop", "1", "T2", "2", 2, 10),
        new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB),
        List.of(new ArrowPlacement(0.1, 0.2, 0.3, 0.4)),
        "Original caption",
        findingId,
        "user_selected",
        "2026-09-09T00:00:00Z",
        CaptureGeometry.unavailable(20, 20),
        List.of());
  }
}
