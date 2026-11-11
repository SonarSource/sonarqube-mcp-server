# Docker integration tests

- Read [Java conventions](../src/AGENTS.md) and [test guidance](../src/test/AGENTS.md); these also apply here.
- Use existing Testcontainers and client helpers. Test packaged behavior for stdio, HTTP, and proxied tools where affected.
- Run `./gradlew :its:integrationTest` or select a class with `--tests`; Docker is required. Root `test` and `build` do not run this suite.
- Keep Server versions explicit when testing compatibility boundaries. Preserve coverage for the oldest supported behavior when adding a newer fixture.
- Keep credentials fake and telemetry isolated. Changes to container setup, startup, auth, or transports need the relevant integration coverage; report unavailable Docker/dependencies rather than claiming a pass.
