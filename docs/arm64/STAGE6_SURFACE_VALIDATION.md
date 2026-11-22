# Stage 6 — Surface Pro 11 owner validation

Status: checklist, not completed acceptance. Use only artifacts from the exact
Stage 6 PR head and a successful run; never substitute Stage 5 binaries. The PR
must stay draft, unmerged, and no public release is authorized by this procedure.

## Development image check (available through normal PR CI)

1. From the Stage 6 PR Checks, open Windows ARM64 development image and download
   `windows-arm64-development-evidence`. From Stage 3 integration's **ARM64** job,
   download `stage3-integration-arm64`, containing `stage3-owner-tools.zip` and
   `stage5-owner-tools.zip`. Confirm all refer to the final PR head SHA.
2. Extract into a fresh test folder; locate the image directory containing
   `Nuvio-ARM64.exe` and `STAGE3_IMAGE.json`. Verify `sourceCommit` matches the PR
   head. Keep each build and RunDirectory separate.
3. Extract packaged Stage 3 tools (including `test-preferences.jar`). In PowerShell
   run the existing isolated launcher, substituting your actual extracted paths:

```powershell
& 'C:\NuvioStage6\owner-tools\Start-OwnerTest.ps1' `
  -ImageDirectory 'C:\NuvioStage6\image\Nuvio-ARM64' `
  -RunDirectory 'C:\NuvioStage6\runs\head-01' -NativeSmoke
# A second NEW run, without -NativeSmoke, opens the application:
& 'C:\NuvioStage6\owner-tools\Start-OwnerTest.ps1' `
  -ImageDirectory 'C:\NuvioStage6\image\Nuvio-ARM64' `
  -RunDirectory 'C:\NuvioStage6\runs\head-02' -PlaybackDiagnostics -PerformanceMetrics
```

The wrapper isolates file profiles AND SDK Java Preferences. APPDATA overrides
alone are insufficient. Do not launch the bare EXE against your normal account
profile for these tests. The tools verify image identity/hash. Enter test account
credentials privately; never upload raw logs, tokens, account email or stream URLs.

4. Verify native launch/close, account authentication, account/addon sync, existing
   Continue Watching, HTTP/HTTPS and source headers, addon/debrid video/audio,
   seek/fullscreen/controls and repeated cleanup. Verify D3D11VA evidence and
   software fallback independently; a 'hwdec requested' line is not decoder proof.
5. Repeat Stage 4 gestures: vertical/horizontal/expanded shelves, Discover genres,
   streams, player menu dismissal and sidebar; confirm bounded 64 MiB scaled-art
   cache and no new stalls. Native torrent/P2P remains guarded/unsupported.
6. Settings → Updates: confirm native Windows ARM64 queries
   `api.github.com/repos/Unitron07/Nuvio-ARM64/releases?per_page=100`, displays a
   safe no-update state when no compatible published asset exists, and never
   offers an upstream x64 MSI. Stable and Beta must remain selectable. A real
   positive discovery test waits for an approved public release; use unit tests
   for positive metadata now. No public release is created for testing.

## MSI validation (pending approved internal candidate)

Do not start until native candidate CI passes and the owner grants access to its
internal MSI. Match `DEPENDENCY-INVENTORY.json`, `candidate.json`, checksums,
workflow source/run and ledger key. The artifact is unsigned; no public binary
is authorized while native source/licensing gates are open.

1. Use a **separate disposable Windows user account** on Surface, without your
   normal Nuvio profile/credentials. Preserve this test user's profile between
   upgrade runs. Do not use your production profile or test arbitrary downgrade
   commands. Take a local backup of the test profile only; never upload it.
2. Verify the MSI hash against SHA256SUMS, inspect its signer (currently unsigned),
   run it interactively and record scope, directory, publisher/name and shortcuts.
   Expect per-user Nuvio-ARM64, separate from official Nuvio. Record Windows
   installed-apps and registry/uninstall identity without private data.
3. Launch in the test account; repeat checks 4–6 above. Close cleanly.
4. Prepare a **second separately approved candidate** with the same UpgradeCode,
   newer human revision and larger MSI sequence. Install normally. Confirm one
   fork uninstall entry, new version, native startup and retained test data.
   This is a manual installer upgrade; in-app auto-install must stay disabled.
5. Uninstall the fork only. Confirm official Nuvio remains installed where tested
   side by side and test profile data remains; reinstall and verify restoration.
   Installer identity separation does not guarantee profile isolation, so do not
   run upstream and fork simultaneously with the same test data.
6. Failure/rollback: cancel the installer before committing changes, check the old
   app still works; simulate other failures only in this disposable test account.
   If an upgrade corrupts test data, close all Nuvio processes, uninstall the fork,
   reinstall the previously approved test MSI and restore the test-only backup.
   If Windows refuses a numeric downgrade, uninstall first; never force an MSI
   downgrade over a live production profile. Retain both matching installers.

Record build SHA, Windows build, test-user isolation, candidate versions/MSI
sequences, each pass/fail and sanitized symptoms. Do not infer installer success
from CI construction. No owner acceptance is recorded until the owner reports it.

## Matching package status

Normal PR CI builds image and owner tools from the exact source, not an MSI.
Installer construction and metadata-only draft automation require the approved
ledger workflow on main. The ledger starts empty. Therefore no install/upgrade
acceptance or ready-to-run Stage 6 MSI is claimed by this document.
