param([string]$ToolDirectory = 'build/stage6-tools')
$ErrorActionPreference='Stop'
$arch=if ([Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString() -eq 'Arm64') {'arm64'} else {'amd64'}
$ToolDirectory=[IO.Path]::GetFullPath($ToolDirectory)
New-Item -ItemType Directory -Force $ToolDirectory | Out-Null
function Acquire([string]$Url,[string]$File,[string]$Hash,[string]$Folder) {
    if (-not (Test-Path -LiteralPath $File)) { Invoke-WebRequest $Url -OutFile $File }
    if ((Get-FileHash -LiteralPath $File -Algorithm SHA256).Hash -ine $Hash) { throw "Pinned installer tool hash mismatch: $File" }
    Expand-Archive -LiteralPath $File -DestinationPath $Folder -Force
}
$ageHash=if ($arch -eq 'arm64') {'fae351336c8d5f30f93cc3ccdec16d1ed7851d68cdd6656d41955cc31a447c9d'} else {'f48d8f8f9ebe903ab5027ed067652f2cc1db94bc206976430133b905dcd8e8c7'}
Acquire "https://github.com/FiloSottile/age/releases/download/v1.3.2/age-v1.3.2-windows-$arch.zip" "$ToolDirectory/age.zip" $ageHash "$ToolDirectory/age"
Acquire 'https://api.nuget.org/v3-flatcontainer/wix/4.0.6/wix.4.0.6.nupkg' "$ToolDirectory/wix.zip" 'a94dd42ae1fb56b32da180e2173ceda4f0d10b4c8871c5ee59ecb502131a1eb6' "$ToolDirectory/wix"
[pscustomobject]@{
    WixDll="$ToolDirectory/wix/tools/net6.0/any/wix.dll"
    Age=(Get-ChildItem "$ToolDirectory/age" -Recurse -Filter age.exe | Select-Object -First 1).FullName
    AgeKeygen=(Get-ChildItem "$ToolDirectory/age" -Recurse -Filter age-keygen.exe | Select-Object -First 1).FullName
    WixSha256='a94dd42ae1fb56b32da180e2173ceda4f0d10b4c8871c5ee59ecb502131a1eb6'
    AgeSha256=$ageHash
}
