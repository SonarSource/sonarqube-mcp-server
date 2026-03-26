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
package org.sonarsource.sonarqube.mcp.serverapi.a3s;

import com.google.gson.Gson;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarqube.mcp.log.McpLogger;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.a3s.request.AnalysisCreationRequest;
import org.sonarsource.sonarqube.mcp.serverapi.a3s.response.AnalysisResponse;

public class A3sAnalysisApi {

  public static final String ANALYSES_PATH = "/a3s-analysis/analyses";
  public static final String A3S_ORG_CONFIG_PATH = "/a3s-analysis/org-config/";
  public static final String CAG_ORG_CONFIG_PATH = "/a3s-analysis/cag-org-config/";

  private static final String JSON_CONTENT_TYPE = "application/json";
  private static final Gson GSON = new Gson();
  private static final McpLogger LOG = McpLogger.getInstance();

  private final ServerApiHelper helper;

  public A3sAnalysisApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public AnalysisResponse analyze(AnalysisCreationRequest request) {
    var requestBody = GSON.toJson(request);
    try (var response = helper.postApiSubdomain(ANALYSES_PATH, JSON_CONTENT_TYPE, requestBody)) {
      return GSON.fromJson(response.bodyAsString(), AnalysisResponse.class);
    }
  }

  @CheckForNull
  public OrgConfigResponse getA3sOrgConfig(String organizationUuidV4) {
    try (var response = helper.getApiSubdomain(A3S_ORG_CONFIG_PATH + organizationUuidV4)) {
      return GSON.fromJson(response.bodyAsString(), OrgConfigResponse.class);
    } catch (Exception e) {
      LOG.warn("Could not retrieve A3S org config for organization '" + organizationUuidV4 + "': " + e.getMessage());
      return null;
    }
  }

  @CheckForNull
  public CagConfigResponse getCagOrgConfig(String organizationUuidV4) {
    try (var response = helper.getApiSubdomain(CAG_ORG_CONFIG_PATH + organizationUuidV4)) {
      return GSON.fromJson(response.bodyAsString(), CagConfigResponse.class);
    } catch (Exception e) {
      LOG.warn("Could not retrieve CAG org config for organization '" + organizationUuidV4 + "': " + e.getMessage());
      return null;
    }
  }

  public record OrgConfigResponse(String id, boolean enabled, boolean eligible) {
  }

  public record CagConfigResponse(String id, boolean enabled, boolean eligible) {
  }
}
