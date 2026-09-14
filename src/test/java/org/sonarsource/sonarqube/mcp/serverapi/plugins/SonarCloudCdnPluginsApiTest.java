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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SonarCloudCdnPluginsApiTest {

  private static final String LOWERCASE_MD5 = "0123456789abcdef0123456789abcdef";
  private static final String UPPERCASE_MD5 = "ABCDEF0123456789ABCDEF0123456789";

  static Stream<Arguments> downloadUrlCases() {
    return Stream.of(
      arguments("https://sonarcloud.io", "java", LOWERCASE_MD5, "https://scanner.sonarcloud.io/plugins/java/versions/" + LOWERCASE_MD5 + ".jar"),
      arguments("https://sonarqube.us", "python", UPPERCASE_MD5, "https://scanner.sonarqube.us/plugins/python/versions/" + UPPERCASE_MD5 + ".jar"),
      arguments("https://cloud.example.com/context?query=value#fragment", "js", LOWERCASE_MD5,
        "https://scanner.cloud.example.com/plugins/js/versions/" + LOWERCASE_MD5 + ".jar"),
      arguments("http://user:password@cloud.example.com:9000/context", "go", LOWERCASE_MD5,
        "http://scanner.cloud.example.com:9000/plugins/go/versions/" + LOWERCASE_MD5 + ".jar"),
      arguments("https://sonarcloud.io", "java analyzer", LOWERCASE_MD5,
        "https://scanner.sonarcloud.io/plugins/java%20analyzer/versions/" + LOWERCASE_MD5 + ".jar")
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
    assertThatThrownBy(() -> SonarCloudCdnPluginsApi.buildDownloadUrl("relative/path", "java", LOWERCASE_MD5))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("SonarQube Cloud base URL must contain a host");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "abc123", "gggggggggggggggggggggggggggggggg", "../../0123456789abcdef0123456789abcdef"})
  void it_should_reject_invalid_md5_before_http_request(String md5) {
    var helper = mock(ServerApiHelper.class);
    when(helper.getBaseUrl()).thenReturn("https://sonarcloud.io");
    var api = new SonarCloudCdnPluginsApi(helper);

    assertThatThrownBy(() -> api.downloadPlugin("java", md5))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Plugin MD5 must be a 32-character hexadecimal value");
    verify(helper, never()).rawGetAnonymousUrl(anyString());
  }

  @Test
  void it_should_download_from_absolute_url_anonymously() {
    var helper = mock(ServerApiHelper.class);
    var response = mock(HttpClient.Response.class);
    var expectedUrl = "https://scanner.sonarcloud.io/plugins/java/versions/" + LOWERCASE_MD5 + ".jar";
    when(helper.getBaseUrl()).thenReturn("https://sonarcloud.io");
    when(helper.rawGetAnonymousUrl(expectedUrl)).thenReturn(response);

    var result = new SonarCloudCdnPluginsApi(helper).downloadPlugin("java", LOWERCASE_MD5);

    assertThat(result).isSameAs(response);
    verify(helper).rawGetAnonymousUrl(expectedUrl);
  }

}
