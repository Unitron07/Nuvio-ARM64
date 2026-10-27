$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
Add-Type -AssemblyName System.IO.Compression.FileSystem
$build=[IO.Path]::GetFullPath("$PSScriptRoot/../../composeApp/build")
$root=Join-Path $build ('image-audit-test-' + [guid]::NewGuid())
New-Item -ItemType Directory -Force $root | Out-Null
function Pe([int]$Machine) {
    $bytes=[byte[]]::new(128)
    $bytes[0]=0x4d; $bytes[1]=0x5a
    [BitConverter]::GetBytes([int]64).CopyTo($bytes,60)
    [BitConverter]::GetBytes([int]0x4550).CopyTo($bytes,64)
    [BitConverter]::GetBytes([uint16]$Machine).CopyTo($bytes,68)
    return ,$bytes
}
function Reject([scriptblock]$Action,[string]$Message) {
    try { & $Action | Out-Null } catch {
        if ($_.Exception.Message -notlike "*$Message*") { throw }
        return
    }
    throw "Expected rejection: $Message"
}
try {
    $arm=Pe 0xaa64
    [IO.File]::WriteAllBytes("$root/launcher.exe",$arm)
    $javaBin=if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin' } else { Split-Path (Get-Command javac -ErrorAction Stop).Source }
    New-Item -ItemType Directory "$root/classes","$root/fixture/windows_x64" | Out-Null
    'public class AuditFixture { public static void main(String[] args) { System.out.println("PASS: Java class loading after native audit"); } }' |
        Set-Content "$root/AuditFixture.java"
    & "$javaBin/javac.exe" -d "$root/classes" "$root/AuditFixture.java"
    if ($LASTEXITCODE -ne 0) { throw 'Java regression fixture compilation failed.' }
    & "$javaBin/jar.exe" --create --file "$root/plain.jar" -C "$root/classes" .
    if ($LASTEXITCODE -ne 0) { throw 'Plain JAR fixture creation failed.' }
    [IO.File]::WriteAllBytes("$root/fixture/windows_x64/lib.dll",(Pe 0x8664))
    & "$javaBin/jar.exe" --create --file "$root/java-native.jar" -C "$root/classes" . -C "$root/fixture" .
    if ($LASTEXITCODE -ne 0) { throw 'Native JAR fixture creation failed.' }
    Remove-Item -LiteralPath "$root/fixture/windows_x64/lib.dll"
    $plainHash=(Get-FileHash "$root/plain.jar" -Algorithm SHA256).Hash
    $jar=[IO.Compression.ZipFile]::Open("$root/runtime.jar",[IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach ($entry in @(@('windows_aarch64/lib.dll',$arm),@('windows_x64/lib.dll',(Pe 0x8664)),@('Test.class',[byte[]](1,2,3)))) {
            $stream=$jar.CreateEntry($entry[0]).Open()
            try { $stream.Write($entry[1],0,$entry[1].Length) } finally { $stream.Dispose() }
        }
    } finally { $jar.Dispose() }
    Reject { & "$PSScriptRoot/audit-arm64-image.ps1" -ImageDirectory $root } 'Foreign JAR native'
    & "$PSScriptRoot/audit-arm64-image.ps1" -ImageDirectory $root -PruneForeignJarNatives
    if ((Get-FileHash "$root/plain.jar" -Algorithm SHA256).Hash -ne $plainHash) { throw 'Untouched JAR bytes changed.' }
    foreach ($javaJar in @('plain.jar','java-native.jar')) {
        & "$javaBin/java.exe" -cp "$root/$javaJar" AuditFixture
        if ($LASTEXITCODE -ne 0) { throw "Java cannot load preserved classes: $javaJar" }
    }
    $jar=[IO.Compression.ZipFile]::OpenRead("$root/runtime.jar")
    try {
        if ($jar.GetEntry('windows_x64/lib.dll')) { throw 'Foreign native retained.' }
        if (-not $jar.GetEntry('windows_aarch64/lib.dll') -or -not $jar.GetEntry('Test.class')) { throw 'Required entry removed.' }
        $stream=$jar.GetEntry('Test.class').Open()
        try { $reader=[IO.BinaryReader]::new($stream); if (($reader.ReadBytes(3) -join ',') -ne '1,2,3') { throw 'Class content changed.' } }
        finally { $stream.Dispose() }
    } finally { $jar.Dispose() }
    & "$PSScriptRoot/audit-arm64-image.ps1" -ImageDirectory $root
    [IO.File]::WriteAllBytes("$root/foreign.dll",(Pe 0x8664))
    Reject { & "$PSScriptRoot/audit-arm64-image.ps1" -ImageDirectory $root } 'Foreign native image'
    Reject { & "$PSScriptRoot/audit-arm64-image.ps1" -ImageDirectory $PSScriptRoot -PruneForeignJarNatives } 'restricted'
    Write-Output 'PASS: JAR pruning rejects/strips foreign natives, preserves ARM64/classes, rejects foreign files and guards output paths.'
} finally {
    $resolved=[IO.Path]::GetFullPath($root)
    if ($resolved.StartsWith($build+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -and
        [IO.Path]::GetFileName($resolved).StartsWith('image-audit-test-')) {
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
