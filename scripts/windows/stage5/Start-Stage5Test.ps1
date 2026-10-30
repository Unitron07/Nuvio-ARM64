param(
    [Parameter(Mandatory=$true)][string]$ImageDirectory,
    [Parameter(Mandatory=$true)][string]$RunDirectory,
    [switch]$Resume
)
$ErrorActionPreference='Stop'
$image=Get-Content -LiteralPath (Join-Path $ImageDirectory 'STAGE3_IMAGE.json') -Raw | ConvertFrom-Json
$tools=Get-Content -LiteralPath "$PSScriptRoot/SOURCE.json" -Raw | ConvertFrom-Json
if ($tools.sourceCommit -ne $image.sourceCommit -or $tools.architecture -ne 'arm64') {
    throw 'Use owner tools and ARM64 application image from the same source commit.'
}
& "$PSScriptRoot/../stage3/Start-OwnerTest.ps1" -ImageDirectory $ImageDirectory -RunDirectory $RunDirectory `
    -Resume:$Resume -PlaybackDiagnostics -PerformanceMetrics
& node "$PSScriptRoot/collect-results.mjs" $RunDirectory
if ($LASTEXITCODE -ne 0) { throw 'Stage 5 collector failed.' }
