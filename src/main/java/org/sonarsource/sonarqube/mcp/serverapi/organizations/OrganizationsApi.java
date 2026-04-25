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
package org.sonarsource.sonarqube.mcp.serverapi.organizations;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import jakarta.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.log.McpLogger;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.UrlBuilder;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.response.SearchMemberOrganizationsResponse;

public class OrganizationsApi {

  public static final String ORGANIZATIONS_PATH = "/organizations/organizations";
  public static final String SEARCH_PATH = "/api/organizations/search";

  private static final McpLogger LOG = McpLogger.getInstance();
  private final ServerApiHelper helper;

  public OrganizationsApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  /**
   * Returns the UUID v4 of the organization identified by the given key. Only available on SonarQube Cloud.
   */
  @Nullable
  public String getOrganizationUuidV4(String organizationKey) {
    var path = new UrlBuilder(ORGANIZATIONS_PATH)
      .addParam("organizationKey", organizationKey)
      .addParam("excludeEligibility", "true")
      .build();
    try (var response = helper.getApiSubdomain(path)) {
      var dtos = new Gson().fromJson(response.bodyAsString(), OrganizationResponse[].class);
      if (dtos == null || dtos.length == 0) {
        return null;
      }
      return dtos[0].uuidV4();
    } catch (Exception e) {
      LOG.debug("Could not retrieve organization UUID for key '" + organizationKey + "': " + e.getMessage());
      return null;
    }
  }

  /**
   * Searches for organizations the authenticated user is a member of.
   * Only available on SonarQube Cloud.
   */
  public SearchMemberOrganizationsResponse searchMemberOrganizations(int page, int pageSize) {
    var path = new UrlBuilder(SEARCH_PATH)
      .addParam("member", "true")
      .addParam("p", page)
      .addParam("ps", pageSize)
      .build();
    try (var response = helper.get(path)) {
      return new Gson().fromJson(response.bodyAsString(), SearchMemberOrganizationsResponse.class);
    }
  }

  record OrganizationResponse(String id, @SerializedName("uuidV4") String uuidV4) {
  }

}
