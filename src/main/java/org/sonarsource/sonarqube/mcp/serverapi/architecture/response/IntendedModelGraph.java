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
 * The {@code intended_models} graph an analysis stores: where its model came from ({@code FILE}, {@code SERVER} or {@code NONE}) and that model,
 * in the analyzer's internal shape. {@code model} is null when the analysis ran without one. Served by the internal graphs endpoint only, until a
 * public read of the analysed model exists.
 */
public record IntendedModelGraph(String source, @Nullable Model model) {

  public static final String FILE_SOURCE = "FILE";

  public record Model(@Nullable List<Perspective> perspectives) {
  }

  public record Perspective(@Nullable String label, @Nullable String description, @Nullable String qualifiers, @Nullable String language,
    @Nullable List<Group> groups, @Nullable List<Constraint> constraints) {
  }

  public record Group(@Nullable String label, @Nullable String description, @Nullable List<String> patterns, @Nullable List<Group> groups,
    @SerializedName("interface") @Nullable Boolean isInterface, @Nullable Boolean interfaceAccess,
    @SerializedName("interface_access") @Nullable Boolean interfaceAccessSnakeCase) {
  }

  public record Constraint(@Nullable List<String> from, @Nullable List<String> to, @Nullable String relation) {
  }

}
