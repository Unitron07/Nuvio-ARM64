package com.nuvio.app.features.updater

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ForkArm64UpdatesTest {
    private fun release(tag: String = "0.1.33-alpha+arm64.1", name: String = "Nuvio-ARM64-Windows-arm64-$tag.msi",
                        digest: String? = "sha256:" + "a".repeat(64), url: String? = null) = GitHubReleaseDto(
        tagName = tag, htmlUrl = "${ForkArm64Updates.releaseRoot}/tag/$tag",
        assets = listOf(GitHubAssetDto(name, url ?: "${ForkArm64Updates.releaseRoot}/download/$tag/$name", 123, digest)),
    )

    @Test fun arm64CandidateIsInformational() {
        val result = ForkArm64Updates.select(listOf(release()), UpdateChannel.BETA)!!
        assertTrue(result.informationalOnly)
        assertEquals("0.1.33-alpha+arm64.1", result.tag)
    }

    @Test fun rejectsOnlyX64AndUnknownOrMissingAssets() {
        for (name in listOf("Nuvio-Windows-x64-0.1.33-alpha.msi", "setup.exe", "Nuvio-arm64.zip")) {
            assertNull(ForkArm64Updates.select(listOf(release(name = name)), UpdateChannel.BETA))
        }
        assertNull(ForkArm64Updates.select(listOf(release().copy(assets = emptyList())), UpdateChannel.BETA))
        assertNull(ForkArm64Updates.select(emptyList(), UpdateChannel.BETA))
    }

    @Test fun rejectsInvalidDigestAndUntrustedLinks() {
        for (digest in listOf(null, "sha256:garbage", "md5:" + "a".repeat(32))) {
            assertNull(ForkArm64Updates.select(listOf(release(digest = digest)), UpdateChannel.BETA))
        }
        assertNull(ForkArm64Updates.select(listOf(release(url = "https://evil.example/setup.msi")), UpdateChannel.BETA))
        assertNull(ForkArm64Updates.select(listOf(release().copy(htmlUrl = "https://evil.example")), UpdateChannel.BETA))
        assertNull(ForkArm64Updates.select(listOf(release().copy(draft = true)), UpdateChannel.BETA))
    }

    @Test fun respectsChannelsEvenWhenAlphaIsMislabeledStableByGitHub() {
        assertNull(ForkArm64Updates.select(listOf(release()), UpdateChannel.STABLE))
        assertEquals("0.1.33+arm64.1", ForkArm64Updates.select(listOf(release("0.1.33+arm64.1")), UpdateChannel.STABLE)?.tag)
        assertNull(ForkArm64Updates.select(listOf(release("0.1.33+arm64.1").copy(prerelease = true)), UpdateChannel.STABLE))
    }

    @Test fun semanticOrderingAndDownstreamRevisionAreNumeric() {
        assertTrue(ForkArm64Version.isRemoteNewer("0.1.33-alpha+arm64.10", "0.1.33-alpha+arm64.2"))
        assertTrue(ForkArm64Version.isRemoteNewer("0.1.33-alpha+arm64.1", "0.1.33-alpha"))
        assertTrue(ForkArm64Version.isRemoteNewer("0.1.33+arm64.1", "0.1.33-alpha+arm64.99"))
        assertFalse(ForkArm64Version.isRemoteNewer("0.1.32+arm64.99", "0.1.33-alpha+arm64.1"))
        assertFalse(ForkArm64Version.isRemoteNewer("0.1.33-alpha+arm64.1", "0.1.33-alpha+arm64.1"))
        assertFalse(ForkArm64Version.isRemoteNewer("0.1.33-alpha+arm64.1", "0.1.33-alpha+arm64.2"))
        assertFalse(ForkArm64Version.isRemoteNewer("invalid", "0.1.33-alpha+arm64.1"))
        assertFalse(ForkArm64Version.isRemoteNewer("0.1.33-alpha+arm64.1", "unknown"))
        val selected = ForkArm64Updates.select(listOf(release("0.1.33-alpha+arm64.2"), release("0.1.33-alpha+arm64.10")), UpdateChannel.ALL_RELEASES)
        assertEquals("0.1.33-alpha+arm64.10", selected?.tag)
    }

    @Test fun malformedForkVersionsFailClosed() {
        for (value in listOf("0.1.33", "0.1.33+arm64.-1", "0.1.33+arm64.01", "0.1.33+arm64.999999999999999999999", "oops+arm64.1")) {
            assertNull(ForkArm64Version.parse(value))
        }
    }

    @Test fun forkDiscoveryUsesListEndpointForEveryChannel() {
        for (channel in UpdateChannel.entries) assertEquals("releases?per_page=100", AppUpdaterRepository.releasePath(channel, true))
    }
}
