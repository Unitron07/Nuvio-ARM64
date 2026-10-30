param([Parameter(Mandatory=$true)][string]$JarPath)
$ErrorActionPreference='Stop'
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to JDK 21 when packaging owner tools.' }
$repo=[IO.Path]::GetFullPath("$PSScriptRoot/../../..")
$classes=Join-Path $repo 'build/test-preferences/classes'
New-Item -ItemType Directory -Force $classes | Out-Null
& "$env:JAVA_HOME/bin/javac.exe" --release 21 -d $classes "$PSScriptRoot/IsolatedPreferencesFactory.java"
if ($LASTEXITCODE -ne 0) { throw 'Test preferences helper compilation failed.' }
& "$env:JAVA_HOME/bin/jar.exe" --create --file $JarPath -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'Test preferences helper packaging failed.' }
