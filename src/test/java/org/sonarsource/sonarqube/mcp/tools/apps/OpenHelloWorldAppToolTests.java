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
package org.sonarsource.sonarqube.mcp.tools.apps;

import io.modelcontextprotocol.spec.McpSchema;
import org.sonarsource.sonarqube.mcp.apps.HelloWorldApp;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;

import static org.assertj.core.api.Assertions.assertThat;

class OpenHelloWorldAppToolTests {

  @SonarQubeMcpServerTest
  void it_should_advertise_the_app_template_on_the_tool_descriptor(SonarQubeMcpServerTestHarness harness) {
    var client = harness.newClient();

    var tool = client.listTools().stream()
      .filter(candidate -> candidate.name().equals(OpenHelloWorldAppTool.TOOL_NAME))
      .findFirst()
      .orElseThrow();

    assertThat(tool.meta())
      .containsEntry("openai/outputTemplate", HelloWorldApp.RESOURCE_URI)
      .containsEntry("openai/widgetAccessible", true);
  }

  @SonarQubeMcpServerTest
  void it_should_return_standard_tool_content_and_app_meta(SonarQubeMcpServerTestHarness harness) {
    var client = harness.newClient();

    var result = client.callTool(OpenHelloWorldAppTool.TOOL_NAME);

    assertThat(result.isError()).isFalse();
    assertThat(result.content()).hasSize(3);
    var embeddedResource = (McpSchema.EmbeddedResource) result.content().get(0);
    assertThat(embeddedResource.resource().uri()).isEqualTo(HelloWorldApp.RESOURCE_URI);
    assertThat(embeddedResource.resource().mimeType()).isEqualTo(HelloWorldApp.MIME_TYPE);
    var resourceLink = (McpSchema.ResourceLink) result.content().get(1);
    assertThat(resourceLink.uri()).isEqualTo(HelloWorldApp.RESOURCE_URI);
    assertThat(resourceLink.mimeType()).isEqualTo(HelloWorldApp.MIME_TYPE);
    var textContent = (McpSchema.TextContent) result.content().get(2);
    assertThat(textContent.text()).contains("Opened the SonarQube Hello World MCP app.");
    assertThat(result.meta()).containsEntry("openai/outputTemplate", HelloWorldApp.RESOURCE_URI);
  }

  @SonarQubeMcpServerTest
  void it_should_serve_the_hello_world_resource(SonarQubeMcpServerTestHarness harness) {
    var client = harness.newClient();

    assertThat(client.listResources())
      .extracting(McpSchema.Resource::uri)
      .contains(HelloWorldApp.RESOURCE_URI);

    var resource = client.readResource(HelloWorldApp.RESOURCE_URI);

    assertThat(resource.contents()).hasSize(1);
    var content = (McpSchema.TextResourceContents) resource.contents().getFirst();
    assertThat(content.mimeType()).isEqualTo(HelloWorldApp.MIME_TYPE);
    assertThat(content.text())
      .contains("<h1>Color Picker</h1>")
      .contains("id=\"color-input\"")
      .contains("id=\"swatches\"");
    assertThat(content.meta())
      .containsEntry("openai/widgetDescription", HelloWorldApp.RESOURCE_DESCRIPTION)
      .containsEntry("openai/widgetPrefersBorder", true);
  }

}
