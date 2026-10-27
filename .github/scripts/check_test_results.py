# SonarQube MCP Server
# Copyright (C) SonarSource
# mailto:info AT sonarsource DOT com
#
# This program is free software; you can redistribute it and/or
# modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource Sàrl.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
# See the Sonar Source-Available License for more details.
#
# You should have received a copy of the Sonar Source-Available License
# along with this program; if not, see https://sonarsource.com/license/ssal/

"""Fail CI when the selected integration-test suite did not execute."""

import argparse
from pathlib import Path
from xml.etree import ElementTree


def check_report(report: Path) -> int:
    suite = ElementTree.parse(report).getroot()
    executed = int(suite.get("tests", "0")) - int(suite.get("skipped", "0"))
    if suite.tag != "testsuite" or executed <= 0:
        raise ValueError(f"{report}: no integration tests executed")
    if int(suite.get("failures", "0")) or int(suite.get("errors", "0")):
        raise ValueError(f"{report}: integration tests failed")
    return executed


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=Path)
    args = parser.parse_args()
    try:
        count = check_report(args.report)
    except (OSError, ElementTree.ParseError, ValueError) as error:
        parser.exit(1, f"Integration test verification failed: {error}\n")
    print(f"{args.report.name}: {count} integration test(s) executed")
