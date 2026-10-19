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
import java.util.List;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;
import org.sonarsource.sonarqube.mcp.tools.Tool;
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
      .setDescription("Replace intended architecture through the public API; imported patterns and metadata cannot be preserved.")
      .addProjectKeyProperty(ToolParameters.PROJECT_KEY, configuredProjectKey)
      .addRequiredEnumProperty("target", new String[] {"intended"}, "intended: replace the model.")
      .addStringProperty(MODEL_PROPERTY, "Whole JSON response or models array from unfiltered architecture_get target intended; partial views are rejected.")
      .addRequiredBooleanProperty(DRY_RUN_PROPERTY, "true to only validate and list the changes, false to write them.")
      .addStringProperty(REVISION_PROPERTY, "Revision from architecture_get target intended; defaults to the model response revision. A mismatch prevents writing.")
      .build(),
      ToolCategory.ARCHITECTURE);
    this.serverApiProvider = serverApiProvider;
    this.configuredProjectKey = configuredProjectKey;
  }

  @Override
  public Result execute(Arguments arguments) {
    arguments.getEnumOrThrow("target", new String[] {"intended"});
    var projectKey = arguments.getProjectKeyWithFallback(ToolParameters.PROJECT_KEY, configuredProjectKey);
    var dryRun = arguments.getBooleanOrThrow(DRY_RUN_PROPERTY);
    var model = arguments.getStringOrThrow(MODEL_PROPERTY);
    var next = IntendedModels.parse(model);
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
    var invalidReason = IntendedModels.invalidReason(next);
    if (invalidReason != null) {
      throw new IllegalArgumentException(invalidReason);
    }
    var api = serverApiProvider.get(arguments.getOptionalString(ToolParameters.ORGANIZATION)).architectureApi();
    var stored = api.getIntended(projectKey);
    if (revision != null && !revision.equals(IntendedRevision.of(stored))) {
      throw new IllegalStateException("The intended architecture of '" + projectKey + "' changed since revision " + revision + " was read, so nothing was changed. "
        + "Call " + ArchitectureGetTool.TOOL_NAME + " with target intended again, reapply the edits to that model and run a dry run again.");
    }
    var current = stored;
    var diff = IntendedModels.diff(current, next);
    if (!diff.isEmpty()) {
      api.checkWritableModels(stored, next);
    }
    var warnings = revision == null
      ? List.of(ArchitectureApi.EXPORT_WARNING, "No revision was supplied; changes since the original export cannot be detected. Use the whole architecture_get response or supply revision.")
      : List.of(ArchitectureApi.EXPORT_WARNING);
    if (dryRun) {
      return Result.success(new ArchitectureSetToolResponse(projectKey, "dry-run", diff, warnings));
    }
    if (diff.isEmpty()) {
      return Result.success(new ArchitectureSetToolResponse(projectKey, "unchanged", diff, warnings));
    }
    try {
      api.replaceIntended(projectKey, stored, next);
    } catch (ArchitectureApi.IntendedRecoveryException failure) {
      return Result.failure(failure.callerMessage());
    }
    return Result.success(new ArchitectureSetToolResponse(projectKey, current.isEmpty() ? "created" : "replaced", diff, warnings));
  }

}
