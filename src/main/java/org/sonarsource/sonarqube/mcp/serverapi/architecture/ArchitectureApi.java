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
import com.google.gson.JsonParseException;
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
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.ProjectFinding;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.StructureGraph;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ForbiddenException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.NotFoundException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ServerApiException;

/** Public architecture HTTP requests, response validation and Cloud/Server routing. */
public class ArchitectureApi {

  public static final String CURRENT_ARCHITECTURES_PATH = "/architecture/current-architectures";
  public static final String INTENDED_ARCHITECTURES_PATH = "/architecture/project-intended-architectures";
  public static final String PROJECT_DIRECTIVES_PATH = "/architecture/project-directives";
  public static final String PROJECT_FINDINGS_PATH = "/architecture/project-findings";
  public static final List<String> FINDING_CATEGORIES = List.of("deviation", "flaw", "smell");
  public static final List<String> FINDING_TYPES = List.of("structuralDeviation", "relationshipDeviation", "tangle", "oversizedComponent", "weakTangle", "splitResponsibility");
  public static final String SERVER_API_PREFIX = "/api/v2";
  private static final String PROJECT_ID_PARAM = "projectId";
  private static final String PAGE_INDEX_PARAM = "pageIndex";
  private static final String PAGE_SIZE_PARAM = "pageSize";
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

  private static final String COMPATIBILITY_HINT = "The response does not match this MCP server's supported public Architecture API contract. "
    + "Check that the MCP server and the connected SonarQube deployment support the same Architecture API.";

  private final ServerApiHelper helper;
  private final Gson gson = new Gson();

