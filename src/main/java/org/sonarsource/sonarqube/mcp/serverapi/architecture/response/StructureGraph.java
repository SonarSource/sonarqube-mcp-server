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
package org.sonarsource.sonarqube.mcp.serverapi.architecture.response;

import jakarta.annotation.Nullable;
import java.util.List;

/**
 * Current architecture of one language: a tree of nodes whose edges point at other node ids.
 */
public record StructureGraph(int nodeCount, int edgeCount, @Nullable List<Node> nodes) {

  /**
   * {@code separator} is what the node's children are joined to it with in node keys, e.g. {@code :} for a module.
   */
  public record Node(int id, String name, String kind, @Nullable String separator, @Nullable List<Edge> edges, @Nullable List<Node> nodes) {
  }

  public record Edge(int toId, int weight) {
  }

}
