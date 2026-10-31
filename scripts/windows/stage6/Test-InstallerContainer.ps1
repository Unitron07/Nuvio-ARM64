$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
if ([Runtime.InteropServices.RuntimeInformation]::ProcessArchitecture.ToString() -ne 'Arm64') { throw 'Native ARM64 fixture runner required.' }
function Run([string]$Exe,[string[]]$Arguments) { & $Exe @Arguments; if ($LASTEXITCODE -ne 0) { throw "$Exe failed: $LASTEXITCODE" } }
$tools=& "$PSScriptRoot/Get-InstallerTools.ps1"
$root=Join-Path $pwd 'build/msi-container-fixture'
New-Item -ItemType Directory -Force "$root/classes","$root/input" | Out-Null
'public class Main { public static void main(String[] args) { System.out.println("ARM64 packaging fixture"); } }' | Set-Content "$root/Main.java"
Run "$env:JAVA_HOME/bin/javac.exe" @('-d',"$root/classes","$root/Main.java")
Run "$env:JAVA_HOME/bin/jar.exe" @('--create','--file',"$root/input/probe.jar",'--main-class','Main','-C',"$root/classes",'.')
Run "$env:JAVA_HOME/bin/jpackage.exe" @('--type','app-image','--input',"$root/input",'--dest',"$root/image",'--name','Nuvio-Packaging-Fixture','--main-jar','probe.jar','--app-version','1.0.1','--add-modules','java.base')
$image="$root/image/Nuvio-Packaging-Fixture"
Run python @("$PSScriptRoot/../../automation/msi_layout.py",'--image',$image,'--output',"$root/probe.wxs",'--version','1.0.1','--name','Nuvio-Packaging-Fixture','--upgrade','ddc7b3d0-52de-4d8f-a908-10b9da4bbd47')
Run dotnet @($tools.WixDll,'build',"$root/probe.wxs",'-arch','arm64','-o',"$root/probe.msi")
& "$PSScriptRoot/Test-MsiPayload.ps1" -Msi "$root/probe.msi" -ImageDirectory $image -PackageVersion '1.0.1' -ProductName 'Nuvio-Packaging-Fixture' -UpgradeCode 'ddc7b3d0-52de-4d8f-a908-10b9da4bbd47' -ValidationDirectory "$root/validation" -Fixture
$launcher=Get-ChildItem "$root/validation/extracted" -Recurse -Filter Nuvio-Packaging-Fixture.exe | Select-Object -First 1
$fixtureOutput=Join-Path $root 'stdout.txt'
$process=Start-Process $launcher.FullName -WorkingDirectory $launcher.DirectoryName -WindowStyle Hidden -PassThru -Wait -RedirectStandardOutput $fixtureOutput
if ($process.ExitCode -ne 0 -or (Get-Content $fixtureOutput -Raw) -notmatch 'ARM64 packaging fixture') { throw 'Extracted fixture cannot use its packaged ARM64 JVM.' }
# Encryption round-trip uses a disposable CI key, never the owner's identity.
New-Item -ItemType Directory -Force "$root/keys","$root/payload" | Out-Null
Run $tools.AgeKeygen @('-o',"$root/keys/fixture-key.txt")
$recipient=& $tools.AgeKeygen -y "$root/keys/fixture-key.txt"
if ($LASTEXITCODE -ne 0) { throw 'Test recipient derivation failed.' }
Copy-Item "$root/probe.msi" "$root/payload/fixture.msi"
@{installerName='fixture.msi';installerSha256=(Get-FileHash "$root/probe.msi" -Algorithm SHA256).Hash.ToLowerInvariant();source='fixture-not-a-release'} | ConvertTo-Json | Set-Content "$root/payload/BUILD-PROVENANCE.json"
& "$PSScriptRoot/Protect-Candidate.ps1" -Recipient $recipient -CandidateDirectory "$root/payload" -OutputDirectory "$root/encrypted"
$cipher=Get-ChildItem "$root/encrypted" -Filter '*.age' | Select-Object -First 1
Run $tools.Age @('--decrypt','-i',"$root/keys/fixture-key.txt",'-o',"$root/decrypted.zip",$cipher.FullName)
Expand-Archive -LiteralPath "$root/decrypted.zip" -DestinationPath "$root/decrypted"
if ((Get-FileHash "$root/decrypted/fixture.msi" -Algorithm SHA256).Hash -ne (Get-FileHash "$root/probe.msi" -Algorithm SHA256).Hash) { throw 'Decrypted MSI bytes changed.' }
Run $tools.AgeKeygen @('-o',"$root/keys/wrong-key.txt")
& $tools.Age --decrypt -i "$root/keys/wrong-key.txt" -o "$root/wrong.zip" $cipher.FullName 2>$null
if ($LASTEXITCODE -eq 0) { throw 'Wrong identity unexpectedly decrypted fixture.' }
[ordered]@{fixtureOnly=$true;applicationCandidateBuilt=$false;msiArchitecture='arm64';scope='perUser';exactPayload='pass';bundledJvmStartup='pass';encryptionRoundTrip='pass';wrongKeyRejected=$true;installerRegistered=$false} | ConvertTo-Json | Set-Content "$root/RESULT.json"
Write-Output 'PASS: ARM64 MSI container, exact extracted payload, bundled JVM launch and owner encryption fixture. No application candidate or installation was performed.'
exit 0