  public ArchitectureApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  private static IllegalStateException incompatibleResponse(String message) {
    return new IllegalStateException(message + " " + COMPATIBILITY_HINT);
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
      throw incompatibleResponse("The public architecture API did not return currentArchitectures. No complete graph was read.");
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
    var path = new UrlBuilder(INTENDED_ARCHITECTURES_PATH).addParam(PROJECT_ID_PARAM, projectId).build();
    var response = readProjectResource(projectKey, () -> fromJson(get(path), IntendedArchitectures.class));
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

  private <T> T readProjectResource(String projectKey, Supplier<T> request) {
    try {
      return read(projectKey, request);
    } catch (NotFoundException e) {
      if (helper.isSonarQubeCloud()) {
        throw e;
      }
      throw new NotFoundException(reason(e) + ". Check the project or directive id and Browse permission. "
        + "On older Server versions, use a Server build that exposes the public Architecture APIs.");
    }
  }

  private static String reason(ServerApiException e) {
    return Strings.CS.removeStart(e.getMessage(), ServerApiException.MESSAGE_PREFIX);
  }

  public DirectivePage getDirectivePage(String projectKey, String projectId, int pageIndex, int pageSize) {
    return readProjectResource(projectKey, () -> readDirectivePage(projectId, pageIndex, pageSize));
  }

  private DirectivePage readDirectivePage(String projectId, int pageIndex, int pageSize) {
    if (pageIndex < 1 || pageSize < 1 || pageSize > 1000) {
      throw new IllegalArgumentException("Directive page must be >= 1 and page size must be between 1 and 1000.");
    }
    var path = new UrlBuilder(PROJECT_DIRECTIVES_PATH).addParam(PROJECT_ID_PARAM, projectId)
      .addParam(PAGE_INDEX_PARAM, Integer.toString(pageIndex)).addParam(PAGE_SIZE_PARAM, Integer.toString(pageSize)).build();
    var response = fromJson(get(path), DirectivePage.class);
    if (response == null || response.projectDirectives() == null || response.page() == null
      || response.page().pageIndex() == null || response.page().pageSize() == null || response.page().total() == null) {
      throw incompatibleResponse("Invalid public directives page; no complete page was read.");
    }
    if (response.page().pageIndex() != pageIndex || response.page().pageSize() != pageSize || response.page().total() < 0
      || response.projectDirectives().size() > pageSize) {
      throw new IllegalStateException("Invalid public directives page; no complete page was read.");
    }
    var ids = new java.util.HashSet<String>();
    for (var directive : response.projectDirectives()) {
      if (directive == null) {
        throw incompatibleResponse("The public directives response contains an incomplete directive.");
      }
      validateStoredDirective(directive);
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
      validateStoredDirective(response);
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
    return readProjectResource(projectKey, () -> {
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
      try (var response = helper.isSonarQubeCloud() ? helper.deleteApiSubdomain(directivePath(id))
        : helper.delete(SERVER_API_PREFIX + directivePath(id))) {
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

  private static void validateStoredDirective(ProjectDirective directive) {
    try {
      directive.validateStored();
    } catch (IllegalArgumentException invalid) {
      throw new IllegalStateException(invalid.getMessage() + " " + COMPATIBILITY_HINT, invalid);
    }
  }

  private static void validateDirectiveResource(@Nullable ProjectDirective directive, String projectId, String id) {
    if (directive == null) {
      throw new IllegalStateException("The public directive response is empty.");
    }
    validateStoredDirective(directive);
    if (!projectId.equals(directive.projectId()) || !id.equalsIgnoreCase(directive.id())) {
      throw new IllegalStateException("The public directive response returned a different id or project.");
    }
  }

  public record DirectivePage(List<ProjectDirective> projectDirectives, DirectivePageInfo page) {
  }

  public FindingPage getFindingPage(String projectKey, String organizationId, String branchId, FindingFilters filters, int pageIndex, int pageSize) {
    validateFindingPage(pageIndex, pageSize);
    var path = new UrlBuilder(PROJECT_FINDINGS_PATH).addParam("organizationId", organizationId).addParam("branchId", branchId)
      .addParam("category", filters.category()).addParam("type", filters.type()).addParam("language", filters.language())
      .addParam(PAGE_INDEX_PARAM, Integer.toString(pageIndex)).addParam(PAGE_SIZE_PARAM, Integer.toString(pageSize)).build();
    var response = read(projectKey, () -> findingsJson(get(path)));
    if (response == null || response.findings() == null || response.page() == null) {
      throw incompatibleResponse("Invalid public findings page.");
    }
    if (!Integer.valueOf(pageIndex).equals(response.page().pageIndex()) || !Integer.valueOf(pageSize).equals(response.page().pageSize())
      || response.page().total() == null || response.page().total() < 0 || response.findings().size() > pageSize) {
      throw new IllegalStateException("Invalid public findings page.");
    }
    if (response.findings().stream().anyMatch(finding -> !filters.matches(finding))) {
      throw new IllegalStateException("The public findings response does not match the requested filters.");
    }
    return response;
  }

  @Nullable
  private FindingPage findingsJson(HttpClient.Response response) {
    var page = fromJson(response, RawFindingPage.class);
    return page == null || page.findings() == null ? null
      : new FindingPage(page.findings().stream().map(ArchitectureApi::parseFinding).toList(), findingPageInfo(page.page()));
  }

  private static ProjectFinding parseFinding(com.google.gson.JsonElement value) {
    try {
      return ProjectFinding.parse(value);
    } catch (IllegalStateException invalid) {
      throw new IllegalStateException(invalid.getMessage() + " " + COMPATIBILITY_HINT, invalid);
    }
  }

  private static DirectivePageInfo findingPageInfo(@Nullable com.google.gson.JsonObject page) {
    if (page == null) {
      throw incompatibleResponse("Invalid public findings page.");
    }
    return new DirectivePageInfo(findingPageNumber(page, PAGE_INDEX_PARAM), findingPageNumber(page, PAGE_SIZE_PARAM), findingPageNumber(page, "total"));
  }

  private static int findingPageNumber(com.google.gson.JsonObject page, String name) {
    var value = page.get(name);
    try {
      if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
        throw incompatibleResponse("Invalid public findings page number.");
      }
      return value.getAsBigDecimal().intValueExact();
    } catch (ArithmeticException | NumberFormatException e) {
      throw new IllegalStateException("Invalid public findings page number. " + COMPATIBILITY_HINT, e);
    }
  }

  private record RawFindingPage(List<com.google.gson.JsonElement> findings, com.google.gson.JsonObject page) {
  }

  public static void validateFindingPage(int pageIndex, int pageSize) {
    if (pageIndex < 1 || pageSize < 0 || pageSize > 1000) {
      throw new IllegalArgumentException("Page must be >= 1 and page size must be between 0 and 1000.");
    }
  }

  public record FindingFilters(@Nullable String category, @Nullable String type, @Nullable String language) {
    private boolean matches(ProjectFinding finding) {
      return (category == null || category.equals(finding.category())) && (type == null || type.equals(finding.type()))
        && (language == null || language.equals(finding.language()));
    }
  }

  public record FindingPage(List<ProjectFinding> findings, DirectivePageInfo page) {
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
    } catch (JsonParseException invalid) {
      throw new IllegalStateException("Invalid public architecture response. " + COMPATIBILITY_HINT, invalid);
    }
  }

  private record CurrentArchitectures(@Nullable List<StructureGraph> currentArchitectures) {
  }

  private static IntendedState exportedResource(IntendedResource response) {
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
