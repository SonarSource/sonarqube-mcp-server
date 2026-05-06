/*
 * SonarQube MCP Server
 * Copyright (C) 2025 SonarSource
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
package org.sonarsource.sonarqube.mcp.docs;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarqube.mcp.configuration.McpServerLaunchConfiguration;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Guards the declarative config-flow.json against drift from the Java source code.
 * If this test fails, it means someone changed ToolCategory or McpServerLaunchConfiguration
 * without updating docs/config-flow.json (or vice versa).
 */
class ConfigFlowSyncTest {

  private static JsonObject configFlow;

  @BeforeAll
  static void loadConfigFlow() throws IOException {
    var path = Path.of("docs", "config-flow.json");
    if (!Files.exists(path)) {
      fail("docs/config-flow.json not found. Run this test from the repository root.");
    }
    var json = Files.readString(path);
    configFlow = new Gson().fromJson(json, JsonObject.class);
  }

  @Test
  void every_tool_category_has_a_matching_toolset_entry() {
    var jsonKeys = getToolsetKeys();
    for (var category : ToolCategory.values()) {
      assertThat(jsonKeys)
        .as("ToolCategory.%s (key='%s') is missing from config-flow.json toolsets[]", category.name(), category.getKey())
        .contains(category.getKey());
    }
  }

  @Test
  void no_extra_toolset_entries_beyond_tool_categories() {
    var javaKeys = Set.of(ToolCategory.values()).stream()
      .map(ToolCategory::getKey)
      .collect(Collectors.toSet());
    var jsonKeys = getToolsetKeys();
    for (var key : jsonKeys) {
      assertThat(javaKeys)
        .as("config-flow.json has toolset '%s' which does not exist in ToolCategory enum", key)
        .contains(key);
    }
  }

  @Test
  void default_enabled_toolsets_match() {
    var javaDefaults = ToolCategory.defaultEnabled().stream()
      .map(ToolCategory::getKey)
      .collect(Collectors.toSet());

    var jsonDefaults = new HashSet<String>();
    for (var el : configFlow.getAsJsonArray("toolsets")) {
      var obj = el.getAsJsonObject();
      if (obj.has("defaultEnabled") && obj.get("defaultEnabled").getAsBoolean()) {
        jsonDefaults.add(obj.get("key").getAsString());
      }
    }

    assertThat(jsonDefaults)
      .as("config-flow.json defaultEnabled toolsets do not match ToolCategory.defaultEnabled()")
      .containsExactlyInAnyOrderElementsOf(javaDefaults);
  }

  @Test
  void projects_toolset_is_marked_always_on() {
    for (var el : configFlow.getAsJsonArray("toolsets")) {
      var obj = el.getAsJsonObject();
      if ("projects".equals(obj.get("key").getAsString())) {
        assertThat(obj.has("alwaysOn") && obj.get("alwaysOn").getAsBoolean())
          .as("The 'projects' toolset must have alwaysOn=true in config-flow.json")
          .isTrue();
        return;
      }
    }
    fail("'projects' toolset not found in config-flow.json");
  }

  @Test
  void platform_fields_reference_known_env_vars() {
    var knownEnvVars = Set.of(
      McpServerLaunchConfiguration.SONARQUBE_TOKEN,
      McpServerLaunchConfiguration.SONARQUBE_ORG,
      McpServerLaunchConfiguration.SONARQUBE_TOOLSETS,
      McpServerLaunchConfiguration.SONARQUBE_READ_ONLY,
      McpServerLaunchConfiguration.SONARQUBE_PROJECT_KEY,
      "SONARQUBE_URL",
      "SONARQUBE_TRANSPORT",
      "SONARQUBE_HTTP_PORT",
      "SONARQUBE_HTTP_HOST",
      "SONARQUBE_DEBUG_ENABLED",
      "SONARQUBE_HTTPS_KEYSTORE_PATH",
      "SONARQUBE_HTTPS_KEYSTORE_PASSWORD",
      "SONARQUBE_HTTPS_TRUSTSTORE_PATH",
      "SONARQUBE_HTTPS_TRUSTSTORE_PASSWORD"
    );

    var referencedVars = collectAllEnvVarReferences();
    for (var envVar : referencedVars) {
      assertThat(knownEnvVars)
        .as("config-flow.json references env var '%s' which is not in the known set. "
          + "Add it to McpServerLaunchConfiguration or update this test.", envVar)
        .contains(envVar);
    }
  }

  @Test
  void toolset_count_matches_enum_size() {
    assertThat(configFlow.getAsJsonArray("toolsets").size())
      .as("config-flow.json toolsets count must match ToolCategory.values().length")
      .isEqualTo(ToolCategory.values().length);
  }

  private Set<String> getToolsetKeys() {
    return StreamSupport.stream(configFlow.getAsJsonArray("toolsets").spliterator(), false)
      .map(JsonElement::getAsJsonObject)
      .map(obj -> obj.get("key").getAsString())
      .collect(Collectors.toSet());
  }

  private Set<String> collectAllEnvVarReferences() {
    var vars = new HashSet<String>();
    // From platform fields
    for (var p : configFlow.getAsJsonArray("platforms")) {
      var platform = p.getAsJsonObject();
      collectEnvVarsFromFields(platform.getAsJsonArray("fields"), vars);
      collectEnvVarsFromImplicitEnv(platform, vars);
    }
    // From transport fields and implicitEnv
    for (var t : configFlow.getAsJsonArray("transports")) {
      var transport = t.getAsJsonObject();
      collectEnvVarsFromImplicitEnv(transport, vars);
      if (transport.has("modes")) {
        var modes = transport.getAsJsonObject("modes");
        for (var key : modes.keySet()) {
          var mode = modes.getAsJsonObject(key);
          collectEnvVarsFromFields(mode.getAsJsonArray("fields"), vars);
          collectEnvVarsFromImplicitEnv(mode, vars);
          if (mode.has("extraFields")) {
            collectEnvVarsFromFields(mode.getAsJsonArray("extraFields"), vars);
          }
        }
      }
    }
    return vars;
  }

  private void collectEnvVarsFromFields(JsonArray fields, Set<String> vars) {
    if (fields == null) return;
    for (var f : fields) {
      var field = f.getAsJsonObject();
      if (field.has("envVar")) {
        vars.add(field.get("envVar").getAsString());
      }
    }
  }

  private void collectEnvVarsFromImplicitEnv(JsonObject obj, Set<String> vars) {
    if (obj.has("implicitEnv")) {
      var implicit = obj.getAsJsonObject("implicitEnv");
      vars.addAll(implicit.keySet());
    }
  }
}
