package com.nuvio.app.stage3

import com.nuvio.app.features.debrid.*
import com.nuvio.app.features.streams.StreamClientResolve
import com.nuvio.app.features.streams.StreamItem
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class Stage3ProviderIntegrationTest {
    @Test fun realDebridAuthenticationResolutionEncodingAndRecoveryUseOnlyLoopback() = runBlocking {
        Stage3Fixture().use { fixture ->
            val provider = RealDebridProviderApi(client = RealDebridClient(fixture.baseUrl))
            val stream = StreamItem(addonName = "fixture", addonId = "addon:fixture",
                clientResolve = StreamClientResolve(service = "realdebrid", infoHash = "0123456789abcdef0123456789abcdef01234567"))
            assertTrue(provider.validateApiKey(" fixture-only "))
            val resolved = assertIs<DirectDebridResolveResult.Success>(provider.resolveClientStream(stream, "fixture-only", null, null))
            assertEquals("${fixture.baseUrl}/video?fixture=one%2Btwo&session=local", resolved.url)
            assertEquals("fixture.mp4", resolved.filename)
            assertTrue(fixture.requests.all { it.headers["authorization"] == "Bearer fixture-only" })
            assertTrue(fixture.requests.first { it.path == "/unrestrict/link" }.body.contains("one%252Btwo"))
            assertEquals("files=1", fixture.requests.first { it.path.startsWith("/torrents/selectFiles") }.body)
            fixture.providerStatus = 401
            assertFalse(provider.validateApiKey("expired-fixture-only"))
            assertIs<DirectDebridResolveResult.Error>(provider.resolveClientStream(stream, "expired-fixture-only", null, null))
            fixture.providerStatus = 200
            fixture.unrestrictStatus = 503
            assertIs<DirectDebridResolveResult.Stale>(provider.resolveClientStream(stream, "fixture-only", null, null))
            assertEquals("DELETE", fixture.requests.last().method)
            fixture.unrestrictStatus = 200
            assertIs<DirectDebridResolveResult.Success>(provider.resolveClientStream(stream, "second-fixture-only", null, null))
            assertEquals("Bearer second-fixture-only", fixture.requests.last().headers["authorization"])
        }
    }
}
