# Stage 6 — ARM64 release candidates

## Candidate discovery

`upstream-release-monitor.yml` checks every six hours (`43 */6 * * *`, UTC).
Manual dispatch accepts an exact upstream release tag. It paginates all public
releases, resolves each tag to its source commit, and records release ID, tag,
source, GitHub prerelease flag and update timestamp in deduplicated candidate
issues. Stable and preview releases are monitored; alpha/beta/rc suffixes remain
previews regardless of the GitHub flag. Drafts are ignored and upstream private
drafts are not visible to this token. A missing tag, rate limit, API/network or git
failure fails the job: it is never interpreted as no releases.

Changed tag targets, replaced IDs, and changed release metadata create a new
candidate identity. Deleted releases retain their audit issues and cannot pass
candidate preparation. Re-running identical data creates no duplicate issue.
The initial scan records historical releases as well; this is an intentional
bootstrap audit, not a request to build all of them. Candidate issues link sync
proposals and state whether the release commit is in main. If not, the owner
must dispatch sync or review a merge of that exact tag (release lineage can
differ from Dev). Automatic tag-specific sync creation is deferred; issues give
an explicit manual integration step. No build starts from monitoring alone.

## Version allocation

| Field | Example | Purpose |
|---|---|---|
| Upstream version | 0.1.33-alpha | Exact upstream basis |
| Downstream revision | 1, then 2 | Hotfixes against that basis |
| Human version / release tag | 0.1.33-alpha+arm64.1 | SemVer build metadata plus explicit fork revision comparison |
| MSI product version | 1.0.42 | Independent globally increasing owner-allocated sequence |
| Asset | Nuvio-ARM64-Windows-arm64-0.1.33-alpha+arm64.1.msi | Strict architecture and release binding |

MSI sequence must increase across ALL candidates, including channel changes,
previews and retries that change payload. It is 1..65535; stop and design a new
numeric epoch before exhaustion. Never reuse a sequence or human version for a
changed payload. Ledger checks prevent duplicate allocations; owner review must
also enforce increasing order. Human version orders upstream SemVer first,
numeric arm64 revision second. Build metadata alone would not work with a normal
SemVer comparator, so ARM64 uses its dedicated comparator. Changing channel does
not authorize a downgrade: a stable build can wait for a newer stable upstream
version. MSI numbers are not derived by stripping alpha. Dev images use revision
0 / MSI 1.0.0 and are not release assets.

## Controlled build procedure

1. Integrate and validate the exact upstream source. Do not auto-merge.
2. Add an owner-reviewed record to `.github/arm64-candidates.json` on main, via PR:

```json
{
  "alpha33-r1": {
    "release_id": 426017592,
    "upstream_tag": "0.1.33-alpha",
    "upstream_sha": "<40-character resolved tag commit>",
    "downstream_sha": "<40-character approved integrated source commit>",
    "revision": 1,
    "package_sequence": 1
  }
}
```

3. Configure the GitHub environment `arm64-internal-candidate` with required
   owner reviewers and a main-only deployment restriction. This repository change
   does not configure GitHub environment protection. Its presence alone is not
   proof an approval gate is configured. The reviewed main ledger and manual
   dispatch are additional gates.
4. Run `windows-arm64-release.yml` from main with that candidate key. Preparation
   re-fetches upstream tag, compares release ID/tag/SHA, checks both ancestry
   relationships, and pins the immutable downstream source. A branch that moves
   cannot change the selected build source. Build jobs have read-only tokens and
   no inherited secrets. Set public NUVIO_SUPABASE_* repository variables if an
   owner image needs configured authentication; never add service-role keys.
5. ARM64/x64 integration and QuickJS pass before native ARM64 image construction.
   Existing pinned hash checks, media lifecycle, HTTP/HTTPS, closure, image/JAR PE
   audit and packaged startup smoke remain required. The audited image is passed
   to native jpackage/WiX, not rebuilt by a second Compose packaging task. The
   resulting MSI must report Arm64 template, exact ProductName/Version/UpgradeCode
   and per-user scope. WiX must be on PATH. Native ARM64 jpackage capability is a
   construction gate; a failure must not be worked around using an x64 launcher.
