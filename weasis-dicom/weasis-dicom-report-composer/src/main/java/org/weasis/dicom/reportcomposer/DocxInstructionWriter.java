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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.weasis.dicom.reportcomposer.CasePacketExporter.ExportedKeyImage;

final class DocxInstructionWriter {
  private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
  private static final String R =
      "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
  private static final String WP =
      "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing";
  private static final String A = "http://schemas.openxmlformats.org/drawingml/2006/main";
  private static final String PIC = "http://schemas.openxmlformats.org/drawingml/2006/picture";
  private static final String REL = "http://schemas.openxmlformats.org/package/2006/relationships";
  private static final String CT = "http://schemas.openxmlformats.org/package/2006/content-types";
  private static final long EMU_PER_INCH = 914400L;

  private DocxInstructionWriter() {}

  static void write(Path destination, ReportPacket packet, List<ExportedKeyImage> images)
      throws IOException {
    try (OutputStream output = Files.newOutputStream(destination);
        ZipOutputStream zip = new ZipOutputStream(output)) {
      writeContentTypes(zip);
      writePackageRelationships(zip);
      writeCoreProperties(zip);
      writeAppProperties(zip);
      writeStyles(zip);
      writeDocumentRelationships(zip, images);
      writeDocument(zip, packet, images);
      writeImages(zip, images);
    } catch (XMLStreamException error) {
      throw new IOException("The transcription DOCX could not be generated.", error);
    }
  }

  private static void writeContentTypes(ZipOutputStream zip)
      throws IOException, XMLStreamException {
    writeXml(
        zip,
        "[Content_Types].xml",
        writer -> {
          writer.writeStartElement("Types");
          writer.writeDefaultNamespace(CT);
          empty(
              writer,
              "Default",
              "Extension",
              "rels",
              "ContentType",
              "application/vnd.openxmlformats-package.relationships+xml");
          empty(writer, "Default", "Extension", "xml", "ContentType", "application/xml");
          empty(writer, "Default", "Extension", "png", "ContentType", "image/png");
          empty(
              writer,
              "Override",
              "PartName",
              "/word/document.xml",
              "ContentType",
              "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml");
          empty(
              writer,
              "Override",
              "PartName",
              "/word/styles.xml",
              "ContentType",
              "application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml");
          empty(
              writer,
              "Override",
              "PartName",
              "/docProps/core.xml",
              "ContentType",
              "application/vnd.openxmlformats-package.core-properties+xml");
          empty(
              writer,
              "Override",
              "PartName",
              "/docProps/app.xml",
              "ContentType",
              "application/vnd.openxmlformats-officedocument.extended-properties+xml");
          writer.writeEndElement();
        });
  }

  private static void writePackageRelationships(ZipOutputStream zip)
      throws IOException, XMLStreamException {
    writeXml(
        zip,
        "_rels/.rels",
        writer -> {
          writer.writeStartElement("Relationships");
          writer.writeDefaultNamespace(REL);
          empty(
              writer,
              "Relationship",
              "Id",
              "rId1",
              "Type",
              R + "/officeDocument",
              "Target",
              "word/document.xml");
          empty(
              writer,
              "Relationship",
              "Id",
              "rId2",
              "Type",
              "http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties",
              "Target",
              "docProps/core.xml");
          empty(
              writer,
              "Relationship",
              "Id",
              "rId3",
              "Type",
              R + "/extended-properties",
              "Target",
              "docProps/app.xml");
          writer.writeEndElement();
        });
  }

