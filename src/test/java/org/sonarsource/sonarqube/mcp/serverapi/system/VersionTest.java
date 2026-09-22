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
package org.sonarsource.sonarqube.mcp.serverapi.system;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VersionTest {

  @Test
  void it_should_consider_the_same_version_satisfies_min_requirements() {
    var version = Version.create("1.2.3-SNAPSHOT");

    var satisfiesMinRequirement = version.satisfiesMinRequirement(version);

    assertThat(satisfiesMinRequirement).isTrue();
  }

  @Test
  void it_should_consider_a_newer_version_satisfies_min_requirements() {
    var version = Version.create("1.2.3-SNAPSHOT");

    var satisfiesMinRequirement = version.satisfiesMinRequirement(Version.create("1.2.2-SNAPSHOT"));

    assertThat(satisfiesMinRequirement).isTrue();
  }

  @Test
  void it_should_consider_an_older_version_does_not_satisfy_min_requirements() {
    var version = Version.create("1.2.2-SNAPSHOT");

    var satisfiesMinRequirement = version.satisfiesMinRequirement(Version.create("1.2.3-SNAPSHOT"));

    assertThat(satisfiesMinRequirement).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"2025.1", "2025.4", "25.1", "25.2"})
  void it_should_consider_supported_sonarqube_server_versions(String versionName) {
    assertThat(Version.create(versionName).isSupportedSonarQubeServerVersion()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"9.9.1", "10.4", "24.12", "25.0", "2024.12"})
  void it_should_consider_unsupported_sonarqube_server_versions(String versionName) {
    assertThat(Version.create(versionName).isSupportedSonarQubeServerVersion()).isFalse();
  }
}
