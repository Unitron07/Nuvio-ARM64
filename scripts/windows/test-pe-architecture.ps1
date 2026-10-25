$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$verifier = Join-Path $PSScriptRoot 'verify-pe-architecture.ps1'
$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('nuvio-pe-' + [guid]::NewGuid())
New-Item -ItemType Directory -Path $testRoot | Out-Null
$passed = 0
function Write-TestPe([string]$Name, [uint16]$Machine) {
    $bytes = [byte[]]::new(128)
    $bytes[0] = 0x4D; $bytes[1] = 0x5A
    [BitConverter]::GetBytes([int]64).CopyTo($bytes, 0x3C)
    [BitConverter]::GetBytes([int]0x4550).CopyTo($bytes, 64)
    [BitConverter]::GetBytes($Machine).CopyTo($bytes, 68)
    $target = Join-Path $testRoot $Name
    [System.IO.File]::WriteAllBytes($target, $bytes)
    return $target
}
function Expect-Failure([scriptblock]$Action, [string]$Message) {
    try { & $Action | Out-Null } catch {
        if ($_.Exception.Message -notlike "*$Message*") { throw }
        $script:passed++
        return
    }
    throw "Expected failure containing: $Message"
}
try {
    $arm = Write-TestPe 'arm.dll' 0xAA64
    $x64 = Write-TestPe 'x64.dll' 0x8664
    & $verifier -Path $arm -Architecture arm64 | Out-Null; $passed++
    & $verifier -Path $x64 -Architecture x64 | Out-Null; $passed++
    Expect-Failure { & $verifier -Path $x64 -Architecture arm64 } 'Wrong architecture'
    Expect-Failure { & $verifier -Path $arm -Architecture x64 } 'Wrong architecture'
    Expect-Failure { & $verifier -Path $testRoot -Architecture arm64 } 'Wrong architecture'
    Expect-Failure { & $verifier -Path (Join-Path $testRoot 'missing.dll') -Architecture arm64 } 'missing'
    $ec = Write-TestPe 'arm64ec.dll' 0xA641
    Expect-Failure { & $verifier -Path $ec -Architecture arm64 } 'Wrong architecture'
    $bad = Join-Path $testRoot 'pointer.dll'
    Set-Content -LiteralPath $bad -Value 'version https://git-lfs.github.com/spec/v1'
    Expect-Failure { & $verifier -Path $bad -Architecture arm64 } 'Not a PE binary'
    $bytes = [System.IO.File]::ReadAllBytes($arm)
    [BitConverter]::GetBytes([int]10000).CopyTo($bytes, 0x3C)
    [System.IO.File]::WriteAllBytes($bad, $bytes)
    Expect-Failure { & $verifier -Path $bad -Architecture arm64 } 'Invalid PE header offset'
    $bytes[0x3C] = 64; $bytes[0x3D] = 0; $bytes[64] = 0
    [System.IO.File]::WriteAllBytes($bad, $bytes)
    Expect-Failure { & $verifier -Path $bad -Architecture arm64 } 'Invalid PE signature'
    Write-Output "$passed PE architecture checks passed."
} finally {
    # Only remove the unique directory created by this test.
    $resolvedTestRoot = [System.IO.Path]::GetFullPath($testRoot)
    $resolvedTempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
    if ($resolvedTestRoot.StartsWith($resolvedTempRoot, [System.StringComparison]::OrdinalIgnoreCase) -and
        [System.IO.Path]::GetFileName($resolvedTestRoot).StartsWith('nuvio-pe-')) {
        Remove-Item -LiteralPath $resolvedTestRoot -Recurse -Force
    }
}
