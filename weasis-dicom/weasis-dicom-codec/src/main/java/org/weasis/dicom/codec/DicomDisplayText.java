/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

import java.util.Locale;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.core.util.StringUtil;

public final class DicomDisplayText {
  private DicomDisplayText() {}

  public static String getExamTitle(MediaSeriesGroup study, MediaSeriesGroup series) {
    String title = getText(study, Tag.StudyDescription);
    if (StringUtil.hasText(title)) {
      return normalizeTitle(title);
    }

    title = buildAnatomyTitle(series);
    if (StringUtil.hasText(title)) {
      return title;
    }

    title = getText(series, Tag.ProtocolName);
    return StringUtil.hasText(title) ? normalizeTitle(title) : StringUtil.EMPTY_STRING;
  }

  public static String getSeriesTitle(MediaSeriesGroup series) {
    String title = getText(series, Tag.SeriesDescription);
    if (StringUtil.hasText(title)) {
      return normalizeTitle(title);
    }

    title = getText(series, Tag.ProtocolName);
    if (StringUtil.hasText(title)) {
      return normalizeTitle(title);
    }

    title = buildAnatomyTitle(series);
    return StringUtil.hasText(title) ? title : StringUtil.EMPTY_STRING;
  }

  public static String getViewTitle(DicomImageElement image, MediaSeriesGroup series) {
    String title = getText(image, Tag.ViewPosition);
    if (StringUtil.hasText(title)) {
      return normalizeTitle(title);
    }

    title = getText(image, Tag.ViewName);
    if (StringUtil.hasText(title)) {
      return normalizeTitle(title);
    }

    title = getLaterality(image, series);
    return StringUtil.hasText(title) ? title : getSeriesTitle(series);
  }

  private static String buildAnatomyTitle(MediaSeriesGroup series) {
    String bodyPart = normalizeTitle(getText(series, Tag.BodyPartExamined));
    String laterality = getLaterality(null, series);

    if (!StringUtil.hasText(bodyPart)) {
      return laterality;
    }
    if (!StringUtil.hasText(laterality) || containsIgnoreCase(bodyPart, laterality)) {
      return bodyPart;
    }
    return laterality + StringUtil.SPACE + bodyPart;
  }

  private static String getLaterality(TagReadable image, MediaSeriesGroup series) {
    String value = getText(image, Tag.FrameLaterality);
    if (!StringUtil.hasText(value)) {
      value = getText(image, Tag.ImageLaterality);
    }
    if (!StringUtil.hasText(value)) {
      value = getText(series, Tag.Laterality);
    }
    return formatLaterality(value);
  }

  private static String formatLaterality(String value) {
    if (!StringUtil.hasText(value)) {
      return StringUtil.EMPTY_STRING;
    }
    String normalized = clean(value);
    return switch (normalized.toUpperCase(Locale.ROOT)) {
      case "L", "LEFT" -> "Left";
      case "R", "RIGHT" -> "Right";
      case "B", "BILATERAL", "BOTH" -> "Bilateral";
      default -> normalizeTitle(normalized);
    };
  }

  private static String getText(TagReadable taggable, int tagId) {
    String value = TagD.getTagValue(taggable, tagId, String.class);
    return StringUtil.hasText(value) ? value : StringUtil.EMPTY_STRING;
  }

  private static boolean containsIgnoreCase(String text, String token) {
    return StringUtil.hasText(text)
        && StringUtil.hasText(token)
        && text.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT));
  }

  private static String normalizeTitle(String value) {
    String cleaned = clean(value);
    if (!StringUtil.hasText(cleaned)) {
      return StringUtil.EMPTY_STRING;
    }
    return isMostlyUpperCase(cleaned) ? toTitleCase(cleaned) : cleaned;
  }

  private static String clean(String value) {
    if (value == null) {
      return StringUtil.EMPTY_STRING;
    }
    return value.replace('^', ' ').replace('_', ' ').trim().replaceAll("\\s+", " ");
  }

  private static boolean isMostlyUpperCase(String value) {
    boolean hasLetter = false;
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      if (Character.isLetter(ch)) {
        hasLetter = true;
        if (Character.isLowerCase(ch)) {
          return false;
        }
      }
    }
    return hasLetter;
  }

  private static String toTitleCase(String value) {
    String[] words = value.split(" ");
    StringBuilder title = new StringBuilder(value.length());
    for (String word : words) {
      if (!title.isEmpty()) {
        title.append(StringUtil.SPACE);
      }
      title.append(toTitleCaseWord(word));
    }
    return title.toString();
  }

  private static String toTitleCaseWord(String word) {
    if (word.length() <= 3 || word.chars().noneMatch(Character::isLetter)) {
      return word;
    }
    return word.substring(0, 1).toUpperCase(Locale.ROOT)
        + word.substring(1).toLowerCase(Locale.ROOT);
  }
}
