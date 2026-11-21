import subprocess
import tempfile
import unittest
from pathlib import Path

import conflict_rules as c

RULES = c.load_rules()


def hunk(ours, base, theirs):
    return f"<<<<<<< ours\n{ours}||||||| base\n{base}=======\n{theirs}>>>>>>> theirs\n"


def resolve(path, ours, base, theirs, before="", after=""):
    return c.resolve_text(path, before + hunk(ours, base, theirs) + after, RULES)


class RuleTests(unittest.TestCase):
    def test_union_only_for_allowlisted_resources(self):
        out, report = resolve("composeApp/src/commonMain/composeResources/values/strings.xml",
                              '  <string name="a">A</string>\n', "", '  <string name="b">B</string>\n',
                              "<resources>\n", "</resources>\n")
        self.assertEqual(report, [(1, "union")])
        self.assertIn('name="a"', out)
        self.assertIn('name="b"', out)
        self.assertIsNone(c.validate("x/strings.xml", out))
        out, report = resolve("composeApp/src/commonMain/kotlin/A.kt", "val a = 1\n", "", "val b = 2\n")
        self.assertIsNone(out)
        self.assertEqual(report, [(1, None)])

    def test_union_duplicate_resource_is_rejected_by_validation(self):
        out, _ = resolve("composeApp/src/commonMain/composeResources/values/strings.xml",
                         '<string name="a">A</string>\n', "", '<string name="a">B</string>\n',
                         "<resources>\n", "</resources>\n")
        self.assertIn("duplicate", c.validate("strings.xml", out))

    def test_contained_addition_keeps_superset(self):
        common = "val close = { exit() }\n"
        out, report = resolve("A.kt", common + "val smoke = close\n", "", common)
        self.assertEqual(report, [(1, "contained")])
        self.assertEqual(out, common + "val smoke = close\n")

    def test_fork_modifier_reapplied_to_rewritten_upstream_line(self):
        out, report = resolve(
            "A.kt",
            "    modifier = Modifier.fillMaxSize().nuvioDesktopAxisDragScroll(gridState, Orientation.Vertical),\n",
            "    modifier = Modifier.fillMaxSize(),\n",
            "    modifier = Modifier.fillMaxSize().then(scrollViewportModifier),\n")
        self.assertEqual(report, [(1, "fork-modifier")])
        self.assertEqual(out, "    modifier = Modifier.fillMaxSize().nuvioDesktopAxisDragScroll(gridState, "
                              "Orientation.Vertical).then(scrollViewportModifier),\n")

    def test_fork_modifier_multiline_and_reindented_upstream(self):
        out, report = resolve(
            "A.kt",
            "        modifier = Modifier\n            .fillMaxSize().nuvioDesktopVerticalDragScroll(listState)\n            .zIndex(1f),\n",
            "        modifier = Modifier\n            .fillMaxSize()\n            .zIndex(1f),\n",
            "    Wrapper {\n            modifier = Modifier\n                .fillMaxSize()\n                .zIndex(1f),\n    }\n")
        self.assertEqual(report, [(1, "fork-modifier")])
        self.assertIn("                .fillMaxSize().nuvioDesktopVerticalDragScroll(listState)\n", out)

    def test_fork_modifier_refuses_ambiguous_anchor(self):
        out, report = resolve(
            "A.kt", "a = Modifier.fillMaxSize().nuvioDesktopVerticalDragScroll(s)\n", "a = Modifier.fillMaxSize()\n",
            "b = Modifier.fillMaxSize()\nc = Modifier.fillMaxSize()\n")
        self.assertIsNone(out)

    def test_fork_modifier_refuses_other_fork_edits(self):
        out, _ = resolve("A.kt", "x = Modifier.fillMaxSize().nuvioDesktopVerticalDragScroll(s)\ny = 2\n",
                         "x = Modifier.fillMaxSize()\n", "x = Modifier.fillMaxSize().then(m)\n")
        self.assertIsNone(out)

    def test_import_alias_survives_upstream_import_changes(self):
        alias = "import com.nuvio.app.core.ui.NuvioTouchLazyColumn as LazyColumn\n"
        plain = "import androidx.compose.foundation.lazy.LazyColumn\n"
        items = "import androidx.compose.foundation.lazy.items\n"
        out, report = resolve("A.kt", alias + items, plain + items, plain,
                              "package a\n", "fun f() { LazyColumn() }\n")
        self.assertEqual(report, [(1, "imports")])
        self.assertIn(alias, out)
        self.assertNotIn(plain, out)
        self.assertNotIn(items, out)  # upstream dropped it; ours did not add it

    def test_unused_fork_import_is_dropped(self):
        alias = "import com.nuvio.app.core.ui.NuvioTouchLazyColumn as LazyColumn\n"
        plain = "import androidx.compose.foundation.lazy.LazyColumn\n"
        out, report = resolve("A.kt", alias, plain, "", "package a\n", "fun f() = Unit\n")
        self.assertEqual(report, [(1, "imports")])
        self.assertNotIn("LazyColumn", out)

    def test_partial_resolution_leaves_file_untouched(self):
        text = (hunk("val a = 1\n", "", "val a = 1\n") + "mid\n" + hunk("x(1)\n", "x(0)\n", "x(2)\n"))
        out, report = c.resolve_text("A.kt", text, RULES)
        self.assertIsNone(out)
        self.assertEqual(report, [(1, "identical"), (2, None)])

    def test_crlf_preserved(self):
        text = hunk("a\n", "", "a\n").replace("\n", "\r\n")
        out, _ = c.resolve_text("A.kt", text, RULES)
        self.assertEqual(out, "a\r\n")


