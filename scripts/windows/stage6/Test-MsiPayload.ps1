param(
    [Parameter(Mandatory=$true)][string]$Msi,
    [Parameter(Mandatory=$true)][string]$ImageDirectory,
    [Parameter(Mandatory=$true)][string]$PackageVersion,
    [string]$ProductName='Nuvio-ARM64',
    [string]$UpgradeCode='febc83be-f241-4a78-8f6c-10af5e358acc',
    [string]$ValidationDirectory='build/stage6-msi-validation',
    [switch]$Fixture
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$Msi=(Resolve-Path -LiteralPath $Msi).Path
$ImageDirectory=(Resolve-Path -LiteralPath $ImageDirectory).Path
$ValidationDirectory=[IO.Path]::GetFullPath($ValidationDirectory)
New-Item -ItemType Directory -Force $ValidationDirectory | Out-Null
if (Get-ChildItem -LiteralPath $ValidationDirectory -Force) { throw 'Use an empty MSI validation directory.' }
$installer=New-Object -ComObject WindowsInstaller.Installer
$db=$installer.OpenDatabase($Msi,0)
function Property([string]$Name) {
    $view=$db.OpenView("SELECT ``Value`` FROM ``Property`` WHERE ``Property``='$Name'")
    $view.Execute(); $record=$view.Fetch()
    if ($null -eq $record) { return '' }
    return $record.StringData(1)
}
$actualName=Property 'ProductName'; $actualVersion=Property 'ProductVersion'
$actualUpgrade=Property 'UpgradeCode'; $productCode=Property 'ProductCode'
$template=$db.SummaryInformation(0).Property(7)
if ($actualName -cne $ProductName -or $actualVersion -cne $PackageVersion -or
    $actualUpgrade.Trim('{}') -ine $UpgradeCode) { throw 'MSI identity mismatch.' }
if ($template -notmatch '^Arm64;') { throw "MSI template is not ARM64: $template" }
if ((Property 'ALLUSERS') -ne '') { throw 'Expected a strictly per-user MSI with no ALLUSERS property.' }
$db=$null; $installer=$null
[GC]::Collect(); [GC]::WaitForPendingFinalizers()
$extract=Join-Path $ValidationDirectory 'extracted'
New-Item -ItemType Directory $extract | Out-Null
$extractLog=Join-Path $ValidationDirectory 'administrative-extraction.log'
$process=Start-Process msiexec.exe -WindowStyle Hidden -PassThru -Wait -ArgumentList @('/a',"`"$Msi`"",'/qn',"TARGETDIR=`"$extract`"",'/l*v',"`"$extractLog`"")
if ($process.ExitCode -ne 0) { throw "MSI administrative extraction failed: $($process.ExitCode). Log remains private CI diagnostic data." }
$launchers=@(Get-ChildItem -LiteralPath $extract -Recurse -File -Filter "$ProductName.exe")
if ($launchers.Count -ne 1) { throw 'Extracted MSI must contain exactly one app launcher.' }
$installed=$launchers[0].Directory.FullName
# Verify every audited image byte is actually in the MSI, not merely in its input.
$count=0
foreach ($file in Get-ChildItem -LiteralPath $ImageDirectory -Recurse -File) {
    $relative=[IO.Path]::GetRelativePath($ImageDirectory,$file.FullName)
    $packaged=Join-Path $installed $relative
    if (-not (Test-Path -LiteralPath $packaged) -or
        (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash -ne
        (Get-FileHash -LiteralPath $packaged -Algorithm SHA256).Hash) { throw "MSI payload mismatch: $relative" }
    $count++
}
& "$PSScriptRoot/../audit-arm64-image.ps1" -ImageDirectory $installed
if (-not $Fixture) {
    if (-not (Test-Path "$installed/runtime/bin/server/jvm.dll")) { throw 'Bundled JVM missing.' }
    if (-not (Test-Path "$installed/app/licenses/Nuvio-GPLv3.txt")) { throw 'Installed application license missing.' }
    $cfg=Get-Content "$installed/app/$ProductName.cfg" -Raw
    if ($cfg.Contains([IO.Path]::GetFullPath($pwd))) { throw 'Launcher depends on the build checkout.' }
    $env:APPDATA=Join-Path $ValidationDirectory 'private-smoke/roaming'
    $env:LOCALAPPDATA=Join-Path $ValidationDirectory 'private-smoke/local'
    $env:NUVIO_DESKTOP_TEST_DATA_DIR=Join-Path $ValidationDirectory 'private-smoke/native'
    New-Item -ItemType Directory -Force $env:APPDATA,$env:LOCALAPPDATA | Out-Null
    $stdout=Join-Path $ValidationDirectory 'private-smoke/stdout.log'
    $stderr=Join-Path $ValidationDirectory 'private-smoke/stderr.log'
    $process=Start-Process $launchers[0].FullName -WorkingDirectory $installed -ArgumentList '--native-runtime-smoke' -WindowStyle Hidden -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    if (-not $process.WaitForExit(180000)) { $process.Kill(); throw 'Extracted MSI native smoke timed out.' }
    if ($process.ExitCode -ne 0 -or (Get-Content $stdout -Raw) -notmatch 'PASS: native prerequisite smoke') { throw 'Extracted MSI native smoke failed.' }
}
$signature=(Get-AuthenticodeSignature -LiteralPath $Msi).Status.ToString()
if ($signature -ne 'NotSigned') { throw "Unexpected signing state $signature; review publisher trust explicitly." }
[ordered]@{productName=$actualName;productVersion=$actualVersion;upgradeCode=$actualUpgrade;
    productCode=$productCode;template=$template;scope='perUser';signature=$signature;
    matchedImageFiles=$count;nativeStartup=$(if($Fixture){'fixture-not-application'}else{'pass'});
    architectureAudit='arm64';physicalInstallation='not tested'} |
    ConvertTo-Json | Set-Content "$ValidationDirectory/MSI-VALIDATION.json"
Write-Output "MSI structure verified: $count image files, Arm64 template, per-user identity, unsigned. Physical installation remains separate."
