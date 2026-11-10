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
package org.sonarsource.sonarqube.mcp.tools.architecture;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.ProjectFinding;
import org.sonarsource.sonarqube.mcp.tools.Tool.Arguments;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.*;

class FindingContinuationTests {
  private static final Gson GSON = new Gson();
  private static final FindingCursor.Query QUERY = new FindingCursor.Query(2, 3, "flaw", "tangle", "java", null, null, null);
  private static final String BINDING = FindingCursor.binding("project", "org", "branch");

  private static ProjectFinding finding(int files, int keys) {
    // Deliberate duplicates exercise preservation of list entries, rather than set membership.
    return new ProjectFinding.Finding<>("tangle", "flaw", "java",
      new ProjectFinding.TangleDetails(IntStream.range(0, keys).mapToObj(i -> "node-" + i / 2).toList(), BigDecimal.valueOf(keys)),
      IntStream.range(0, files).mapToObj(i -> "src/F" + i / 2 + ".java").toList());
  }

  private static JsonObject json(Object finding) {
    return GSON.toJsonTree(finding).getAsJsonObject();
  }

  @Test
  void it_should_reconstruct_large_lists_and_independently_exhaust_each_collection() {
    for (var sizes : List.of(new int[] {10000, 230}, new int[] {1, 230}, new int[] {230, 0}, new int[] {0, 230})) {
      var finding = finding(sizes[0], sizes[1]);
      var files = new ArrayList<String>();
      var keys = new ArrayList<String>();
      var result = json(FindingResponses.preview(finding, BINDING, QUERY));
      var limit = 10;
      var calls = 0;
      while (true) {
        assertThat(++calls).isLessThan(110);
        assertThat(result.get("fileOffset").getAsInt()).isEqualTo(files.size());
        assertThat(result.get("fileCount").getAsInt()).isEqualTo(sizes[0]);
        var chunkFiles = result.getAsJsonArray("files");
        var details = result.getAsJsonObject("details");
        var chunkKeys = details.getAsJsonArray("nodeKeys");
        assertThat(chunkFiles.size()).isLessThanOrEqualTo(limit);
        assertThat(chunkKeys.size()).isLessThanOrEqualTo(limit);
        assertThat(details.get("nodeKeyOffset").getAsInt()).isEqualTo(keys.size());
        chunkFiles.forEach(file -> files.add(file.getAsString()));
        chunkKeys.forEach(key -> keys.add(key.getAsString()));
        if (!result.has("nextCursor")) {
          break;
        }
        var cursor = FindingCursor.decode(result.get("nextCursor").getAsString());
        assertThat(cursor.query()).isEqualTo(QUERY);
        result = json(FindingResponses.continueFinding(List.of(finding), BINDING, cursor));
        limit = 100;
      }
      var original = (ProjectFinding.Finding<?>) finding;
      assertThat(files).containsExactlyElementsOf(original.files());
      assertThat(keys).containsExactlyElementsOf(((ProjectFinding.TangleDetails) original.details()).nodeKeys());
    }
  }

  @Test
  void it_should_reject_stale_context_missing_finding_and_exhausted_offsets() {
    var finding = finding(120, 0);
    var cursor = FindingCursor.decode(json(FindingResponses.preview(finding, BINDING, QUERY)).get("nextCursor").getAsString());
    assertThatThrownBy(() -> FindingResponses.continueFinding(List.of(finding), "different", cursor)).hasMessageContaining("Refresh");
    var changed = List.of(finding(121, 0));
    assertThatThrownBy(() -> FindingResponses.continueFinding(changed, BINDING, cursor)).hasMessageContaining("Refresh");
    var exhausted = new FindingCursor(1, BINDING, QUERY, cursor.fingerprint(), 120, 0);
    assertThatThrownBy(() -> FindingResponses.continueFinding(List.of(finding), BINDING, exhausted)).hasMessageContaining("Refresh");
    var outside = new FindingCursor(1, BINDING, QUERY, cursor.fingerprint(), 121, 0);
    assertThatThrownBy(() -> FindingResponses.continueFinding(List.of(finding), BINDING, outside)).hasMessageContaining("Refresh");
  }

