$ErrorActionPreference='Stop'
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to JDK 21.' }
$repo=[IO.Path]::GetFullPath("$PSScriptRoot/../../..")
$run=Join-Path $repo ('build/test-preferences/run-'+[guid]::NewGuid())
New-Item -ItemType Directory -Force $run | Out-Null
$jar=Join-Path $run 'test-preferences.jar'
& "$PSScriptRoot/../stage3/Build-TestPreferences.ps1" -JarPath $jar
& "$env:JAVA_HOME/bin/javac.exe" --release 21 -d $run "$PSScriptRoot/PreferencesProbe.java"
if ($LASTEXITCODE -ne 0) { throw 'Preferences test compilation failed' }
foreach ($mode in @('write','read')) {
    & "$env:JAVA_HOME/bin/java.exe" "-Xbootclasspath/a:$jar" `
        '-Djava.util.prefs.PreferencesFactory=nuvio.testing.IsolatedPreferencesFactory' `
        "-Dnuvio.test.preferences=$run/profile" -cp $run PreferencesProbe $mode
    if ($LASTEXITCODE -ne 0) { throw "Preferences $mode failed" }
}
# A misconfigured helper must fail closed rather than selecting Windows registry storage.
& "$env:JAVA_HOME/bin/java.exe" "-Xbootclasspath/a:$jar" `
    '-Djava.util.prefs.PreferencesFactory=nuvio.testing.IsolatedPreferencesFactory' `
    '-Dnuvio.test.preferences=relative-forbidden' -cp $run PreferencesProbe write *> (Join-Path $run 'expected-failure.log')
if ($LASTEXITCODE -eq 0) { throw 'Relative isolation root was accepted' }
$global:LASTEXITCODE=0
Write-Host 'PASS: fresh-JVM test-session persistence, tree cleanup, separate roots and fail-closed configuration'
