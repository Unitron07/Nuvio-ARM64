# Vertical shelf scrolling: measured work and bounded scale reuse

Status: targeted optimization implemented; Surface speedup remains pending. PR #9
stays draft. Media/authentication/provider and gesture physics code are unchanged.
This supersedes the earlier missing-recording/diagnostic-only status in the older
performance guide; that history remains for provenance.

## Verified owner recordings

Read the original owner-provided files at C:/NuvioTests/stage4-v2-metrics, without
copying raw owner data into the repository. Reproduction:

```powershell
node ./scripts/windows/stage3/analyze-scroll-metrics.mjs C:/NuvioTests/stage4-v2-metrics/real-cold-v2.jsonl --mode active
node ./scripts/windows/stage3/analyze-scroll-metrics.mjs C:/NuvioTests/stage4-v2-metrics/real-warm-v2.jsonl --mode active
node ./scripts/windows/stage3/analyze-scroll-metrics.mjs C:/NuvioTests/stage4-v2-metrics/synthetic-artwork-v2.jsonl --mode active --from-ms 120000
```

| Metric | Real cold | Real warm | Synthetic after 120s (limited) |
| --- | ---: | ---: | ---: |
| Selected windows | 27 | 16 | 8 (81 excluded) |
| Active intervals | 2635 | 2109 | 202 |
| Mean active ms | 11.3561 | 12.3525 | 10.6854 |
| Active >33.3ms | 126 (4.7818%) | 209 (9.9099%) | 4 (1.9802%) |
| Painter creations | 968 | 1579 | 244 |
| Scale misses | 944 | 1569 | 242 |
| Scale time seconds | 6.2274 | 7.3943 | 1.9818 |
| Estimated allocated bytes | 890862480 | 1481546112 | 223120128 |
| Image memory-cache completions | 802 | 1418 | 200 |
| JVM GC ms | 310 | 104 | 172 |

Work counters cover whole windows, NOT only active scrolling. Allocation is summed
estimated bitmap allocation volume, not live/retained memory. Warm scale misses
are 99.4% of painter creations despite 1418 memory-cache image completions.
Average measured scale cost is about 6.60ms cold / 4.71ms warm, but this does not
prove scaling alone explains callback stalls or assign all that time to gestures.

The real recordings have no overlapping windows. The synthetic recording has
interleaved/overlapping windows (84 adjacent overlaps across 89 records), consistent
with multiple active probe writers; its small post-cutoff sample is NOT a clean
performance baseline. v2 has no probe IDs, so overlapping writers cannot be reliably
separated. The 120s cutoff is approximate setup exclusion, not a verified gesture
start. Do not infer a controlled real-versus-synthetic speed ratio from that column.
Old v2 active classification observes outer-list movement, not horizontal shelf
movement; v3 below corrects that limitation.

## Root-cause path and why direction matters

HomeScreen uses keyed catalog items/content types in an outer LazyColumn.
HomeCatalogRowSection -> NuvioShelfSection -> LazyRow -> HomePosterCard ->
NuvioAsyncImage supplies each visible shelf/card. Shelf state uses Compose's
rememberLazyListState/saveable integration; no missing key/state bug was established.
Normal lazy disposal of an offscreen shelf is necessary, not a bug to suppress.

Moving vertically can dispose/recreate a shelf and all its visible poster painters.
Moving horizontally usually retains that shelf and many existing painters while
adding cards at its edge. Previously ScaledBitmapPainter cached one size only in
that painter. Re-entry could retrieve a decoded source from Coil but allocate and
run the same synchronous Skia scale again in onDraw. The measured near one-to-one
painter/miss relationship and high warm decoded-cache activity support this reuse
gap. v2 does not count shelf disposal directly or identify individual images, so
the share of stutter attributable to this path is still an inference.

## Targeted optimization

Keep the existing per-painter fast path, lazy rendering, request sizes, scale
algorithm, quantized target size, filtering, overlays and animation behavior.
On a painter cache miss, consult a shared desktop scaled-result LRU:

- Key: decoded Skia bitmap OBJECT identity, bitmap generation, exact quantized
  target dimensions. New decoded content/generation or size cannot reuse stale
  results. URLs/catalog identifiers are not keys and are never logged.
- At most **64 MiB** of estimated N32 scaled pixels retained by this cache. Larger
  individual entries bypass retention. Oldest unused entries are evicted; hits
  update LRU order. Dead weak sources and superseded generations are pruned on
  insertion/miss. Decoded sources are weakly referenced and not kept alive here.
  A separate 256-entry cap bounds metadata even for tiny icons.
- Active painters can keep their own references after eviction. Never close an
  evicted bitmap still in use by a painter/GPU. Reference release/Skia cleanup uses
  existing JVM/native ownership. The 64 MiB cap is cache-owned retention, not a cap
  on all painter/source/GPU memory or transient allocation. Coil's 256 MiB source
  cache remains separate. A miss can transiently allocate one result before eviction.
