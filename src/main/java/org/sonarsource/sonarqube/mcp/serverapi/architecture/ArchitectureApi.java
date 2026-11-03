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

import java.util.Objects;
import java.util.UUID;
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
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.ProjectDirective;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.StructureGraph;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ForbiddenException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ServerApiException;

/** Public architecture HTTP requests, response validation and Cloud/Server routing. */
public class ArchitectureApi {

  public static final String CURRENT_ARCHITECTURES_PATH = "/architecture/current-architectures";
  public static final String INTENDED_ARCHITECTURES_PATH = "/architecture/intended-architectures";
  public static final String PROJECT_DIRECTIVES_PATH = "/architecture/project-directives";
  public static final String SERVER_API_PREFIX = "/api/v2";
  /** SonarQube Server has no organizations; its architecture endpoints expect this fixed id. */
  public static final String SERVER_ORGANIZATION_ID = "00000000-0000-4000-0000-000000000000";
  public static final int MAX_PERSPECTIVES = 50;
  public static final List<String> LANGUAGES = List.of("java", "cs", "js", "ts", "py");

  public static final String EXPORT_WARNING = "Imported organization patterns remain linked by patternId. "
    + "Exported imported child groups must remain unedited or be omitted; they are excluded from writes. "
    + "Keep patternId to preserve the reference; imported contents cannot be edited here.";

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
    var path = new UrlBuilder(INTENDED_ARCHITECTURES_PATH)
      .addParam("projectId", projectId)
      .build();
    var response = read(projectKey, () -> fromJson(get(path), IntendedArchitectures.class));
    if (response == null || response.intendedArchitectures() == null) {
      throw new IllegalStateException("The public architecture API did not return intendedArchitectures. No complete model was read.");
    }
    return exportedModels(response);
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

