/*
 * SonarQube MCP Server
 * Copyright (C) SonarSource
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource Sàrl.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonarsource.sonarqube.mcp.its.sonarcloud.harness;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.apache.commons.lang3.SystemUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public final class SonarCloudStagingFixture {

  private static final Pattern CE_TASK_ID = Pattern.compile("api/ce/task\\?id=([A-Za-z0-9_-]+)");

  private final String token;
  private final String logicalName;
  private final String projectKey;
  private final HttpClient httpClient;

  private SonarCloudStagingFixture(String logicalName, int randomSuffix) {
    this.token = SonarCloudStagingEnvironment.requireToken();
    this.logicalName = logicalName;
    this.projectKey = SonarCloudStagingEnvironment.PROJECT_KEY_PREFIX + logicalName + "-" + randomSuffix;
    this.httpClient = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(30))
      .build();
  }

  public static SonarCloudStagingFixture create(String logicalName) {
    var suffix = new Random().nextInt() & Integer.MAX_VALUE;
    return new SonarCloudStagingFixture(logicalName, suffix);
  }

  /**
   * Creates, provisions, and analyzes the shared sample project (see sonarlint-core {@code SonarCloudTests}).
   */
  private static final String HOTSPOT_QUALITY_PROFILE = "SonarLint IT Java Hotspot";
  private static final String HOTSPOT_QUALITY_PROFILE_BACKUP = "java-sonarlint-with-hotspot.xml";

  public static SonarCloudStagingFixture createSharedAnalyzed() {
    var fixture = create(SonarCloudAnalyzedProject.SAMPLE_PROJECT_RESOURCE);
    fixture.restoreQualityProfile(HOTSPOT_QUALITY_PROFILE_BACKUP);
    fixture.provisionProject();
    fixture.associateProjectToQualityProfile("java", HOTSPOT_QUALITY_PROFILE);
    fixture.analyzeMavenProject(sampleProjectDir());
    fixture.awaitOpenIssue("java:S1118");
    return fixture;
  }

  public String projectKey() {
    return projectKey;
  }

  public String projectName() {
    if (SonarCloudAnalyzedProject.SAMPLE_PROJECT_RESOURCE.equals(logicalName)) {
      return "MCP SonarCloud IT sample (Java hotspot)";
    }
    return "MCP SonarCloud IT " + logicalName;
  }

  public String mainFileKey() {
    return projectKey + ":" + SonarCloudAnalyzedProject.MAIN_FILE_PATH;
  }

  public static Path sampleProjectDir() {
    var fromModuleDir = Path.of("projects", SonarCloudAnalyzedProject.SAMPLE_PROJECT_RESOURCE);
    if (fromModuleDir.resolve("pom.xml").toFile().isFile()) {
      return fromModuleDir.toAbsolutePath().normalize();
    }
    var fromRepoRoot = Path.of("its/projects", SonarCloudAnalyzedProject.SAMPLE_PROJECT_RESOURCE);
    if (fromRepoRoot.resolve("pom.xml").toFile().isFile()) {
      return fromRepoRoot.toAbsolutePath().normalize();
    }
    throw new IllegalStateException("Cannot locate " + SonarCloudAnalyzedProject.SAMPLE_PROJECT_RESOURCE + " Maven project");
  }

  public void restoreQualityProfile(String backupResourceName) {
    var backupFile = resolveTestResource(backupResourceName);
    postMultipart(
      "/api/qualityprofiles/restore?organization=" + encode(SonarCloudStagingEnvironment.SONARQUBE_ORG),
      "backup",
      backupFile);
  }

  public void associateProjectToQualityProfile(String language, String profileName) {
    postForm(
      "/api/qualityprofiles/add_project",
      "language", language,
      "project", projectKey,
      "qualityProfile", profileName,
      "organization", SonarCloudStagingEnvironment.SONARQUBE_ORG);
  }

  public void provisionProject() {
    postForm(
      "/api/projects/create",
      "project", projectKey,
      "name", projectName(),
      "organization", SonarCloudStagingEnvironment.SONARQUBE_ORG);
  }

  /**
   * Runs {@code mvn clean package sonar:sonar} against SonarQube Cloud staging, same properties as sonarlint-core ITs.
   */
  public void analyzeMavenProject(Path projectDir) {
    if (!projectDir.resolve("pom.xml").toFile().isFile()) {
      throw new IllegalStateException("Maven project not found at " + projectDir);
    }
    var builder = buildMavenCommand(projectDir,
      "clean", "package", "sonar:sonar",
      "-Dsonar.projectKey=" + projectKey,
      "-Dsonar.projectName=" + projectName(),
      "-Dsonar.host.url=" + SonarCloudStagingEnvironment.SONARQUBE_URL,
      "-Dsonar.organization=" + SonarCloudStagingEnvironment.SONARQUBE_ORG,
      "-Dsonar.scm.disabled=true",
      "-Dsonar.branch.autoconfig.disabled=true");
    builder.environment().put("SONAR_TOKEN", token);
    var mavenOutput = runProcess(builder, projectDir);
    var ceTaskId = extractCeTaskId(mavenOutput);
    if (ceTaskId != null) {
      awaitCeTaskSuccess(ceTaskId);
    } else {
      awaitAnalysisQueueEmpty();
    }
  }

  public void cleanup() {
    postForm(
      "/api/projects/bulk_delete",
      "projects", projectKey,
      "organization", SonarCloudStagingEnvironment.SONARQUBE_ORG);
  }

  /** Waits until at least one OPEN issue for {@code rule} exists on this project (post-analysis indexing). */
  public void awaitOpenIssue(String rule) {
    await().atMost(3, TimeUnit.MINUTES)
      .pollInterval(3, TimeUnit.SECONDS)
      .ignoreExceptions()
      .until(() -> hasOpenIssue(rule));
  }

  public void deleteProjectWebhook(String webhookKey) {
    try {
      postForm(
        "/api/webhooks/delete",
        "webhook", webhookKey,
        "project", projectKey,
        "organization", SonarCloudStagingEnvironment.SONARQUBE_ORG);
    } catch (IllegalStateException e) {
      // Best-effort cleanup; do not fail the test if staging rejects delete
      System.err.println("Webhook cleanup failed for " + webhookKey + ": " + e.getMessage());
    }
  }

  private boolean hasOpenIssue(String rule) throws IOException, InterruptedException {
    var path = "/api/issues/search?organization=" + encode(SonarCloudStagingEnvironment.SONARQUBE_ORG)
      + "&projects=" + encode(projectKey)
      + "&rules=" + encode(rule)
      + "&issueStatuses=OPEN"
      + "&ps=1";
    var request = HttpRequest.newBuilder()
      .uri(URI.create(SonarCloudStagingEnvironment.SONARQUBE_URL + path))
      .timeout(Duration.ofSeconds(30))
      .header("Authorization", "Bearer " + token)
      .GET()
      .build();
    var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    return response.statusCode() == 200
      && response.body().contains("\"issues\":[")
      && !response.body().contains("\"issues\":[]");
  }

  private void awaitCeTaskSuccess(String taskId) {
    await().atMost(10, TimeUnit.MINUTES)
      .pollInterval(2, TimeUnit.SECONDS)
      .until(() -> {
        try {
          var body = fetchCeTaskBody(taskId);
          if (body.contains("\"status\":\"FAILED\"") || body.contains("\"status\":\"CANCELED\"")) {
            throw new IllegalStateException("CE task " + taskId + " did not succeed: " + body);
          }
          return body.contains("\"status\":\"SUCCESS\"");
        } catch (IOException | InterruptedException e) {
          if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
          }
          return false;
        }
      });
  }

  private String fetchCeTaskBody(String taskId) throws IOException, InterruptedException {
    var path = "/api/ce/task?id=" + encode(taskId)
      + "&organization=" + encode(SonarCloudStagingEnvironment.SONARQUBE_ORG);
    var request = HttpRequest.newBuilder()
      .uri(URI.create(SonarCloudStagingEnvironment.SONARQUBE_URL + path))
      .timeout(Duration.ofSeconds(30))
      .header("Authorization", "Bearer " + token)
      .GET()
      .build();
    var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      return "";
    }
    return response.body();
  }

  private void awaitAnalysisQueueEmpty() {
    await().atMost(3, TimeUnit.MINUTES)
      .pollInterval(2, TimeUnit.SECONDS)
      .ignoreExceptions()
      .until(this::isAnalysisQueueEmpty);
  }

  private boolean isAnalysisQueueEmpty() throws IOException, InterruptedException {
    var request = HttpRequest.newBuilder()
      .uri(URI.create(SonarCloudStagingEnvironment.SONARQUBE_URL + "/api/analysis_reports/is_queue_empty"))
      .timeout(Duration.ofSeconds(30))
      .header("Authorization", "Bearer " + token)
      .GET()
      .build();
    var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    return response.statusCode() == 200 && "true".equals(response.body());
  }

  private static String extractCeTaskId(String mavenOutput) {
    var matcher = CE_TASK_ID.matcher(mavenOutput);
    return matcher.find() ? matcher.group(1) : null;
  }

  private static ProcessBuilder buildMavenCommand(Path projectDir, String... args) {
    ProcessBuilder builder;
    if (SystemUtils.IS_OS_WINDOWS) {
      builder = new ProcessBuilder();
      builder.command("cmd.exe", "/c", "mvn");
    } else {
      builder = new ProcessBuilder("mvn");
    }
    var command = builder.command();
    command.add("--batch-mode");
    command.add("--show-version");
    command.add("--errors");
    command.addAll(java.util.List.of(args));
    builder.directory(projectDir.toFile());
    builder.redirectErrorStream(true);
    return builder;
  }

  private static String runProcess(ProcessBuilder builder, Path projectDir) {
    try {
      var process = builder.start();
      var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      var exitCode = process.waitFor();
      assertThat(exitCode)
        .withFailMessage("Maven failed in %s with exit %s:%n%s", projectDir, exitCode, output)
        .isZero();
      return output;
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Maven analysis failed in " + projectDir, e);
    }
  }

  private static File resolveTestResource(String resourceName) {
    var fromModule = Path.of("src/test/resources", resourceName);
    if (fromModule.toFile().isFile()) {
      return fromModule.toAbsolutePath().toFile();
    }
    var fromRepo = Path.of("its/src/test/resources", resourceName);
    if (fromRepo.toFile().isFile()) {
      return fromRepo.toAbsolutePath().toFile();
    }
    throw new IllegalStateException("Test resource not found: " + resourceName);
  }

  private void postMultipart(String path, String partName, File file) {
    var boundary = "----SonarCloudStagingFixture" + System.currentTimeMillis();
    var body = buildMultipartBody(boundary, partName, file);
    var request = HttpRequest.newBuilder()
      .uri(URI.create(SonarCloudStagingEnvironment.SONARQUBE_URL + path))
      .timeout(Duration.ofSeconds(120))
      .header("Authorization", "Bearer " + token)
      .header("Content-Type", "multipart/form-data; boundary=" + boundary)
      .POST(HttpRequest.BodyPublishers.ofByteArray(body))
      .build();
    sendRequest(request, path);
  }

  private static byte[] buildMultipartBody(String boundary, String partName, File file) {
    try {
      var fileBytes = Files.readAllBytes(file.toPath());
      var header = ("--" + boundary + "\r\n"
        + "Content-Disposition: form-data; name=\"" + partName + "\"; filename=\"" + file.getName() + "\"\r\n"
        + "Content-Type: application/xml\r\n\r\n").getBytes(StandardCharsets.UTF_8);
      var footer = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
      var body = new byte[header.length + fileBytes.length + footer.length];
      System.arraycopy(header, 0, body, 0, header.length);
      System.arraycopy(fileBytes, 0, body, header.length, fileBytes.length);
      System.arraycopy(footer, 0, body, header.length + fileBytes.length, footer.length);
      return body;
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read " + file, e);
    }
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private void postForm(String path, String... params) {
    var body = encodeForm(params);
    var request = HttpRequest.newBuilder()
      .uri(URI.create(SonarCloudStagingEnvironment.SONARQUBE_URL + path))
      .timeout(Duration.ofSeconds(60))
      .header("Authorization", "Bearer " + token)
      .header("Content-Type", "application/x-www-form-urlencoded")
      .POST(HttpRequest.BodyPublishers.ofString(body))
      .build();
    sendRequest(request, path);
  }

  private void sendRequest(HttpRequest request, String pathForError) {
    try {
      var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        throw new IllegalStateException(
          "SonarCloud API " + pathForError + " failed with HTTP " + response.statusCode() + ": " + response.body());
      }
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("SonarCloud API " + pathForError + " call failed", e);
    }
  }

  private static String encodeForm(String... params) {
    if (params.length % 2 != 0) {
      throw new IllegalArgumentException("Expected key/value pairs");
    }
    var encoded = new StringBuilder();
    for (var i = 0; i < params.length; i += 2) {
      if (i > 0) {
        encoded.append('&');
      }
      encoded.append(URLEncoder.encode(params[i], StandardCharsets.UTF_8));
      encoded.append('=');
      encoded.append(URLEncoder.encode(params[i + 1], StandardCharsets.UTF_8));
    }
    return encoded.toString();
  }
}
