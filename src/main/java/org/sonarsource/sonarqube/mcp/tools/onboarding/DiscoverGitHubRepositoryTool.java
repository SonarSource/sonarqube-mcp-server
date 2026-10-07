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

import java.util.List;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.onboarding.CloudOnboardingApi;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;
import org.sonarsource.sonarqube.mcp.tools.ToolDefinitionBuilder;

public class DiscoverGitHubRepositoryTool extends Tool {
  public static final String TOOL_NAME = "discover_github_repository";
  private final ServerApiProvider provider;

  public DiscoverGitHubRepositoryTool(ServerApiProvider provider) {
    super(ToolDefinitionBuilder.builder().setName(TOOL_NAME).setTitle("Discover GitHub Repository")
      .setDescription("Find existing Cloud organizations and projects bound to a GitHub repository before onboarding.")
      .addRequiredStringProperty("repository", "GitHub OWNER/REPO.").setReadOnlyHint().build(), ToolCategory.PROJECTS);
    this.provider = provider;
  }

  @Override
  public Result execute(Arguments arguments) {
    var slug = arguments.getStringOrThrow("repository");
    var owner = OnboardingSupport.owner(slug);
    var api = provider.getForOnboarding().cloudOnboardingApi();
    var environment = api.environment();
    var organizations = OnboardingSupport.matchingOrganizations(api, owner).stream().map(org -> {
      var repositories = api.repositories(org.key()).repositories().stream().filter(repo -> repo.repositorySlug().equalsIgnoreCase(slug)).toList();
      return new Match(org.key(), org.actions() != null && org.actions().admin(), repositories);
    }).toList();
    return Result.success(new Response(api.serverUrl(), api.apiUrl(), environment, slug, organizations));
  }

  public record Match(String organizationKey, boolean admin, List<CloudOnboardingApi.Repository> repositories) { }
  public record Response(String server, String apiServer, CloudOnboardingApi.Environment environment, String repository, List<Match> organizations) { }
}
