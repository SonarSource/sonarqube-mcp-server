# Integration Tests (ITS)

Integration tests for the SonarQube MCP Server.

## Running Tests

```bash
# From project root
./gradlew :its:integrationTest
```

**Note**: Integration tests are NOT run by default in `./gradlew test` or `./gradlew build`.

## Prerequisites

- Docker installed and running
- Java 21

## Test Structure

```
its/
├── build.gradle.kts
├── src/test/
│   ├── java/org/sonarsource/sonarqube/mcp/its/
│   │   ├── HttpTransportITest.java      # HTTP transport tests
│   │   ├── StdioTransportITest.java     # STDIO transport tests
│   │   └── ...
│   └── resources/
│       ├── proxied-mcp-servers-its.json
│       └── binaries/
│           └── sonar-context-augmentation    # Alpine Linux binary (Rust, musl)
```

## Architecture

Tests use [Testcontainers](https://www.testcontainers.org/) to run the server and proxied server binary in Alpine Linux containers.

The `sonar-context-augmentation` binary is compiled for Alpine Linux (musl libc) and cannot run directly on most host systems.

## Test Container Dependencies

The following dependencies are automatically installed in test containers to match the production Dockerfile:
- **nodejs**: Runtime dependencies
- **wget**: HTTP testing utility

## Hosted Staging authentication acceptance

The opt-in `hostedAuthenticationTest` suite targets `https://api.sc-staging.io/mcp`.
It uses the MCP Java client and real credentials, without Docker, an application JAR, or CAG binaries.
It is separate from `integrationTest`, `test`, `build`, and normal PR CI.

Live acceptance is **pending the OAuth-capable Staging deployment**. Prepare and compile the tests now;
run the live commands only after the MCP server, sidecar, Authentication metadata, Auth0 resources,
and token exchange credentials are configured in Staging.

### Credentials and fixtures

Supply these environment variables through your local secret manager or a secure shell prompt;
do not paste tokens into commands, chat, or tracked files:

- `SONARCLOUD_IT_OAUTH_TOKEN`: a fresh, preferably previously unused Auth0 access token (Token A),
  issued by `https://auth.sc-staging.io/` for audience `https://api.sc-staging.io/mcp`,
  with at least `mcp:tools:read`. Use the OAuth client login flow to obtain it; an ID token,
  Cloud API token (Token B), or machine-to-machine token is not suitable.
- `SONARCLOUD_IT_TOKEN`: a valid Staging SonarQube **user** token with access to both fixtures.
- `SONARCLOUD_IT_ORGANIZATION`: the key of an organization the user belongs to.
- `SONARCLOUD_IT_PRIVATE_PROJECT_KEY`: the exact key of a **private** project in that organization
  which the user can browse. The tests check that anonymous access to this project is denied.

Prefer the same existing SQC account for both credentials. The account must have signed into
Staging SQC at least once; an Auth0 account alone is insufficient for Cloud API access.
OAuth-only runs do not require `SONARCLOUD_IT_TOKEN`; legacy-only runs do not require
`SONARCLOUD_IT_OAUTH_TOKEN`. Missing configuration fails explicitly. OAuth expiry is checked
locally; a revoked or expired SonarQube user token is detected by failed live authorization/reads.

### Running

From the repository root, with Java 21:

```bash
# Both live authentication paths (plus the local helper tests)
./gradlew :its:hostedAuthenticationTest

# OAuth only
./gradlew :its:hostedAuthenticationTest --tests '*HostedOAuthAuthenticationITest'

# Legacy token only: exercises Authorization and SONARQUBE_TOKEN separately
./gradlew :its:hostedAuthenticationTest --tests '*HostedTokenAuthenticationITest'
```

Each run checks the current deployment, even if a previous run passed. Tests perform only reads.
They set `SONARQUBE_READ_ONLY=true` and `SONARQUBE_TOOLSETS=projects`, initialize the client,
check that listed tools are read-only, and assert real fixture data returned by `list_organizations`
and `search_my_sonarqube_projects`. HTTP 200 with an MCP tool error is a failure.
OAuth passes the organization as a tool argument; legacy requests use `SONARQUBE_ORG`.

The OAuth suite also checks the unauthenticated Bearer challenge and its Staging metadata,
requires Token A to be rejected directly by the protected Cloud organizations API, and checks
that an altered JWT signature is rejected. Network failures, redirects, and HTTP 5xx do not count
as successful authentication rejection. The legacy suite checks missing and invalid credentials.

Successful Cloud reads through MCP, while the same Token A is rejected directly by Cloud,
provide functional evidence of the deployed token exchange/delegation flow. Token B and backend
client secrets stay on the server. These tests do not observe the exact exchange payload or count;
the existing mocked OAuth tests cover those details. A previously used Token A may reuse the
server's short-lived exchange cache.

Failures deliberately omit raw HTTP bodies and exception causes because they can contain tokens.
The isolated runtime has no logging provider; do not enable JDK HTTP wire logging or add one that
logs credentials. Browser login, consent UI, refresh tokens, writes, and caller-IP restrictions are
outside this suite. No deployment or OAuth acceptance result is implied by compiling it.

### Local preparation without contacting Staging

```bash
./gradlew :its:compileHostedTestJava
./gradlew :its:hostedAuthenticationTest --tests '*HostedAuthenticationSupportTest'
./gradlew :its:hostedAuthenticationTest --dry-run
```

The helper tests use synthetic credentials and a loopback HTTP server to check the MCP client
lifecycle, header/organization handling, token fixture validation, redirects, and sanitized failures.
The task graph must not include `:jar`, `:its:downloadCagBinary`, or container tests.
