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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.ProjectDirective;
import org.sonarsource.sonarqube.mcp.tools.ToolResponseUtils;

import static org.assertj.core.api.Assertions.assertThat;

class DirectiveResponsesTests {
  @ParameterizedTest
  @CsvSource({"50,10000,x,25", "50,100,x,25", "50,0,x,25", "50,10000,界,25", "10,10000,x,1000"})
  void it_should_measure_complete_serialized_pages(int count, int descriptionLength, String character, int keyLength) {
    var rows = IntStream.range(0, count).mapToObj(i -> directive(i, character.repeat(descriptionLength), "k".repeat(keyLength) + i)).toList();
    var page = new ArchitectureApi.DirectivePageInfo(1, count, count);
    var original = new FullPage("my_project", rows, page);
    var summary = new SummaryPage("my_project", "summary", rows.stream().map(DirectiveResponses::summary).toList(), page);
    var before = bytes(ToolResponseUtils.toJsonString(original));
    var after = bytes(ToolResponseUtils.toJsonString(summary));
    assertThat(summary.directives()).hasSize(count);
    assertThat(after).isLessThan(before);
    System.out.printf("PAYLOAD page count=%d description=%d character=%s key=%d before=%d after=%d reduction=%.2f%%%n",
      count, descriptionLength, character, keyLength, before, after, 100.0 * (before - after) / before);
  }

  @Test
  void it_should_show_complete_proposed_values_but_not_repeat_them_after_apply() {
    var description = "new".repeat(3000);
    var before = directive(1, "old".repeat(3000), "app:a");
    var after = directive(1, description, "app:a").withoutIdentity();
    var created = directive(2, description, "app:c");
    var changes = List.of(new ProjectDirectives.Change(before.id(), before, after));
    var preview = new ProjectDirectives.Applied("my_project", "dry-run", List.of(created.withoutIdentity()), changes, List.of(before),
      1, List.of("skipped-id"), List.of(), List.of(), List.of(), List.of("permission notice", "concurrent edit notice"));
    var compact = DirectiveResponses.of(preview);
    assertThat(compact.toCreate().getFirst().description()).isEqualTo(description);
    assertThat(compact.changes().getFirst().after()).containsOnlyKeys("description").containsEntry("description", description);
    assertThat(compact.toDelete().getFirst().fromNodeKey()).isEqualTo("app:a");
    assertThat(compact.toDelete().getFirst().descriptionOmitted()).isTrue();
    assertThat(compact.warnings()).containsExactlyElementsOf(preview.warnings());
    assertThat(ToolResponseUtils.toJsonString(compact)).doesNotContain("old".repeat(100));
    var applied = new ProjectDirectives.Applied("my_project", "applied", preview.toCreate(), changes, preview.toDelete(),
      preview.skipped(), preview.skippedIds(), List.of(created), List.of(before), List.of(before.id()), preview.warnings());
    var receipt = DirectiveResponses.of(applied);
    assertThat(receipt.view()).isEqualTo("receipt");
    assertThat(receipt.toCreate()).isNull();
    assertThat(receipt.changes()).isNull();
    assertThat(receipt.toDelete()).isNull();
    assertThat(receipt.created().getFirst().id()).isEqualTo(created.id());
    assertThat(receipt.updated().getFirst().id()).isEqualTo(before.id());
    assertThat(receipt.deleted()).containsExactly(before.id());
    assertThat(receipt.skippedIds()).containsExactly("skipped-id");
    assertThat(receipt.warnings()).containsExactlyElementsOf(applied.warnings());
    var originalBytes = bytes(ToolResponseUtils.toJsonString(applied));
    var receiptBytes = bytes(ToolResponseUtils.toJsonString(receipt));
    assertThat(ToolResponseUtils.toJsonString(receipt)).doesNotContain(description, "old".repeat(100));
    System.out.printf("PAYLOAD mixed-receipt before=%d after=%d reduction=%.2f%%%n", originalBytes, receiptBytes,
      100.0 * (originalBytes - receiptBytes) / originalBytes);
  }

  private static int bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8).length;
  }

  private static ProjectDirective directive(int number, String description, String from) {
    return new ProjectDirective(String.format("00000000-0000-4000-8000-%012d", number), "00000000-0000-4000-8000-000000000099",
      "REMOVE_EDGE", "java", "namespace_graph", from, "app:b", "default_perspective", description, "existing-client");
  }

  private record FullPage(String projectKey, List<ProjectDirective> directives, ArchitectureApi.DirectivePageInfo page) {
  }

  private record SummaryPage(String projectKey, String view, List<DirectiveResponses.Summary> directives, ArchitectureApi.DirectivePageInfo page) {
  }
}
