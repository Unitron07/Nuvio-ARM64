# Stage 4: real-catalog performance investigation

**Core touchscreen UX validated on Surface Pro 11 — ready for merge, subject to final checks.**

Current results: [final validation ledger](STAGE4_FINAL_VALIDATION.md) and
[bounded scale reuse / v3 diagnostics](STAGE4_VERTICAL_SCROLLING.md). The owner
reports measurable reuse, 6.66% to 4.35% severe vertical callback intervals and
subjectively smoother scrolling. This is a limited A/B observation, not an FPS
improvement or elimination of stutter.

The diagnostic-only/no-optimization/missing-recording statements and v1/v2 procedure
below are **historical**, preceding the shared cache and final owner report.
They preserve the investigation evidence rather than describe current readiness.

## Historical v1/v2 evidence ledger

The owner confirms natural short swipes, source-selector dragging, Home/Settings,
horizontal shelves and touch menu dismissal, plus authentication/sync, real addons,
debrid and native video/audio on Surface Pro 11. The unavailable-addon mpv -13 case
is not evidence of a player regression. Exact owner-tested SHA was not supplied.

The owner supplied these previously summarized v1 callback recordings:

| Metric | Real cold | Real warm | Synthetic artwork |
| --- | ---: | ---: | ---: |
| Intervals | 5,700 | 8,400 | 16,500 |
| Mean ms | 10.82 | 11.03 | 9.47 |
| Above 16.7 ms | 5.6% | 6.3% | 3.1% |
| Above 33.3 ms | 3.5% | 3.7% | 1.5% |
| Maximum ms | 132.5 | 186.3 | 100.9 |

These are **owner-provided summaries, not independently verified measurements**.
Workspace search did not find `real-cold.jsonl`, `real-warm.jsonl` or
`synthetic-artwork.jsonl`. The original files and a reliable setup/end boundary
are needed to verify weighted calculations and exclude roughly two minutes of
synthetic logout/setup. No raw records have been reconstructed. Ratios computed
from the rounded over-33ms percentages are about 2.33 and 2.47; they are not a
controlled comparison or a bottleneck diagnosis. Approximate summed callback time
from rounded counts/means is 61.7/92.7/156.3 seconds, not session wall duration.
The synthetic setup confound is therefore substantial.

## Rendering audit and strongest hypothesis

- `AsyncImage.desktop.kt`: NuvioAsyncImage remembers request models and callbacks.
  Windows high-quality mode requests up to 1536px and uses ScaledBitmapPainter.
  Its first scale or quantized-size miss allocates an N32 bitmap and invokes Skia
  scalePixels synchronously inside onDraw. Subsequent draws reuse a bitmap owned
  by that painter, not Coil's shared memory cache. A newly created painter starts
  empty even when its decoded source came from the Coil cache. No global scaled
  bitmap cache exists. This establishes where work occurs, not its measured cost.
- Desktop Coil has a 256 MiB decoded-image memory cache. Request identity is
  remembered in poster cards; decoding/fetch, disk-cache effectiveness and GPU
  texture uploads have not been profiled on the owner's real catalog.
- Home has duplicate-safe item keys and content types; shelf entries are remembered
  and normally use keyed poster items. A missing-key fallback exists where no key
  function is supplied; no observed real-catalog churn has been attributed to it.
- Real cards can include text, logos, gradients/depth, badges and animated artwork;
  the original fixture had 40 URLs sharing one small JPEG and reused URLs across
  shelves. It underrepresented unique content and source dimensions. Visibility,
  image state changes, text/layout, animation, uploads, GC and recomposition remain
  competing causes. No platform-specific graphics defect is established.

The strongest actionable hypothesis is repeated draw-thread scaling/allocation
when painter instances are recreated. Shipping an asynchronous resizer/shared
bitmap cache now would be speculative and could alter quality, lifetime or memory
use. Per the request's diagnostic fallback, this build instruments the path first.

## Opt-in v2 diagnostics

Set `NUVIO_SCROLL_METRICS_FILE` to a NEW absolute filename before isolated launch;
unset it after normal close. Without it there is no probe frame loop, clock
sampling, MXBean reads, counter updates or file writes. Image callbacks remain
null when neither diagnostics nor application callbacks need them.

Every 300 callback intervals, `nuvio-frame-v2` writes only allowlisted numeric
aggregates plus schema/static bucket names:

- `active` / `idle`: frames, totalNs, maxNs, over16ms and over33ms. Active means
  framework scrolling OR a visible list index/offset change. This catches raw
  promoted-mouse drags. Stationary holds are idle; programmatic scrolling is active.
  This is observed list movement, not touch-source identification or input latency.
- `fromMs` / `toMs`: relative monotonic elapsed time since desktop diagnostics
  initialization, retained across screen navigation within the process. Select
  only complete windows inside a recorded test interval. New processes reset it.
