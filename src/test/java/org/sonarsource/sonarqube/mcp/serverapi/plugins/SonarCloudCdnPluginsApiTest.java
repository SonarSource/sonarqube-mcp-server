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
package org.sonarsource.sonarqube.mcp.serverapi.plugins;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarqube.mcp.http.HttpClient;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SonarCloudCdnPluginsApiTest {

  static Stream<Arguments> downloadUrlCases() {
    return Stream.of(
      arguments("https://sonarcloud.io", "java", "abc123", "https://scanner.sonarcloud.io/plugins/java/versions/abc123.jar"),
      arguments("https://sonarqube.us", "python", "def456", "https://scanner.sonarqube.us/plugins/python/versions/def456.jar"),
      arguments("https://cloud.example.com/context?query=value#fragment", "js", "789", "https://scanner.cloud.example.com/plugins/js/versions/789.jar"),
      arguments("http://user:password@cloud.example.com:9000/context", "go", "123", "http://scanner.cloud.example.com:9000/plugins/go/versions/123.jar"),
      arguments("https://sonarcloud.io", "java analyzer", "hash value", "https://scanner.sonarcloud.io/plugins/java%20analyzer/versions/hash%20value.jar")
    );
  }

  @ParameterizedTest
  @MethodSource("downloadUrlCases")
  void it_should_build_download_url(String baseUrl, String pluginKey, String md5, String expectedUrl) {
    assertThat(SonarCloudCdnPluginsApi.buildDownloadUrl(baseUrl, pluginKey, md5)).isEqualTo(expectedUrl);
  }

  @Test
  void it_should_expose_plugin_download_path() {
    assertThat(SonarCloudCdnPluginsApi.PLUGIN_DOWNLOAD_PATH).isEqualTo("/plugins/%s/versions/%s.jar");
  }

  @ParameterizedTest
  @ValueSource(strings = {"https://sonarcloud.io", "https://sonarqube.us/context"})
  void it_should_be_available_for_supported_production_hosts(String baseUrl) {
    var helper = mock(ServerApiHelper.class);
    when(helper.getBaseUrl()).thenReturn(baseUrl);

    assertThat(new SonarCloudCdnPluginsApi(helper).isAvailable()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"https://next.sonarcloud.io", "https://test.sc-test.io"})
  void it_should_not_be_available_for_non_production_hosts(String baseUrl) {
    var helper = mock(ServerApiHelper.class);
    when(helper.getBaseUrl()).thenReturn(baseUrl);

    assertThat(new SonarCloudCdnPluginsApi(helper).isAvailable()).isFalse();
  }

  @Test
  void it_should_reject_base_url_without_host() {
    assertThatThrownBy(() -> SonarCloudCdnPluginsApi.buildDownloadUrl("relative/path", "java", "abc123"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("SonarQube Cloud base URL must contain a host");
  }

  @Test
  void it_should_download_from_absolute_url_anonymously() {
    var helper = mock(ServerApiHelper.class);
    var response = mock(HttpClient.Response.class);
    var expectedUrl = "https://scanner.sonarcloud.io/plugins/java/versions/abc123.jar";
    when(helper.getBaseUrl()).thenReturn("https://sonarcloud.io");
    when(helper.rawGetAnonymousUrl(expectedUrl)).thenReturn(response);

    var result = new SonarCloudCdnPluginsApi(helper).downloadPlugin("java", "abc123");

    assertThat(result).isSameAs(response);
    verify(helper).rawGetAnonymousUrl(expectedUrl);
  }

}
