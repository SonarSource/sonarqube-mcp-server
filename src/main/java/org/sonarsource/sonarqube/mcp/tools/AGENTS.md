# MCP tools

Read [the tool development guide](../../../../../../../../docs/development/mcp-tools.md) for implementation steps and contract checks.

- Build definitions with `ToolDefinitionBuilder`; reuse project, organization, branch/PR, and argument helpers. Assign an existing domain category where possible.
- Treat the advertised name, input schema, annotations, and response as an API. Keep schema types, bounds, required fields, enums, and execution validation consistent.
- Keep descriptions to the shortest text that disambiguates usage. Omit rarely needed parameters; put tutorials and full model examples in docs.
- Return typed response records through `Tool.Result.success`: one JSON text response, no advertised output schema or duplicate payload. Do not add `@JsonPropertyDescription`.
- Bound nested collections and large strings as well as page counts. Detail and recovery paths need limits too; expose truncation and a usable way to retrieve remaining data.
- Do not report malformed, unavailable, or partially read data as an empty successful result. Preserve exact identifiers and distinguish counts from complete exports.
- Mark reads with `readOnlyHint` and destructive writes accordingly. Preserve read-only and OAuth scope enforcement; dry-run and input validation must complete before mutation.
- Keep network/DTO handling in `serverapi`, presentation and MCP limits here. Shared argument or builder changes affect every toolset and need shared regression tests.
