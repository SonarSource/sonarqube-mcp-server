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
package org.sonarsource.sonarqube.mcp.tools;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import jakarta.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarqube.mcp.client.TransportMode;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.tools.ToolAvailabilityPolicy.Deployment;
import org.sonarsource.sonarqube.mcp.tools.ToolAvailabilityPolicy.StartupRequirement;
import org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureSetTool;
import org.sonarsource.sonarqube.mcp.tools.enterprises.ListEnterprisesTool;
import org.sonarsource.sonarqube.mcp.tools.organizations.ListOrganizationsTool;
import org.sonarsource.sonarqube.mcp.tools.system.SystemHealthTool;
import org.sonarsource.sonarqube.mcp.tools.system.SystemInfoTool;
import org.sonarsource.sonarqube.mcp.tools.system.SystemLogsTool;
import org.sonarsource.sonarqube.mcp.tools.system.SystemPingTool;
import org.sonarsource.sonarqube.mcp.tools.system.SystemStatusTool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.sonarsource.sonarqube.mcp.client.TransportMode.HTTP;
import static org.sonarsource.sonarqube.mcp.client.TransportMode.STDIO;
import static org.sonarsource.sonarqube.mcp.tools.ToolAvailabilityPolicy.Deployment.CLOUD;
import static org.sonarsource.sonarqube.mcp.tools.ToolAvailabilityPolicy.Deployment.SERVER;
import static org.sonarsource.sonarqube.mcp.tools.ToolAvailabilityPolicy.StartupRequirement.SARA_ENABLED;

class ToolRegistrarTest {

  private static final ToolAvailabilityPolicy REQUIRES_SARA = new ToolAvailabilityPolicy(Set.of(TransportMode.values()), Set.of(Deployment.values()),
    Map.of(STDIO, Set.of(SARA_ENABLED)));

  @ParameterizedTest
  @CsvSource({"STDIO,CLOUD", "HTTP,CLOUD", "STDIO,SERVER", "HTTP,SERVER"})
  void it_should_register_unrestricted_tools(TransportMode transport, Deployment deployment) {
    var tool = readTool("unrestricted", ToolAvailabilityPolicy.UNRESTRICTED, ToolCategory.ISSUES);
    var registrar = new ToolRegistrar(Set.of(ToolCategory.ISSUES), false, transport, deployment, requirement -> {
      throw new AssertionError("Unrestricted tools must not resolve startup requirements");
    });

    registrar.register(tool);

    assertThat(registrar.registeredTools()).containsExactly(tool);
  }

  @ParameterizedTest
  @CsvSource({"STDIO,CLOUD,true", "HTTP,CLOUD,false", "STDIO,SERVER,false", "HTTP,SERVER,false"})
  void it_should_enforce_support_before_resolving_requirements(TransportMode transport, Deployment deployment, boolean expected) {
    var calls = new AtomicInteger();
    var policy = new ToolAvailabilityPolicy(Set.of(STDIO), Set.of(CLOUD), Map.of(STDIO, Set.of(SARA_ENABLED), HTTP, Set.of(SARA_ENABLED)));
    var tool = readTool("restricted", policy, ToolCategory.ISSUES);
    var registrar = new ToolRegistrar(ToolCategory.all(), false, transport, deployment, requirement -> {
      calls.incrementAndGet();
      return true;
    });

    registrar.register(tool);

    assertThat(registrar.registeredTools()).isEqualTo(expected ? List.of(tool) : List.of());
    assertThat(calls.get()).isEqualTo(expected ? 1 : 0);
  }

  @Test
  void it_should_skip_requirements_for_unselected_categories() {
    var registrar = new ToolRegistrar(Set.of(ToolCategory.RULES), false, STDIO, CLOUD, requirement -> {
      throw new AssertionError("Disabled categories must not resolve startup requirements");
    });

    registrar.register(readTool("disabled", REQUIRES_SARA, ToolCategory.ISSUES));

    assertThat(registrar.registeredTools()).isEmpty();
  }

