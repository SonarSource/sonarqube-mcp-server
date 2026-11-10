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
import com.google.gson.GsonBuilder;
import jakarta.annotation.Nullable;
import java.net.HttpURLConnection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.apache.commons.lang3.Strings;
import org.apache.hc.core5.net.URIBuilder;
import org.sonarsource.sonarqube.mcp.http.HttpClient;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.UrlBuilder;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.IntendedArchitecture;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.ProjectDirective;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.ProjectFinding;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.ProjectFindingDeserializer;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.StructureGraph;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ClientErrorException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ForbiddenException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.NotFoundException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ServerApiException;

/** Public architecture HTTP requests and Cloud/Server routing. */
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
  private static final String LANGUAGE_PARAM = "language";
  private static final String INVALID_FINDINGS_PAGE = "Invalid public findings page.";
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

  private static final String API_RESPONSE_ERROR = "The Architecture API returned an unexpected response.";

  private final ServerApiHelper helper;
  private final Gson gson = new GsonBuilder().registerTypeAdapter(ProjectFinding.class, new ProjectFindingDeserializer()).create();

  public ArchitectureApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  private static IllegalStateException unexpectedResponse(String message) {
    return new IllegalStateException(message + " " + API_RESPONSE_ERROR);
  }

  /**
   * Returns an empty list when the project has no current architecture for the language yet.
   */
  public List<StructureGraph> getStructure(String projectKey, String organizationId, String branchId, String language) {
    var path = new UrlBuilder(CURRENT_ARCHITECTURES_PATH)
      .addParam("organizationId", organizationId)
      .addParam("branchId", branchId)
      .addParam(LANGUAGE_PARAM, language)
      .build();
    CurrentArchitectures response;
    try {
      response = read(projectKey, () -> fromJson(get(path), CurrentArchitectures.class));
    } catch (ClientErrorException e) {
      var message = e.getMessage();
      if (helper.isSonarQubeCloud() || e.status() != HttpURLConnection.HTTP_BAD_REQUEST || message == null
        || !message.contains("Method parameter 'language'") || !message.contains("Failed to convert") || !message.contains(".PublicEcosystem")) {
        throw e;
      }
      throw new IllegalStateException("A known SonarQube Server issue currently blocks current architecture reads (HTTP 400).", e);
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
    if (response.intendedArchitectures().size() > 1) {
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
          throw unexpectedResponse("The public intended architecture DELETE did not return HTTP 204. Inspect the current model before retrying.");
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
    if (result.perspectives().isEmpty()) {
      throw unexpectedResponse("The public architecture API did not return the written intended perspectives.");
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
      if (model.type() == null || model.language() == null || model.groups() == null || model.constraints() == null) {
        throw incompleteModel();
      }
      validateGroups(model.groups(), new HashSet<>());
      for (var constraint : model.constraints()) {
        if (constraint == null || constraint.from() == null || constraint.to() == null) {
          throw incompleteModel();
        }
      }
    }
    return models;
  }

  private static void validateGroups(List<IntendedArchitecture.Group> groups, HashSet<String> labels) {
    for (var group : groups) {
      if (group == null || group.label() == null || group.label().isBlank() || group.label().contains("/")
        || !labels.add(group.label()) || (group.patterns() != null && group.patterns().contains(null))) {
        throw incompleteModel();
      }
      if (group.groups() != null) {
        validateGroups(group.groups(), labels);
      }
    }
  }

  private static IllegalStateException incompleteModel() {
    return unexpectedResponse("The public architecture API returned an incomplete intended architecture. No complete model was read.");
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
      var gatewayHint = reason(e).contains("Invalid key=value pair") && reason(e).contains("Authorization header")
        ? ". This gateway response can also mean the Architecture endpoint is unavailable on the deployment; check endpoint availability and gateway routing" : "";
      var failure = new ForbiddenException("a refusal to read the architecture of project '" + projectKey + "': " + reason(e)
        + ". " + READ_HINT + ". " + PLAN_HINT + gatewayHint);
      failure.initCause(e);
      throw failure;
    }
  }

  private <T> T readProjectResource(String projectKey, Supplier<T> request) {
    try {
      return read(projectKey, request);
    } catch (NotFoundException e) {
      if (helper.isSonarQubeCloud()) {
        throw e;
      }
      var failure = new NotFoundException(reason(e) + ". Check the project and Browse permission, public Architecture endpoint availability and gateway routing");
      failure.initCause(e);
      throw failure;
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
    if (response.page().pageIndex() != pageIndex || response.page().pageSize() != pageSize || response.page().total() < 0
      || response.projectDirectives().size() > pageSize) {
      throw new IllegalStateException("Invalid public directives page; no complete page was read.");
    }
    var ids = new HashSet<String>();
    for (var directive : response.projectDirectives()) {
      validateStoredDirective(directive);
      if (!ids.add(directive.id().toLowerCase(Locale.ROOT))) {
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
        directive.toNodeKey(), directive.graphPerspective() == null ? updated.identity().perspective() : directive.identity().perspective());
      if (!updated.identity().equals(expectedIdentity)
        || (directive.description() != null && !Objects.equals(updated.description(), directive.description()))) {
        throw new IllegalStateException("The directive update response did not confirm the requested directive. Inspect it before retrying.");
      }
      return updated;
    });
  }

  public void deleteDirective(String projectKey, String id) {
    write(projectKey, () -> {
      try (var response = delete(directivePath(id))) {
        if (response.code() != HttpURLConnection.HTTP_NO_CONTENT) {
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
      throw new IllegalStateException(invalid.getMessage() + " " + API_RESPONSE_ERROR, invalid);
    }
  }

  private static void validateDirectiveResource(ProjectDirective directive, String projectId, String id) {
    validateStoredDirective(directive);
    if (!projectId.equals(directive.projectId()) || !id.equalsIgnoreCase(directive.id())) {
      throw new IllegalStateException("The public directive response returned a different id or project.");
    }
  }

  public record DirectivePage(List<ProjectDirective> projectDirectives, DirectivePageInfo page) {
    public DirectivePage {
      projectDirectives = List.copyOf(projectDirectives);
      Objects.requireNonNull(page);
    }
  }

  public FindingPage getFindingPage(String projectKey, String organizationId, String branchId, FindingFilters filters, int pageIndex, int pageSize) {
    validateFindingPage(pageIndex, pageSize);
    var path = new UrlBuilder(PROJECT_FINDINGS_PATH).addParam("organizationId", organizationId).addParam("branchId", branchId)
      .addParam("category", filters.category()).addParam("type", filters.type()).addParam(LANGUAGE_PARAM, filters.language())
      .addParam(PAGE_INDEX_PARAM, Integer.toString(pageIndex)).addParam(PAGE_SIZE_PARAM, Integer.toString(pageSize)).build();
    var page = read(projectKey, () -> fromJson(get(path), FindingPageResponse.class));
    if (!Integer.valueOf(pageIndex).equals(page.page().pageIndex()) || !Integer.valueOf(pageSize).equals(page.page().pageSize())
      || page.page().total() < 0 || page.findings().size() > pageSize) {
      throw new IllegalStateException(INVALID_FINDINGS_PAGE);
    }
    if (page.findings().stream().anyMatch(finding -> !filters.matches(finding))) {
      throw unexpectedResponse("The public findings response does not match the requested filters.");
    }
    var findings = page.findings().stream().filter(ProjectFinding::supported).toList();
    var skipped = page.findings().size() - findings.size();
    return new FindingPage(findings, page.page(), skipped == 0 ? null : skipped);
  }

  private record FindingPageResponse(List<ProjectFinding> findings, DirectivePageInfo page) {
    private FindingPageResponse {
      findings = List.copyOf(findings);
      Objects.requireNonNull(page);
    }
  }

  public static void validateFindingPage(int pageIndex, int pageSize) {
    if (pageIndex < 1 || pageSize < 0 || pageSize > 1000) {
      throw new IllegalArgumentException("Page must be >= 1 and page size must be between 0 and 1000.");
    }
  }

  public record FindingFilters(@Nullable String category, @Nullable String type, @Nullable String language) {
    public FindingFilters {
      if (type != null && category != null && !ProjectFinding.categoryOf(type).equals(category)) {
        throw new IllegalArgumentException("Finding type " + type + " belongs to category " + ProjectFinding.categoryOf(type) + ", not " + category + ".");
      }
    }

    private boolean matches(ProjectFinding finding) {
      return (category == null || category.equals(finding.category())) && (type == null || type.equals(finding.type()))
        && (language == null || language.equals(finding.language()));
    }
  }

  public record FindingPage(List<ProjectFinding> findings, DirectivePageInfo page, @Nullable Integer skippedFindings) {
  }

  public record DirectivePageInfo(Integer pageIndex, Integer pageSize, Integer total) {
    public DirectivePageInfo {
      Objects.requireNonNull(pageIndex);
      Objects.requireNonNull(pageSize);
      Objects.requireNonNull(total);
    }
  }

  private HttpClient.Response delete(String path) {
    return helper.isSonarQubeCloud() ? helper.deleteApiSubdomain(path) : helper.delete(SERVER_API_PREFIX + path);
  }

  private HttpClient.Response get(String path) {
    return helper.isSonarQubeCloud() ? helper.getApiSubdomain(path) : helper.get(SERVER_API_PREFIX + path);
  }

  private <T> T fromJson(HttpClient.Response toBeClosed, Class<T> type) {
    try (var response = toBeClosed) {
      return Objects.requireNonNull(gson.fromJson(response.bodyAsString(), type));
    } catch (RuntimeException invalid) {
      throw new IllegalStateException("Invalid public architecture response. " + API_RESPONSE_ERROR, invalid);
    }
  }

  private record CurrentArchitectures(List<StructureGraph> currentArchitectures) {
    private CurrentArchitectures {
      currentArchitectures = List.copyOf(currentArchitectures);
    }
  }

  private static IntendedState exportedResource(IntendedResource response) {
    try {
      if (response.id() == null || !UUID.fromString(response.id()).toString().equalsIgnoreCase(response.id())
        || response.perspectives().size() > MAX_PERSPECTIVES) {
        throw incompleteModel();
      }
    } catch (IllegalArgumentException invalid) {
      throw incompleteModel();
    }
    var identities = new HashSet<String>();
    var models = validateModels(response.perspectives().stream().map(model -> {
      if (!identities.add(model.qualifiers() + "/" + model.language())) {
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

  private record IntendedResource(@Nullable String id, List<PublicIntendedExport> perspectives) {
    private IntendedResource {
      perspectives = List.copyOf(perspectives);
    }
  }

  private record IntendedArchitectures(List<IntendedResource> intendedArchitectures) {
    private IntendedArchitectures {
      intendedArchitectures = List.copyOf(intendedArchitectures);
    }
  }

}
