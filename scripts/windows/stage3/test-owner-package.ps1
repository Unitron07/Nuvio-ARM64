$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$directory = Join-Path ([IO.Path]::GetTempPath()) ("nuvio-stage3-package-" + [guid]::NewGuid())
New-Item -ItemType Directory -Path "$directory/private","$directory/profile" | Out-Null
try {
    @{schema='nuvio-stage3-v1'; acceptance=@(@{id='fixture';status='pending';evidence=''})} |
        ConvertTo-Json -Depth 5 | Set-Content "$directory/results.json"
    Set-Content "$directory/private/stdout.log" 'synthetic-private-must-not-share'
    Set-Content "$directory/profile/credentials.properties" 'synthetic-private-must-not-share'
    Set-Content "$directory/fixture-requests.jsonl" '{"status":200,"tls":false,"range":true,"fixtureAuthorization":true,"fixtureUserAgent":true,"fixtureReferer":true,"fixtureExtraHeader":true,"unexpectedSecret":"must-not-share"}'
    & "$PSScriptRoot/Collect-OwnerResults.ps1" -RunDirectory $directory
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [IO.Compression.ZipFile]::OpenRead("$directory/stage3-owner-results.zip")
    try {
        $names = @($zip.Entries | ForEach-Object FullName)
        if ($names.Count -ne 2 -or $names -notcontains 'results.json' -or $names -notcontains 'fixture-summary.json') { throw 'Unexpected archive contents.' }
        foreach ($entry in $zip.Entries) {
            $reader = [IO.StreamReader]::new($entry.Open())
            try { if ($reader.ReadToEnd() -match 'must-not-share') { throw 'Private data leaked.' } } finally { $reader.Dispose() }
        }
    } finally { $zip.Dispose() }
    @{schema='nuvio-stage3-v1'; acceptance=@(@{id='fixture';status='pass';evidence=''})} |
        ConvertTo-Json -Depth 5 | Set-Content "$directory/results.json"
    $rejected = $false
    try { & "$PSScriptRoot/Collect-OwnerResults.ps1" -RunDirectory $directory } catch { $rejected = $true }
    if (-not $rejected) { throw 'Unevidenced pass accepted.' }
    Write-Host 'PASS: owner archive excludes private/profile data, drops unknown fixture fields, rejects pass without evidence.'
} finally {
    $resolved = [IO.Path]::GetFullPath($directory)
    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\'
    if (-not $resolved.StartsWith($tempRoot,[StringComparison]::OrdinalIgnoreCase) -or (Split-Path $resolved -Leaf) -notlike 'nuvio-stage3-package-*') {
        throw 'Refusing cleanup outside the generated temporary directory.'
    }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}
