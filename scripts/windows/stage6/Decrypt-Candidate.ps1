param(
    [Parameter(Mandatory=$true)][string]$EncryptedFile,
    [Parameter(Mandatory=$true)][string]$IdentityFile,
    [Parameter(Mandatory=$true)][string]$OutputZip,
    [Parameter(Mandatory=$true)][string]$AgeExecutable
)
$ErrorActionPreference='Stop'
if (Test-Path -LiteralPath $OutputZip) { throw 'Choose a new output ZIP path; preserve earlier candidate bytes.' }
& $AgeExecutable --decrypt -i $IdentityFile -o $OutputZip $EncryptedFile
if ($LASTEXITCODE -ne 0) { throw 'Decryption/authentication failed. Do not install any partial output.' }
Write-Output 'Decrypted owner-only bundle. Extract it and verify the MSI against its SHA256SUMS before installation.'
