# Onboarding through MCP

Use this path when the user selects MCP or the CLI/local shell is unavailable. The tools run on the MCP server and use the authenticated caller's connection. Organization keys are explicit tool arguments; onboarding never stores a shared user's selected organization.

## Environment and sign-in

Use an MCP deployment configured for the selected web/API pair. For this hackathon the deployment needs `SONARQUBE_URL=https://dev9.sc-dev9.io`, `SONARQUBE_CLOUD_API_URL=https://api.sc-dev9.io`, and `SONARQUBE_IS_CLOUD=true`. These are deployment settings, not tool arguments; do not assume tools can switch hosts. Discovery reads the public static configuration itself and rejects a mismatched API host. It returns only the environment name and API host, not other public configuration keys.

Complete sign-in through the client's MCP OAuth connection, choosing GitHub. The user can sign up in the browser without an existing token or Cloud organization. Dev9 advertises resource metadata at `https://api.sc-dev9.io/authentication/.well-known/oauth-protected-resource/mcp`, with resource `https://api.sc-dev9.io/mcp` and issuer `https://auth-dev9.sc-dev9.io/`. The authorization endpoint is `https://auth-dev9.sc-dev9.io/authorize`; let the client discover/configure authorization metadata and handle PKCE, callback and token storage rather than constructing a URL manually.

Request both `read:all` and `write:all` for onboarding. The server verifies the MCP access token and exchanges it for a caller-bound Cloud token. Read-only grants omit and reject setup tools. If mutation tools are missing after sign-in, check delegated scopes and deployment toolsets before diagnosing missing Cloud setup; reconnect with write consent when onboarding is authorized. Never ask for a token in chat or pass credentials to a tool. Reuse a valid connection. An expired/invalid MCP session may require one client reconnect; first distinguish that from the split upstream authorization failure below.

OAuth requests must omit `SONARQUBE_ORG`; organization selection belongs in onboarding arguments. The explicit project findings tools work without this header. Legacy token-mode clients may still use organization headers for organization-wide tools.

Resolve the exact GitHub `OWNER/REPO` from repository context or the user's supplied target. If a remote agent cannot read a remote or repository context, ask for the slug. Do not require a local checkout merely to onboard a known repository.

Call `discover_github_repository` with `repository: OWNER/REPO`. Check the returned `server` equals the selected web host and `apiServer` equals the selected API host (allow a trailing slash). Also check `environment.name` is `dev9` for this hackathon. If any value differs, stop and request the correct connection before any mutation. Discovery paginates member organizations, matches the GitHub binding and returns exact repository bindings. Authorization or connectivity errors are errors, never evidence that setup is missing.

## Diagnose partial authorization

After an authorization failure, make one read-only v1 probe and one read-only v2 probe through this same connection, inspecting actual tool schemas. For v1, use an explicit-project `list_branches` when a known accessible project exists, or `discover_github_repository` to probe `/api/organizations/search` when onboarding tools are available. The latter is a discovery probe that also verifies environment metadata. For v2, use `list_enterprises`. Capture which upstream request actually failed; absence of a tool is a deployment capability issue, not an HTTP authorization result.

If a v1 request returns 401/403 while v2 succeeds (an empty successful enterprise list still counts), the MCP session works for at least that v2 call. Treat this as evidence of a server-side token-exchange, delegated-scope or v1 authorization compatibility problem, not as proof that the user needs another browser sign-in. A 403 can also reflect endpoint-specific permissions; report the split for operator investigation rather than declaring its cause proven. Stop onboarding mutations and report environment, tool, upstream path, status and timestamp, without tokens. Do not loop reconnects or ask the user to generate a PAT. If both probes reject an expired session, reconnect once through the client and retest; persistent failures stop the flow.

When the user explicitly chose MCP and it is broken, report the problem and offer a capable local CLI only if they have not ruled it out. Wait for their choice before switching; retain completed organization/project setup.

## Reuse existing setup

Each discovery match includes `organizationKey`, `admin`, and matching `repositories` with `linkedProjects`.

- One linked project: reuse its key; skip organization import, subscription signup and repository import. Read analysis status and findings.
- Multiple organizations or linked projects: use a verified explicit key or ask which project to review. Do not choose by similar name.
- Organization found but repository not linked: retain its binding. Once repository access is confirmed, call `ensure_cloud_subscription` as an admin to reuse its existing plan or start a trial if unsubscribed, then import the project. The by-key organization lookup can omit `alm`, so use the discovery match for the binding. Repository absence indicates access still needs approval; do not change visibility or fabricate an installation key.
- No matching organization: proceed with missing organization setup below.

Viewing findings does not require organization-admin permissions. Setup mutations do.

## Organization and repository access

Call `import_github_organization` with `github: OWNER`. Pass `organizationKey` only for an explicit key or a collision resolution; pass `installationId` only when known from the actual GitHub App installation.

- `status: browser_required`: explain repository access before opening the exact returned `url` with the client/browser capability, or provide the link if browser opening is unavailable. Verify HTTPS and `github.com`. The user approves the App for the account and requested repository. Return to the agent afterwards; the Cloud redirect does not require manual organization creation or plan selection. After approval, call the same import tool again. A `browser_required` response can repeat until installation is discoverable; do not open repeated tabs automatically.
- `status: pending`: installation webhook is not visible yet. Wait at least `retryAfterSeconds` and repeat the same call, up to 10 minutes overall.
- `status: ready`: use the returned `organizationKey`. This confirms organization binding, not access to every repository and not an active subscription.

