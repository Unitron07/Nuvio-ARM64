import io
import json
import subprocess
import tempfile
import unittest
import urllib.error
from pathlib import Path
from unittest.mock import Mock, patch

import downstream as d


SHA = "a" * 40
BASE = "b" * 40
RELEASE = {"id": 42, "tag_name": "0.1.33-alpha", "prerelease": False,
           "draft": False, "updated_at": "2026-10-30T00:00:00Z", "html_url": "https://github.com/up/releases/42"}


class VersionTests(unittest.TestCase):
    def test_version_mapping_and_hotfix(self):
        self.assertEqual(d.version("0.1.33-alpha", 2, 10),
                         {"version": "0.1.33-alpha+arm64.2", "package_version": "1.0.10"})
        self.assertEqual(d.version("v1.2.3", 1, 11)["version"], "1.2.3+arm64.1")

    def test_invalid_versions_and_msi_bounds(self):
        for tag, revision, sequence in [("latest", 1, 1), ("1.0", 1, 1), ("1.0.0", 0, 1),
                                         ("1.0.0", 1, 65536), ("1.0.0+other", 1, 1)]:
            with self.assertRaises(ValueError):
                d.version(tag, revision, sequence)

    def test_immutable_release_identity(self):
        first = d.release_identity(RELEASE, SHA)
        self.assertEqual(first, d.release_identity(RELEASE, SHA))
        self.assertNotEqual(first["key"], d.release_identity(RELEASE, BASE)["key"])
        self.assertNotEqual(first["key"], d.release_identity(RELEASE | {"id": 43}, SHA)["key"])
        self.assertNotEqual(first["key"], d.release_identity(RELEASE | {"prerelease": True}, SHA)["key"])
        self.assertIsNone(d.release_identity(RELEASE | {"draft": True}, SHA))
        with self.assertRaises(ValueError):
            d.release_identity(RELEASE, "missing tag")


class ApiTests(unittest.TestCase):
    @patch.dict(d.os.environ, GH_TOKEN="test-only")
    def test_network_permissions_and_rate_limits_fail_closed(self):
        for code in [403, 429, 500]:
            with patch.object(d.urllib.request, "urlopen", side_effect=urllib.error.HTTPError("url", code, "error", {}, None)):
                with self.assertRaisesRegex(RuntimeError, f"HTTP {code}"):
                    d.GitHub().request("GET", "repos/test/repo/releases")

    @patch.dict(d.os.environ, GH_TOKEN="test-only")
    def test_missing_only_when_explicit(self):
        with patch.object(d.urllib.request, "urlopen", side_effect=urllib.error.HTTPError("url", 404, "missing", {}, None)):
            self.assertIsNone(d.GitHub().request("GET", "repos/test/repo/labels/name", missing=True))
            with self.assertRaises(RuntimeError):
                d.GitHub().request("GET", "repos/test/repo/releases/tags/missing")

    def test_pagination(self):
        api = d.GitHub()
        api.request = Mock(side_effect=[[{}] * 100, [{"id": 101}]])
        self.assertEqual(len(api.pages("repos/test/repo/releases")), 101)
        self.assertIn("page=2", api.request.call_args.args[1])

    def test_candidate_dedup_and_closed_record(self):
        api = Mock()
        api.pages.return_value = [{"number": 2, "body": "marker\nbody", "state": "closed"}]
        d.upsert_issue(api, "test/repo", "marker", "title", "body")
        api.request.assert_not_called()


