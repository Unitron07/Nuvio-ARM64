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
    if ($offset -lt 64 -or $offset -gt ($Length - 24)) { throw "Invalid PE offset $offset in $Length bytes." }
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
        # Read-only inspection is essential: Update mode can rewrite untouched
        # ZIP data descriptors in a form Java's class loader cannot read.
        $foreign=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        $archive=[IO.Compression.ZipFile]::OpenRead($file.FullName)
        try {
            foreach ($entry in @($archive.Entries | Where-Object { $_.FullName -match '\.(dll|exe)$' })) {
                $nativeLength=$entry.Length
                $stream=$entry.Open()
                try { $machine=Machine $stream $nativeLength } finally { $stream.Dispose() }
                if ($machine -ne 0xAA64) {
                    $name=$file.FullName.Substring($image.Length+1) + '!' + $entry.FullName
                    if (-not $PruneForeignJarNatives) { throw "Foreign JAR native resource: $name" }
                    $removed.Add($name)
                    [void]$foreign.Add($entry.FullName)
                } else { $checked++ }
            }
        } finally { $archive.Dispose() }
        if ($foreign.Count -gt 0) {
            $temporary=$file.FullName + '.' + [guid]::NewGuid() + '.tmp'
            $source=[IO.Compression.ZipFile]::OpenRead($file.FullName)
            $target=[IO.Compression.ZipFile]::Open($temporary,[IO.Compression.ZipArchiveMode]::Create)
            try {
                foreach ($entry in $source.Entries) {
                    if ($foreign.Contains($entry.FullName)) { continue }
                    $copy=$target.CreateEntry($entry.FullName,[IO.Compression.CompressionLevel]::Optimal)
                    $copy.LastWriteTime=$entry.LastWriteTime
                    $copy.ExternalAttributes=$entry.ExternalAttributes
                    $inputStream=$entry.Open()
                    $outputStream=$copy.Open()
                    try { $inputStream.CopyTo($outputStream) }
                    finally { $outputStream.Dispose(); $inputStream.Dispose() }
                }
            } finally { $target.Dispose(); $source.Dispose() }
            [IO.File]::Move($temporary,$file.FullName,$true)
        }
    }
}
if ($checked -eq 0) { throw 'No native binaries were audited.' }
@("ARM64 native files/resources verified: $checked", "Foreign JAR natives removed: $($removed.Count)") + $removed |
    Set-Content (Join-Path $image 'ARM64-native-audit.txt')
Write-Output "Verified $checked ARM64 native files/resources; removed $($removed.Count) foreign JAR natives."
