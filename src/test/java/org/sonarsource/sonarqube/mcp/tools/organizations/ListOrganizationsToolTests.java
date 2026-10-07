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
package org.sonarsource.sonarqube.mcp.tools.organizations;

import com.github.tomakehurst.wiremock.http.Body;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Nested;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.OrganizationsApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertResultEquals;

class ListOrganizationsToolTests {

  private static final String LIST_PATH = OrganizationsApi.ORGANIZATIONS_PATH + "?excludeEligibility=true";

  @SonarQubeMcpServerTest
  void it_should_validate_annotations(SonarQubeMcpServerTestHarness harness) {
    var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

    var tool = mcpClient.listTools().stream().filter(t -> t.name().equals(ListOrganizationsTool.TOOL_NAME)).findFirst().orElseThrow();

    assertThat(tool.annotations()).isNotNull();
    assertThat(tool.annotations().readOnlyHint()).isTrue();
    assertThat(tool.annotations().openWorldHint()).isTrue();
    assertThat(tool.annotations().idempotentHint()).isFalse();
    assertThat(tool.annotations().destructiveHint()).isFalse();
    assertThat(tool.outputSchema()).isNull();
  }

  @Nested
  class WithSonarQubeServer {

    @SonarQubeMcpServerTest
    void it_should_not_be_available_for_sonarqube_server(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient();

      var exception = assertThrows(McpError.class, () -> mcpClient.callTool(ListOrganizationsTool.TOOL_NAME));

      assertThat(exception.getMessage()).isEqualTo("Unknown tool: invalid_tool_name");
    }
  }

  @Nested
  class WithSonarQubeCloud {

    @SonarQubeMcpServerTest
    void it_should_return_organizations(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(LIST_PATH).willReturn(aResponse().withResponseBody(Body.fromJsonBytes("""
        [
          {"id": "AXyz", "key": "my-org", "name": "My Org", "uuidV4": "4f0a1d2e-0000-4000-8000-000000000001"},
          {"id": "AXab", "key": "other-org", "name": "Other Org", "uuidV4": "4f0a1d2e-0000-4000-8000-000000000002"}
        ]""".getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(ListOrganizationsTool.TOOL_NAME);

      assertResultEquals(result, """
        {
          "organizations" : [ {
            "key" : "my-org",
            "name" : "My Org"
          }, {
            "key" : "other-org",
            "name" : "Other Org"
          } ]
        }""");
    }

    @SonarQubeMcpServerTest
    void it_should_return_empty_list_when_no_organizations(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(LIST_PATH).willReturn(aResponse().withResponseBody(Body.fromJsonBytes("[]".getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(ListOrganizationsTool.TOOL_NAME);

      assertResultEquals(result, """
        {
          "organizations" : [ ]
        }""");
    }

    @SonarQubeMcpServerTest
    void it_should_return_an_error_when_forbidden(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(LIST_PATH).willReturn(aResponse().withStatus(HttpStatus.SC_FORBIDDEN)));
      var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(ListOrganizationsTool.TOOL_NAME);

      assertThat(result.isError()).isTrue();
      assertThat(((McpSchema.TextContent) result.content().getFirst()).text()).contains("Forbidden");
    }

    @SonarQubeMcpServerTest
    void it_should_return_an_error_when_server_fails(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(LIST_PATH).willReturn(aResponse().withStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR)));
      var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(ListOrganizationsTool.TOOL_NAME);

      assertThat(result.isError()).isTrue();
    }
  }

}
