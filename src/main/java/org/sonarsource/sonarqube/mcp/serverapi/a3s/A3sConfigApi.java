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

public class A3sConfigApi {

  private static final String A3S_ANALYSIS_BASE_PATH = "/a3s-analysis";
  public static final String ORG_CONFIG_PATH = A3S_ANALYSIS_BASE_PATH + "/org-config/";
  private static final Gson GSON = new Gson();
  private static final McpLogger LOG = McpLogger.getInstance();

  private final ServerApiHelper helper;

  public A3sConfigApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  @CheckForNull
  public OrgConfigResponse getOrgConfig(String organizationUuidV4) {
    try (var response = helper.getApiSubdomain(ORG_CONFIG_PATH + organizationUuidV4)) {
      return GSON.fromJson(response.bodyAsString(), OrgConfigResponse.class);
    } catch (Exception e) {
      LOG.warn("Could not retrieve A3S org config for organization '" + organizationUuidV4 + "': " + e.getMessage());
      return null;
    }
  }

  public record OrgConfigResponse(String id, boolean enabled, boolean eligible) {
  }

}