class SyncTests(unittest.TestCase):
    def setUp(self):
        self.api = Mock()
        self.api.pages.side_effect = [[], [RELEASE]]
        self.api.request.return_value = {"number": 7, "html_url": "https://example/pr/7", "draft": True,
                                         "head": {"sha": SHA}}

    def fake_git(self, *args, **kwargs):
        if args[0] == "rev-parse":
            return SHA if args[1].startswith("FETCH_HEAD") else BASE
        if args[0] == "merge-base":
            return "c" * 40
        if args[0] == "ls-remote":
            return BASE + "\trefs/heads/main"
        return "file.kt" if args[0] == "diff" else "commit"

    @patch.object(d, "ancestry", return_value=True)
    def test_no_changes_has_no_mutations(self, ancestor):
        with patch.object(d, "git", side_effect=self.fake_git):
            d.sync(self.api, "test/repo", "up/repo", "Dev")
        self.api.request.assert_not_called()
        self.api.pages.assert_not_called()

    @patch.object(d, "ancestry", return_value=False)
    def test_existing_pr_is_not_force_pushed(self, ancestor):
        self.api.pages.side_effect = [[{"number": 7, "state": "open", "draft": True, "html_url": "pr", "head": {"sha": SHA}}], []]
        with patch.object(d, "git", side_effect=self.fake_git) as git:
            d.sync(self.api, "test/repo", "up/repo", "Dev")
        self.assertFalse(any(c.args[0] == "push" for c in git.call_args_list))
        dispatch = next(c for c in self.api.request.call_args_list if 'dispatches' in c.args[1])
        self.assertEqual(dispatch.args[2]["inputs"]["source_sha"], SHA)

    @patch.object(d, "ancestry", return_value=False)
    def test_closed_candidate_requires_manual_decision(self, ancestor):
        self.api.pages.side_effect = [[{"state": "closed", "html_url": "pr"}]]
        with patch.object(d, "git", side_effect=self.fake_git):
            d.sync(self.api, "test/repo", "up/repo", "Dev")
        self.api.request.assert_not_called()

    @patch.object(d, "ancestry", return_value=False)
    def test_duplicate_run_does_not_dispatch_validation_again(self, ancestor):
        self.api.pages.side_effect = [[{"state": "open", "draft": True, "html_url": "pr",
                                       "body": f"<!-- validation-dispatched:{SHA} -->", "head": {"sha": SHA}}], []]
        with patch.object(d, "git", side_effect=self.fake_git):
            d.sync(self.api, "test/repo", "up/repo", "Dev")
        self.api.request.assert_not_called()

    @patch.object(d, "ancestry", return_value=False)
    def test_new_commits_merge_without_rewriting_history(self, ancestor):
        with patch.object(d, "git", side_effect=self.fake_git) as git, \
             patch.object(d.subprocess, "run", return_value=Mock(returncode=0)):
            d.sync(self.api, "test/repo", "up/repo", "Dev")
        pushes = [c for c in git.call_args_list if c.args[0] == "push"]
        self.assertEqual(len(pushes), 1)
        self.assertNotIn("--force", pushes[0].args)
        self.assertTrue(any(c.args[0] == "commit" for c in git.call_args_list))

    @patch.object(d, "ancestry", return_value=False)
    def test_conflict_reports_files_without_commit_or_push(self, ancestor):
        with patch.object(d, "git", side_effect=self.fake_git) as git, \
             patch.object(d.subprocess, "run", return_value=Mock(returncode=1)), \
             patch.object(d, "upsert_issue", return_value={"html_url": "conflict"}) as issue:
            d.sync(self.api, "test/repo", "up/repo", "Dev")
        self.assertFalse(any(c.args[0] in ("commit", "push") for c in git.call_args_list))
        self.assertIn("file.kt", issue.call_args.args[-1])

    @patch.object(d, "ancestry", return_value=False)
    def test_moved_main_cannot_push_stale_integration(self, ancestor):
        def moved(*args, **kwargs):
            return SHA + "\tmain" if args[0] == "ls-remote" else self.fake_git(*args, **kwargs)
        with patch.object(d, "git", side_effect=moved) as git, \
             patch.object(d.subprocess, "run", return_value=Mock(returncode=0)):
            with self.assertRaisesRegex(RuntimeError, "main moved"):
                d.sync(self.api, "test/repo", "up/repo", "Dev")
        self.assertFalse(any(c.args[0] == "push" for c in git.call_args_list))


class MonitorTests(unittest.TestCase):
    @patch.object(d, "git", return_value=SHA)
    @patch.object(d, "ancestry", return_value=False)
    def test_unintegrated_stable_and_prerelease_only_record_candidates(self, ancestor, git):
        for release in [RELEASE, RELEASE | {"prerelease": True, "tag_name": "1.0.0-beta.1"}]:
            api = Mock()
            api.pages.return_value = [release]
            with patch.object(d, "upsert_issue", return_value={"html_url": "candidate"}) as issue:
                d.monitor(api, "test/repo", "up/repo", "")
            self.assertIn("**False**", issue.call_args.args[-1])
            api.request.assert_not_called()  # no automatic packaging or publishing

    def test_missing_tag_or_api_failure_does_not_create_candidate(self):
        api = Mock()
        api.request.side_effect = RuntimeError("HTTP 404")
        with patch.object(d, "upsert_issue") as issue:
            with self.assertRaises(RuntimeError):
                d.monitor(api, "test/repo", "up/repo", "missing")
        issue.assert_not_called()


if __name__ == "__main__":
    unittest.main()
