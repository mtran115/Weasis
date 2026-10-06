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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Where an AI packet can be sent, and where that provider's API key is looked up. */
enum AiProvider {
  OPENAI(
      "OpenAI",
      "gpt-6-astra",
      URI.create("https://api.openai.com/v1/responses"),
      "OPENAI_API_KEY",
      "weasis-ai-openai",
      1500,
      400L * 1024 * 1024),
  ANTHROPIC(
      "Claude",
      "claude-opus-5-5",
      URI.create("https://api.anthropic.com/v1/messages"),
      "ANTHROPIC_API_KEY",
      "weasis-ai-anthropic",
      100,
      30L * 1024 * 1024);

  private static final Path SECURITY = Path.of("/usr/bin/security");

  private final String label;
  private final String defaultModel;
  private final URI endpoint;
  private final String environmentVariable;
  private final String keychainService;
  private final int maxImages;
  private final long maxImageBytes;

  AiProvider(
      String label,
      String defaultModel,
      URI endpoint,
      String environmentVariable,
      String keychainService,
      int maxImages,
      long maxImageBytes) {
    this.label = label;
    this.defaultModel = defaultModel;
    this.endpoint = endpoint;
    this.environmentVariable = environmentVariable;
    this.keychainService = keychainService;
    this.maxImages = maxImages;
    this.maxImageBytes = maxImageBytes;
  }

  String label() {
    return label;
  }

  String defaultModel() {
    return defaultModel;
  }

  URI endpoint() {
    return endpoint;
  }

  int maxImages() {
    return maxImages;
  }

  /** Base64 image data allowed in one request, kept under the provider's request size limit. */
  long maxImageBytes() {
    return maxImageBytes;
  }

  String fileSuffix() {
    return name().toLowerCase(Locale.ROOT);
  }

  /**
   * The Terminal command that asks for the key and stores it in the login Keychain. It asks through
   * the shell because the security tool's own prompt keeps only 128 characters, shorter than OpenAI
   * keys, and it asks after the command runs so copying the command cannot replace the key.
   */
  String keychainCommand() {
    return "read -rs \"KEY?Paste your "
        + label
        + " key, then press Return: \" && security add-generic-password -U -a \"$USER\" -s "
        + keychainService
        + " -w \"$KEY\"; unset KEY";
  }

  /** From the environment, else the macOS login Keychain; the key is never logged or stored. */
  Optional<String> apiKey() {
    String fromEnvironment = System.getenv(environmentVariable);
    if (fromEnvironment != null && !fromEnvironment.isBlank()) {
      return Optional.of(fromEnvironment.strip());
    }
    return keychainKey();
  }

  private Optional<String> keychainKey() {
    if (!Files.isExecutable(SECURITY)) {
      return Optional.empty();
    }
    try {
      Process process =
          new ProcessBuilder(
                  SECURITY.toString(), "find-generic-password", "-s", keychainService, "-w")
              .redirectError(ProcessBuilder.Redirect.DISCARD)
              .start();
      String key = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      if (!process.waitFor(10, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        return Optional.empty();
      }
      return process.exitValue() == 0 && !key.isBlank()
          ? Optional.of(key.strip())
          : Optional.empty();
    } catch (IOException e) {
      return Optional.empty();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    }
  }
}
