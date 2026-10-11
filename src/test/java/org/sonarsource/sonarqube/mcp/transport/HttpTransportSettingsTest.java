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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HttpTransportSettingsTest {
  @Test
  void should_keep_tls_passwords_out_of_settings_diagnostics() {
    var settings = new HttpTransportSettings.TlsSettings(true, Path.of("keystore.p12"), "private-key-password", "PKCS12",
      Path.of("truststore.p12"), "private-trust-password", "PKCS12");

    assertThat(settings.toString()).contains("credentials=redacted").doesNotContain("private-key-password", "private-trust-password");
  }
}