  private static void writeCoreProperties(ZipOutputStream zip)
      throws IOException, XMLStreamException {
    writeXml(
        zip,
        "docProps/core.xml",
        writer -> {
          writer.writeStartElement(
              "cp",
              "coreProperties",
              "http://schemas.openxmlformats.org/package/2006/metadata/core-properties");
          writer.writeNamespace(
              "cp", "http://schemas.openxmlformats.org/package/2006/metadata/core-properties");
          writer.writeNamespace("dc", "http://purl.org/dc/elements/1.1/");
          writer.writeNamespace("dcterms", "http://purl.org/dc/terms/");
          writer.writeNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance");
          text(
              writer,
              "dc",
              "title",
              "http://purl.org/dc/elements/1.1/",
              "Transcription Instructions");
          text(
              writer,
              "dc",
              "creator",
              "http://purl.org/dc/elements/1.1/",
              "Weasis Report Composer");
          writer.writeStartElement("dcterms", "created", "http://purl.org/dc/terms/");
          writer.writeAttribute(
              "xsi", "http://www.w3.org/2001/XMLSchema-instance", "type", "dcterms:W3CDTF");
          writer.writeCharacters(OffsetDateTime.now(ZoneOffset.UTC).toString());
          writer.writeEndElement();
          writer.writeEndElement();
        });
  }

  private static void writeAppProperties(ZipOutputStream zip)
      throws IOException, XMLStreamException {
    writeXml(
        zip,
        "docProps/app.xml",
        writer -> {
          writer.writeStartElement("Properties");
          writer.writeDefaultNamespace(
              "http://schemas.openxmlformats.org/officeDocument/2006/extended-properties");
          text(writer, "Application", "Weasis Report Composer");
          text(writer, "AppVersion", "1.0");
          writer.writeEndElement();
        });
  }

  private static void writeStyles(ZipOutputStream zip) throws IOException, XMLStreamException {
    writeXml(
        zip,
        "word/styles.xml",
        writer -> {
          writer.writeStartElement("w", "styles", W);
          writer.writeNamespace("w", W);
          writeStyle(writer, "Normal", "Normal", 20, false, "000000", 0, 120);
          writeStyle(writer, "InstructionTitle", "Instruction Title", 34, true, "111111", 0, 200);
          writeStyle(writer, "Heading1", "Heading 1", 28, true, "1F4E79", 240, 100);
          writeStyle(writer, "Heading2", "Heading 2", 22, true, "333333", 180, 80);
          writer.writeEndElement();
        });
  }

  private static void writeStyle(
      XMLStreamWriter writer,
      String id,
      String name,
      int halfPoints,
      boolean bold,
      String color,
      int before,
      int after)
      throws XMLStreamException {
    writer.writeStartElement("w", "style", W);
    writer.writeAttribute("w", W, "type", "paragraph");
    writer.writeAttribute("w", W, "styleId", id);
    if ("Normal".equals(id)) {
      writer.writeAttribute("w", W, "default", "1");
    }
    emptyW(writer, "name", "val", name);
    writer.writeStartElement("w", "pPr", W);
    emptyW(
        writer,
        "spacing",
        "before",
        Integer.toString(before),
        "after",
        Integer.toString(after),
        "line",
        "276",
        "lineRule",
        "auto");
    writer.writeEndElement();
    writer.writeStartElement("w", "rPr", W);
    emptyW(writer, "rFonts", "ascii", "Arial", "hAnsi", "Arial", "eastAsia", "Arial");
    emptyW(writer, "color", "val", color);
    emptyW(writer, "sz", "val", Integer.toString(halfPoints));
    emptyW(writer, "szCs", "val", Integer.toString(halfPoints));
    if (bold) {
      emptyW(writer, "b");
    }
    writer.writeEndElement();
    writer.writeEndElement();
  }

  private static void writeDocumentRelationships(ZipOutputStream zip, List<ExportedKeyImage> images)
      throws IOException, XMLStreamException {
    writeXml(
        zip,
        "word/_rels/document.xml.rels",
        writer -> {
          writer.writeStartElement("Relationships");
          writer.writeDefaultNamespace(REL);
          empty(
              writer, "Relationship", "Id", "rId1", "Type", R + "/styles", "Target", "styles.xml");
          for (int index = 0; index < images.size(); index++) {
            empty(
                writer,
                "Relationship",
                "Id",
                "rId" + (index + 2),
                "Type",
                R + "/image",
                "Target",
                "media/image" + (index + 1) + ".png");
          }
          writer.writeEndElement();
        });
  }

