# SonarQube API clients

- Verify endpoints, field semantics, required/null values, enums, and pagination against the public contract for each target deployment. Record the source/version in the PR; internal UI APIs and mocks are not compatibility evidence.
- Separate API existence from product availability. Establish a supported release/edition or explicit feature gate; state unresolved upstream dependencies instead of assuming a draft will ship.
- Add domain clients through the `ServerApi` facade. Reuse `ServerApiHelper`, `UrlBuilder`, and the configured HTTP client; do not create parallel auth or transport paths.
- Use response records and Gson, public path constants, and try-with-resources for responses. Keep MCP summaries and output limits in the tool layer.
- Validate required response structure before primitive defaults or null handling can turn malformed data into zeros or empty success. Enforce only invariants guaranteed by the contract.
- Let standard authorization and HTTP exceptions propagate to the existing executor mapping. Distinguish absent, forbidden, unavailable, and malformed responses.
- Never retry an ambiguous write automatically. Preserve endpoint-specific idempotency and partial-failure behavior; sanitize diagnostics without logging payloads or credentials.
