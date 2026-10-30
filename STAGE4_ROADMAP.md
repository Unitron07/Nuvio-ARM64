# Stage 4: touch usability and hardening

Stage 4 PR #9 is now merged. The deferred decoder/performance/stability work is
tracked in [Stage 5](STAGE5_ARM64_PERFORMANCE.md), with
[owner instructions](STAGE5_SURFACE_VALIDATION.md). The historical Stage 4 readiness
and acceptance evidence below is preserved.

**Core touchscreen UX validated on Surface Pro 11 — ready for merge, subject to final checks.**

The owner confirms Home/shelves, natural short swipes/momentum, tested details,
Settings, expanded shelves, Discover genres, sources, player menus and repaired
sidebar/Back behavior. Account/addon sync and native debrid playback remain working.
See [final evidence, cache A/B results and limits](STAGE4_FINAL_VALIDATION.md).
Stage 3 PR #4 is merged. PR #9 is prepared for review after final checks; no merge,
release or installer publication is authorized. Core implementation is complete;
future work below is hardening, not an open rejection of the reported fixes.

## Original Surface touch issues — resolved in tested paths

Observed on Microsoft Surface Pro 11 / Windows ARM64, October 28, 2026. Exact
post-authentication-fix owner build SHA and issue frequencies are unavailable.
Do not assume these are ARM64-specific; compare upstream desktop and x64.

| Tracking issue | Observed / expected | Code sharing |
| --- | --- | --- |
| [#5 Home momentum](https://github.com/Unitron07/Nuvio-ARM64/issues/5) | Finger drag works, release stops immediately; add natural fling and deceleration | Shared NuvioScreen with downstream desktop drag fallback; fallback lacks inertia |
| [#6 Details scrolling](https://github.com/Unitron07/Nuvio-ARM64/issues/6) | Movie/TV page ignores finger drag; vertical swipes should scroll | Shared upstream commonMain MetaDetailsScreen LazyColumn outside that fallback |
| [#7 Settings scrolling](https://github.com/Unitron07/Nuvio-ARM64/issues/7) | Only narrow scrollbar dragging works; content swipes should scroll smoothly with momentum | Shared SettingsScreen and independent settings LazyColumns |
| [#8 Subtitle dismissal](https://github.com/Unitron07/Nuvio-ARM64/issues/8) | Escape required; provide visible close/back or reliable tap outside | Upstream/shared desktop player-ui controls.js in WebView2 |

The table preserves the original defects and code context. The final owner report
confirms their core resolution; issue verification notes link PR #9. Further test
coverage should include detached-keyboard touch-only use, mouse/keyboard,
horizontal shelves, nested scrolling/child controls, cancellation and focus;
collect before/after evidence. Shared code does not prove the upstream bug
without hardware reproduction. Do not implement this overhaul in PR #4.

## Deferred validation and security

- Ten consecutive playback sessions and measured long-duration/memory stability.
- Newly generated progress synchronization after restart (existing Continue
  Watching/resume data is already owner-confirmed).
- Secure credential storage/migration and cloud-export review; current plain
  desktop properties are inherited on ARM64 and x64.
- Exhaustive x64 UI regression, additional HTTPS/provider/expired-session/logout
  cases, subtitles and hardware-decoder/performance validation.
- Existing QuickJS JNI unchecked-exception warning audit.

## Later distribution gates

Complete native licensing/corresponding-source closure, ARM64 MSI validation and
fork-specific installer/updater identity before distribution. Existing x64
release behavior and the ARM64 P2P guard remain intact.
