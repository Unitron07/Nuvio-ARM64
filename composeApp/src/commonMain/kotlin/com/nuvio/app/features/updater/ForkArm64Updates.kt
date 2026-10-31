package com.nuvio.app.features.updater

/** Fork releases use upstream SemVer plus +arm64.N; N is compared explicitly. */
internal data class ForkArm64Version(val upstream: SemanticVersion, val revision: Long) : Comparable<ForkArm64Version> {
    override fun compareTo(other: ForkArm64Version): Int =
        upstream.compareTo(other.upstream).takeIf { it != 0 } ?: revision.compareTo(other.revision)

    companion object {
        fun parse(tag: String): ForkArm64Version? {
            val parts = VersionUtils.normalize(tag).split("+arm64.")
            if (parts.size != 2 || !parts[1].matches(Regex("[1-9][0-9]*"))) return null
            val base = VersionUtils.parse(parts[0]) ?: return null
            val revision = parts[1].toLongOrNull() ?: return null
            return ForkArm64Version(base, revision)
        }

        fun isRemoteNewer(remote: String, local: String): Boolean {
            val candidate = parse(remote) ?: return false
            val installed = parse(local) ?: VersionUtils.parse(local)?.let { ForkArm64Version(it, 0) } ?: return false
            return candidate > installed
        }
    }
}

internal object ForkArm64Updates {
    const val releaseRoot = "https://github.com/Unitron07/Nuvio-ARM64/releases"

    fun select(releases: List<GitHubReleaseDto>, channel: UpdateChannel): AppUpdate? = releases
        .filterNot { it.draft }
        .mapNotNull { release ->
            val tag = release.tagName ?: return@mapNotNull null
            val version = ForkArm64Version.parse(tag) ?: return@mapNotNull null
            if (channel == UpdateChannel.STABLE && (release.prerelease || version.upstream.prerelease.isNotEmpty())) {
                return@mapNotNull null
            }
            version to release
        }
        .sortedByDescending { it.first }
        .firstNotNullOfOrNull { (_, release) ->
            val tag = release.tagName!!
            // A single wrong-architecture asset must never become a fallback.
            val name = "Nuvio-ARM64-Windows-arm64-$tag.msi"
            val encodedTag = tag.replace("+", "%2B")
            val encodedName = name.replace("+", "%2B")
            val releaseUrl = "$releaseRoot/tag/$encodedTag"
            if (release.htmlUrl !in listOf(releaseUrl, "$releaseRoot/tag/$tag")) return@firstNotNullOfOrNull null
            val asset = release.assets.singleOrNull {
                it.name == name && (it.size ?: 0) > 0 &&
                    it.browserDownloadUrl in listOf(
                        "$releaseRoot/download/$encodedTag/$encodedName",
                        "$releaseRoot/download/$tag/$name",
                    ) && it.digest?.matches(Regex("sha256:[0-9a-fA-F]{64}")) == true
            } ?: return@firstNotNullOfOrNull null
            AppUpdate(
                tag = tag, title = release.name?.takeIf { it.isNotBlank() } ?: tag,
                notes = release.body.orEmpty(), releaseUrl = releaseUrl,
                assetName = asset.name, assetUrl = asset.browserDownloadUrl, assetSizeBytes = asset.size,
                informationalOnly = true,
            )
        }
}
