/*
 * SonarQube MCP Server
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
package org.sonarsource.sonarqube.mcp.tools.system;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import jakarta.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.tools.Description;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SystemHealthToolResponse(
  @Description("Overall health status of the system") String health,
  @Description("List of health issues, if any") @Nullable List<Cause> causes,
  @Description("List of cluster nodes with their health status") @Nullable List<Node> nodes
) {
  
  public record Cause(
    @Description("Description of the health issue") String message
  ) {}
  
  public record Node(
    @Description("Node name") String name,
    @Description("Node type (APPLICATION, SEARCH, etc.)") String type,
    @Description("Health status of this node") String health,
    @Description("Host address") String host,
    @Description("Port number") int port,
    @Description("Timestamp when the node started") String startedAt,
    @Description("List of node-specific health issues") @Nullable List<Cause> causes
  ) {}
}


