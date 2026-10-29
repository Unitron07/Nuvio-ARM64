param(
    [Parameter(Mandatory=$true)][string]$QuickJsDll,
    [ValidateSet('aarch64','amd64')][string]$ExpectedArch = 'aarch64'
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repo = [IO.Path]::GetFullPath("$PSScriptRoot/../../..")
$dll = (Resolve-Path -LiteralPath $QuickJsDll).Path
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to JDK 21 for the requested architecture.' }
$properties = & "$env:JAVA_HOME/bin/java.exe" -XshowSettings:properties -version 2>&1 | Out-String
if ($LASTEXITCODE -ne 0 -or $properties -notmatch "os.arch = $ExpectedArch") { throw "Requires a $ExpectedArch JDK." }
$arch = if ($ExpectedArch -eq 'aarch64') { 'arm64' } else { 'x64' }
& "$repo/scripts/windows/verify-pe-architecture.ps1" -Path $dll -Architecture $arch
Push-Location $repo
try {
    if (-not (Test-Path local.properties)) { New-Item -ItemType File local.properties | Out-Null }
    & node --test scripts/windows/stage3/test-fixture.mjs
    if ($LASTEXITCODE -ne 0) { throw 'Fixture self-test failed.' }
    & node scripts/windows/stage3/test-tablet-fixture.mjs
    if ($LASTEXITCODE -ne 0) { throw 'Tablet fixture self-test failed.' }
    & node scripts/windows/stage3/test-scroll-metrics.mjs
    if ($LASTEXITCODE -ne 0) { throw 'Scroll metrics analyzer test failed.' }
    & ./gradlew.bat :composeApp:desktopTest `
        --tests 'com.nuvio.app.stage3.*' `
        --tests 'com.nuvio.app.core.ui.DesktopVerticalDragScrollTest' `
        --tests 'com.nuvio.app.core.ui.StreamListTouchTest' `
        --tests 'com.nuvio.app.core.ui.ScrollDiagnosticsTest' `
        --tests 'com.nuvio.app.core.ui.ScaledArtworkCacheTest' `
        --tests 'com.nuvio.app.core.storage.DesktopStorageTest' `
        --tests 'com.nuvio.app.features.plugins.PluginRuntimeDesktopTest' `
        --tests 'com.nuvio.app.features.player.desktop.NativePlayerControllerTeardownTest' `
        --tests 'com.nuvio.app.features.player.PlayerScreenRuntimeStateTest' `
        --tests 'com.nuvio.app.features.debrid.*' `
        -x verifyWindowsArm64Inputs -x buildWindowsPlayerBridge `
        -x prepareWindowsPlayerRuntime -x generateWindowsPlayerRuntimeIndex `
        "-Pnuvio.windows.quickjs.dll=$dll" `
        '-Dorg.gradle.jvmargs=-Xmx6g' '-Pkotlin.compiler.execution.strategy=in-process' `
        --max-workers=2 --no-configuration-cache --no-daemon --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Desktop integration tests failed. See composeApp/build/reports/tests/desktopTest.' }
} finally { Pop-Location }
