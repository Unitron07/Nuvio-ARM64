package com.nuvio.app.stage3

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.features.addons.AddonStorage
import com.nuvio.app.features.plugins.PluginStorage
import com.nuvio.app.features.debrid.DebridSettingsStorage
import com.nuvio.app.features.settings.ThemeSettingsStorage
import com.nuvio.app.features.watchprogress.WatchProgressStorage
import java.nio.file.Files
import kotlin.test.*

class Stage3PersistenceTest {
    @Test fun installedAddonsPluginConfigurationSettingsProgressAndSyntheticCredentialsReload() {
        // The Gradle desktopTest task supplies a dedicated profile before this JVM starts.
        assertTrue(DesktopStorage.rootDir.toString().contains("stage3-test-profile"))
        val urls = listOf("http://127.0.0.1:19876/manifest.json?fixture=local")
        AddonStorage.saveInstalledAddonUrls(1, urls)
        AddonStorage.saveAddonEnabledStates(1, mapOf(urls.single() to true))
        PluginStorage.saveScraperSettings("stage3-fixture", "{\"quality\":\"1080p\"}")
        ThemeSettingsStorage.saveSelectedTheme("dark")
        DebridSettingsStorage.saveRealDebridApiKey("synthetic-fixture-only")
        WatchProgressStorage.savePayload(1, "{\"stage3:video\":{\"positionMs\":12500}}")
        listOf("nuvio_addons", "nuvio_plugins", "nuvio_theme_settings", "nuvio_debrid_settings", "nuvio_watch_progress")
            .forEach { DesktopStorage.store(it).clearInMemory() }
        assertEquals(urls, AddonStorage.loadInstalledAddonUrls(1))
        assertEquals(mapOf(urls.single() to true), AddonStorage.loadAddonEnabledStates(1))
        assertEquals("{\"quality\":\"1080p\"}", PluginStorage.loadScraperSettings("stage3-fixture"))
        assertEquals("dark", ThemeSettingsStorage.loadSelectedTheme())
        assertEquals("synthetic-fixture-only", DebridSettingsStorage.loadRealDebridApiKey())
        assertEquals("{\"stage3:video\":{\"positionMs\":12500}}", WatchProgressStorage.loadPayload(1))
        DebridSettingsStorage.saveRealDebridApiKey("")
        DesktopStorage.store("nuvio_debrid_settings").clearInMemory()
        assertEquals("", DebridSettingsStorage.loadRealDebridApiKey())
    }

    @Test fun newStoreInstanceReadsAtomicReplacementAndCorruptionDoesNotExposePartialState() {
        val directory = Files.createTempDirectory("nuvio-stage3-store")
        val file = directory.resolve("preferences.properties")
        try {
            DesktopStorage.Store(file).apply { putString("installed", "fixture"); putInt("position", 12500) }
            DesktopStorage.Store(file).apply {
                assertEquals("fixture", getString("installed"))
                assertEquals(12500, getInt("position"))
                putInt("position", 20000)
            }
            assertEquals(20000, DesktopStorage.Store(file).getInt("position"))
            Files.list(directory).use { assertEquals(1L, it.count()) }
            val corrupt = "partial=must-not-leak\nbroken=\\uZZZZ\n".toByteArray()
            Files.write(file, corrupt)
            val damaged = DesktopStorage.Store(file)
            assertNull(damaged.getString("partial"))
            assertContentEquals(corrupt, Files.readAllBytes(file))
            // Explicit user save recovers; a failed read itself never rewrites the file.
            damaged.putString("recovered", "yes")
            assertEquals("yes", DesktopStorage.Store(file).getString("recovered"))
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(directory)
        }
    }
}