The tool reuses administrable bindings, binds an unbound organization when applicable, and refuses to replace another DevOps binding. If the default key collides, choose an available `<owner>-dev9` key with a numeric suffix as needed. If the user explicitly requested the conflicting key, ask before substituting it. Preserve completed steps and propagate authorization errors.

Call discovery again after organization binding. Confirm the target repository is listed before announcing repository access is ready.

## Subscription when missing

Call `ensure_cloud_subscription` for a newly created organization or a matching one with no linked project, after repository access is confirmed. Use `organizationKey` and `plan: team-trial` (default), or `plan: free` when requested or when the automatic fallback rules permit it after a failed new trial. An organization with existing linked projects skips this tool. The tool preserves existing subscriptions. For an unsubscribed organization, it treats dev9 billing 404 as no subscription and can create the requested trial; after a pending or uncertain POST, use `createIfMissing: false`.

- `reused`: preserve the returned subscription, including an existing Free, paid, expired or paused subscription. Never replace it to get a trial.
- `ready`: newly submitted or resumed signup has been read back and verified. A Team trial is verified active, with a valid future expiry and no payment method; Free uses a unique zero-cost price.
- `organization_pending`: the UUID is not yet visible and no signup was submitted. Wait at least `retryAfterSeconds` and repeat the original call.
- `pending`: billing visibility is delayed after signup. Repeat with `createIfMissing: false` and the same organization/plan, waiting at least `retryAfterSeconds`. This reads status without another signup POST. If there is still no subscription after 10 minutes, stop and report incomplete billing; do not retry creation blindly.

For a newly submitted trial that becomes visible on a later status call, verify `subscription.planKey: team`, `trial: true`, `status: active`, a valid future `trialPeriod.end` after its start, and `paymentMethodStatus: NONE` before continuing. If a request fails after signup may have been submitted, use `createIfMissing: false` to inspect billing before any creation retry. If a new trial fails, announce and automatically try or reuse Free according to [billing-recovery.md](billing-recovery.md), without another confirmation. Preserve any existing subscription; an unexpected or unverified trial must not be replaced. For HTTP failures, use bounded status-only retries and the available plan diagnostic before choosing a safe fallback route; never submit a paid-price or duplicate signup. Report the actual plan and expiry, and that a cardless trial pauses at expiry unless a paid plan is chosen.

## Import and analysis

Call `import_github_repository` with the resolved `organizationKey` and exact `repository`. It validates the GitHub organization binding and repository access, uses the server-returned installation key, and returns `projects`.

Reuse a `reused` project binding. A `ready` result means provisioning, not completed analysis. If several projects are returned, ask for the intended key. A `pending` provisioning result requires read-only discovery to find the binding before any retry; do not blindly repeat project creation.

Call `get_cloud_analysis_status` with `projectKey`. If no completed analysis exists and automatic analysis is within the request, set `enableAutomaticAnalysis: true`. This tool can enable analysis and is classified as a write tool even when merely checking status.

- `completed`: returns the most recent completed default-branch `analysis` (key, date and revision when available) and dashboard URL. It may be historical; compare revision/date when freshness matters, and never claim it covers local uncommitted edits.
- `pending`: wait at least `retryAfterSeconds` and repeat, up to 15 minutes overall. Eligibility can itself be asynchronous. Preserve the imported project on timeout.
- `ci_required`: explain the returned `reason` and prepare an appropriate scanner/CI setup only within the request. Do not claim completed analysis.

Once complete, use `list_branches` to identify the main branch, then `get_component_measures` with overall-code metrics (`vulnerabilities`, `bugs`, `code_smells`, `security_hotspots`, `security_rating`, `reliability_rating`, `sqale_rating`) on the analyzed branch, and existing MCP tools `get_project_quality_gate_status`, `search_sonar_issues_in_projects`, and `search_security_hotspots` to review results. Inspect each actual input schema and paginate findings. Query the analyzed main branch; do not infer a PR context from the agent's checkout. These result tools accept explicit project identifiers without requiring an organization header. For issues, pass `projectKeys: [PROJECT_KEY]`; an unscoped issues search still needs an organization. Other organization-wide tools currently need a configured deployment default; legacy token-mode clients may use `SONARQUBE_ORG`, but OAuth requests must not send it. Onboarding does not change their scope. Report dev9, organization/project keys, analysis freshness, dashboard link, actual plan/trial expiry when applicable, quality gate and actionable findings. A completed analysis does not by itself mean the repository is secure.


Apply the shared indexing, gate, coverage and final-report requirements in `SKILL.md`. If overall issue measures are positive but the scoped issue search is empty, poll that same search every 10 seconds for up to 2 minutes, then report incomplete indexing instead of zero findings. Paginate issues and hotspots independently; failed hotspot retrieval is incomplete review. `NOT_COMPUTED` on a first analysis can mean the new-code baseline is not yet established; use overall-code ratings/measures and do not label it a failure or pass. Only the default branch/revision returned by analysis status is covered. Flag a different local branch/commit if checkout context is available; otherwise state that local coverage is unknown.