- No eager composition of hidden shelves, prefetch tuning, global painter cache,
  background resizer or custom gesture change. First scales/new sizes and source
  evictions can still cost draw time. Decoded static sources are treated read-only,
  as before; animation frames bypass this static painter path.

The actual Skia test recreates 40 painters for the same decoded bitmap/size and
asserts the exact same scaled bitmap is reused. It also checks pixels, target-size
separation and source-generation invalidation. LRU tests enforce byte bounds,
oversize bypass and clear behavior. These prove eliminated repeated work for that
scenario, NOT a measured before/after Surface frame-rate improvement.

## Direction-specific v3 diagnostics

Use NUVIO_SCROLL_METRICS_FILE as before. Disabled diagnostics do not register shelf
observers or run a frame loop. Enabled probes now gate on LocalScreenActive and
emit numeric `probe` IDs to distinguish writers. Shelf DisposableEffects register
only active NuvioShelfSection states and remove them on disposal/inactivation.
No shelf names, addon IDs or image keys are recorded or retained in diagnostics.

Each 300-interval window separates `vertical`, `horizontal`, `mixed`, `idle` using
observed outer-list/shelf offset movement or framework scrolling. Raw drags are
covered without changing handlers. Horizontal coverage is registered generic
catalog shelves; other custom horizontal containers are not automatically covered.
Shelf entries/disposals are active-scope lifecycle counts, not every recomposition.
Mixed means both axes observed in the interval, not necessarily one diagonal gesture.

Work metrics add sharedScaleHits, shelfEntries, shelfDisposals and cacheBudgetBytes.
Local scaleHits remain distinct. Work still covers WHOLE windows, including idle
and other directions; do not claim per-direction attribution for those totals.
Capture separate vertical-only/horizontal-only phases and use complete windows.
Callback intervals are not GPU-presented frames or exact dropped-frame counts.
GC/latency and animated-collection coverage limitations in STAGE4_PERFORMANCE.md
still apply. The analyzer reports overlapping windows and refuses to silently
combine multiple v3 probe IDs; older v2 cannot support direction/ID selection.

## Exact Surface A/B procedure (new physical results pending)

Download passing image and owner tools for the same final commit linked in PR #9.
Use a fresh isolated RunDirectory per image; use the SAME local test profile/content
within that image for the controlled comparison. Do not send private profile/log data.
Keep dimensions/scaling/refresh/power/settings/network conditions and gesture
sequence identical. Use new metrics filenames per process; never concatenate runs.

Control run, same image, same rendering quality, shared reuse disabled:

```powershell
$env:NUVIO_SHARED_ARTWORK_CACHE='off'
$env:NUVIO_SCROLL_METRICS_FILE='C:/NuvioTests/vertical-control-v3.jsonl'
# Start-OwnerTest -Resume with the fresh matching-image run directory; close normally.
Remove-Item Env:NUVIO_SHARED_ARTWORK_CACHE
Remove-Item Env:NUVIO_SCROLL_METRICS_FILE
```

Optimized run (default 64 MiB retention):

```powershell
$env:NUVIO_SCROLL_METRICS_FILE='C:/NuvioTests/vertical-reuse-v3.jsonl'
# Start-OwnerTest -Resume against the same matching image/profile; close normally.
Remove-Item Env:NUVIO_SCROLL_METRICS_FILE
```

In EACH run: settle after startup; record elapsed phase boundaries. Scroll real
catalog vertically down several shelves then back; repeat the SAME shelf re-entry
sequence three times to distinguish warm re-entry from first visit. Then leave the
outer list stationary and swipe horizontally within one shelf for a separate phase.
Repeat with synthetic/text controls. Alternate run order and repeat to reduce
order/cold-cache bias; do not compare first-image-cold against warmed second run.

```powershell
node ./scripts/windows/stage3/analyze-scroll-metrics.mjs C:/NuvioTests/vertical-reuse-v3.jsonl --list-probes
# Replace probe and elapsed bounds using the actual phase, not these example numbers:
node ./scripts/windows/stage3/analyze-scroll-metrics.mjs C:/NuvioTests/vertical-reuse-v3.jsonl --probe 1 --mode vertical --from-ms 30000 --to-ms 60000
node ./scripts/windows/stage3/analyze-scroll-metrics.mjs C:/NuvioTests/vertical-reuse-v3.jsonl --probe 1 --mode horizontal --from-ms 60000 --to-ms 90000
```

Compare active callback mean/>16.7ms/>33.3ms/max, shared hits, true scale misses/time/
bytes and shelf entries/disposals for matched phases. cacheBudgetBytes confirms
off/on. Compare complete nonoverlapping windows; discard transitions/mixed phases.
Verify horizontal smoothness/position retention, shelf refresh updates, taps and
arbitration, details/settings/source list, menu dismissal, mouse/keyboard and real
debrid playback. No new physical result is marked passed; existing owner successes
remain recorded. No Surface speedup numbers have been invented.
