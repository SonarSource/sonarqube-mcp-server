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

/**
 * Shared SonarQube Cloud staging project provisioned once and analyzed with Maven ({@code mvn sonar:sonar}),
 * following the sonarlint-core {@code SonarCloudTests#analyzeMavenProject} pattern.
 */
public final class SonarCloudAnalyzedProject {

  public static final String SAMPLE_PROJECT_RESOURCE = "sample-java";
  public static final String MAIN_FILE_PATH = "src/main/java/foo/Foo.java";

  private static final Object LOCK = new Object();
  private static SonarCloudStagingFixture fixture;

  private SonarCloudAnalyzedProject() {
  }

  public static SonarCloudStagingFixture getOrInitialize() {
    synchronized (LOCK) {
      if (fixture == null) {
        fixture = SonarCloudStagingFixture.createSharedAnalyzed();
        Runtime.getRuntime().addShutdownHook(new Thread(SonarCloudAnalyzedProject::cleanup, "sonarcloud-analyzed-project-cleanup"));
      }
      return fixture;
    }
  }

  private static void cleanup() {
    synchronized (LOCK) {
      if (fixture != null) {
        fixture.cleanup();
        fixture = null;
      }
    }
  }
}
