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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.weasis.dicom.reportcomposer.MriFindingCatalog.GeneratedFinding;

/**
 * Sends a dry-run AI packet to OpenAI or Claude, after trimming it to the provider's limits, and
 * saves the raw response and a readable answer next to the packet.
 */
final class AiPacketSender {
  static final String TOOL_NAME = "record_findings";
  private static final String MARKED = "-marked";
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Pattern ESSENTIAL_SAGITTAL =
      Pattern.compile("\\bt1|\\bt2|stir|tirm", Pattern.CASE_INSENSITIVE);
  private static final Pattern ESSENTIAL_AXIAL = Pattern.compile("\\bt2", Pattern.CASE_INSENSITIVE);
  private static final Duration TIMEOUT = Duration.ofMinutes(15);
  private static final int CLAUDE_MAX_TOKENS = 16_000;

  record Sent(
      Path answerFile, String answerText, int imagesSent, List<String> notSent, JsonNode answer) {}

  // One request and its reply, before the readable answer is written.
  private record Exchange(
      ObjectNode request,
      JsonNode reply,
      JsonNode answer,
      int sent,
      int total,
      Fit fit,
      String suffix) {}

  /** Which images to leave out so a packet fits a provider's image count and size limits. */
  record Fit(Set<String> dropped, List<String> notes) {}

  record SeriesInfo(String id, String plane, String description) {}

  private AiPacketSender() {}

  /** Sends the blind request: every image, no rings. */
  static Sent send(
      Path packet, AiProvider provider, String model, String apiKey, URI endpoint, Clock clock)
      throws IOException, InterruptedException {
    Exchange exchange =
        exchange(packet, "request.json", "", provider, model, apiKey, endpoint, clock);
    String text = heading(provider, model, exchange, clock) + "\n\n" + answerText(exchange);
    return save(packet, exchange, text);
  }

  /**
   * Sends the marked request, after the blind one, and compares each ring with the blind read: a
   * ring at a level where the blind read already found something is listed as found blind.
   */
  static Sent sendMarked(
      Path packet,
      AiProvider provider,
      String model,
      String apiKey,
      URI endpoint,
      Clock clock,
      JsonNode blindAnswer)
      throws IOException, InterruptedException {
    Exchange exchange =
        exchange(
            packet,
            AiPacketExporter.MARKED_REQUEST,
            "-marked",
            provider,
            model,
            apiKey,
            endpoint,
            clock);
    String text =
        heading(provider, model + " (marked)", exchange, clock)
            + "\n\n"
            + ringsText(exchange.answer(), blindAnswer, lumbar(exchange.request()))
            + answerText(exchange);
    return save(packet, exchange, text);
  }

  static boolean hasMarks(Path packet) {
    return Files.isRegularFile(packet.resolve(AiPacketExporter.MARKED_REQUEST));
  }

