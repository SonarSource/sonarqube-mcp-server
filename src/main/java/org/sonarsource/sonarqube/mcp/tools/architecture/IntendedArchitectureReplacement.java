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
import org.sonarsource.sonarqube.mcp.serverapi.exception.ClientErrorException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ForbiddenException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.NotFoundException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.UnauthorizedException;

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
    final List<IntendedArchitecture> written;
    try {
      written = previous.isEmpty() ? api.createIntended(projectKey, projectId, next) : api.patchIntended(projectKey, previous.getFirst().id(), next);
    } catch (RuntimeException failure) {
      if (isDefiniteRejection(failure)) {
        throw failure;
      }
      reconcileFailure(projectKey, projectId, previous, next, failure);
      return;
    }
    try {
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

  private static boolean isDefiniteRejection(RuntimeException failure) {
    return (failure instanceof ClientErrorException clientError && clientError.status() != java.net.HttpURLConnection.HTTP_CLIENT_TIMEOUT)
      || failure instanceof ForbiddenException || failure instanceof NotFoundException || failure instanceof UnauthorizedException;
  }

  static void checkWritableModels(List<IntendedArchitecture> previous, List<IntendedArchitecture> next) {
    if (next.isEmpty() || next.size() > ArchitectureApi.MAX_PERSPECTIVES) {
      throw new IllegalArgumentException("Give between 1 and " + ArchitectureApi.MAX_PERSPECTIVES
        + " intended architecture perspectives. Nothing was changed.");
    }
    validateResourceIds(previous);
    for (var model : next) {
      var before = previous.stream().filter(candidate -> candidate.language().equals(model.language()) && candidate.type().equals(model.type()))
        .findFirst().orElse(null);
      checkImportedGroups(before == null ? List.of() : before.groups(), model.groups());
    }
  }

  private static void checkImportedGroups(List<IntendedArchitecture.Group> previous, List<IntendedArchitecture.Group> next) {
    for (var group : next) {
      var before = previous.stream().filter(candidate -> candidate.label().equals(group.label())).findFirst().orElse(null);
      if (before != null && group.patternId() != null && group.patternId().equalsIgnoreCase(before.patternId())
        && group.groups() != null && !sameExpansion(before.groups(), group.groups())) {
        throw new IllegalArgumentException("Imported pattern contents cannot be edited here. Keep the exported groups unchanged or change patternId. Nothing was changed.");
      }
      if (group.patternId() == null) {
        checkImportedGroups(before == null || before.groups() == null ? List.of() : before.groups(), group.groups() == null ? List.of() : group.groups());
      }
    }
  }

  private static boolean sameExpansion(@Nullable List<IntendedArchitecture.Group> left, @Nullable List<IntendedArchitecture.Group> right) {
    return IntendedRevision.of(List.of(new IntendedArchitecture("namespace", "java", left == null ? List.of() : left, List.of())))
      .equals(IntendedRevision.of(List.of(new IntendedArchitecture("namespace", "java", right == null ? List.of() : right, List.of()))));
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

  private static boolean sameResources(List<IntendedArchitecture> left, List<IntendedArchitecture> right) {
    return IntendedRevision.of(left).equals(IntendedRevision.of(right))
      && left.stream().map(IntendedArchitecture::id).sorted(Comparator.nullsFirst(Comparator.naturalOrder())).toList()
      .equals(right.stream().map(IntendedArchitecture::id).sorted(Comparator.nullsFirst(Comparator.naturalOrder())).toList());
  }

  private void reconcileFailure(String projectKey, String projectId, List<IntendedArchitecture> previous, List<IntendedArchitecture> next,
    RuntimeException failure) {
    final List<IntendedArchitecture> actual;
    try {
      actual = api.getIntended(projectKey, projectId);
      validateResourceIds(actual);
    } catch (RuntimeException readFailure) {
      throw manualRecovery(previous, "The write could not be confirmed and its resulting state could not be read. No automatic rollback was attempted", failure);
    }
    if (sameModels(next, actual) && (previous.isEmpty() || previous.getFirst().id().equals(actual.getFirst().id()))) {
      return;
    }
    throw manualRecovery(previous, "The write could not be confirmed and the requested model could not be verified. "
      + "No automatic rollback was attempted; read the current state before retrying", failure);
  }

  private IntendedRecoveryException manualRecovery(List<IntendedArchitecture> previous, String message, RuntimeException cause) {
    return new IntendedRecoveryException(message + ". Failure: " + cause.getMessage() + ". " + ArchitectureApi.EXPORT_WARNING,
      gson.toJson(new PreviousModels(previous.stream().map(model -> new IntendedArchitecture(model.id(), model.type(), model.language(),
        recoveryGroups(model.groups()), model.constraints(), model.label(), model.description())).toList())));
  }

  private static List<IntendedArchitecture.Group> recoveryGroups(List<IntendedArchitecture.Group> groups) {
    return groups.stream().map(group -> new IntendedArchitecture.Group(group.label(), group.patterns(),
      group.patternId() != null || group.groups() == null ? null : recoveryGroups(group.groups()),
      group.isInterface(), group.interfaceAccess(), group.description(), group.patternId())).toList();
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
    return models.stream().map(model -> gson.toJson(new IntendedArchitecture(null, model.type(), model.language(), normalizedGroups(model.groups()),
      model.constraints() == null ? List.of() : model.constraints().stream().sorted(Comparator.comparing(gson::toJson)).toList(), model.label(), model.description())))
      .sorted().toList();
  }

  private static List<IntendedArchitecture.Group> normalizedGroups(@Nullable List<IntendedArchitecture.Group> groups) {
    if (groups == null) {
      return List.of();
    }
    return groups.stream().map(group -> new IntendedArchitecture.Group(group.label(),
      group.patterns() == null ? List.of() : group.patterns().stream().sorted().toList(), normalizedGroups(group.patternId() == null ? group.groups() : null),
      Boolean.TRUE.equals(group.isInterface()), Boolean.TRUE.equals(group.interfaceAccess()), group.description(),
      group.patternId() == null ? null : group.patternId().toLowerCase(java.util.Locale.ROOT))).toList();
  }

  private record PreviousModels(List<IntendedArchitecture> models) {
  }
}
