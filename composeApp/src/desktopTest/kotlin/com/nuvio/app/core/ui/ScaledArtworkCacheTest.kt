package com.nuvio.app.core.ui

import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.IntSize
import org.jetbrains.skia.Bitmap
import kotlin.test.*

class ScaledArtworkCacheTest {
    @Test fun painterRecreationReusesRealSkiaScaleAndGenerationInvalidatesIt() {
        scaledArtworkCache.clear()
        Bitmap().use { source ->
            source.allocN32Pixels(64, 96); source.erase(0xffff0000.toInt())
            fun painter() = ScaledBitmapPainter(source.asComposeImageBitmap(), source)
            val first = painter().scaledBitmap(IntSize(32, 48))
            repeat(40) {
                val next = painter().scaledBitmap(IntSize(32, 48))
                if (scaledArtworkCacheBudgetBytes > 0) assertSame(first, next) else assertNotSame(first, next)
            }
            assertEquals(1f, first.toPixelMap()[16, 24].red)
            val differentSize = painter().scaledBitmap(IntSize(16, 24))
            assertNotSame(first, differentSize)
            source.erase(0xff0000ff.toInt()); source.notifyPixelsChanged()
            val refreshed = painter().scaledBitmap(IntSize(32, 48))
            assertNotSame(first, refreshed)
            assertEquals(1f, refreshed.toPixelMap()[16, 24].blue)
            assertEquals(if (scaledArtworkCacheBudgetBytes > 0) 1 else 0, scaledArtworkCache.size)
        }
        scaledArtworkCache.clear()
        assertEquals(0L, scaledArtworkCache.retainedBytes)
    }

    @Test fun lruBudgetOversizeAndSourceIdentityAreEnforced() {
        val cache = ScaledArtworkCache<Any>(32)
        val a = Any(); val b = Any(); val c = Any()
        val first = cache.getOrCreate(a, 1, 2, 2) { Any() }
        val second = cache.getOrCreate(b, 1, 2, 2) { Any() }
        assertSame(first, cache.getOrCreate(a, 1, 2, 2) { error("must reuse") })
        cache.getOrCreate(c, 1, 2, 2) { Any() }
        assertEquals(32L, cache.retainedBytes)
        assertNotSame(second, cache.getOrCreate(b, 1, 2, 2) { Any() })
        assertTrue(cache.retainedBytes <= 32)
        val oversized = cache.getOrCreate(a, 1, 3, 3) { Any() }
        assertNotSame(oversized, cache.getOrCreate(a, 1, 3, 3) { Any() })
        assertFailsWith<IllegalArgumentException> { cache.getOrCreate(a, 1, 0, 2) { Any() } }
        cache.clear(); assertEquals(0, cache.size)
    }
}
