param(
    [Parameter(Mandatory=$true)][string]$RuntimeDirectory,
    [ValidateSet('arm64','x64')][string]$Architecture='arm64',
    [string]$Dumpbin
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
& "$PSScriptRoot/verify-pe-architecture.ps1" -Architecture $Architecture -Path $RuntimeDirectory
if (-not $Dumpbin) {
    $command=Get-Command dumpbin -ErrorAction SilentlyContinue
    if ($command) { $Dumpbin=$command.Source } else {
        $vswhere="${env:ProgramFiles(x86)}/Microsoft Visual Studio/Installer/vswhere.exe"
        $root=& $vswhere -latest -products '*' -property installationPath
        $hostArch=if ([Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString() -eq 'Arm64') {'arm64'} else {'x64'}
        $Dumpbin=Get-ChildItem "$root/VC/Tools/MSVC/*/bin/Host$hostArch/$hostArch/dumpbin.exe" |
            Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
    }
}
if (-not $Dumpbin) { throw 'dumpbin is required to verify native dependency closure.' }
# Explicit Windows OS contract. A random DLL installed in System32 does not
# satisfy application dependencies: in particular vulkan-1.dll must be bundled.
$system=@('kernel32.dll','kernelbase.dll','ntdll.dll','user32.dll','gdi32.dll','advapi32.dll',
    'shell32.dll','ole32.dll','oleaut32.dll','version.dll','avrt.dll','dwmapi.dll','imm32.dll',
    'shcore.dll','uxtheme.dll','bcrypt.dll','shlwapi.dll','ws2_32.dll','dwrite.dll','wldap32.dll',
    'crypt32.dll','bcryptprimitives.dll','avicap32.dll','winmm.dll','setupapi.dll','iphlpapi.dll',
    'secur32.dll','normaliz.dll','d2d1.dll','cfgmgr32.dll','dxgi.dll','d3d11.dll','ucrtbase.dll',
    'wintrust.dll','comdlg32.dll','rpcrt4.dll','msvcrt.dll')
$files=@(Get-ChildItem -LiteralPath $RuntimeDirectory -File -Filter '*.dll')
$names=@($files.Name)
foreach ($file in $files) {
    $output=& $Dumpbin /nologo /dependents $file.FullName
    if ($LASTEXITCODE -ne 0) { throw "Cannot inspect imports: $($file.Name)" }
    $imports=@($output | ForEach-Object { if ($_ -match '^\s+([A-Za-z0-9_.-]+\.dll)\s*$') { $Matches[1].ToLowerInvariant() } })
    if ($imports.Count -eq 0) { throw "No imports found for $($file.Name); inspect dumpbin output." }
    foreach ($dependency in $imports) {
        if ($dependency -in $names -or $dependency -in $system -or $dependency -match '^(api|ext)-ms-win-') { continue }
        throw "Unbundled native dependency: $($file.Name) -> $dependency"
    }
    Write-Output "$($file.Name): verified imports $($imports -join ', ')"
}
