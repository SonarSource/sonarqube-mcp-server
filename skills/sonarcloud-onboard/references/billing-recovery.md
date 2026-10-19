# Billing failure recovery

Read this only after a subscription lookup, signup, or verification fails. Preserve the existing organization, GitHub binding and any project. A failed billing request does not prove there is no subscription. Never submit a duplicate subscription POST to diagnose an uncertain result.

## Classify and inspect once

Record the failed method and endpoint, HTTP status, organization key, and timestamp with timezone. Read the entire stderr stream: `step_failed` may omit the diagnostic naming `GET` or `POST /billing/subscriptions`.

- **401:** credentials may be rejected by billing. In CLI mode, allow one forced Cloud login, combining `--org "$ORG_KEY"` with it once the organization is known; then retry the failed read. A second 401 stops the flow. A later 404 is a distinct billing problem, not grounds for another login. In MCP mode, first use the v1/v2 authorization diagnostic in `mcp-onboarding.md`; avoid repeated client reconnects for a split authorization result.
- **404 on a new organization:** the organization/subscription may not have propagated, or billing may have a service/routing fault. Do not classify it as confirmed absence. Run the bounded read-only retry below; if it remains 404, stop billing setup and report the fault. A POST 404 does not authorize repeated signup attempts.
- **403 or other failures:** report permissions or connectivity/service errors; do not change plans, credentials or organization bindings speculatively.

Allow this specific read-only diagnostic after the failure, using the same authenticated backend and dev9 overrides:

```sh
# ORG_KEY is URL-encoded for the query. E is the function from SKILL.md.
E api get "/api/organizations/search?organizations=$ORG_KEY"
```

In MCP mode, use a read-only tool only if its actual schema exposes this organization record and plan. The current `discover_github_repository` response contains binding/access information, not the v1 `subscription` field. If the deployment has no tool for this diagnostic, report that limitation; do not invent a tool or infer a plan from binding alone. Offer CLI diagnosis only under the backend-switch rule. Without verified plan evidence, this fallback is unavailable.

Find the exact organization key and inspect its GitHub binding, admin access, and `subscription` field. For example, `subscription: FREE` is evidence of the v1 organization's reported plan; it does not verify a Team trial, billing activation, expiry or payment-method state. Do not browse unrelated billing endpoints, retrieve payment data, or expose tokens.

## Bounded read-only billing retry

CLI: reuse the organization's actual UUID from prior successful lookup/output, or resolve it with `E api get "/organizations/organizations?organizationKey=$ORG_KEY&excludeEligibility=true"` and read `uuidV4` from the exact organization record in the returned array. Do not pass the organization key as a UUID or guess it. The subscription status check uses:

```sh
E api get "/billing/subscriptions?resourceId=$ORG_UUID&resourceType=organization"
```

This is v2 on `API_SERVER`; the CLI routes `/billing/...` there. Use GET only. After an uncertain signup result, do not rerun `org import` merely to poll billing: it may submit another POST. Inspect the returned subscription before any continuation, preserving its actual plan.

MCP: use `ensure_cloud_subscription` with the same `organizationKey` and plan plus **`createIfMissing: false`**. This is a status check, with no new signup. It must be available in the connected deployment; do not invent a generic billing tool if it is missing.

For a new organization with delayed visibility, retry the same read after 10, 20, 40 and then 60 seconds between checks, honoring a longer server retry hint without exceeding **10 minutes from the first failure**. Keep the user informed through monitored short waits. On success, verify the subscription/trial as in the normal workflow. After a pending or uncertain signup, even a successful empty response leaves its outcome unverified; do not treat that, a missing UUID or persistent 404 as permission to create another subscription. A definitively rejected trial with successful empty billing read-back can use the Free creation route below. For an established organization, persistent 404 is a service fault, not a new-organization propagation excuse.

## Automatic Free fallback after a new Team trial fails

The default workflow authorizes automatic Free fallback when a new Team trial fails. Announce: “The Team trial could not be completed. I’m checking the Free fallback so I can continue analysis where that plan supports this repository.” Do not ask for another confirmation. An explicit user instruction to require Team or forbid Free overrides this default. Preserve every existing subscription; this rule does not authorize downgrading an active, paused or expired Team/paid subscription.

Choose the route using the actual failure and read-back evidence:

- **Free already exists:** reuse it. If billing is unavailable but the exact GitHub-bound organization reports `subscription: FREE` through `/api/organizations/search`, continue on that reported Free plan without another confirmation. Skip subscription creation and `org import`; disclose that billing remains unverified and no trial was confirmed. A v1 plan field does not prove a failed signup had no effect: when a trial POST may have been submitted, still complete the bounded status-only checks, and submit no additional signup. Preserve any subsequently discovered subscription.
- **Trial definitively rejected, no subscription exists:** only after successful billing read-back confirms an empty subscription list and the signup outcome is known (not pending/uncertain), attempt Free signup once through the supported backend. In CLI mode, use `E org import --github "$OWNER" --key "$ORG_KEY" --plan free --timeout 600 --no-browser --events --format json`, preserving the confirmed binding and any required installation ID. In MCP mode, call `ensure_cloud_subscription` with the same `organizationKey` and `plan: free`. Read back and verify Free before importing. The backend must resolve its unique zero-cost price; never guess a price or submit a paid price. If Free also fails, stop and report; after an uncertain Free signup, use status-only recovery rather than another creation attempt.
- **Trial actually exists:** preserve it and report its actual status. A trial-verification failure is not permission to replace it with Free. Stop when the existing subscription cannot support the next step.
- **Billing/authorization fault or unknown outcome:** perform the bounded recovery above. `org import --plan free` is not a workaround for a failed existing-subscription lookup: it happens before plan handling and can return the same 404. If no Free plan is confirmed and safe signup cannot be established, stop and report the blocked fallback. Do not turn persistent 401/403/404, missing UUID, propagation delay or timeout into another POST.

When reusing a v1-reported Free plan, check/select the organization through the supported selection/login route in `SKILL.md`, then recheck auth status before CLI `import`; in MCP mode, pass the exact organization key to repository import. Verify repository access, project visibility/entitlements and analysis eligibility normally. Free may not support the requested repository or features; stop and explain the reported limitation rather than changing visibility, upgrading, or claiming analysis succeeded. If the selected MCP deployment cannot expose plan evidence, the v1-reported-plan route is unavailable; honor the backend-switch rule before offering CLI diagnosis.

## Failure report

Include environment/web and API hosts, organization key (UUID if already known), failed method and endpoint, HTTP status, first/last attempt time with timezone, retry duration, and any request/correlation ID actually returned. State completed steps, the v1 reported plan if available, whether signup was attempted or its outcome is uncertain, and whether automatic Free fallback was attempted, reused or blocked, and its verification source. Summarize the diagnostic without credentials, personal/payment data or raw token-bearing URLs. Link the existing organization dashboard on the selected web host and explain what is blocked and what an operator needs to investigate.
