package com.nuvio.app.features.updater

import com.nuvio.app.core.build.AppVersionConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopReleaseSourceTest {
    @Test
    fun forkBuildOnlyDiscoversForkReleases() {
        val source = desktopReleaseSource(forkBuild = true)
        assertEquals("Unitron07" to "Nuvio-ARM64", source.owner to source.repo)
        assertTrue(source.forkArm64)
    }

    @Test
    fun upstreamBuildKeepsUpstreamReleases() {
        val source = desktopReleaseSource(forkBuild = false)
        assertEquals("NuvioMedia" to "NuvioDesktop", source.owner to source.repo)
        assertFalse(source.forkArm64)
    }

    @Test
    fun runtimeSourceFollowsTheBuildNotTheJvmArchitecture() {
        val expected = desktopReleaseSource(AppVersionConfig.DESKTOP_FORK_WINDOWS_ARM64)
        assertEquals(expected, AppUpdaterPlatform.releaseSource)
    }

    @Test
    fun forkFlagMatchesForkVersionIdentity() {
        // The fork build script appends +arm64.N; upstream versions never carry it.
        assertEquals(
            AppVersionConfig.DESKTOP_FORK_WINDOWS_ARM64,
            AppVersionConfig.DESKTOP_VERSION_NAME.contains("+arm64."),
        )
    }
}
