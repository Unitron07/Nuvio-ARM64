# Stage 3: application integration on Windows ARM64

Stage 3 remains **in progress**. Implementation, automated evidence, native
runtime evidence and interactive owner validation are separate gates.
Baseline: merged `main` `bc0b30ba` (PR #3), inspected October 27, 2026.

## Inspection and implementation plan

- Preserve the pinned ARM64 QuickJS runtime, libmpv Lua-disable embedding preset,
  UI Automation initialization, normal destruction, P2P guard and x64 releases.
- Exercise existing HTTP addons (`AddonRepository`, transport and catalog/meta/
  stream parsers) separately from JavaScript plugins (`PluginRuntime`, QuickJS,
  fetch bridge and promise handling). HTTP addon manifests do not run in QuickJS.
- Use deterministic loopback fixtures for discovery, protected URLs, redirects,
  query strings, headers and controlled failures. Add a local plugin repository.
- Test the existing Real-Debrid authentication/API-key validation and resolver
  against an instance-scoped local endpoint; keep the production endpoint fixed
  in the default client. No account or provider request is needed by CI.
- Protect preference saves with temporary-file replacement and discard partially
  parsed properties after a malformed file. Exercise reload and recovery.
- Provide isolated owner launch/results tools and native/desktop CI evidence.
  Real service sign-in, rendering and navigation remain owner acceptance gates.

## Architecture and findings

The app is Kotlin/JVM + Compose on Windows. HTTP addons use the desktop OkHttp
transport; JavaScript scrapers use the shared full plugin runtime and QuickJS-KT.
Streams carry request headers in `behaviorHints.proxyHeaders.request`; player
runtime sanitization removes `Range` so libmpv controls seeking. NativePlayerController
passes source URL, header lines and resume position to the JNI bridge. The Windows
bridge initializes libmpv before loading the source. Debrid resolution uses the
existing provider APIs and stream model; Real-Debrid supports an API-key workflow,
not the Torbox/Premiumize device authorization workflow.

Two reproducible integration defects were found:

1. The Windows bridge escaped both commas and backslashes into mpv's string-list
   option. The pinned mpv parser only unescapes separators, so header values with
   a backslash changed. A protected native HTTP fixture rejected the old form
   (`extra=false`, other header/query checks true). The bridge now uses a typed
   `MPV_FORMAT_NODE_ARRAY`, checks the option result, and preserves literal values.
2. Native `SHGetKnownFolderPath` bypassed APPDATA/LOCALAPPDATA test isolation.
   `NUVIO_DESKTOP_TEST_DATA_DIR` now provides an explicit absolute test root for
   native WebView2/shader directories. Normal launches keep the known-folder path.

Preference writes previously truncated the active file. Saves now replace it from
a completed temporary file (atomic move where supported; replacement fallback
otherwise). This reduces interrupted-write exposure but is not a power-loss/fsync
guarantee or a multi-process concurrency solution. Failed properties parsing now
clears partial in-memory state. Reads do not rewrite corrupt files.

**Credential storage limitation:** inspection contradicts the assumption of an
existing secure desktop credential vault. `DebridSettingsStorage.desktop.kt`
stores provider API keys through `DesktopStorage.Store`, in profile-scoped plain
properties; cloud export can include those fields. This is inherited behavior on
both x64 and ARM64. This change does not migrate credentials or claim encryption.
Tests use synthetic values only. Owner test profiles and raw logs must remain
private. Secure credential storage needs a separately reviewed design.

Both architectures normally share the Windows Nuvio data identity; never run the
tests against an existing user profile. Native/JVM isolation is provided by the
owner launcher and the desktopTest fork environment. Cloud account synchronization
is a different persistence path and still needs a configured owner account.

## Automated tests

### Local Surface application evidence

On October 27, the passing `d25a19ab` CI image was downloaded and its artifact
SHA256 verified. On this Surface Pro 11, Windows ARM64 10.0.26300.0, packaged
JVM 21.0.11/aarch64:

- Native prerequisites pass locally, including five JNI mpv create/destroy cycles.
- Normal app launch, Continue Without Account, local profile creation, settings
  navigation and fixture addon installation pass through the actual UI.
- Home catalog, title metadata and three stream choices load from loopback.
- The protected HTTP redirect stream visibly renders through the actual addon →
  selection → JNI → libmpv path. The server records 206/range requests with all
  four header checks true, including literal comma/backslash values.
- K pause, native seek to 15 seconds, K resume and advancing playback pass.
  Player close restores the stream list. A second direct stream renders after
  a failed-stream session. Normal application close exits 0 with no new JVM crash
  report; a fresh launch restores the local profile and addon catalog.
- A controlled 503 exposed a terminal-error defect: the old bridge drained
  END_FILE errors without notifying Kotlin, leaving Buffering visible. The new
  bridge forwards only error reason/code events to the existing error UI;
  callbacks are bound to the active source so retired-player errors are ignored.
  The new regression passes; retesting the corrected UI requires its new image.

These are UI-automation observations on real hardware, not fabricated owner
results. Screenshots, safe fixture summaries and session manifests are delivered
with local evidence. Full HTTPS application playback, ten-session navigation,
audio/subtitle/hardware-decode behavior, real credentials and leak measurements
are still pending. No account, provider credential or real user profile was used.

Local October 27 evidence: native Microsoft ARM64 JDK 21.0.12.1+1 compiled the
desktop application/tests and passed **100 tests in 18 suites, zero failures,
errors or skips**. Ten tests are new Stage 3 cases; the selection also includes
existing plugin concurrency, teardown, debrid and playback-state regressions.
The fixture server self-test, owner-archive privacy test, PowerShell parser checks
and `git diff --check` pass. Real desktop HTTP/QuickJS requests pass a trusted
loopback HTTPS test; separate fresh JVMs restore addon/plugin configuration,
settings, progress and synthetic provider authentication. Native `MpvHttpSmoke`
passes three protected redirect/header/decode/pause/seek/resume/stop/destruction
cycles for each of HTTP and verified HTTPS, followed by DLL unload, using the
existing pinned ARM64 libmpv (SHA256
`f90cee7e3dbb0a12fbfa6b95af1fb2bb22dbfebf8422efc0324918ee75661850`).
This local environment has no MSVC/Windows SDK installation; the changed C++
bridge is compiled by native CI. Unit tests alone do not establish UI playback;
the separate Surface observations above establish the stated HTTP workflow.
Fresh-JVM probes use an explicit classpath file and Java argument
files to avoid classloader assumptions and Windows command-line length limits.
Native header failure was reproduced before the API fix.

With a native matching JDK 21, Node.js, the existing source-build prerequisites
and a matching QuickJS-KT 1.0.15 JNI DLL:

```powershell
./scripts/windows/stage3/run-tests.ps1 -QuickJsDll C:/deps/arm64/libquickjs.dll
# On Windows x64:
./scripts/windows/stage3/run-tests.ps1 -QuickJsDll C:/deps/x64/libquickjs.dll -ExpectedArch amd64
./scripts/windows/stage3/test-owner-package.ps1
```

The script compiles the actual desktop application and tests. It excludes native
player build tasks deliberately: controller lifecycle tests substitute native
create/dispose, while **QuickJS executes the real matching JNI runtime**. No fake
player result proves rendered playback or native player destruction. The existing
native-image workflow separately compiles the bridge, checks architecture and
dependency closure, builds an app image and runs packaged native prerequisites.

Coverage includes HTTP addon registration from persisted installation state,
catalog/meta/stream parsing, addon reload/error recovery, QuickJS initialization
errors, async fetch/promise errors/recovery, repeated engine disposal, mock
Real-Debrid API-key validation/resolution/401/503/cleanup/account switching,
header/query preservation, handoff of parsed stream URLs and resume positions to
mocked native creation, preference disk reload and corrupt file recovery.
Existing debrid, playback-state and controller teardown regressions are selected.

Reports: `composeApp/build/reports/tests/desktopTest/index.html` and
`composeApp/build/test-results/desktopTest/`. The new workflow runs these on
`windows-11-arm` and `windows-2022` with matching JDKs/QuickJS. It uploads
`stage3-integration-arm64` / `stage3-integration-x64`, including owner tools.
x64 results validate x64 behavior only. Empty `local.properties` allows compile/
mock tests but does not configure Nuvio's real account and catalog services.

The extended `MpvHttpSmoke.java` uses generated video, a protected same-origin
redirect, encoded query parameters, authorization/user-agent/referer and comma/
backslash headers. It checks decode, pause state, seek/resume, EOF and stop through
three player creation/destruction cycles followed by DLL unload. Optional
PKCS12/CA arguments enable HTTPS with certificate verification, generated using
`Make-TestTls.ps1`; no system trust store is modified. It uses null
video/audio outputs, so it does not establish rendering, audio or UI navigation.
Run it with the same JNA/libmpv inputs as the native-image workflow. The ARM64
Lua-disable preset is retained without weakening normal destruction.

## Surface Pro 11 owner procedure

Use the Stage 3 branch's **new** passing development image, not an older Stage 2
image (the old bridge lacks test-data isolation and the header fix). Check the
source SHA and status in its development evidence artifact. The launcher requires
`STAGE3_IMAGE.json` and checks the launcher hash to reject old images lacking
native test-data isolation. No installer is needed.
Use PowerShell 7 and Node.js. Keep a native ARM64 JDK for source tests.

1. Download the image and owner tools artifacts for the same commit and extract
   them. The tools archive preserves `scripts/windows/stage3/` and its helpers.
   For the local fixture workflow, select **Continue Without Account**. If no
   profile exists, select **Manage Profiles → Add Profile**, enter a synthetic
   name and create the local profile. No backend key is needed for this path.
   Real account/cloud-service tests need your existing authorized configuration
   (`NUVIO_SUPABASE_URL`, `NUVIO_SUPABASE_ANON_KEY`, and where needed `TMDB_API_KEY`
   through the existing ignored local.properties/build mechanism). Keep those
   values out of results and the PR. Do not update the development image through
   the upstream update banner; updater identity remains a later validation gate.
2. In one shell, serve your own authorized, seekable video (30+ seconds is useful):

   ```powershell
   New-Item -ItemType Directory C:/NuvioTests/stage3-media -Force
   node ./scripts/windows/stage3/fixture.mjs --media C:/NuvioTests/stage3-media/authorized.mp4 --port 19876 --log C:/NuvioTests/fixture-requests.jsonl
   ```

   For a generated codec/transport fixture, use
   `python ./scripts/windows/make-smoke-avi.py C:/NuvioTests/stage3-media/fixture.avi`.
   It is only two seconds long and contains no audio; use longer authorized media
   for interaction tests. The server binds only to 127.0.0.1.
3. In a second shell, start native prerequisites, then resume the same isolated
   profile for the normal application. The launcher must sit directly inside
   `ImageDirectory`:

   ```powershell
   ./scripts/windows/stage3/Start-OwnerTest.ps1 -ImageDirectory C:/NuvioTests/Nuvio -RunDirectory C:/NuvioTests/stage3-run -NativeSmoke
   ./scripts/windows/stage3/Start-OwnerTest.ps1 -ImageDirectory C:/NuvioTests/Nuvio -RunDirectory C:/NuvioTests/stage3-run -Resume
   ```

   Each launch audits the image read-only, uses dedicated roaming/local/WebView2/
   native directories, captures private JVM crash reports, waits for
   normal close and records exit/crash/event summaries. It never kills an app.
   Console output is discarded by default because upstream addon logs can contain
   configured URLs. `-FixtureLogs` enables private stdout/stderr only for synthetic
   local fixture sessions; never enable it when testing real credentials. Native
   prerequisite mode captures only its account-free runtime diagnostics.
   Test sessions may intentionally display a Nuvio window. Architecture failures
   stop the launch; no foreign native resource is silently pruned by this tool.
4. Complete the application checklists below. In Addons install
   `http://127.0.0.1:19876/manifest.json`. Browse **Stage 3 Local**, open the fixture
   title and select its redirect/header stream. Try the direct stream and the
   controlled playback failure, then recover to a working stream. Also attempt
   installing `http://127.0.0.1:19876/malformed/manifest.json` and confirm a usable UI.
   In plugin settings install `http://127.0.0.1:19876/plugin.json`, enable its scraper
   and use its built-in test (or select a movie and inspect its fixture streams).
5. For HTTPS, use authorized HTTPS content, or provide a local test certificate
   valid for 127.0.0.1 and trusted by both the JVM and mpv runtime:
   `--cert C:/NuvioTests/test-cert.pem --key C:/NuvioTests/test-key.pem --https-port 19877`.
   This adds an HTTPS choice. Default HTTP mode does not validate TLS. Do not
   disable certificate verification or share private certificate keys.
6. Close normally and rerun the launch with `-Resume` to verify restart behavior.
   Keep the same ports/fixture server so saved addon URLs remain valid. Verify
   installed addons, plugin configuration, settings, progress and watch state in
   the actual UI. Do not copy an existing profile into the test directory.
7. If you own a supported provider account, authenticate inside this isolated
   profile, resolve/play authorized content, restart with `-Resume`, verify saved
   authentication, then logout/switch accounts where supported. Real-Debrid uses
   its existing API-key settings flow. Never send credentials to the fixture URL.
8. Edit `C:/NuvioTests/stage3-run/results.json`. Use `pass`, `fail`, `pending` or
   `blocked`, with evidence for each pass. Copy the fixture JSONL into that run
   directory as `fixture-requests.jsonl`, then package:

   ```powershell
   ./scripts/windows/stage3/Collect-OwnerResults.ps1 -RunDirectory C:/NuvioTests/stage3-run
   ```

   Attach `stage3-owner-results.zip` to the PR. Review the authored results first.
   The archive excludes profiles, raw application/provider logs and crash dumps;
   its fixture summary retains only status and boolean header/range/TLS checks.
   Private logs remain under `private/`. If a crash occurs, inspect the JVM crash
   file / Windows Application events locally and share only a redacted excerpt.
   Zero exit status alone is not evidence of UI playback, no leaks or provider
   success. Freezes require recording the last completed step and affected session;
   no force-killed process may be recorded as a clean close.

## Owner checklist and acceptance ledger

The ledger separates the named local Surface observations from the remaining
owner matrix. PR #3's historical Surface results are retained in the ARM64
document, not promoted into Stage 3 results.

| Acceptance | Status / required evidence |
| --- | --- |
| Native ARM64 application launches | Pass on Surface for d25a19ab: native smoke, normal UI and exit 0; error-fix image rerun pending |
| Test addon catalog and metadata | Pass automated tests and actual Surface local-addon UI |
| Addon stream discovery | Pass automated parser/QuickJS fetch and actual Surface stream list |
| URLs and headers reach libmpv | Pass actual Surface HTTP/JNI route and native HTTP/HTTPS fixtures; UI HTTPS remains pending |
| Authorized HTTP/HTTPS through actual UI | Pending owner, both protocols required |
| Navigation and repeated playback | Two successful Surface sessions plus controlled failure/return pass; full ten-session matrix pending |
| Settings and user state persist | Automated disk reload/corruption and fresh-JVM restart pass; full UI/cloud restart pending; secure credential storage gap above |
| Provider automated mocks | Pass locally: existing Real-Debrid resolver through loopback HTTP; no account required |
| Real provider validation documented | Procedure implemented; actual authentication/playback/restart/logout pending owner |
| Existing ARM64 native CI green | All five workflows pass at d25a19ab; terminal-error follow-up CI pending |
| Existing x64 compatibility preserved | x64 application integration and native JNI pass at d25a19ab; follow-up CI and full x64 UI pending |
| Owner tools/instructions | Implemented; archive privacy/manifest tests pass locally |
| Documentation reflects evidence | Implemented ledger; update with test reports and CI links |

Also record audible output, subtitles, fullscreen, touch input and hardware decode
as separate observations, not inferred passes. Check recurring JNI warnings,
native crashes, stuck player threads, UI restoration and shutdown. Track memory
over repeated sessions before claiming absence of leaks. Existing QuickJS-KT
unchecked-exception JNI warnings remain a known upstream limitation.

## Roadmap

Finish matching-architecture automated CI, then run the configured application
owner matrix and attach results. Investigate failures before completing Stage 3.
Resolve secure desktop credential storage separately with an approved migration
design. Continue codec/audio/subtitle/hardware-decoding validation afterward.
Installer/updater identity, complete native dependency licensing/source closure,
touch redesign and performance work remain later distribution gates. No release,
merge, updater replacement, new provider or destructive migration is included.
