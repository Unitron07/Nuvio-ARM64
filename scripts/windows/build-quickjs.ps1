param(
    [ValidateSet('arm64', 'x64')][string]$Architecture = 'arm64',
    [string]$BuildRoot = "$PSScriptRoot/../../build/windows-quickjs",
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$VsDevCmd,
    [string]$ClangCl = 'clang-cl',
    [string]$CMake = 'cmake'
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
# v1.0.15 is pinned by commit, including its gitlink dependencies.
$revision = 'a081755bcdb18ee6afa7ffee25b8fa976f06d31f'
$quickjsRevision = '04be246001599f5995fa2f2d8c91a0f198d3f34c'
$vectorRevision = '774773d4cb1e66dd736e90e7f482d4f22af464c6'
function Invoke-Checked([string]$Program, [string[]]$Arguments) {
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Program failed with exit code $LASTEXITCODE" }
}
$BuildRoot = [IO.Path]::GetFullPath($BuildRoot)
New-Item -ItemType Directory -Force $BuildRoot | Out-Null
$JavaHome = [IO.Path]::GetFullPath($JavaHome)
if (-not (Test-Path "$JavaHome/include/win32/jni_md.h")) { throw 'A Windows JDK with JNI headers is required.' }
$env:JAVA_HOME = $JavaHome
if (-not $VsDevCmd) {
    $vswhere = "${env:ProgramFiles(x86)}/Microsoft Visual Studio/Installer/vswhere.exe"
    if (-not (Test-Path $vswhere)) { throw 'Install Visual Studio C++ ARM64 tools and Windows SDK, or pass -VsDevCmd.' }
    $VsDevCmd = & $vswhere -latest -products '*' -find 'Common7/Tools/VsDevCmd.bat' | Select-Object -First 1
}
if (-not $VsDevCmd -or -not (Test-Path $VsDevCmd)) { throw 'VsDevCmd.bat was not found.' }
# Import the target SDK/MSVC library environment for CMake and the linker.
$hostArch = if ($env:PROCESSOR_ARCHITECTURE -eq 'ARM64') { 'arm64' } else { 'x64' }
$nativeHost = [Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString().ToLowerInvariant()
if ($Architecture -ne $nativeHost -or $hostArch -ne $nativeHost) {
    throw 'Use a native shell on a matching Windows host: this recipe executes native tests and does not accept emulation.'
}
$environmentScript = Join-Path $BuildRoot "msvc-environment-$Architecture.cmd"
@('@echo off', "call `"$VsDevCmd`" -no_logo -arch=$Architecture -host_arch=$hostArch >nul",
  'if errorlevel 1 exit /b %errorlevel%', 'set') | Set-Content -LiteralPath $environmentScript -Encoding ASCII
$environment = & cmd.exe /d /c "call `"$environmentScript`""
if ($LASTEXITCODE -ne 0) { throw 'MSVC target environment initialization failed.' }
foreach ($line in $environment) {
    if ($line -match '^([^=]+)=(.*)$') { [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process') }
}
$ClangCl = (Get-Command $ClangCl -ErrorAction Stop).Source
$CMake = (Get-Command $CMake -ErrorAction Stop).Source
$source = Join-Path $BuildRoot 'source'
New-Item -ItemType Directory -Force $BuildRoot | Out-Null
if (-not (Test-Path "$source/.git")) {
    Invoke-Checked git @('clone', '--no-checkout', 'https://github.com/dokar3/quickjs-kt.git', $source)
}
Invoke-Checked git @('-C', $source, 'checkout', '--detach', $revision)
Invoke-Checked git @('-C', $source, 'submodule', 'update', '--init', 'quickjs/native/quickjs', 'quickjs/native/c-vector')
foreach ($pair in @(@($source, $revision), @("$source/quickjs/native/quickjs", $quickjsRevision), @("$source/quickjs/native/c-vector", $vectorRevision))) {
    $actual = & git -C $pair[0] rev-parse HEAD
    if ($LASTEXITCODE -ne 0 -or $actual -ne $pair[1]) { throw "Source revision mismatch: $($pair[0])" }
    $dirty = & git -C $pair[0] status --porcelain --untracked-files=normal
    if ($LASTEXITCODE -ne 0 -or $dirty) { throw "Source tree must be clean: $($pair[0])" }
}
$target = if ($Architecture -eq 'arm64') { 'aarch64-pc-windows-msvc' } else { 'x86_64-pc-windows-msvc' }
$build = Join-Path $BuildRoot "obj-$Architecture"
Invoke-Checked $CMake @('-S', "$PSScriptRoot/quickjs", '-B', $build, '-G', 'Ninja',
    "-DQUICKJS_KT_SOURCE=$source", "-DCMAKE_C_COMPILER=$ClangCl", "-DCMAKE_C_COMPILER_TARGET=$target",
    '-DCMAKE_BUILD_TYPE=Release')
Invoke-Checked $CMake @('--build', $build, '--parallel', '2')
# This executable must run natively on a matching host. Cross builds are not
# accepted as smoke-tested by this script.
Invoke-Checked ctest @('--test-dir', $build, '--output-on-failure')
$output = Join-Path $BuildRoot $Architecture
New-Item -ItemType Directory -Force $output | Out-Null
Copy-Item -LiteralPath "$build/libquickjs.dll" -Destination $output
$dll = Join-Path $output 'libquickjs.dll'
& "$PSScriptRoot/verify-pe-architecture.ps1" -Architecture $Architecture -Path $dll
$exports = & dumpbin /nologo /exports $dll
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect JNI exports.' }
$expected = [regex]::Matches((Get-Content "$source/quickjs/native/jni/quickjs_jni.c" -Raw), 'JNICALL\s+(Java_\w+)') | ForEach-Object Value
if ($expected.Count -eq 0) { throw 'No JNI declarations found in the pinned source.' }
foreach ($symbol in $expected) {
    $symbol = $symbol -replace '^JNICALL\s+', ''
    if (-not ($exports -match "\b$symbol\b")) { throw "Missing JNI export: $symbol" }
}
$exports | Set-Content "$output/exports.txt"
$dependents = & dumpbin /nologo /dependents $dll
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect DLL imports.' }
$dependents | Set-Content "$output/dependents.txt"
$recipeHashes = @{}
Get-ChildItem "$PSScriptRoot/quickjs" -Recurse -File | ForEach-Object {
    $recipeHashes[$_.FullName.Substring($PSScriptRoot.Length + 1).Replace('\', '/')] = (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
}
Copy-Item "$source/LICENSE.txt" "$output/QuickJS-KT-LICENSE.txt"
Copy-Item "$source/quickjs/native/quickjs/LICENSE" "$output/QuickJS-LICENSE.txt"
Copy-Item "$source/quickjs/native/c-vector/LICENSE" "$output/c-vector-LICENSE.txt"
$provenance = [ordered]@{
    quickjsKt = $revision; quickjs = $quickjsRevision; cVector = $vectorRevision
    architecture = $Architecture; compilerTarget = $target; runtime = 'static MSVC/UCRT'
    compiler = (& $ClangCl --version | Out-String).Trim()
    cmake = (& $CMake --version | Out-String).Trim()
    windowsSdk = $env:WindowsSDKVersion; msvc = $env:VCToolsVersion
    dllSha256 = (Get-FileHash $dll -Algorithm SHA256).Hash.ToLowerInvariant()
    jniExportCount = $expected.Count
    recipeSha256 = $recipeHashes
}
$provenance | ConvertTo-Json | Set-Content "$output/provenance.json"
Write-Output "Built and verified $dll. Run the separate JVM smoke test before accepting this runtime."
