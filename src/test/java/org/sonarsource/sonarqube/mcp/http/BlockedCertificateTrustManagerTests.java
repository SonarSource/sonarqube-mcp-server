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

import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509ExtendedTrustManager;
import nl.altindag.ssl.SSLFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BlockedCertificateTrustManagerTests {
  private final X509ExtendedTrustManager delegate = mock(X509ExtendedTrustManager.class);
  private final BlockedCertificateTrustManager underTest = new BlockedCertificateTrustManager(delegate);

  @BeforeEach
  void prepareDelegate() {
    when(delegate.getAcceptedIssuers()).thenReturn(new X509Certificate[0]);
  }

  @Test
  void it_should_block_all_published_certificates_before_delegating() throws Exception {
    try (var input = getClass().getResourceAsStream("/ssl/blocked-intermediates.pem")) {
      var certificates = CertificateFactory.getInstance("X.509").generateCertificates(input);
      assertThat(certificates).hasSize(16);
      for (var certificate : certificates) {
        var chain = new X509Certificate[] {(X509Certificate) certificate};
        assertBlocked(chain);
        // A blocked intermediate must also be rejected after an unrelated leaf.
        assertBlocked(new X509Certificate[] {trustedCertificate(), (X509Certificate) certificate});
      }
    }
    verifyNoInteractions(delegate);
  }

  private void assertBlocked(X509Certificate[] chain) {
    assertThatThrownBy(() -> underTest.checkServerTrusted(chain, "EC")).isInstanceOf(CertificateException.class).hasMessageContaining("blocked public key");
    assertThatThrownBy(() -> underTest.checkServerTrusted(chain, "EC", (Socket) null)).isInstanceOf(CertificateException.class);
    assertThatThrownBy(() -> underTest.checkServerTrusted(chain, "EC", (SSLEngine) null)).isInstanceOf(CertificateException.class);
    assertThatThrownBy(() -> underTest.checkClientTrusted(chain, "EC")).isInstanceOf(CertificateException.class);
    assertThatThrownBy(() -> underTest.checkClientTrusted(chain, "EC", (Socket) null)).isInstanceOf(CertificateException.class);
    assertThatThrownBy(() -> underTest.checkClientTrusted(chain, "EC", (SSLEngine) null)).isInstanceOf(CertificateException.class);
  }

  @Test
  void it_should_preserve_every_delegate_validation_and_accepted_issuers() throws Exception {
    var chain = new X509Certificate[] {trustedCertificate()};
    var socket = mock(Socket.class);
    var engine = mock(SSLEngine.class);
    underTest.checkServerTrusted(chain, "RSA");
    underTest.checkServerTrusted(chain, "RSA", socket);
    underTest.checkServerTrusted(chain, "RSA", engine);
    underTest.checkClientTrusted(chain, "RSA");
    underTest.checkClientTrusted(chain, "RSA", socket);
    underTest.checkClientTrusted(chain, "RSA", engine);
    verify(delegate).checkServerTrusted(chain, "RSA");
    verify(delegate).checkServerTrusted(chain, "RSA", socket);
    verify(delegate).checkServerTrusted(chain, "RSA", engine);
    verify(delegate).checkClientTrusted(chain, "RSA");
    verify(delegate).checkClientTrusted(chain, "RSA", socket);
    verify(delegate).checkClientTrusted(chain, "RSA", engine);
    when(delegate.getAcceptedIssuers()).thenReturn(chain);
    assertThat(underTest.getAcceptedIssuers()).isSameAs(chain);
  }

  @Test
  void it_should_preserve_validation_failures() throws Exception {
    var chain = new X509Certificate[] {trustedCertificate()};
    var failure = new CertificateException("Untrusted chain");
    doThrow(failure).when(delegate).checkServerTrusted(chain, "RSA");
    assertThatThrownBy(() -> underTest.checkServerTrusted(chain, "RSA")).isSameAs(failure);
  }

  @Test
  void it_should_reject_missing_chains() {
    for (var chain : new X509Certificate[][] {null, new X509Certificate[0]}) {
      assertThatThrownBy(() -> underTest.checkServerTrusted(chain, "RSA")).isInstanceOf(CertificateException.class);
    }
    verifyNoInteractions(delegate);
  }

  @Test
  void it_should_wrap_the_composite_including_permissive_custom_trust() throws Exception {
    var factory = BlockedCertificateTrustManager.protect(SSLFactory.builder().withTrustMaterial(delegate).build());
    try (var input = getClass().getResourceAsStream("/ssl/blocked-intermediates.pem")) {
      var certificate = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
      assertThatThrownBy(() -> factory.getTrustManager().orElseThrow().checkServerTrusted(new X509Certificate[] {certificate}, "EC"))
        .isInstanceOf(CertificateException.class).hasMessageContaining("blocked public key");
    }
    verifyNoInteractions(delegate);
  }

  @Test
  void it_should_reject_a_blocked_trust_anchor_omitted_from_the_peer_chain() throws Exception {
    try (var input = getClass().getResourceAsStream("/ssl/blocked-intermediates.pem")) {
      var issuer = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
      var leaf = mock(X509Certificate.class);
      when(leaf.getPublicKey()).thenReturn(trustedCertificate().getPublicKey());
      when(leaf.getIssuerX500Principal()).thenReturn(issuer.getSubjectX500Principal());
      when(delegate.getAcceptedIssuers()).thenReturn(new X509Certificate[] {issuer});
      assertThatThrownBy(() -> underTest.checkServerTrusted(new X509Certificate[] {leaf}, "EC"))
        .isInstanceOf(CertificateException.class).hasMessageContaining("blocked public key");
      verify(leaf).verify(issuer.getPublicKey());
    }
  }

  @Test
  void it_should_preserve_client_identity() {
    var keyManager = mock(X509ExtendedKeyManager.class);
    var original = SSLFactory.builder().withTrustMaterial(delegate).withIdentityMaterial(keyManager).build();
    var protectedFactory = BlockedCertificateTrustManager.protect(original);
    assertThat(protectedFactory.getKeyManager()).isEqualTo(original.getKeyManager());
  }

  private static X509Certificate trustedCertificate() {
    return SSLFactory.builder().withDefaultTrustMaterial().build().getTrustedCertificates().getFirst();
  }
}