  @Test
  void it_should_reject_malformed_cursors_and_invalid_wire_values() {
    for (var encoded : List.of("", "?", "a".repeat(4097), encode("{}"), encode("null"), encode("[]"))) {
      assertThatThrownBy(() -> FindingCursor.decode(encoded)).hasMessageContaining("Refresh");
    }
    var valid = json(FindingCursor.decode(json(FindingResponses.preview(finding(120, 0), BINDING, QUERY)).get("nextCursor").getAsString()));
    for (var field : List.of("version", "binding", "query", "fingerprint", "fileOffset", "nodeKeyOffset")) {
      var missing = valid.deepCopy();
      missing.remove(field);
      var encoded = encode(missing.toString());
      assertThatThrownBy(() -> FindingCursor.decode(encoded)).hasMessageContaining("Refresh");
    }
    for (var field : List.of("version", "fileOffset", "nodeKeyOffset")) {
      for (var value : List.of("1", -1, 1.5, 2147483648L, true)) {
        var invalid = valid.deepCopy();
        invalid.add(field, GSON.toJsonTree(value));
        var encoded = encode(invalid.toString());
        assertThatThrownBy(() -> FindingCursor.decode(encoded)).hasMessageContaining("Refresh");
      }
    }
  }

  @Test
  void it_should_validate_cursor_query_shape_filters_and_selectors() {
    var valid = json(FindingCursor.decode(json(FindingResponses.preview(finding(120, 0), BINDING, QUERY)).get("nextCursor").getAsString()));
    for (var field : List.of("pageIndex", "pageSize", "category", "type", "language", "component", "from", "to")) {
      for (var value : List.of("invalid", true, -1, 0, 51, 1.5)) {
        var invalid = valid.deepCopy();
        invalid.getAsJsonObject("query").add(field, GSON.toJsonTree(value));
        var encoded = encode(invalid.toString());
        if (field.equals("pageIndex") && (value.equals(51))) {
          continue;
        }
        if (field.equals("component") && value.equals("invalid")) {
          continue;
        }
        assertThatThrownBy(() -> FindingCursor.decode(encoded)).as("%s=%s", field, value).hasMessageContaining("Refresh");
      }
    }
    var extra = valid.deepCopy();
    extra.getAsJsonObject("query").addProperty("unknown", "value");
    var encoded = encode(extra.toString());
    assertThatThrownBy(() -> FindingCursor.decode(encoded)).hasMessageContaining("Refresh");
  }

