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
import java.util.ArrayList;
import java.util.List;
import org.sonarsource.sonarqube.mcp.serverapi.onboarding.CloudOnboardingApi.Organization;
import org.sonarsource.sonarqube.mcp.serverapi.onboarding.CloudOnboardingApi.Repository;
import org.sonarsource.sonarqube.mcp.serverapi.onboarding.CloudOnboardingApi;

final class OnboardingSupport {
  private OnboardingSupport() { }

  static void validateOwner(String owner) {
    if (!owner.matches("[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,37}[a-zA-Z0-9])?")) {
      throw new IllegalArgumentException("github must be a GitHub account name.");
    }
  }

  static String owner(String repository) {
    var parts = repository.split("/", -1);
    if (parts.length != 2 || !parts[1].matches("[a-zA-Z0-9_.-]+")) {
      throw new IllegalArgumentException("repository must be OWNER/REPO.");
    }
    validateOwner(parts[0]);
    return parts[0];
  }

  static boolean boundTo(Organization organization, String owner) {
    if (organization.alm() == null || !"github".equals(organization.alm().key()) || organization.alm().url() == null) {
      return false;
    }
    try {
      var uri = URI.create(organization.alm().url());
      return "github.com".equalsIgnoreCase(uri.getHost()) && uri.getPath().replaceAll("^/|/$", "").equalsIgnoreCase(owner);
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  static List<Organization> matchingOrganizations(CloudOnboardingApi api, String owner) {
    var matches = new ArrayList<Organization>();
    int page = 1;
    CloudOnboardingApi.Organizations data;
    do {
      data = api.memberOrganizations(page);
      data.organizations().stream().filter(org -> boundTo(org, owner)).forEach(matches::add);
    } while (page++ * 100 < data.paging().total());
    return matches;
  }

  static Organization organization(CloudOnboardingApi api, String key) {
    return api.organizationByKey(key).organizations().stream().filter(org -> org.key().equals(key)).findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Organization was not found or is inaccessible."));
  }

  static void requireAdmin(Organization organization) {
    if (organization.actions() == null || !organization.actions().admin()) {
      throw new IllegalArgumentException("Organization administrator permissions are required.");
    }
  }

  static Repository repository(CloudOnboardingApi api, String organization, String slug) {
    return api.repositories(organization).repositories().stream().filter(repo -> repo.repositorySlug().equalsIgnoreCase(slug))
      .findFirst().orElseThrow(() -> new IllegalArgumentException("Repository access is not confirmed. Grant the GitHub App access to the requested repository."));
  }
}