  private List<IntendedArchitecture> writeModels(String path, String body, boolean patch) {
    try (var response = request(path, body, patch)) {
      var result = gson.fromJson(response.bodyAsString(), IntendedArchitectures.class);
      if (result == null || result.intendedArchitectures() == null || result.intendedArchitectures().isEmpty()) {
        throw new IllegalStateException("The public architecture API did not return the written intendedArchitectures.");
      }
      return exportedModels(result);
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

  public DirectivePage getDirectivePage(String projectKey, String projectId, int pageIndex, int pageSize) {
    return read(projectKey, () -> readDirectivePage(projectId, pageIndex, pageSize));
  }

  private DirectivePage readDirectivePage(String projectId, int pageIndex, int pageSize) {
    if (pageIndex < 1 || pageSize < 1 || pageSize > 1000) {
      throw new IllegalArgumentException("Directive page must be >= 1 and page size must be between 1 and 1000.");
    }
    var path = new UrlBuilder(PROJECT_DIRECTIVES_PATH).addParam("projectId", projectId)
      .addParam("pageIndex", Integer.toString(pageIndex)).addParam("pageSize", Integer.toString(pageSize)).build();
    var response = fromJson(get(path), DirectivePage.class);
    if (response == null || response.projectDirectives() == null || response.page() == null
      || response.page().pageIndex() == null || response.page().pageIndex() != pageIndex
      || response.page().pageSize() == null || response.page().pageSize() != pageSize
      || response.page().total() == null || response.page().total() < 0
      || response.projectDirectives().size() > pageSize) {
      throw new IllegalStateException("Invalid public directives page; no complete page was read.");
    }
    var ids = new java.util.HashSet<String>();
    for (var directive : response.projectDirectives()) {
      if (directive == null) {
        throw new IllegalStateException("The public directives response contains an incomplete directive.");
      }
      directive.validateStored();
      if (!ids.add(directive.id().toLowerCase(java.util.Locale.ROOT))) {
        throw new IllegalStateException("The public directives page returned duplicate ids.");
      }
      if (!projectId.equals(directive.projectId())) {
        throw new IllegalStateException("The public directives page returned a different project.");
      }
    }
    return response;
  }

  public ProjectDirective createDirective(String projectKey, String projectId, ProjectDirective directive) {
    directive.validate();
    return write(projectKey, () -> {
      var body = gson.toJson(directive.forProject(projectId));
      var response = fromJson(request(PROJECT_DIRECTIVES_PATH, body, false), ProjectDirective.class);
      if (response == null) {
        throw new IllegalStateException("The directive write returned no directive. Inspect the current list before retrying.");
      }
      response.validateStored();
      if (!projectId.equals(response.projectId())) {
        throw new IllegalStateException("The directive write returned a different project. Inspect the current list before retrying.");
      }
      if (!response.identity().equals(directive.identity())
        || (directive.description() != null && !directive.description().equals(response.description()))) {
        throw new IllegalStateException("The directive write response did not confirm the requested directive. Inspect the current list before retrying.");
      }
      return response;
    });
  }

  public ProjectDirective getDirective(String projectKey, String projectId, String id) {
    return read(projectKey, () -> {
      var directive = fromJson(get(directivePath(id)), ProjectDirective.class);
      validateDirectiveResource(directive, projectId, id);
      return directive;
    });
  }

  public ProjectDirective updateDirective(String projectKey, String projectId, String id, ProjectDirective directive) {
    directive.validate();
    return write(projectKey, () -> {
      var updated = fromJson(request(directivePath(id), gson.toJson(directive.withoutIdentity()), true), ProjectDirective.class);
      validateDirectiveResource(updated, projectId, id);
      var expectedIdentity = new ProjectDirective.Identity(directive.type(), directive.ecosystem(), directive.graphType(), directive.fromNodeKey(),
        directive.toNodeKey(), directive.graphPerspective() == null ? updated.identity().perspective() : directive.graphPerspective());
      if (!updated.identity().equals(expectedIdentity)
        || (directive.description() != null && !Objects.equals(updated.description(), directive.description()))) {
        throw new IllegalStateException("The directive update response did not confirm the requested directive. Inspect it before retrying.");
      }
      return updated;
    });
  }

  public void deleteDirective(String projectKey, String id) {
    write(projectKey, () -> {
      try (var response = helper.deleteApiSubdomain(directivePath(id))) {
        if (response.code() != java.net.HttpURLConnection.HTTP_NO_CONTENT) {
          throw new IllegalStateException("The directive deletion was not confirmed. Inspect it before retrying.");
        }
      }
      return null;
    });
  }

  private static String directivePath(String id) {
    if (!UUID.fromString(id).toString().equalsIgnoreCase(id)) {
      throw new IllegalArgumentException("Directive id must be a UUID.");
    }
    return PROJECT_DIRECTIVES_PATH + "/" + id;
  }

  private static void validateDirectiveResource(@Nullable ProjectDirective directive, String projectId, String id) {
    if (directive == null) {
      throw new IllegalStateException("The public directive response is empty.");
    }
    directive.validateStored();
    if (!projectId.equals(directive.projectId()) || !id.equalsIgnoreCase(directive.id())) {
      throw new IllegalStateException("The public directive response returned a different id or project.");
    }
  }

  public record DirectivePage(List<ProjectDirective> projectDirectives, DirectivePageInfo page) {
  }

  public record DirectivePageInfo(Integer pageIndex, Integer pageSize, Integer total) {
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

  private static List<IntendedArchitecture> exportedModels(IntendedArchitectures response) {
    if (!response.intendedArchitectures().isEmpty()) {
      try {
        if (response.id() == null || !UUID.fromString(response.id()).toString().equalsIgnoreCase(response.id())) {
          throw incompleteModel();
        }
      } catch (IllegalArgumentException e) {
        throw incompleteModel();
      }
    }
    return validateModels(response.intendedArchitectures().stream().map(model -> {
      if (model == null) {
        throw incompleteModel();
      }
      return new IntendedArchitecture(response.id(), model.qualifiers(), model.language(),
      model.groups(), model.constraints(), model.label(), model.description());
    }).toList());
  }

  private record PublicIntendedExport(String qualifiers, String language, List<IntendedArchitecture.Group> groups,
    List<IntendedArchitecture.Constraint> constraints, @Nullable String label, @Nullable String description) {
  }

  private record IntendedArchitectures(@Nullable String id, @Nullable List<PublicIntendedExport> intendedArchitectures) {
  }

}
