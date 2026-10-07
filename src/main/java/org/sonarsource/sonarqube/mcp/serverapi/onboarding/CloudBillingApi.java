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
package org.sonarsource.sonarqube.mcp.serverapi.onboarding;

import com.google.gson.Gson;
import java.util.List;
import java.util.Map;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.UrlBuilder;

public class CloudBillingApi {
  private final ServerApiHelper helper;
  private final Gson gson = new Gson();

  public CloudBillingApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  private <T> T getBilling(String path, Class<T> type) {
    try (var response = helper.getApiSubdomain(path)) {
      return gson.fromJson(response.bodyAsString(), type);
    }
  }

  public Subscriptions subscriptions(String id) {
    return getBilling(resourcePath("/billing/subscriptions", id), Subscriptions.class);
  }

  public Customer customer(String id) {
    return getBilling(resourcePath("/billing/customers", id), Customer.class);
  }

  private static String resourcePath(String path, String id) {
    return new UrlBuilder(path).addParam("resourceId", id).addParam("resourceType", "organization").build();
  }

  public Plan[] plans() {
    return getBilling("/billing/plans?product=SonarCloud", Plan[].class);
  }

  public User currentUser() {
    try (var response = helper.get("/api/users/current")) {
      return gson.fromJson(response.bodyAsString(), User.class);
    }
  }

  public void subscribe(Map<String, String> body) {
    try (var response = helper.postApiSubdomain("/billing/subscriptions", "application/json", gson.toJson(body))) {
      // Response is not evidence that the subscription is active. Read it back separately.
    }
  }

  public record Subscriptions(List<Subscription> subscriptions) { }
  public record Subscription(String planKey, String status, Boolean trial, TrialPeriod trialPeriod) { }
  public record TrialPeriod(String start, String end) { }
  public record Customer(String paymentMethodStatus) { }
  public record Plan(String name, List<Tier> tiers) { }
  public record Tier(String priceId, List<Currency> currencyOptions) { }
  public record Currency(long unitAmount) { }
  public record User(String email) { }
}
