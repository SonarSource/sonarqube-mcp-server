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
package org.sonarsource.sonarqube.mcp.tools.architecture;

import jakarta.annotation.Nullable;
import java.util.List;

public record ArchitectureGetCurrentToolResponse(String projectKey, @Nullable String component, int depth, boolean capped, List<Graph> graphs,
  List<DrillDown> drillDown, @Nullable String message) {

  /**
   * {@code totalNodes}/{@code totalEdges} describe the whole graph. {@code nodesInScope} counts what the component and
   * depth select before the node cap; {@code edgesInScope} counts the edges between shown nodes before the edge cap.
   * {@code feedbackPairs} is counted over those same edges. {@code outsideViewWeight} is the weight of the dependencies
   * between a shown node and a node that is not shown.
   */
  public record Graph(String language, int totalNodes, int totalEdges, int nodesInScope, int edgesInScope, List<Node> nodes,
    List<Edge> edges, FeedbackPairs feedbackPairs, int outsideViewWeight) {
  }

  /**
   * Shown nodes that depend on each other both ways. {@code heaviest} holds at most five pairs, by combined weight;
   * {@code from} is the heavier direction.
   */
  public record FeedbackPairs(int total, List<Edge> heaviest) {
  }

  /**
   * {@code children} is the number of direct children in the whole graph, shown or not.
   */
  public record Node(String key, String kind, int children) {
  }

  public record Edge(String from, String to, int weight) {
  }

  public record DrillDown(String language, String component, int descendants) {
  }

}
