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

public class ImportGitHubRepositoryTool extends Tool {
  public static final String TOOL_NAME = "import_github_repository";
  private final ServerApiProvider provider;

  public ImportGitHubRepositoryTool(ServerApiProvider provider) {
    super(ToolDefinitionBuilder.builder().setName(TOOL_NAME).setTitle("Import GitHub Repository")
      .setDescription("Import a GitHub repository into Cloud, reusing an existing project binding.")
      .addRequiredStringProperty("organizationKey", "Cloud organization key.")
      .addRequiredStringProperty("repository", "GitHub OWNER/REPO.").build(), ToolCategory.PROJECTS);
    this.provider = provider;
  }

  @Override
  public Result execute(Arguments arguments) {
    var key = arguments.getStringOrThrow("organizationKey");
    var slug = arguments.getStringOrThrow("repository");
    var owner = OnboardingSupport.owner(slug);
    var api = provider.getForOnboarding().cloudOnboardingApi();
    var org = OnboardingSupport.organization(api, key);
    if (!OnboardingSupport.boundTo(org, owner)) {
      return Result.failure("Organization is not bound to the requested GitHub account.");
    }
    var repo = OnboardingSupport.repository(api, key, slug);
    if (!repo.linkedProjects().isEmpty()) {
      return Result.success(new Response("reused", key, slug, repo.linkedProjects()));
    }
    OnboardingSupport.requireAdmin(org);
    var projects = api.provision(key, repo.installationKey()).projects();
    if (projects.isEmpty()) {
      projects = OnboardingSupport.repository(api, key, slug).linkedProjects();
    }
    return Result.success(new Response(projects.isEmpty() ? "pending" : "ready", key, slug, projects));
  }

  public record Response(String status, String organizationKey, String repository,
    List<CloudOnboardingApi.Project> projects) { }
}
