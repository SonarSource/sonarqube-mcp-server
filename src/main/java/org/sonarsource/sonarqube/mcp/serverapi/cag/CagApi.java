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
package org.sonarsource.sonarqube.mcp.serverapi.cag;

import com.google.gson.Gson;
import jakarta.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.log.McpLogger;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.exception.NotFoundException;

public class CagApi {

  public static final String CAG_ENTITLEMENT_PATH = "/cag/cag-entitlement/";

  /**
   * Placeholder organization id for the Server entitlement path segment.
   * The Hub resolves the caller's default organization server-side; the nil
   * UUID is a syntactically valid placeholder.
   */
  public static final String SERVER_ORGANIZATION_ID_PLACEHOLDER = "00000000-0000-0000-0000-000000000000";

  private static final Gson GSON = new Gson();
  private static final McpLogger LOG = McpLogger.getInstance();

  private final ServerApiHelper helper;

  public CagApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  @Nullable
  public CagEntitlementResponse getCagEntitlement(String organizationUuidV4) {
    var path = CAG_ENTITLEMENT_PATH + organizationUuidV4;
    try (var response = helper.isSonarQubeCloud() ? helper.getApiSubdomain(path) : helper.get("/api/v2" + path)) {
      return GSON.fromJson(response.bodyAsString(), CagEntitlementResponse.class);
    } catch (NotFoundException e) {
      // A Server 404 means the CAG Hub is not on this instance (pre-LTA). Stay silent.
      // Cloud 404 is a failed check, same as any other transport error.
      if (helper.isSonarQubeCloud()) {
        LOG.warn("Could not retrieve CAG entitlement for organization '" + organizationUuidV4 + "': " + e.getMessage());
      } else {
        LOG.debug("CAG entitlement endpoint not found for organization '" + organizationUuidV4 + "': " + e.getMessage());
      }
      return null;
    } catch (Exception e) {
      LOG.warn("Could not retrieve CAG entitlement for organization '" + organizationUuidV4 + "': " + e.getMessage());
      return null;
    }
  }

  public record CagEntitlementResponse(boolean hasEntitlement) {
  }
}
