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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;
import org.sonarsource.sonarqube.mcp.serverapi.exception.NotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArchitectureIntendedReadErrorsTests {
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void it_should_keep_not_found_guidance_scoped_to_server(boolean cloud) {
    var helper = mock(ServerApiHelper.class);
    var failure = new NotFoundException("Error 404 on intended architecture");
    var path = ArchitectureApi.INTENDED_ARCHITECTURES_PATH + "?projectId=project-id";
    when(helper.isSonarQubeCloud()).thenReturn(cloud);
    if (cloud) {
      when(helper.getApiSubdomain(path)).thenThrow(failure);
    } else {
      when(helper.get(ArchitectureApi.SERVER_API_PREFIX + path)).thenThrow(failure);
    }
    var api = new ArchitectureApi(helper);

    var actual = catchThrowable(() -> api.getIntendedState("my-project", "project-id"));

    assertThat(actual).isInstanceOf(NotFoundException.class).hasMessageContaining("Error 404");
    if (cloud) {
      assertThat(actual).isSameAs(failure);
    } else {
      assertThat(actual).hasMessageContaining("Check the project and Browse permission")
        .hasMessageContaining("older Server versions").hasMessageContaining("public Architecture APIs");
    }
  }
}
