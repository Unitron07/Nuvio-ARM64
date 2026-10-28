# Stage 4: touch usability and hardening

Stage 3 core functionality is validated and ready for merge in PR #4, subject to
final-head CI. The work below is deferred and does not block core integration.
No release or installer distribution is authorized by Stage 3 completion.

## Owner-reported Surface touch issues

Observed on Microsoft Surface Pro 11 / Windows ARM64, October 28, 2026. Exact
post-authentication-fix owner build SHA and issue frequencies are unavailable.
Do not assume these are ARM64-specific; compare upstream desktop and x64.

| Tracking issue | Observed / expected | Code sharing |
| --- | --- | --- |
| [#5 Home momentum](https://github.com/Unitron07/Nuvio-ARM64/issues/5) | Finger drag works, release stops immediately; add natural fling and deceleration | Shared NuvioScreen with downstream desktop drag fallback; fallback lacks inertia |
| [#6 Details scrolling](https://github.com/Unitron07/Nuvio-ARM64/issues/6) | Movie/TV page ignores finger drag; vertical swipes should scroll | Shared upstream commonMain MetaDetailsScreen LazyColumn outside that fallback |
| [#7 Settings scrolling](https://github.com/Unitron07/Nuvio-ARM64/issues/7) | Only narrow scrollbar dragging works; content swipes should scroll smoothly with momentum | Shared SettingsScreen and independent settings LazyColumns |
| [#8 Subtitle dismissal](https://github.com/Unitron07/Nuvio-ARM64/issues/8) | Escape required; provide visible close/back or reliable tap outside | Upstream/shared desktop player-ui controls.js in WebView2 |

Keep each fix scoped. Test detached-keyboard touch-only use, mouse/keyboard,
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
