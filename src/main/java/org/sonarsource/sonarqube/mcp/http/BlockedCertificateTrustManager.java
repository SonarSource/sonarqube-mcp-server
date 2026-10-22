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
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedTrustManager;
import nl.altindag.ssl.SSLFactory;

/** Applies mandatory public-key distrust before any configured trust manager. */
public final class BlockedCertificateTrustManager extends X509ExtendedTrustManager {
  // SHA-256 of SubjectPublicKeyInfo, derived from OpenJDK certificate trust data:
  // https://github.com/openjdk/jdk/commit/22bc7357ce6bc5f1ed2f1e83e0544b2c25f781d4
  // Matching keys also covers cross-signatures and alternate certificate encodings.
  private static final Set<String> BLOCKED_KEYS = Set.of(
    "43bffa19d6ef651451f6841e019931e96f6f2eaeff28ab4b6f1ce4576de2717b",
    "49dd353dc2b919a3770645117d0e60c16115e7d1bc259eefe734b00ea434fe5f",
    "5c0660a2b164910d8ff589c18e14552e9e0ec237dcefe2cb736d41a4811f81a0",
    "63ecefee431d49523bc1136c4e72d0206a477086caf606cd5cf025686c109cf8",
    "92c063d67e8f14db71d288e11266ffdaf69cbf115f7bab14f3bc2f68d25e0e30",
    "e0254c7fcdc265e2f0f8a428dabb75ab4ae039b4872d62f5ee57f0877d5a8a41",
    "ebed6475c2c2d80fcb199f3fe8dd6e6b3ce60b77eb15d4e42c3dec0c6f5b5105",
    "ec4ffa1ad0ac88081314b7aaffef6a91f97cd57291bb5ba686c5415f5eb4f6b6");

  private final X509ExtendedTrustManager delegate;
  private final List<X509Certificate> blockedIssuers;

  public BlockedCertificateTrustManager(X509ExtendedTrustManager delegate) {
    this.delegate = delegate;
    this.blockedIssuers = Arrays.stream(delegate.getAcceptedIssuers())
      .filter(issuer -> BLOCKED_KEYS.contains(publicKeyFingerprint(issuer)))
      .toList();
  }

  public static SSLFactory protect(SSLFactory factory) {
    var builder = SSLFactory.builder()
      .withTrustMaterial(new BlockedCertificateTrustManager(factory.getTrustManager().orElseThrow()));
    factory.getKeyManager().ifPresent(builder::withIdentityMaterial);
    return builder.build();
  }

  private void checkBlockedKeys(@Nullable X509Certificate[] chain) throws CertificateException {
    if (chain == null || chain.length == 0) {
      throw new CertificateException("Certificate chain is empty");
    }
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      for (var certificate : chain) {
        var fingerprint = HexFormat.of().formatHex(digest.digest(certificate.getPublicKey().getEncoded()));
        if (BLOCKED_KEYS.contains(fingerprint)) {
          throw new CertificateException("Certificate chain contains a blocked public key");
        }
      }
      // A locally trusted issuer may be omitted from the peer's chain.
      for (var issuer : blockedIssuers) {
        rejectCertificatesSignedBy(chain, issuer);
      }
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required for certificate validation", e);
    }
  }

  private static String publicKeyFingerprint(X509Certificate certificate) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(certificate.getPublicKey().getEncoded()));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required for certificate validation", e);
    }
  }

  private static void rejectCertificatesSignedBy(X509Certificate[] chain, X509Certificate issuer) throws CertificateException {
    for (var certificate : chain) {
      if (certificate.getIssuerX500Principal().equals(issuer.getSubjectX500Principal())) {
        try {
          certificate.verify(issuer.getPublicKey());
        } catch (GeneralSecurityException e) {
          continue;
        }
        throw new CertificateException("Certificate chain contains a blocked public key");
      }
    }
  }

  @Override
  public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
    checkBlockedKeys(chain);
    delegate.checkServerTrusted(chain, authType);
  }

  @Override
  public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
    checkBlockedKeys(chain);
    delegate.checkServerTrusted(chain, authType, socket);
  }

  @Override
  public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
    checkBlockedKeys(chain);
    delegate.checkServerTrusted(chain, authType, engine);
  }

  @Override
  public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
    checkBlockedKeys(chain);
    delegate.checkClientTrusted(chain, authType);
  }

  @Override
  public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
    checkBlockedKeys(chain);
    delegate.checkClientTrusted(chain, authType, socket);
  }

  @Override
  public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
    checkBlockedKeys(chain);
    delegate.checkClientTrusted(chain, authType, engine);
  }

  @Override
  public X509Certificate[] getAcceptedIssuers() {
    return delegate.getAcceptedIssuers();
  }
}
