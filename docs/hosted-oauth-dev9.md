# Hosted MCP OAuth prototype for Dev9

OAuth is opt-in. The default `TOKEN` mode and stdio transport retain their existing authentication behavior. This first pass accepts GitHub users and rejects organization-bound tokens while enterprise SSO is deferred.

The MCP endpoint verifies Token A using the configured Auth0 issuer and cached JWKS, requiring an RS256 signature, the MCP audience, user identity, originating client, issuance and expiration, and a lifetime of at most five minutes. The initial challenge requests `read:all`; protected-resource metadata advertises independent `read:all` and `write:all` scopes.

Every native Auth0 OBO request explicitly supplies only the delegation scopes from the verified Token A. It never relies on an omitted or default exchange scope. The returned Token B must verify against the same issuer and the separate Cloud audience, preserve the user, identify the backend in both `azp` and the outer `act.sub`, identify the originating client in the nested actor, and contain a nonempty subset of Token A's delegation scopes. An unexpected broader grant is rejected. Only Token B enters the Cloud API request context; Token A goes only to Auth0's exchange endpoint.

An authenticated MCP POST does not require write delegation simply because the transport uses POST. A read-only delegation narrows tool visibility and execution through the existing read-only tool filter. The downstream Cloud authenticator applies the authoritative method rule: GET requires `read:all`; every other method requires `write:all`, independently. Existing Cloud permissions remain applicable.

## Configuration

```sh
SONARQUBE_TRANSPORT=http
SONARQUBE_IS_CLOUD=true
SONARQUBE_URL=https://dev9.sc-dev9.io
SONARQUBE_CLOUD_API_URL=https://api.sc-dev9.io
SONARQUBE_HTTP_AUTH_MODE=OAUTH
SONARQUBE_OAUTH_RESOURCE=https://api.sc-dev9.io/mcp
SONARQUBE_OAUTH_ISSUER=https://auth-dev9.sc-dev9.io/
SONARQUBE_OAUTH_CLOUD_AUDIENCE=https://api.sc-dev9.io/
SONARQUBE_OAUTH_CLIENT_ID=<mcp-backend-client-id>
SONARQUBE_OAUTH_CACHE_TTL_SECONDS=30
SONARQUBE_OAUTH_CACHE_SIZE=256
```

Inject `SONARQUBE_OAUTH_CLIENT_SECRET` from Secrets Manager into the container; do not place its value in files, logs, source control, or deployment command arguments. The Dev9 backend credential is stored in `Authentication-McpOAuth-BackendClient-Dev9`. Both Cloud URLs must be explicitly configured. The Cloud API URL must match the Token B audience and the resource must share its API origin. The Cloud website URL must use HTTPS. Auth0 exchange requests use the issuer's HTTPS `/oauth/token` endpoint, a five-second connection timeout, a ten-second request timeout, bounded responses, and no redirects.

Clients use `Authorization: Bearer <Token A>`. The legacy `SONARQUBE_TOKEN` header is accepted only in token mode. OAuth does not require `SONARQUBE_ORG`; select an ordinary organization through tool arguments. OAuth rejects a `SONARQUBE_ORG` header to avoid mixing selection with trusted authentication context. Optional `SONARQUBE_READ_ONLY` and `SONARQUBE_TOOLSETS` headers can narrow the available tools.

Metadata is public at `/.well-known/oauth-protected-resource/mcp`. Route this path through the hosting proxy to the MCP container. Missing or invalid authentication produces HTTP 401 with the metadata challenge. A verified token without delegation produces HTTP 403 with an `insufficient_scope` challenge. Auth0/JWKS outages produce 503; an unacceptable exchange response produces 502. Cloud permission errors continue through the existing tool error handling.

## Cache and revocation

The process-local LRU cache holds at most 256 exchange results by default. Its key includes the SHA-256 fingerprint of the full authorization token, issuer, user, originating client, Cloud audience, and delegation scopes. Different authorizations for the same user remain isolated. Tokens and credentials are absent from cache keys and diagnostic string representations.