  private static Exchange exchange(
      Path packet,
      String requestName,
      String tag,
      AiProvider provider,
      String model,
      String apiKey,
      URI endpoint,
      Clock clock)
      throws IOException, InterruptedException {
    ObjectNode request = (ObjectNode) JSON.readTree(packet.resolve(requestName).toFile());
    JsonNode manifest = JSON.readTree(packet.resolve("manifest.json").toFile());
    List<SeriesInfo> series = new ArrayList<>();
    manifest
        .path("series")
        .forEach(
            s ->
                series.add(
                    new SeriesInfo(
                        s.path("id").asText(),
                        s.path("plane").asText(),
                        s.path("description").asText())));
    Map<String, Long> imageBytes = new LinkedHashMap<>();
    for (JsonNode part : request.at("/input/0/content")) {
      if ("input_image".equals(part.path("type").asText())) {
        String file = part.path("image_url").asText();
        imageBytes.put(imageId(file), base64Length(Files.size(packet.resolve(file))));
      }
    }
    Fit fit = fit(series, imageBytes, provider.maxImages(), provider.maxImageBytes());
    if (imageBytes.size() == fit.dropped().size()) {
      throw new IOException("No images fit within " + provider.label() + "'s limits.");
    }
    ObjectNode body =
        provider == AiProvider.ANTHROPIC
            ? claudeBody(request, packet, model, fit)
            : openAiBody(request, packet, model, fit);

    HttpRequest.Builder http =
        HttpRequest.newBuilder(endpoint)
            .timeout(TIMEOUT)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
    if (provider == AiProvider.ANTHROPIC) {
      http.header("x-api-key", apiKey).header("anthropic-version", "2023-06-01");
    } else {
      http.header("Authorization", "Bearer " + apiKey);
    }
    HttpResponse<String> response;
    try (HttpClient client =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build()) {
      response = client.send(http.build(), HttpResponse.BodyHandlers.ofString());
    } catch (HttpTimeoutException e) {
      throw new IOException(provider.label() + " did not answer in time. Try again.", e);
    } catch (IOException e) {
      // Connection failures often carry no message, which would show as "null".
      throw new IOException(
          "Could not reach " + provider.label() + ". Check the internet connection.", e);
    }
    // Each send keeps its own files, so repeated runs on one packet are all kept.
    String suffix =
        provider.fileSuffix()
            + "-"
            + LocalDateTime.now(clock).format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
            + tag;
    Files.writeString(packet.resolve("response-" + suffix + ".json"), response.body());
    if (response.statusCode() / 100 != 2) {
      throw new IOException(provider.label() + " answered " + errorMessage(response));
    }
    JsonNode reply = JSON.readTree(response.body());
    JsonNode answer = provider == AiProvider.ANTHROPIC ? claudeAnswer(reply) : openAiAnswer(reply);
    return new Exchange(
        request,
        reply,
        answer,
        imageBytes.size() - fit.dropped().size(),
        imageBytes.size(),
        fit,
        suffix);
  }

  private static Sent save(Path packet, Exchange exchange, String text) throws IOException {
    JSON.writerWithDefaultPrettyPrinter()
        .writeValue(
            packet.resolve("answer-" + exchange.suffix() + ".json").toFile(), exchange.answer());
    Path answerFile = packet.resolve("answer-" + exchange.suffix() + ".txt");
    Files.writeString(answerFile, text, StandardCharsets.UTF_8);
    return new Sent(answerFile, text, exchange.sent(), exchange.fit().notes(), exchange.answer());
  }

  private static String answerText(Exchange exchange) {
    return answerText(exchange.answer(), lumbar(exchange.request()));
  }

  private static boolean lumbar(JsonNode request) {
    return AiLumbarRequest.FORMAT_NAME.equals(request.at("/text/format/name").asText());
  }

  /** Each ring's answer, and for lumbar studies whether the blind read reported its level. */
  static String ringsText(JsonNode markedAnswer, JsonNode blindAnswer, boolean lumbar) {
    Set<String> blindLevels = new LinkedHashSet<>();
    if (lumbar && blindAnswer != null) {
      AiLumbarRequest.read(blindAnswer).selection().levelSelections().stream()
          .filter(SpineFindingBuilder.LevelSelection::hasFinding)
          .forEach(level -> blindLevels.add(level.level()));
    }
    List<String> lines = new ArrayList<>();
    for (JsonNode ring : markedAnswer.path("marks")) {
      String location = ring.path("location").asText("").strip();
      String blind =
          !lumbar || blindAnswer == null || location.isEmpty() || "other".equalsIgnoreCase(location)
              ? ""
              : blindLevels.contains(location.toUpperCase(Locale.ROOT))
                  ? " · blind read also found " + location
                  : " · blind read reported nothing at " + location;
      lines.add(
          "Ring "
              + ring.path("number").asInt()
              + " ("
              + (location.isEmpty() ? "?" : location)
              + "): "
              + ring.path("finding").asText("").strip()
              + " ("
              + ring.path("confidence").asText("?")
              + " confidence)"
              + blind);
    }
    StringBuilder text = new StringBuilder();
    section(text, "RINGS", lines);
    return text.toString();
  }

