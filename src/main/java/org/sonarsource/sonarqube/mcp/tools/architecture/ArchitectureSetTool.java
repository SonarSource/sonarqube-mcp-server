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
package org.sonarsource.sonarqube.mcp.tools.architecture;

import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.IntendedArchitecture;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolAccessMode;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;
import org.sonarsource.sonarqube.mcp.tools.ToolDefinitionBuilder;
import org.sonarsource.sonarqube.mcp.tools.ToolParameters;

public class ArchitectureSetTool extends Tool {

  public static final String TOOL_NAME = "architecture_set";
  public static final String MODEL_PROPERTY = "model";
  public static final String DRY_RUN_PROPERTY = "dryRun";
  public static final String REVISION_PROPERTY = "revision";

  private static final String[] CLOUD_TARGETS = {ArchitectureGetTool.INTENDED, ArchitectureGetTool.DIRECTIVES};
  private static final String[] SERVER_TARGETS = {ArchitectureGetTool.INTENDED};

  private final ServerApiProvider serverApiProvider;
  private final boolean isSonarQubeCloud;
  private final CurrentGraphReads currentReads;
  @Nullable
  private final String configuredProjectKey;

  public ArchitectureSetTool(ServerApiProvider serverApiProvider, boolean isSonarQubeCloud, @Nullable String configuredProjectKey) {
    this(serverApiProvider, isSonarQubeCloud, configuredProjectKey, new CurrentGraphReads());
  }

  ArchitectureSetTool(ServerApiProvider serverApiProvider, boolean isSonarQubeCloud, @Nullable String configuredProjectKey, CurrentGraphReads currentReads) {
    super((isSonarQubeCloud ? ToolDefinitionBuilder.cloudBuilder() : ToolDefinitionBuilder.builder())
      .setName(TOOL_NAME)
      .setTitle("Set Architecture")
      .setDestructiveHint()
      .setDescription(isSonarQubeCloud
        ? "Replace intended architecture (omitted content is removed), or create, update or delete explicit project directives."
        : "Replace intended architecture (omitted content is removed).")
      .addProjectKeyProperty(ToolParameters.PROJECT_KEY, configuredProjectKey)
      .addRequiredEnumProperty("target", isSonarQubeCloud ? CLOUD_TARGETS : SERVER_TARGETS,
        isSonarQubeCloud ? "intended: replace model; directives: explicit CRUD operations." : "intended: replace model.")
      .addRequiredStringProperty(MODEL_PROPERTY, isSonarQubeCloud
        ? "Intended: full response or models array; an empty complete model deletes it. Directives: "
        + "{create: [directives], update: [{id, ...changed directive fields}], delete: [{id}]}; updates are flat objects. "
        + "Directive: type REMOVE_EDGE|RENAME_NODE|MOVE_NODE, ecosystem java|cs|js|ts|py, "
        + "graphType file_graph|namespace_graph, fromNodeKey/toNodeKey from architecture_get current. "
        + "toNodeKey for MOVE_NODE: existing destination container; for RENAME_NODE: new complete key. "
        + "graphPerspective defaults to default_perspective, optional description."
        : "Intended: full response or models array; an empty complete model deletes it.")
      .addRequiredBooleanProperty(DRY_RUN_PROPERTY, "true to only validate and list the changes, false to write them.")
      .addStringProperty(REVISION_PROPERTY, "Revision from architecture_get intended; defaults to the model response revision. "
        + "Required to clear the whole model; a mismatch prevents writing.")
      .build(),
      ToolCategory.ARCHITECTURE);
    this.currentReads = currentReads;
    this.serverApiProvider = serverApiProvider;
    this.isSonarQubeCloud = isSonarQubeCloud;
    this.configuredProjectKey = configuredProjectKey;
  }

  @Override
  public ToolAccessMode requiredAccessMode() {
    return ToolAccessMode.READ_WRITE;
  }

