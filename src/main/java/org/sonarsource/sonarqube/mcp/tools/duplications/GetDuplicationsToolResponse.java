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
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GetDuplicationsToolResponse(
  @JsonPropertyDescription("List of duplication groups found") List<Duplication> duplications,
  @JsonPropertyDescription("Map of file references to file information") List<FileInfo> files
) {

  public record Duplication(
    @JsonPropertyDescription("List of code blocks involved in this duplication") List<Block> blocks
  ) {}

  public record Block(
    @JsonPropertyDescription("Starting line number") int from,
    @JsonPropertyDescription("Number of lines") int size,
    @JsonPropertyDescription("File name") String fileName,
    @JsonPropertyDescription("File key") String fileKey
  ) {}

  public record FileInfo(
    @JsonPropertyDescription("File key") String key,
    @JsonPropertyDescription("File name") String name
  ) {}

}
