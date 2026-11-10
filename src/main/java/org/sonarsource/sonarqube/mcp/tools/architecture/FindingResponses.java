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

/** Bounds both collection previews and per-finding continuations without dropping their remaining entries. */
final class FindingResponses {
  static final int MAX_LISTED = 10;
  static final int MAX_DETAIL = 100;

  private FindingResponses() {
  }

  static Object preview(ProjectFinding finding, String binding, FindingCursor.Query query) {
    if (!(finding instanceof Finding<?> found) || (found.files().size() <= MAX_LISTED && keys(found).size() <= MAX_LISTED)) {
      return finding;
    }
    return chunk(found, binding, query, 0, 0, MAX_LISTED);
  }

  static Object continueFinding(List<ProjectFinding> findings, String binding, FindingCursor cursor) {
    if (!binding.equals(cursor.binding())) {
      throw FindingCursor.invalid();
    }
    var found = findings.stream().filter(finding -> FindingCursor.fingerprint(finding).equals(cursor.fingerprint()))
      .filter(Finding.class::isInstance).map(finding -> (Finding<?>) finding).findFirst().orElseThrow(FindingCursor::invalid);
    var keys = keys(found);
    if (cursor.fileOffset() > found.files().size() || cursor.nodeKeyOffset() > keys.size()
      || (cursor.fileOffset() == found.files().size() && cursor.nodeKeyOffset() == keys.size())) {
      throw FindingCursor.invalid();
    }
    return chunk(found, binding, cursor.query(), cursor.fileOffset(), cursor.nodeKeyOffset(), MAX_DETAIL);
  }

  static boolean truncated(Object finding) {
    return finding instanceof Chunk chunk && chunk.nextCursor() != null;
  }

  private static Chunk chunk(Finding<?> found, String binding, FindingCursor.Query query, int fileOffset, int keyOffset, int limit) {
    var files = found.files();
    var keys = keys(found);
    var fileEnd = Math.min(files.size(), fileOffset + limit);
    var keyEnd = Math.min(keys.size(), keyOffset + limit);
    var moreFiles = fileEnd < files.size();
    var moreKeys = keyEnd < keys.size();
    Object details = found.details() instanceof TangleDetails tangle
      ? new TangleChunk(keys.subList(keyOffset, keyEnd), tangle.nodeCount(), keys.size(), keyOffset, moreKeys)
      : found.details();
    return new Chunk(found.type(), found.category(), found.language(), details, files.subList(fileOffset, fileEnd), files.size(), fileOffset, moreFiles,
      moreFiles || moreKeys ? FindingCursor.next(binding, query, found, fileEnd, keyEnd) : null);
  }

  private static List<String> keys(Finding<?> found) {
    return found.details() instanceof TangleDetails tangle ? tangle.nodeKeys() : List.of();
  }

  static boolean matches(ProjectFinding finding, @Nullable String component, @Nullable String from, @Nullable String to) {
    if (!(finding instanceof Finding<?> found)) {
      return false;
    }
    return switch (found.details()) {
      case StructuralDetails details -> component != null && component.equals(details.nodeKey());
      case ComponentDetails details -> component != null && component.equals(details.nodeKey());
      case RelationshipDetails details -> from != null
        ? (from.equals(details.fromNodeKey()) && to.equals(details.toNodeKey()))
        : (component != null && (component.equals(details.fromNodeKey()) || component.equals(details.toNodeKey())));
      case TangleDetails details -> component != null && details.nodeKeys().contains(component);
    };
  }

  private record Chunk(String type, String category, String language, Object details, List<String> files, int fileCount, int fileOffset,
    boolean filesTruncated, @Nullable String nextCursor) {
  }

  private record TangleChunk(List<String> nodeKeys, BigDecimal nodeCount, int nodeKeyCount, int nodeKeyOffset, boolean nodeKeysTruncated) {
  }
}
