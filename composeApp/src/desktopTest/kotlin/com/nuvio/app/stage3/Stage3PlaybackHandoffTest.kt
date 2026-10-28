package com.nuvio.app.stage3

import com.nuvio.app.features.player.desktop.NativePlayerController
import com.nuvio.app.features.player.desktop.NativePlayerHost
import com.nuvio.app.features.player.sanitizePlaybackHeaders
import com.nuvio.app.features.streams.StreamParser
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.*

class Stage3PlaybackHandoffTest {
    @Test fun parsedAddonUrlAndHeadersReachNativeCreateAcrossRepeatedNavigation() {
        Stage3Fixture().use { fixture ->
            val streams = StreamParser.parse(fixture.fixture("stream.json"), "fixture", "addon:fixture")
            repeat(10) { cycle ->
                val stream = streams[cycle % 2]
                val created = CountDownLatch(1)
                val allowCreate = CountDownLatch(1)
                val released = CountDownLatch(1)
                var capturedSource = ""
                var capturedHeaders = emptyList<String>()
                var capturedPosition = -1L
                var disposedHandle = 0L
                val controller = NativePlayerController(
                    host = NativePlayerHost(),
                    nativeCreate = { _, source, headers, play, position, _, _, _, _ ->
                        capturedSource = source
                        capturedHeaders = headers.toList()
                        capturedPosition = position
                        assertTrue(play)
                        created.countDown()
                        assertTrue(allowCreate.await(5, TimeUnit.SECONDS))
                        cycle + 1L
                    },
                    nativeDispose = { disposedHandle = it },
                    isHostDisplayable = { true },
                    resolveHostView = { 1L },
                    resolveControlsPage = { "about:blank" },
                )
                controller.attach(stream.playableDirectUrl!!,
                    sanitizePlaybackHeaders(stream.behaviorHints.proxyHeaders?.request), true, 12500L, 0, false) {
                    fail("Unexpected create error: $it")
                }
                assertTrue(created.await(5, TimeUnit.SECONDS))
                controller.releaseBeforeNavigation { released.countDown() }
                allowCreate.countDown()
                assertTrue(released.await(5, TimeUnit.SECONDS))
                assertEquals(stream.url, capturedSource)
                assertEquals(12500L, capturedPosition)
                assertEquals(cycle + 1L, disposedHandle)
                assertEquals(stream.behaviorHints.proxyHeaders!!.request!!.map { (key, value) -> "$key: $value" }.toSet(), capturedHeaders.toSet())
            }
        }
    }
}
