# Stage 4 final validation ledger

**Core touchscreen UX validated on Surface Pro 11 — ready for merge, subject to final checks.**

PR [#9](https://github.com/Unitron07/Nuvio-ARM64/pull/9) continues the existing
`stage4-arm64-touchscreen-ux` branch. Stage 3 PR [#4](https://github.com/Unitron07/Nuvio-ARM64/pull/4)
is merged. The owner retains the merge decision; no merge or release is authorized.

## Owner-reported hardware acceptance

The owner's finalization report, received October 29, 2026, confirms the following
on Microsoft Surface Pro 11, Windows ARM64 / Snapdragon X. These are owner-reported
physical results, not CI results or tests performed by the finalization reviewer.
The exact hardware-tested commit, session settings and raw A/B recordings were not
supplied with this report; no exact tested SHA or independent reanalysis is inferred.
This later report supersedes the earlier pending core acceptance statements.

| Area | Reported result |
| --- | --- |
| Home and shelves | Vertical drag, horizontal shelves, natural momentum and short swipes work |
| Details and Settings | Movie/TV details scrolling in tested paths and Settings touch-drag work |
| Expanded shelves and Discover | Expanded shelf scrolling and genre dropdown scrolling work |
| Sources | Stream/source selector touch scrolling works |
| Player | Subtitle and other relevant menu dismissal by touch; native video/audio, pause, seek, fullscreen and normal navigation work |
| Navigation rail | Accidental expansion addressed; Back no longer triggers the unwanted sidebar behavior |
| Integration | Authentication, account/addon/catalog sync, Continue Watching/existing resume information, real debrid playback, native launch and shutdown work |

General touchscreen navigation works across the tested areas. The owner believes
scrolling works throughout the application but has not individually tested every
screen, menu or interaction. Do not interpret this as exhaustive coverage of folder
variants, every popup, every input device or newly generated progress sync after restart.

## Artwork cache and owner A/B observations

Vertical movement between real catalog shelves previously stuttered substantially;
horizontal movement within a shelf was consistently smooth. Recreated painters
repeated synchronous scaling despite decoded-image reuse, a credible contributor.
The retained shared cache reuses scaled static artwork across painter lifetimes.

| Vertical callback metric | Shared cache disabled | Shared cache enabled |
| --- | ---: | ---: |
| Samples | 1,621 | 3,402 (selected primary probe) |
| Intervals above 33.3 ms | 108 | 148 |
| Severe-interval proportion | 6.66% | 4.35% |
| Mean interval | approximately 12.54 ms | approximately 11.1 ms |
| Shared scaled-artwork reuse hits | 0 | 996 |

The rounded proportions imply approximately **34.7% relative reduction** in severe
frame-clock interval proportion, about 2.31 percentage points. The owner also felt
vertical scrolling was smoother. Counts and rounded percentages/means are retained
as supplied, rather than manufacturing more precise timings.

This is encouraging, not a perfectly controlled benchmark: duration and scrolling
activity differ. Compose callback intervals are not GPU-presented frames or exact
dropped-frame counts. This is not a proven 34.7% FPS improvement or elimination of
all stutter. Work counters cover whole selected windows, not only vertical intervals.
Measured reuse and promising hardware observations support preserving the cache.

## Final implementation and privacy review

- Shared desktop primary-Mouse fallback handles promoted Windows touch with slop,
  dominant-axis lock, nested pre/post scrolling, velocity tracking and framework
  fling decay. New press/disposal cancels its momentum. Actual touch/pen retains
  Compose handling. Home/shelves, details, Settings, sources, expanded grids and
  desktop dropdowns reuse this infrastructure with their own scroll state.
- Touch-accessible player close controls preserve Escape/backdrop handling and
  restore opener focus. Sidebar entry/focus/click alone does not expand; deliberate
  unpressed mouse movement plus dwell or an explicit localized chevron does.
  Route departure clears transient state. No finalization gesture-physics change.
- Shared scaled cache access/creation/eviction is synchronized. Weak source identity,
  generation and exact quantized target dimensions separate entries. Dead sources
  and superseded generations are pruned on misses; LRU eviction bounds retention
  to 64 MiB estimated N32 pixels and 256 entries. Oversize results bypass retention.
  Eviction releases references without closing bitmaps still used by painters.
- Coil's independent 256 MiB decoded-image cache, native rendering, request sizes,
  filtering and poster quality remain. Active painters, GPU/source memory and a
  transient miss allocation are outside the 64 MiB retained-cache budget. Static
  decoded sources remain read-only by contract; animated frames bypass this path.
- Opt-in v3 diagnostics retain only allowlisted aggregate numbers/static labels;
  no account, addon/catalog identifiers, URLs, headers or credentials. Disabled
  diagnostics have no frame loop/file writes. Owner-results ZIP collection remains
  allowlisted and excludes raw metrics/profiles/private logs. Generated benchmark
  JPEGs are synthetic fixtures; historical validation evidence is preserved.
- Review of Stage 4 changed source/scripts found no critical regression, new
  dependencies, private owner artifacts or temporary unconditional logging.
  Authentication/credential storage and production release workflows are unchanged.

## Automated evidence

Before finalization, head `e74b0e50c15777ac596d7c2741f42b0ae4320c0b` had passing
ARM64/x64 application integration, ARM64 development-image/native lifecycle,
HTTP/HTTPS/architecture/startup, ARM64/x64 QuickJS, PE validation and PR metadata
workflows. The previous reported local run was 120 tests in 25 suites with zero
failures/errors/skips; this is historical evidence, not a new local rerun.

Finalization local checks passed: loopback addon/protected-stream fixture,
synthetic tablet artwork/source fixture, weighted metrics/privacy analyzer, player
menu dismissal and shortcuts, ten PE architecture checks, JAR/image architecture
audit, owner-results ZIP privacy/rejection tests and updated owner-toolkit packaging.
The two fixture tests required a rerun with sandbox-blocked loopback access allowed.
A local Gradle attempt could not create its lock under the restricted user cache;
no new local Kotlin/Compose test pass is claimed. Final-head CI provides the clean
matching-architecture build/integration evidence.

Finalization also removes obsolete shelf pointer-handler imports, without changing
behavior, and updates documentation and development-check path filters so these
checks rerun for the final documentation commit. **The final SHA, actual rerun
results and workflow links are recorded in PR #9** after completion; readiness is
conditional until those final-head checks pass. Unit coverage includes gesture,
short flick/cancellation/nesting, real source list, expanded grid/dropdown selection,
sidebar policy, cache/pixels/generation/bounds, shelf restoration/keyed refresh,
diagnostics, addon/provider/authentication/storage and player-state regressions.
Native-image CI separately exercises real native lifecycle/HTTP/HTTPS/startup.

Synthetic menu/keyboard tests do not prove native popup Escape dismissal: an earlier
content-injected Escape attempt did not reach Compose's scene dismissal layer.
Enter selection is covered; exhaustive native Escape/outside dismissal, mouse,
trackpad, keyboard/focus, scrollbar, resizing/fullscreen and mixed-input behavior
remain broader regression work where not individually reported. No intentional
removal of these paths was found. Android/iOS drag/probe fallbacks remain no-ops;
Windows CI does not establish affected-platform compilation or physical acceptance.

## Tracking and deferred work

Issues [#5](https://github.com/Unitron07/Nuvio-ARM64/issues/5),
[#6](https://github.com/Unitron07/Nuvio-ARM64/issues/6),
[#7](https://github.com/Unitron07/Nuvio-ARM64/issues/7) and
[#8](https://github.com/Unitron07/Nuvio-ARM64/issues/8) have owner-reported core
resolution. They were previously closed by the unlabeled-issue bot as `not_planned`;
verification notes and corrected `completed` resolution follow final checks. Repository
issue search found no separate expanded-shelf, dropdown, source-selector, sidebar
or catalog-performance issues; those resolutions are recorded here and in PR #9.

Nonblocking follow-up: repeat controlled same-activity performance measurements,
profile remaining cold scaling/decode/GPU/GC work, exhaustive menus/input-device
and x64 UI checks, Android/iOS compilation, pen/display-scale/high-refresh/small-window
coverage and long-duration playback/memory stress. Historical collection-hover
expectations failed both with diagnostics enabled and disabled because unchanged
code prefetches on visibility; no fix or pass is claimed. Newly generated progress
sync, inherited desktop credential-at-rest migration, QuickJS warning audit and
installer/licensing/updater distribution gates remain separate Stage 3/later work.
