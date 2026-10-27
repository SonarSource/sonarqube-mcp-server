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

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarqube.mcp.its.HostedAuthenticationSupport.alterSignature;
import static org.sonarsource.sonarqube.mcp.its.HostedAuthenticationSupport.assertDiscovery;
import static org.sonarsource.sonarqube.mcp.its.HostedAuthenticationSupport.assertProtectedReads;
import static org.sonarsource.sonarqube.mcp.its.HostedAuthenticationSupport.get;
import static org.sonarsource.sonarqube.mcp.its.HostedAuthenticationSupport.headers;
import static org.sonarsource.sonarqube.mcp.its.HostedAuthenticationSupport.ping;
import static org.sonarsource.sonarqube.mcp.its.HostedAuthenticationSupport.requiredEnvironment;
import static org.sonarsource.sonarqube.mcp.its.HostedAuthenticationSupport.validateOAuthToken;

@Timeout(120)
class HostedOAuthAuthenticationITest {
  private static String token;
  private static String organization;
  private static String projectKey;

  @BeforeAll
  static void requireFixture() {
    token = requiredEnvironment(System.getenv(), "SONARCLOUD_IT_OAUTH_TOKEN");
    validateOAuthToken(token, Instant.now());
    organization = requiredEnvironment(System.getenv(), "SONARCLOUD_IT_ORGANIZATION");
    projectKey = requiredEnvironment(System.getenv(), "SONARCLOUD_IT_PRIVATE_PROJECT_KEY");
  }

  @Test
  void it_should_advertise_staging_oauth_discovery() {
    assertDiscovery();
  }

  @Test
  void it_should_use_an_mcp_audience_token_to_read_protected_cloud_data() {
    validateOAuthToken(token, Instant.now());
    var direct = get(HostedAuthenticationSupport.ORGANIZATIONS, Map.of("Authorization", "Bearer " + token));
    assertThat(direct.statusCode()).as("Cloud must reject Token A's MCP audience").isIn(401, 403);

    assertProtectedReads(headers("Authorization", token, null), organization, projectKey, true);
  }

  @Test
  void it_should_reject_an_altered_oauth_signature() {
    var response = ping(headers("Authorization", alterSignature(token), null));
    assertThat(response.statusCode()).as("Tampered Token A status").isEqualTo(401);
  }
}
