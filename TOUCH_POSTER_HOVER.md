# Poster previews and touch input

Base: `main` at `b29cfa75c437da9cece80b197c86e813378473b0` (includes
merged Stage 6 / PR #17 and MSI-candidate PR #18).

## Root cause

`HomePosterHoverPreview` used `Modifier.hoverable` and
`collectIsHoveredAsState` on both the poster and its popup. Pointer entry could
start the delayed preview without establishing mouse hover intent, and a press
or drag did not explicitly cancel the pending preview. The shared
`desktopPosterHoverScale` used the same generic hover mechanism.

On desktop, touch can be presented as mouse input. This is also the premise of
the existing Stage 4 drag/scroll handlers. Checking `PointerType.Mouse` alone
would therefore leave the promoted-touch path open. See JetBrains' description
of [desktop pointer behavior](https://kotlinlang.org/docs/multiplatform/compose-platform-specifics.html).

## Fix

`posterMouseHover` observes pointer events in the Initial pass without consuming
them. A poster needs unpressed Mouse movement beyond the framework's touch slop
before it can arm hover. Entry, focus, press, release, scrolling, and non-Mouse
input cannot arm it. Press/drag/scroll/non-Mouse input clears hover; release alone
does not rearm it. Fresh unpressed mouse movement restores hover on hybrid devices.
Movement is measured against a stored root position, since hover events may not
retain `previousPosition`. Shelf relayout under a stationary cursor cannot count
as movement. Popup entry permission is also cleared by direct input, preventing
synthetic re-entry after a tap from restoring hover.

The preview wrapper cancels pending activation and dismisses its popup when the
poster receives direct input. A legitimately mounted popup accepts mouse entry
to preserve transfer from the poster into its actions. Mouse actions remain
mounted through press/release, including a held click; actual touch or wheel
input on the popup dismisses it. The existing open delay, close grace period,
animation, layout, trailer settings and actions remain in use.

The shared scale helper uses the same intent guard. Existing poster click,
long-press, secondary-click, keyboard and Stage 4 scrolling handlers are retained.

Mouse behavior refinement: entering a poster and stopping immediately no longer
opens a preview. Move within it beyond touch slop, then pause for the configured
delay. This is necessary for the promoted-touch fallback, where source modality
is unavailable to this shared Compose handler.

## Affected views

- Home catalog shelves (`HomePosterCard`).
- Search result shelves (`HomeCatalogRowSection` / `HomePosterCard`).
- Discover grids (`PosterGridTile`).
- Expanded catalogs, including landscape tiles (`CatalogScreen`).
- Library posters, folder grids and detail poster rails.
- Other callers of `desktopPosterHoverScale`, including continue-watching and
  shared shelf artwork, receive the same scale-only input guard.

## Files

- `composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/PosterMouseHover.kt`
- `composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/PosterLift.kt`
- `composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/ShelfComponents.kt`
- `composeApp/src/commonMain/kotlin/com/nuvio/app/features/home/components/HomePosterHoverPreview.kt`
- `composeApp/src/desktopTest/kotlin/com/nuvio/app/core/ui/PosterMouseHoverTest.kt`
- `composeApp/src/desktopTest/kotlin/com/nuvio/app/features/home/components/HomePosterHoverPreviewTest.kt`
- `scripts/windows/stage3/run-tests.ps1` (includes both new suites and existing
  `PosterCardClickTest` in ARM64/x64 integration CI).
- This document.

## Validation

Final local run: **30 tests passed in six suites, zero failures/errors/skips** on
Windows ARM64 with Microsoft JDK 21. Desktop Kotlin compilation passed as part of
the same Gradle build. Tests use the actual pointer modifier and preview wrapper
with the shared poster scale, plus existing click and Stage 4 regressions. Input
injection covers both Touch and promoted Mouse gestures.

| Suite | Passed |
| --- | ---: |
| PosterMouseHoverTest | 10 |
| HomePosterHoverPreviewTest | 5 |
| PosterCardClickTest | 4 |
| DesktopVerticalDragScrollTest | 6 |
| TouchCleanupTest | 4 |
| StreamListTouchTest | 1 |

Coverage includes deliberate mouse hover and recovery after direct input,
mouse transfer into the popup and held-click activation of Details, pending
preview cancellation, repeated touch/promoted taps, vertical/horizontal dragging,
wheel scrolling and popup dismissal, keyboard Enter, long press, secondary click,
fling, nested shelf scrolling, sidebar intent and genre-menu selection.

The integration runner's PowerShell parse and `git diff --check` also passed.
Local validation used Gradle's isolated desktop test profile. x64 CI execution
has not been performed for this branch; the runner includes the new suites for
both architectures.

The selected local test invocation was:

```powershell
./gradlew.bat :composeApp:desktopTest `
    --tests 'com.nuvio.app.core.ui.PosterMouseHoverTest' `
    --tests 'com.nuvio.app.features.home.components.HomePosterHoverPreviewTest' `
    --tests 'com.nuvio.app.core.ui.PosterCardClickTest' `
    --tests 'com.nuvio.app.core.ui.DesktopVerticalDragScrollTest' `
    --tests 'com.nuvio.app.core.ui.TouchCleanupTest' `
    --tests 'com.nuvio.app.core.ui.StreamListTouchTest' `
    -x verifyWindowsArm64Inputs -x buildWindowsPlayerBridge `
    -x prepareWindowsPlayerRuntime -x generateWindowsPlayerRuntimeIndex `
    '-Pnuvio.windows.quickjs.dll=PATH_TO_EXISTING_ARM64_LIBQUICKJS_DLL' `
    '-Dorg.gradle.jvmargs=-Xmx4g' '-Pkotlin.compiler.execution.strategy=in-process' `
    --max-workers=2 --no-configuration-cache --no-daemon --offline --console=plain
```

The DLL value above is a placeholder for the existing ARM64 test library used
locally. The selected tests do not validate QuickJS or native player operations.

Physical Surface acceptance remains pending. Synthetic input cannot establish
the exact Windows/AWT sequence emitted by every touchscreen driver. The shared
handler intentionally uses hover intent rather than introducing a native input
hook; an OS that emits substantial unpressed mouse movement after touch release
could still resemble real mouse use.

Repeat on a matching ARM64 build, with hover previews enabled:

- Vertically scroll Home and drag directly across posters; horizontally scroll
  shelves in both directions. Pause longer than the configured preview delay.
- Repeatedly tap posters; verify details opens once per stationary tap. Return
  and verify no leftover preview. Check long-press actions.
- Repeat on Search, Discover, expanded catalogs, Library, folders and detail rails.
- With a mouse, move into/within a poster and pause; verify preview and optional
  trailer. Move into the popup and click Details, watched and saved actions.
- Switch between mouse and touch. Verify touch clears pending/existing hover,
  release stays collapsed, and fresh unpressed mouse movement restores previews.
- Check wheel scrolling, keyboard Tab/Enter, and the Stage 4 sidebar, genre menu,
  nested shelf scrolling and fling behavior.
