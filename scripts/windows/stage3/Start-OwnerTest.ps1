param(
    [Parameter(Mandatory=$true)][string]$ImageDirectory,
    [Parameter(Mandatory=$true)][string]$RunDirectory,
    [switch]$Resume,
    [switch]$NativeSmoke
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
try {
    foreach ($name in $overrides.Keys) {
        $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        [Environment]::SetEnvironmentVariable($name, $overrides[$name], 'Process')
    }
    New-Item -ItemType Directory -Force -Path $env:APPDATA,$env:LOCALAPPDATA,$env:WEBVIEW2_USER_DATA_FOLDER | Out-Null
    $arguments = @{FilePath=$launcher[0].FullName; WorkingDirectory=$image; PassThru=$true;
        RedirectStandardOutput=(Join-Path $private "$session.stdout.log");
        RedirectStandardError=(Join-Path $private "$session.stderr.log")}
    if ($NativeSmoke) { $arguments.ArgumentList = '--native-runtime-smoke'; $arguments.WindowStyle = 'Hidden' }
    Write-Host 'Launching the isolated test profile. Close Nuvio normally when finished.'
    $process = Start-Process @arguments
} finally {
    foreach ($name in $saved.Keys) { [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process') }
}
$started = [DateTime]::UtcNow
$process.WaitForExit()
$process.Refresh()
$crashes = @(Get-ChildItem -LiteralPath $private -File -Filter 'hs_err_pid*.log')
$events = @(Get-WinEvent -FilterHashtable @{LogName='Application'; Id=1000,1001,1002; StartTime=$started.ToLocalTime()} -ErrorAction SilentlyContinue |
    Where-Object { $_.Message -match '(?i)nuvio|player_bridge|libmpv|java' } |
    ForEach-Object { @{id=$_.Id; timeUtc=$_.TimeCreated.ToUniversalTime().ToString('o'); provider=$_.ProviderName} })
[ordered]@{schema='nuvio-stage3-session-v1'; startedUtc=$started.ToString('o'); endedUtc=[DateTime]::UtcNow.ToString('o');
    mode=$(if ($NativeSmoke) {'native-prerequisites'} else {'application'}); exitCode=$process.ExitCode;
    launcherSha256=(Get-FileHash -LiteralPath $launcher[0].FullName -Algorithm SHA256).Hash;
    osBuild=[Environment]::OSVersion.Version.ToString(); osArchitecture='Arm64';
    privateCrashReportCount=$crashes.Count; relevantEventSummaries=$events;
    validation='Process exit is diagnostic evidence; acceptance statuses require owner observations.'} |
    ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $run "$session.session.json")
Write-Host "Exit code: $($process.ExitCode). Edit results.json with observations and evidence; raw logs stay in private/."
& "$PSScriptRoot/Collect-OwnerResults.ps1" -RunDirectory $run
