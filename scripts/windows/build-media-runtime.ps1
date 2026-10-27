param([string]$BuildRoot = "$PSScriptRoot/../../build/windows-media-arm64")
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if ([Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString() -ne 'Arm64') {
    throw 'This build recipe requires native Windows ARM64.'
}
function Run([string]$Program, [string[]]$Arguments) {
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Program failed: $LASTEXITCODE" }
}
function Download([string]$Url, [string]$Destination, [string]$Sha256) {
    if (-not (Test-Path -LiteralPath $Destination)) { Invoke-WebRequest $Url -OutFile $Destination }
    if ((Get-FileHash $Destination -Algorithm SHA256).Hash -ine $Sha256) { throw "Download hash mismatch: $Destination" }
}
function Source([string]$Name, [string]$Url, [string]$Revision) {
    $directory = Join-Path $BuildRoot $Name
    if (-not (Test-Path "$directory/.git")) { Run git @('clone', $Url, $directory) | Out-Host }
    Run git @('-C', $directory, 'checkout', '--detach', $Revision) | Out-Host
    if ((& git -C $directory rev-parse HEAD) -ne $Revision) { throw "Revision mismatch: $Name" }
    if (& git -C $directory status --porcelain) { throw "Source must be clean: $Name" }
    return $directory
}
$BuildRoot = [IO.Path]::GetFullPath($BuildRoot)
New-Item -ItemType Directory -Force $BuildRoot | Out-Null
$runtime = Join-Path $BuildRoot 'runtime'
New-Item -ItemType Directory -Force $runtime | Out-Null
$archive = Join-Path $BuildRoot 'mpv-dev-aarch64.7z'
$mpvHash = '015f133399b3525021b6d937cf54ace2761d4f70224d5e62f5df24191b28f398'
Download 'https://github.com/shinchiro/mpv-winbuild-cmake/releases/download/20261021/mpv-dev-aarch64-20261021-git-e471496c56.7z' $archive $mpvHash
$mpv = Join-Path $BuildRoot 'mpv-dev'
New-Item -ItemType Directory -Force $mpv | Out-Null
Run tar @('-xf', $archive, '-C', $mpv)
Copy-Item "$mpv/libmpv-2.dll" $runtime
$loaderPin = 'f703f919c30c5b67958d35d40a4297cb3823ed78'
$headersPin = '9a0f3099c8a9607a7c0f3127d8abfdc19a93e8c5'
$loader = Source 'Vulkan-Loader' 'https://github.com/KhronosGroup/Vulkan-Loader.git' $loaderPin
$headers = Source 'Vulkan-Headers' 'https://github.com/KhronosGroup/Vulkan-Headers.git' $headersPin
$vswhere = "${env:ProgramFiles(x86)}/Microsoft Visual Studio/Installer/vswhere.exe"
$vs = (& $vswhere -latest -products '*' -format json | ConvertFrom-Json) | Select-Object -First 1
$major = [int]($vs.installationVersion.Split('.')[0])
$generator = if ($major -eq 18) { 'Visual Studio 18 2026' } elseif ($major -eq 17) { 'Visual Studio 17 2022' } else { throw "Unsupported MSVC generator: $major" }
$prefix = Join-Path $BuildRoot 'vulkan-install'
Run cmake @('-S', $headers, '-B', "$BuildRoot/headers-build", '-G', $generator, '-A', 'ARM64', "-DCMAKE_INSTALL_PREFIX=$prefix")
Run cmake @('--install', "$BuildRoot/headers-build", '--config', 'Release')
Run cmake @('-S', $loader, '-B', "$BuildRoot/loader-build", '-G', $generator, '-A', 'ARM64',
    "-DVULKAN_HEADERS_INSTALL_DIR=$prefix", '-DBUILD_TESTS=OFF', "-DCMAKE_INSTALL_PREFIX=$prefix")
Run cmake @('--build', "$BuildRoot/loader-build", '--config', 'Release', '--parallel', '2')
Run cmake @('--install', "$BuildRoot/loader-build", '--config', 'Release')
Copy-Item "$prefix/bin/vulkan-1.dll" $runtime
& "$PSScriptRoot/verify-pe-architecture.ps1" -Architecture arm64 -Path $runtime
$sdk = Join-Path $BuildRoot 'webview2'
$sdkArchive = Join-Path $BuildRoot 'webview2.zip'
Download 'https://api.nuget.org/v3-flatcontainer/microsoft.web.webview2/1.0.4078.44/microsoft.web.webview2.1.0.4078.44.nupkg' $sdkArchive 'dc4d1d9168df26b830398303e50210b6e1729f6ce5a7ac69d2c766852f489962'
if (-not (Test-Path "$sdk/build/native/include/WebView2.h")) { Expand-Archive -LiteralPath $sdkArchive -DestinationPath $sdk }
& "$PSScriptRoot/verify-pe-architecture.ps1" -Architecture arm64 -Path "$sdk/build/native/arm64/WebView2Loader.dll"
$notices = Join-Path $BuildRoot 'notices'
New-Item -ItemType Directory -Force $notices | Out-Null
Copy-Item "$loader/LICENSE*" $notices
Copy-Item "$headers/LICENSE*" $notices
[ordered]@{
    architecture='arm64'; mpvArchiveSha256=$mpvHash
    mpv='e471496c56f0f49c133bd88b50aed62b845f1909'
    ffmpeg='9a861e9687d30e657d59521817cad18405995983'
    mediaBuilder='1f8d2e76255abc48f0456d9be99b4cab9c6566e6'
    vulkanLoader=$loaderPin; vulkanHeaders=$headersPin; webview2Version='1.0.4078.44'
    purpose='Development runtime only; static mpv dependency notices/source audit remains a distribution gate.'
    files=@(Get-ChildItem $runtime -Filter '*.dll' | ForEach-Object {
        @{name=$_.Name;sha256=(Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}
    })
} | ConvertTo-Json -Depth 5 | Set-Content "$BuildRoot/provenance.json"
Write-Output "ARM64 media runtime: $runtime"
