# MCP server review focus

- Check changed tool names, input schemas, required fields, defaults, output shapes, and annotations against actual invocations and advertised behavior.
- Preserve intentional Cloud/Server registration and entitlement differences, including feature flags.
- Check availability separately for stdio and HTTP; intentionally unsupported combinations are not regressions.
- Trace HTTP credentials, server selection, and authorization within each request; prevent cross-client leaks or reuse of stdio-only context.
- Report concrete contract or isolation failures with the affected environment, invocation, evidence, and source location.
