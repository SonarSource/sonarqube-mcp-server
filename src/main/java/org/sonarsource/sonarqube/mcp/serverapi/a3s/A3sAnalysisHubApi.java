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
package org.sonarsource.sonarqube.mcp.serverapi.a3s;

import com.google.gson.Gson;
import java.util.List;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.a3s.request.AnalysisCreationRequest;
import org.sonarsource.sonarqube.mcp.serverapi.a3s.response.AnalysisResponse;

public class A3sAnalysisHubApi {

  public static final String ANALYSES_PATH = "/a3s-analysishub/analyses";
  private static final String JSON_CONTENT_TYPE = "application/json";
  private static final Gson GSON = new Gson();

  private final ServerApiHelper helper;
  private final boolean useMockResponse;

  public A3sAnalysisHubApi(ServerApiHelper helper, boolean useMockResponse) {
    this.helper = helper;
    this.useMockResponse = useMockResponse;
  }

  public AnalysisResponse analyze(AnalysisCreationRequest request) {
    if (useMockResponse) {
      return createMockResponse(request);
    }

    var requestBody = GSON.toJson(request);
    try (var response = helper.postApiSubdomain(ANALYSES_PATH, JSON_CONTENT_TYPE, requestBody)) {
      return GSON.fromJson(response.bodyAsString(), AnalysisResponse.class);
    }
  }

  private static AnalysisResponse createMockResponse(AnalysisCreationRequest request) {
    var textRange = new AnalysisResponse.TextRange(10, 15, 4, 25);
    var flowTextRange1 = new AnalysisResponse.TextRange(5, 5, 0, 30);
    var flowTextRange2 = new AnalysisResponse.TextRange(10, 10, 4, 25);

    var flowLocation1 = new AnalysisResponse.Location(
      flowTextRange1,
      "Variable 'data' is assigned here",
      request.filePath()
    );
    var flowLocation2 = new AnalysisResponse.Location(
      flowTextRange2,
      "Potential null dereference here",
      request.filePath()
    );

    var dataFlow = new AnalysisResponse.Flow(
      "DATA",
      "Data flow leading to potential null pointer dereference",
      List.of(flowLocation1, flowLocation2)
    );

    var executionFlowLocation1 = new AnalysisResponse.Location(
      new AnalysisResponse.TextRange(3, 3, 0, 40),
      "Method entry point",
      request.filePath()
    );
    var executionFlowLocation2 = new AnalysisResponse.Location(
      new AnalysisResponse.TextRange(7, 7, 4, 35),
      "Condition evaluated",
      request.filePath()
    );
    var executionFlowLocation3 = new AnalysisResponse.Location(
      new AnalysisResponse.TextRange(10, 10, 4, 25),
      "Issue location reached",
      request.filePath()
    );

    var executionFlow = new AnalysisResponse.Flow(
      "EXECUTION",
      "Execution path through the code",
      List.of(executionFlowLocation1, executionFlowLocation2, executionFlowLocation3)
    );

    var issue1 = new AnalysisResponse.Issue(
      "7a2f5c8d-9e3b-4a1c-bf72-83a892472f22",
      request.filePath(),
      "Possible null pointer dereference of 'data'. This variable was assigned null on line 5.",
      "java:S2259",
      textRange,
      List.of(dataFlow, executionFlow)
    );

    var issue2TextRange = new AnalysisResponse.TextRange(25, 25, 8, 45);
    var issue2 = new AnalysisResponse.Issue(
      "8b3f6d9e-0f4c-5b2d-cg83-94b903583g33",
      request.filePath(),
      "Remove this unused private method 'unusedHelper'.",
      "java:S1144",
      issue2TextRange,
      List.of()
    );

    var issues = List.of(issue1, issue2);

    AnalysisResponse.PatchResult patchResult = null;
    if (request.patchContent() != null && !request.patchContent().isEmpty()) {
      var newIssueTextRange = new AnalysisResponse.TextRange(30, 32, 0, 15);
      var newIssue = new AnalysisResponse.Issue(
        "9c4g7e0f-1g5d-6c3e-dh94-05c014694h44",
        request.filePath(),
        "Add a nested comment explaining why this method is empty.",
        "java:S1186",
        newIssueTextRange,
        List.of()
      );

      var matchedIssue = new AnalysisResponse.Issue(
        "7a2f5c8d-9e3b-4a1c-bf72-83a892472f22",
        request.filePath(),
        "Possible null pointer dereference of 'data'. This variable was assigned null on line 5.",
        "java:S2259",
        textRange,
        List.of(dataFlow, executionFlow)
      );

      patchResult = new AnalysisResponse.PatchResult(
        List.of(newIssue),
        List.of(matchedIssue),
        List.of("old-issue-id-that-was-fixed")
      );
    }

    return new AnalysisResponse(
      "57f08a8b-4a6e-4c64-bf72-83a892472f22",
      issues,
      patchResult
    );
  }
}
