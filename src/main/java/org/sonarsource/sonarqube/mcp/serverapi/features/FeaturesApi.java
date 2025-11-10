/*
 * SonarQube MCP Server
 * Copyright (C) 2025 SonarSource
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
package org.sonarsource.sonarqube.mcp.serverapi.features;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;

public class FeaturesApi {

  public static final String FEATURES_LIST_PATH = "/api/features/list";

  private final ServerApiHelper helper;

  public FeaturesApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public Set<Feature> listFeatures() {
    try (var response = helper.get(FEATURES_LIST_PATH)) {
      var responseStr = response.bodyAsString();
      var featureKeys = new Gson().fromJson(responseStr, String[].class);
      return Arrays.stream(featureKeys).flatMap(key -> Feature.fromKey(key).stream()).collect(Collectors.toSet());
    }
  }

}

