param([Parameter(Mandatory=$true)][string]$RunDirectory)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$run = (Resolve-Path -LiteralPath $RunDirectory).Path
$manifestFile = Join-Path $run 'results.json'
$manifest = Get-Content -LiteralPath $manifestFile -Raw | ConvertFrom-Json
if ($manifest.schema -ne 'nuvio-stage3-v1') { throw 'Not a Stage 3 results directory.' }
foreach ($check in $manifest.acceptance) {
    if ($check.status -notin @('pass','fail','pending','blocked')) { throw "Invalid acceptance status for $($check.id)" }
    if ($check.status -eq 'pass' -and [string]::IsNullOrWhiteSpace($check.evidence)) { throw "Pass requires evidence: $($check.id)" }
}
# Conservative rejection of obvious secrets/URLs in user-authored result text.
$text = Get-Content -LiteralPath $manifestFile -Raw
if ($text -match '(?i)https?://|bearer\s+|api[_-]?key\s*[:=]|access[_-]?token\s*[:=]') {
    throw 'Remove URLs, authorization headers and credentials from results.json before packaging.'
}
$files = @($manifestFile)
$files += @(Get-ChildItem -LiteralPath $run -File -Filter '*.session.json' | ForEach-Object FullName)
# Fixed generated files only; profile/, private/, JVM dumps, arbitrary screenshots
# and raw provider/app logs are deliberately excluded from the shareable archive.
$audit = Join-Path $run 'architecture-audit.txt'
if (Test-Path -LiteralPath $audit) { $files += $audit }
$fixtureLog = Join-Path $run 'fixture-requests.jsonl'
if (Test-Path -LiteralPath $fixtureLog) {
    # Reconstruct the safe schema instead of copying arbitrary JSONL fields.
    $safe = Join-Path $run 'fixture-summary.json'
    $records = @(Get-Content -LiteralPath $fixtureLog | ForEach-Object {
        $entry = $_ | ConvertFrom-Json
        [ordered]@{status=[int]$entry.status; tls=[bool]$entry.tls; range=[bool]$entry.range;
            fixtureAuthorization=[bool]$entry.fixtureAuthorization; fixtureUserAgent=[bool]$entry.fixtureUserAgent;
            fixtureReferer=[bool]$entry.fixtureReferer; fixtureExtraHeader=[bool]$entry.fixtureExtraHeader}
    })
    ConvertTo-Json -InputObject $records -Depth 3 | Set-Content -LiteralPath $safe
    $files += $safe
}
$zip = Join-Path $run 'stage3-owner-results.zip'
Compress-Archive -LiteralPath $files -DestinationPath $zip -Force
Write-Host "Shareable results: $zip"
Write-Host 'Review results.json before sharing. Private logs and crash dumps remain local for selective redacted diagnosis.'
