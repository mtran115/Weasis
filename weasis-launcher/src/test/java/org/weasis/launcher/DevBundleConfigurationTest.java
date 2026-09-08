/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.pref.AppPreferences;
import org.weasis.pref.Preference;

class DevBundleConfigurationTest {
  private static final String VERSION = "4.7.1-SNAPSHOT";
  private static final Path LAUNCHER_DIR =
      Path.of(System.getProperty("basedir", ".")).toAbsolutePath().normalize();

  @Test
  void loadsWorktreeBundlesDespiteConflictingMavenArtifacts(@TempDir Path temporary)
      throws IOException {
    Path worktree = temporary.resolve("Report Composer");
    Path repository = temporary.resolve("Shared Maven Repository");
    AppPreferences preferences = configuration("base.json", worktree, repository);

    for (String artifact : List.of("weasis-core", "weasis-dicom-report-composer")) {
      boolean core = artifact.equals("weasis-core");
      Path localJar =
          worktree.resolve(
              (core ? "weasis-core" : "weasis-dicom/" + artifact)
                  + "/target/"
                  + artifact
                  + "-"
                  + VERSION
                  + ".jar");
      Path mavenJar =
          repository.resolve(
              "org/weasis/"
                  + (core ? "core/" : "dicom/")
                  + artifact
                  + "/"
                  + VERSION
                  + "/"
                  + localJar.getFileName());
      write(localJar, "composer worktree " + artifact);
      write(mavenJar, "other worktree " + artifact);

      URI bundle =
          resolve(
              preferences,
              bundleTemplates(preferences)
                  .filter(value -> value.endsWith("/" + artifact + "-${app.version}.jar"))
                  .findFirst()
                  .orElseThrow());
      assertEquals(localJar, Path.of(bundle).normalize());
      assertTrue(bundle.toString().contains("Report%20Composer"));
      assertEquals("composer worktree " + artifact, read(bundle));
    }

    Path gogoJar =
        repository.resolve(
            "org/apache/felix/org.apache.felix.gogo.runtime/1.1.6/"
                + "org.apache.felix.gogo.runtime-1.1.6.jar");
    write(gogoJar, "shared third-party bundle");
    URI gogo = resolve(preferences, preferences.getValue("felix.auto.start.1"));
    assertEquals(gogoJar, Path.of(gogo).normalize());
    assertEquals("shared third-party bundle", read(gogo));
  }

  @Test
  void allDevProfilesResolveProjectBundlesToTheirModuleTargets(@TempDir Path temporary)
      throws Exception {
    Path worktree = temporary.resolve("Report Composer");
    for (String profile : List.of("base.json", "dicomizer.json", "non-dicom-explorer.json")) {
      AppPreferences preferences =
          configuration(profile, worktree, temporary.resolve("Shared Maven Repository"));
      List<String> projectBundles =
          bundleTemplates(preferences).filter(value -> value.contains("${app.version}")).toList();
      assertTrue(!projectBundles.isEmpty(), profile);
      for (String template : projectBundles) {
        Path jar = Path.of(resolve(preferences, template)).normalize();
        assertTrue(jar.startsWith(worktree), profile + ": " + template);
        assertEquals("target", jar.getParent().getFileName().toString(), template);
        Path module = worktree.relativize(jar.getParent().getParent());
        Path pom = LAUNCHER_DIR.getParent().resolve(module).resolve("pom.xml");
        assertTrue(Files.isRegularFile(pom), "Missing source module for " + template);
        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pom.toFile());
        String artifact =
            XPathFactory.newInstance().newXPath().evaluate("/project/artifactId", document);
        assertEquals(artifact + "-" + VERSION + ".jar", jar.getFileName().toString(), template);
      }
    }
  }

  private static AppPreferences configuration(String profile, Path worktree, Path repository)
      throws IOException {
    AppPreferences preferences = new AppPreferences();
    assertTrue(preferences.readJson(LAUNCHER_DIR.resolve("conf").resolve(profile).toUri()));
    Path codebase = Files.createDirectories(worktree.resolve("weasis-launcher/target"));
    String codebaseUrl = codebase.toFile().toURI().toString();
    put(preferences, "weasis.codebase.url", codebaseUrl.substring(0, codebaseUrl.length() - 1));
    put(preferences, "maven.localRepository", Utils.adaptPathToUri(repository.toString()));
    put(preferences, "app.version", VERSION);
    put(preferences, "felix.gogo.version", "1.1.6");
    return preferences;
  }

  private static Stream<String> bundleTemplates(AppPreferences preferences) {
    return preferences.values().stream()
        .filter(value -> value.getCode().startsWith("felix.auto."))
        .filter(value -> value.getValue() != null)
        .flatMap(value -> Arrays.stream(value.getValue().split("\\s+")));
  }

  private static void put(AppPreferences preferences, String key, String value) {
    Preference preference = new Preference(key, "A", null, "FELIX_CONFIG");
    preference.setValue(value);
    preferences.put(key, preference);
  }

  private static URI resolve(AppPreferences preferences, String template) {
    return URI.create(preferences.substVars(template, "bundle", null));
  }

  private static void write(Path file, String contents) throws IOException {
    Files.createDirectories(file.getParent());
    Files.writeString(file, contents);
  }

  private static String read(URI bundle) throws IOException {
    try (var input = FileUtil.getAdaptedConnection(bundle.toURL(), false).getInputStream()) {
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
