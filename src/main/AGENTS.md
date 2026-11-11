# Production boundaries

- Reuse the existing configuration and registration paths in `SonarQubeMcpServer`. Resolve deployment through configuration; the presence of an organization argument alone does not identify Cloud.
- New optional toolsets stay opt-in unless the task explicitly changes defaults. Preserve existing version, edition, entitlement, feature-flag, and transport restrictions; a schema or draft upstream PR is not proof of released support.
- Unsupported tools must be absent from discovery and rejected on direct calls. Keep deployment gates consistent in HTTP and stdio; document and test intentional transport differences.
- HTTP request toolsets and read-only preferences may only narrow startup availability. Enforce authorization on both `tools/list` and `tools/call`.
- Resolve one `ServerApi` for the invocation and reuse its selected credentials and organization. Never substitute the startup token for an HTTP caller or share caller-specific state across requests.
- Use the trusted authentication result; do not re-read raw headers or weaken OAuth validation to make a tool work. Preserve request-scoped cleanup on failures.
- Reuse startup checks where possible and avoid feature-specific network work when its toolset is disabled. Analyzer downloads must not delay otherwise eligible REST tools.
- Before changing lifecycle or security behavior, read [tool loading](../../docs/tool-loading.md), [HTTP authentication](../../docs/http-authentication-architecture.md), or [stdio transport](../../docs/stdio-transport-architecture.md) as relevant. Proxied tools also follow [the proxy design](../../docs/proxied-mcp-servers.md).
