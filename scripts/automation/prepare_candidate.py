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


if __name__ == "__main__":
    if os.environ["GITHUB_REPOSITORY"] != "Unitron07/Nuvio-ARM64":
        raise RuntimeError("Downstream repository required")
    candidate = prepare(sys.argv[1], GitHub())
    Path("candidate.json").write_text(json.dumps(candidate, indent=2), encoding="utf-8")
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
        for key in ["downstream_sha", "version", "package_version"]:
            output.write(f"{key}={candidate[key]}\n")
