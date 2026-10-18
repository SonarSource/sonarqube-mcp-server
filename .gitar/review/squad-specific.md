# MCP server review focus

Apply these checks only when the diff touches the relevant behavior.
Leave an advisory finding only for a concrete failure supported by the changed
code and its callers; name the failing scenario and source location.
Update an existing thread for the same concern, preserve unresolved evidence,
and remain quiet when the checks pass. Do not approve, block, or modify the PR.

## MCP contracts

- For changed tools, compare names, input schemas, required fields, defaults,
  output shape, and annotations against actual callers and advertised behavior.
  Show a broken invocation or misleading capability before raising a concern.
- Start with `src/main/java/org/sonarsource/sonarqube/mcp/tools/Tool.java` and
  `src/main/java/org/sonarsource/sonarqube/mcp/tools/ToolDefinitionBuilder.java`,
  then inspect the changed tool's implementation and existing tests.
- Distinguish a deliberate documented contract change from an accidental break;
  do not request extra schema fields or abstractions without a failing use case.

## Registration, transport, and request isolation

- When affected, trace Cloud versus Server registration, entitlements, feature
  flags, and stdio versus HTTP availability in
  `src/main/java/org/sonarsource/sonarqube/mcp/SonarQubeMcpServer.java`.
- For authentication or transport changes, check that HTTP credentials, server
  selection, and authorization remain scoped to the current request and cannot
  leak between clients or reuse stdio-only context. Relevant paths include
  `src/main/java/org/sonarsource/sonarqube/mcp/authentication/AuthenticationFilter.java`,
  `src/main/java/org/sonarsource/sonarqube/mcp/transport/HttpServerTransportProvider.java`,
  and `src/main/java/org/sonarsource/sonarqube/mcp/tools/ToolExecutor.java`.
- Cite the specific environment, invocation, and failure. Missing support in an
  intentionally unsupported transport or entitlement is not itself a regression.
