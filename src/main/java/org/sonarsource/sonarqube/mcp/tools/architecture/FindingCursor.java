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

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import jakarta.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.ProjectFinding;

/** Stateless navigation only: every continuation rechecks context and reads with the caller's credentials. */
record FindingCursor(int version, String binding, Query query, String fingerprint, int fileOffset, int nodeKeyOffset) {
  private static final Gson GSON = new Gson();
  private static final int CURSOR_VERSION = 1;
  private static final String REFRESH = "Invalid or stale finding cursor. Refresh the findings listing.";

  record Query(int pageIndex, int pageSize, @Nullable String category, @Nullable String type, @Nullable String language,
    @Nullable String component, @Nullable String from, @Nullable String to) {
    Query {
      ArchitectureApi.validateFindingPage(pageIndex, pageSize);
      if (pageSize == 0 || (category != null && !ArchitectureApi.FINDING_CATEGORIES.contains(category))
        || (type != null && !ArchitectureApi.FINDING_TYPES.contains(type))
        || (language != null && !ArchitectureApi.LANGUAGES.contains(language))) {
        throw invalid();
      }
      new ArchitectureApi.FindingFilters(category, type, language);
      if ((from == null) != (to == null) || (from != null && component != null)) {
        throw invalid();
      }
    }

    ArchitectureApi.FindingFilters filters() {
      return new ArchitectureApi.FindingFilters(category, type, language);
    }
  }

  FindingCursor {
    if (version != CURSOR_VERSION || binding == null || !binding.matches("[0-9a-f]{64}") || query == null
      || fingerprint == null || !fingerprint.matches("[0-9a-f]{64}") || fileOffset < 0 || nodeKeyOffset < 0) {
      throw invalid();
    }
  }

  static String binding(String projectKey, String organizationId, String branchId) {
    return digest(GSON.toJson(List.of(projectKey, organizationId, branchId)));
  }

  static String fingerprint(ProjectFinding finding) {
    return digest(GSON.toJson(finding));
  }

  static String next(String binding, Query query, ProjectFinding finding, int fileOffset, int nodeKeyOffset) {
    return new FindingCursor(CURSOR_VERSION, binding, query, fingerprint(finding), fileOffset, nodeKeyOffset).encode();
  }

  private String encode() {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(GSON.toJson(this).getBytes(StandardCharsets.UTF_8));
  }

  static FindingCursor decode(String encoded) {
    try {
      if (encoded.length() > 4096 || !encoded.matches("[A-Za-z0-9_-]+")) {
        throw invalid();
      }
      var json = JsonParser.parseString(new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8)).getAsJsonObject();
      if (!json.keySet().equals(Set.of("version", "binding", "query", "fingerprint", "fileOffset", "nodeKeyOffset"))) {
        throw invalid();
      }
      for (var name : List.of("version", "fileOffset", "nodeKeyOffset")) {
        requireInteger(json.get(name));
      }
      requireString(json.get("binding"));
      requireString(json.get("fingerprint"));
      var query = json.getAsJsonObject("query");
      if (!Set.of("pageIndex", "pageSize", "category", "type", "language", "component", "from", "to").containsAll(query.keySet())) {
        throw invalid();
      }
      requireInteger(query.get("pageIndex"));
      requireInteger(query.get("pageSize"));
      for (var name : List.of("category", "type", "language", "component", "from", "to")) {
        if (query.has(name) && !query.get(name).isJsonNull()) {
          requireString(query.get(name));
        }
      }
      return GSON.fromJson(json, FindingCursor.class);
    } catch (RuntimeException e) {
      throw invalid();
    }
  }

  private static int requireInteger(@Nullable JsonElement value) {
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
      throw invalid();
    }
    return value.getAsBigDecimal().intValueExact();
  }

  private static void requireString(@Nullable JsonElement value) {
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
      throw invalid();
    }
  }

  static IllegalArgumentException invalid() {
    return new IllegalArgumentException(REFRESH);
  }

  private static String digest(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
