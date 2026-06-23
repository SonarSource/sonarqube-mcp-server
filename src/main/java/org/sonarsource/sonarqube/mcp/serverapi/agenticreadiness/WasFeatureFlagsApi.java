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
package org.sonarsource.sonarqube.mcp.serverapi.agenticreadiness;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.List;
import java.util.Map;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.UrlBuilder;

public class WasFeatureFlagsApi {

  public static final String FEATURE_FLAGS_PATH = "/was-experiments/feature-flags";
  public static final String SARA_FEATURE_FLAG_KEY = "workflow-standards-enable-agentic-readiness-assessment";

  private static final Gson GSON = new Gson();

  private final ServerApiHelper helper;

  public WasFeatureFlagsApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public Map<String, Object> getFeatureFlags(String organizationId, List<String> keys) {
    var path = new UrlBuilder(FEATURE_FLAGS_PATH)
      .addParam("organizationId", organizationId)
      .addParam("keys", keys)
      .build();
    try (var response = helper.getApiSubdomain(path)) {
      return GSON.fromJson(response.bodyAsString(), new TypeToken<Map<String, Object>>() {}.getType());
    }
  }
}
