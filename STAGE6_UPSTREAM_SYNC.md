# Stage 6 — upstream synchronization

## Inspection and implementation plan

Inspected fork main `4cfa5499` (merged Stage 5 PR #10) and upstream Dev
`3e876f699e59e38b66ccc738331fa8071cbf29cc` on 2026-10-30. Upstream default is
`Dev`; the other observed active branch is `feat/desktoptorengine`. No release
branch was observed. Fork default is `main`. Their common ancestor is
`5e18596aa919a6a4e7b6cdee7f90dfaabbf71b00`; main has 75 unique commits, Dev 85.
Upstream release `0.1.33-alpha`, release ID `426017592`, reports target commit
`5f99fb4dba1ee43258977abacde7703a27e941fa`; release automation always resolves the
actual tag commit rather than trusting target_commitish. Upstream labels this
alpha non-prerelease: we must inspect semantic suffixes as well as GitHub flags.

Original desktop packaging is Compose/jpackage, display name Nuvio, MSI upgrade
UUID `395990ee-9b8a-3548-922c-e7a23a495b8d`, architecture-based artifact names,
no observed Windows signing configuration. Existing Windows workflows cover
QuickJS on ARM64/x64, native architecture checks, Stage 3 integration on both
architectures, and an ARM64 development image with closure/startup checks.
The official desktop release workflow builds x64 Windows packages, not this port.

Plan: A) merge proposals and trusted CI dispatch; B) immutable release candidate
records; C) fork identity and internal MSI construction; D) architecture-specific
informational updater; E) reviewed ledger + pinned internal build + metadata-only
draft. C and E remain experimental until native CI and owner installer evidence.
Public publication and automatic installation are deliberately unimplemented.

## Operation

`upstream-sync.yml` checks every six hours (`17 */6 * * *`, UTC), or via Actions →
Upstream synchronization proposals → Run workflow → main. Inputs default to
`NuvioMedia/NuvioDesktop`, `Dev`. Only the downstream repository and main execute
the maintenance job. Schedules begin only after owner merges the Stage 6 PR;
this implementation does not merge it. Configure repository Actions permissions
to allow GITHUB_TOKEN to create pull requests. No PAT is needed.

A merge preserves provenance and downstream commits. Cherry-picking loses merge
ancestry and requires maintaining a separate integration ledger; repeated rebases
would rewrite shared history. `merge-base(main,candidate)` is the last shared
integrated ancestor, and ancestry is the integration truth. No main reset,
force-push, blanket upstream-preference conflict resolution, or auto-merge exists.

Each upstream SHA has an immutable `automation/upstream-<full SHA>` branch and at
most one PR. The clean merge is based on the current main snapshot; if main moves
before push the run fails for retry. An existing candidate PR is reused without
rewriting its branch. Once its validation-dispatch marker exists, unchanged runs
only print its link. Closed candidates require an owner decision. A later upstream
revision gets a new proposal rather than changing already-reviewed source; review
and close superseded drafts explicitly. Human edits to a bot branch are not
rewritten. A branch left by an interrupted push/PR API transaction is never
force-pushed: inspect it and recover the PR manually, or remove only that bot
branch after review. Re-running after API failure is safe for main.

Conflicts abort the merge and create/update a deduplicated issue containing exact
SHAs, commit list, diff statistics, and conflicting paths. There is no invalid
merge commit. For resolution, create a separate branch from current main, merge
the reported upstream SHA, resolve each conflict while preserving the ARM64
implementation, then run the complete CI and Surface checklist before merging.
Do not squash a sync merge: retain the upstream parent ancestry. A squash merge
would cause future runs to rediscover the same upstream commits.

The PR links upstream versions (a discovery list, not proof of source inclusion),
compatibility risks and CI. `upstream-validation.yml` is explicitly dispatched at
main with the immutable PR head. It calls existing reusable validation workflows
without inherited secrets and with read-only tokens, credential-free checkout,
and no write credentials. The write job only runs trusted main automation and git
operations with hooks disabled; no upstream scripts execute there. Deployment
values in the image workflow use public repository variables, not secrets.
Concurrent maintenance runs are serialized; pushes use ordinary fast-forward
checks. Failed validation can be manually rerun for the exact head; never treat a
workflow dispatch link as a test pass. GitHub bot push events alone are not CI
orchestration; see [GitHub dispatch documentation](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows).

## Contribution and licensing

Keep LICENSE (GPLv3), upstream attribution and vendored notices. Upstream
CONTRIBUTING restricts major changes to approved feature requests. The fork owner
explicitly requested this downstream milestone; no upstream PR is authorized or
created. The Stage 6 draft links a tracking issue recording that owner request.
No new third-party application dependency is introduced by this automation.

## Limits

Mocked automation tests do not demonstrate live scheduled execution. Bot PR
creation, dispatch permissions, conflicts against future revisions, and CI
capacity must be verified after integration. The scheduled workflow never
executes a candidate application's code with write tokens. Existing trusted
main workflow definitions orchestrate read-only builds of untrusted candidate
code. Review workflow changes in each sync PR before integration.

## CI checkout correction

Credential-free checkout exposed an inherited orphan gitlink: libass-android
`c10b71ab8b4d90796d4a795f775d337c29198ad0` had no .gitmodules mapping. Restored
its verified source URL peerless2012/libass-android and the second orphan
`vendor/quickjs-kt` mapping to dokar3/quickjs-kt. Neither gitlink commit is changed,
and mobile dependencies are not initialized. This permits checkout auth cleanup
without retaining credentials. Stage 6 Windows actions are pinned to verified
checkout/setup-java/upload-artifact commit SHAs.
