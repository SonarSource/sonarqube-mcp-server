# Integration Tests (ITS)

Integration tests for the SonarQube MCP Server in two categories:

1. **Docker / Testcontainers** — transport and proxied MCP server smoke tests
2. **SonarQube Cloud staging** — in-process MCP client against real SonarQube Cloud staging (like [sonarlint-core `SonarCloudTests`](https://github.com/SonarSource/sonarlint-core/blob/master/its/tests/src/test/java/its/SonarCloudTests.java))

Unit tests with WireMock remain in the root `src/test` module.

## Running tests

```bash
# Docker-based ITS (requires Docker)
./gradlew :its:integrationTest

# SonarQube Cloud staging ITS (requires token + Maven on PATH)
export SONARCLOUD_IT_TOKEN="<your-token>"
./gradlew :its:sonarCloudIntegrationTest
```

**Note:** Neither task runs from `./gradlew test` or `./gradlew build`.

## Docker ITS prerequisites

- Docker installed and running
- Java 21

## SonarQube Cloud ITS prerequisites

- Java 21
- Maven on `PATH` (one `mvn sonar:sonar` per JVM against `its/projects/sample-java-hotspot`)
- `SONARCLOUD_IT_TOKEN` for org `sonarlint-it`

## Layout

```
its/
├── build.gradle.kts
├── projects/
│   └── sample-java-hotspot/          # Maven sample (hotspot + code smell)
├── src/test/
│   ├── java/org/sonarsource/sonarqube/mcp/its/
│   │   ├── HttpTransportITest.java           # @Tag not SonarCloud (JUnit tag id)
│   │   ├── StdioTransportITest.java
│   │   └── sonarcloud/                       # @Tag("SonarCloud")
│   │       ├── AbstractSonarCloudStagingIT.java
│   │       ├── SonarCloudCoveredTools.java
│   │       ├── harness/
│   │       └── tools/*SonarCloudIT.java
│   └── resources/
│       ├── java-sonarlint-with-hotspot.xml
│       └── proxied-mcp-servers-its.json
```

## SonarQube Cloud tool coverage

Each tool exposed on staging has a `*SonarCloudIT` class. `SonarCloudToolCoverageIT` fails if a new tool is registered without a matching test.

Shared project (key prefix `sonarqube-mcp-its-sample-java-hotspot-…`), provisioned once per JVM:

1. Restore profile **SonarLint IT Java Hotspot** (`java:S4792` + `java:S1118`)
2. `POST api/projects/create` (org `sonarlint-it`)
3. `mvn clean package sonar:sonar`
4. `bulk_delete` with exact `projects` key on JVM shutdown

## CI

| Workflow                     | Task                             | When                                                       |
|------------------------------|----------------------------------|------------------------------------------------------------|
| `build.yml`                  | `:its:integrationTest`           | Every PR (matrix: STDIO / HTTP / ProxiedServer)            |
| `build.yml`                  | `:its:sonarCloudIntegrationTest` | Every PR in parallel (matrix: SonarQubeCloud; needs token) |
| `sonarcloud-integration.yml` | `:its:sonarCloudIntegrationTest` | Weekly + manual (same tests as PR matrix job)              |

## Docker ITS architecture

Tests use [Testcontainers](https://www.testcontainers.org/) to run the server and proxied server binary in Alpine Linux containers.

The `sonar-context-augmentation` binary is compiled for Alpine Linux (musl libc) and cannot run directly on most host systems.
