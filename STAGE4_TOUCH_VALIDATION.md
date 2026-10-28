# Stage 4 technical plan and Surface validation

Status: implementation in draft; physical touchscreen acceptance is pending.
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
Raw drag deltas are retained from the existing desktop fallback; same-axis nested
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