The cache expires at the earliest of Token A expiration, Token B expiration, and its configured TTL (at most 30 seconds). Setting its TTL to zero disables reuse. The server verifies Token A on every request and rechecks cached Token B signatures and expiration. At most 16 exchanges run concurrently; failures release that capacity.

JWT signature validation does not establish immediate access-token revocation. The five-minute token lifetime and 30-second exchange-cache TTL are separate limits, and refresh-token revocation is owned by Auth0 and the client. Measure the actual post-revocation acceptance window in Dev9 before declaring the live flow verified.

## Verification scope

Unit tests exercise signed tokens, independent delegation combinations, signature/issuer/audience/actor/user/organization failures, broader returned scopes, authorization isolation, bounded caching and concurrency, exact native exchange form parameters, response redaction and limits, and HTTP context injection. A read-only Token A is accepted for an MCP POST without an organization header, and the trusted context contains only Token B.

Deployment, real Cloud permission checks, read/write operations on disposable resources, refresh/revocation timing, and client compatibility require separate live Dev9 verification.

## Local verification on October 6, 2026

The Gradle wrapper was used with the installed Corretto 25 runner; the repository retained its Java 21 toolchain. Dependency locks were regenerated through the wrapper with configured Repox credentials.

| Command | Observed result |
|---|---|
| `./gradlew :dependencies :its:dependencies --write-locks` | `BUILD SUCCESSFUL in 1m 27s`; lock changes add only Auth0 `java-jwt:4.6.1` and `jwks-rsa:0.24.1` |
| `./gradlew test --tests 'org.sonarsource.sonarqube.mcp.authentication.*' --tests 'org.sonarsource.sonarqube.mcp.configuration.McpServerLaunchConfigurationHttpTest' --tests 'org.sonarsource.sonarqube.mcp.transport.HttpServerTransport*Test' --tests 'org.sonarsource.sonarqube.mcp.SonarQubeMcpServerHttpTest'` | `BUILD SUCCESSFUL in 52s`; XML totals: 142 tests, zero failures/errors/skips |
| `./gradlew :build` | Compilation, packaging, and license tasks passed; 1,133 tests ran with three failures; `BUILD FAILED in 7m 17s` |
| `./gradlew :test --tests '*ListLanguagesToolTests*it_should_return_the_languages_list'` | Both language-list variants passed on a focused rerun; `BUILD SUCCESSFUL in 11s` |

Two full-suite failures are existing outside-workspace assertions in `AnalyzeCodeSnippetToolTests` and `RunAdvancedCodeAnalysisToolTests`. Both reproduce in an unchanged `HEAD` snapshot at commit `58d283d`: four focused tests ran, two failed, and both language-list variants passed. The traversal fixture `../../etc/passwd` resolves under the macOS temporary directory to a nonexistent path. The unchanged resolver calls `toRealPath()` before its outside-workspace check, producing `Could not read file` rather than the expected `outside the configured` message. The baseline snapshot is `/private/tmp/mcp-oauth-baseline-qsvn4o8w`.

The third full-suite failure was a Cloud language-list server-initialization 404. It did not reproduce in either the unchanged baseline's focused run or the current checkout's focused rerun. This remains a full-suite verification limitation; the full build is not reported as passing. Docker integration tests and a live Dev9 deployment have not been run.

## Reversible Cloud V2 acceptance operations

The opt-in `system` category now includes three Cloud-only group administration tools, alongside the existing Server-only system health/administration tools. The category remains disabled by default. Enable `SONARQUBE_TOOLSETS=projects,system` for this acceptance fixture. Existing Server tools remain unavailable in Cloud mode; Cloud group tools remain unavailable in Server mode.

