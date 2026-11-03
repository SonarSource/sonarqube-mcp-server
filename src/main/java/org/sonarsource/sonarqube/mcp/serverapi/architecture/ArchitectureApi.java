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
package org.sonarsource.sonarqube.mcp.serverapi.architecture;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.apache.commons.lang3.Strings;
import org.apache.hc.core5.net.URIBuilder;
import org.sonarsource.sonarqube.mcp.http.HttpClient;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.UrlBuilder;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.IntendedArchitecture;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.StructureGraph;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ForbiddenException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ServerApiException;

/** Public architecture HTTP requests, response validation and Cloud/Server routing. */
public class ArchitectureApi {

  public static final String CURRENT_ARCHITECTURES_PATH = "/architecture/current-architectures";
  public static final String INTENDED_ARCHITECTURES_PATH = "/architecture/project-intended-architectures";
  public static final String SERVER_API_PREFIX = "/api/v2";
  /** SonarQube Server has no organizations; its architecture endpoints expect this fixed id. */
  public static final String SERVER_ORGANIZATION_ID = "00000000-0000-4000-0000-000000000000";
  public static final int MAX_PERSPECTIVES = 50;
  public static final List<String> LANGUAGES = List.of("java", "cs", "js", "ts", "py");

  public static final String EXPORT_WARNING = "Imported organization patterns remain linked by patternId. "
    + "The public export does not expand their groups or constraints. "
    + "Keep patternId to preserve the reference; imported contents cannot be edited here.";

  private static final String READ_HINT = "Check project permissions, feature availability and the public API gateway routing; "
    + "HTTP 403 alone does not establish which check failed";
  private static final String PLAN_HINT = "Architecture is available on SonarQube Cloud Team and Enterprise plans, "
    + "and on SonarQube Server editions that include it";

  private static final String COMPATIBILITY_HINT = "The response does not match this MCP server's supported public Architecture API contract. "
    + "Check that the MCP server and the connected SonarQube deployment support the same Architecture API.";

  private static IllegalStateException incompatibleResponse(String message) {
    return new IllegalStateException(message + " " + COMPATIBILITY_HINT);
  }

  private final ServerApiHelper helper;
  private final Gson gson = new Gson();

  public ArchitectureApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  /**
   * Returns an empty list when the project has no current architecture for the language yet.
   */
  public List<StructureGraph> getStructure(String projectKey, String organizationId, String branchId, String language) {
    var path = new UrlBuilder(CURRENT_ARCHITECTURES_PATH)
      .addParam("organizationId", organizationId)
      .addParam("branchId", branchId)
      .addParam("language", language)
      .build();
    var response = read(projectKey, () -> fromJson(get(path), CurrentArchitectures.class));
    if (response == null || response.currentArchitectures() == null) {
      throw new IllegalStateException("The public architecture API did not return currentArchitectures. No complete graph was read.");
    }
    return response.currentArchitectures();
  }

  /**
   * Returns all publicly exported intended perspectives, or an empty list when none is visible.
   */
  public List<IntendedArchitecture> getIntended(String projectKey, String projectId) {
    return getIntendedState(projectKey, projectId).models();
  }

  public IntendedState getIntendedState(String projectKey, String projectId) {
    var path = new UrlBuilder(INTENDED_ARCHITECTURES_PATH).addParam("projectId", projectId).build();
    var response = read(projectKey, () -> fromJson(get(path), IntendedArchitectures.class));
    if (response == null || response.intendedArchitectures() == null || response.intendedArchitectures().size() > 1) {
      throw incompleteModel();
    }
    return response.intendedArchitectures().isEmpty() ? new IntendedState(null, List.of()) : exportedResource(response.intendedArchitectures().getFirst());
  }

  public IntendedState createIntended(String projectKey, String projectId, List<IntendedArchitecture> models) {
    var body = gson.toJson(new CreateModels(projectId, publicModels(models)));
    return write(projectKey, () -> writeModels(INTENDED_ARCHITECTURES_PATH, body, false));
  }

  public IntendedState patchIntended(String projectKey, String id, List<IntendedArchitecture> models) {
    var path = new URIBuilder().setPath(INTENDED_ARCHITECTURES_PATH).appendPathSegments(id).toString();
    var body = gson.toJson(new PatchModels(publicModels(models)));
    return write(projectKey, () -> writeModels(path, body, true));
  }

  public void deleteIntended(String projectKey, String id) {
    var path = new URIBuilder().setPath(INTENDED_ARCHITECTURES_PATH).appendPathSegments(id).toString();
    write(projectKey, () -> {
      try (var response = helper.isSonarQubeCloud() ? helper.deleteApiSubdomain(path) : helper.delete(SERVER_API_PREFIX + path)) {
        if (response.code() != 204) {
          throw new IllegalStateException("The public intended architecture DELETE did not return HTTP 204. Inspect the current model before retrying.");
        }
      }
      return null;
    });
  }

  private static List<PublicIntendedModel> publicModels(List<IntendedArchitecture> models) {
    return models.stream().map(model -> new PublicIntendedModel(model.type(), model.language(), model.label(), model.description(),
      model.groups() == null ? List.of() : model.groups(), model.constraints() == null ? List.of() : model.constraints())).toList();
  }