  private static void writeDocument(
      ZipOutputStream zip, ReportPacket packet, List<ExportedKeyImage> images)
      throws IOException, XMLStreamException {
    writeXml(
        zip,
        "word/document.xml",
        writer -> {
          writer.writeStartElement("w", "document", W);
          writer.writeNamespace("w", W);
          writer.writeNamespace("r", R);
          writer.writeNamespace("wp", WP);
          writer.writeNamespace("a", A);
          writer.writeNamespace("pic", PIC);
          writer.writeStartElement("w", "body", W);

          paragraph(writer, "TRANSCRIPTION INSTRUCTIONS", "InstructionTitle");
          warningParagraph(
              writer, "NOT A FINAL REPORT - COPY CONTENT INTO THE APPROVED REPORT TEMPLATE");
          paragraph(writer, packet.context().examTitle(), "Heading1");
          labeledParagraph(writer, "Patient", packet.context().patientDisplayName());
          labeledParagraph(writer, "Patient ID / MRN", available(packet.context().patientId()));
          labeledParagraph(writer, "Date of birth", available(packet.context().patientBirthDate()));
          labeledParagraph(writer, "Accession", available(packet.context().accessionNumber()));
          labeledParagraph(writer, "Study date", available(packet.context().studyDate()));

          paragraph(writer, "REPORT TEXT / INSTRUCTIONS", "Heading1");
          String reportText = packet.reportText();
          if (reportText.isBlank()) {
            paragraph(writer, "[No report text entered]", "Normal");
          } else {
            for (String line : reportText.split("\\R", -1)) {
              paragraph(writer, line, "Normal");
            }
          }

          paragraph(writer, "KEY IMAGE INSTRUCTIONS", "Heading1");
          if (images.isEmpty()) {
            paragraph(writer, "No key images selected.", "Normal");
          } else {
            for (int index = 0; index < images.size(); index++) {
              ExportedKeyImage image = images.get(index);
              if (index > 0) {
                pageBreak(writer);
              }
              String identifier = "KI-" + String.format("%02d", image.number());
              paragraph(writer, identifier + " - " + image.imageReference(), "Heading2");
              drawing(writer, image, "rId" + (index + 2), index + 1);
              paragraph(writer, "Separate file: " + image.fileName(), "Normal");
            }
          }

          sectionProperties(writer);
          writer.writeEndElement();
          writer.writeEndElement();
        });
  }

