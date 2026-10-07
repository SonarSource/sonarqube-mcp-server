# Cloud onboarding

The MCP tools mirror the Cloud APIs used by CLI PR #970, with short, resumable calls for remote agents. Sign-in belongs to the MCP transport and is not a tool. Organization import and billing are separate actions so repository discovery can reuse an existing project without signup.

## Dev9 deployment

Set `SONARQUBE_URL=https://dev9.sc-dev9.io`, `SONARQUBE_IS_CLOUD=true`, and `SONARQUBE_CLOUD_API_URL=https://api.sc-dev9.io`. Tools do not accept host overrides. `discover_github_repository` returns both configured hosts plus the live environment name/API host from public static configuration. It rejects a web/API mismatch, and the agent verifies the selected environment before mutations.

This branch builds on OAuth PR #601. Dev9's authorization endpoint is `https://auth-dev9.sc-dev9.io/authorize`; resource metadata is owned by the Authentication API at `https://api.sc-dev9.io/authentication/.well-known/oauth-protected-resource/mcp`. The client discovers the issuer and handles browser sign-up/sign-in, PKCE and credential storage. The MCP server verifies access tokens and exchanges them for a caller-bound Cloud token before API calls. A new user does not need a pre-existing token or organization.

Onboarding requires both `read:all` and `write:all`. The OAuth transport restricts read-only grants to read-only tools, including rejecting direct mutation calls. OAuth requests must not contain `SONARQUBE_ORG`; onboarding uses explicit arguments and project findings use explicit identifiers. Never pass credentials through tool arguments or results.

For local OAuth hosting, configure `SONARQUBE_HTTP_AUTH_MODE=OAUTH`, `SONARQUBE_OAUTH_ISSUER=https://auth-dev9.sc-dev9.io/`, `SONARQUBE_OAUTH_RESOURCE=https://api.sc-dev9.io/mcp`, `SONARQUBE_OAUTH_CLOUD_AUDIENCE=https://api.sc-dev9.io/`, the backend's `SONARQUBE_OAUTH_CLIENT_ID`, and securely injected `SONARQUBE_OAUTH_CLIENT_SECRET`, alongside the dev9 web/API settings above. Use `SONARQUBE_OAUTH_METADATA_URL=https://api.sc-dev9.io/authentication/.well-known/oauth-protected-resource/mcp` for the deployed metadata owner. No startup `SONARQUBE_TOKEN` is needed. The OAuth client must be registered/configured for the intended local callback. Local hosting still requires the backend exchange credentials; these are server settings, not credentials the new user must obtain.

## Organization context

HTTP requests still require caller authentication. An absent `SONARQUBE_ORG` is permitted for account-scoped onboarding and explicitly scoped project findings. In legacy token/stdio mode, the provider's normal `get()` rejects Cloud organization-wide operations without organization context; OAuth preserves PR #601's organization-independent API access and relies on endpoint validation; `getForOnboarding()` and `getForProject()` allow an authenticated request without it. Findings tools use explicit project/analysis/issue identifiers; unscoped issue search still requires organization context.

No selected organization or token is stored by these tools. Each HTTP call obtains a request-local API client. Organization-wide tools use the deployment's default organization; legacy token-mode clients may supply `SONARQUBE_ORG`, while OAuth rejects that header. A deployment with a default organization still rejects conflicting organization headers. Stdio continues to require a configured token; when it has no memberships, startup allows onboarding instead of failing. Import does not rewrite stdio configuration; use the returned key for explicit onboarding/project calls or configure `SONARQUBE_ORG` for organization-wide tools.

## Resumption and billing

Organization import returns a validated GitHub installation URL when access is missing. It never opens a browser on the server; the agent/client owns the handoff. Call again after consent. A webhook 404 returns `pending`; authentication/permission/transport failures propagate.

Subscriptions are read before creation. Existing plans/trials are preserved. A new Team trial omits `priceId`, verifies active status, future trial dates and payment method `NONE`; explicit Free uses a unique zero-cost price. No paid fallback is attempted. Calls are not transactional: if signup was submitted and visibility is delayed, the result is `pending` and all subsequent checks must set `createIfMissing=false`. A visible subscription on such a resume call is validated against the requested plan and returns `ready`; it cannot skip trial/Free verification as a reused subscription. `organization_pending` means no signup was submitted because the UUID is not yet visible; repeat the original call after the suggested delay. A failed request after a subscription POST is also potentially submitted: inspect billing with `createIfMissing=false` before attempting creation again. Backend uniqueness/idempotency remains necessary for concurrent callers; the MCP server does not persist a cross-request operation ledger.

Repository import checks binding and access, reuses existing project keys and never fabricates installation keys. Pending provisioning should be resolved with read-only discovery before considering another import. Analysis status reuses completed default-branch analyses; no claim is made about uncommitted changes or fresh scans. Asynchronous eligibility returns pending, unsupported analysis returns `ci_required` with a reason.

The portable skill is distributed here and in the CLI repository. Keep both copies and their MCP reference aligned when changing the tools or workflow. The combined local OAuth/onboarding regression tests use a test authenticator to verify token forwarding and scope enforcement. A complete live dev9 journey still needs browser consent and verification against the deployed endpoints.
