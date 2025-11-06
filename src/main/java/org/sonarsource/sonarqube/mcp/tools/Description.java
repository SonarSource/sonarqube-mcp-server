/*
 * SonarQube MCP Server
 * Copyright (C) 2025 SonarSource
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to provide human-readable descriptions for record components and fields.
 * Used to generate documentation in MCP output schemas.
 * 
 * <p>Example usage:</p>
 * <pre>
 * public record IssueOutput(
 *   {@literal @}Description("Unique issue identifier") String key,
 *   {@literal @}Description("Rule that triggered the issue") String rule,
 *   {@literal @}Description("Project key where the issue was found") String project
 * ) {}
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.RECORD_COMPONENT, ElementType.PARAMETER, ElementType.FIELD})
public @interface Description {
  /**
   * The human-readable description of the field.
   */
  String value();
}


