param([Parameter(Mandatory=$true)][string]$ImageDirectory,[Parameter(Mandatory=$true)][string]$OutputFile)
$ErrorActionPreference='Stop'
$image=(Resolve-Path -LiteralPath $ImageDirectory).Path
$audit=Get-Content (Join-Path $image 'ARM64-native-audit.txt') -Raw
$match=[regex]::Match($audit,'verified: (\d+)')
if (-not $match.Success) { throw 'Run the strict image architecture audit before inventory generation.' }
$entries=[Collections.Generic.List[object]]::new()
foreach ($file in Get-ChildItem -LiteralPath $image -Recurse -File) {
    $relative=[IO.Path]::GetRelativePath($image,$file.FullName).Replace('\','/')
    if ($file.Extension -in @('.exe','.dll')) {
        $entries.Add([ordered]@{path=$relative;machine='0xAA64';architecture='arm64';sha256=(Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()})
    } elseif ($file.Extension -eq '.jar') {
        $archive=[IO.Compression.ZipFile]::OpenRead($file.FullName)
        try {
            foreach ($entry in $archive.Entries | Where-Object FullName -Match '(?i)\.(dll|exe)$') {
                $stream=$entry.Open()
                try { $hash=(Get-FileHash -InputStream $stream -Algorithm SHA256).Hash.ToLowerInvariant() }
                finally { $stream.Dispose() }
                $entries.Add([ordered]@{path=$relative+'!'+$entry.FullName;machine='0xAA64';architecture='arm64';sha256=$hash})
            }
        } finally { $archive.Dispose() }
    }
}
if ($entries.Count -ne [int]$match.Groups[1].Value) { throw 'Native inventory count differs from the completed PE/JAR audit.' }
[ordered]@{validationMethod='audit-arm64-image.ps1; every listed PE/JAR entry passed strict AA64 validation';count=$entries.Count;entries=$entries.ToArray()} |
    ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $OutputFile
