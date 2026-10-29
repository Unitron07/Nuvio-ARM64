package com.nuvio.app.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals

class ScrollDiagnosticsTest {
    @Test fun workSnapshotRetainsScaleCostCacheReuseAndUnknownLatencyDistinction() {
        val counters = ScrollWorkCounters()
        counters.painterCreated(); counters.cacheHit(); counters.cacheHit()
        counters.scaled(40_000_000, 480L * 720 * 4)
        counters.scaled(1_000_000, 100L * 100 * 4)
        counters.imageCompleted(0, true) // cache completion without a loading callback
        counters.imageCompleted(20_000_000, false)
        counters.imageFailed()
        assertContentEquals(longArrayOf(1, 2, 2, 41_000_000, 1_422_400, 2, 20_000_000, 1, 1, 40_000_000, 1), counters.snapshot())
        assertContentEquals(counters.snapshot(), counters.snapshot()) // reads do not drain other probes
    }
    @Test fun frameBucketsKeepWeightedDurationsAndThresholds() {
        val bucket = FrameIntervals()
        bucket.add(10_000_000); bucket.add(20_000_000); bucket.add(40_000_000)
        assertEquals("{\"frames\":3,\"totalNs\":70000000,\"maxNs\":40000000,\"over16ms\":2,\"over33ms\":1}", bucket.json())
    }
}