  /**
   * Leaves out series that matter least for reading (anything but sagittal T1/T2/STIR and axial
   * T2), largest first, then every other slice of the largest remaining series, until it fits.
   */
  static Fit fit(
      List<SeriesInfo> series, Map<String, Long> imageBytes, int maxImages, long maxBytes) {
    Set<String> dropped = new LinkedHashSet<>();
    List<String> notes = new ArrayList<>();
    Map<String, List<String>> bySeries = new LinkedHashMap<>();
    imageBytes.keySet().stream()
        .filter(id -> !id.endsWith(MARKED))
        .forEach(id -> bySeries.computeIfAbsent(seriesOf(id), k -> new ArrayList<>()).add(id));
    List<SeriesInfo> optional =
        series.stream()
            .filter(s -> !essential(s))
            .sorted(
                Comparator.comparingInt(
                        (SeriesInfo s) -> bySeries.getOrDefault(s.id(), List.of()).size())
                    .reversed())
            .toList();
    for (SeriesInfo s : optional) {
      if (fits(imageBytes, dropped, maxImages, maxBytes)) {
        break;
      }
      if (dropped.addAll(bySeries.getOrDefault(s.id(), List.of()))) {
        notes.add(s.id() + " (" + s.plane() + " " + s.description().strip() + ") was not sent");
      }
    }
    while (!fits(imageBytes, dropped, maxImages, maxBytes)) {
      List<String> largest =
          bySeries.entrySet().stream()
              .map(
                  e ->
                      e.getValue().stream()
                          .filter(id -> !id.startsWith("LOC-") && !dropped.contains(id))
                          .toList())
              .filter(ids -> ids.size() > 2)
              .max(Comparator.comparingInt(List::size))
              .orElse(List.of());
      if (largest.isEmpty()) {
        break;
      }
      for (int index = 1; index < largest.size(); index += 2) {
        dropped.add(largest.get(index));
      }
      notes.add(seriesOf(largest.getFirst()) + ": only every other remaining slice was sent");
    }
    return new Fit(Set.copyOf(dropped), List.copyOf(notes));
  }

  static ObjectNode openAiBody(ObjectNode request, Path packet, String model, Fit fit)
      throws IOException {
    ObjectNode body = request.deepCopy();
    body.put("model", model);
    body.put("store", false);
    ArrayNode content = JSON.createArrayNode();
    for (JsonNode part : keptParts(request, fit)) {
      ObjectNode copy = part.deepCopy();
      if ("input_image".equals(part.path("type").asText())) {
        copy.put("image_url", "data:image/png;base64," + base64(packet, part));
      }
      content.add(copy);
    }
    ((ObjectNode) body.at("/input/0")).set("content", content);
    return body;
  }

  /**
   * Claude answers by calling a tool whose input schema is the answer format. Newer models refuse a
   * forced tool choice, and the format is too large for strict structured output, so the model is
   * asked to call the tool and the reader tolerates fields it leaves out.
   */
  static ObjectNode claudeBody(ObjectNode request, Path packet, String model, Fit fit)
      throws IOException {
    ArrayNode content = JSON.createArrayNode();
    for (JsonNode part : keptParts(request, fit)) {
      if ("input_image".equals(part.path("type").asText())) {
        ObjectNode image = content.addObject().put("type", "image");
        image
            .putObject("source")
            .put("type", "base64")
            .put("media_type", "image/png")
            .put("data", base64(packet, part));
      } else {
        content.addObject().put("type", "text").put("text", part.path("text").asText());
      }
    }
    ObjectNode body = JSON.createObjectNode();
    body.put("model", model);
    body.put("max_tokens", CLAUDE_MAX_TOKENS);
    body.put(
        "system",
        request.path("instructions").asText()
            + "\n\nGive your complete answer by calling the "
            + TOOL_NAME
            + " tool once.");
    ObjectNode message = body.putArray("messages").addObject().put("role", "user");
    message.set("content", content);
    ObjectNode tool = body.putArray("tools").addObject();
    tool.put("name", TOOL_NAME);
    tool.put("description", "Record the findings for this examination in the required structure.");
    tool.set("input_schema", request.at("/text/format/schema"));
    body.putObject("tool_choice").put("type", "auto");
    return body;
  }