  private static void drawing(
      XMLStreamWriter writer, ExportedKeyImage image, String relationshipId, int drawingId)
      throws XMLStreamException {
    long[] extent = imageExtent(image.width(), image.height());
    writer.writeStartElement("w", "p", W);
    writer.writeStartElement("w", "r", W);
    writer.writeStartElement("w", "drawing", W);
    writer.writeStartElement("wp", "inline", WP);
    writer.writeAttribute("distT", "0");
    writer.writeAttribute("distB", "0");
    writer.writeAttribute("distL", "0");
    writer.writeAttribute("distR", "0");
    emptyNs(
        writer, "wp", "extent", WP, "cx", Long.toString(extent[0]), "cy", Long.toString(extent[1]));
    emptyNs(writer, "wp", "effectExtent", WP, "l", "0", "t", "0", "r", "0", "b", "0");
    emptyNs(
        writer,
        "wp",
        "docPr",
        WP,
        "id",
        Integer.toString(drawingId),
        "name",
        "Key image " + drawingId,
        "descr",
        image.imageReference());
    writer.writeStartElement("wp", "cNvGraphicFramePr", WP);
    emptyNs(writer, "a", "graphicFrameLocks", A, "noChangeAspect", "1");
    writer.writeEndElement();
    writer.writeStartElement("a", "graphic", A);
    writer.writeStartElement("a", "graphicData", A);
    writer.writeAttribute("uri", PIC);
    writer.writeStartElement("pic", "pic", PIC);
    writer.writeStartElement("pic", "nvPicPr", PIC);
    emptyNs(
        writer,
        "pic",
        "cNvPr",
        PIC,
        "id",
        "0",
        "name",
        image.fileName(),
        "descr",
        image.imageReference());
    emptyNs(writer, "pic", "cNvPicPr", PIC);
    writer.writeEndElement();
    writer.writeStartElement("pic", "blipFill", PIC);
    writer.writeStartElement("a", "blip", A);
    writer.writeAttribute("r", R, "embed", relationshipId);
    writer.writeEndElement();
    writer.writeStartElement("a", "stretch", A);
    emptyNs(writer, "a", "fillRect", A);
    writer.writeEndElement();
    writer.writeEndElement();
    writer.writeStartElement("pic", "spPr", PIC);
    writer.writeStartElement("a", "xfrm", A);
    emptyNs(writer, "a", "off", A, "x", "0", "y", "0");
    emptyNs(writer, "a", "ext", A, "cx", Long.toString(extent[0]), "cy", Long.toString(extent[1]));
    writer.writeEndElement();
    writer.writeStartElement("a", "prstGeom", A);
    writer.writeAttribute("prst", "rect");
    emptyNs(writer, "a", "avLst", A);
    writer.writeEndElement();
    writer.writeEndElement();
    writer.writeEndElement();
    writer.writeEndElement();
    writer.writeEndElement();
    writer.writeEndElement();
    writer.writeEndElement();
    writer.writeEndElement();
    writer.writeEndElement();
  }

  private static long[] imageExtent(int width, int height) {
    double maxWidth = 6.5 * EMU_PER_INCH;
    double maxHeight = 7.0 * EMU_PER_INCH;
    double scale = Math.min(maxWidth / width, maxHeight / height);
    scale = Math.min(scale, EMU_PER_INCH / 120.0);
    return new long[] {Math.round(width * scale), Math.round(height * scale)};
  }

  private static void paragraph(XMLStreamWriter writer, String text, String style)
      throws XMLStreamException {
    writer.writeStartElement("w", "p", W);
    writer.writeStartElement("w", "pPr", W);
    emptyW(writer, "pStyle", "val", style);
    writer.writeEndElement();
    writer.writeStartElement("w", "r", W);
    writeText(writer, text);
    writer.writeEndElement();
    writer.writeEndElement();
  }

  private static void labeledParagraph(XMLStreamWriter writer, String label, String value)
      throws XMLStreamException {
    writer.writeStartElement("w", "p", W);
    writer.writeStartElement("w", "pPr", W);
    emptyW(writer, "pStyle", "val", "Normal");
    writer.writeEndElement();
    writer.writeStartElement("w", "r", W);
    writer.writeStartElement("w", "rPr", W);
    emptyW(writer, "b");
    writer.writeEndElement();
    writeText(writer, label + ": ");
    writer.writeEndElement();
    writer.writeStartElement("w", "r", W);
    writeText(writer, value);
    writer.writeEndElement();
    writer.writeEndElement();
  }

  private static void warningParagraph(XMLStreamWriter writer, String text)
      throws XMLStreamException {
    writer.writeStartElement("w", "p", W);
    writer.writeStartElement("w", "pPr", W);
    emptyW(writer, "shd", "val", "clear", "color", "auto", "fill", "FFF2CC");
    emptyW(writer, "spacing", "before", "80", "after", "180");
    writer.writeEndElement();
    writer.writeStartElement("w", "r", W);
    writer.writeStartElement("w", "rPr", W);
    emptyW(writer, "b");
    emptyW(writer, "color", "val", "9C5700");
    writer.writeEndElement();
    writeText(writer, text);
    writer.writeEndElement();
    writer.writeEndElement();
  }