  @Test
  void it_should_accept_any_enabled_category_and_always_enable_projects() {
    var registrar = new ToolRegistrar(Set.of(ToolCategory.ISSUES), false, STDIO, SERVER, requirement -> true);
    var multipleCategories = readTool("multiple", ToolAvailabilityPolicy.UNRESTRICTED, ToolCategory.RULES, ToolCategory.ISSUES);
    var projects = readTool("projects", ToolAvailabilityPolicy.UNRESTRICTED, ToolCategory.PROJECTS);

    registrar.registerAll(List.of(multipleCategories, projects,
      readTool("unsupported_project_tool", ToolAvailabilityPolicy.CLOUD_ONLY, ToolCategory.PROJECTS)));

    assertThat(registrar.registeredTools()).containsExactly(multipleCategories, projects);
  }

  @Test
  void it_should_filter_missing_and_false_read_only_annotations_before_requirements() {
    var calls = new AtomicInteger();
    var registrar = new ToolRegistrar(ToolCategory.all(), true, STDIO, CLOUD, requirement -> {
      calls.incrementAndGet();
      return true;
    });
    registrar.registerAll(List.of(
      tool("no_annotations", REQUIRES_SARA, null, ToolCategory.PROJECTS),
      tool("empty_annotations", REQUIRES_SARA, new McpSchema.ToolAnnotations(null, null, null, null, null, null), ToolCategory.ISSUES),
      tool("false_hint", REQUIRES_SARA, new McpSchema.ToolAnnotations(null, false, null, null, null, null), ToolCategory.ISSUES)));
    assertThat(registrar.registeredTools()).isEmpty();
    assertThat(calls.get()).isZero();

    var readOnly = readTool("read_only", REQUIRES_SARA, ToolCategory.ISSUES);
    registrar.register(readOnly);

    assertThat(registrar.registeredTools()).containsExactly(readOnly);
    assertThat(calls.get()).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void it_should_resolve_shared_requirements_once_including_disabled_results(boolean enabled) {
    var calls = new AtomicInteger();
    var registrar = new ToolRegistrar(ToolCategory.all(), false, STDIO, CLOUD,
      requirement -> calls.getAndIncrement() == 0 ? enabled : !enabled);
    var first = readTool("first", REQUIRES_SARA, ToolCategory.ISSUES);
    var second = readTool("second", REQUIRES_SARA, ToolCategory.RULES);

    registrar.registerAll(List.of(first), "first group");
    registrar.registerAll(List.of(second), "second group");

    assertThat(calls.get()).isEqualTo(1);
    assertThat(registrar.registeredTools()).isEqualTo(enabled ? List.of(first, second) : List.of());
    assertThat(registrar.instructions()).isEqualTo(enabled ? List.of("first group", "second group") : List.of());
  }

  @Test
  void it_should_keep_failed_requirements_disabled_until_a_new_startup() {
    var calls = new AtomicInteger();
    Predicate<StartupRequirement> resolver = requirement -> {
      if (calls.getAndIncrement() == 0) {
        throw new IllegalStateException("Feature service unavailable");
      }
      return true;
    };
    var tool = readTool("requires_feature", REQUIRES_SARA, ToolCategory.ISSUES);
    var registrar = new ToolRegistrar(ToolCategory.all(), false, STDIO, CLOUD, resolver);

    registrar.registerAll(List.of(tool), "hidden instructions");
    registrar.register(tool);
    assertThat(registrar.registeredTools()).isEmpty();
    assertThat(registrar.instructions()).isEmpty();
    assertThat(calls.get()).isEqualTo(1);

    var restarted = new ToolRegistrar(ToolCategory.all(), false, STDIO, CLOUD, resolver);
    restarted.register(tool);
    assertThat(restarted.registeredTools()).containsExactly(tool);
    assertThat(calls.get()).isEqualTo(2);
  }

  @ParameterizedTest
  @EnumSource(TransportMode.class)
  void it_should_apply_only_requirements_for_the_current_transport(TransportMode transport) {
    var calls = new AtomicInteger();
    var tool = readTool("conditional_requirement", REQUIRES_SARA, ToolCategory.ISSUES);
    var registrar = new ToolRegistrar(ToolCategory.all(), false, transport, CLOUD, requirement -> {
      calls.incrementAndGet();
      return false;
    });

    registrar.register(tool);

    assertThat(registrar.registeredTools()).isEqualTo(transport == HTTP ? List.of(tool) : List.of());
    assertThat(calls.get()).isEqualTo(transport == HTTP ? 0 : 1);
  }

  @Test
  void it_should_include_group_instructions_once_when_any_tool_is_accepted() {
    var registrar = new ToolRegistrar(Set.of(ToolCategory.ISSUES), false, STDIO, CLOUD, requirement -> true);
    var rejected = readTool("rejected", ToolAvailabilityPolicy.UNRESTRICTED, ToolCategory.RULES);
    var first = readTool("first", ToolAvailabilityPolicy.UNRESTRICTED, ToolCategory.ISSUES);
    var second = readTool("second", ToolAvailabilityPolicy.UNRESTRICTED, ToolCategory.ISSUES);

    registrar.registerAll(List.of(rejected), "hidden");
    registrar.registerAll(List.of(), "empty group");
    registrar.registerAll(List.of(rejected, first), "shared instructions");
    registrar.registerAll(List.of(second), "shared instructions");

    assertThat(registrar.registeredTools()).containsExactly(first, second);
    assertThat(registrar.instructions()).containsExactly("shared instructions");
  }

  @Test
  void it_should_return_immutable_registration_snapshots() {
    var registrar = new ToolRegistrar(ToolCategory.all(), false, STDIO, CLOUD, requirement -> true);
    var first = readTool("first", ToolAvailabilityPolicy.UNRESTRICTED, ToolCategory.ISSUES);
    var second = readTool("second", ToolAvailabilityPolicy.UNRESTRICTED, ToolCategory.ISSUES);
    registrar.registerAll(List.of(first), "first instructions");
    var tools = registrar.registeredTools();
    var instructions = registrar.instructions();

    registrar.registerAll(List.of(second), "second instructions");

    assertThat(tools).containsExactly(first);
    assertThat(instructions).containsExactly("first instructions");
    assertThatThrownBy(() -> tools.add(second)).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> instructions.add("new instructions")).isInstanceOf(UnsupportedOperationException.class);
  }