  static JsonNode openAiAnswer(JsonNode reply) throws IOException {
    if (!"completed".equals(reply.path("status").asText("completed"))) {
      throw new IOException(
          "The answer was incomplete: " + reply.at("/incomplete_details/reason").asText("unknown"));
    }
    for (JsonNode item : reply.path("output")) {
      for (JsonNode part : item.path("content")) {
        if ("refusal".equals(part.path("type").asText())) {
          throw new IOException("The model declined: " + part.path("refusal").asText());
        }
        if ("output_text".equals(part.path("type").asText())) {
          return JSON.readTree(part.path("text").asText());
        }
      }
    }
    throw new IOException("The answer contained no findings.");
  }

  static JsonNode claudeAnswer(JsonNode reply) throws IOException {
    if ("max_tokens".equals(reply.path("stop_reason").asText())) {
      throw new IOException("The answer was cut off before it finished.");
    }
    StringBuilder text = new StringBuilder();
    for (JsonNode part : reply.path("content")) {
      if ("tool_use".equals(part.path("type").asText())
          && TOOL_NAME.equals(part.path("name").asText())) {
        return part.path("input");
      }
      text.append(part.path("text").asText(""));
    }
    String answer = text.toString().strip();
    if (answer.startsWith("{")) {
      return JSON.readTree(answer);
    }
    throw new IOException(
        "Claude answered without the structured findings: "
            + (answer.length() > 300 ? answer.substring(0, 300) + "…" : answer));
  }

  /** The answer as the composer would write it, plus what to check before using it. */
  static String answerText(JsonNode answer, boolean lumbar) {
    StringBuilder text = new StringBuilder();
    if (lumbar) {
      AiLumbarRequest.Answer read = AiLumbarRequest.read(answer);
      List<GeneratedFinding> findings = SpineFindingBuilder.generate(read.selection());
      section(text, "FINDINGS", findings.stream().map(GeneratedFinding::findingText).toList());
      section(text, "ADDITIONAL FINDINGS", read.additionalFindings());
      section(
          text,
          "IMPRESSION",
          findings.stream()
              .map(GeneratedFinding::impressionText)
              .filter(s -> !s.isBlank())
              .toList());
      section(text, "LIMITATIONS", read.limitations());
      section(
          text,
          "IMAGES CITED",
          read.imagesByLevel().entrySet().stream()
              .map(
                  e ->
                      e.getKey()
                          + Optional.ofNullable(read.confidenceByLevel().get(e.getKey()))
                              .map(confidence -> " (" + confidence + " confidence)")
                              .orElse("")
                          + ": "
                          + String.join(", ", e.getValue()))
              .toList());
      section(text, "CHECK", read.warnings());
    } else {
      List<String> findings = new ArrayList<>();
      for (JsonNode finding : answer.path("findings")) {
        findings.add(
            finding.path("location").asText()
                + ": "
                + finding.path("finding").asText()
                + " ("
                + finding.path("severity").asText()
                + ", "
                + finding.path("confidence").asText()
                + " confidence; "
                + String.join(", ", strings(finding.path("image_ids")))
                + ")");
      }
      section(text, "FINDINGS", findings);
      section(text, "LIMITATIONS", strings(answer.path("limitations")));
    }
    return text.toString().strip() + "\n";
  }

