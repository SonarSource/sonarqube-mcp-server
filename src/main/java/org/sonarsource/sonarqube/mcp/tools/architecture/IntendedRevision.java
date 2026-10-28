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

import jakarta.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.IntendedArchitecture;

/**
 * The revision of an intended architecture: the SHA-256 of a canonical JSON form in which pattern and constraint order does not matter
 * and group order does: models sorted by language then type, each as {@code [language, type, groups, sorted constraints, label, description]}, each group as
 * {@code [label, sorted patterns, interface, interfaceAccess, groups, description, patternId]}, each constraint as the JSON text of {@code [from, to]}, strings
 * sorted by UTF-16 code unit and written as JavaScript's {@code JSON.stringify} writes them.
 */
final class IntendedRevision {

  private IntendedRevision() {
    // static only
  }

  static String of(List<IntendedArchitecture> models) {
    var canonical = models.stream()
      .sorted(Comparator.comparing((IntendedArchitecture model) -> String.valueOf(model.language()))
        .thenComparing(model -> String.valueOf(model.type())))
      .map(IntendedRevision::canonicalModel)
      .toList();
    try {
      var digest = MessageDigest.getInstance("SHA-256").digest(array(canonical).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  static boolean sameGroups(@Nullable List<IntendedArchitecture.Group> left, @Nullable List<IntendedArchitecture.Group> right) {
    return nullToEmpty(left).stream().map(IntendedRevision::canonicalGroup).toList()
      .equals(nullToEmpty(right).stream().map(IntendedRevision::canonicalGroup).toList());
  }

  private static String canonicalModel(IntendedArchitecture model) {
    var constraints = nullToEmpty(model.constraints()).stream()
      .map(constraint -> array(List.of(string(constraint.from()), string(constraint.to()))))
      .sorted()
      .map(IntendedRevision::string)
      .toList();
    var groups = nullToEmpty(model.groups()).stream().map(IntendedRevision::canonicalGroup).toList();
    return array(List.of(string(model.language()), string(model.type()), array(groups), array(constraints), string(model.label()), string(model.description())));
  }

  private static String canonicalGroup(IntendedArchitecture.Group group) {
    var patterns = nullToEmpty(group.patterns()).stream().sorted().map(IntendedRevision::string).toList();
    var groups = nullToEmpty(group.groups()).stream().map(IntendedRevision::canonicalGroup).toList();
    return array(List.of(string(group.label()), array(patterns), String.valueOf(Boolean.TRUE.equals(group.isInterface())),
      String.valueOf(Boolean.TRUE.equals(group.interfaceAccess())), array(groups), string(group.description()),
      string(group.patternId() == null ? null : group.patternId().toLowerCase(java.util.Locale.ROOT))));
  }

  private static String array(List<String> jsonValues) {
    return "[" + String.join(",", jsonValues) + "]";
  }

  private static String string(@Nullable String value) {
    if (value == null) {
      return "null";
    }
    var json = new StringBuilder(value.length() + 2).append('"');
    for (var i = 0; i < value.length(); i++) {
      var c = value.charAt(i);
      switch (c) {
        case '"' -> json.append("\\\"");
        case '\\' -> json.append("\\\\");
        case '\b' -> json.append("\\b");
        case '\f' -> json.append("\\f");
        case '\n' -> json.append("\\n");
        case '\r' -> json.append("\\r");
        case '\t' -> json.append("\\t");
        default -> {
          if (c < 0x20 || isLoneSurrogate(value, i)) {
            json.append(String.format("\\u%04x", (int) c));
          } else {
            json.append(c);
          }
        }
      }
    }
    return json.append('"').toString();
  }

  private static boolean isLoneSurrogate(String value, int i) {
    var c = value.charAt(i);
    if (Character.isHighSurrogate(c)) {
      return i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(i + 1));
    }
    return Character.isLowSurrogate(c) && (i == 0 || !Character.isHighSurrogate(value.charAt(i - 1)));
  }

  private static <T> List<T> nullToEmpty(@Nullable List<T> list) {
    return list == null ? List.of() : list;
  }

}