  @ParameterizedTest
  @CsvSource({"STDIO,CLOUD", "HTTP,CLOUD", "STDIO,SERVER", "HTTP,SERVER"})
  void it_should_preserve_migrated_cloud_and_server_restrictions(TransportMode transport, Deployment deployment) {
    var provider = mock(ServerApiProvider.class);
    var cloudTools = List.of(new ListOrganizationsTool(provider), new ListEnterprisesTool(provider), new ArchitectureSetTool(provider, true, null));
    var serverTools = List.of(new SystemHealthTool(provider), new SystemInfoTool(provider), new SystemLogsTool(provider),
      new SystemPingTool(provider), new SystemStatusTool(provider));
    var registrar = new ToolRegistrar(ToolCategory.all(), false, transport, deployment, requirement -> {
      throw new AssertionError("Deployment restrictions do not need feature lookups");
    });

    registrar.registerAll(cloudTools);
    registrar.registerAll(serverTools);

    assertThat(registrar.registeredTools()).containsExactlyElementsOf(deployment == CLOUD ? cloudTools : serverTools);
  }

  private static Tool readTool(String name, ToolAvailabilityPolicy policy, ToolCategory... categories) {
    return tool(name, policy, new McpSchema.ToolAnnotations(null, true, null, null, null, null), categories);
  }

  private static Tool tool(String name, ToolAvailabilityPolicy policy, @Nullable McpSchema.ToolAnnotations annotations, ToolCategory... categories) {
    return new Tool(McpSchema.Tool.builder(name, Map.of("type", "object")).annotations(annotations).build(), categories) {
      @Override
      public ToolAvailabilityPolicy availabilityPolicy() {
        return policy;
      }

      @Override
      public Result execute(Arguments arguments) {
        throw new AssertionError("Registration must not execute tools");
      }
    };
  }
}