  private static String heading(AiProvider provider, String model, Exchange exchange, Clock clock) {
    JsonNode reply = exchange.reply();
    int sent = exchange.sent();
    int total = exchange.total();
    Fit fit = exchange.fit();
    StringBuilder text = new StringBuilder();
    text.append(provider.label()).append(" · ").append(model).append(" · ");
    text.append(LocalDateTime.now(clock).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
    text.append("\nImages sent: ").append(sent).append(" of ").append(total);
    fit.notes().forEach(note -> text.append("\n  ").append(note));
    JsonNode usage = reply.path("usage");
    if (usage.has("input_tokens")) {
      text.append(
          String.format(
              Locale.ROOT,
              "%nTokens: %,d in, %,d out",
              usage.path("input_tokens").asLong(),
              usage.path("output_tokens").asLong()));
    }
    return text.toString();
  }

  private static void section(StringBuilder text, String title, List<String> lines) {
    if (!lines.isEmpty()) {
      text.append(title).append('\n');
      lines.forEach(line -> text.append(line).append('\n'));
      text.append('\n');
    }
  }

  // The overview, then each kept image with its label; a note says what was left out.
  private static List<JsonNode> keptParts(ObjectNode request, Fit fit) {
    List<JsonNode> parts = new ArrayList<>();
    JsonNode content = request.at("/input/0/content");
    for (int index = 0; index < content.size(); index++) {
      JsonNode part = content.get(index);
      JsonNode next = index + 1 < content.size() ? content.get(index + 1) : null;
      boolean labelOfDropped =
          next != null
              && "input_image".equals(next.path("type").asText())
              && fit.dropped().contains(imageId(next.path("image_url").asText()));
      boolean droppedImage =
          "input_image".equals(part.path("type").asText())
              && fit.dropped().contains(imageId(part.path("image_url").asText()));
      if (!labelOfDropped && !droppedImage) {
        parts.add(part);
      }
    }
    if (!fit.notes().isEmpty() && !parts.isEmpty()) {
      ObjectNode overview = parts.getFirst().deepCopy();
      overview.put(
          "text",
          overview.path("text").asText()
              + "\nNot sent, to fit the request limits: "
              + String.join("; ", fit.notes())
              + ".");
      parts.set(0, overview);
    }
    return parts;
  }

  private static boolean essential(SeriesInfo series) {
    return switch (series.plane()) {
      case "sagittal" -> ESSENTIAL_SAGITTAL.matcher(series.description()).find();
      case "axial" -> ESSENTIAL_AXIAL.matcher(series.description()).find();
      default -> false;
    };
  }

  private static boolean fits(
      Map<String, Long> imageBytes, Set<String> dropped, int maxImages, long maxBytes) {
    long count = imageBytes.keySet().stream().filter(id -> !dropped.contains(id)).count();
    long bytes =
        imageBytes.entrySet().stream()
            .filter(e -> !dropped.contains(e.getKey()))
            .mapToLong(Map.Entry::getValue)
            .sum();
    return count <= maxImages && bytes <= maxBytes;
  }

  private static String seriesOf(String imageId) {
    return imageId.startsWith("LOC-")
        ? imageId.substring(4)
        : imageId.substring(0, imageId.indexOf('-'));
  }

  private static String imageId(String file) {
    String name = file.substring(file.lastIndexOf('/') + 1);
    return name.endsWith(".png") ? name.substring(0, name.length() - 4) : name;
  }

  private static long base64Length(long bytes) {
    return 4 * ((bytes + 2) / 3);
  }

  private static String base64(Path packet, JsonNode part) throws IOException {
    Path file = packet.resolve(part.path("image_url").asText()).normalize();
    if (!file.startsWith(packet.normalize())) {
      throw new IOException("An image path points outside the packet.");
    }
    return Base64.getEncoder().encodeToString(Files.readAllBytes(file));
  }

  private static String errorMessage(HttpResponse<String> response) {
    try {
      String message = JSON.readTree(response.body()).at("/error/message").asText("");
      return "HTTP " + response.statusCode() + (message.isBlank() ? "" : ": " + message);
    } catch (IOException e) {
      return "HTTP " + response.statusCode();
    }
  }

  private static List<String> strings(JsonNode values) {
    List<String> strings = new ArrayList<>();
    values.forEach(value -> strings.add(value.asText()));
    return strings;
  }
}
