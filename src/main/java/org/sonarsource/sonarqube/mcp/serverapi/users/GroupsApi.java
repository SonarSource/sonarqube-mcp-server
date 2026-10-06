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
import com.google.gson.JsonObject;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.UrlBuilder;

public final class GroupsApi {
  public static final String GROUPS_PATH = "/users/groups";
  private static final Gson GSON = new Gson();
  private final ServerApiHelper helper;

  public GroupsApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public ListResponse list(UUID organizationId, @Nullable String name, @Nullable Integer pageIndex, @Nullable Integer pageSize) {
    var path = new UrlBuilder(GROUPS_PATH).addParam("organizationIds", organizationId.toString())
      .addParam("name", name).addParam("pageIndex", pageIndex).addParam("pageSize", pageSize).build();
    try (var response = helper.getApiSubdomain(path)) {
      return GSON.fromJson(response.bodyAsString(), ListResponse.class);
    }
  }

  public Group create(UUID organizationId, String name, @Nullable String description) {
    var body = new JsonObject();
    body.addProperty("organizationId", organizationId.toString());
    body.addProperty("name", name);
    if (description != null) {
      body.addProperty("description", description);
    }
    try (var response = helper.postApiSubdomain(GROUPS_PATH, "application/json", body.toString())) {
      return GSON.fromJson(response.bodyAsString(), Group.class);
    }
  }

  public void delete(UUID groupId) {
    try (var response = helper.deleteApiSubdomain(GROUPS_PATH + "/" + groupId)) {
      if (response.code() != 204) {
        throw new IllegalStateException("Expected a no-content response when deleting a Cloud group");
      }
    }
  }

  public record Group(String id, String organizationId, String name, @Nullable String description, @Nullable Boolean builtIn, @Nullable Boolean managed) { }
  public record Page(int pageIndex, int pageSize, long total) { }
  public record ListResponse(List<Group> groups, Page page) { }
}

