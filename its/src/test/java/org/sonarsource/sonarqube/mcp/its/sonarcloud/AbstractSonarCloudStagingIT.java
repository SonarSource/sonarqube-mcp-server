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
package org.sonarsource.sonarqube.mcp.its.sonarcloud;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.sonarsource.sonarqube.mcp.its.sonarcloud.harness.SonarCloudAnalyzedProject;
import org.sonarsource.sonarqube.mcp.its.sonarcloud.harness.SonarCloudStagingFixture;
import org.sonarsource.sonarqube.mcp.its.sonarcloud.harness.SonarCloudStagingHarness;
import org.sonarsource.sonarqube.mcp.its.sonarcloud.harness.SonarQubeMcpTestClient;

/**
 * Shared {@link TestInstance.Lifecycle#PER_CLASS} lifecycle for SonarQube Cloud staging tool tests.
 *
 * <p>Only wires the analyzed project fixture and in-process MCP client; helpers live on
 * {@link SonarCloudStagingFixture} and {@link SonarQubeMcpTestClient}. An alternative (as in
 * sonarlint-core) is a JUnit {@code ParameterResolver} that injects the harness into each test
 * method — slightly more explicit, but more verbose across many one-test classes.
 */
@Tag("SonarCloud")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractSonarCloudStagingIT {

  protected SonarCloudStagingFixture fixture;
  protected SonarQubeMcpTestClient mcpClient;
  private SonarCloudStagingHarness harness;

  @BeforeAll
  void setUpStaging() {
    fixture = SonarCloudAnalyzedProject.getOrInitialize();
    harness = new SonarCloudStagingHarness();
    mcpClient = harness.newStagingClient();
  }

  @AfterAll
  void tearDownStaging() {
    if (harness != null) {
      harness.close();
      harness = null;
    }
    fixture = null;
  }

}
