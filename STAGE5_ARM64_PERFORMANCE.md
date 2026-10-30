# Stage 5: performance, power and stability

**Initial ARM64 hardware acceleration and diagnostic validation complete. Advanced
performance, battery and long-duration stability work deferred.**
The owner accepts this narrower milestone for PR #10. This is not completion of
all original Stage 5 acceptance criteria or broad public-distribution readiness.
No comparative CPU/GPU/energy improvement, endurance or leak freedom is claimed.
The merged Stage 4 baseline is
`2f71f1c8a4585457427c540ec2de3cc260768d72`; Stage 4's owner cache observations remain
in STAGE4_FINAL_VALIDATION.md with their original uncontrolled-workload limitations.

## Owner application session at b62e5550

Owner results from matching image/tools at
`b62e5550781aa52b2f70bb8602283c9e026eb975`: approximately five minutes on Surface Pro 11,
267 application process-tree samples, exit 0, zero JVM crash reports and playback
errors, player initialization and native destruction returned. Embedded AV1 1080p
near 23.976 FPS reported auto requested / d3d11va active across 40 qualifying
intervals from 57 playback samples. This is initial stability/hardware evidence,
not an endurance result. The supplied aggregate JSON and application-session manifest
were independently inspected: duration 311.70 seconds, matching source/architecture,
exit/crash/error values and metrics. [Sanitized evidence/input hashes](docs/stage5/owner-surface-results.json)
are committed; raw archives, private paths and media/account identifiers are excluded.
The legacy archive checklist is still pending; the owner's explicit narrower
milestone acceptance is separate. Raw per-sample telemetry was not reprocessed and
hardware testing was not repeated during finalization.

CPU mean approximately **2.40%**, peak **23.41%**, last **1.35%**, normalized across
logical processors. This covers startup/navigation and the application session,
not solely steady-state video playback. It does not establish a hardware-decoding
power benefit or a measured optimization over Stage 4/software decoding.

| Process-tree measure | Average | Peak | Final sample |
| --- | --- | --- | --- |
| Working set | approximately 1,173 MiB | approximately 2,204 MiB | approximately 709 MiB |
| Private bytes | approximately 1,202 MiB | approximately 2,058 MiB | approximately 746 MiB |
| Handles | not supplied | 4,317 | 1,243 |
| Threads | not supplied | 239 | 70 |
| Processes | not supplied | 8 | 2 |

Summed working sets can double-count shared pages; private bytes include managed
and native allocations. The temporary peak warrants future monitoring. The decrease
at the final sample establishes neither a leak nor absence of leaks; one session
does not establish repeated-session resource growth. No memory optimization is proven.
The earlier Codex short-smoke/standalone measurements remain separate evidence.

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

The following deferred work is **not completed** and is not an automatic blocker
for the owner-approved narrower milestone. Final merge-review readiness still
requires a focused diff and passing current-head automated checks.

| Check | Implementation/evidence boundary | Status |
| --- | --- | --- |
| Property parsing/privacy | C++/Node tests; unknown fields remain unknown | implemented; final-head checks recorded in PR #10 |
| Native repeated playback | ten software HTTP cycles; three HTTPS cycles in ARM64 CI | automated coverage retained; not ten UI sessions |
| Initial full-app hardware/stability | owner AV1 evidence and clean exit at b62e5550 | accepted for initial milestone |
| Ten actual app sessions and 60–120 minute playback | memory/handle growth and native cleanup | deferred [#11](https://github.com/Unitron07/Nuvio-ARM64/issues/11) |
| Full codec/profile/container/fps, 10-bit/HDR and fallback matrix | owner AV1 and separate Codex probes are partial coverage | deferred [#12](https://github.com/Unitron07/Nuvio-ARM64/issues/12) |
| Controlled hardware/software CPU and GPU engine/GC measurements | whole-session exploratory metrics are available | deferred [#13](https://github.com/Unitron07/Nuvio-ARM64/issues/13) |
| Battery/power | repeatable protocol below; no energy benefit measured | deferred [#13](https://github.com/Unitron07/Nuvio-ARM64/issues/13) |
| New progress after restart/sync and additional account/debrid regressions | existing resume data is a different result | deferred [#14](https://github.com/Unitron07/Nuvio-ARM64/issues/14) |
| QuickJS JNI exception discipline | inherited native risk, not a new Stage 5 crash report | deferred [#15](https://github.com/Unitron07/Nuvio-ARM64/issues/15) |

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

## Finalization review

The full PR diff and empty review/comment/thread lists were reviewed. Existing
automatic decoder selection, fallback/preference semantics, source headers, native
lifecycle/embedding workaround, WebView2/controls, cache bounds and Stage 4 touch
code remain unchanged during finalization. No critical introduced defect was found.
One collector guard now rejects a progress interval when either elapsed timestamp
is unavailable/nonfinite; a failing-then-passing regression covers both endpoints.
This is malformed-input correctness, not a decoder-policy or performance change.
The opt-in diagnostics and owner-only Preferences isolation helper are retained.
Final-head CI and source-bound artifacts are recorded in PR #10; owner hardware
results retain their exact b62e5550 provenance and were not rerun for finalization.