- `work`: painter creations, scaled-cache hits/misses, scaling nanoseconds and
  estimated allocated N32 bytes; completed/failed image requests, memory-cache
  completions, summed request latency and samples with a known loading start;
  GC collection/time deltas. Request latency includes queue/fetch/decode/callback
  scheduling, not isolated decoder CPU time. Failures log no exception details.
- `scaleWorstNs`: process-lifetime worst instrumented scale, NOT a window delta.
  Other work fields are process-wide window deltas and include both active and
  idle work, possibly other concurrently composed screens. Atomic counters give
  approximate window attribution under concurrency, not a transaction trace.

Scaling duration is measured on the calling draw thread. GC counters are JVM-wide
MXBeans and cannot prove that a pause coincided with one callback stall. Correlate
work-heavy windows with active bucket stalls; correlation is not causation.
Frame-clock callbacks are NOT GPU-presented frames or actual dropped frames.
16.7/33.3ms thresholds are fixed comparison thresholds, not refresh-aware budgets.
File I/O runs on Dispatchers.IO outside measured callback intervals. Partial windows
at close and intervals awaiting writes are omitted. Probe overhead can affect pacing.
Navigation transitions/multiple screen effects can overlap; discard such windows.

No account IDs, catalog names, URLs, headers, image keys, credentials, paths or
absolute timestamps enter the records. Metrics stay local and are not added to
the existing ZIP collector. Raw JFR/profiler recordings may contain private strings:
keep them local and share only reviewed aggregate results. No raw profile is bundled.

## Reproducible owner comparison

1. Download the passing image and owner tools from the same final SHA linked in
   PR #9. Use a fresh RunDirectory (do not resume a different image). Keep the
   same window dimensions, scaling, refresh rate, power mode and artwork settings.
2. Start the fixture, with your authorized local media:

   ```powershell
   node ./scripts/windows/stage3/fixture.mjs --tablet true --artwork true --media C:/NuvioTests/authorized.mp4
   $env:NUVIO_SCROLL_METRICS_FILE='C:/NuvioTests/real-warm-v2.jsonl'
   # Run the existing isolated Start-OwnerTest -NativeSmoke then -Resume procedure.
   # After closing normally:
   Remove-Item Env:NUVIO_SCROLL_METRICS_FILE
   ```

3. Test text-only (omit artwork), complex synthetic artwork, then the SAME real
   catalog. The fixture now has 24 distinct generated JPEGs at 480x720,
   1000x1500 and 1536x2304, distinct per-shelf URLs, longer metadata and cacheable
   image responses. It remains synthetic; it does not match real artwork/animations
   or network latency exactly. Its committed generator needs Pillow only to
   regenerate assets, not for CI builds or owner testing.
4. Finish navigation/setup first. Note elapsed test start/end locally. Scroll the
   same 30-second short/long vertical sequence, then back over the loaded shelves.
   Use new filenames per process; do not concatenate runs with reset timelines.
   Compare cold/warm active intervals, keeping idle intervals as a separate control.
   Do not clear or copy private owner profiles merely to force a cache miss.
5. Example analysis (replace time bounds with the actual recorded test period):

   ```powershell
   node ./scripts/windows/stage3/analyze-scroll-metrics.mjs C:/NuvioTests/real-warm-v2.jsonl --mode active --from-ms 120000 --to-ms 150000
   node ./scripts/windows/stage3/analyze-scroll-metrics.mjs C:/NuvioTests/real-warm-v2.jsonl --mode idle --from-ms 120000 --to-ms 150000
   ```

   The analyzer weights averages/percentages by intervals rather than averaging
   per-window means. It excludes boundary-straddling windows and reports how many
   were selected/excluded. `workWholeWindows` is always all work in selected windows,
   even with `--mode active`; do not label it active-only. v1 can be summarized in
   `all` mode but rejects time/active filtering because it has no such evidence.
   `--skip-records N` is valid only with an independently known record boundary;
   it cannot infer the two-minute setup cutoff. Unknown fields are never echoed.
6. For costly active windows inspect scaling count/time/bytes, painter churn,
   request completions/latency/cache hit counts and GC deltas. If scaling is small,
   do not optimize it based solely on suspicion. If it is material, compare a
   separately reviewed same-quality optimization under these identical conditions.
   No optimized image or before/after speedup exists in this diagnostic revision.

## Automated and physical validation

Counter/window and analyzer tests cover cache/work accounting, thresholds,
weighted v1/v2 summaries, setup selection and privacy-safe output. Existing real
StreamList/gesture and Stage 3 integration tests remain. CI continues native ARM64
and x64 integration/QuickJS and ARM64 image audits. Android/iOS probe actuals are
still no-ops with matching signatures; their platform builds are not executed by
these Windows workflows. No Android/iOS compatibility pass is invented.

New controlled performance measurements, scale/GC attribution, subjective real
scrolling responsiveness and repeated mixed-input/playback checks remain pending
as future controlled hardening. The later owner comparison is recorded in the
final ledger; final-head test counts, CI and artifact links are in PR #9.
