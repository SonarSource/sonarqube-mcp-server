# MCP server review focus

- Check changed tool names, input schemas, required fields, defaults, output shapes, and annotations against actual invocations and advertised behavior.
- Check availability separately for stdio and HTTP; intentionally unsupported combinations are not regressions.
- Trace HTTP credentials, server selection, and authorization within each request; prevent cross-client leaks or reuse of stdio-only context.
