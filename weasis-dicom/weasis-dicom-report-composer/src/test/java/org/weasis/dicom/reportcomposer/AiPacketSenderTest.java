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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AiPacketSenderTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-05T20:00:00Z"), ZoneId.of("America/Los_Angeles"));
  private static final String ANSWER =
      """
      {"alignment": {"lordosis": "straightened", "scoliosis": "none",
                     "scoliosis_severity": "none", "scoliosis_degrees": null},
       "spondylosis": {"extent": "generalized", "levels": []},
       "levels": [{"level": "L4-5", "bulge": true, "herniation": "none",
                   "herniation_locations": [], "disc_size_mm": 1, "image_ids": ["S2-02"]}],
       "transitional_anatomy": "lumbarized_s1",
       "limitations": [{"kind": "motion", "detail": ""}]}
      """;

  @TempDir Path root;

  @Test
  void leavesOutLeastUsefulSeriesFirstThenThinsTheLargest() {
    List<AiPacketSender.SeriesInfo> series =
        List.of(
            new AiPacketSender.SeriesInfo("S1", "sagittal", "T2 SAG"),
            new AiPacketSender.SeriesInfo("S2", "sagittal", "T1W_TSE"),
            new AiPacketSender.SeriesInfo("S3", "axial", "T2 AX"),
            new AiPacketSender.SeriesInfo("S4", "axial", "T1 AX"));
    Map<String, Long> images = new LinkedHashMap<>();
    addSeries(images, "S1", 20);
    addSeries(images, "S2", 20);
    images.put("LOC-S3", 10L);
    addSeries(images, "S3", 40);
    images.put("LOC-S4", 10L);
    addSeries(images, "S4", 40);

    AiPacketSender.Fit underCount = AiPacketSender.fit(series, images, 100, Long.MAX_VALUE);
    assertEquals(41, underCount.dropped().size());
    assertTrue(underCount.dropped().contains("LOC-S4"));
    assertEquals(List.of("S4 (axial T1 AX) was not sent"), underCount.notes());

    AiPacketSender.Fit thinned = AiPacketSender.fit(series, images, 60, Long.MAX_VALUE);
    assertTrue(images.size() - thinned.dropped().size() <= 60);
    assertTrue(thinned.dropped().contains("S3-02") && !thinned.dropped().contains("S3-01"));
    assertFalse(thinned.dropped().contains("LOC-S3"));

    AiPacketSender.Fit underSize = AiPacketSender.fit(series, images, 1000, 900);
    assertTrue(
        images.entrySet().stream()
                .filter(e -> !underSize.dropped().contains(e.getKey()))
                .mapToLong(Map.Entry::getValue)
                .sum()
            <= 900);
    assertTrue(AiPacketSender.fit(series, images, 1000, Long.MAX_VALUE).dropped().isEmpty());
  }

  @Test
  void claudeGetsBase64ImagesAndAForcedToolWithTheAnswerSchema() throws Exception {
    Path packet = packet();
    ObjectNode request = (ObjectNode) JSON.readTree(packet.resolve("request.json").toFile());
    AiPacketSender.Fit fit = new AiPacketSender.Fit(Set.of("S1-03"), List.of("S1: test note"));

    JsonNode body = AiPacketSender.claudeBody(request, packet, "claude-test", fit);

    assertEquals("claude-test", body.path("model").asText());
    assertTrue(body.path("system").asText().startsWith(AiLumbarRequest.INSTRUCTIONS));
    assertTrue(body.path("system").asText().endsWith("calling the record_findings tool once."));
    assertEquals("auto", body.at("/tool_choice/type").asText());
    assertEquals(AiPacketSender.TOOL_NAME, body.at("/tools/0/name").asText());
    assertEquals(request.at("/text/format/schema"), body.at("/tools/0/input_schema"));
    JsonNode content = body.at("/messages/0/content");
    assertTrue(
        content
            .get(0)
            .path("text")
            .asText()
            .endsWith("Not sent, to fit the request limits: S1: test note."));
    List<String> texts = new ArrayList<>();
    int images = 0;
    for (JsonNode part : content) {
      if ("image".equals(part.path("type").asText())) {
        images++;
        assertEquals("base64", part.at("/source/type").asText());
        assertFalse(part.at("/source/data").asText().isEmpty());
      } else {
        texts.add(part.path("text").asText());
      }
    }
    assertEquals(6, images);
    assertTrue(texts.stream().noneMatch(t -> t.startsWith("[S1-03]")));
  }

  @Test
  void openAiGetsDataUrlsAndIsNotStored() throws Exception {
    Path packet = packet();
    ObjectNode request = (ObjectNode) JSON.readTree(packet.resolve("request.json").toFile());

    JsonNode body =
        AiPacketSender.openAiBody(
            request, packet, "gpt-test", new AiPacketSender.Fit(Set.of(), List.of()));

    assertEquals("gpt-test", body.path("model").asText());
    assertFalse(body.path("store").asBoolean(true));
    assertTrue(
        body.at("/input/0/content/2/image_url").asText().startsWith("data:image/png;base64,"));
    assertEquals(request.at("/text/format"), body.at("/text/format"));
  }

  @Test
  void sendsToClaudeAndWritesTheAnswerInTheComposersWording() throws Exception {
    Path packet = packet();
    AtomicReference<String> key = new AtomicReference<>();
    String reply =
        "{\"content\": [{\"type\": \"tool_use\", \"name\": \"record_findings\", \"input\": "
            + ANSWER
            + "}], \"stop_reason\": \"tool_use\", \"usage\": {\"input_tokens\": 1200, \"output_tokens\": 300}}";

    AiPacketSender.Sent sent =
        withServer(
            200,
            reply,
            key,
            "x-api-key",
            uri ->
                AiPacketSender.send(
                    packet, AiProvider.ANTHROPIC, "claude-test", "test-key", uri, CLOCK));

    assertEquals("test-key", key.get());
    assertEquals(7, sent.imagesSent());
    String text = Files.readString(packet.resolve("answer-anthropic-20261005-130000.txt"));
    assertEquals(text, sent.answerText());
    assertTrue(
        text.startsWith(
            "Claude · claude-test · 2026-10-05 13:00\nImages sent: 7 of 7\nTokens: 1,200 in, 300 out"),
        text);
    assertTrue(
        text.contains(
            "FINDINGS\nStraightening of the lumbar lordosis.\nLumbar spondylosis.\nL4-5: Disc bulge. 1 mm bulge.\n"),
        text);
    assertTrue(
        text.contains(
            "ADDITIONAL FINDINGS\nTransitional anatomy with partial lumbarization of S1.\n"),
        text);
    assertTrue(text.contains("LIMITATIONS\nLimited exam due to motion.\n"), text);
    assertTrue(text.contains("IMAGES CITED\nL4-5: S2-02\n"), text);
    assertTrue(Files.isRegularFile(packet.resolve("response-anthropic-20261005-130000.json")));
    assertTrue(Files.isRegularFile(packet.resolve("answer-anthropic-20261005-130000.json")));
  }

  @Test
  void sendsToOpenAiAndReadsTheStructuredText() throws Exception {
    Path packet = packet();
    AtomicReference<String> key = new AtomicReference<>();
    String reply =
        JSON.writeValueAsString(
            Map.of(
                "status",
                "completed",
                "output",
                List.of(
                    Map.of(
                        "type",
                        "message",
                        "content",
                        List.of(Map.of("type", "output_text", "text", ANSWER))))));

    AiPacketSender.Sent sent =
        withServer(
            200,
            reply,
            key,
            "Authorization",
            uri ->
                AiPacketSender.send(packet, AiProvider.OPENAI, "gpt-test", "test-key", uri, CLOCK));

    assertEquals("Bearer test-key", key.get());
    assertTrue(sent.answerText().contains("L4-5: Disc bulge. 1 mm bulge."), sent.answerText());
  }

  @Test
  void providerErrorsKeepTheResponseAndExplainTheFailure() throws Exception {
    Path packet = packet();

    IOException error =
        assertThrows(
            IOException.class,
            () ->
                withServer(
                    401,
                    "{\"error\": {\"message\": \"invalid x-api-key\"}}",
                    new AtomicReference<>(),
                    "x-api-key",
                    uri ->
                        AiPacketSender.send(packet, AiProvider.ANTHROPIC, "m", "bad", uri, CLOCK)));

    assertEquals("Claude answered HTTP 401: invalid x-api-key", error.getMessage());
    assertTrue(Files.isRegularFile(packet.resolve("response-anthropic-20261005-130000.json")));
  }

  @Test
  void claudeAnswersInTextAreReadOrExplained() throws Exception {
    JsonNode jsonText =
        JSON.readTree(
            "{\"content\": [{\"type\": \"text\", \"text\": \" {\\\"levels\\\": []} \"}]}");
    assertTrue(AiPacketSender.claudeAnswer(jsonText).path("levels").isArray());

    IOException prose =
        assertThrows(
            IOException.class,
            () ->
                AiPacketSender.claudeAnswer(
                    JSON.readTree(
                        "{\"content\": [{\"type\": \"text\", \"text\": \"I cannot see axials.\"}]}")));
    assertEquals(
        "Claude answered without the structured findings: I cannot see axials.",
        prose.getMessage());
  }

  @Test
  void unreachableProvidersSayToCheckTheConnection() throws Exception {
    Path packet = packet();
    // A port that was just closed refuses the connection, as when offline.
    URI uri;
    try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
      uri = URI.create("http://127.0.0.1:" + socket.getLocalPort() + "/");
    }

    IOException error =
        assertThrows(
            IOException.class,
            () -> AiPacketSender.send(packet, AiProvider.OPENAI, "m", "key", uri, CLOCK));

    assertEquals("Could not reach OpenAI. Check the internet connection.", error.getMessage());
  }

  @Test
  void genericAnswersListFindingsWithTheirImages() throws Exception {
    JsonNode answer =
        JSON.readTree(
            """
            {"findings": [{"location": "L4-5", "finding": "Disc bulge", "severity": "mild",
                           "image_ids": ["S1-04", "S2-10"], "confidence": "high"}],
             "limitations": ["Motion"]}
            """);

    assertEquals(
        "FINDINGS\nL4-5: Disc bulge (mild, high confidence; S1-04, S2-10)\n\nLIMITATIONS\nMotion\n",
        AiPacketSender.answerText(answer, false));
  }

  private Path packet() throws IOException {
    AiPacketPlan plan =
        AiPacketPlan.build(
            AiPacketPlanTest.STUDY,
            List.of(
                AiPacketPlanTest.series("601", "T2 SAG", AiPacketPlanTest.sagittals(-2, 0, 2)),
                AiPacketPlanTest.series("801", "T2 AX", AiPacketPlanTest.axials(30, 20, 10))),
            Optional.empty());
    return AiPacketExporter.export(plan, root, CLOCK).directory();
  }

  private static void addSeries(Map<String, Long> images, String id, int count) {
    for (int index = 1; index <= count; index++) {
      images.put(String.format("%s-%02d", id, index), 10L);
    }
  }

  private interface Call<T> {
    T run(URI uri) throws Exception;
  }

  private static <T> T withServer(
      int status, String body, AtomicReference<String> key, String keyHeader, Call<T> call)
      throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          key.set(exchange.getRequestHeaders().getFirst(keyHeader));
          exchange.getRequestBody().readAllBytes();
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(status, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    try {
      return call.run(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"));
    } finally {
      server.stop(0);
    }
  }
}
