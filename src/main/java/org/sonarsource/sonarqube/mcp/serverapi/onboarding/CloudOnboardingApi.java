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
import java.util.stream.Collectors;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.UrlBuilder;

/** Account-scoped Cloud APIs; organization is explicit, never stored in a shared session. */
public class CloudOnboardingApi {
  private final ServerApiHelper helper;
  private final Gson gson = new Gson();

  public CloudOnboardingApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public String serverUrl() {
    return helper.getBaseUrl();
  }

  public String apiUrl() {
    return helper.getApiBaseUrl();
  }

  public Environment environment() {
    try (var response = helper.getAnonymous("/static_configuration/configuration.json")) {
      var configuration = gson.fromJson(response.bodyAsString(), StaticConfiguration.class);
      if (configuration == null || configuration.api() == null || configuration.api().v2() == null
        || !configuration.api().v2().replaceAll("/+$", "").equals(apiUrl().replaceAll("/+$", ""))) {
        throw new IllegalStateException("Cloud web/API configuration does not match this MCP deployment.");
      }
      return new Environment(configuration.environmentName(), configuration.api().v2());
    }
  }

  public record Environment(String name, String apiServer) { }
  private record StaticConfiguration(String environmentName, ApiConfiguration api) { }
  private record ApiConfiguration(String v2) { }

  private <T> T get(String path, Class<T> type) {
    try (var response = helper.get(path)) {
      return gson.fromJson(response.bodyAsString(), type);
    }
  }

  private <T> T getBilling(String path, Class<T> type) {
    try (var response = helper.getApiSubdomain(path)) {
      return gson.fromJson(response.bodyAsString(), type);
    }
  }

  private <T> T postForm(String path, Map<String, String> fields, Class<T> type) {
    var body = fields.entrySet().stream()
      .map(e -> java.net.URLEncoder.encode(e.getKey(), java.nio.charset.StandardCharsets.UTF_8) + "=" + java.net.URLEncoder.encode(e.getValue(), java.nio.charset.StandardCharsets.UTF_8))
      .collect(Collectors.joining("&"));
    try (var response = helper.post(path, "application/x-www-form-urlencoded", body)) {
      return type == Void.class ? null : gson.fromJson(response.bodyAsString(), type);
    }
  }

  public Organizations memberOrganizations(int page) {
    return get(new UrlBuilder("/api/organizations/search").addParam("member", "true")
      .addParam("p", String.valueOf(page)).addParam("ps", "100").build(), Organizations.class);
  }

  public Organizations organizationByKey(String key) {
    return get(new UrlBuilder("/api/organizations/search").addParam("organizations", key).build(), Organizations.class);
  }

  public Installations installations() {
    return get("/api/alm_integration/list_unbound_applications", Installations.class);
  }

  public InstallationInfo installationInfo(String installationId) {
    return postForm("/api/alm_integration/show_dop_organization", Map.of("installationId", installationId), InstallationInfo.class);
  }

  public ApplicationInfo applicationInfo() {
    return get("/api/alm_integration/show_app_info", ApplicationInfo.class);
  }

  public Organization createOrganization(String key, String name, String installationId) {
    return postForm("/api/organizations/create", Map.of("key", key, "name", name, "installationId", installationId), CreatedOrganization.class).organization();
  }

  public void bindOrganization(String key, String installationId) {
    postForm("/api/alm_integration/bind_organization", Map.of("organization", key, "installationId", installationId), Void.class);
  }

  public Repositories repositories(String key) {
    return get(new UrlBuilder("/api/alm_integration/list_repositories").addParam("organization", key).build(), Repositories.class);
  }

  public Projects provision(String organization, String installationKey) {
    return postForm("/api/alm_integration/provision_projects", Map.of("organization", organization, "installationKeys", installationKey), Projects.class);
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
    return get("/api/users/current", User.class);
  }

  public void subscribe(Map<String, String> body) {
    try (var response = helper.postApiSubdomain("/billing/subscriptions", "application/json", gson.toJson(body))) {
      // Response is not evidence that the subscription is active. Read it back separately.
    }
  }

  public Analyses analyses(String project) {
    return get(new UrlBuilder("/api/project_analyses/search").addParam("project", project).addParam("ps", "1").build(), Analyses.class);
  }

  public Eligibility eligibility(String project, boolean enable) {
    var path = new UrlBuilder("/api/autoscan/eligibility").addParam("projectKey", project)
      .addParam("autoEnable", String.valueOf(enable)).addParam("ignoreCache", "false").build();
    try (var response = helper.get(path)) {
      return response.code() == 202 ? null : gson.fromJson(response.bodyAsString(), Eligibility.class);
    }
  }

  public record Organizations(List<Organization> organizations, Paging paging) { }
  public record Paging(int total) { }
  public record Organization(String key, String name, Alm alm, Actions actions) { }
  public record Alm(String key, String url) { }
  public record Actions(boolean admin) { }
  public record Installations(List<Installation> applications) { }
  public record Installation(String installationId, String key, String name) { }
  public record InstallationInfo(Account almOrganization, Account boundOrganization) { }
  public record Account(String key, String name) { }
  public record ApplicationInfo(Application application) { }
  public record Application(String installationUrl) { }
  public record CreatedOrganization(Organization organization) { }
  public record Repositories(List<Repository> repositories) { }
  public record Repository(String slug, String installationKey, List<Project> linkedProjects) {
    public String repositorySlug() {
      return slug == null ? installationKey.split("\\|", 2)[0] : slug;
    }
  }
  public record Projects(List<Project> projects) { }
  public record Project(String key, String name) { }
  public record Subscriptions(List<Subscription> subscriptions) { }
  public record Subscription(String planKey, String status, Boolean trial, TrialPeriod trialPeriod) { }
  public record TrialPeriod(String start, String end) { }
  public record Customer(String paymentMethodStatus) { }
  public record Plan(String name, List<Tier> tiers) { }
  public record Tier(String priceId, List<Currency> currencyOptions) { }
  public record Currency(long unitAmount) { }
  public record User(String email) { }
  public record Analyses(List<Analysis> analyses) { }
  public record Analysis(String key, String date, String revision) { }
  public record Eligibility(boolean eligible, String ineligibilityReason) { }
}
