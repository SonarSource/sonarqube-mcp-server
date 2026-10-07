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

import java.net.URI;
import java.util.Locale;
import org.apache.hc.core5.net.URIBuilder;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.exception.NotFoundException;
import org.sonarsource.sonarqube.mcp.serverapi.onboarding.CloudOnboardingApi;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;
import org.sonarsource.sonarqube.mcp.tools.ToolDefinitionBuilder;

public class ImportGitHubOrganizationTool extends Tool {
  public static final String TOOL_NAME = "import_github_organization";
  private final ServerApiProvider provider;

  public ImportGitHubOrganizationTool(ServerApiProvider provider) {
    super(ToolDefinitionBuilder.builder().setName(TOOL_NAME).setTitle("Import GitHub Organization")
      .setDescription("Reuse or bind a Cloud organization; returns a GitHub approval URL when installation is missing. Call again after approval.")
      .addRequiredStringProperty("github", "GitHub account name.")
      .addStringProperty("organizationKey", "Cloud key; defaults to the GitHub account name.")
      .addStringProperty("installationId", "Optional numeric GitHub App installation ID.").build(), ToolCategory.PROJECTS);
    this.provider = provider;
  }

  @Override
  public Result execute(Arguments arguments) {
    var owner = arguments.getStringOrThrow("github");
    OnboardingSupport.validateOwner(owner);
    var key = arguments.getOptionalString("organizationKey");
    var id = arguments.getOptionalString("installationId");
    if (id != null && !id.matches("\\d+")) {
      return Result.failure("installationId must be numeric.");
    }
    var api = provider.getForOnboarding().cloudOnboardingApi();
    var bound = reuseBoundOrganization(api, owner, key);
    if (bound != null) {
      return bound;
    }
    return resolveInstallation(api, owner, key, id);
  }

  @jakarta.annotation.Nullable
  private static Result reuseBoundOrganization(CloudOnboardingApi api, String owner, @jakarta.annotation.Nullable String key) {
    var matches = OnboardingSupport.matchingOrganizations(api, owner);
    if (matches.size() > 1) {
      if (key == null) {
        return Result.failure("Multiple Cloud organizations match. Specify organizationKey.");
      }
      var requestedKey = key;
      matches = matches.stream().filter(org -> org.key().equals(requestedKey)).toList();
      if (matches.size() != 1) {
        return Result.failure("organizationKey does not identify a matching GitHub organization.");
      }
    }
    if (!matches.isEmpty()) {
      var org = matches.getFirst();
      OnboardingSupport.requireAdmin(org);
      if (key != null && !key.equals(org.key())) {
        return Result.failure("GitHub account is already bound to '" + org.key() + "'.");
      }
      return ready(api, org.key(), owner);
    }
    return null;
  }

  private static Result resolveInstallation(CloudOnboardingApi api, String owner, @jakarta.annotation.Nullable String key,
    @jakarta.annotation.Nullable String id) {
    if (id == null) {
      var installations = api.installations().applications().stream().filter(app -> app.key().equalsIgnoreCase(owner)).toList();
      if (installations.size() > 1) {
        return Result.failure("Multiple installations match. Specify installationId.");
      }
      if (!installations.isEmpty()) {
        id = installations.getFirst().installationId();
      }
    }
    if (id == null) {
      var url = URI.create(api.applicationInfo().application().installationUrl());
      if (!"https".equals(url.getScheme()) || !"github.com".equals(url.getHost()) || url.getUserInfo() != null) {
        return Result.failure("Cloud returned an unexpected GitHub installation URL.");
      }
      var installationUrl = new URIBuilder(url).setParameter("state", "sonarqube-mcp").toString();
      return Result.success(new Response("browser_required", api.serverUrl(), null, owner, installationUrl, "user", 5));
    }
    return bindInstallation(api, owner, key, id);
  }

  private static Result bindInstallation(CloudOnboardingApi api, String owner, @jakarta.annotation.Nullable String key, String id) {
    CloudOnboardingApi.InstallationInfo info;
    try {
      info = api.installationInfo(id);
    } catch (NotFoundException e) {
      return Result.success(new Response("pending", api.serverUrl(), null, owner, null, null, 5));
    }
    if (!info.almOrganization().key().equalsIgnoreCase(owner)) {
      return Result.failure("The GitHub installation belongs to a different account.");
    }
    if (info.boundOrganization() != null) {
      return Result.failure("Installation is already bound to an inaccessible organization.");
    }
    key = key == null ? owner.toLowerCase(Locale.ROOT) : key;
    var requestedKey = key;
    var existing = api.organizationByKey(key).organizations().stream().filter(org -> org.key().equals(requestedKey)).findFirst();
    if (existing.isPresent()) {
      OnboardingSupport.requireAdmin(existing.get());
      if (existing.get().alm() != null) {
        return Result.failure("Organization key is bound to another DevOps account. Choose another organizationKey.");
      }
      api.bindOrganization(key, id);
    } else {
      var org = api.createOrganization(key, info.almOrganization().name(), id);
      key = org.key();
    }
    return ready(api, key, owner);
  }

  private static Result ready(CloudOnboardingApi api, String key, String owner) {
    return Result.success(new Response("ready", api.serverUrl(), key, owner, null, null, null));
  }

  public record Response(String status, String server, String organizationKey, String github, String url, String actor, Integer retryAfterSeconds) { }
}
