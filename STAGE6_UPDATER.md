# Stage 6 — fork updater

## Verified call path

`AppUpdaterController.ensureAutoCheckStarted()` / `checkForUpdates()` call
`AppUpdaterRepository.getLatestChannelUpdate()`, using `httpRequestRaw` against
GitHub's REST API. `releasePath`, `selectUpdate`, `ReleaseSelector` and
`VersionUtils` control discovery and comparison. Desktop formerly forced
ALL_RELEASES, took the newest returned release with permissive asset fallback,
and used `isRemoteNewerLegacy`. `downloadUpdateAsset` streamed a file;
`installDownloadedUpdate` called `launchInstaller`, `windowsInstallerCommand`
(`msiexec /i` for MSI), then scheduled process exit. This was verified in code,
not inferred from README links. Original download did not verify checksums.

## Windows ARM64 policy

A native aarch64/arm64 JVM on Windows selects Unitron07/Nuvio-ARM64. Other
platforms retain their upstream release source and distribution behavior. An x64
JVM running under Windows ARM emulation remains the x64 distribution; this does
not constitute a native ARM64 install. Unsupported Windows JVM architectures do
not enable updates.

ARM64 fetches the first 100 releases for all channels, with semantic sorting
rather than relying on latest/publication order. Stable excludes GitHub previews
and semantic prereleases. Beta and All include them but still enforce monotonic
version comparison; legacy changed-label comparison is never used for ARM64.
Older releases outside that 100-item discovery window are not searched (safe
no-update rather than architecture fallback). Missing/empty/no-compatible-release
results use the existing no-update handling; network/API errors use existing
failure feedback. Ignored version and selected channel are stored in a separate
`nuvio_arm64_updater` settings store, without migrating account/profile data.
Existing controller channel-switch cancellation, state reset and ignored-tag
reset remain in place. Dismissing an informational ARM64 banner stores the ignored tag, so it does not
repeat automatically. Forced manual checks may show an ignored update.

`ForkArm64Updates.select()` accepts only an exact name bound to a parsed fork tag:
`Nuvio-ARM64-Windows-arm64-<version>.msi`. It requires a nonzero size, GitHub API
SHA256 digest syntax, and exact fork release-page/download URL. x64-only releases,
foreign hosts, drafts, invalid metadata, missing assets or missing digests never
fall back to another installer. Release notes come from that same release.
`ForkArm64Version` compares upstream SemVer then numeric downstream revision, so
revision 10 exceeds 2, equivalent versions do not repeat, and older upstream
versions cannot be offered as hotfix upgrades. Revision-0 development builds can
transition to revision 1 of the same upstream basis.

## Transition gates

**Phase A implemented:** informational discovery; the action is **View release**
and opens the validated fork release page. Controller guards informational
updates; desktop ARM64 download and installation entry points also reject them.
No MSI is downloaded, executed, or trusted solely because GitHub reports a digest.
A valid digest string is discovery metadata, not file-integrity verification.

**Phase B pending:** physical fresh install/upgrade/profile/rollback testing of
exact candidate bytes plus checksum/signature verification. Do not use a public
release to test this; mocked release metadata tests require no publication.

**Phase C disabled:** automatic install requires a separately reviewed design
that binds release ID/tag/source/asset, verifies downloaded bytes, handles signer
trust and failure, and passes the owner upgrade matrix. Do not just flip the
blocking checks or replace download URLs. No arbitrary update scripts are added.

Original Nuvio attribution/documentation/license links remain. Updater tests in
`ForkArm64UpdatesTest` cover strict assets/URLs/digests, missing release/asset,
stable and preview policy, numeric revisions, invalid/older/equal versions and
repository list selection. Existing controller/repository/network behavior tests
remain applicable; complete fault-injected HTTP/controller tests for ARM64 are
still follow-up work. UI text uses a new resource with normal English fallback.

## Windows regression-suite portability

Adding all desktop updater tests to ARM64/x64 Windows CI exposed three inherited
Linux command-construction tests with hard-coded Unix absolute-path expectations.
Their fixture expectations now use the host's File.absolutePath, matching the
existing command builder; Linux product behavior is unchanged. The suite remains
included on both Windows architectures rather than excluding those tests.
