/*
 * Sonar MCP Server
 * Copyright (C) 2025 SonarSource
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonar.mcp.harness;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import java.util.List;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

public class MockWebServer {
  private final WireMockServer mockServer = new WireMockServer(options().dynamicPort());

  public void start() {
    mockServer.start();
  }

  public void stop() {
    mockServer.stop();
  }

  public List<ReceivedRequest> getReceivedRequests() {
    return mockServer.getServeEvents().getRequests()
      .stream().map(event -> new ReceivedRequest(event.getRequest().getHeader("Authorization"), event.getRequest().getBodyAsString()))
      .toList();
  }

  public StubMapping stubFor(MappingBuilder mappingBuilder) {
    return mockServer.stubFor(mappingBuilder);
  }

  public String baseUrl() {
    return mockServer.baseUrl();
  }

}
