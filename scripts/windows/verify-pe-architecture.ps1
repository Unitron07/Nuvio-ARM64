param(
    [Parameter(Mandatory = $true)][string[]]$Path,
    [Parameter(Mandatory = $true)][ValidateSet('x64', 'arm64')][string]$Architecture
)

$ErrorActionPreference = 'Stop'
$expectedMachine = if ($Architecture -eq 'arm64') { 0xAA64 } else { 0x8664 }

foreach ($entry in $Path) {
    if (-not (Test-Path -LiteralPath $entry)) { throw "Native input is missing: $entry" }
    $item = Get-Item -LiteralPath $entry
    $files = @(if ($item.PSIsContainer) {
        Get-ChildItem -LiteralPath $item.FullName -Filter '*.dll' -File -Recurse
    } else { $item })
    if ($files.Count -eq 0) { throw "No native DLLs found in $entry" }

    foreach ($file in $files) {
        $stream = [System.IO.File]::OpenRead($file.FullName)
        $reader = [System.IO.BinaryReader]::new($stream)
        try {
            if ($stream.Length -lt 64 -or $reader.ReadUInt16() -ne 0x5A4D) {
                throw "Not a PE binary (possibly a Git LFS pointer): $($file.FullName)"
            }
            $stream.Position = 0x3C
            $offset = $reader.ReadInt32()
            if ($offset -lt 64 -or $offset -gt ($stream.Length - 24)) {
                throw "Invalid PE header offset: $($file.FullName)"
            }
            $stream.Position = $offset
            if ($reader.ReadUInt32() -ne 0x00004550) { throw "Invalid PE signature: $($file.FullName)" }
            $machine = $reader.ReadUInt16()
            if ($machine -ne $expectedMachine) {
                throw ('Wrong architecture for {0}: machine 0x{1:X4}, expected {2} (0x{3:X4}). Supply matching native binaries; renaming x64 DLLs cannot make them ARM64.' -f $file.FullName, $machine, $Architecture, $expectedMachine)
            }
            Write-Output ('Verified {0}: {1}' -f $Architecture, $file.FullName)
        } finally { $reader.Dispose() }
    }
}
