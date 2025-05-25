package org.sonar.mcp.serverapi.rules.response;

import java.util.List;

public record RepositoriesResponse(List<Repository> repositories) {

  public record Repository(String key, String name, String language) {
  }

}
