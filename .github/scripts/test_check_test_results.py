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

import tempfile
import unittest
from pathlib import Path
from xml.etree import ElementTree

from check_test_results import check_report


class CheckTestResultsTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.report = Path(directory.name) / "TEST-example.xml"

    def test_rejects_missing_report(self):
        with self.assertRaises(FileNotFoundError):
            check_report(self.report)

    def test_rejects_malformed_report(self):
        self.report.write_text("invalid XML")
        with self.assertRaises(ElementTree.ParseError):
            check_report(self.report)

    def test_rejects_empty_skipped_or_failed_suites(self):
        for attributes in [
            'tests="0"',
            'tests="2" skipped="2"',
            'tests="2" failures="1"',
            'tests="2" errors="1"',
        ]:
            with self.subTest(attributes=attributes):
                self.report.write_text(f"<testsuite {attributes}/>")
                with self.assertRaises(ValueError):
                    check_report(self.report)

    def test_counts_executed_tests(self):
        self.report.write_text('<testsuite tests="3" skipped="1" failures="0" errors="0"/>')
        self.assertEqual(check_report(self.report), 2)


if __name__ == "__main__":
    unittest.main()
