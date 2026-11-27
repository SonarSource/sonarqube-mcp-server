# Container integration and E2E tests

- Read [Java conventions](../src/AGENTS.md) and [test guidance](../src/test/AGENTS.md); these also apply here.
- Use existing Testcontainers and client helpers. Test packaged behavior for stdio, HTTP, and proxied tools where affected.
- Run `./gradlew :its:integrationTest` or select a class with `--tests`; Docker is required. Root `test` and `build` do not run this suite.
- Keep Server versions explicit when testing compatibility boundaries. Preserve coverage for the oldest supported behavior when adding a newer fixture.
- Use fake credentials for local-only checks and the existing secret-managed test credential for staging E2E. Keep telemetry isolated. Changes to container setup, startup, auth, or transports need the relevant container coverage; report unavailable Docker/dependencies rather than claiming a pass.
