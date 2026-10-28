package com.nuvio.app.stage3

import com.nuvio.app.features.addons.*
import com.nuvio.app.features.details.MetaDetailsParser
import com.nuvio.app.features.home.HomeCatalogParser
import com.nuvio.app.features.plugins.runtime.PluginRuntime
import com.nuvio.app.features.plugins.PluginRepository
import com.nuvio.app.features.plugins.PluginStorage
import com.nuvio.app.features.plugins.StoredPluginsState
import com.nuvio.app.features.plugins.StoredPluginRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import com.nuvio.app.features.streams.StreamParser
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlin.test.*

class Stage3ApplicationIntegrationTest {
    @Test fun installedPluginRepositoryFetchesRelativeCodeAndExecutesThroughQuickJs() = runBlocking {
        Stage3Fixture().use { fixture ->
            PluginRepository.clearLocalState()
            PluginStorage.saveState(1, Json.encodeToString(StoredPluginsState(repositories = listOf(
                StoredPluginRepository(manifestUrl = "${fixture.baseUrl}/plugins/manifest.json", name = "fixture"),
            ))))
            try {
                PluginRepository.initialize()
                val state = withTimeout(5000) {
                    PluginRepository.uiState.first { it.scrapers.size == 1 && it.repositories.none { repo -> repo.isRefreshing } }
                }
                assertEquals("scraper.js", state.scrapers.single().filename)
                assertTrue(fixture.requests.any { it.path == "/plugins/scraper.js" })
                val streams = PluginRepository.testScraper(state.scrapers.single().id).getOrThrow()
                assertEquals(2, streams.size)
                assertEquals("Bearer fixture-only", streams.first().headers?.get("Authorization"))
            } finally {
                PluginRepository.clearLocalState()
                PluginStorage.saveState(1, Json.encodeToString(StoredPluginsState()))
            }
        }
    }

    @Test fun trustedHttpsAddonAndQuickJsFetchPreserveRedirectHeaders() = runBlocking {
        Stage3Fixture(tls = true).use { fixture ->
            val manifestUrl = "${fixture.baseUrl}/manifest.json"
            val manifest = AddonManifestParser.parse(manifestUrl, fetchAddonResponseText(manifestUrl))
            assertEquals("org.nuvio.stage3.fixture", manifest.id)
            val streams = PluginRuntime.executePlugin(fixture.fixture("scraper.js"), "stage3:video", "movie", null, null, "stage3-https")
            val stream = streams.first()
            val result = httpRequestRaw("GET", stream.url, stream.headers.orEmpty(), "")
            assertEquals(200, result.status)
            assertEquals("authorized-fixture-bytes", result.body)
            assertEquals("Bearer fixture-only", fixture.requests.last().headers["authorization"])
            assertEquals("fixture=one%2Btwo&session=local", fixture.requests.last().query)
        }
    }

    @Test fun installedAddonRegistersReloadsAndRemainsUsableAfterManifestFailure() = runBlocking {
        Stage3Fixture().use { fixture ->
            val manifestUrl = "${fixture.baseUrl}/manifest.json"
            AddonRepository.clearLocalState()
            AddonStorage.saveInstalledAddonUrls(1, listOf(manifestUrl))
            AddonStorage.saveAddonEnabledStates(1, mapOf(manifestUrl to true))
            try {
                suspend fun settled() = withTimeout(5000) {
                    AddonRepository.uiState.first { it.addons.size == 1 && !it.addons.single().isRefreshing }.addons.single()
                }
                AddonRepository.initialize()
                assertEquals("org.nuvio.stage3.fixture", settled().manifest?.id)
                fixture.manifestStatus = 503
                AddonRepository.refreshAddon(manifestUrl, true)
                assertNotNull(settled().errorMessage)
                fixture.manifestStatus = 200
                // Wait for the refresh worker's finally block to remove its job;
                // clear/init also models restoration without an external sync service.
                AddonRepository.clearLocalState()
                AddonRepository.initialize()
                assertNull(settled().errorMessage)
                assertEquals("org.nuvio.stage3.fixture", settled().manifest?.id)
            } finally {
                AddonRepository.clearLocalState()
                AddonStorage.saveInstalledAddonUrls(1, emptyList())
            }
        }
    }

    @Test fun addonTransportCatalogMetadataStreamsAndFailureRecovery() = runBlocking {
        Stage3Fixture().use { fixture ->
            val manifestUrl = "${fixture.baseUrl}/manifest.json?config=one%2Btwo"
            val manifest = AddonManifestParser.parse(manifestUrl, fetchAddonResponseText(manifestUrl))
            assertEquals("org.nuvio.stage3.fixture", manifest.id)
            val catalog = HomeCatalogParser.parseCatalog(fetchAddonResponseText(
                buildAddonResourceUrl(manifestUrl, "catalog", "movie", "stage3")))
            val meta = MetaDetailsParser.parse(fetchAddonResponseText(
                buildAddonResourceUrl(manifestUrl, "meta", "movie", catalog.single().id)))
            val streams = StreamParser.parse(fetchAddonResponseText(
                buildAddonResourceUrl(manifestUrl, "stream", "movie", meta.id)), manifest.name, "addon:${manifest.id}")
            assertEquals(3, streams.size)
            val stream = streams.first()
            val headers = stream.behaviorHints.proxyHeaders!!.request!!
            val result = httpRequestRaw("GET", stream.playableDirectUrl!!, headers, "")
            assertEquals(200, result.status)
            assertEquals("authorized-fixture-bytes", result.body)
            val request = fixture.requests.last()
            assertEquals("fixture=one%2Btwo&session=local", request.query)
            headers.forEach { (name, value) -> assertEquals(value, request.headers[name.lowercase()]) }
            assertTrue(fixture.requests.filter { it.path.endsWith(".json") }.all { it.query == "config=one%2Btwo" })
            assertFails { fetchAddonResponseText("${fixture.baseUrl}/error") }
            assertEquals(manifest.id, AddonManifestParser.parse(manifestUrl, fetchAddonResponseText(manifestUrl, true)).id)
            assertEquals("no-cache", fixture.requests.last().headers["cache-control"])
        }
    }

    @Test fun actualQuickJsFetchPromisesHeadersReloadAndRecovery() = runBlocking {
        Stage3Fixture().use { fixture ->
            suspend fun execute(code: String) = PluginRuntime.executePlugin(
                code, "stage3:video", "movie", null, null, "stage3-local-fixture")
            repeat(5) {
                val streams = execute(fixture.fixture("scraper.js"))
                assertEquals(2, streams.size)
                assertEquals("${fixture.baseUrl}/redirect?fixture=one%2Btwo&session=local", streams.first().url)
                assertEquals("Bearer fixture-only", streams.first().headers?.get("Authorization"))
                assertTrue(execute("module.exports.getStreams = async function() { throw new Error('fixture'); };").isEmpty())
                assertTrue(execute("module.exports.getStreams = async function() { await fetch('${fixture.baseUrl}/error').then(function(r) { if (!r.ok) throw new Error('http'); }); };").isEmpty())
                assertFails { execute("throw new Error('initialization fixture');") }
            }
            assertEquals(2, execute(fixture.fixture("scraper.js")).size)
        }
    }
}
