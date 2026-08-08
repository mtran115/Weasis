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
import java.util.Objects;
import java.util.UUID;

public final class KeyImageCapture {
  private final String id;
  private final ImageReference reference;
  private final BufferedImage baseImage;
  private final ArrowPlacement arrow;
  private String caption;

  public KeyImageCapture(
      String id,
      ImageReference reference,
      BufferedImage baseImage,
      ArrowPlacement arrow,
      String caption) {
    this.id = ComposerText.clean(id);
    this.reference = Objects.requireNonNull(reference);
    this.baseImage = copy(Objects.requireNonNull(baseImage));
    this.arrow = arrow;
    this.caption = ComposerText.sentence(caption);
    if (this.id.isBlank()) {
      throw new IllegalArgumentException("A key image ID is required.");
    }
  }

  public static KeyImageCapture create(
      ImageReference reference, BufferedImage image, ArrowPlacement arrow, String caption) {
    return new KeyImageCapture(UUID.randomUUID().toString(), reference, image, arrow, caption);
  }

  public String id() {
    return id;
  }

  public ImageReference reference() {
    return reference;
  }

  public ArrowPlacement arrow() {
    return arrow;
  }

  public String caption() {
    return caption;
  }

  public void setCaption(String caption) {
    this.caption = ComposerText.sentence(caption);
  }

  public BufferedImage renderedImage() {
    return ArrowRenderer.render(baseImage, arrow);
  }

  BufferedImage baseImageCopy() {
    return copy(baseImage);
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
