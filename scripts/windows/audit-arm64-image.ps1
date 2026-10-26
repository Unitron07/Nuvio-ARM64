param([Parameter(Mandatory=$true)][string]$ImageDirectory, [switch]$PruneForeignJarNatives)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
Add-Type -AssemblyName System.IO.Compression.FileSystem
$image=[IO.Path]::GetFullPath($ImageDirectory)
if (-not (Test-Path -LiteralPath $image -PathType Container)) { throw "Image is missing: $image" }
if ($PruneForeignJarNatives) {
    $build=[IO.Path]::GetFullPath("$PSScriptRoot/../../composeApp/build") + [IO.Path]::DirectorySeparatorChar
    if (-not ($image + [IO.Path]::DirectorySeparatorChar).StartsWith($build,[StringComparison]::OrdinalIgnoreCase)) {
        throw 'Native resource pruning is restricted to this checkout composeApp/build output.'
    }
}
function Machine([IO.Stream]$Stream, [long]$Length) {
    $reader=[IO.BinaryReader]::new($Stream)
    $head=$reader.ReadBytes(64)
    if ($head.Length -lt 64 -or [BitConverter]::ToUInt16($head,0) -ne 0x5A4D) { throw 'Invalid PE image.' }
    $offset=[BitConverter]::ToInt32($head,60)
    if ($offset -lt 64 -or $offset -gt $Length-24) { throw 'Invalid PE offset.' }
    [void]$reader.ReadBytes($offset-64)
    if ($reader.ReadUInt32() -ne 0x4550) { throw 'Invalid PE signature.' }
    return $reader.ReadUInt16()
}
$removed=[Collections.Generic.List[string]]::new()
$checked=0
foreach ($file in Get-ChildItem -LiteralPath $image -Recurse -File) {
    if ($file.Extension -in @('.dll','.exe')) {
        $stream=$file.OpenRead()
        try { $machine=Machine $stream $stream.Length } finally { $stream.Dispose() }
        if ($machine -ne 0xAA64) { throw "Foreign native image $($file.FullName): 0x$($machine.ToString('X4'))" }
        $checked++
    } elseif ($file.Extension -eq '.jar') {
        $mode=if ($PruneForeignJarNatives) { [IO.Compression.ZipArchiveMode]::Update } else { [IO.Compression.ZipArchiveMode]::Read }
        $archive=[IO.Compression.ZipFile]::Open($file.FullName,$mode)
        try {
            foreach ($entry in @($archive.Entries | Where-Object { $_.FullName -match '\.(dll|exe)$' })) {
                $stream=$entry.Open()
                try { $machine=Machine $stream $entry.Length } finally { $stream.Dispose() }
                if ($machine -ne 0xAA64) {
                    $name=$file.FullName.Substring($image.Length+1) + '!' + $entry.FullName
                    if (-not $PruneForeignJarNatives) { throw "Foreign JAR native resource: $name" }
                    $removed.Add($name)
                    $entry.Delete()
                } else { $checked++ }
            }
        } finally { $archive.Dispose() }
    }
}
if ($checked -eq 0) { throw 'No native binaries were audited.' }
@("ARM64 native files/resources verified: $checked", "Foreign JAR natives removed: $($removed.Count)") + $removed |
    Set-Content (Join-Path $image 'ARM64-native-audit.txt')
Write-Output "Verified $checked ARM64 native files/resources; removed $($removed.Count) foreign JAR natives."
