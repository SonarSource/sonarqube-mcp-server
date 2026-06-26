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
import com.google.gson.annotations.SerializedName;
import java.util.List;
import jakarta.annotation.Nullable;
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

  /**
   * Returns whether the agentic readiness assessment feature is enabled for the given organization.
   */
  public boolean isAgenticReadinessAssessmentEnabled(String organizationId) {
    var path = new UrlBuilder(FEATURE_FLAGS_PATH)
      .addParam("organizationId", organizationId)
      .addParam("keys", List.of(SARA_FEATURE_FLAG_KEY))
      .build();
    try (var response = helper.getApiSubdomain(path)) {
      var flags = GSON.fromJson(response.bodyAsString(), FeatureFlagsResponse.class);
      return flags != null && flags.isAgenticReadinessAssessmentEnabled();
    }
  }

  private record FeatureFlagsResponse(@SerializedName(SARA_FEATURE_FLAG_KEY) @Nullable Boolean agenticReadinessAssessmentEnabled) {

    boolean isAgenticReadinessAssessmentEnabled() {
      return Boolean.TRUE.equals(agenticReadinessAssessmentEnabled);
    }
  }
}
