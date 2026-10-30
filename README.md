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

## Windows ARM64 development

**Core touchscreen UX validated on Surface Pro 11 — ready for merge, subject to final checks.**
See the [Stage 4 final validation ledger](STAGE4_FINAL_VALIDATION.md) for owner-reported
touchscreen results, bounded artwork caching, A/B observations and remaining limits.
PR #9 is prepared for owner review; it is not merged and no release is published.

Stage 3 PR #4 is merged: native ARM64 launch,
local addons, account sign-in/synchronization and real debrid playback are
owner-confirmed on Surface Pro 11. Automated checks and owner reports are recorded
separately; remaining hardening work is in the
[Stage 4 roadmap](STAGE4_ROADMAP.md). See the
[ARM64 build notes](WINDOWS_ARM64_SURFACE.md) and
[Stage 3 tests, evidence and Surface owner procedure](STAGE3_ARM64_VALIDATION.md).

## License

[GNU General Public License v3.0](./LICENSE)