  @Override
  public Result execute(Arguments arguments) {
    if (arguments.toMap().containsKey("operation")) {
      throw new IllegalArgumentException("operation is not supported. Use the model schema for the selected target.");
    }
    var target = arguments.getEnumOrThrow("target", isSonarQubeCloud ? CLOUD_TARGETS : SERVER_TARGETS);
    var projectKey = arguments.getProjectKeyWithFallback(ToolParameters.PROJECT_KEY, configuredProjectKey);
    boolean dryRun = arguments.getBooleanOrThrow(DRY_RUN_PROPERTY);
    var model = arguments.getStringOrThrow(MODEL_PROPERTY);
    if (ArchitectureGetTool.DIRECTIVES.equals(target)) {
      if (arguments.toMap().get(REVISION_PROPERTY) != null) {
        throw new IllegalArgumentException("Revision is only used for intended architecture.");
      }
      var directives = ProjectDirectives.parse(model, projectKey);
      var serverApi = serverApiProvider.get(arguments.getOptionalString(ToolParameters.ORGANIZATION));
      if (!serverApi.isSonarQubeCloud()) {
        throw new IllegalArgumentException("SonarQube Server supports only target intended.");
      }
      var projectId = ArchitectureProjectContext.projectId(serverApi, projectKey);
      return Result.success(DirectiveResponses.of(ProjectDirectives.apply(serverApi.architectureApi(), projectKey, projectId, directives, dryRun)));
    }

    var next = IntendedModels.parse(model);
    IntendedModels.checkProject(model, projectKey);
    var revision = revision(arguments, model);
    var invalidReason = IntendedModels.invalidReason(next);
    if (invalidReason != null) {
      throw new IllegalArgumentException(invalidReason);
    }
    var serverApi = serverApiProvider.get(arguments.getOptionalString(ToolParameters.ORGANIZATION));
    var projectId = ArchitectureProjectContext.projectId(serverApi, projectKey);
    var api = serverApi.architectureApi();
    var storedState = api.getIntendedState(projectKey, projectId);
    var stored = storedState.models();
    checkRevision(projectKey, revision, stored);
    IntendedArchitectureReplacement.checkWritableModels(stored, next);
    var clearingStoredResource = storedState.id() != null && next.isEmpty();
    if (clearingStoredResource && !dryRun && revision == null) {
      throw new IllegalArgumentException("Clearing the whole intended architecture needs the revision from " + ArchitectureGetTool.TOOL_NAME
        + " with target intended. Run a dry run first and pass that revision. Nothing was changed.");
    }
    var current = stored;
    var diff = IntendedModels.diff(current, next);
    var warnings = new ArrayList<String>();
    warnings.add(ArchitectureApi.EXPORT_WARNING);
    if (clearingStoredResource) {
      warnings.add("The stored intended architecture resource will be deleted when this change is applied. "
        + "Applying this clear requires the revision from " + ArchitectureGetTool.TOOL_NAME + " response with target intended.");
    }
    warnings.add("The public API has no conditional write; concurrent edits can race the revision check.");
    if (revision == null) {
      warnings.add("No revision was supplied; changes since the original export cannot be detected. "
        + "Use the whole architecture_get response or supply revision.");
    }
    if (!diff.isEmpty()) {
      warnings.addAll(IntendedDiagnostics.inspect(serverApi, projectKey, next, currentReads));
    }
    if (dryRun) {
      return Result.success(new ArchitectureSetToolResponse(projectKey, "dry-run", diff, warnings));
    }
    if (diff.isEmpty() && !clearingStoredResource) {
      return Result.success(new ArchitectureSetToolResponse(projectKey, "unchanged", diff, warnings));
    }
    try {
      new IntendedArchitectureReplacement(api).replace(projectKey, projectId, storedState, next);
    } catch (IntendedArchitectureReplacement.IntendedRecoveryException failure) {
      return Result.failure(failure.callerMessage());
    }
    return Result.success(new ArchitectureSetToolResponse(projectKey, replacementStatus(next.isEmpty(), storedState.id()), diff, warnings));
  }

  private static void checkRevision(String projectKey, @Nullable String revision, List<IntendedArchitecture> stored) {
    if (revision != null && !revision.equals(IntendedRevision.of(stored))) {
      throw new IllegalStateException("The intended architecture of '" + projectKey + "' changed since revision " + revision + " was read, so nothing was changed. "
        + "Call " + ArchitectureGetTool.TOOL_NAME + " with target intended again, reapply the edits to that model and run a dry run again.");
    }
  }

  private static String replacementStatus(boolean clear, @Nullable String storedId) {
    if (clear) {
      return "cleared";
    }
    return storedId == null ? "created" : "replaced";
  }

  @Nullable
  private static String revision(Arguments arguments, String model) {
    var revision = IntendedModels.embeddedRevision(model);
    var argumentMap = arguments.toMap();
    if (argumentMap.containsKey(REVISION_PROPERTY)) {
      if (!(argumentMap.get(REVISION_PROPERTY) instanceof String explicitRevision) || explicitRevision.isBlank()) {
        throw new IllegalArgumentException("revision must be a nonblank string. Nothing was changed.");
      }
      if (revision != null && !revision.equals(explicitRevision)) {
        throw new IllegalArgumentException("The explicit revision differs from the model response revision. Nothing was changed.");
      }
      revision = explicitRevision;
    }
    return revision;
  }

}
