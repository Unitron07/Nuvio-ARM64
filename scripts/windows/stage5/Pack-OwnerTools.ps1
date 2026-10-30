param(
    [Parameter(Mandatory=$true)][string]$OutputDirectory,
    [string]$SourceCommit = ''
)
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath("$PSScriptRoot/../../..")
if (-not $SourceCommit) { $SourceCommit=(& git -C $repo rev-parse HEAD) }
if ($SourceCommit -notmatch '^[a-f0-9]{40}$') { throw 'A full source commit is required.' }
& "$PSScriptRoot/../stage3/Pack-OwnerTools.ps1" -OutputDirectory $OutputDirectory
$output=[IO.Path]::GetFullPath($OutputDirectory)
Copy-Item -LiteralPath $PSScriptRoot -Destination (Join-Path $output 'scripts/windows/stage5') -Recurse
[ordered]@{schema='nuvio-stage5-tools-v1'; sourceCommit=$SourceCommit; architecture='arm64'} |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $output 'scripts/windows/stage5/SOURCE.json')
foreach ($name in @('STAGE5_ARM64_PERFORMANCE.md','STAGE5_HARDWARE_DECODING.md','STAGE5_SURFACE_VALIDATION.md')) {
    Copy-Item -LiteralPath (Join-Path $repo $name) -Destination $output
}
New-Item -ItemType Directory -Force (Join-Path $output 'docs/stage5') | Out-Null
Copy-Item -LiteralPath "$repo/docs/stage5/standalone-surface-results.json" -Destination (Join-Path $output 'docs/stage5')
Copy-Item -LiteralPath "$repo/docs/stage5/owner-surface-results.json" -Destination (Join-Path $output 'docs/stage5')
Compress-Archive -Path (Join-Path $output '*') -DestinationPath "$output.zip" -Force
Write-Host "Stage 5 matching owner tools: $output.zip"
