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
import java.math.BigDecimal;
import java.util.List;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.ProjectFinding;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.ProjectFinding.ComponentDetails;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.ProjectFinding.Finding;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.ProjectFinding.RelationshipDetails;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.ProjectFinding.StructuralDetails;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.ProjectFinding.TangleDetails;

/** Caps finding file and node-key lists in page responses and keeps a full finding for a detail read. */
final class FindingResponses {

  static final int MAX_LISTED = 10;

  private FindingResponses() {
  }

  static Object present(ProjectFinding finding, boolean compact) {
    if (!compact || !(finding instanceof Finding<?> found)) {
      return finding;
    }
    var files = found.files();
    var filesTruncated = files.size() > MAX_LISTED;
    var details = compactDetails(found.details());
    if (!filesTruncated && details == found.details()) {
      return finding;
    }
    return new ListedFinding(found.type(), found.category(), found.language(), details,
      filesTruncated ? files.subList(0, MAX_LISTED) : files,
      filesTruncated ? files.size() : null,
      filesTruncated ? true : null);
  }

  static boolean truncated(Object presented) {
    return presented instanceof ListedFinding listed && (Boolean.TRUE.equals(listed.filesTruncated()) || nodeKeysTruncated(listed.details()))
      || presented instanceof Finding<?> found && nodeKeysTruncated(found.details());
  }

  static boolean matches(ProjectFinding finding, @Nullable String component, @Nullable String from, @Nullable String to) {
    if (!(finding instanceof Finding<?> found)) {
      return false;
    }
    return switch (found.details()) {
      case StructuralDetails details -> component != null && component.equals(details.nodeKey());
      case ComponentDetails details -> component != null && component.equals(details.nodeKey());
      case RelationshipDetails details -> from != null
        ? from.equals(details.fromNodeKey()) && to.equals(details.toNodeKey())
        : component != null && (component.equals(details.fromNodeKey()) || component.equals(details.toNodeKey()));
      case TangleDetails details -> component != null && details.nodeKeys().contains(component);
    };
  }

  private static Object compactDetails(ProjectFinding.Details details) {
    if (details instanceof TangleDetails tangle && tangle.nodeKeys().size() > MAX_LISTED) {
      return new CompactTangle(tangle.nodeKeys().subList(0, MAX_LISTED), tangle.nodeCount(), tangle.nodeKeys().size(), true);
    }
    return details;
  }

  private static boolean nodeKeysTruncated(Object details) {
    return details instanceof CompactTangle tangle && tangle.nodeKeysTruncated();
  }

  private record ListedFinding(String type, String category, String language, Object details, List<String> files,
    @Nullable Integer fileCount, @Nullable Boolean filesTruncated) {
  }

  private record CompactTangle(List<String> nodeKeys, BigDecimal nodeCount, int nodeKeyCount, boolean nodeKeysTruncated) {
  }
}
