# Stage 5: performance, power and stability

Stage 5 remains in progress. No CPU/GPU/energy improvement, long-duration stability
or absence of leaks is claimed before measurements. The merged Stage 4 baseline is
`2f71f1c8a4585457427c540ec2de3cc260768d72`; Stage 4's owner cache observations remain
in STAGE4_FINAL_VALIDATION.md with their original uncontrolled-workload limitations.

## Implemented measurement support

Owner testing exposed an inherited isolation defect: Supabase's default Java
Preferences session backend can restore the normal Windows account even when
APPDATA/user.home point at a fresh profile. The new **owner-only** JVM preferences
factory bypasses the Windows registry, stores test sessions within profile/preferences
and preserves them across restart. Missing helper/invalid roots fail closed.
Normal application/authentication code remains unchanged. Synthetic fresh-JVM
write/read/remove/root-separation tests pass; a packaged Surface launch with the
helper shows the unauthenticated welcome screen and closes normally. Earlier toolkit
isolation claims did not cover this SDK session backend; use the updated toolkit.

- Opt-in actual-player decoder/timing/drop diagnostics, sanitized at the native
  boundary; unavailable numeric properties are null.
- Isolated owner launcher samples application process-tree CPU, working set, private bytes,
  handles and threads once per second; raw samples remain under private/.
- Collector emits per-launch source/architecture, crash count, native-player
  initialization/destruction markers, progressing hardware intervals and process
  metric count/mean/peak/first/last. Review the resulting JSON before sharing.
- Existing ARM64 HTTP native smoke supports configurable cycles, set to ten in CI,
  preserving protected redirects/headers, pause, seek, resume, EOF, failure and unload.
  HTTPS remains a separate three-cycle test. These use null output/software raw AVI,
  not application navigation/fullscreen or GPU validation.
- Existing real QuickJS tests, architecture/image audits, packaged native startup,
  desktop integration, auth/provider mocks, touch/cache tests remain required.

## Repeatable comparison

Use identical file hashes, app settings, drivers, Windows build, display mode and
workload. Record source SHA, ARM64 architecture and decoder actually observed.
Measure Stage 4 and Stage 5 in alternating order; warm up five minutes, measure
at least fifteen minutes, repeat three times. Compare default automatic versus
the existing software decoder preference on the same Stage 5 file. Preserve hardware
priority semantics; choose default/automatic to test graceful fallback.

Separate idle home (five minutes untouched), fixed catalog browsing route, 1080p,
and 4K. Report per-window process CPU mean/peak/sustained ranges, WS/private memory,
handles/threads, drop-counter deltas, A/V sync range, startup/seek wall-clock latency,
buffering and fullscreen observations. Collector metrics currently aggregate the
whole launch; use separate launches for each workload and exclude warm-up manually
from private raw samples for controlled comparisons. It does not yet compute latency,
GC, native allocation attribution or statistically comparable baseline deltas.

CPU percent is normalized to all logical processors and covers the discovered
launcher/JVM/WebView2 descendants. The packaged launcher has a lightweight parent;
sampling that PID alone undercounts the JVM. Tree discovery uses parent PIDs only,
never command lines. Check exported processScopes: unavailable discovery is explicitly
launcher-only. Exited/inaccessible workers and processes between samples can be missed.
Summed working sets double-count shared pages. Memory includes Java and native allocations;
private-byte growth is not a measured native leak. For GC, use JDK JFR/jcmd locally
with an isolated fixture profile. Compare allocation rate/pauses and CPU stacks
before changing startup, catalog/image work or background tasks. JFR/raw dumps may
contain paths/content and must not be uploaded through the safe collector.

In Task Manager select the application's GPU Video Decode graph separately from
3D/Copy/composition. Where available use Windows Performance Recorder/Analyzer GPU
and CPU traces; correlate the target PID and interval. GPU utilization counters can
be missing/localized/driver-dependent and do not replace `hwdec-current` evidence.
No GPU or GC metric is silently synthesized when unavailable.

## Surface Pro 11 power protocol

Record AC/battery, charge start/end (prefer middle 30–80% range), Windows power mode,
brightness, refresh rate, scaling, HDR, resolution, volume/audio output, Wi-Fi/network,
background workload, thermals, driver/OS, duration, file hash and decoder mode.
Keep brightness/refresh/HDR/network fixed. Let the device cool/stabilize between runs.
Compare idle, browsing, 1080p and 4K with automatic and software decoding, three
repeats each; use 30–60 minute battery intervals when coarse charge readings require it.

Capture start/end Windows battery percentage and timestamps. `powercfg /batteryreport`
can corroborate historical capacity/drain; it is coarse and contains device details.
Keep its raw report private. If Windows Performance Recorder energy estimates or
external power instrumentation are available, document their model/device coverage
and sample interval. AC adapter watts include charging/display/system overhead;
battery percentage per hour is not application watts or precise joules. CPU reduction
alone is not an energy result. Report unavailable energy as unavailable.

Diagnostics introduce IO/property-read overhead. Run the same instrumentation in
both compared configurations; repeat a representative case with diagnostics disabled
to estimate observer impact. No aggressive scheduling, cache resizing, image-quality,
gesture or visual changes are included without profiling evidence.

The process-tree tests cover out-of-order/nested children, unrelated/cyclic records,
real current-process sampling and retired CPU-baseline cleanup. A baseline packaged
native-prerequisite smoke validates sampler integration in an isolated profile; it
is not final-head player acceptance. Process discovery adds observer overhead.

## Stability and evidence ledger

| Check | Implementation/evidence boundary | Status |
| --- | --- | --- |
| Property parsing/privacy | C++ injected-source tests and Node collector tests | run results recorded in PR |
| Native repeated playback | ten software HTTP cycles; three HTTPS cycles in ARM64 CI | final-head CI required |
| Ten actual app sessions/fullscreen | owner checklist | pending |
| Sustained 60–120 minute playback | owner checklist, raw metrics | pending |
| GPU/codec matrix | actual decoder/progress plus visible validation | pending |
| CPU/WS/private bytes | collection implemented; comparable device measurements | pending |
| Energy/battery | controlled protocol; no precise values fabricated | pending |
| New progress after restart/sync | isolated owner account workflow | pending |

For ten actual sessions, record per-session post-close idle memory/handles after a
consistent settling interval, and compare first/last/trend. For sustained playback
record five-minute windows, decoder changes, A/V drift, drop accumulation, responsiveness,
native crashes/hangs and normal exit. Check invalid local media, controlled HTTP 503,
unsupported codec/profile, interruption and successful next playback. Do not classify
all errors as hardware faults or force an unavailable decoder into a success claim.

Later release gates remain: credential-at-rest/export hardening, QuickJS exception
discipline, native licensing/source closure, MSI/updater identity and broad device
regression. No release/installer or public distribution milestone is completed here.

October 30 local evidence: ten native ARM64 software HTTP playback cycles pass,
including protected headers, pause/seek/resume, controlled error, destruction and
DLL unload. Standalone short Surface probes report active D3D11VA for generated
H.264/HEVC/AV1/VP9 at 1080p/4K; software H.264 and automatic raw-AVI controls progress
with `hwdec-current=no`. See STAGE5_HARDWARE_DECODING.md for exact limits and hashes.
This is not final-head embedded application or long-duration acceptance. No measured
CPU/GPU-utilization/energy benefit is claimed from these short, concurrent-workload runs.
