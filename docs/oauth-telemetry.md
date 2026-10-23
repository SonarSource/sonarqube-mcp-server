# HTTP authentication telemetry

Gessie receives two new events for OAuth adoption and reliability reporting. Existing `McpToolInvoked` version 2 is unchanged. HTTP attribution comes from the current request, without sessions, a client registry, or client configuration. Stdio keeps its existing tool telemetry.

## Event contracts

All events use the existing envelope: `metadata.event_id`, `metadata.event_type`, `metadata.event_version`, `metadata.event_timestamp`, `metadata.source.domain = MCP`, and `event_payload`. Retries reuse the envelope and event ID; consumers must deduplicate by event ID.

Common payload fields are `mcp_server_id`, `mcp_server_version`, `connection_type` (`SQC` or `SQS`), and `transport_mode` (`http`, `https`, or `stdio`). Unknown optional fields are omitted. Timestamps are Unix milliseconds; durations are milliseconds.

### Analytics.Mcp.McpClientInitialized, version 1

One observation per successful HTTP `initialize` request:

- `authentication_method`: `oauth` or `token`.
- `calling_agent_name`, `calling_agent_version` (optional): exact client-reported `clientInfo` values.
- `initialization_timestamp`: when successful initialization is observed.
- `oauth_client_key` (optional): verified OAuth application correlation key; absent for token authentication.
- `user_uuid` (optional): authenticated user's SonarQube UUID, resolved using the request credential.
- `sqs_installation_id` (optional): SonarQube Server installation ID; absent for Cloud.

Identity enrichment runs asynchronously and is best effort. User and Server installation lookups fail independently; failures never discard the initialization event or change the response. OAuth enrichment uses verified Token B. Initialization does not resolve an organization or send `organization_uuid_v4`: OAuth users select organizations later through tool arguments.

An initialization is an observation, not a unique client, user, session, or completed browser login. Clients may initialize repeatedly, and later requests may reach other replicas.

### Analytics.Mcp.McpToolInvoked, version 2 (unchanged)

Existing fields remain: `invocation_id`, `tool_name`, optional `organization_uuid_v4`, `sqs_installation_id`, `user_uuid`, `calling_agent_name`, `calling_agent_version`, `tool_execution_duration_ms`, `is_successful`, optional `error_type`, `response_size_bytes`, optional `container_arch`, `invocation_timestamp`, and `matching_toolsets`, plus common fields.

No authentication method or OAuth application key is added. Client name/version keep their existing stdio behavior; HTTP labels are initialization observations. Failed identity enrichment must not discard execution outcomes. A tool denied before execution, including by scope enforcement, does not emit this event.

### Analytics.Mcp.McpOAuthAuthenticationCompleted, version 1

One outcome per call to OAuth authentication, including cache hits and callers sharing an in-flight exchange:

- `authentication_timestamp`: request authentication start time.
- `authentication_duration_ms`: monotonic elapsed verification/exchange time, including shared-exchange waiting and excluding telemetry delivery.
- `outcome`: `success`, `rejected`, or `service_failure`.
- `failure_stage` (on failure): `mcp_token_verification`, `delegation_validation`, `token_exchange`, or `cloud_token_validation`.
- `failure_category` (on failure): `invalid_token`, `insufficient_scope`, `unavailable`, `invalid_exchange`, or `unexpected_error`.
- `oauth_client_key` (optional): available only after Token A passes signature and standard claim verification and has a readable, nonblank `azp`.

This event performs no identity lookup. HTTP 401/403 authentication failures are `rejected`. Upstream outages and unacceptable Token B responses are `service_failure`. Sanitized stages survive shared exchange failures; unclassified cache-load failures retain the existing unavailable response and use `token_exchange`.

Anonymous discovery challenges and legacy token authentication do not emit this event. It measures request outcomes, not browser login/consent, refresh-token issuance, or login conversion. Successful authentication can precede failed tool execution or a tool-specific scope denial.

## Application correlation and privacy

