param(
    [Parameter(Mandatory=$true)][string]$Recipient,
    [string]$CandidateDirectory='build/stage6-installer',
    [string]$OutputDirectory='build/stage6-encrypted'
)
$ErrorActionPreference='Stop'
if ($Recipient -cnotmatch '^age1[0-9a-z]{58}$') { throw 'Owner age recipient required; no plaintext fallback exists.' }
$tools=& "$PSScriptRoot/Get-InstallerTools.ps1"
New-Item -ItemType Directory -Force $OutputDirectory | Out-Null
if (Get-ChildItem -LiteralPath $OutputDirectory -Force) { throw 'Encrypted output must be empty.' }
$summary=Get-Content "$CandidateDirectory/BUILD-PROVENANCE.json" -Raw | ConvertFrom-Json
$stem=$summary.installerName + '.test-bundle.zip'
$zip=Join-Path $env:RUNNER_TEMP $stem
if (Test-Path -LiteralPath $zip) { throw 'Refusing to replace an existing candidate bundle.' }
Compress-Archive -Path "$CandidateDirectory/*" -DestinationPath $zip
$cipher=Join-Path $OutputDirectory ($stem + '.age')
& $tools.Age -r $Recipient -o $cipher $zip
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $cipher)) { throw 'Owner encryption failed; no artifact may be uploaded.' }
$summary | Add-Member -NotePropertyName recipient -NotePropertyValue $Recipient
$summary | Add-Member -NotePropertyName encryptedArtifact -NotePropertyValue ([IO.Path]::GetFileName($cipher))
$summary | Add-Member -NotePropertyName encryptedSha256 -NotePropertyValue (Get-FileHash $cipher -Algorithm SHA256).Hash.ToLowerInvariant()
$summary | ConvertTo-Json -Depth 6 | Set-Content "$OutputDirectory/BUILD-SUMMARY.json"
"$($summary.encryptedSha256)  $([IO.Path]::GetFileName($cipher))" | Set-Content "$OutputDirectory/SHA256SUMS.txt"
Write-Output 'Only ciphertext and non-sensitive build metadata are eligible for upload. Owner private key never enters CI.'
