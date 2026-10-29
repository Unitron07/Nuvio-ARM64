# Stage 4 technical plan and Surface validation

Final cleanup: [expanded grids, Discover genre popup and sidebar intent](STAGE4_FINAL_TOUCH_CLEANUP.md).
The latest owner reports improved real-catalog vertical scrolling and successful
prior touch/playback fixes. Only the three cleanup items are newly pending;
older per-revision pending checklists below are historical, not a rejection of
the owner's later observations. Exact tested SHA was not supplied.

Latest: [vertical shelf re-entry optimization and direction-specific retest](STAGE4_VERTICAL_SCROLLING.md).

Current performance work: [real-catalog investigation and v2 diagnostic procedure](STAGE4_PERFORMANCE.md).
The v2 procedure supersedes the older v1 probe instructions below. Owner now
confirms short flicks and source dragging; controlled real-catalog performance
attribution remains pending. No rendering speedup is claimed.

Status: implementation in draft; physical touchscreen acceptance is pending.

## Surface follow-up report and focused retest

Owner reports successful sign-in, addon/catalog sync, real debrid/native video and
audio, touch menu dismissal, most navigation, Settings dragging, smooth horizontal
shelves and basic vertical Home scrolling. Exact tested SHA was not supplied.
The reported mpv -13 case was an unavailable third-party addon; subsequent real
debrid playback succeeded. No media/authentication/provider fix is warranted.
All NEW follow-up retest rows below remain pending, rather than overriding those
earlier owner observations.

Confirmed source gap: the tablet/desktop source overlays both call StreamList,
whose independent LazyColumn lacked the Stage 4 wrapper. It now uses that shared
wrapper, preserving list state/keys, filters, refresh callbacks and scrollbar.
The desktop recognizer also discarded cumulative pre-slop movement and applied
only the last event at recognition. It now consumes only cumulative movement
beyond slop; duplicate-timestamp move/up samples no longer distort velocity fitting.
These are demonstrated code defects; their contribution to the owner's subjective
short-flick behavior requires retesting. Framework velocity fitting/decay remains.

Performance inspection: real posters use NuvioAsyncImage and Coil with a 256 MiB
desktop memory cache; poster request models and duplicate-safe lazy keys/content
types are remembered. Windows high-quality requests can decode up to 1536px;
ScaledBitmapPainter allocates/scales a bitmap synchronously on first draw or size
cache miss, then caches it per painter. Real cards also add depth, text and overlays.
The prior text-only fixture omits these image paths. This identifies plausible
costs, NOT a measured bottleneck. Network/decode, UI composition, GPU/upload,
draw-time scaling and GC have not been apportioned. No visual-quality reduction
or speculative rendering optimization is claimed; no before/after performance
numbers are available yet.

Run `fixture.mjs --tablet true --artwork true --media C:/NuvioTests/authorized.mp4`
for network-loaded 480x720 JPEG posters, normal poster cards and metadata; omit
`--artwork true` for the text control. Tablet mode now supplies 80 selectable
protected local sources. Images are generated benchmark artwork, not real catalog
content; compare the owner's same real catalog separately.

Optional aggregate probe (Home/shared NuvioScreen only), before launching:

```powershell
$env:NUVIO_SCROLL_METRICS_FILE='C:/NuvioTests/artwork-cold-frames.jsonl'
# Launch with the normal isolated Start-OwnerTest -Resume procedure below.
# Close normally when finished, then clear the opt-in variable:
Remove-Item Env:NUVIO_SCROLL_METRICS_FILE
```

Use a new absolute metrics filename per run. The disabled probe has no frame loop
or file I/O. Enabled sampling awaits Compose frame-clock callbacks; every 300
intervals it writes only frame count, total/max nanoseconds and counts above
16.67/33.33ms, on Dispatchers.IO. No identifiers, timestamps, URLs or account data
are recorded. These are callback pacing proxies, NOT GPU-presented frame counts,
actual dropped frames or measured input latency. Sampling itself can affect pacing.
It does not profile decode/network/GC; use a local JVM profiler if needed, keeping
raw profiles private because they may contain URLs. The owner ZIP collector does
not automatically include metrics or arbitrary profiles.

Compare 30 seconds of the same gesture sequence, viewport, scaling, refresh rate,
window size and power mode: text fixture, cold artwork, warm artwork, then the
same real catalog cold/warm. Record build SHA and those settings separately in the
sanitized checklist. Keep probes enabled equally for comparisons; discard partial
windows/navigation transitions and compare average (`totalNs / frames`), maximum
and over-budget proportions. Do not compare old non-instrumented builds as if
they supplied metrics. A future measured rendering optimization can use these same
conditions for before/after results. Preserve original image quality throughout.

- [ ] Synthetic Home short/long flicks, stop on new press, top/bottom boundaries.
- [ ] Artwork fixture and identical real catalog cold/warm pacing comparisons.
- [ ] Horizontal shelves, movie/TV details, Settings and nested controls.
- [ ] 80-source selector dragging/momentum, card tap versus drag, filters/refresh.
- [ ] Subtitle/audio dismissal, real debrid playback and mouse/keyboard/trackpad.

Automated additions exercise short flick continuation/interruption and the actual
StreamList with 80 real StreamCard composables (drag must not select; tap must).
Fixture tests verify JPEG loading and source count. These are not physical tests
or proof that real-artwork scrolling is faster. Commit-specific results and
matching artifact links are recorded in draft PR #9.
Baseline: merged Stage 3 PR #4, main e6b05536. Issues: #5, #6, #7, #8.

