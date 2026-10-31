"""Trusted automation entry point. Standard library only; never executes upstream code.

Run from a credential-free checkout of the downstream default branch. Git merges
use a separate worktree with hooks disabled. API failures are fatal, never 'no news'.
"""
import argparse
import base64
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import urllib.error
import urllib.parse
import urllib.request


def git(*args, cwd=None, ok=(0,), authenticated=False):
    env = dict(os.environ, GIT_LFS_SKIP_SMUDGE="1", GIT_TERMINAL_PROMPT="0")
    if authenticated:
        auth = base64.b64encode(f"x-access-token:{env['GH_TOKEN']}".encode()).decode()
        env.update(GIT_CONFIG_COUNT="1", GIT_CONFIG_KEY_0="http.https://github.com/.extraheader",
                   GIT_CONFIG_VALUE_0=f"AUTHORIZATION: basic {auth}")
    result = subprocess.run(["git", "-c", "core.hooksPath=/dev/null", *args], cwd=cwd,
                            env=env, text=True, capture_output=True)
    if result.returncode not in ok:
        raise RuntimeError(f"git {args[0]} failed ({result.returncode}): {result.stderr}")
    return result.stdout.strip()


class GitHub:
    def request(self, method, path, data=None, missing=False):
        request = urllib.request.Request("https://api.github.com/" + path,
            data=None if data is None else json.dumps(data).encode(), method=method,
            headers={"Authorization": "Bearer " + os.environ["GH_TOKEN"],
                     "Accept": "application/vnd.github+json", "User-Agent": "Nuvio-ARM64-maintenance",
                     "X-GitHub-Api-Version": "2022-11-28"})
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                payload = response.read()
                return json.loads(payload) if payload else None
        except urllib.error.HTTPError as error:
            if error.code == 404 and missing:
                return None
            raise RuntimeError(f"GitHub {method} {path}: HTTP {error.code}; retry after checking permissions/rate limits") from error

    def pages(self, path):
        result = []
        for page in range(1, 1001):
            items = self.request("GET", f"{path}{'&' if '?' in path else '?'}per_page=100&page={page}")
            result.extend(items)
            if len(items) < 100:
                return result
        raise RuntimeError("Pagination limit exceeded; refusing partial discovery")


def validate_repo(value):
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", value):
        raise ValueError("Invalid repository")
    return value


def release_identity(release, source):
    if release.get("draft"):
        return None
    if not re.fullmatch(r"[0-9a-f]{40}", source):
        raise ValueError("Release tag must resolve to a commit")
    tag = release["tag_name"]
    version(tag, 1, 1)  # reject unsupported upstream versions before recording them
    canonical = {"id": release["id"], "tag": tag, "source": source,
                 "prerelease": release["prerelease"], "updated_at": release["updated_at"]}
    digest = hashlib.sha256(json.dumps(canonical, sort_keys=True).encode()).hexdigest()[:16]
    return canonical | {"key": f"{release['id']}-{digest}"}


def version(upstream, revision, sequence):
    # Build metadata identifies the fork without making stable builds prereleases.
    # The fork updater explicitly compares the numeric arm64 revision.
    if not re.fullmatch(r"v?\d+\.\d+\.\d+(?:-[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?", upstream):
        raise ValueError("Unsupported upstream version")
    if not 1 <= revision <= 65535 or not 1 <= sequence <= 65535:
        raise ValueError("revision and monotonically allocated package sequence must be 1..65535")
    return {"version": upstream.removeprefix("v") + f"+arm64.{revision}",
            "package_version": f"1.0.{sequence}"}


