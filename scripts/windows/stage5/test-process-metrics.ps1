$ErrorActionPreference='Stop'
. "$PSScriptRoot/../stage3/ProcessMetrics.ps1"
# Out-of-order descendants, nested WebView workers, unrelated processes and a cycle.
$records=@(
    @{ProcessId=5;ParentProcessId=4}, @{ProcessId=4;ParentProcessId=2},
    @{ProcessId=2;ParentProcessId=1}, @{ProcessId=99;ParentProcessId=98},
    @{ProcessId=98;ParentProcessId=99})
$ids=Get-NuvioProcessTreeIds -RootProcessId 1 -Processes $records
if ($ids.Count -ne 4 -or -not $ids.Contains(5) -or $ids.Contains(99)) { throw 'Process tree traversal lost descendants or included unrelated processes.' }
# Verify the real collector returns a current process observation and bounded baseline keys.
$previous=@{'retired:1'=100.0}
$sample=Get-NuvioProcessTreeMetrics -RootProcessId $PID -PreviousCpu $previous
if ($sample.processCount -lt 1 -or $sample.workingSetBytes -le 0 -or $sample.privateBytes -le 0 -or $previous.ContainsKey('retired:1')) {
    throw 'Process observation/baseline cleanup failed.'
}
Write-Host 'PASS: process descendants, unrelated/cyclic trees, real metrics and retired CPU-baseline cleanup'