6. Internal artifact `windows-arm64-internal-installer` includes MSI, SHA256SUMS,
   image file hash inventory, native provenance, notices, LICENSE and explicit
   unsigned/development status. `arm64-candidate-metadata` records the exact
   upstream and downstream relationship. The inventory is equivalent dependency
   evidence, not a complete license-cleared SPDX SBOM.
7. A separate write-token job executes no checked-out application code and creates
   a **metadata-only draft**. Existing published releases are never modified;
   existing drafts are retained. No installers are attached while release gates
   are open. There is no publish command or automatic-installer enable switch.

Native release packaging is experimental pending the first approved candidate
run. Normal PR CI supplies an app image, not an installer. Current ledger is empty
because Stage 6 source is not yet approved/integrated. No matching Stage 6 MSI is
claimed. For actual public shipping a follow-up must validate candidate bytes,
reattest source/run identity, collect full notices/sources and gate final binary
attachment/publication through a protected approval environment.

## Identity, profile and trust

ARM64 uses display/launcher/package/menu name `Nuvio-ARM64`, vendor
`Nuvio-ARM64 contributors`, per-user install, and upgrade UUID
`febc83be-f241-4a78-8f6c-10af5e358acc`. It is distinct from official Nuvio's UUID
and install name; MSI ProductCodes are generated per package. Validate actual
registry, shortcuts, install directory and uninstall behavior on hardware.
Production `%APPDATA%/Nuvio`, cache and SDK authentication identity are preserved;
there is NO silent profile migration. The fork and official app may share data:
installer identity separation does not establish safe simultaneous production
profile use. Use a separate Windows test user for all installation tests.

No Windows signing configuration was found or added. Packages are unsigned;
checksums do not establish publisher identity or SmartScreen trust. jpackage
Windows prerequisites are documented by [Oracle](https://docs.oracle.com/en/java/javase/21/jpackage/packaging-overview.html).

## Public release blockers (all open)

`.github/arm64-release-policy.json` records unresolved gates; it does not assert
clearance or provide an automatic publish path. Inherited desktop/mobile release
workflows are restricted to the official upstream repository to avoid a fork
publish bypass.

- libmpv archive pin `015f133399b3525021b6d937cf54ace2761d4f70224d5e62f5df24191b28f398`,
  builder recipe `1f8d2e76255abc48f0456d9be99b4cab9c6566e6`, mpv
  `e471496c56f0f49c133bd88b50aed62b845f1909`, FFmpeg
  `9a861e9687d30e657d59521817cad18405995983`: full static dependency graph,
  corresponding-source availability and complete redistribution notices remain
  unresolved. Existing build provenance/hashes and runtime closure are not legal
  clearance. Include QuickJS, Vulkan, WebView2, JDK, Skiko and all other shipped
  dependencies in the final review. Retain original upstream and vendor licenses.
- Desktop file-backed properties / SDK Windows Preferences storage require an
  explicit credential-at-rest security decision and remediation tracking.
  Stage 6 does not rewrite authentication or claim encrypted storage.
- Real Surface install, update, uninstall, reinstall, failure/rollback and profile
  preservation evidence is absent. Earlier development-image playback evidence
  cannot substitute for MSI validation.
- Signing/publisher and SmartScreen decision is unresolved. No signed/trusted
  claim is made. Any unsigned distribution needs a separate explicit owner decision.
- No integrity-verified automatic installation path has been implemented.

Public artifacts must exclude profiles, credentials, provider URLs, raw logs and
owner testing data. Development evidence and package inventory are not clearance
to distribute native binaries. Keep internal candidates access-controlled under
repository/environment policy until the gates close.
