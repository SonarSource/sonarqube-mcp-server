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

import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sonarsource.sonarqube.mcp.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mock;
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

  @Test
  void it_should_reject_base_url_without_host() {
    assertThatThrownBy(() -> SonarCloudCdnPluginsApi.buildDownloadUrl("relative/path", "java", LOWERCASE_MD5))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("SonarQube Cloud base URL must contain a host");
  }

  @Test
  void it_should_download_from_absolute_url_anonymously() {
    var httpClient = mock(HttpClient.class);
    var response = mock(HttpClient.Response.class);
    var expectedUrl = "https://scanner.sonarcloud.io/plugins/java/versions/" + LOWERCASE_MD5 + ".jar";
    when(httpClient.getAsyncAnonymous(expectedUrl)).thenReturn(CompletableFuture.completedFuture(response));

    var result = new SonarCloudCdnPluginsApi("https://sonarcloud.io", httpClient).downloadPlugin("java", LOWERCASE_MD5);

    assertThat(result).isSameAs(response);
    verify(httpClient).getAsyncAnonymous(expectedUrl);
  }

}
