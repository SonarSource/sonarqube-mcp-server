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
package org.sonarsource.sonarqube.mcp.tools.system;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import jakarta.annotation.Nullable;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SystemHealthToolResponse(
  @JsonPropertyDescription("Overall health status of the system") String health,
  @JsonPropertyDescription("List of health issues, if any") @Nullable List<Cause> causes,
  @JsonPropertyDescription("List of cluster nodes with their health status") @Nullable List<Node> nodes
) {
  
  public record Cause(
    @JsonPropertyDescription("Description of the health issue") String message
  ) {}
  
  public record Node(
    @JsonPropertyDescription("Node name") String name,
    @JsonPropertyDescription("Node type (APPLICATION, SEARCH, etc.)") String type,
    @JsonPropertyDescription("Health status of this node") String health,
    @JsonPropertyDescription("Host address") String host,
    @JsonPropertyDescription("Port number") int port,
    @JsonPropertyDescription("Timestamp when the node started") String startedAt,
    @JsonPropertyDescription("List of node-specific health issues") @Nullable List<Cause> causes
  ) {}
}


