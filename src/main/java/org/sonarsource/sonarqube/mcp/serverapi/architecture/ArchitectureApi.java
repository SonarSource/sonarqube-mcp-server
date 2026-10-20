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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.apache.commons.lang3.Strings;
import org.apache.hc.core5.net.URIBuilder;
import org.sonarsource.sonarqube.mcp.http.HttpClient;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.UrlBuilder;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.IntendedArchitecture;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.StructureGraph;
import org.sonarsource.sonarqube.mcp.serverapi.branches.ProjectBranchesApi;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ForbiddenException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ServerApiException;

/**
 * Every architecture endpoint, query parameter, id lookup and DTO mapping lives in this class, so moving to another
 * version of the architecture API changes this file only.
 * <p>
 * Only the public architecture API is used. SonarQube Cloud serves it on the API subdomain; SonarQube Server serves it
 * under {@code /api/v2}.
 */
public class ArchitectureApi {

  public static final String CURRENT_ARCHITECTURES_PATH = "/architecture/current-architectures";
  public static final String INTENDED_ARCHITECTURES_PATH = "/architecture/intended-architectures";
  public static final String SERVER_API_PREFIX = "/api/v2";
  /** SonarQube Server has no organizations; its architecture endpoints expect this fixed id. */
  public static final String SERVER_ORGANIZATION_ID = "00000000-0000-4000-0000-000000000000";
  public static final int MAX_PERSPECTIVES = 50;
  public static final List<String> LANGUAGES = List.of("java", "cs", "js", "ts", "py");

  /** Public exports flatten organization pattern imports and omit metadata. */
  public static final String EXPORT_WARNING = "The public API cannot identify imported organization patterns or preserve import links and metadata such as descriptions. "
    + "Writing an exported model turns imported patterns into copies and may lose metadata.";

  private static final String READ_HINT = "Check project permissions, feature availability and the public API gateway routing; "
    + "HTTP 403 alone does not establish which check failed";
  private static final String PLAN_HINT = "Architecture is available on SonarQube Cloud Team and Enterprise plans, "
    + "and on SonarQube Server editions that include it";

  private final ServerApiHelper helper;
  private final Supplier<String> organizationUuid;
  private final ProjectBranchesApi projectBranchesApi;
  private final Gson gson = new Gson();
  private final Map<String, String> mainBranchIdByProjectKey = new HashMap<>();
  @Nullable
  private String organizationId;

  /**
   * {@code organizationUuid} gives the uuid of the selected SonarQube Cloud organization, or null when none is selected or it cannot be resolved.
   */
  public ArchitectureApi(ServerApiHelper helper, Supplier<String> organizationUuid) {
    this.helper = helper;
    this.organizationUuid = organizationUuid;
    this.projectBranchesApi = new ProjectBranchesApi(helper);
  }

