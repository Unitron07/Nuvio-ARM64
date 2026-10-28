param([Parameter(Mandatory=$true)][string]$OutputDirectory)
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath("$PSScriptRoot/../../..")
$output = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $output) { throw 'Choose a new output directory.' }
$scripts = Join-Path $output 'scripts/windows'
New-Item -ItemType Directory -Path $scripts -Force | Out-Null
Copy-Item -LiteralPath $PSScriptRoot -Destination (Join-Path $scripts 'stage3') -Recurse
foreach ($name in @('audit-arm64-image.ps1','verify-pe-architecture.ps1','make-smoke-avi.py')) {
    Copy-Item -LiteralPath (Join-Path "$repo/scripts/windows" $name) -Destination $scripts
}
Copy-Item -LiteralPath "$repo/STAGE3_ARM64_VALIDATION.md" -Destination $output
$zip = "$output.zip"
Compress-Archive -LiteralPath (Join-Path $output 'scripts'),(Join-Path $output 'STAGE3_ARM64_VALIDATION.md') -DestinationPath $zip
Write-Host "Owner tools: $zip (application image and authorized media supplied separately)"
