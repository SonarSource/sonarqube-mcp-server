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
package org.sonarsource.sonarqube.mcp.serverapi.users;

import com.google.gson.Gson;
import jakarta.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.log.McpLogger;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.exception.NotFoundException;

public class UsersApi {

  public static final String CURRENT_USER_PATH = "/api/users/current";

  private static final McpLogger LOG = McpLogger.getInstance();
  private final ServerApiHelper helper;
  @Nullable
  private String currentUserId;
  private boolean currentUserResolved;

  public UsersApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  /**
   * Returns the UUID of the currently authenticated user. Available on SonarQube Cloud and SonarQube Server 2025.6+.
   * Caches successful responses and unsupported endpoints; transient failures remain retryable.
   */
  @Nullable
  public synchronized String getCurrentUserId() {
    if (currentUserResolved) {
      return currentUserId;
    }
    try (var response = helper.get(CURRENT_USER_PATH)) {
      var dto = new Gson().fromJson(response.bodyAsString(), CurrentUserResponse.class);
      currentUserId = dto.id();
      currentUserResolved = true;
    } catch (NotFoundException e) {
      currentUserResolved = true;
    } catch (Exception e) {
      LOG.debug("Could not retrieve current user id: " + e.getMessage());
    }
    return currentUserId;
  }

  record CurrentUserResponse(String id) {
  }

}