| Tool | Ordinary arguments | Cloud request |
|---|---|---|
| `list_groups` | `organizationId` UUID; optional exact `name`, `pageIndex`, `pageSize` | GET `/users/groups?organizationIds=<UUID>&name=<name>` |
| `create_group` | `organizationId` UUID, `name`; optional `description` | POST `/users/groups`, JSON body containing only those fields |
| `delete_group` | Recorded `groupId` UUID | DELETE `/users/groups/<UUID>`; requires the specified 204 response |

These use `SONARQUBE_CLOUD_API_URL`, and are independent of legacy Core `/api` authentication. The [Users public contract](https://github.com/SonarSource/sonarcloud-users/blob/HEAD/portal-api/portal-api-external-platform/api-definitions/external_api.yaml) and [controller](https://github.com/SonarSource/sonarcloud-users/blob/HEAD/users/application/src/main/java/io/sonarcloud/users/controller/GroupsController.java) were inspected before implementing the wrappers. The existing Users service requires organization or enterprise administrator permission. It rejects ordinary callers changing managed groups or deleting built-in groups. The MCP tools retain those API checks and propagate permission failures; selecting an organization in a tool argument adds no authorization.

For live acceptance, use a disposable organization where the GitHub user is administrator and a separate organization where that user lacks administrator access. Create one uniquely named empty custom group, record its returned ID, list it by the exact name, delete only that recorded ID, and verify absence. The creation tool sends no memberships, roles, built-in, or managed-group fields. Delete is marked destructive and idempotent. Keep both fixtures' real Cloud permissions in the test matrix.

Example calls:

```json
{"name":"list_groups","arguments":{"organizationId":"<allowed-organization-UUID>","name":"mcp-oauth-fixture-<unique-suffix>"}}
{"name":"create_group","arguments":{"organizationId":"<allowed-organization-UUID>","name":"mcp-oauth-fixture-<unique-suffix>","description":"Empty OAuth acceptance fixture"}}
{"name":"delete_group","arguments":{"groupId":"<recorded-created-group-UUID>"}}
```

GET requires only `read:all`; POST and DELETE require only `write:all` at the downstream authenticator. A read-only authorization narrows the MCP tool list and rejects mutation calls. A write-only authorization can call the mutations without obtaining read delegation; reading back for cleanup verification uses a separately authorized read or combined grant.

Verification for this addition:

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-25.jdk/Contents/Home ./gradlew :test --tests 'org.sonarsource.sonarqube.mcp.tools.groups.*' --tests 'org.sonarsource.sonarqube.mcp.serverapi.users.GroupsApiTest' --tests 'org.sonarsource.sonarqube.mcp.http.HttpClientProviderTests' :license
```

Observed output: `BUILD SUCCESSFUL in 18s`; XML totals: 29 tests, zero failures/errors/skips. Tests cover the separate API origin, exact empty-group creation body, authenticated DELETE, normal permission and managed-group failures, annotations, Cloud/Server registration, opt-in categories, read-only filtering, pagination, and invalid IDs/field lengths. No live group mutation was performed, and fixture access and deployed contract compatibility still require Dev9 verification. The full suite was not repeated after this isolated addition; its earlier baseline limitations remain recorded above.

## Final check after rebasing

Rebased the feature onto upstream `291bbe4` (Logback 1.6.5). Wrapper-generated locks retain that upstream version and add only the two Auth0 libraries. The final combined OAuth/configuration/HTTP/group/transport test selection, plus `:license` and `:jar`, returned `BUILD SUCCESSFUL in 36s`: 172 tests, zero failures/errors/skips. This includes the HTTP 403 `insufficient_scope` challenge for a verified token without delegation. Test identity data is synthetic.

The packaged `build/libs/sonarqube-mcp-server-1.29-SNAPSHOT.jar` is 58,666,900 bytes (55.95 MiB). The full suite was not repeated after the rebase; the earlier baseline and intermittent test limitations, unrun Docker tests, and pending live Dev9 acceptance remain applicable.
