/*
 * SonarQube MCP Server
 * Copyright (C) SonarSource
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource Sàrl.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonarsource.sonarqube.mcp.tools.onboarding;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.onboarding.CloudOnboardingApi;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;
import org.sonarsource.sonarqube.mcp.tools.ToolDefinitionBuilder;

public class EnsureCloudSubscriptionTool extends Tool {
  public static final String TOOL_NAME = "ensure_cloud_subscription";
  private static final String[] PLANS = {"team-trial", "free"};
  private final ServerApiProvider provider;

  public EnsureCloudSubscriptionTool(ServerApiProvider provider) {
    super(ToolDefinitionBuilder.builder().setName(TOOL_NAME).setTitle("Ensure Cloud Subscription")
      .setDescription("Preserve an existing subscription, or start a cardless Team trial/Free subscription when absent.")
      .addRequiredStringProperty("organizationKey", "Cloud organization key.")
      .addEnumProperty("plan", PLANS, "Defaults to team-trial; free is explicit zero-cost signup.")
      .addBooleanProperty("createIfMissing", "Defaults to true. Set false to check a pending signup without submitting it again.")
      .build(), ToolCategory.PROJECTS);
    this.provider = provider;
  }

  @Override
  public Result execute(Arguments arguments) {
    var key = arguments.getStringOrThrow("organizationKey");
    var plan = arguments.getEnumOrDefault("plan", PLANS, "team-trial");
    var server = provider.getForOnboarding();
    var api = server.cloudOnboardingApi();
    var org = OnboardingSupport.organization(api, key);
    OnboardingSupport.requireAdmin(org);
    // Do not use getOrganizationUuidV4: it swallows authorization/network errors.
    var organizations = server.organizationsApi().listOrganizations().stream().filter(o -> o.key().equals(key)).toList();
    if (organizations.isEmpty() || organizations.getFirst().uuidV4() == null) {
      return Result.success(new Response("organization_pending", key, null, null, 5));
    }
    var id = organizations.getFirst().uuidV4();
    var existing = api.subscriptions(id).subscriptions();
    if (!existing.isEmpty()) {
      var subscription = existing.getFirst();
      return Result.success(new Response("reused", key, subscription,
        Boolean.TRUE.equals(subscription.trial()) ? api.customer(id).paymentMethodStatus() : null, null));
    }
    if (Boolean.FALSE.equals(arguments.getOptionalBoolean("createIfMissing"))) {
      return Result.success(new Response("pending", key, null, null, 5));
    }
    var body = new HashMap<String, String>();
    body.put("customerName", org.name());
    body.put("entityId", id);
    body.put("entityType", "organization");
    if ("free".equals(plan)) {
      var prices = Arrays.stream(api.plans()).filter(p -> "free_v2".equalsIgnoreCase(p.name()))
        .flatMap(p -> p.tiers().stream()).filter(t -> !t.currencyOptions().isEmpty() && t.currencyOptions().stream().allMatch(c -> c.unitAmount() == 0))
        .map(CloudOnboardingApi.Tier::priceId).distinct().toList();
      if (prices.size() != 1) {
        return Result.failure("Could not identify one zero-cost Free plan.");
      }
      body.put("priceId", prices.getFirst());
    }
    var email = api.currentUser().email();
    if (email != null && !email.isBlank()) {
      body.put("email", email);
    } else if ("team-trial".equals(plan)) {
      return Result.failure("An account email is required for the cardless Team trial.");
    }
    api.subscribe(body);
    var confirmed = api.subscriptions(id).subscriptions();
    if (confirmed.isEmpty()) {
      // Never invite a blind repeat of a subscription POST while billing is eventually consistent.
      return Result.success(new Response("pending", key, null, null, 5));
    }
    var subscription = confirmed.getFirst();
    if ("team-trial".equals(plan)) {
      validateTrial(api, id, subscription);
    } else if (!"free_v2".equalsIgnoreCase(subscription.planKey()) || Boolean.TRUE.equals(subscription.trial())) {
      return Result.failure("Cloud did not confirm the requested Free subscription. Review billing before continuing.");
    }
    return Result.success(new Response("ready", key, subscription,
      Boolean.TRUE.equals(subscription.trial()) ? "NONE" : null, null));
  }

  private static void validateTrial(CloudOnboardingApi api, String id, CloudOnboardingApi.Subscription subscription) {
    var period = subscription.trialPeriod();
    try {
      if (!"team".equalsIgnoreCase(subscription.planKey()) || !Boolean.TRUE.equals(subscription.trial())
        || !"active".equalsIgnoreCase(subscription.status()) || period == null
        || !Instant.parse(period.start()).isBefore(Instant.parse(period.end())) || !Instant.parse(period.end()).isAfter(Instant.now())) {
        throw new IllegalArgumentException("Cloud did not confirm an active Team trial with valid expiry. No paid fallback was attempted.");
      }
    } catch (java.time.format.DateTimeParseException e) {
      throw new IllegalArgumentException("Cloud returned an invalid trial period.");
    }
    if (!"NONE".equals(api.customer(id).paymentMethodStatus())) {
      throw new IllegalArgumentException("Cloud did not confirm a cardless trial. Review billing before continuing.");
    }
  }

  public record Response(String status, String organizationKey, CloudOnboardingApi.Subscription subscription, String paymentMethodStatus, Integer retryAfterSeconds) { }
}
