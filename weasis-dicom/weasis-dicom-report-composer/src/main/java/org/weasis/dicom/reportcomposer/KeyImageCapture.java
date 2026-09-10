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

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class KeyImageCapture {
  private final String id;
  private final ImageReference reference;
  private final BufferedImage baseImage;
  private final List<ArrowPlacement> arrows;
  private final String findingId;
  private final String findingLinkSource;
  private final String capturedAt;
  private final CaptureGeometry captureGeometry;
  private final List<SourceArrowPlacement> sourceArrows;
  private String caption;

  public KeyImageCapture(
      String id,
      ImageReference reference,
      BufferedImage baseImage,
      ArrowPlacement arrow,
      String caption) {
    this(id, reference, baseImage, arrow == null ? List.of() : List.of(arrow), caption);
  }

  private KeyImageCapture(
      String id,
      ImageReference reference,
      BufferedImage baseImage,
      List<ArrowPlacement> arrows,
      String caption) {
    this(
        id,
        reference,
        baseImage,
        arrows,
        caption,
        "",
        "unlinked",
        Instant.now().toString(),
        CaptureGeometry.unavailable(baseImage.getWidth(), baseImage.getHeight()),
        List.of(),
        true);
  }

  private KeyImageCapture(
      String id,
      ImageReference reference,
      BufferedImage baseImage,
      List<ArrowPlacement> arrows,
      String caption,
      String findingId,
      String findingLinkSource,
      String capturedAt,
      CaptureGeometry captureGeometry,
      List<SourceArrowPlacement> sourceArrows,
      boolean copyPixels) {
    this.id = ComposerText.clean(id);
    this.reference = Objects.requireNonNull(reference);
    this.baseImage = copyPixels ? copy(Objects.requireNonNull(baseImage)) : baseImage;
    this.arrows = List.copyOf(arrows);
    this.caption = ComposerText.sentence(caption);
    this.findingId = ComposerText.clean(findingId);
    this.findingLinkSource = ComposerText.clean(findingLinkSource);
    this.capturedAt = ComposerText.clean(capturedAt);
    this.captureGeometry =
        captureGeometry == null
            ? CaptureGeometry.unavailable(baseImage.getWidth(), baseImage.getHeight())
            : captureGeometry;
    this.sourceArrows = sourceArrows == null ? List.of() : List.copyOf(sourceArrows);
    if (this.id.isBlank()) {
      throw new IllegalArgumentException("A key image ID is required.");
    }
  }

  public static KeyImageCapture create(
      ImageReference reference, BufferedImage image, ArrowPlacement arrow, String caption) {
    return createWithArrows(reference, image, arrow == null ? List.of() : List.of(arrow), caption);
  }

  public static KeyImageCapture createWithArrows(
      ImageReference reference, BufferedImage image, List<ArrowPlacement> arrows, String caption) {
    return new KeyImageCapture(UUID.randomUUID().toString(), reference, image, arrows, caption);
  }

  static KeyImageCapture createWithArrows(
      DicomContextReader.CapturedView capture,
      List<ArrowPlacement> arrows,
      String caption,
      String findingId,
      String findingLinkSource) {
    return new KeyImageCapture(
        UUID.randomUUID().toString(),
        capture.reference(),
        capture.image(),
        arrows,
        caption,
        findingId,
        findingLinkSource,
        capture.capturedAt(),
        capture.geometry(),
        capture.geometry().sourceArrows(arrows, capture.reference().geometry()),
        true);
  }

  public static KeyImageCapture restore(
      String id,
      ImageReference reference,
      BufferedImage baseImage,
      List<ArrowPlacement> arrows,
      String caption,
      String findingId,
      String findingLinkSource,
      String capturedAt,
      CaptureGeometry captureGeometry,
      List<SourceArrowPlacement> sourceArrows) {
    return new KeyImageCapture(
        id,
        reference,
        baseImage,
        arrows,
        caption,
        findingId,
        findingLinkSource,
        capturedAt,
        captureGeometry,
        sourceArrows,
        true);
  }

  /** Base pixels are private and immutable; snapshot only the mutable caption. */
  public KeyImageCapture snapshot() {
    return new KeyImageCapture(
        id,
        reference,
        baseImage,
        arrows,
        caption,
        findingId,
        findingLinkSource,
        capturedAt,
        captureGeometry,
        sourceArrows,
        false);
  }

  public KeyImageCapture withFindingLink(String newFindingId, String newLinkSource) {
    return new KeyImageCapture(
        id,
        reference,
        baseImage,
        arrows,
        caption,
        newFindingId,
        newLinkSource,
        capturedAt,
        captureGeometry,
        sourceArrows,
        false);
  }

  public String findingId() {
    return findingId;
  }

  public String findingLinkSource() {
    return findingLinkSource;
  }

  public String capturedAt() {
    return capturedAt;
  }

  public CaptureGeometry captureGeometry() {
    return captureGeometry;
  }

  public List<SourceArrowPlacement> sourceArrows() {
    return sourceArrows;
  }

  public String id() {
    return id;
  }

  public ImageReference reference() {
    return reference;
  }

  public ArrowPlacement arrow() {
    return arrows.isEmpty() ? null : arrows.getFirst();
  }

  public List<ArrowPlacement> arrows() {
    return arrows;
  }

  public String caption() {
    return caption;
  }

  public void setCaption(String caption) {
    this.caption = ComposerText.sentence(caption);
  }

  public BufferedImage renderedImage() {
    return ArrowRenderer.renderAll(baseImage, arrows);
  }

  BufferedImage baseImageCopy() {
    return copy(baseImage);
  }

  Object baseImageIdentity() {
    return baseImage;
  }

  @Override
  public String toString() {
    String text = reference.humanReference();
    return caption.isBlank() ? text : text + " - " + caption;
  }

  private static BufferedImage copy(BufferedImage source) {
    BufferedImage copy =
        new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = copy.createGraphics();
    graphics.drawImage(source, 0, 0, null);
    graphics.dispose();
    return copy;
  }
}
