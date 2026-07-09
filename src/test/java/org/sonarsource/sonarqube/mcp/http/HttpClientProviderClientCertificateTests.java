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
package org.sonarsource.sonarqube.mcp.http;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.LoggerFactory;
import org.sonarsource.sonarqube.mcp.log.McpLogger;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpClientProviderClientCertificateTests {

  private static final String USER_AGENT = "SonarQube MCP Server Tests";
  private static final String KEY_STORE_PROPERTY = "javax.net.ssl.keyStore";
  private static final String KEY_STORE_PASSWORD_PROPERTY = "javax.net.ssl.keyStorePassword";
  private static final String TRUST_STORE_PROPERTY = "javax.net.ssl.trustStore";
  private static final String TRUST_STORE_PASSWORD_PROPERTY = "javax.net.ssl.trustStorePassword";
  private static final String KEY_STORE_PASSWORD = "test123";
  private static final Path KEYSTORE_PATH = resolveKeystorePath();

  @RegisterExtension
  static WireMockExtension sonarqubeMock = WireMockExtension.newInstance()
    .options(wireMockConfig()
      .httpDisabled(true)
      .dynamicHttpsPort()
      .keystorePath(KEYSTORE_PATH.toString())
      .keystorePassword(KEY_STORE_PASSWORD)
      .keyManagerPassword(KEY_STORE_PASSWORD)
      .needClientAuth(true)
      .trustStorePath(KEYSTORE_PATH.toString())
      .trustStorePassword(KEY_STORE_PASSWORD))
    .build();

  private String originalKeyStore;
  private String originalKeyStorePassword;
  private String originalTrustStore;
  private String originalTrustStorePassword;
  private HttpClientProvider underTest;
  private Logger mcpLogger;
  private ListAppender<ILoggingEvent> logAppender;

  @BeforeEach
  void setUp() {
    originalKeyStore = System.getProperty(KEY_STORE_PROPERTY);
    originalKeyStorePassword = System.getProperty(KEY_STORE_PASSWORD_PROPERTY);
    originalTrustStore = System.getProperty(TRUST_STORE_PROPERTY);
    originalTrustStorePassword = System.getProperty(TRUST_STORE_PASSWORD_PROPERTY);
    mcpLogger = (Logger) LoggerFactory.getLogger(McpLogger.class);
    logAppender = new ListAppender<>();
    logAppender.start();
    mcpLogger.addAppender(logAppender);
  }

  @AfterEach
  void tearDown() {
    if (underTest != null) {
      underTest.shutdown();
    }
    mcpLogger.detachAppender(logAppender);
    logAppender.stop();
    restoreProperty(KEY_STORE_PROPERTY, originalKeyStore);
    restoreProperty(KEY_STORE_PASSWORD_PROPERTY, originalKeyStorePassword);
    restoreProperty(TRUST_STORE_PROPERTY, originalTrustStore);
    restoreProperty(TRUST_STORE_PASSWORD_PROPERTY, originalTrustStorePassword);
  }

  private static void restoreProperty(String key, String value) {
    if (value == null) {
      System.clearProperty(key);
    } else {
      System.setProperty(key, value);
    }
  }

  @Test
  void it_should_present_client_certificate_when_keystore_configured() {
    trustServerCertificate();
    System.setProperty(KEY_STORE_PROPERTY, KEYSTORE_PATH.toString());
    System.setProperty(KEY_STORE_PASSWORD_PROPERTY, KEY_STORE_PASSWORD);

    underTest = new HttpClientProvider(USER_AGENT);

    try (var ignored = underTest.getHttpClient("token").getAsync(mcpUrl("/test")).join()) {
      // nothing
    }
    sonarqubeMock.verify(getRequestedFor(urlEqualTo("/test")));
  }

  @Test
  void it_should_fail_handshake_when_client_certificate_is_absent() {
    trustServerCertificate();
    System.clearProperty(KEY_STORE_PROPERTY);
    System.clearProperty(KEY_STORE_PASSWORD_PROPERTY);

    underTest = new HttpClientProvider(USER_AGENT);
    var getRequest = underTest.getHttpClient("token").getAsync(mcpUrl("/test"));
    assertThatThrownBy(getRequest::join)
      .isInstanceOf(CompletionException.class)
      .hasCauseInstanceOf(IOException.class);
  }

  @Test
  void it_should_warn_and_continue_when_keystore_property_points_to_invalid_keystore() {
    System.setProperty(KEY_STORE_PROPERTY, "/tmp/non-existent-keystore.p12");

    underTest = new HttpClientProvider(USER_AGENT);

    assertThat(logAppender.list)
      .filteredOn(event -> event.getLevel() == Level.WARN)
      .extracting(ILoggingEvent::getFormattedMessage)
      .anyMatch(message -> message.contains("client certificate"));
  }

  @Test
  void it_should_warn_and_continue_when_truststore_property_points_to_invalid_truststore() {
    System.setProperty(TRUST_STORE_PROPERTY, "/tmp/non-existent-truststore.p12");

    underTest = new HttpClientProvider(USER_AGENT);

    assertThat(logAppender.list)
      .filteredOn(event -> event.getLevel() == Level.WARN)
      .extracting(ILoggingEvent::getFormattedMessage)
      .anyMatch(message -> message.contains("trust store"));
  }

  private static void trustServerCertificate() {
    System.setProperty(TRUST_STORE_PROPERTY, KEYSTORE_PATH.toString());
    System.setProperty(TRUST_STORE_PASSWORD_PROPERTY, KEY_STORE_PASSWORD);
  }

  private static String mcpUrl(String path) {
    return sonarqubeMock.getRuntimeInfo().getHttpsBaseUrl() + path;
  }

  private static Path resolveKeystorePath() {
    try {
      return Paths.get(Objects.requireNonNull(
        HttpClientProviderClientCertificateTests.class.getResource("/ssl/test-keystore.p12")).toURI());
    } catch (Exception e) {
      throw new IllegalStateException("Test keystore not found at /ssl/test-keystore.p12", e);
    }
  }

}
