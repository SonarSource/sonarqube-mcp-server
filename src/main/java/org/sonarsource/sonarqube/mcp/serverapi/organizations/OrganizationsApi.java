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
import java.util.Arrays;
import java.util.List;
import org.sonarsource.sonarqube.mcp.log.McpLogger;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.UrlBuilder;

public class OrganizationsApi {

  public static final String ORGANIZATIONS_PATH = "/organizations/organizations";

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
      var dtos = new Gson().fromJson(response.bodyAsString(), Organization[].class);
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
   * Lists the organizations the authenticated user is a member of. Only available on SonarQube Cloud.
   * Unlike {@link #getOrganizationUuidV4(String)}, this does not swallow errors: HTTP failures
   * (e.g. invalid token) propagate so callers can distinguish an authentication problem from an empty result.
   */
  public List<Organization> listOrganizations() {
    var path = new UrlBuilder(ORGANIZATIONS_PATH)
      .addParam("excludeEligibility", "true")
      .build();
    try (var response = helper.getApiSubdomain(path)) {
      var dtos = new Gson().fromJson(response.bodyAsString(), Organization[].class);
      if (dtos == null) {
        return List.of();
      }
      return Arrays.stream(dtos).toList();
    }
  }

  public record Organization(String id, String key, String name, @SerializedName("uuidV4") String uuidV4) {
  }

}
