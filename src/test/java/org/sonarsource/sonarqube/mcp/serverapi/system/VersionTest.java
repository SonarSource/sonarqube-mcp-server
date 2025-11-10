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
package org.sonarsource.sonarqube.mcp.serverapi.system;

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
}
