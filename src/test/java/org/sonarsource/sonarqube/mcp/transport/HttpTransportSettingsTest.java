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
package org.sonarsource.sonarqube.mcp.transport;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarqube.mcp.configuration.McpServerLaunchConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

class HttpTransportSettingsTest {

  @Test
  void it_should_preserve_https_listener_and_request_configuration(@TempDir Path storage) {
    var environment = new HashMap<String, String>();
    environment.put("STORAGE_PATH", storage.toString());
    environment.put("SONARQUBE_TRANSPORT", "https");
    environment.put("SONARQUBE_IS_CLOUD", "true");
    environment.put("SONARQUBE_ORG", "server-org");
    environment.put("SONARQUBE_HTTP_PORT", "8443");
    environment.put("SONARQUBE_HTTP_HOST", "0.0.0.0");
    environment.put("SONARQUBE_HTTP_ALLOWED_ORIGINS", "https://client.example.com,https://other.example.com");
    environment.put("SONARQUBE_MCP_IN_CONTAINER", "true");
    environment.put("SONARQUBE_HTTPS_KEYSTORE_PATH", storage.resolve("keystore.p12").toString());
    environment.put("SONARQUBE_HTTPS_KEYSTORE_PASSWORD", "keystore-secret");
    environment.put("SONARQUBE_HTTPS_KEYSTORE_TYPE", "PKCS12");
    environment.put("SONARQUBE_HTTPS_TRUSTSTORE_PATH", storage.resolve("truststore.jks").toString());
    environment.put("SONARQUBE_HTTPS_TRUSTSTORE_PASSWORD", "truststore-secret");
    environment.put("SONARQUBE_HTTPS_TRUSTSTORE_TYPE", "JKS");
    var configuration = new McpServerLaunchConfiguration(environment);

    var settings = HttpTransportSettings.from(configuration);

    assertThat(settings.port()).isEqualTo(8443);
    assertThat(settings.host()).isEqualTo("0.0.0.0");
    assertThat(settings.isSonarQubeCloud()).isTrue();
    assertThat(settings.serverOrg()).isEqualTo("server-org");
    assertThat(settings.tls().enabled()).isTrue();
    assertThat(settings.tls().keystorePath()).isEqualTo(storage.resolve("keystore.p12"));
    assertThat(settings.tls().keystorePassword()).isEqualTo("keystore-secret");
    assertThat(settings.tls().keystoreType()).isEqualTo("PKCS12");
    assertThat(settings.tls().truststorePath()).isEqualTo(storage.resolve("truststore.jks"));
    assertThat(settings.tls().truststorePassword()).isEqualTo("truststore-secret");
    assertThat(settings.tls().truststoreType()).isEqualTo("JKS");
    assertThat(settings.requests().allowedOrigins()).containsExactly("https://client.example.com", "https://other.example.com");
    assertThat(settings.requests().appVersion()).isEqualTo(configuration.getAppVersion());
    assertThat(settings.requests().isRunningInContainer()).isTrue();
    assertThat(settings.toString()).doesNotContain("keystore-secret", "truststore-secret");
    assertThat(settings.tls().toString()).doesNotContain("keystore-secret", "truststore-secret");
  }

  @Test
  void it_should_preserve_an_http_sonarqube_server_configuration(@TempDir Path storage) {
    var configuration = new McpServerLaunchConfiguration(Map.of("STORAGE_PATH", storage.toString(), "SONARQUBE_TRANSPORT", "http",
      "SONARQUBE_URL", "http://localhost:9000"));

    var settings = HttpTransportSettings.from(configuration);

    assertThat(settings.isSonarQubeCloud()).isFalse();
    assertThat(settings.serverOrg()).isNull();
    assertThat(settings.tls().enabled()).isFalse();
    assertThat(settings.requests().allowedOrigins()).isEmpty();
    assertThat(settings.requests().isRunningInContainer()).isFalse();
  }
}
