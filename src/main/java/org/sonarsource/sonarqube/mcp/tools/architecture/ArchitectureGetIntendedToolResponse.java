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

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;
import java.util.List;

/**
 * Without component, depth and language, {@code models} contains all publicly exported perspectives; the export may omit metadata. {@code revision} is the
 * revision of the whole stored model, whatever component, depth or language were asked for.
 */
public record ArchitectureGetIntendedToolResponse(String projectKey, String revision, @Nullable String component, @Nullable Integer depth, List<Model> models,
  @Nullable Boolean partial, @Nullable String message) {

  public record Model(@Nullable String language, @Nullable String type, List<Group> groups, List<Constraint> constraints, @Nullable Boolean partial, @Nullable String label, @Nullable String description) {
  }

  /**
   * {@code hiddenSubgroups} counts the subgroups left out by {@code depth}.
   */
  public record Group(@Nullable String label, @Nullable List<String> patterns, @JsonProperty("interface") @Nullable Boolean isInterface,
    @Nullable Boolean interfaceAccess, @Nullable List<Group> groups, @Nullable Integer hiddenSubgroups, @Nullable String description, @Nullable String patternId) {
  }

  public record Constraint(@Nullable String from, @Nullable String to) {
  }

}
