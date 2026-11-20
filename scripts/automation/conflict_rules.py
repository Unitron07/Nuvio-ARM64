"""Deterministic resolution of recurring upstream merge conflicts. Standard library only.

Upstream content is treated as data: nothing from the merged tree is executed or
imported, and rules come from the trusted checkout, never from the merge worktree.
Every hunk is either resolved by one named rule or left for a human. A file is
staged only when all of its hunks are resolved and it passes validation.

Local use while a merge is stopped on conflicts:
    python scripts/automation/conflict_rules.py            # resolve and stage
    python scripts/automation/conflict_rules.py --dry-run  # report only
"""
import argparse
import fnmatch
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

DEFAULT_RULES = Path(__file__).resolve().parents[2] / ".github" / "upstream-merge-rules.json"
HUNK = re.compile(r"^<<<<<<< [^\n]*\n(.*?)^\|\|\|\|\|\|\|[^\n]*\n(.*?)^=======\n(.*?)^>>>>>>> [^\n]*\n",
                  re.S | re.M)
IMPORT = re.compile(r"^import\s+([\w.]+)(?:\s+as\s+(\w+))?\s*$")
CALL = re.compile(r"(\.\w+\([^()\n]*\)|Modifier)$")


def load_rules(path=DEFAULT_RULES):
    rules = json.loads(Path(path).read_text(encoding="utf-8"))
    rules["fork_modifier_patterns"] = [re.compile(p) for p in rules.get("fork_modifier_patterns", [])]
    return rules


def _matches(path, globs):
    return any(fnmatch.fnmatch(path, g) for g in globs)


def _blank(text):
    return not text.strip()


def rule_identical(path, ours, base, theirs, ctx, rules):
    return ours if ours == theirs else None


def rule_contained(path, ours, base, theirs, ctx, rules):
    # Both sides added code and one addition already includes the other.
    if not _blank(base):
        return None
    if theirs.strip() and theirs in ours:
        return ours
    if ours.strip() and ours in theirs:
        return theirs
    return None


def rule_union(path, ours, base, theirs, ctx, rules):
    # Append-only resources (string tables, stylesheets): keep both additions.
    if _blank(base) and _matches(path, rules.get("union_paths", [])):
        return ours + theirs
    return None


def _imports(text):
    lines = [l for l in text.splitlines() if l.strip()]
    parsed = [IMPORT.match(l.strip()) for l in lines]
    return [l.strip() for l in lines] if lines and all(parsed) else None


def _imported_name(line):
    m = IMPORT.match(line)
    return m.group(2) or m.group(1).rsplit(".", 1)[-1]


def rule_imports(path, ours, base, theirs, ctx, rules):
    if not path.endswith(".kt"):
        return None
    o, b, t = _imports(ours) or [], _imports(base) or [], _imports(theirs) or []
    if not ((_imports(ours) is not None or _blank(ours)) and (_imports(base) is not None or _blank(base))
            and (_imports(theirs) is not None or _blank(theirs))):
        return None
    added = [l for l in o if l not in b]
    removed = set(b) - set(o)
    result = [l for l in t if l not in removed]
    body = "\n".join(l for l in ctx.splitlines() if not l.lstrip().startswith("import "))
    for line in added:
        # Drop fork imports whose symbol upstream no longer references.
        if line not in result and re.search(r"\b%s\b" % re.escape(_imported_name(line)), body):
            result.append(line)
    # Never keep two imports binding the same simple name.
    names = [_imported_name(l) for l in result]
    if len(names) != len(set(names)):
        aliased = {_imported_name(l) for l in result if " as " in l}
        result = [l for l in result if " as " in l or _imported_name(l) not in aliased]
    return "".join(l + "\n" for l in result)


def rule_fork_modifier(path, ours, base, theirs, ctx, rules):
    # Ours = base + one contiguous chain of fork-only modifier calls. Reapply that
    # chain to upstream's rewritten version at the same anchor call.
    patterns = rules.get("fork_modifier_patterns", [])
    if not patterns or ours == base:
        return None
    spans = sorted(m.span() for p in patterns for m in p.finditer(ours))
    if not spans:
        return None
    start, end = spans[0][0], spans[0][1]
    for s, e in spans[1:]:
        if s != end:
            return None
        end = e
    chain = ours[start:end]
    if ours[:start] + ours[end:] != base:
        return None
    before = base[:start]
    line_prefix = before.rsplit("\n", 1)[-1].lstrip()
    call = CALL.search(before)
    candidates = []
    if line_prefix:
        candidates.append(re.compile(r"(?m)^[ \t]*" + re.escape(line_prefix)))
    if call:
        candidates.append(re.compile(re.escape(call.group(1))))
    for anchor in candidates:
        found = list(anchor.finditer(theirs))
        if len(found) == 1:
            at = found[0].end()
            return theirs[:at] + chain + theirs[at:]
    return None


