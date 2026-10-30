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
package org.sonarsource.sonarqube.mcp.its;

import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarqube.mcp.its.HostedAuthenticationSupport.assertProtectedReads;
import static org.sonarsource.sonarqube.mcp.its.HostedAuthenticationSupport.headers;
import static org.sonarsource.sonarqube.mcp.its.HostedAuthenticationSupport.ping;
import static org.sonarsource.sonarqube.mcp.its.HostedAuthenticationSupport.requiredEnvironment;

@Timeout(120)
class HostedTokenAuthenticationITest {
  private static String token;
  private static String organization;
  private static String projectKey;

  @BeforeAll
  static void requireFixture() {
    token = requiredEnvironment(System.getenv(), "SONARCLOUD_IT_TOKEN");
    organization = requiredEnvironment(System.getenv(), "SONARCLOUD_IT_ORGANIZATION");
    projectKey = requiredEnvironment(System.getenv(), "SONARCLOUD_IT_PRIVATE_PROJECT_KEY");
  }

  @ParameterizedTest
  @ValueSource(strings = {"Authorization", "SONARQUBE_TOKEN"})
  void it_should_preserve_legacy_token_access_to_protected_cloud_data(String header) {
    assertProtectedReads(headers(header, token, organization), organization, projectKey, false);
  }

  @ParameterizedTest
  @ValueSource(strings = {"Authorization", "SONARQUBE_TOKEN"})
  void it_should_reject_an_invalid_legacy_token(String header) {
    var invalidToken = "squ_0000000000000000000000000000000000000000";
    var response = ping(headers(header, invalidToken, organization));
    assertThat(response.statusCode()).as("Invalid legacy token status").isIn(401, 403);
  }

  @Test
  void it_should_require_credentials() {
    assertThat(ping(Map.of()).statusCode()).as("Missing credentials status").isEqualTo(401);
  }
}
