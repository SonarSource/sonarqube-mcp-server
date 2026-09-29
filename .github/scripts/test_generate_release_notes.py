#!/usr/bin/env python3
# SonarQube MCP Server
# Copyright (C) SonarSource SA
# mailto:info AT sonarsource DOT com
#
# This program is free software; you can redistribute it and/or
# modify it under the terms of the GNU Lesser General Public
# License as published by the Free Software Foundation; either
# version 3 of the License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
# Lesser General Public License for more details.
#
# You should have received a copy of the GNU Lesser General Public License
# along with this program; if not, write to the Free Software Foundation,
# Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.

import argparse
import importlib.util
import io
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest.mock import patch


SCRIPT_PATH = Path(__file__).with_name("generate-release-notes.py")
SPEC = importlib.util.spec_from_file_location("generate_release_notes", SCRIPT_PATH)
release_notes = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release_notes)


class ReleaseNotesCliTests(unittest.TestCase):
    def test_release_tag_accepts_three_or_four_numeric_segments(self):
        for tag in ("1.19.0", "1.19.0.123"):
            with self.subTest(tag=tag):
                release_notes.validate_release_tag(tag)

    def test_release_tag_rejects_invalid_or_git_option_values(self):
        for tag in ("1.19", "1.19.0.1.2", "v1.19.0", "1.19.0\n", "--help", "1.19.0^{commit}"):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                release_notes.validate_release_tag(tag)

    def test_output_path_accepts_files_inside_working_directory(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(release_notes.Path, "cwd", return_value=Path(directory)):
            self.assertEqual(Path(directory, "notes.md").resolve(), release_notes.resolve_output_path("notes.md"))
            self.assertEqual(Path(directory, "drafts/notes.md").resolve(), release_notes.resolve_output_path("drafts/notes.md"))

    def test_output_path_rejects_traversal_and_symlink_escape(self):
        with tempfile.TemporaryDirectory() as directory, tempfile.TemporaryDirectory() as outside:
            Path(directory, "escape").symlink_to(outside, target_is_directory=True)
            with patch.object(release_notes.Path, "cwd", return_value=Path(directory)):
                for filename in ("../notes.md", str(Path(outside, "notes.md")), "escape/notes.md"):
                    with self.subTest(filename=filename), self.assertRaises(ValueError):
                        release_notes.resolve_output_path(filename)

    def test_main_writes_release_notes_to_valid_output_path(self):
        with tempfile.TemporaryDirectory() as directory:
            args = argparse.Namespace(tag="1.19.0", out="notes.md", dry_run=False)
            with (patch.object(release_notes, "parse_args", return_value=args),
                  patch.object(release_notes.Path, "cwd", return_value=Path(directory)),
                  patch.object(release_notes, "resolve_upper_bound", return_value="HEAD"),
                  patch.object(release_notes, "resolve_previous_tag", return_value=None),
                  patch.object(release_notes, "list_commits", return_value=[]),
                  patch.object(release_notes, "fetch_jira_tickets", return_value=[]),
                  patch.object(release_notes, "build_prompt", return_value="prompt"),
                  patch.object(release_notes, "call_anthropic", return_value="# Notes"),
                  patch.dict(release_notes.environ, {"CLAUDE_CODE_API_KEY": "test-key"}),
                  redirect_stdout(io.StringIO())):
                release_notes.main()
            self.assertEqual("# Notes\n", Path(directory, "notes.md").read_text(encoding="utf-8"))

    def test_main_rejects_invalid_tag_before_git_or_api_calls(self):
        args = argparse.Namespace(tag="--help", out="notes.md", dry_run=False)
        with patch.object(release_notes, "parse_args", return_value=args), patch.object(release_notes, "resolve_upper_bound") as git:
            with self.assertRaises(ValueError):
                release_notes.main()
            git.assert_not_called()

    def test_dry_run_ignores_output_path(self):
        args = argparse.Namespace(tag="1.19.0", out="../notes.md", dry_run=True)
        with (patch.object(release_notes, "parse_args", return_value=args),
              patch.object(release_notes, "resolve_upper_bound", return_value="HEAD"),
              patch.object(release_notes, "resolve_previous_tag", return_value=None),
              patch.object(release_notes, "list_commits", return_value=[]),
              patch.object(release_notes, "fetch_jira_tickets", return_value=[]),
              patch.object(release_notes, "build_prompt", return_value="prompt"),
              redirect_stdout(io.StringIO()) as output):
            release_notes.main()
        self.assertEqual("prompt\n", output.getvalue())


if __name__ == "__main__":
    unittest.main()