  /**
   * Returns an empty list when the project has no current architecture for the language yet.
   */
  public List<StructureGraph> getStructure(String projectKey, String language) {
    var path = new UrlBuilder(CURRENT_ARCHITECTURES_PATH)
      .addParam("organizationId", organizationId())
      .addParam("branchId", mainBranchId(projectKey))
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
  public List<IntendedArchitecture> getIntended(String projectKey) {
    var path = new UrlBuilder(INTENDED_ARCHITECTURES_PATH)
      .addParam("projectId", projectId(projectKey))
      .build();
    var response = read(projectKey, () -> fromJson(get(path), IntendedArchitectures.class));
    if (response == null || response.intendedArchitectures() == null) {
      throw new IllegalStateException("The public architecture API did not return intendedArchitectures. No complete model was read.");
    }
    return response.intendedArchitectures();
  }

  /** Replaces all perspectives of the backed-up model; the public API provides no conditional write. */
  public void replaceIntended(String projectKey, List<IntendedArchitecture> previous, List<IntendedArchitecture> next) {
    checkWritableModels(previous, next);
    var actual = getIntended(projectKey);
    if (!sameResources(previous, actual)) {
      throw new IllegalStateException("The intended architecture changed before replacement. "
        + "Nothing was changed; read it again and repeat the dry run.");
    }
    try {
      var written = previous.isEmpty() ? createIntended(projectKey, next) : patchIntended(projectKey, previous.getFirst().id(), next);
      validateResourceIds(written);
      if (!sameModels(next, written) || (!previous.isEmpty() && !previous.getFirst().id().equals(written.getFirst().id()))) {
        throw new IllegalStateException("The public API write response did not identify the requested model. "
          + "Read the current state before retrying.");
      }
      if (!sameResources(written, getIntended(projectKey))) {
        throw new IllegalStateException("The intended architecture after the write differs from its response.");
      }
    } catch (RuntimeException failure) {
      reconcileFailure(projectKey, previous, next, failure);
    }
  }

  public void checkWritableModels(List<IntendedArchitecture> previous, List<IntendedArchitecture> next) {
    if (next.isEmpty() || next.size() > MAX_PERSPECTIVES) {
      throw new IllegalArgumentException("Give between 1 and " + MAX_PERSPECTIVES
        + " intended architecture perspectives. Nothing was changed.");
    }
    validateResourceIds(previous);
  }

  private List<IntendedArchitecture> createIntended(String projectKey, List<IntendedArchitecture> models) {
    var body = gson.toJson(new CreateModels(projectId(projectKey), publicModels(models)));
    return write(projectKey, () -> writeModels(INTENDED_ARCHITECTURES_PATH, body, false));
  }

  private List<IntendedArchitecture> patchIntended(String projectKey, String id, List<IntendedArchitecture> models) {
    var path = new URIBuilder().setPath(INTENDED_ARCHITECTURES_PATH).appendPathSegments(id).toString();
    var body = gson.toJson(new PatchModels(publicModels(models)));
    return write(projectKey, () -> writeModels(path, body, true));
  }

  private static List<PublicIntendedModel> publicModels(List<IntendedArchitecture> models) {
    return models.stream().map(model -> new PublicIntendedModel(model.type(), model.language(),
      model.groups() == null ? List.of() : model.groups(), model.constraints() == null ? List.of() : model.constraints())).toList();
  }

  private static void validateResourceIds(List<IntendedArchitecture> models) {
    String modelId = null;
    for (var model : models) {
      try {
        if (model == null || model.id() == null || !UUID.fromString(model.id()).toString().equalsIgnoreCase(model.id())
          || (modelId != null && !modelId.equals(model.id()))) {
          throw new IllegalArgumentException();
        }
        modelId = model.id();
      } catch (IllegalArgumentException invalid) {
        throw new IllegalStateException("The public intended architecture response has missing, invalid or inconsistent model ids. "
          + "No unknown resource will be changed.");
      }
    }
  }

  private boolean sameResources(List<IntendedArchitecture> left, List<IntendedArchitecture> right) {
    return sameModels(left, right) && left.stream().map(IntendedArchitecture::id).sorted(Comparator.nullsFirst(Comparator.naturalOrder())).toList()
      .equals(right.stream().map(IntendedArchitecture::id).sorted(Comparator.nullsFirst(Comparator.naturalOrder())).toList());
  }

  private void reconcileFailure(String projectKey, List<IntendedArchitecture> previous, List<IntendedArchitecture> next,
    RuntimeException failure) {
    final List<IntendedArchitecture> actual;
    try {
      actual = getIntended(projectKey);
      validateResourceIds(actual);
    } catch (RuntimeException readFailure) {
      throw manualRecovery(previous, "The write failed and its resulting state could not be read. No automatic rollback was attempted", failure);
    }
    if (sameModels(next, actual) && (previous.isEmpty() || previous.getFirst().id().equals(actual.getFirst().id()))) {
      return;
    }
    throw manualRecovery(previous, "The write failed and the requested model could not be verified. "
      + "No automatic rollback was attempted; read the current state before retrying", failure);
  }

  private IntendedRecoveryException manualRecovery(List<IntendedArchitecture> previous, String message, RuntimeException cause) {
    return new IntendedRecoveryException(message + ". Failure: " + cause.getMessage() + ". " + EXPORT_WARNING,
      gson.toJson(new PreviousModels(previous)));
  }

  /** Keeps project exports out of exception messages, stack traces and server logs. */
  public static final class IntendedRecoveryException extends RuntimeException {
    private final String exportedModel;

    private IntendedRecoveryException(String message, String exportedModel) {
      super(message);
      this.exportedModel = exportedModel;
    }

    public String callerMessage() {
      return getMessage() + " Previous exported model for manual recovery: " + exportedModel;
    }
  }

  private boolean sameModels(List<IntendedArchitecture> left, List<IntendedArchitecture> right) {
    return normalizedModels(left).equals(normalizedModels(right));
  }

  private List<String> normalizedModels(List<IntendedArchitecture> models) {
    return models.stream().map(model -> gson.toJson(new IntendedArchitecture(model.type(), model.language(), normalizedGroups(model.groups()),
      model.constraints() == null ? List.of() : model.constraints().stream().sorted(Comparator.comparing(gson::toJson)).toList())))
      .sorted().toList();
  }

  private static List<IntendedArchitecture.Group> normalizedGroups(@Nullable List<IntendedArchitecture.Group> groups) {
    if (groups == null) {
      return List.of();
    }
    return groups.stream().map(group -> new IntendedArchitecture.Group(group.label(),
      group.patterns() == null ? List.of() : group.patterns().stream().sorted().toList(), normalizedGroups(group.groups()),
      Boolean.TRUE.equals(group.isInterface()), Boolean.TRUE.equals(group.interfaceAccess()))).toList();
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
      return result.intendedArchitectures();
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

  private record CreateModels(String projectId, List<PublicIntendedModel> intendedArchitectures) {
  }

  private record PatchModels(List<PublicIntendedModel> intendedArchitectures) {
  }

  private record PublicIntendedModel(@Nullable String type, @Nullable String language, List<IntendedArchitecture.Group> groups,
    List<IntendedArchitecture.Constraint> constraints) {
  }

  private record PreviousModels(List<IntendedArchitecture> models) {
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

  private String organizationId() {
    if (organizationId == null) {
      organizationId = helper.isSonarQubeCloud() ? cloudOrganizationId() : SERVER_ORGANIZATION_ID;
    }
    return organizationId;
  }

  private String cloudOrganizationId() {
    var uuid = organizationUuid.get();
    if (uuid == null) {
      throw new IllegalStateException("Could not resolve the id of organization '" + helper.getOrganization()
        + "'. Check the organization key with list_organizations, and the token.");
    }
    return uuid;
  }

  private String mainBranchId(String projectKey) {
    return mainBranchIdByProjectKey.computeIfAbsent(projectKey, key -> projectBranchesApi.getProjectId(key)
      .orElseThrow(() -> new IllegalStateException("Project '" + key + "' has no main branch. Use search_my_sonarqube_projects to check the project key.")));
  }

  /**
   * SonarQube Cloud keys architecture data by the main branch id; SonarQube Server keys it by the project uuid.
   */
  private String projectId(String projectKey) {
    if (helper.isSonarQubeCloud()) {
      return mainBranchId(projectKey);
    }
    throw new IllegalStateException("Intended architecture on SonarQube Server requires a project UUID, but no supported public API resolves it from a project key. "
      + "Nothing was read or changed. Waiting for public project ID resolution.");
  }

  private record CurrentArchitectures(@Nullable List<StructureGraph> currentArchitectures) {
  }

  private record IntendedArchitectures(@Nullable List<IntendedArchitecture> intendedArchitectures) {
  }

}
