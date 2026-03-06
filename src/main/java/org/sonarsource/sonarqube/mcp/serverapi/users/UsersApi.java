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
package org.sonarsource.sonarqube.mcp.serverapi.users;

import com.google.gson.Gson;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarqube.mcp.log.McpLogger;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
@SuppressWarnings("java:S1075")

public class UsersApi {

  public static final String CURRENT_USER_PATH = "/api/users/current";

  private static final McpLogger LOG = McpLogger.getInstance();
  private final ServerApiHelper helper;

  public UsersApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  /**
   * Returns the UUID of the currently authenticated user. Available on SonarQube Cloud and SonarQube Server 2025.6+.
   */
  @CheckForNull
  public String getCurrentUserId() {
    try (var response = helper.get(CURRENT_USER_PATH)) {
      var dto = new Gson().fromJson(response.bodyAsString(), CurrentUserResponse.class);
      return dto.id();
    } catch (Exception e) {
      LOG.debug("Could not retrieve current user id: " + e.getMessage());
      return null;
    }
  }

  record CurrentUserResponse(String id) {
  }

}