def summary(message):
    print(message)
    if os.getenv("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as out:
            out.write(message + "\n")


def upsert_issue(api, repo, marker, title, body):
    issues = api.pages(f"repos/{repo}/issues?state=all&creator=github-actions%5Bbot%5D")
    found = next((i for i in issues if not i.get("pull_request") and marker in (i.get("body") or "")), None)
    payload = {"title": title, "body": marker + "\n" + body}
    if found:
        # Keep closed records closed. Immutable candidate identities avoid repeated notifications.
        if found["body"] != payload["body"]:
            api.request("PATCH", f"repos/{repo}/issues/{found['number']}", payload)
        return found
    return api.request("POST", f"repos/{repo}/issues", payload)


def ancestry(older, newer, cwd=None):
    result = subprocess.run(["git", "merge-base", "--is-ancestor", older, newer], cwd=cwd)
    if result.returncode not in (0, 1):
        raise RuntimeError("Cannot determine source ancestry")
    return result.returncode == 0


def sync(api, repo, upstream, branch):
    validate_repo(upstream)
    git("check-ref-format", "--branch", branch)
    git("fetch", "--no-tags", "origin", "main")
    git("fetch", "--no-tags", f"https://github.com/{upstream}.git", f"refs/heads/{branch}")
    candidate = git("rev-parse", "FETCH_HEAD^{commit}")
    base = git("rev-parse", "origin/main")
    previous = git("merge-base", base, candidate)
    if ancestry(candidate, base):
        summary(f"No upstream changes: {candidate} is integrated.")
        return
    name = f"automation/upstream-{candidate}"
    prs = api.pages(f"repos/{repo}/pulls?state=all&head={repo.split('/')[0]}:{name}&base=main")
    existing = next(iter(prs), None)
    if existing and existing["state"] == "closed":
        summary(f"Candidate was already reviewed/closed: {existing['html_url']}; manual decision required.")
        return
    commits = git("log", "--format=%h %s", f"{base}..{candidate}")
    files = git("diff", "--stat", previous, candidate)
    releases = [r["tag_name"] for r in api.pages(f"repos/{upstream}/releases") if not r["draft"]]
    body = (f"Upstream: https://github.com/{upstream}, branch `{branch}`\n\n"
            f"Previous common/integrated ancestor: `{previous}`\nCandidate: `{candidate}`\nDownstream base: `{base}`\n\n"
            f"Published upstream versions (not a claim of integration): {', '.join(releases[:20])}\n\n"
            f"Commits:\n```text\n{commits[:18000]}\n```\nFiles:\n```text\n{files[:12000]}\n```\n\n"
            "ARM64 risk review: native JNI/libmpv/Skiko/WebView2 and lifecycle; D3D11VA/fallback; "
            "headers/addons/debrid; touch/scroll/menu/sidebar/cache; auth/sync/resume/profile isolation; "
            "x64 and shared mobile code; unsupported P2P guard. See STAGE6_SURFACE_VALIDATION.md.\n\n"
            f"Validation: https://github.com/{repo}/actions/workflows/upstream-validation.yml\n"
            "Inspect matching SHA checks and perform Surface tests. Never auto-merge.\n")
    if not existing:
        work = Path(os.environ.get("RUNNER_TEMP", ".")) / "upstream-merge"
        git("worktree", "add", "--detach", str(work), base)
        try:
            git("config", "user.name", "github-actions[bot]", cwd=work)
            git("config", "user.email", "41898282+github-actions[bot]@users.noreply.github.com", cwd=work)
            result = subprocess.run(["git", "-c", "core.hooksPath=/dev/null", "merge", "--no-ff", "--no-commit", candidate],
                                    cwd=work, capture_output=True, text=True,
                                    env=dict(os.environ, GIT_LFS_SKIP_SMUDGE="1"))
            if result.returncode:
                conflicts = git("diff", "--name-only", "--diff-filter=U", cwd=work)
                git("merge", "--abort", cwd=work)
                if not conflicts:
                    raise RuntimeError("Integration failed without merge conflicts: " + result.stderr)
                issue = upsert_issue(api, repo, f"<!-- upstream-conflict:{candidate} -->",
                    f"Upstream sync requires manual intervention: {candidate[:12]}",
                    body + f"\nMerge conflicts (no integration commit created):\n```text\n{conflicts}\n```\n")
                summary(issue["html_url"])
                return
            git("commit", "-m", f"Merge {upstream} {branch} at {candidate}", cwd=work)
            # Refuse stale snapshots; a normal push never rewrites a shared branch.
            if git("ls-remote", "origin", "refs/heads/main").split()[0] != base:
                raise RuntimeError("main moved during integration; rerun against fresh main")
            git("push", "origin", f"HEAD:refs/heads/{name}", cwd=work, authenticated=True)
        finally:
            git("worktree", "remove", "--force", str(work))
        existing = api.request("POST", f"repos/{repo}/pulls", {
            "title": f"Upstream Sync: NuvioDesktop {branch} â€” {datetime.date.today()}",
            "head": name, "base": "main", "draft": True, "body": body + "\nMerge conflict status: clean at creation.\n"})
        api.request("POST", f"repos/{repo}/issues/{existing['number']}/labels", {"labels": ["upstream-sync"]})
    # Existing bot branches are immutable. Dispatch exact PR head through a trusted
    # default-branch workflow; GITHUB_TOKEN push/PR events alone are insufficient.
    if not existing["draft"]:
        raise RuntimeError("Sync PR is no longer draft; owner controls further updates")
    validation_marker = f"<!-- validation-dispatched:{existing['head']['sha']} -->"
    if validation_marker in (existing.get("body") or ""):
        summary(existing["html_url"] + " (unchanged; validation already requested)")
        return
    api.request("POST", f"repos/{repo}/actions/workflows/upstream-validation.yml/dispatches",
                {"ref": "main", "inputs": {"source_sha": existing["head"]["sha"]}})
    api.request("PATCH", f"repos/{repo}/pulls/{existing['number']}",
                {"body": (existing.get("body") or body) + "\n" + validation_marker})
    summary(existing["html_url"])


def monitor(api, repo, upstream, tag):
    releases = ([api.request("GET", f"repos/{upstream}/releases/tags/{urllib.parse.quote(tag, safe='')}")]
                if tag else api.pages(f"repos/{upstream}/releases"))
    git("fetch", "--no-tags", "origin", "main")
    base = git("rev-parse", "origin/main")
    for release in releases:
        if release["draft"]:
            continue
        git("fetch", "--no-tags", f"https://github.com/{upstream}.git", f"refs/tags/{release['tag_name']}")
        source = git("rev-parse", "FETCH_HEAD^{commit}")
        identity = release_identity(release, source)
        integrated = ancestry(source, base)
        marker = f"<!-- upstream-release:{identity['key']} -->"
        body = (f"Upstream release: {release['html_url']}\n\n```json\n{json.dumps(identity, indent=2)}\n```\n\n"
                f"Source ancestor of downstream `{base}`: **{integrated}**.\n\n"
                f"Sync proposals: https://github.com/{repo}/pulls?q=is%3Apr+label%3Aupstream-sync\n"
                "If unintegrated, dispatch upstream-sync on the appropriate branch, or review a merge of this exact tag. "
                "A Dev merge alone is not proof that a release on another lineage is integrated.\n\n"
                "Owner must approve/integrate the source, allocate revision and monotonic MSI sequence, then dispatch "
                "windows-arm64-release from main. Alpha/beta/rc are prereleases even if GitHub labels them stable. "
                "This issue is a candidate record, not authorization to package or publish.\n\n"
                "Public gates: native license/corresponding-source closure, credential-at-rest decision, "
                "Surface install/upgrade/uninstall and profile preservation, signing/trust decision. "
                "Deleted/replaced releases do not delete this audit record; changed tags create a new identity.\n")
        issue = upsert_issue(api, repo, marker, f"ARM64 candidate: {release['tag_name']} ({identity['key']})", body)
        summary(issue["html_url"])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["sync", "monitor", "version"])
    parser.add_argument("--upstream", default="NuvioMedia/NuvioDesktop")
    parser.add_argument("--branch", default="Dev")
    parser.add_argument("--tag", default="")
    parser.add_argument("--revision", type=int, default=1)
    parser.add_argument("--sequence", type=int, default=1)
    args = parser.parse_args()
    if args.command == "version":
        print(json.dumps(version(args.tag, args.revision, args.sequence)))
        return
    repo = validate_repo(os.environ["GITHUB_REPOSITORY"])
    if repo != "Unitron07/Nuvio-ARM64":
        raise RuntimeError("Automation is restricted to the downstream repository")
    validate_repo(args.upstream)
    api = GitHub()
    if args.command == "sync":
        # Label is idempotent and never executes a fetched script.
        if api.request("GET", f"repos/{repo}/labels/upstream-sync", missing=True) is None:
            api.request("POST", f"repos/{repo}/labels", {"name": "upstream-sync", "color": "0366d6"})
        sync(api, repo, args.upstream, args.branch)
    else:
        monitor(api, repo, args.upstream, args.tag)


if __name__ == "__main__":
    main()