## Root causes and implementation

Home and shelves used delta-only pointer handlers: no release velocity or fling.
The details root and independently constructed Settings lists lacked the desktop
fallback. Windows AWT can promote finger input to mouse; real Compose touch
already has framework scrolling. These are desktop input paths, not ARM64 code.

The shared axis helper handles only primary Mouse pointers, uses touch slop and
dominant-axis locking, leaves actual touch/pen to Compose, and uses Compose
VelocityTracker and ScrollableDefaults fling decay. A new gesture cancels its
momentum job; disposal cancels the pointer coroutine. Opposite-axis gestures yield.
No wheel synthesis, polling, new physics engine or global window interception.
The NuvioTouchLazyColumn wrapper gives Settings lists the same fallback, retaining
their states, arrangements and scrollbars. Details opts in with its existing state.
Drag deltas use nested pre/post dispatch and child-first event handling; same-axis
handoff, hero stretch/search reveal and child text/slider arbitration need focused
hardware testing before acceptance. Mouse primary page dragging remains supported.

Player menus are HTML/CSS/JS hosted in WebView2; content screens are Compose.
Inspection found existing backdrop click and Escape dismissal, but track rails
lacked visible close buttons. Subtitle/audio/speed rails now have 44px close
targets; close restores opener focus and modal pointerdown cannot reach root
playback hold/drag handlers. No native player lifecycle, authentication, provider,
WebView2 host routing or libmpv embedding changes are made.

## Automated evidence and limits

Compose tests cover promoted-primary drag versus taps, secondary button rejection,
horizontal shelf versus vertical parent, and continued motion after release.
Node executes production modal handlers with synthetic elements and checks close,
backdrop, inside-panel retention and propagation isolation. This is not a browser
or physical touch test. Existing player shortcuts and Stage 3 integration suites
remain enabled; integration CI also runs the gesture class on ARM64 and x64.
Final run results and commit-specific links belong in the draft PR.

## Matching artifacts

Download `windows-arm64-development-evidence` and `stage3-integration-arm64` from
passing runs on the **same PR head SHA**. The latter includes
`stage3-owner-tools.zip`, this guide and the Stage 3 isolation/privacy scripts.
Verify STAGE3_IMAGE.json sourceCommit and architecture. These are development
images, not installers. Do not use upstream auto-update during testing.

Start the fixture with Node (your own authorized seekable media):

```powershell
node ./scripts/windows/stage3/fixture.mjs --tablet true --media C:/NuvioTests/authorized.mp4 --port 19876
./scripts/windows/stage3/Start-OwnerTest.ps1 -ImageDirectory C:/NuvioTests/Nuvio -RunDirectory C:/NuvioTests/stage4-run -NativeSmoke
./scripts/windows/stage3/Start-OwnerTest.ps1 -ImageDirectory C:/NuvioTests/Nuvio -RunDirectory C:/NuvioTests/stage4-run -Resume
```

Continue Without Account, create a synthetic profile and install
`http://127.0.0.1:19876/manifest.json`. Tablet mode supplies eight shelves, forty
titles per catalog, long descriptions and four seasons with twelve episodes each.
The default Stage 3 fixture is unchanged. All fixture streams use the supplied
local media; no account or provider credentials are required.

## Owner checklist — all pending physical validation

- [ ] Home vertical/horizontal finger drag, release momentum and smooth stop.
- [ ] Tap stops momentum; no oscillation at top/bottom; no unintended card clicks.
- [ ] Movie/TV description, seasons, episodes and nested shelf scrolling.
- [ ] Settings long pages/dialogs; toggles, sliders, dropdowns and text selection.
- [ ] Subtitle/audio/speed close button, tap outside, inside-panel scrolling;
      Escape still works and no touch toggles playback behind the menu.
- [ ] Seek/volume, show/hide controls, pause, fullscreen and return navigation.
- [ ] Mouse click/wheel, trackpad, keyboard shortcuts/focus and scrollbar dragging.
- [ ] Attached/detached keyboard, pen where available, 150/200% scaling, resize,
      maximize and high-refresh display responsiveness.
- [ ] Existing native playback, account/addon sync and real debrid regression,
      using owner credentials privately without fixture logging.

Record SHA/architecture, input device, scaling, expected/actual behavior and
pass/fail/pending per row in a local `stage4-results.md`. Do not put account data,
URLs, credentials or raw logs in that file. Keep Stage 3 results.json criteria
separate; they are not Stage 4 touch passes. Review and share only your sanitized
Stage 4 checklist plus the existing whitelisted owner-results ZIP. The collector
excludes arbitrary files, profiles and private logs; do not enable FixtureLogs
for account tests. No physical acceptance boxes are checked by synthetic tests.

## Targeted audit / remaining validation

Home/profile/search/tab navigation use shared Compose controls; no broad click or
window message remapping is introduced. Settings modal lists use the shared list
wrapper; independent verticalScroll/grid/dialog paths elsewhere remain an audit
follow-up if hardware reveals gaps. Native WebView2 pointer capture, pen routing,
focus after fullscreen, scaling, high refresh, performance and input switching
remain hardware checks. No measured smoothness/CPU claim is made. Keep this PR
draft until owner touchscreen validation and any discovered blockers are resolved.
