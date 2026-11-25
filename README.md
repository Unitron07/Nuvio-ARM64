# Nuvio-ARM64

A native Windows on ARM build of [Nuvio Desktop](https://github.com/NuvioMedia/NuvioDesktop)
for Snapdragon X PCs such as the Surface Pro 11. It follows upstream closely and
changes only what Windows ARM64 needs. This is an unofficial fork: report ARM64
problems [here](https://github.com/Unitron07/Nuvio-ARM64/issues), not upstream.

Upstream's Windows MSI is x64 only and runs under emulation on ARM. This fork ships
a native ARM64 app: ARM64 JDK, libmpv/FFmpeg, QuickJS JNI bridge, Skiko and
WebView2 player bridge.

## Install

1. Download `Nuvio-ARM64-Windows-arm64-<version>.msi` from
   [Releases](https://github.com/Unitron07/Nuvio-ARM64/releases).
2. Check the file against the SHA-256 published with the release:
   `Get-FileHash .\Nuvio-ARM64-Windows-arm64-<version>.msi`.
3. Run the MSI. It installs for the current user only.

The package is **not code-signed**, so SmartScreen will warn on first run
(**More info → Run anyway**). It installs as `Nuvio-ARM64`, separately from
official Nuvio, but both use the same `%APPDATA%\Nuvio` profile. Don't run the two
at the same time.

## Updates

The in-app update check looks only at this repository's releases and tells you
when a newer ARM64 build is available. It never installs automatically; download
the new MSI and run it over the old one. Versions look like `0.1.38-alpha+arm64.1`:
the upstream version, then the fork revision.

## What's different from upstream

- Native Windows ARM64 runtime and MSI packaging (jpackage + WiX), with its own
  install identity.
- Touch: drag-to-scroll with momentum on lists, grids, shelves and menus.
- ARM64 media runtime and player diagnostics, validated on a Surface Pro 11.
- Automation that proposes upstream merges every six hours and resolves routine
  conflicts ([how it works](docs/arm64/STAGE6_UPSTREAM_SYNC.md)).

## Status and known limits

Alpha, like upstream. Builds are unsigned. Licensing review of the bundled native
dependencies (libmpv, FFmpeg) and the credential-storage review are still open;
see [release gates](docs/arm64/STAGE6_ARM64_RELEASES.md#public-release-blockers-all-open).
Open follow-ups are tracked in [issues](https://github.com/Unitron07/Nuvio-ARM64/issues).

## Build from source on Windows ARM64

You need an ARM64 JDK 21, MSVC ARM64 tools, the Windows SDK, the WebView2 SDK and
ARM64 builds of libmpv and the QuickJS JNI bridge. See
[build preparation](docs/arm64/WINDOWS_ARM64_SURFACE.md#build-preparation).
Release packages come from the `windows-arm64-release.yml` workflow
([procedure](docs/arm64/STAGE6_ARM64_RELEASES.md#controlled-build-procedure)).

All fork design notes and validation records are in [docs/arm64](docs/arm64/README.md).

---

*The rest of this README is upstream's, unchanged.*

<div align="center">

  <img src="https://nuvio.tv/assets/nuvio-app-logo-wordmark.webp" alt="Nuvio" width="320" />

  <p>
    A free, open-source media app for Windows, macOS, and Linux.
    <br />
    Bring your own sources. Nuvio turns them into a library with artwork, ratings, subtitles, and your place saved on every screen.
  </p>

  [Website](https://nuvio.tv) · [GitHub releases](https://github.com/NuvioMedia/NuvioDesktop/releases/latest) · [Support Nuvio](https://nuvio.tv/support)

</div>

## ⚠️ Alpha Software - Slow Development - Testers Only

Nuvio Desktop is currently in alpha and is intended only for testers. It is under development and is not suitable for daily use.

Expect breaking changes with every update. Features, settings, stored data, and compatibility may change or stop working without notice. Do not rely on this build as your primary media app, and report any issues you encounter during testing.

## Get Nuvio Desktop

Download the latest build from [GitHub Releases](https://github.com/NuvioMedia/NuvioDesktop/releases/latest).

- Windows: MSI installer
- macOS: DMG installer
- Linux: DEB, RPM, Flatpak, or AppImage

## Build from source

```bash
git clone https://github.com/NuvioMedia/NuvioDesktop.git
cd NuvioDesktop
```

### Run the app

```bash
./gradlew :composeApp:run
```

On Windows PowerShell:

```powershell
.\gradlew.bat :composeApp:run
```

### Package the app

Build a release package for the current host:

```bash
./gradlew :composeApp:packageReleaseDistributionForCurrentOS
```

For platform-specific packaging:

Windows PowerShell:

```powershell
.\gradlew.bat :composeApp:packageReleaseMsi --rerun-tasks
```

macOS:

```bash
./scripts/build-macos-release-dmgs.sh --package-only
```

Linux:

```bash
./gradlew :composeApp:packageReleaseDeb
```

The shared app is built with Kotlin Multiplatform and Compose Multiplatform.

## License

[GNU General Public License v3.0](./LICENSE)
