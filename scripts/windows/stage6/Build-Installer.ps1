param(
    [Parameter(Mandatory=$true)][string]$Version,
    [Parameter(Mandatory=$true)][string]$PackageVersion
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
if ([Runtime.InteropServices.RuntimeInformation]::ProcessArchitecture.ToString() -ne 'Arm64') {
    throw 'Installer construction requires a native ARM64 process and JDK.'
}
if ($Version -notmatch '^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?\+arm64\.[1-9][0-9]*$') { throw 'Invalid fork version.' }
if ($PackageVersion -notmatch '^1\.0\.[1-9][0-9]{0,4}$' -or [int]$PackageVersion.Split('.')[-1] -gt 65535) { throw 'Invalid MSI sequence.' }
$image=(Resolve-Path 'composeApp/build/compose/binaries/main/app/Nuvio-ARM64').Path
& "$PSScriptRoot/../audit-arm64-image.ps1" -ImageDirectory $image
$cfg=Get-Content "$image/app/Nuvio-ARM64.cfg" -Raw
if ($cfg -notmatch 'com.nuvio.app.MainKt') { throw 'Unexpected application entry point.' }
$destination=Join-Path $pwd 'build/stage6-installer'
New-Item -ItemType Directory -Force $destination | Out-Null
if (Get-ChildItem -LiteralPath $destination -Force) { throw 'Use an empty installer output directory; never reuse a previous candidate payload.' }
# Use the already audited app-image. Rebuilding via packageMsi would reintroduce
# foreign DLL resources removed by the image audit. WiX must be installed on PATH.
& "$env:JAVA_HOME/bin/jpackage.exe" --type msi --app-image $image --dest $destination `
    --name Nuvio-ARM64 --app-version $PackageVersion --vendor 'Nuvio-ARM64 contributors' `
    --win-upgrade-uuid febc83be-f241-4a78-8f6c-10af5e358acc --win-per-user-install `
    --win-menu --win-menu-group Nuvio-ARM64 --win-shortcut --win-dir-chooser
if ($LASTEXITCODE -ne 0) { throw 'Native ARM64 jpackage/WiX failed; do not substitute an x64 launcher.' }
$msis=@(Get-ChildItem -LiteralPath $destination -Filter '*.msi')
if ($msis.Count -ne 1) { throw 'Expected exactly one freshly built MSI.' }
$msi=$msis[0]
$installer=New-Object -ComObject WindowsInstaller.Installer
$db=$installer.OpenDatabase($msi.FullName,0)
function Property([string]$Name) {
    $view=$db.OpenView("SELECT ``Value`` FROM ``Property`` WHERE ``Property``='$Name'")
    $view.Execute(); $record=$view.Fetch()
    if ($null -eq $record) { return '' }
    return $record.StringData(1)
}
if ((Property 'ProductName') -ne 'Nuvio-ARM64' -or (Property 'ProductVersion') -ne $PackageVersion -or
    (Property 'UpgradeCode').Trim('{}') -ine 'febc83be-f241-4a78-8f6c-10af5e358acc') { throw 'MSI identity mismatch.' }
if ($db.SummaryInformation(0).Property(7) -notmatch '^Arm64;') { throw 'MSI template is not ARM64.' }
if ((Property 'ALLUSERS') -eq '1') { throw 'Unexpected per-machine installer.' }
$db=$null; $installer=$null
[GC]::Collect(); [GC]::WaitForPendingFinalizers()
$name="Nuvio-ARM64-Windows-arm64-$Version.msi"
Move-Item -LiteralPath $msi.FullName -Destination (Join-Path $destination $name)
$source=(& git rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Cannot record source commit.' }
$inventory=Get-ChildItem -LiteralPath $image -Recurse -File | ForEach-Object {
    [ordered]@{path=$_.FullName.Substring($image.Length+1); sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash}
}
[ordered]@{schema='nuvio-arm64-internal-candidate-v1'; source=$source; version=$Version;
    packageVersion=$PackageVersion; signed=$false; publicDistributionCleared=$false;
    validation='Construction, PE and MSI metadata only; physical install/upgrade testing required';
    files=@($inventory)} | ConvertTo-Json -Depth 5 | Set-Content "$destination/DEPENDENCY-INVENTORY.json"
Copy-Item LICENSE "$destination/LICENSE"
Copy-Item build/windows-media-arm64/provenance.json "$destination/NATIVE-PROVENANCE.json"
Copy-Item build/windows-media-arm64/notices "$destination/notices" -Recurse
@('INTERNAL DEVELOPMENT CANDIDATE. UNSIGNED. DO NOT REDISTRIBUTE.',
  "Source=$source", 'Licensing/corresponding source, credentials, physical install/upgrade and signing gates remain open.') |
  Set-Content "$destination/STATUS.txt"
Get-ChildItem -LiteralPath $destination -Recurse -File | Where-Object Name -ne 'SHA256SUMS.txt' | Sort-Object FullName | ForEach-Object {
    "$( (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() )  $($_.FullName.Substring($destination.Length+1).Replace('\','/'))"
} | Set-Content "$destination/SHA256SUMS.txt"
