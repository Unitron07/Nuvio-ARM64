"""Resolve an owner-reviewed ledger record; no mutable branch may select build code."""
import json
import os
from pathlib import Path
import re
import sys
import urllib.parse

from downstream import GitHub, ancestry, git, version


def prepare(candidate_id, api):
    ledger = json.loads(Path(".github/arm64-candidates.json").read_text(encoding="utf-8"))
    record = ledger[candidate_id]
    recipient = record.get("delivery_recipient", "")
    if not re.fullmatch(r"age1[0-9a-z]{58}", recipient):
        raise ValueError("Owner encryption recipient required; plaintext delivery forbidden")
    source = record["downstream_sha"]
    upstream = record["upstream_sha"]
    if not all(re.fullmatch("[0-9a-f]{40}", sha) for sha in [source, upstream]):
        raise ValueError("Full immutable source SHAs required")
    release = api.request("GET", f"repos/NuvioMedia/NuvioDesktop/releases/{int(record['release_id'])}")
    if release["draft"] or release["tag_name"] != record["upstream_tag"]:
        raise ValueError("Upstream release was replaced or is unavailable")
    git("fetch", "--no-tags", "origin", "main")
    if not ancestry(source, "origin/main"):
        raise ValueError("Candidate source is not integrated into downstream main")
    git("fetch", "--no-tags", "https://github.com/NuvioMedia/NuvioDesktop.git", "refs/tags/" + record["upstream_tag"])
    if git("rev-parse", "FETCH_HEAD^{commit}") != upstream or not ancestry(upstream, source):
        raise ValueError("Upstream tag moved or release source is unintegrated")
    sequence = record["package_sequence"]
    for key, other in ledger.items():
        if key != candidate_id and (other["package_sequence"] == sequence or
            (other["upstream_tag"], other["revision"]) == (record["upstream_tag"], record["revision"])):
            raise ValueError("MSI sequence or fork version already allocated")
    values = version(record["upstream_tag"], record["revision"], sequence)
    return record | values | {"prerelease": release["prerelease"] or "-" in record["upstream_tag"],
                              "upstream_url": release["html_url"]}


def assert_first_attempt(candidate_id, api, run_id, attempt):
    """A dispatched version is immutable; failures use a new reviewed revision."""
    if attempt != "1" or not run_id.isdigit():
        raise ValueError("Candidate reruns require a new revision and MSI sequence")
    current = api.request("GET", f"repos/Unitron07/Nuvio-ARM64/actions/runs/{run_id}")
    current_order = (current["created_at"], int(run_id))
    for page in range(1, 1001):
        response = api.request("GET", "repos/Unitron07/Nuvio-ARM64/actions/workflows/windows-arm64-release.yml/runs"
                               f"?event=workflow_dispatch&per_page=100&page={page}")
        runs = response["workflow_runs"]
        for run in runs:
            if (run["display_title"] == f"ARM64 candidate {candidate_id}" and
                (run["created_at"], run["id"]) < current_order):
                raise ValueError("Candidate version already dispatched; allocate a new revision/sequence instead of replacing bytes")
        if len(runs) < 100:
            return
    raise ValueError("Workflow history pagination limit exceeded")


if __name__ == "__main__":
    if os.environ["GITHUB_REPOSITORY"] != "Unitron07/Nuvio-ARM64":
        raise RuntimeError("Downstream repository required")
    api = GitHub()
    assert_first_attempt(sys.argv[1], api, os.environ["GITHUB_RUN_ID"], os.environ["GITHUB_RUN_ATTEMPT"])
    candidate = prepare(sys.argv[1], api)
    if api.request("GET", "repos/Unitron07/Nuvio-ARM64/releases/tags/" + urllib.parse.quote(candidate["version"], safe=""), missing=True):
        raise ValueError("Release version already exists; candidate bytes may not be replaced")
    Path("candidate.json").write_text(json.dumps(candidate, indent=2), encoding="utf-8")
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
        for key in ["downstream_sha", "version", "package_version", "delivery_recipient", "upstream_tag", "upstream_sha"]:
            output.write(f"{key}={candidate[key]}\n")
