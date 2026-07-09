# Test SSL Certificates

This directory contains SSL certificates used for testing HTTPS functionality.

## test-keystore.p12

A PKCS12 keystore containing a self-signed certificate for testing purposes.

- **Alias**: test-mcp
- **Algorithm**: RSA 2048-bit
- **Validity**: 3650 days (10 years)
- **Password**: test123
- **Distinguished Name**: CN=localhost, OU=Test, O=SonarSource, L=Geneva, ST=Geneva, C=CH

## Regenerating the Keystore

If you need to regenerate the test keystore, use:

```bash
cd src/test/resources/ssl
keytool -genkeypair \
  -alias test-mcp \
  -keyalg RSA \
  -keysize 2048 \
  -validity 3650 \
  -keystore test-keystore.p12 \
  -storetype PKCS12 \
  -storepass test123 \
  -keypass test123 \
  -dname "CN=localhost, OU=Test, O=SonarSource, L=Geneva, ST=Geneva, C=CH"
```

## Usage in Tests

The keystore is used in `HttpsServerTransportIntegrationTest` to test HTTPS server startup and SSL configuration without requiring manual certificate generation for each test run.

It is also used in `HttpClientProviderClientCertificateTests`, both as the client identity material and — since a PKCS12 key entry's leaf certificate doubles as a trust anchor — as the truststore for the mutual-TLS WireMock server (client and server share the same `CN=localhost` certificate).

**Note**: These certificates are for testing only and should never be used in production.

