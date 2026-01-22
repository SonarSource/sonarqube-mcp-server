/*
 * SonarQube MCP Server
 * Copyright (C) 2025 SonarSource
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
package org.sonarsource.sonarqube.mcp.its;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

@Testcontainers
class StdioTransportITest {

  @Container
  private static final GenericContainer<?> stdioServerContainer = createStdioServerContainer();

  @Test
  void should_start_server_with_stdio_transport() {
    assertThat(stdioServerContainer.isRunning()).isTrue();

    assertThat(stdioServerContainer.getLogs()).contains("Transport: stdio", "SonarQube MCP Server Started:");
  }

  @Test
  void should_start_server_and_download_analyzers() {
    assertThat(stdioServerContainer.isRunning()).isTrue();

    await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(stdioServerContainer.getLogs())
      .contains("Transport: stdio", "SonarQube MCP Server Started:")
      .contains("Found 12 plugins")
      .contains("Backend restarted with new analyzers"));
  }

  private static GenericContainer<?> createStdioServerContainer() {
    var jarPath = System.getProperty("sonarqube.mcp.jar.path");
    if (jarPath == null || jarPath.isEmpty()) {
      throw new IllegalStateException("sonarqube.mcp.jar.path system property must be set");
    }

    var sonarqubeToken = System.getenv("SONARCLOUD_IT_TOKEN");
    if (sonarqubeToken == null || sonarqubeToken.isEmpty()) {
      throw new IllegalStateException("SONARCLOUD_IT_TOKEN must be set");
    }

    var container = new GenericContainer<>("eclipse-temurin:21-jre-alpine")
      .withCopyFileToContainer(MountableFile.forHostPath(jarPath), "/app/server.jar")
      .withCopyFileToContainer(
        MountableFile.forClasspathResource("empty-external-tool-providers-its.json"),
        "/app/external-tool-providers.json"
      )
      .withEnv("STORAGE_PATH", "/app/storage")
      .withEnv("SONARQUBE_TOKEN", sonarqubeToken)
      .withEnv("SONARQUBE_ORG", "sonarlint-it")
      .withEnv("SONARQUBE_CLOUD_URL", "https://sc-staging.io")
      .withCommand("sh", "-c",
        "apk add --no-cache git nodejs npm && " +
          "mkdir -p /app/storage && " +
          "tail -f /dev/null | java -Dexternal.tools.config.path=/app/external-tool-providers.json -jar /app/server.jar"
      )
      .withStartupTimeout(Duration.ofMinutes(4))
      .waitingFor(Wait.forLogMessage(".*SonarQube MCP Server Started.*", 1).withStartupTimeout(Duration.ofMinutes(4)));

    container.withLogConsumer(outputFrame -> System.out.print("[STDIO-Container] " + outputFrame.getUtf8String()));
    return container;
  }

}
