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
import jakarta.annotation.Nullable;
import java.util.List;
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
  public static final String INTENDED_ARCHITECTURES_PATH = "/architecture/intended-architectures";
  public static final String SERVER_API_PREFIX = "/api/v2";
  /** SonarQube Server has no organizations; its architecture endpoints expect this fixed id. */
  public static final String SERVER_ORGANIZATION_ID = "00000000-0000-4000-0000-000000000000";
  public static final int MAX_PERSPECTIVES = 50;
  public static final List<String> LANGUAGES = List.of("java", "cs", "js", "ts", "py");

  /** Public exports are insufficient for lossless replacement or recovery. */
  public static final String EXPORT_WARNING = "Public exports do not identify imported organization patterns or include all model metadata. "
    + "Replacing or restoring an export may remove import links and metadata. "
    + "If organization features are disabled, imported groups may be exported without their contents. The export cannot recover omitted data.";

  private static final String READ_HINT = "Check project permissions, feature availability and the public API gateway routing; "
    + "HTTP 403 alone does not establish which check failed";
  private static final String PLAN_HINT = "Architecture is available on SonarQube Cloud Team and Enterprise plans, "
    + "and on SonarQube Server editions that include it";

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
      .addParam("ecosystem", language)
      .build();
    var response = read(projectKey, () -> fromJson(get(path), CurrentArchitectures.class));
    if (response == null || response.currentArchitectures() == null) {
      throw new IllegalStateException("The public architecture API did not return currentArchitectures. No complete graph was read.");
    }
    return response.currentArchitectures();
  }

  /**
   * Returns one entry per language that has an intended architecture, or an empty list when none is defined.
   */
  public List<IntendedArchitecture> getIntended(String projectKey, String projectId) {
    var path = new UrlBuilder(INTENDED_ARCHITECTURES_PATH)
      .addParam("projectId", projectId)
      .build();
    var response = read(projectKey, () -> fromJson(get(path), IntendedArchitectures.class));
    if (response == null || response.intendedArchitectures() == null) {
      throw new IllegalStateException("The public architecture API did not return intendedArchitectures. No complete model was read.");
    }
    return validateModels(response.intendedArchitectures());
  }

  public List<IntendedArchitecture> createIntended(String projectKey, String projectId, List<IntendedArchitecture> models) {
    var body = gson.toJson(new CreateModels(projectId, publicModels(models)));
    return write(projectKey, () -> writeModels(INTENDED_ARCHITECTURES_PATH, body, false));
  }

  public List<IntendedArchitecture> patchIntended(String projectKey, String id, List<IntendedArchitecture> models) {
    var path = new URIBuilder().setPath(INTENDED_ARCHITECTURES_PATH).appendPathSegments(id).toString();
    var body = gson.toJson(new PatchModels(publicModels(models)));
    return write(projectKey, () -> writeModels(path, body, true));
  }

  private static List<PublicIntendedModel> publicModels(List<IntendedArchitecture> models) {
    return models.stream().map(model -> new PublicIntendedModel(model.type(), model.language(),
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

  private List<IntendedArchitecture> writeModels(String path, String body, boolean patch) {
    try (var response = request(path, body, patch)) {
      var result = gson.fromJson(response.bodyAsString(), IntendedArchitectures.class);
      if (result == null || result.intendedArchitectures() == null || result.intendedArchitectures().isEmpty()) {
        throw new IllegalStateException("The public architecture API did not return the written intendedArchitectures.");
      }
      return validateModels(result.intendedArchitectures());
    }
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
    return new IllegalStateException("The public architecture API returned an incomplete intended architecture. No complete model was read.");
  }

  private record CreateModels(String projectId, List<PublicIntendedModel> intendedArchitectures) {
  }

  private record PatchModels(List<PublicIntendedModel> intendedArchitectures) {
  }

  private record PublicIntendedModel(String type, String language, List<IntendedArchitecture.Group> groups,
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
    }
  }

  private record CurrentArchitectures(@Nullable List<StructureGraph> currentArchitectures) {
  }

  private record IntendedArchitectures(@Nullable List<IntendedArchitecture> intendedArchitectures) {
  }

}
