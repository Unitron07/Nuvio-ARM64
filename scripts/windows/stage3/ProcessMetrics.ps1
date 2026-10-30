# Best-effort process tree accounting. No command lines, executable paths or names are exported.
function Get-NuvioProcessTreeIds {
    param([int]$RootProcessId, [object[]]$Processes)
    $ids=[Collections.Generic.HashSet[int]]::new()
    [void]$ids.Add($RootProcessId)
    do {
        $changed=$false
        foreach ($entry in $Processes) {
            if ($ids.Contains([int]$entry.ParentProcessId) -and $ids.Add([int]$entry.ProcessId)) { $changed=$true }
        }
    } while ($changed)
    return ,$ids
}
function Get-NuvioProcessTreeMetrics {
    param([int]$RootProcessId, [hashtable]$PreviousCpu)
    $scope='process-tree'
    try {
        $tree=Get-NuvioProcessTreeIds -RootProcessId $RootProcessId -Processes @(
            Get-CimInstance Win32_Process -Property ProcessId,ParentProcessId -ErrorAction Stop)
    } catch {
        $tree=@($RootProcessId)
        $scope='launcher-only'
    }
    $totals=[ordered]@{cpuSeconds=0.0;workingSetBytes=0L;privateBytes=0L;handles=0L;threads=0L;processCount=0;scope=$scope}
    $alive=[Collections.Generic.HashSet[string]]::new()
    foreach ($processId in $tree) {
        $item=$null
        try {
            $item=[Diagnostics.Process]::GetProcessById($processId)
            $key="$($item.Id):$($item.StartTime.ToUniversalTime().Ticks)"
            $cpu=$item.TotalProcessorTime.TotalSeconds
            $old=if ($PreviousCpu.ContainsKey($key)) { $PreviousCpu[$key] } else { 0.0 }
            $totals.cpuSeconds += [Math]::Max(0.0,$cpu-$old)
            $totals.workingSetBytes += $item.WorkingSet64
            $totals.privateBytes += $item.PrivateMemorySize64
            $totals.handles += $item.HandleCount
            $totals.threads += $item.Threads.Count
            $totals.processCount++
            $PreviousCpu[$key]=$cpu
            [void]$alive.Add($key)
        } catch { } # Exited/inaccessible workers are missing observations, not zero-cost proof.
        finally { if ($null -ne $item) { $item.Dispose() } }
    }
    foreach ($key in @($PreviousCpu.Keys)) { if (-not $alive.Contains($key)) { $PreviousCpu.Remove($key) } }
    return $totals
}
