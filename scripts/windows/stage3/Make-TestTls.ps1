param([Parameter(Mandatory=$true)][string]$OutputDirectory)
$ErrorActionPreference = 'Stop'
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to JDK 21.' }
$directory = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $directory) { throw 'Choose a new test certificate directory.' }
New-Item -ItemType Directory -Path $directory | Out-Null
$store = Join-Path $directory 'fixture.p12'
$certificate = Join-Path $directory 'fixture-ca.pem'
& "$env:JAVA_HOME/bin/keytool.exe" -genkeypair -alias fixture -keyalg RSA -keysize 2048 -validity 2 `
    -dname CN=localhost -ext 'SAN=ip:127.0.0.1,dns:localhost' -storetype PKCS12 `
    -keystore $store -storepass fixture-only -keypass fixture-only -noprompt
if ($LASTEXITCODE -ne 0) { throw 'Test certificate generation failed.' }
& "$env:JAVA_HOME/bin/keytool.exe" -exportcert -rfc -alias fixture -keystore $store `
    -storepass fixture-only -file $certificate
if ($LASTEXITCODE -ne 0) { throw 'Test certificate export failed.' }
Write-Host 'Generated local, short-lived TLS fixture. Never distribute its private keystore.'
