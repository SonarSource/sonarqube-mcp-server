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
package org.sonarsource.sonarqube.mcp.tools.duplications;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.annotation.Nullable;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchDuplicatedFilesToolResponse(
  @JsonPropertyDescription("List of files with duplications, sorted by most duplicated first") List<DuplicatedFile> files,
  @JsonPropertyDescription("Pagination information") Paging paging,
  @JsonPropertyDescription("Summary of duplication metrics") @Nullable Summary summary
) {

  public record DuplicatedFile(
    @JsonPropertyDescription("File key") String key,
    @JsonPropertyDescription("File name") String name,
    @JsonPropertyDescription("File path") @Nullable String path,
    @JsonPropertyDescription("Number of duplicated lines") @Nullable Integer duplicatedLines,
    @JsonPropertyDescription("Number of duplicated blocks") @Nullable Integer duplicatedBlocks,
    @JsonPropertyDescription("Duplication density percentage") @Nullable String duplicatedLinesDensity
  ) {}

  public record Paging(
    @JsonPropertyDescription("Current page number") int pageIndex,
    @JsonPropertyDescription("Number of results per page") int pageSize,
    @JsonPropertyDescription("Total number of duplicated files") int total
  ) {}

  public record Summary(
    @JsonPropertyDescription("Total duplicated lines in the project") @Nullable Integer totalDuplicatedLines,
    @JsonPropertyDescription("Total duplicated blocks in the project") @Nullable Integer totalDuplicatedBlocks,
    @JsonPropertyDescription("Overall duplication density percentage") @Nullable String overallDuplicationDensity
  ) {}

}