  private static void pageBreak(XMLStreamWriter writer) throws XMLStreamException {
    writer.writeStartElement("w", "p", W);
    writer.writeStartElement("w", "r", W);
    emptyW(writer, "br", "type", "page");
    writer.writeEndElement();
    writer.writeEndElement();
  }

  private static void sectionProperties(XMLStreamWriter writer) throws XMLStreamException {
    writer.writeStartElement("w", "sectPr", W);
    emptyW(writer, "pgSz", "w", "12240", "h", "15840");
    emptyW(
        writer, "pgMar", "top", "1080", "right", "1080", "bottom", "1080", "left", "1080", "header",
        "720", "footer", "720", "gutter", "0");
    writer.writeEndElement();
  }

  private static void writeText(XMLStreamWriter writer, String text) throws XMLStreamException {
    writer.writeStartElement("w", "t", W);
    writer.writeAttribute("xml", "http://www.w3.org/XML/1998/namespace", "space", "preserve");
    writer.writeCharacters(text == null ? "" : text);
    writer.writeEndElement();
  }

  private static String available(String value) {
    return value.isBlank() ? "Not available" : value;
  }

  private static void writeImages(ZipOutputStream zip, List<ExportedKeyImage> images)
      throws IOException {
    for (int index = 0; index < images.size(); index++) {
      zip.putNextEntry(new ZipEntry("word/media/image" + (index + 1) + ".png"));
      Files.copy(images.get(index).path(), zip);
      zip.closeEntry();
    }
  }

  private static void writeXml(ZipOutputStream zip, String name, XmlWriter action)
      throws IOException, XMLStreamException {
    zip.putNextEntry(new ZipEntry(name));
    XMLStreamWriter writer =
        XMLOutputFactory.newFactory()
            .createXMLStreamWriter(new NonClosingOutputStream(zip), "UTF-8");
    writer.writeStartDocument("UTF-8", "1.0");
    action.write(writer);
    writer.writeEndDocument();
    writer.flush();
    zip.closeEntry();
  }

  private static void text(XMLStreamWriter writer, String element, String value)
      throws XMLStreamException {
    writer.writeStartElement(element);
    writer.writeCharacters(value);
    writer.writeEndElement();
  }

  private static void text(
      XMLStreamWriter writer, String prefix, String element, String namespace, String value)
      throws XMLStreamException {
    writer.writeStartElement(prefix, element, namespace);
    writer.writeCharacters(value);
    writer.writeEndElement();
  }

  private static void empty(XMLStreamWriter writer, String element, String... attributes)
      throws XMLStreamException {
    writer.writeEmptyElement(element);
    for (int index = 0; index < attributes.length; index += 2) {
      writer.writeAttribute(attributes[index], attributes[index + 1]);
    }
  }

  private static void emptyW(XMLStreamWriter writer, String element, String... attributes)
      throws XMLStreamException {
    writer.writeEmptyElement("w", element, W);
    for (int index = 0; index < attributes.length; index += 2) {
      writer.writeAttribute("w", W, attributes[index], attributes[index + 1]);
    }
  }

  private static void emptyNs(
      XMLStreamWriter writer, String prefix, String element, String namespace, String... attributes)
      throws XMLStreamException {
    writer.writeEmptyElement(prefix, element, namespace);
    for (int index = 0; index < attributes.length; index += 2) {
      writer.writeAttribute(attributes[index], attributes[index + 1]);
    }
  }

  @FunctionalInterface
  private interface XmlWriter {
    void write(XMLStreamWriter writer) throws XMLStreamException;
  }

  private static final class NonClosingOutputStream extends OutputStream {
    private final OutputStream delegate;

    private NonClosingOutputStream(OutputStream delegate) {
      this.delegate = delegate;
    }

    @Override
    public void write(int value) throws IOException {
      delegate.write(value);
    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws IOException {
      delegate.write(bytes, offset, length);
    }

    @Override
    public void flush() throws IOException {
      delegate.flush();
    }

    @Override
    public void close() {
      // The ZIP stream owns the underlying output.
    }
  }
}
