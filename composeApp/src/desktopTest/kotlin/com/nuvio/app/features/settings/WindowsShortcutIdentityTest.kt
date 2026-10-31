package com.nuvio.app.features.settings

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WindowsShortcutIdentityTest {
    private fun locations(arch: String) = windowsShortcutLocations(
        Path.of("desktop"), Path.of("programs"), Path.of("data"),
        Path.of("common-desktop"), Path.of("common-programs"), arch,
    ).map { it.toString().replace('\\', '/') }

    @Test fun arm64CustomizationCannotTargetOfficialShortcuts() {
        for (arch in listOf("aarch64", "arm64", "ARM64")) {
            val targets = locations(arch)
            assertEquals(7, targets.size)
            assertTrue(targets.all { it.endsWith("Nuvio-ARM64.lnk") })
            assertTrue(targets.none { it.endsWith("/Nuvio.lnk") || it.contains("/Nuvio/") })
            assertEquals("Nuvio-ARM64", windowsShortcutApplicationName(arch))
        }
    }

    @Test fun x64RetainsExistingShortcutLocations() {
        assertEquals(listOf("desktop/Nuvio.lnk", "programs/Nuvio.lnk", "programs/Nuvio/Nuvio.lnk",
            "data/Microsoft/Internet Explorer/Quick Launch/User Pinned/TaskBar/Nuvio.lnk",
            "common-desktop/Nuvio.lnk", "common-programs/Nuvio/Nuvio.lnk", "common-programs/Nuvio.lnk"), locations("amd64"))
    }

    @Test fun absentWindowsFoldersAreIgnored() {
        assertEquals(emptyList(), windowsShortcutLocations(null, null, null, null, null, "arm64"))
    }
}