RULES = [("identical", rule_identical), ("contained", rule_contained), ("union", rule_union),
         ("imports", rule_imports), ("fork-modifier", rule_fork_modifier)]


def resolve_text(path, text, rules):
    """Return (new_text or None, [(hunk_number, rule_name or None)])."""
    crlf = "\r\n" in text
    text = text.replace("\r\n", "\n")
    hunks = list(HUNK.finditer(text))
    ctx = HUNK.sub(lambda m: m.group(1) + m.group(3), text)
    report, pieces, last = [], [], 0
    for number, m in enumerate(hunks, 1):
        ours, base, theirs = m.group(1), m.group(2), m.group(3)
        resolved, name = None, None
        for rule_name, rule in RULES:
            resolved = rule(path, ours, base, theirs, ctx, rules)
            if resolved is not None:
                name = rule_name
                break
        report.append((number, name))
        pieces.append(text[last:m.start()])
        pieces.append(resolved if resolved is not None else m.group(0))
        last = m.end()
    pieces.append(text[last:])
    if not hunks or any(name is None for _, name in report):
        return None, report
    out = "".join(pieces)
    if "\n<<<<<<< " in "\n" + out or "\n>>>>>>> " in out:
        return None, report
    return (out.replace("\n", "\r\n") if crlf else out), report


def validate(path, text):
    """Return an error string, or None when the resolved file is acceptable."""
    if path.endswith(".xml"):
        try:
            root = ET.fromstring(text.encode("utf-8"))
        except ET.ParseError as error:
            return f"XML no longer parses: {error}"
        if root.tag == "resources":
            names = [(e.tag, e.get("name")) for e in root if e.get("name")]
            dupes = sorted({n for n in names if names.count(n) > 1})
            if dupes:
                return "duplicate resource names: " + ", ".join(f"{t}/{n}" for t, n in dupes[:10])
    return None


def _git(*args, cwd=None):
    result = subprocess.run(["git", "-c", "core.hooksPath=/dev/null", *args], cwd=cwd,
                            capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError(f"git {args[0]} failed: {result.stderr}")
    return result.stdout


def resolve_worktree(cwd=None, rules=None, dry_run=False, git=_git):
    """Resolve every unmerged path. Returns {"resolved": {...}, "unresolved": {...}}."""
    rules = rules if rules is not None else load_rules()
    root = Path(cwd or ".")
    paths = [p for p in git("diff", "--name-only", "--diff-filter=U", cwd=cwd).splitlines() if p]
    outcome = {"resolved": {}, "unresolved": {}}
    for path in paths:
        if _matches(path, rules.get("never_auto_paths", [])):
            outcome["unresolved"][path] = "listed in never_auto_paths"
            continue
        git("checkout", "--conflict=diff3", "--", path, cwd=cwd)
        file = root / path
        try:
            text = file.read_bytes().decode("utf-8")
        except (UnicodeDecodeError, FileNotFoundError):
            outcome["unresolved"][path] = "binary, deleted or renamed on one side"
            continue
        new, report = resolve_text(path, text, rules)
        summary = ", ".join(f"#{n} {name or 'manual'}" for n, name in report)
        error = validate(path, new) if new is not None else None
        if new is None or error:
            outcome["unresolved"][path] = error or summary or "no diff3 hunks found"
            continue
        outcome["resolved"][path] = summary
        if not dry_run:
            file.write_bytes(new.encode("utf-8"))
            git("add", "--", path, cwd=cwd)
    return outcome


def format_report(outcome):
    lines = ["| File | Result |", "|---|---|"]
    for path, how in outcome["resolved"].items():
        lines.append(f"| `{path}` | auto: {how} |")
    for path, why in outcome["unresolved"].items():
        lines.append(f"| `{path}` | **manual**: {why} |")
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--rules", default=str(DEFAULT_RULES))
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    outcome = resolve_worktree(rules=load_rules(args.rules), dry_run=args.dry_run)
    print(format_report(outcome))
    if outcome["unresolved"]:
        raise SystemExit(f"{len(outcome['unresolved'])} file(s) still need manual resolution")


if __name__ == "__main__":
    main()
