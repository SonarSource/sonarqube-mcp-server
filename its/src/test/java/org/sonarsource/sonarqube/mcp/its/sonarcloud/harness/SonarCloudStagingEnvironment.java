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
package org.sonarsource.sonarqube.mcp.its.sonarcloud.harness;

public final class SonarCloudStagingEnvironment {

  public static final String TOKEN_ENV_VAR = "SONARCLOUD_IT_TOKEN";
  public static final String SONARQUBE_ORG = "sonarlint-it";
  public static final String SONARQUBE_URL = "https://sc-staging.io";
  public static final String SONARQUBE_CLOUD_API_URL = "https://api.sc-staging.io";
  public static final String PROJECT_KEY_PREFIX = "sonarqube-mcp-its-";

  private SonarCloudStagingEnvironment() {
  }

  public static String requireToken() {
    var token = System.getenv(TOKEN_ENV_VAR);
    if (token == null || token.isBlank()) {
      throw new IllegalStateException(TOKEN_ENV_VAR + " must be set to run SonarQube Cloud integration tests against staging");
    }
    return token;
  }
}