  private static String encode(String json) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
  }

  @SonarQubeMcpServerTest
  void it_should_continue_the_original_filtered_page_and_reject_stale_or_wrong_context(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    var path = ArchitectureApi.PROJECT_FINDINGS_PATH + "?organizationId=" + CLOUD_ORGANIZATION_ID + "&branchId=" + MAIN_BRANCH_ID
      + "&category=flaw&type=tangle&language=java&pageIndex=2&pageSize=3";
    var body = "{\"findings\":[" + GSON.toJson(finding(120, 30)) + "," + GSON.toJson(finding(140, 30)) + "],\"page\":{\"pageIndex\":2,\"pageSize\":3,\"total\":4}}";
    harness.getMockSonarQubeServer().stubFor(get(path).willReturn(okJson(body)));
    harness.getMockSonarQubeServer().stubFor(get("/organizations/organizations?excludeEligibility=true").willReturn(okJson("[]")));
    harness.getMockSonarQubeServer().stubFor(get("/organizations/organizations?organizationKey=org&excludeEligibility=true")
      .willReturn(okJson("[{\"key\":\"org\",\"uuidV4\":\"" + CLOUD_ORGANIZATION_ID + "\"}]")));
    var client = harness.newClient(Map.of("SONARQUBE_IS_CLOUD", "true", "SONARQUBE_TOOLSETS", "architecture"));
    var preview = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.ofEntries(Map.entry("target", "findings"), Map.entry("projectKey", "my_project"), Map.entry("organization", "org"),
      Map.entry("category", "flaw"), Map.entry("type", "tangle"), Map.entry("language", "java"), Map.entry("pageIndex", 2), Map.entry("pageSize", 3), Map.entry("component", "node-1")));
    assertThat(preview.isError()).as(textOf(preview)).isFalse();
    var previews = JsonParser.parseString(textOf(preview)).getAsJsonObject().getAsJsonArray("findings");
    assertThat(previews).hasSize(2);
    var cursor = previews.get(0).getAsJsonObject().get("nextCursor").getAsString();
    assertThat(FindingCursor.decode(cursor).query().component()).isEqualTo("node-1");
    var arguments = Map.<String, Object>of("target", "findings", "projectKey", "my_project", "organization", "org", "cursor", cursor);
    var continued = client.callTool(ArchitectureGetTool.TOOL_NAME, arguments);
    assertThat(continued.isError()).as(textOf(continued)).isFalse();
    var chunk = JsonParser.parseString(textOf(continued)).getAsJsonObject().getAsJsonArray("findings").get(0).getAsJsonObject();
    assertThat(chunk.getAsJsonArray("files")).hasSize(100);
    assertThat(chunk.get("fileOffset").getAsInt()).isEqualTo(10);
    var secondArguments = new HashMap<>(arguments);
    secondArguments.put("cursor", previews.get(1).getAsJsonObject().get("nextCursor").getAsString());
    assertThat(secondArguments).doesNotContainEntry("cursor", cursor);
    var second = client.callTool(ArchitectureGetTool.TOOL_NAME, secondArguments);
    assertThat(second.isError()).as(textOf(second)).isFalse();
    assertThat(JsonParser.parseString(textOf(second)).getAsJsonObject().getAsJsonArray("findings").get(0).getAsJsonObject().get("fileCount").getAsInt())
      .isEqualTo(140);
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(path)).isEqualTo(3);
    var valid = Map.<String, Object>of("component", "node-1", "from", "node-0", "to", "node-1", "depth", 1, "language", "java",
      "category", "flaw", "type", "tangle", "pageIndex", 2, "pageSize", 3, "directiveId", "00000000-0000-4000-8000-000000000001");
    var beforeRejectedCalls = harness.getMockSonarQubeServer().getReceivedRequests().size();
    for (var entry : valid.entrySet()) {
      var conflicting = new HashMap<>(arguments);
      conflicting.put(entry.getKey(), entry.getValue());
      var rejected = client.callTool(ArchitectureGetTool.TOOL_NAME, conflicting);
      assertThat(rejected.isError()).isTrue();
      assertThat(textOf(rejected)).as(entry.getKey()).contains("Use cursor only");
    }
    assertThat(harness.getMockSonarQubeServer().getReceivedRequests()).hasSize(beforeRejectedCalls);
    harness.getMockSonarQubeServer().stubFor(get("/organizations/organizations?organizationKey=other-org&excludeEligibility=true")
      .willReturn(okJson("[{\"key\":\"other-org\",\"uuidV4\":\"other-uuid\"}]")));
    var wrongOrganization = new HashMap<>(arguments);
    wrongOrganization.put("organization", "other-org");
    assertThat(textOf(client.callTool(ArchitectureGetTool.TOOL_NAME, wrongOrganization))).contains("Refresh");
    harness.getMockSonarQubeServer().stubFor(get(path).willReturn(okJson(body.replace("F0.java", "changed.java"))));
    assertThat(textOf(client.callTool(ArchitectureGetTool.TOOL_NAME, arguments))).contains("Refresh");
  }

  @Test
  void it_should_reject_a_cursor_on_other_targets_before_backend_access() {
    var api = mock(ServerApi.class);
    var tool = new ArchitectureGetTool(() -> api, true, null);
    var args = new Arguments(Map.of("target", "current", "projectKey", "project", "cursor", "invalid"), null);
    assertThatThrownBy(() -> tool.execute(args)).hasMessageContaining("Use cursor only");
    verifyNoInteractions(api);
  }
}
