package com.nuvio.app.core.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.StandardOpenOption

internal class FrameIntervals {
    var frames = 0; private set
    private var total = 0L
    private var maximum = 0L
    private var over16 = 0
    private var over33 = 0
    fun add(nanos: Long) {
        frames++; total += nanos; maximum = maxOf(maximum, nanos)
        if (nanos > 16_666_667) over16++
        if (nanos > 33_333_334) over33++
    }
    fun json() = "{\"frames\":$frames,\"totalNs\":$total,\"maxNs\":$maximum,\"over16ms\":$over16,\"over33ms\":$over33}"
}

internal actual fun Modifier.nuvioScrollFrameProbe(state: LazyListState): Modifier {
    val path = DesktopScrollDiagnostics.target ?: return this
    return composed {
        if (!LocalScreenActive.current) return@composed Modifier
        LaunchedEffect(path, state) {
            val probeId = DesktopScrollDiagnostics.nextProbeId()
            val collectors = ManagementFactory.getGarbageCollectorMXBeans()
            fun gcCount() = collectors.sumOf { it.collectionCount.coerceAtLeast(0) }
            fun gcTime() = collectors.sumOf { it.collectionTime.coerceAtLeast(0) }
            val origin = DesktopScrollDiagnostics.originNs
            var fromMs = (System.nanoTime() - origin) / 1_000_000
            var previous = withFrameNanos { it }
            var index = state.firstVisibleItemIndex
            var offset = state.firstVisibleItemScrollOffset
            var work = DesktopScrollDiagnostics.counters.snapshot()
            var collections = gcCount()
            var collectionMs = gcTime()
            while (true) {
                val vertical = FrameIntervals()
                val horizontal = FrameIntervals()
                val mixed = FrameIntervals()
                val idle = FrameIntervals()
                repeat(300) {
                    val now = withFrameNanos { it }
                    val moved = index != state.firstVisibleItemIndex || offset != state.firstVisibleItemScrollOffset
                    // Raw promoted-pointer drags do not set isScrollInProgress.
                    // Visible offset movement also covers wheel and raw drag paths.
                    val outerMoving = moved || state.isScrollInProgress
                    val shelfMoving = ShelfScrollDiagnostics.sampleHorizontal()
                    (when {
                        outerMoving && shelfMoving -> mixed
                        outerMoving -> vertical
                        shelfMoving -> horizontal
                        else -> idle
                    }).add(now - previous)
                    previous = now
                    index = state.firstVisibleItemIndex
                    offset = state.firstVisibleItemScrollOffset
                }
                val toMs = (System.nanoTime() - origin) / 1_000_000
                val current = DesktopScrollDiagnostics.counters.snapshot()
                val delta = current.indices.map { current[it] - work[it] }
                val nextCollections = gcCount()
                val nextCollectionMs = gcTime()
                val record = "{\"schema\":\"nuvio-frame-v3\",\"probe\":$probeId,\"fromMs\":$fromMs,\"toMs\":$toMs,\"vertical\":${vertical.json()},\"horizontal\":${horizontal.json()},\"mixed\":${mixed.json()},\"idle\":${idle.json()},\"work\":{\"painters\":${delta[0]},\"scaleHits\":${delta[1]},\"scaleMisses\":${delta[2]},\"scaleNs\":${delta[3]},\"scaledBytes\":${delta[4]},\"imageCompletions\":${delta[5]},\"imageLatencyNs\":${delta[6]},\"imageMemoryHits\":${delta[7]},\"imageErrors\":${delta[8]},\"scaleWorstNs\":${current[9]},\"imageLatencySamples\":${delta[10]},\"gcCollections\":${nextCollections - collections},\"gcMs\":${nextCollectionMs - collectionMs},\"sharedScaleHits\":${delta[11]},\"shelfEntries\":${delta[12]},\"shelfDisposals\":${delta[13]},\"cacheBudgetBytes\":$scaledArtworkCacheBudgetBytes}}\n"
                withContext(Dispatchers.IO) {
                    runCatching { Files.writeString(path, record, StandardOpenOption.CREATE, StandardOpenOption.APPEND) }
                }
                work = current; collections = nextCollections; collectionMs = nextCollectionMs
                // File-write wait is outside the callback interval statistics.
                previous = withFrameNanos { it }
                fromMs = (System.nanoTime() - origin) / 1_000_000
            }
        }
        Modifier
    }
}