`oauth_client_key` is lowercase hexadecimal SHA-256 of UTF-8 `configured_issuer + "\0" + verified_Token_A_azp`, with one NUL separator. It identifies an OAuth application within an issuer, not a user or token. It stays stable across token refresh and replicas; different issuers/client IDs produce different keys. The exchange cache's private token fingerprint is never emitted.

Initialization adds pseudonymous user and Server installation identifiers. These are correlation data, not anonymous data, and require DPO/PM review under existing retention/access rules. The application hash also provides correlation rather than anonymization: known client IDs can be tested against it. Client labels remain untrusted free text exactly as reported, so they could contain personal data.

No credentials, tokens, token fingerprints, raw Auth0 subjects, tool arguments/results, or upstream error bodies are added. Authentication outcome events contain no user, organization, or installation identifier.

## Reporting

Deduplicate every stream by `metadata.event_id`, and apply the same reporting window before joining. Aggregate initialization observations first to avoid multiplying request/tool counts.

- Adoption by client/method: count initialization observations; count distinct `user_uuid` for observed users. Report missing UUIDs separately. For Server, scope user UUIDs by installation ID.
- Client labels for OAuth reliability: group initialization labels by `oauth_client_key`; one distinct name is usable, multiple names are `Ambiguous`, and missing observations are `Unknown`. Versions describe initialization observations only.
- Authentication reliability: group outcome counts and duration distributions by application, stage, and category. Label the metric request reliability, not end-to-end login success.
- Tool outcomes among observed OAuth users: select distinct Cloud user UUIDs from OAuth initialization, then join existing tool events to that user cohort. A user can use multiple methods and applications: this does **not** attribute individual calls to OAuth, a client, or a client version.
- Coverage: report missing user UUIDs, missing app keys, unknown/conflicting labels, and the observation window explicitly. Initialization coverage is incomplete when clients skip initialization or telemetry is dropped.

Example query shapes, after event-ID deduplication (adapt view names to deployed Redshift views):

```sql
-- Tool outcomes for users observed initializing with OAuth, regardless of a call's auth method.
WITH oauth_users AS (
  SELECT DISTINCT event_payload.user_uuid::varchar AS user_uuid
  FROM mcp_client_initialized_events
  WHERE event_payload.authentication_method::varchar = 'oauth'
    AND event_payload.connection_type::varchar = 'SQC'
    AND event_payload.user_uuid IS NOT NULL
)
SELECT t.event_payload.tool_name::varchar AS tool_name,
  COUNT(*) AS invocations,
  SUM(CASE WHEN t.event_payload.is_successful::boolean THEN 1 ELSE 0 END) AS successes
FROM mcp_tool_invoked_events t
JOIN oauth_users u ON t.event_payload.user_uuid::varchar = u.user_uuid
WHERE t.event_payload.connection_type::varchar = 'SQC'
GROUP BY 1;

SELECT event_payload.outcome::varchar AS outcome,
  event_payload.failure_stage::varchar AS stage,
  event_payload.failure_category::varchar AS category,
  COUNT(*) AS authentication_requests,
  AVG(event_payload.authentication_duration_ms::bigint) AS mean_duration_ms
FROM mcp_oauth_authentication_completed_events
GROUP BY 1, 2, 3;
```

## Delivery and rollout

`TELEMETRY_DISABLED=true` suppresses both new events and existing tool telemetry. Delivery and identity lookups are asynchronous. A bounded queue of 1,000 pending tasks limits enrichment/dispatch backlog; a full queue or shutdown drops work without changing responses. This is best-effort telemetry, not an audit trail or a bound on in-flight HTTP deliveries/retries. Shutdown drains queued work subject to the existing timeout.

Register both events in `sonardata-delivery-definition/event_definitions/sonarqube_mcp_server/`. No Auth0 configuration change is required. Before production rollout, validate ingestion and reporting views in Dev8 using development Gessie, including identity coverage, application joins, failure categories, opt-out, and existing token/stdio metrics. DPO/PM approval remains a rollout requirement.
