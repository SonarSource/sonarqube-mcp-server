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
package org.sonarsource.sonarqube.mcp.serverapi.architecture.response;

import com.google.gson.annotations.SerializedName;
import jakarta.annotation.Nullable;
import java.util.List;

/**
 * Intended architecture of one language. Group keys are labels joined by {@code /}; constraints reference those keys.
 * The resource id is absent for unsaved input. Nested group collections and interface flags are optional in the public API.
 */
public record IntendedArchitecture(@Nullable String id, String type, String language, List<Group> groups, List<Constraint> constraints) {

  public IntendedArchitecture(String type, String language, List<Group> groups, List<Constraint> constraints) {
    this(null, type, language, groups, constraints);
  }

  public record Group(String label, @Nullable List<String> patterns, @Nullable List<Group> groups,
    @SerializedName("interface") @Nullable Boolean isInterface, @Nullable Boolean interfaceAccess) {
  }

  public record Constraint(String from, String to) {
  }

}
