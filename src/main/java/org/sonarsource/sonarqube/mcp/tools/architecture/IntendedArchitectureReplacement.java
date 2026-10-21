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

import com.google.gson.Gson;
import jakarta.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.IntendedArchitecture;

/** Checks for concurrent changes and reconciles uncertain writes without automatic rollback. */
final class IntendedArchitectureReplacement {
  private final ArchitectureApi api;
  private final Gson gson = new Gson();

  IntendedArchitectureReplacement(ArchitectureApi api) {
    this.api = api;
  }

  /** Replaces all perspectives of the backed-up model; the public API provides no conditional write. */
  void replace(String projectKey, String projectId, List<IntendedArchitecture> previous, List<IntendedArchitecture> next) {
    checkWritableModels(previous, next);
    var actual = api.getIntended(projectKey, projectId);
    if (!sameResources(previous, actual)) {
      throw new IllegalStateException("The intended architecture changed before replacement. "
        + "Nothing was changed; read it again and repeat the dry run.");
    }
    try {
      var written = previous.isEmpty() ? api.createIntended(projectKey, projectId, next) : api.patchIntended(projectKey, previous.getFirst().id(), next);
      validateResourceIds(written);
      if (!sameModels(next, written) || (!previous.isEmpty() && !previous.getFirst().id().equals(written.getFirst().id()))) {
        throw new IllegalStateException("The public API write response did not identify the requested model. "
          + "Read the current state before retrying.");
      }
      if (!sameResources(written, api.getIntended(projectKey, projectId))) {
        throw new IllegalStateException("The intended architecture after the write differs from its response.");
      }
    } catch (RuntimeException failure) {
      reconcileFailure(projectKey, projectId, previous, next, failure);
    }
  }

  static void checkWritableModels(List<IntendedArchitecture> previous, List<IntendedArchitecture> next) {
    if (next.isEmpty() || next.size() > ArchitectureApi.MAX_PERSPECTIVES) {
      throw new IllegalArgumentException("Give between 1 and " + ArchitectureApi.MAX_PERSPECTIVES
        + " intended architecture perspectives. Nothing was changed.");
    }
    validateResourceIds(previous);
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

  private void reconcileFailure(String projectKey, String projectId, List<IntendedArchitecture> previous, List<IntendedArchitecture> next,
    RuntimeException failure) {
    final List<IntendedArchitecture> actual;
    try {
      actual = api.getIntended(projectKey, projectId);
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
    return new IntendedRecoveryException(message + ". Failure: " + cause.getMessage() + ". " + ArchitectureApi.EXPORT_WARNING,
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

  private record PreviousModels(List<IntendedArchitecture> models) {
  }
}
