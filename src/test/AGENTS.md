# Unit tests and harness

- Add regression tests for changed behavior, using the nearest existing tests as the pattern. Use pure unit tests for local transformations and the MCP harness for exposed tool behavior.
- Tool tests use `@SonarQubeMcpServerTest`, `SonarQubeMcpServerTestHarness`, WireMock, and `assertResultEquals` for JSON text. Name tests `it_should_<behavior>`.
- Exercise the advertised schema through the MCP client, not only direct `execute()` calls. Cover annotations, happy paths, relevant permission/backend errors, and empty results.
- For availability changes, cover opt-in/default behavior, supported and unsupported versions/deployments, read-only mode, and HTTP/stdio. Check both listing and direct invocation where restrictions differ.
- Set explicit versions in compatibility tests. A shared mock default or fabricated endpoint is not evidence that a real Server release supports the feature.
- For validation/output changes, include boundary values, malformed known data, valid nested structures, and large nested lists. Exercise pagination and detail retrieval after truncation.
- Keep tests isolated with temporary storage, fake credentials, and stubbed services. Telemetry stays disabled; tests enabling it must use a local collector.
- Run focused tests while iterating, then the affected suites. Tests for shared helpers must cover existing consumers; do not weaken assertions or change unrelated fixtures just to get green results.
