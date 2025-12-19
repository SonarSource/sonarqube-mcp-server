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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for external tool provider binary validation.
 * <p>
 * These tests validate that the external provider binary:
 * - Can be deployed to a container
 * - Has correct permissions
 * - Can execute in the target environment (Alpine Linux)
 * <p>
 * Note: Currently disabled due to container startup issues.
 * Binary deployment is validated within HttpTransportITest.
 */
@Disabled("Container startup issues - binary deployment is tested in HttpTransportITest")
@Testcontainers
class ExternalToolProviderITest {

  @org.testcontainers.junit.jupiter.Container
  private static final GenericContainer<?> externalProviderContainer =
    new GenericContainer<>("alpine/git:latest") // Using alpine/git as a base for musl libc
      .withCopyToContainer(
        MountableFile.forClasspathResource("binaries/sonar-code-context-mcp"),
        "/app/sonar-code-context-mcp"
      )
      .withCommand("tail", "-f", "/dev/null") // Keep container running
      .withStartupTimeout(Duration.ofSeconds(120));

  @Test
  void should_binary_be_present_and_executable_in_container() throws Exception {
    org.testcontainers.containers.Container.ExecResult lsResult = externalProviderContainer.execInContainer("ls", "-l", "/app");
    assertThat(lsResult.getStdout()).contains("sonar-code-context-mcp");
    assertThat(lsResult.getStdout()).containsPattern("-rwxr-xr-x.*sonar-code-context-mcp"); // Check execute permissions
    assertThat(lsResult.getExitCode()).isZero();
  }

  @Test
  void should_binary_start_without_immediate_crash_in_container() throws Exception {
    org.testcontainers.containers.Container.ExecResult execResult = externalProviderContainer.execInContainer("/app/sonar-code-context-mcp", "--version");
    assertThat(execResult.getExitCode()).isZero();
    assertThat(execResult.getStdout()).contains("sonar-code-context-mcp");
  }
}
