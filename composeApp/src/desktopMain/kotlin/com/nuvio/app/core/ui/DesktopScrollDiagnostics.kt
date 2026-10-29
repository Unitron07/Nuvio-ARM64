package com.nuvio.app.core.ui

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLongArray
import java.util.concurrent.atomic.AtomicLong

/** Numeric process-wide counters only. Never accepts image models/URLs/errors. */
internal object DesktopScrollDiagnostics {
    val target: Path? = runCatching {
        System.getenv("NUVIO_SCROLL_METRICS_FILE")?.takeIf { it.isNotBlank() }
            ?.let { Path.of(it) }?.takeIf { it.isAbsolute }
    }.getOrNull()
    val enabled: Boolean = target != null
    val originNs: Long = System.nanoTime()
    val counters = ScrollWorkCounters()
    private val probeIds = AtomicLong()
    fun nextProbeId() = probeIds.incrementAndGet()
}

internal class ScrollWorkCounters {
    private val values = AtomicLongArray(14)
    fun painterCreated() { values.incrementAndGet(0) }
    fun cacheHit() { values.incrementAndGet(1) }
    fun scaled(nanos: Long, bytes: Long) {
        values.incrementAndGet(2); values.addAndGet(3, nanos); values.addAndGet(4, bytes)
        var previous = values.get(9)
        while (nanos > previous && !values.compareAndSet(9, previous, nanos)) previous = values.get(9)
    }
    fun imageCompleted(nanos: Long, memoryHit: Boolean) {
        values.incrementAndGet(5); values.addAndGet(6, nanos)
        if (memoryHit) values.incrementAndGet(7)
        if (nanos > 0) values.incrementAndGet(10)
    }
    fun imageFailed() { values.incrementAndGet(8) }
    fun sharedScaleHit() { values.incrementAndGet(11) }
    fun shelfEntered() { values.incrementAndGet(12) }
    fun shelfDisposed() { values.incrementAndGet(13) }
    fun snapshot(): LongArray = LongArray(14) { values.get(it) }
}
