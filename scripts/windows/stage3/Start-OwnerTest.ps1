param(
    [Parameter(Mandatory=$true)][string]$ImageDirectory,
    [Parameter(Mandatory=$true)][string]$RunDirectory,
    [switch]$Resume,
    [switch]$NativeSmoke,
    [switch]$FixtureLogs
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$image = (Resolve-Path -LiteralPath $ImageDirectory).Path
$run = [IO.Path]::GetFullPath($RunDirectory)
if (-not [Runtime.InteropServices.RuntimeInformation]::OSArchitecture.Equals([Runtime.InteropServices.Architecture]::Arm64)) {
    throw 'Owner testing requires native ARM64 Windows.'
}
$launcher = @(Get-ChildItem -LiteralPath $image -File -Filter '*.exe')
if ($launcher.Count -ne 1) { throw 'ImageDirectory must contain exactly one app launcher.' }
$imageManifestPath = Join-Path $image 'STAGE3_IMAGE.json'
if (-not (Test-Path -LiteralPath $imageManifestPath)) { throw 'Use a Stage 3 CI image with STAGE3_IMAGE.json. Older images lack native test-data isolation.' }
$imageManifest = Get-Content -LiteralPath $imageManifestPath -Raw | ConvertFrom-Json
if ($imageManifest.schema -ne 'nuvio-stage3-image-v1' -or $imageManifest.sourceCommit -notmatch '^[a-f0-9]{40}$' -or
    -not $imageManifest.nativeTestDataIsolation -or -not $imageManifest.typedHttpHeaders) { throw 'Image does not declare the required Stage 3 isolation/header support.' }
$launcherHash = (Get-FileHash -LiteralPath $launcher[0].FullName -Algorithm SHA256).Hash
if ($imageManifest.launcherSha256 -ne $launcherHash) { throw 'Launcher does not match Stage 3 image manifest.' }
$manifestPath = Join-Path $run 'results.json'
if (Test-Path -LiteralPath $run) {
    if (-not $Resume -or -not (Test-Path -LiteralPath $manifestPath)) { throw 'Use a new RunDirectory, or -Resume with an existing Stage 3 run.' }
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    if ($manifest.schema -ne 'nuvio-stage3-v1') { throw 'Not a Stage 3 test directory.' }
} else {
    if ($Resume) { throw 'Cannot resume a missing run.' }
    New-Item -ItemType Directory -Path $run | Out-Null
    $checks = @('app_launch','addon_catalog_metadata','addon_stream_discovery','url_headers_to_mpv',
        'http_https_application_playback','navigation_repeated_sessions','state_persistence',
        'provider_automated_mocks','real_provider_owner_test','arm64_native_ci','x64_compatibility',
        'owner_package','accurate_documentation') | ForEach-Object {
        [ordered]@{id=$_; status='pending'; evidence=''}
    }
    [ordered]@{schema='nuvio-stage3-v1'; createdUtc=[DateTime]::UtcNow.ToString('o');
        acceptance=$checks; notes='Only mark pass with evidence. No credentials or complete stream URLs.'} |
        ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $manifestPath
}
$private = Join-Path $run 'private'
New-Item -ItemType Directory -Force -Path $private | Out-Null
$session = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ')
& "$PSScriptRoot/../audit-arm64-image.ps1" -ImageDirectory $image > (Join-Path $run 'architecture-audit.txt')
$saved = @{}
$overrides = @{
    APPDATA=(Join-Path $run 'profile/roaming'); LOCALAPPDATA=(Join-Path $run 'profile/local');
    WEBVIEW2_USER_DATA_FOLDER=(Join-Path $run 'profile/webview2');
    NUVIO_DESKTOP_TEST_DATA_DIR=(Join-Path $run 'profile/native');
    JAVA_TOOL_OPTIONS=('-XX:ErrorFile="' + (Join-Path $private 'hs_err_pid%p.log') + '"');
    NUVIO_DESKTOP_SMOKE_PLAYER_URL=$null; NUVIO_DESKTOP_SMOKE_REPORT=$null
}
$started = [DateTime]::UtcNow
$stdoutTarget = $null
$stderrTarget = $null
try {
    foreach ($name in $overrides.Keys) {
        $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        [Environment]::SetEnvironmentVariable($name, $overrides[$name], 'Process')
    }
    New-Item -ItemType Directory -Force -Path $env:APPDATA,$env:LOCALAPPDATA,$env:WEBVIEW2_USER_DATA_FOLDER | Out-Null
    $process = [Diagnostics.Process]::new()
    $process.StartInfo.FileName = $launcher[0].FullName
    $process.StartInfo.WorkingDirectory = $image
    $process.StartInfo.UseShellExecute = $false
    $process.StartInfo.RedirectStandardOutput = $true
    $process.StartInfo.RedirectStandardError = $true
    $process.StartInfo.CreateNoWindow = [bool]$NativeSmoke
    if ($NativeSmoke) { $process.StartInfo.ArgumentList.Add('--native-runtime-smoke') }
    # Upstream logs can contain configured addon URLs. Discard console streams
    # by default; opt-in raw logging is only for synthetic local fixture sessions.
    $capture = $NativeSmoke -or $FixtureLogs
    $stdoutTarget = if ($capture) { [IO.File]::Create((Join-Path $private "$session.stdout.log")) } else { [IO.Stream]::Null }
    $stderrTarget = if ($capture) { [IO.File]::Create((Join-Path $private "$session.stderr.log")) } else { [IO.Stream]::Null }
    Write-Host 'Launching the isolated test profile. Close Nuvio normally when finished.'
    if (-not $process.Start()) { throw 'Application process did not start.' }
    $stdoutDrain = $process.StandardOutput.BaseStream.CopyToAsync($stdoutTarget)
    $stderrDrain = $process.StandardError.BaseStream.CopyToAsync($stderrTarget)
} catch {
    if ($null -ne $stdoutTarget) { $stdoutTarget.Dispose() }
    if ($null -ne $stderrTarget) { $stderrTarget.Dispose() }
    throw
} finally {
    foreach ($name in $saved.Keys) { [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process') }
}
$process.WaitForExit()
try {
    $stdoutDrain.GetAwaiter().GetResult()
    $stderrDrain.GetAwaiter().GetResult()
} finally {
    $stdoutTarget.Dispose()
    $stderrTarget.Dispose()
}
$process.Refresh()
$crashes = @(Get-ChildItem -LiteralPath $private -File -Filter 'hs_err_pid*.log' | Where-Object LastWriteTimeUtc -ge $started)
$events = @(Get-WinEvent -FilterHashtable @{LogName='Application'; Id=1000,1001,1002; StartTime=$started.ToLocalTime()} -ErrorAction SilentlyContinue |
    Where-Object { $_.Message -match '(?i)nuvio|player_bridge|libmpv|java' } |
    ForEach-Object { @{id=$_.Id; timeUtc=$_.TimeCreated.ToUniversalTime().ToString('o'); provider=$_.ProviderName} })
[ordered]@{schema='nuvio-stage3-session-v1'; startedUtc=$started.ToString('o'); endedUtc=[DateTime]::UtcNow.ToString('o');
    mode=$(if ($NativeSmoke) {'native-prerequisites'} else {'application'}); exitCode=$process.ExitCode;
    launcherSha256=$launcherHash; sourceCommit=$imageManifest.sourceCommit;
    osBuild=[Environment]::OSVersion.Version.ToString(); osArchitecture='Arm64';
    privateCrashReportCount=$crashes.Count; relevantEventSummaries=$events;
    validation='Process exit is diagnostic evidence; acceptance statuses require owner observations.'} |
    ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $run "$session.session.json")
Write-Host "Exit code: $($process.ExitCode). Edit results.json with observations and evidence."
& "$PSScriptRoot/Collect-OwnerResults.ps1" -RunDirectory $run
