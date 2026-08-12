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

final class ReportTextFormatter {
  private ReportTextFormatter() {}

  static String format(ReportPacket packet) {
    StringBuilder text = new StringBuilder();
    CaseContext context = packet.context();
    text.append("TRANSCRIPTION INSTRUCTIONS - NOT FINAL REPORT\n\n");
    appendField(text, "Patient", context.patientDisplayName());
    appendField(text, "Patient ID / MRN", context.patientId());
    appendField(text, "Date of birth", context.patientBirthDate());
    appendField(text, "Accession", context.accessionNumber());
    appendField(text, "Study date", context.studyDate());
    appendField(text, "Exam", context.examTitle());

    text.append("\nREPORT TEXT TO COPY\n\nFINDINGS\n");
    if (packet.findings().isEmpty()) {
      text.append("[No findings entered]\n");
    } else {
      for (FindingEntry finding : packet.findings()) {
        text.append(finding.findingText()).append('\n');
      }
    }

    text.append("\nIMPRESSION\n");
    String impression = packet.impressionText();
    text.append(impression.isBlank() ? "[No impression entered]" : impression).append('\n');

    if (!packet.reportInstructions().isBlank()) {
      text.append("\nADDITIONAL REPORT TEXT / INSTRUCTIONS\n");
      text.append(packet.reportInstructions()).append('\n');
    }

    text.append("\nKEY IMAGE INSTRUCTIONS\n");
    if (packet.keyImages().isEmpty()) {
      text.append("No key images selected.\n");
    } else {
      for (int index = 0; index < packet.keyImages().size(); index++) {
        KeyImageCapture keyImage = packet.keyImages().get(index);
        text.append("KI-").append(String.format("%02d", index + 1)).append(" - ");
        text.append(keyImage.reference().humanReference());
        if (!keyImage.caption().isBlank()) {
          text.append(": ").append(keyImage.caption());
        }
        text.append('\n');
      }
    }
    return text.toString();
  }

  private static void appendField(StringBuilder text, String label, String value) {
    text.append(label).append(": ");
    text.append(value.isBlank() ? "Not available" : value).append('\n');
  }
}
