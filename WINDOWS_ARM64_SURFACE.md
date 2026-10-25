# Windows ARM64 and Surface Pro 11

Status: native Windows ARM64 QuickJS JNI built and smoke-tested;
**not a working ARM64 application or installer yet**.
Baseline: [NuvioMedia/NuvioDesktop Dev](https://github.com/NuvioMedia/NuvioDesktop/tree/5e18596aa919a6a4e7b6cdee7f90dfaabbf71b00), commit `5e18596aa919a6a4e7b6cdee7f90dfaabbf71b00`.
Inspection: October 24–25, 2026.

## Stage 2: QuickJS JNI source build

Stage 2 starts at merged `main` commit
`f14296e7e2f38cef5acedc1831d927f0161029b0` (PR #1). The only other branch,
`arm64-surface-foundation`, is merged. Upstream `Dev` remains the recorded
`5e18596aa919a6a4e7b6cdee7f90dfaabbf71b00` baseline. Existing PE/touch work is
retained. Latest foundation PE and PR-template Actions runs passed; an older
PR-template failure was corrected before merging.

The new `scripts/windows/build-quickjs.ps1` builds the **QuickJS-KT JNI bridge**,
not just the QuickJS C API. It pins QuickJS-KT 1.0.15 to
`a081755bcdb18ee6afa7ffee25b8fa976f06d31f`, Bellard QuickJS to
`04be246001599f5995fa2f2d8c91a0f198d3f34c`, and c-vector to
`774773d4cb1e66dd736e90e7f482d4f22af464c6`. Dirty source trees are rejected.
The isolated CMake configuration includes the unchanged JNI and mapping sources.
One explicit engine-source patch is applied to a build copy: `JSClosureVar`'s
three-bit `closure_type` is made `unsigned int`. MSVC-mode Clang 22 reads enum
values 4–7 as negative in a signed three-bit field, reaching the closure dispatch
abort when a script references globals. Native tests cover `String(42)` and
captured lexical variables. The original source checkout stays clean; the
provenance manifest records the compiled engine hash and patch. The engine also
keeps its usual 64-bit BigInt limbs, with a small unsigned 128-bit division helper
for the Microsoft runtime; 22 independent quotient/remainder vectors test it.
`-fwrapv` matches the signed-overflow contract in Bellard's Makefile.
Bellard's GNU C extensions require Clang; use **clang-cl targeting the MSVC ABI**,
MSVC ARM64 libraries and Windows SDK rather than claiming plain `cl` support.
The private Win32 compatibility layer covers only the mutex, condition variable
and realtime clock functions used by these pinned sources. It preserves recursive
JNI locking and QuickJS Atomics; it is not a general pthread implementation.
The static C runtime avoids a separate winpthreads DLL and permits QuickJS-KT's
single-resource extraction/loading behavior. Notices, source pins, toolchain
versions, DLL SHA256 and JNI export listing accompany the output.

Prerequisites: Windows JDK 21, Visual Studio C++ tools for the target architecture,
Windows SDK, LLVM clang-cl, CMake, Ninja, Git and network access. With an ARM64
JDK in `JAVA_HOME`, from a fresh checkout on Surface Pro 11:

```powershell
./scripts/windows/build-quickjs.ps1 -Architecture arm64
./gradlew.bat -p scripts/windows/quickjs-smoke run `
  "-PquickjsDll=$pwd/build/windows-quickjs/arm64/libquickjs.dll" `
  -PexpectedArch=aarch64 --no-daemon --console=plain
```

This tests the published Kotlin artifact and actual `jni/windows_aarch64`
resource loader with `-Xcheck:jni`, including initialization, evaluation, Unicode
and embedded NUL, exceptions/recovery, synchronous callbacks, suspending callbacks,
promise jobs, Atomics and repeated disposal. A separate workflow runs ARM64 on
`windows-11-arm` and x64 regression on `windows-2022`; artifacts are JNI runtime
bundles, **not application images**. The original x64 release workflow is untouched.
Successful source compilation alone does not establish JNI ABI compatibility.

For application staging after this test passes, pass
`-Pnuvio.windows.quickjs.dll=<checkout>/build/windows-quickjs/arm64/libquickjs.dll`
to the existing app tasks, together with the libmpv and WebView2 properties below.

At initial implementation time the local host has an ARM64 JDK but no installed
MSVC/Windows SDK/LLVM C++ toolchain. Native CI execution and local DLL smoke results
are recorded below when available. libmpv's complete ARM64 runtime, the player
bridge, full application startup and HTTP/debrid playback remain separate gates.
No MSI or release is produced by this milestone.

### Stage 2 verified results

The [native CI run 40170624286](https://github.com/Unitron07/Nuvio-ARM64/actions/runs/40170624286)
passes on Windows ARM64 and Windows x64. Both build the actual JNI DLL, verify
all 29 declared JNI exports and matching PE architecture, and run the Win32
synchronization test plus all 22 division/remainder golden vectors. The complete
published-API JVM smoke test passes on both architectures, including global and
captured lexical references, BigInt, strings/NUL, callbacks, exception recovery,
coroutine/promise jobs, Atomics and three engine creation/disposal cycles.
The supplied ARM64 DLL imports only `KERNEL32.dll`; no private runtime DLL or
MSYS2 installation is needed for this QuickJS build.

The [final code/provenance CI run 40170698966](https://github.com/Unitron07/Nuvio-ARM64/actions/runs/40170698966)
also passes both architectures. The same ARM64 DLL passes the complete resource
loader/JVM suite locally on this Surface with Microsoft ARM64 JDK 21.0.12.1+1.
DLL SHA256: `ceef75214ac8ac3995acae17c86724ecc43ffed165c4fc7b6e7aa1181a3ef110`.
The CI bundles include `VALIDATION_STATUS.txt`; a retained artifact with failed
smoke status is diagnostic only. Source/build provenance belongs to code commit
`40d90e63ddc1e2136e30dd4fd7d552c9c38edd3a`.

`-Xcheck:jni` emits warnings about unchecked exceptions after
`CallStaticObjectMethod` in the pinned JNI bridge on both architectures. They do
not fail the smoke suite, but the JNI exception discipline still needs auditing;
these are not warning-free tests. This validates the QuickJS dependency rather
than all plugin services or the full application. The original x64 application
release pipeline and touch implementation are unchanged; full x64 app, provider,
playback and input regression runs were not repeated for this tooling milestone.

### libmpv candidate investigation (not accepted for staging)

The mpv.io-linked shinchiro builder publishes an actual ARM64 libmpv. The inspected
candidate is [20261021 mpv-dev-aarch64](https://github.com/shinchiro/mpv-winbuild-cmake/releases/tag/20261021),
archive `mpv-dev-aarch64-20261021-git-e471496c56.7z`, SHA256
`015f133399b3525021b6d937cf54ace2761d4f70224d5e62f5df24191b28f398`
(matches GitHub's published asset digest). Its builder workflow
[39518569492](https://github.com/shinchiro/mpv-winbuild-cmake/actions/runs/39518569492)
ran build recipe `1f8d2e76255abc48f0456d9be99b4cab9c6566e6`.
The matching source commits resolve to mpv
`e471496c56f0f49c133bd88b50aed62b845f1909` and FFmpeg
`9a861e9687d30e657d59521817cad18405995983`.

Observed on this Surface Pro 11 / Snapdragon X X1P64100, Windows build
26100.9711, Microsoft ARM64 JDK 21.0.12.1+1, JNA 5.19.1:

- PE machine is `0xAA64`. `mpv_create`, option setting, `mpv_initialize` and
  property queries execute in the ARM64 JVM. Reported versions match the pins.
- **`mpv_terminate_destroy` crashes the JVM with native exception `0xe24c4a02`.**
  Reproduced twice, including phase markers confirming the crash happens before
  destruction returns, before unloading the library. Root cause is not established.
- The development archive contains headers, an import library and one DLL; its
  import table includes `vulkan-1.dll`, an external dependency not supplied by that
  archive. This host has a Vulkan installation; load success here does not prove
  a self-contained distribution. Audit that closure and driver interaction.
- The candidate reports libass/libcurl support in its build configuration and
  statically preferred dependencies. Actual codecs, HTTP streaming, subtitles,
  D3D11/D3D11VA and cleanup still need validation. No playback was attempted after
  the initialization/cleanup failure.

Reproduce the isolated failure without provider credentials:

```powershell
# Download/extract the exact archive above and verify its hash first.
# Use the JNA 5.19.1 JAR already resolved by the application.
& "$env:JAVA_HOME/bin/java.exe" '-XX:ErrorFile=hs_err_pid%p.log' `
  -cp C:/deps/jna-5.19.1.jar scripts/windows/MpvProbe.java `
  C:/deps/mpv-arm64/libmpv-2.dll
```

Do not bundle this candidate on the strength of architecture alone. Resolve the
cleanup failure and native dependency closure, and retain all corresponding
source/build recipes and notices before distribution. A builder snapshot alone
does not freeze its moving git dependencies; any source rebuild must also pin
the complete recipe dependency graph. The QuickJS milestone can be reviewed and
used independently while this media runtime gate remains open.

## Scope

The requested ARM64 target uses a debrid service and HTTP media streams. Native
torrent playback is outside this target. Keep the existing Windows ARM64 P2P
unsupported-platform guard, and omit the x64 torrent engine from ARM64 packages.
The x64 build retains its existing engine and playback functionality.

## Stack and findings

| Component | Current implementation | ARM64 implication |
| --- | --- | --- |
| Application | Kotlin 2.4.10; Compose Multiplatform 1.13.0-beta01; Gradle 9.4.1 | Keep the shared UI and Kotlin/JVM desktop target. No rewrite is needed. |
| Window/UI renderer | Swing/AWT, Compose and Skiko | Use an ARM64 JVM and matching Skiko native libraries. Compose documents Windows ARM64 support. |
| Windows player | JNI C++17 `player_bridge.cpp`, libmpv, Direct3D 11 | Compile the bridge with MSVC ARM64 and supply an ARM64 libmpv DLL plus matching dependencies. |
| Player controls | Local HTML/CSS/JavaScript hosted in WebView2 | Use the SDK's `build/native/arm64` loader and the ARM64 WebView2 Runtime. Audit pointer interactions independently of Compose. |
| Plugins | QuickJS-KT 1.0.15 JNI | Maven has only the Windows x64 DLL. The Stage 2 source build now supplies a tested ARM64 JNI DLL through the existing `windows_aarch64` resource staging. Full plugin startup remains to be tested. |
| Debrid/networking | Kotlin services, Ktor, OkHttp | Retain existing provider integration, authentication, source selection and HTTP streaming. Test using the user's provider after a native build exists. |
| Other native pieces | JNA; generated/bundled runtime libraries | Audit resolved artifacts and extracted DLLs, including the JDK and Skiko, before shipping. |
| Torrent engine | Bundled x64 `nuvio_engine.dll`; architecture guard in `DesktopEngineLibrary` | Keep unsupported on Windows ARM64 per user scope. |
| Updates and packaging | Windows x64 release job; MSI via Compose/jpackage; updater points upstream | An ARM64 release pipeline and fork-specific installer/update identity remain to be implemented before distribution. |

Both checked-in Windows `libmpv-2.dll` and `nuvio_engine.dll` have PE machine
`0x8664` (AMD64). Their filenames do not imply ARM64 compatibility. The current
Windows release workflow also selects x64 WebView2 and emits x64 MSI artifacts.
An ARM64 JVM cannot load these x64 DLLs in-process. This makes a packaging-only
architecture switch insufficient. Emulation may contribute to lag, but no
performance comparison has yet established its share of the problem.

## Architecture

Retain the existing application architecture:

```text
ARM64 launcher + bundled ARM64 JVM
  Kotlin/Compose UI -> ARM64 Skiko
  plugin services -> ARM64 QuickJS JNI
  debrid services -> HTTP media URL
  JNI player bridge (ARM64) -> libmpv (ARM64) -> D3D11 / hardware decoder
                           -> WebView2 loader + ARM64 Runtime -> player controls
```

The C++ bridge already has architecture-neutral JNI/Win32 code. Its existing
MSVC and WebView2 architecture selection can be retained. Each DLL loaded into
the JVM must match its architecture; do not replace a native library with an
external x64 process without separately designing and testing that interface.

## Initial changes

- Separate bridge outputs and staged player runtimes by JVM architecture. Remove
  the unconditional "DLL exists" skip so Gradle can rebuild a changed bridge.
- Resolve architecture-specific libmpv runtimes first. Legacy bundled/MSYS2 x64
  fallbacks apply only to x64. Unknown Windows JVM architectures fail explicitly.
- Add `verifyWindowsArm64Inputs` and a PE validator. Check supplied libmpv,
  transitive runtime DLLs, QuickJS, WebView2 loader and discovered C++ runtime
  DLLs before building. Reject x64, ARM64EC, malformed binaries and LFS pointers.
  This validates machine type, not ABI, exports, signatures or codec behavior.
- Accept `-Pnuvio.windows.quickjs.dll=...` and place its ARM64 JNI resource on
  both development and packaged classpaths. Preserve the existing x64 resource.
- Keep the upstream torrent engine and architecture guard unchanged; package
  its DLL on x64 only.
- Add a desktop vertical drag fallback to the shared `NuvioScreen` list used by
  Home and other screens. AWT promotes touchscreen input to mouse events, so the
  fallback accepts primary mouse drags, locks vertically after touch slop, and
  yields horizontal drags to shelves. Actual Compose touch events retain the
  existing touch handler. Normal left-mouse page dragging becomes possible too.
- Add interaction regression tests and a Windows PE-validation CI job.

The initial fallback does not provide inertia for promoted mouse gestures,
multitouch, or coverage for independently constructed grids, lists and dialogs.
It uses the same raw-delta approach as upstream horizontal shelves; nested-scroll
effects and interruption of existing animations need a later coordinated handler.
It is an initial fix, not a claim of complete touchscreen support.

## Build preparation

Use an ARM64 JDK supported by Gradle/Compose on Windows ARM64, MSVC ARM64 tools,
Windows SDK, and the WebView2 SDK. The JDK running Gradle determines the native
target. Kotlin/Native host warnings concern disabled iOS targets; this desktop
target is Kotlin/JVM. Verify the actual resolved desktop dependency graph.

Supply a real ARM64 runtime directory containing `libmpv-2.dll` and its dependent
DLLs. Supply an ARM64 build of QuickJS-KT's JNI bridge from the matching 1.0.15
source, with its expected JNI exports; a generic QuickJS DLL is insufficient.
The upstream QuickJS build has only a Windows x64 recipe. The Stage 2 build above
adds the Microsoft-ABI ARM64 port and verifies exception/string/coroutine behavior.
The main remaining native runtime blocker is now libmpv cleanup and dependency
closure. Record exact source revisions and hashes for every accepted runtime.

Example, once these prerequisites exist:

```powershell
# local.properties must exist, even if empty for compile-only tests.
# Real playback needs the user's own service/application configuration.
.\gradlew.bat :composeApp:verifyWindowsArm64Inputs `
  "-Pnuvio.webview2.dir=C:/deps/Microsoft.Web.WebView2/1.0.4078.44" `
  "-Pnuvio.windows.libmpv.runtimeDir=C:/deps/mpv-arm64" `
  "-Pnuvio.windows.quickjs.dll=C:/deps/quickjs-arm64/libquickjs.dll"
```

Pass the same properties to `:composeApp:run`, `:composeApp:createDistributable`,
and eventually `:composeApp:packageReleaseMsi`. Validate ARM64 jpackage/WiX
compatibility and the generated MSI before treating packaging as supported.
The current release workflow remains x64; it has not been relabeled ARM64.

## Completion plan and acceptance gates

1. **Native dependencies:** QuickJS JNI is built and smoke-tested; obtain/rebuild ARM64
   libmpv with required codecs/subtitles and all dependencies. Use reproducible,
   pinned sources and preserve their licenses. Compile the existing player bridge
   with ARM64 MSVC and link ARM64 WebView2. Check exports and dependency loading.
2. **Debrid smoke build:** launch an ARM64 app image, verify JVM/Skiko/JNA/QuickJS
   initialization, then test provider sign-in, plugin execution, catalogs,
   resolved debrid streams, resume and watched-state persistence. Confirm no
   torrent-engine initialization is required for these routes.
3. **Complete input coverage:** apply a coordinated drag/scroll implementation
   to remaining vertical lists, grids and dialogs; add fling velocity, cancellation,
   child control arbitration, nested scrolling and boundary handling. Audit the
   WebView2 seek/volume controls, tap-to-show controls and pointer capture. Use a
   native Windows touch adapter only if promoted mouse events cannot meet the
   required interaction contract; avoid changing all mouse drags globally.
4. **Surface Pro 11 validation:** test portrait/landscape, 150–200% scaling,
   detached/attached keyboard, finger/pen/mouse/trackpad switching, vertical page
   swipes from poster cards, horizontal shelves, tap versus long press, sliders,
   dropdowns, settings and modal lists, virtual keyboard/focus, fullscreen,
   subtitles, PiP, sleep/resume and cancellation when a window loses focus.
   Capture before/after evidence. Test both x64 and ARM64 regression builds.
5. **Playback/performance:** compare identical media and settings with the x64
   build; measure startup, scrolling frame times, dropped frames, CPU and power.
   Confirm D3D11 hardware decoding rather than assume that ARM64 fixes all lag.
6. **Release:** add a separate ARM64 build job; validate every extracted PE DLL
   and launcher, actual ARM64 runtime launch, MSI install/uninstall/upgrade and
   architecture-safe updater selection. Give the fork its own update source and
   installer upgrade identity before distributing, to avoid replacing it with
   an upstream x64 update. Retain the existing x64 job and regression coverage.

## License and provenance

The original `LICENSE`, notices, vendored licenses and upstream Git history are
retained. This repository was initially empty and independent on GitHub; its
`main` now contains the upstream `Dev` history and merged foundation PR #1;
Stage 2 changes are on a separate branch.
It is not a GitHub fork-network member. This is an unofficial derivative.

Before distributing binaries, include the applicable notices and corresponding
source/build information for the app and native dependencies. The draft PR is
for this user's repository. An upstream submission would require following
upstream `CONTRIBUTING.md`, including approval and evidence where required.

## Sources

- [Upstream source and build](https://github.com/NuvioMedia/NuvioDesktop)
- [Existing touchscreen bug #143](https://github.com/NuvioMedia/NuvioDesktop/issues/143)
- [Existing Windows ARM64 request #79](https://github.com/NuvioMedia/NuvioDesktop/issues/79)
- [Compose platform support](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html)
- [Compose desktop pointer behavior](https://kotlinlang.org/docs/multiplatform/compose-platform-specifics.html)
- [QuickJS-KT source](https://github.com/dokar3/quickjs-kt)
- [WebView2 Runtime distribution](https://learn.microsoft.com/en-us/microsoft-edge/webview2/concepts/distribution)
- [mpv's Windows build references](https://mpv.io/installation/)

## Validation results

| Check | Result |
| --- | --- |
| PE validator regression suite | 10 checks passed locally and on Windows GitHub Actions |
| Original bundled media DLL inspection | Both libmpv and torrent engine confirmed AMD64 `0x8664` |
| Gradle configuration on ARM64 JDK 21 | Passed |
| Kotlin desktop application and test compilation | Passed with a 6 GiB compiler allowance; initial 2 GiB attempt exhausted memory |
| `DesktopVerticalDragScrollTest` | All four tests passed: vertical drag, tap activation, horizontal shelf drag, secondary-button rejection |
| Missing ARM64 prerequisites | Build refuses to select bundled x64 libmpv |
| PR metadata check and diff whitespace | Passed |
| Native ARM64 playback, full touchscreen use, x64 regressions and MSI | Still pending |

The focused Compose tests compile the application and execute only the new test
class, excluding unavailable native-player tasks. They do not validate ARM64
playback or packaging. Reproduction command, with an empty `local.properties`
for these input-only tests:

```powershell
.\gradlew.bat :composeApp:desktopTest `
  --tests com.nuvio.app.core.ui.DesktopVerticalDragScrollTest `
  -x verifyWindowsArm64Inputs -x buildWindowsPlayerBridge `
  -x prepareWindowsPlayerRuntime -x generateWindowsPlayerRuntimeIndex `
  "-Dorg.gradle.jvmargs=-Xmx6g" "-Pkotlin.compiler.execution.strategy=in-process" `
  --max-workers=2 --no-configuration-cache --no-daemon
```
