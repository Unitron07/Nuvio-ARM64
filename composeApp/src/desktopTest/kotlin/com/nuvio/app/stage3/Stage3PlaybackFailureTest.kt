package com.nuvio.app.stage3

import com.nuvio.app.features.player.desktop.NativePlayerController
import com.nuvio.app.features.player.desktop.NativePlayerEventSink
import com.nuvio.app.features.player.desktop.NativePlayerHost
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import kotlin.test.*

class Stage3PlaybackFailureTest {
    @Test fun asynchronousFailuresReachTheActiveSourceAndRetiredCallbacksAreIgnored() {
        val started = List(2) { CountDownLatch(1) }
        val finish = List(2) { CountDownLatch(1) }
        val reported = List(2) { CountDownLatch(1) }
        val sinks = arrayOfNulls<NativePlayerEventSink>(2)
        val errors = Collections.synchronizedList(mutableListOf<String>())
        val disposed = Collections.synchronizedList(mutableListOf<Long>())
        val released = CountDownLatch(1)
        val controller = NativePlayerController(
            host = NativePlayerHost(),
            resolveControlsPage = { "about:blank" },
            isHostDisplayable = { true },
            resolveHostView = { 1L },
            nativeCreate = { _, source, _, _, _, _, _, _, sink ->
                val index = source.last().digitToInt()
                sinks[index] = sink
                started[index].countDown()
                check(finish[index].await(5, TimeUnit.SECONDS))
                index + 1L
            },
            nativeDispose = { disposed += it },
        )
        fun attach(index: Int) = controller.attach("http://127.0.0.1/source$index", emptyMap(), true, 0L, 0, false) {
            errors += "$index:$it"
            reported[index].countDown()
        }
        try {
            attach(0)
            assertTrue(started[0].await(5, TimeUnit.SECONDS))
            sinks[0]!!.onPlayerEvent("playbackError", -13.0)
            assertTrue(reported[0].await(5, TimeUnit.SECONDS))
            attach(1)
            sinks[0]!!.onPlayerEvent("playbackError", -13.0)
            SwingUtilities.invokeAndWait {}
            assertEquals(1, errors.size)
            finish[0].countDown()
            assertTrue(started[1].await(5, TimeUnit.SECONDS))
            sinks[1]!!.onPlayerEvent("playbackError", -13.0)
            assertTrue(reported[1].await(5, TimeUnit.SECONDS))
            controller.releaseBeforeNavigation { released.countDown() }
            sinks[1]!!.onPlayerEvent("playbackError", -13.0)
            finish[1].countDown()
            assertTrue(released.await(5, TimeUnit.SECONDS))
            SwingUtilities.invokeAndWait {}
            assertEquals(listOf("0:Playback failed (mpv error -13).", "1:Playback failed (mpv error -13)."), errors.toList())
            assertEquals(listOf(1L, 2L), disposed.toList())
        } finally {
            finish.forEach { it.countDown() }
            controller.dispose()
        }
    }
}