  private static <T> T write(String projectKey, Supplier<T> request) {
    try {
      return request.get();
    } catch (ForbiddenException e) {
      throw new ForbiddenException("a refusal to change the architecture of project '" + projectKey + "': " + reason(e)
        + ". Changing architecture needs project edit permissions. " + PLAN_HINT);
    }
  }

  private IntendedState writeModels(String path, String body, boolean patch) {
    var result = fromJson(request(path, body, patch), IntendedResource.class);
    if (result == null || result.perspectives() == null || result.perspectives().isEmpty()) {
      throw incompatibleResponse("The public architecture API did not return the written intended perspectives.");
    }
    return exportedResource(result);
  }

  private HttpClient.Response request(String path, String body, boolean patch) {
    if (patch) {
      return helper.isSonarQubeCloud() ? helper.patchApiSubdomain(path, HttpClient.MERGE_PATCH_CONTENT_TYPE, body)
        : helper.patch(SERVER_API_PREFIX + path, HttpClient.MERGE_PATCH_CONTENT_TYPE, body);
    }
    return helper.isSonarQubeCloud() ? helper.postApiSubdomain(path, HttpClient.JSON_CONTENT_TYPE, body)
      : helper.post(SERVER_API_PREFIX + path, HttpClient.JSON_CONTENT_TYPE, body);
  }

  private static List<IntendedArchitecture> validateModels(List<IntendedArchitecture> models) {
    for (var model : models) {
      if (model == null || model.type() == null || model.language() == null || model.groups() == null || model.constraints() == null) {
        throw incompleteModel();
      }
      validateGroups(model.groups());
      for (var constraint : model.constraints()) {
        if (constraint == null || constraint.from() == null || constraint.to() == null) {
          throw incompleteModel();
        }
      }
    }
    return models;
  }

  private static void validateGroups(List<IntendedArchitecture.Group> groups) {
    for (var group : groups) {
      if (group == null || group.label() == null || (group.patterns() != null && group.patterns().contains(null))) {
        throw incompleteModel();
      }
      if (group.groups() != null) {
        validateGroups(group.groups());
      }
    }
  }

  private static IllegalStateException incompleteModel() {
    return incompatibleResponse("The public architecture API returned an incomplete intended architecture. No complete model was read.");
  }

  private record CreateModels(String projectId, List<PublicIntendedModel> perspectives) {
  }

  private record PatchModels(List<PublicIntendedModel> perspectives) {
  }

  private record PublicIntendedModel(String qualifiers, String language, @Nullable String label, @Nullable String description, List<IntendedArchitecture.Group> groups,
    List<IntendedArchitecture.Constraint> constraints) {
  }

  private static <T> T read(String projectKey, Supplier<T> request) {
    try {
      return request.get();
    } catch (ForbiddenException e) {
      throw new ForbiddenException("a refusal to read the architecture of project '" + projectKey + "': " + reason(e) + ". " + READ_HINT + ". " + PLAN_HINT);
    }
  }

  private static String reason(ForbiddenException e) {
    return Strings.CS.removeStart(e.getMessage(), ServerApiException.MESSAGE_PREFIX);
  }

  private HttpClient.Response get(String path) {
    return helper.isSonarQubeCloud() ? helper.getApiSubdomain(path) : helper.get(SERVER_API_PREFIX + path);
  }

  @Nullable
  private <T> T fromJson(HttpClient.Response toBeClosed, Class<T> type) {
    try (var response = toBeClosed) {
      return gson.fromJson(response.bodyAsString(), type);
    } catch (JsonParseException invalid) {
      throw new IllegalStateException("Invalid public architecture response. " + COMPATIBILITY_HINT, invalid);
    }
  }

  private record CurrentArchitectures(@Nullable List<StructureGraph> currentArchitectures) {
  }

  private static IntendedState exportedResource(@Nullable IntendedResource response) {
    try {
      if (response == null || response.id() == null || !UUID.fromString(response.id()).toString().equalsIgnoreCase(response.id())
        || response.perspectives() == null || response.perspectives().size() > MAX_PERSPECTIVES) {
        throw incompleteModel();
      }
    } catch (IllegalArgumentException invalid) {
      throw incompleteModel();
    }
    var identities = new java.util.HashSet<String>();
    var models = validateModels(response.perspectives().stream().map(model -> {
      if (model == null || !identities.add(model.qualifiers() + "/" + model.language())) {
        throw incompleteModel();
      }
      return new IntendedArchitecture(response.id(), model.qualifiers(), model.language(), model.groups(), model.constraints(), model.label(), model.description());
    }).toList());
    return new IntendedState(response.id(), models);
  }

  public record IntendedState(@Nullable String id, List<IntendedArchitecture> models) {
  }

  private record PublicIntendedExport(String qualifiers, String language, List<IntendedArchitecture.Group> groups,
    List<IntendedArchitecture.Constraint> constraints, @Nullable String label, @Nullable String description) {
  }

  private record IntendedResource(@Nullable String id, @Nullable List<PublicIntendedExport> perspectives) {
  }

  private record IntendedArchitectures(@Nullable List<IntendedResource> intendedArchitectures) {
  }

}
