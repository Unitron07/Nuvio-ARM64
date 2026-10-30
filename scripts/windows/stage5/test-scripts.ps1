$ErrorActionPreference='Stop'
foreach ($script in @(Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.ps1') + @(Get-Item "$PSScriptRoot/../stage3/Start-OwnerTest.ps1")) {
    $tokens=$null; $parseErrors=$null
    [void][Management.Automation.Language.Parser]::ParseFile($script.FullName,[ref]$tokens,[ref]$parseErrors)
    if ($parseErrors) { throw ($parseErrors | Out-String) }
}
& node "$PSScriptRoot/test-results.mjs"
if ($LASTEXITCODE -ne 0) { throw 'Stage 5 privacy/evidence tests failed.' }
Write-Host 'PASS: Stage 5 PowerShell syntax and privacy/evidence tests'
