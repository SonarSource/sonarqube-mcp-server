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
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;
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

  private final ServerApiProvider serverApiProvider;
  @Nullable
  private final String configuredProjectKey;

  public ArchitectureSetTool(ServerApiProvider serverApiProvider, boolean isSonarQubeCloud, @Nullable String configuredProjectKey) {
    super((isSonarQubeCloud ? ToolDefinitionBuilder.cloudBuilder() : ToolDefinitionBuilder.builder())
      .setName(TOOL_NAME)
      .setTitle("Set Architecture")
      .setDestructiveHint()
      .setDescription("Replace intended architecture (omitted content is removed), or create, update or delete explicit project directives.")
      .addProjectKeyProperty(ToolParameters.PROJECT_KEY, configuredProjectKey)
      .addRequiredEnumProperty("target", new String[] {"intended", ArchitectureGetTool.DIRECTIVES}, "intended: replace model; directives: explicit CRUD operations.")
      .addRequiredStringProperty(MODEL_PROPERTY, "Intended: full response or models array. Directives: "
        + "{create: [directives], update: [{id, fields}], delete: [{id}]}; directive: type REMOVE_EDGE|RENAME_NODE|MOVE_NODE, ecosystem java|cs|js|ts|py, "
        + "graphType file_graph|namespace_graph, fromNodeKey/toNodeKey from architecture_get current (RENAME_NODE: full renamed key), "
        + "graphPerspective defaults to default_perspective, optional description.")
      .addRequiredBooleanProperty(DRY_RUN_PROPERTY, "true to only validate and list the changes, false to write them.")
      .addStringProperty(REVISION_PROPERTY, "Revision from architecture_get target intended; defaults to the model response revision. A mismatch prevents writing.")
      .build(),
      ToolCategory.ARCHITECTURE);
    this.serverApiProvider = serverApiProvider;
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
    var target = arguments.getEnumOrThrow("target", new String[] {"intended", ArchitectureGetTool.DIRECTIVES});
    var projectKey = arguments.getProjectKeyWithFallback(ToolParameters.PROJECT_KEY, configuredProjectKey);
    boolean dryRun = arguments.getBooleanOrThrow(DRY_RUN_PROPERTY);
    var model = arguments.getStringOrThrow(MODEL_PROPERTY);
    if (ArchitectureGetTool.DIRECTIVES.equals(target)) {
      if (arguments.toMap().get(REVISION_PROPERTY) != null) {
        throw new IllegalArgumentException("Revision is only used for intended architecture.");
      }
      var directives = ProjectDirectives.parse(model, projectKey);
      var serverApi = serverApiProvider.get(arguments.getOptionalString(ToolParameters.ORGANIZATION));
      var projectId = ArchitectureProjectContext.projectId(serverApi, projectKey);
      return Result.success(ProjectDirectives.apply(serverApi.architectureApi(), projectKey, projectId, directives, dryRun));
    }

    var next = IntendedModels.parse(model);
    var revision = revision(arguments, model);
    var invalidReason = IntendedModels.invalidReason(next);
    if (invalidReason != null) {
      throw new IllegalArgumentException(invalidReason);
    }
    var serverApi = serverApiProvider.get(arguments.getOptionalString(ToolParameters.ORGANIZATION));
    var projectId = ArchitectureProjectContext.projectId(serverApi, projectKey);
    var api = serverApi.architectureApi();
    var stored = api.getIntended(projectKey, projectId);
    if (revision != null && !revision.equals(IntendedRevision.of(stored))) {
      throw new IllegalStateException("The intended architecture of '" + projectKey + "' changed since revision " + revision + " was read, so nothing was changed. "
        + "Call " + ArchitectureGetTool.TOOL_NAME + " with target intended again, reapply the edits to that model and run a dry run again.");
    }
    IntendedArchitectureReplacement.checkWritableModels(stored, next);
    var current = stored;
    var diff = IntendedModels.diff(current, next);
    var warnings = new java.util.ArrayList<String>();
    warnings.add(ArchitectureApi.EXPORT_WARNING);
    warnings.add("The public API has no conditional write; concurrent edits can race the revision check.");
    if (revision == null) {
      warnings.add("No revision was supplied; changes since the original export cannot be detected. "
        + "Use the whole architecture_get response or supply revision.");
    }
    if (dryRun) {
      return Result.success(new ArchitectureSetToolResponse(projectKey, "dry-run", diff, warnings));
    }
    if (diff.isEmpty()) {
      return Result.success(new ArchitectureSetToolResponse(projectKey, "unchanged", diff, warnings));
    }
    try {
      new IntendedArchitectureReplacement(api).replace(projectKey, projectId, stored, next);
    } catch (IntendedArchitectureReplacement.IntendedRecoveryException failure) {
      return Result.failure(failure.callerMessage());
    }
    return Result.success(new ArchitectureSetToolResponse(projectKey, current.isEmpty() ? "created" : "replaced", diff, warnings));
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