class WorktreeTests(unittest.TestCase):
    def git(self, *args):
        return subprocess.run(["git", "-c", "user.name=t", "-c", "user.email=t@t", *args], cwd=self.dir,
                              check=False, capture_output=True, text=True).stdout

    def test_real_merge_resolves_rule_files_and_reports_the_rest(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.dir = tmp
            res = Path(tmp, "composeApp/src/commonMain/composeResources/values")
            res.mkdir(parents=True)
            strings, code, native = res / "strings.xml", Path(tmp, "A.kt"), Path(tmp, "composeApp/src/desktopMain/native")
            native.mkdir(parents=True)
            self.git("init", "-q", "-b", "main")
            strings.write_text("<resources>\n  <string name=\"x\">X</string>\n</resources>\n")
            code.write_text("val m = Modifier.fillMaxSize()\n")
            (native / "b.cpp").write_text("int a = 0;\n")
            self.git("add", "-A"); self.git("commit", "-qm", "base")
            self.git("checkout", "-qb", "up")
            strings.write_text("<resources>\n  <string name=\"x\">X</string>\n  <string name=\"u\">U</string>\n</resources>\n")
            code.write_text("val m = Modifier.fillMaxSize().then(c)\n")
            (native / "b.cpp").write_text("int a = 2;\n")
            self.git("commit", "-qam", "upstream")
            self.git("checkout", "-q", "main")
            strings.write_text("<resources>\n  <string name=\"x\">X</string>\n  <string name=\"f\">F</string>\n</resources>\n")
            code.write_text("val m = Modifier.fillMaxSize().nuvioDesktopVerticalDragScroll(s)\n")
            (native / "b.cpp").write_text("int a = 1;\n")
            self.git("commit", "-qam", "fork")
            self.git("-c", "merge.conflictStyle=diff3", "merge", "--no-commit", "up")
            outcome = c.resolve_worktree(tmp, RULES)
            self.assertEqual(set(outcome["resolved"]),
                             {"A.kt", "composeApp/src/commonMain/composeResources/values/strings.xml"})
            self.assertEqual(list(outcome["unresolved"]), ["composeApp/src/desktopMain/native/b.cpp"])
            self.assertEqual(code.read_text(), "val m = Modifier.fillMaxSize().nuvioDesktopVerticalDragScroll(s).then(c)\n")
            self.assertEqual(self.git("diff", "--name-only", "--diff-filter=U").split(),
                             ["composeApp/src/desktopMain/native/b.cpp"])


if __name__ == "__main__":
    unittest.main()
