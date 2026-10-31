param(
    [Parameter(Mandatory=$true)][string]$Version,
    [Parameter(Mandatory=$true)][string]$PackageVersion,
    [Parameter(Mandatory=$true)][string]$RecipeCommit,
    [Parameter(Mandatory=$true)][string]$UpstreamTag,
    [Parameter(Mandatory=$true)][string]$UpstreamCommit
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
if ([Runtime.InteropServices.RuntimeInformation]::ProcessArchitecture.ToString() -ne 'Arm64') { throw 'Native ARM64 process and JDK required.' }
if ($Version -notmatch '^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?\+arm64\.[1-9][0-9]*$') { throw 'Invalid fork version.' }
if ($RecipeCommit -notmatch '^[0-9a-f]{40}$' -or $UpstreamCommit -notmatch '^[0-9a-f]{40}$') { throw 'Immutable source and recipe SHAs required.' }
$image=(Resolve-Path 'composeApp/build/compose/binaries/main/app/Nuvio-ARM64').Path
$destination=Join-Path $pwd 'build/stage6-installer'
New-Item -ItemType Directory -Force $destination | Out-Null
if (Get-ChildItem -LiteralPath $destination -Force) { throw 'Use an empty output directory. Never replace candidate bytes.' }
$cfg=Get-Content "$image/app/Nuvio-ARM64.cfg" -Raw
if ($cfg -notmatch 'com.nuvio.app.MainKt' -or $cfg.Contains([IO.Path]::GetFullPath($pwd))) { throw 'Unexpected or checkout-dependent launcher.' }
# Install known notices beside the app, retaining JDK legal/ and JAR META-INF notices.
$licenses=Join-Path $image 'app/licenses'
New-Item -ItemType Directory -Force $licenses | Out-Null
Copy-Item LICENSE "$licenses/Nuvio-GPLv3.txt"
Copy-Item vendor/compose-media-player/LICENSE "$licenses/compose-media-player-LICENSE.txt"
Copy-Item build/windows-quickjs/arm64/*LICENSE* $licenses
Copy-Item build/windows-quickjs/arm64/provenance.json "$licenses/QuickJS-PROVENANCE.json"
foreach ($component in @('Vulkan-Loader','Vulkan-Headers')) {
    $target=Join-Path $licenses $component
    New-Item -ItemType Directory -Force $target | Out-Null
    Copy-Item "build/windows-media-arm64/$component/LICENSE*" $target
}
$webviewLicenses=@(Get-ChildItem build/windows-media-arm64/webview2 -File | Where-Object Name -match '(?i)license|notice')
if ($webviewLicenses.Count -eq 0) { throw 'WebView2 SDK notice missing; inspect the pinned package.' }
$webviewDir=Join-Path $licenses 'WebView2'
New-Item -ItemType Directory -Force $webviewDir | Out-Null
$webviewLicenses | Copy-Item -Destination $webviewDir
Copy-Item "$PSScriptRoot/notices/*" $licenses
Copy-Item "$PSScriptRoot/FIRST_MSI_SURFACE_TEST.md" "$destination/INSTALLATION-AND-ROLLBACK.md"
& "$PSScriptRoot/../audit-arm64-image.ps1" -ImageDirectory $image
$tools=& "$PSScriptRoot/Get-InstallerTools.ps1"
$name="Nuvio-ARM64-Windows-arm64-$Version.msi"
$msi=Join-Path $destination $name
$layout=Join-Path $env:RUNNER_TEMP 'nuvio-arm64.wxs'
# JDK 21 WixPipeline hard-codes x86/x64. Keep its already audited app-image;
# compile only the MSI container with a pinned WiX 4 -arch arm64 toolchain.
& python "$PSScriptRoot/../../automation/msi_layout.py" --image $image --output $layout --version $PackageVersion
if ($LASTEXITCODE -ne 0) { throw 'MSI authoring failed.' }
& dotnet $tools.WixDll build $layout -arch arm64 -o $msi
if ($LASTEXITCODE -ne 0) { throw 'ARM64 WiX compilation failed. Architecture checks may not be suppressed.' }
& "$PSScriptRoot/Test-MsiPayload.ps1" -Msi $msi -ImageDirectory $image -PackageVersion $PackageVersion
Copy-Item build/stage6-msi-validation/MSI-VALIDATION.json $destination
& "$PSScriptRoot/Write-NativeInventory.ps1" -ImageDirectory $image -OutputFile "$destination/NATIVE-INVENTORY.json"
Copy-Item "$image/ARM64-native-audit.txt" "$destination/NATIVE-ARCHITECTURE-AUDIT.txt"
$source=(& git rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Cannot record application source.' }
$inventory=Get-ChildItem -LiteralPath $image -Recurse -File | ForEach-Object {
    [ordered]@{path=[IO.Path]::GetRelativePath($image,$_.FullName).Replace('\','/');sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}
}
[ordered]@{schema='nuvio-arm64-image-inventory-v1';source=$source;files=@($inventory)} | ConvertTo-Json -Depth 5 | Set-Content "$destination/DEPENDENCY-INVENTORY.json"
[ordered]@{schema='nuvio-arm64-internal-candidate-v2';source=$source;recipeCommit=$RecipeCommit;
    upstreamTag=$UpstreamTag;upstreamCommit=$UpstreamCommit;version=$Version;packageVersion=$PackageVersion;
    installerName=$name;installerSha256=(Get-FileHash $msi -Algorithm SHA256).Hash.ToLowerInvariant();
    architecture='arm64';signed=$false;publicDistributionCleared=$false;
    runId=$env:GITHUB_RUN_ID;runAttempt=$env:GITHUB_RUN_ATTEMPT;
    wixVersion='4.0.6';wixPackageSha256=$tools.WixSha256;
    ageVersion='1.3.2';ageArchiveSha256=$tools.AgeSha256;
    validation='MSI structure, exact payload, PE/JAR architecture and extracted native startup; physical installation untested'} |
    ConvertTo-Json -Depth 5 | Set-Content "$destination/BUILD-PROVENANCE.json"
Copy-Item build/windows-media-arm64/provenance.json "$destination/NATIVE-PROVENANCE.json"
Copy-Item $licenses "$destination/notices" -Recurse
@('UNSIGNED OWNER-ONLY TEST CANDIDATE. PUBLIC DISTRIBUTION IS NOT CLEARED.',
  "Application source=$source", "Packaging recipe=$RecipeCommit",
  'Native licensing/corresponding-source closure remains unresolved. Preserve test profile isolation.') | Set-Content "$destination/STATUS.txt"
Get-ChildItem -LiteralPath $destination -Recurse -File | Where-Object Name -ne 'SHA256SUMS.txt' | Sort-Object FullName | ForEach-Object {
    "$((Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant())  $([IO.Path]::GetRelativePath($destination,$_.FullName).Replace('\','/'))"
} | Set-Content "$destination/SHA256SUMS.txt"
