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
package org.sonarsource.sonarqube.mcp.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ExternalToolsLoaderTest {

  @Test
  void loadExternalTools_should_return_empty_when_no_providers_configured() {
    // Test resources has empty external-tool-providers.json
    var loader = new ExternalToolsLoader();
    var tools = loader.loadExternalTools();

    // Should return empty list without throwing exceptions
    assertThat(tools).isEmpty();
  }

  @Test
  void shutdown_should_not_fail_when_no_clients_initialized() {
    var loader = new ExternalToolsLoader();

    assertThatCode(loader::shutdown).doesNotThrowAnyException();
  }

}

