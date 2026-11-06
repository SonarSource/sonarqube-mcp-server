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
import java.net.HttpURLConnection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
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
  void replace(String projectKey, String projectId, ArchitectureApi.IntendedState previousState, List<IntendedArchitecture> next) {
    var previous = previousState.models();
    checkWritableModels(previous, next);
    var actual = api.getIntendedState(projectKey, projectId);
    if (!sameResources(previousState, actual)) {
      throw new IllegalStateException("The intended architecture changed before replacement. "
        + "Nothing was changed; read it again and repeat the dry run.");
    }
    final ArchitectureApi.IntendedState written;
    try {
      var writable = writableModels(next);
      if (next.isEmpty()) {
        api.deleteIntended(projectKey, previousState.id());
        written = new ArchitectureApi.IntendedState(null, List.of());
      } else {
        written = previousState.id() == null ? api.createIntended(projectKey, projectId, writable) : api.patchIntended(projectKey, previousState.id(), writable);
      }
    } catch (RuntimeException failure) {
      if (isDefiniteRejection(failure)) {
        throw failure;
      }
      reconcileFailure(projectKey, projectId, previousState, next, failure, false);
      return;
    }
    try {
      validateResourceIds(written.models());
      if (!sameModels(next, written.models()) || (!next.isEmpty() && previousState.id() != null && !previousState.id().equals(written.id()))) {
        throw new IllegalStateException("The public API write response did not identify the requested model. "
          + "Read the current state before retrying.");
      }
      if (!sameResources(written, api.getIntendedState(projectKey, projectId))) {
        throw new IllegalStateException("The intended architecture after the write differs from its response.");
      }
    } catch (RuntimeException failure) {
      reconcileFailure(projectKey, projectId, previousState, next, failure, true);
    }
  }

  private static boolean isDefiniteRejection(RuntimeException failure) {
    return (failure instanceof ClientErrorException clientError && clientError.status() != HttpURLConnection.HTTP_CLIENT_TIMEOUT)
      || failure instanceof ForbiddenException || failure instanceof NotFoundException || failure instanceof UnauthorizedException;
  }

  static void checkWritableModels(List<IntendedArchitecture> previous, List<IntendedArchitecture> next) {
    if (next.size() > ArchitectureApi.MAX_PERSPECTIVES) {
      throw new IllegalArgumentException("Give at most " + ArchitectureApi.MAX_PERSPECTIVES
        + " intended architecture perspectives. Nothing was changed.");
    }
    validateResourceIds(previous);
    for (var model : next) {
      var storedGroups = previous.stream().filter(stored -> Objects.equals(stored.language(), model.language()) && Objects.equals(stored.type(), model.type()))
        .findFirst().map(IntendedArchitecture::groups).orElse(List.of());
      checkImportedGroups(storedGroups, model.groups());
    }
  }

  private static void checkImportedGroups(List<IntendedArchitecture.Group> previous, List<IntendedArchitecture.Group> next) {
    var storedByLabel = previous.stream().collect(Collectors.toMap(IntendedArchitecture.Group::label, Function.identity(), (left, right) -> {
      throw new IllegalStateException("The public intended architecture response has duplicate group labels. Nothing was changed.");
    }));
    for (var group : next) {
      var stored = storedByLabel.get(group.label());
      if (group.patternId() != null && group.groups() != null && !group.groups().isEmpty()) {
        if (stored == null || !group.patternId().equalsIgnoreCase(stored.patternId()) || !IntendedRevision.sameGroups(stored.groups(), group.groups())) {
          throw new IllegalArgumentException("Group '" + group.label() + "' imports a pattern and cannot declare child groups. "
            + "Keep its exported child groups unchanged, or remove them and keep patternId. Nothing was changed.");
        }
      } else if (group.patternId() == null && group.groups() != null) {
        checkImportedGroups(stored == null || stored.patternId() != null || stored.groups() == null ? List.of() : stored.groups(), group.groups());
      }
    }
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

  private static boolean sameResources(ArchitectureApi.IntendedState left, ArchitectureApi.IntendedState right) {
    return Objects.equals(left.id(), right.id()) && IntendedRevision.of(left.models()).equals(IntendedRevision.of(right.models()));
  }

  private void reconcileFailure(String projectKey, String projectId, ArchitectureApi.IntendedState previous, List<IntendedArchitecture> next,
    RuntimeException failure, boolean acknowledged) {
    final ArchitectureApi.IntendedState actual;
    try {
      actual = api.getIntendedState(projectKey, projectId);
      validateResourceIds(actual.models());
    } catch (RuntimeException readFailure) {
      throw manualRecovery(previous.models(), "The write could not be confirmed and its resulting state could not be read. No automatic rollback was attempted", failure);
    }
    if (!next.isEmpty() && sameModels(next, actual.models()) && (previous.id() == null || previous.id().equals(actual.id()))) {
      return;
    }
    if (next.isEmpty() && actual.id() == null) {
      if (acknowledged) {
        return;
      }
      throw manualRecovery(previous.models(), "The deletion could not be confirmed. No intended architecture resource was visible on readback, "
        + "but the DELETE response did not confirm deletion. No automatic rollback was attempted; read the current state before retrying", failure);
    }
    throw manualRecovery(previous.models(), "The write could not be confirmed and the requested model could not be verified. "
      + "No automatic rollback was attempted; read the current state before retrying", failure);
  }

  private IntendedRecoveryException manualRecovery(List<IntendedArchitecture> previous, String message, RuntimeException cause) {
    return new IntendedRecoveryException(message + ". Failure: " + cause.getMessage() + ". " + ArchitectureApi.EXPORT_WARNING,
      gson.toJson(new PreviousModels(writableModels(previous))));
  }

  private static List<IntendedArchitecture> writableModels(List<IntendedArchitecture> models) {
    return models.stream().map(model -> new IntendedArchitecture(model.id(), model.type(), model.language(),
      writableGroups(model.groups()), model.constraints(), model.label(), model.description())).toList();
  }

  private static List<IntendedArchitecture.Group> writableGroups(List<IntendedArchitecture.Group> groups) {
    return groups.stream().map(group -> new IntendedArchitecture.Group(group.label(), group.patterns(),
      group.patternId() != null || group.groups() == null ? null : writableGroups(group.groups()),
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
      group.patternId() == null ? null : group.patternId().toLowerCase(Locale.ROOT))).toList();
  }

  private record PreviousModels(List<IntendedArchitecture> models) {
  }
}
