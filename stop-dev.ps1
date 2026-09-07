# ============================================================
#  stop-dev.ps1 - stop backend (8123) and frontend (3000) started
#  by start-dev.ps1. Uses netstat to find the listening PIDs and
#  taskkill to stop them, so it also works when the processes were
#  started another way.
# ============================================================
$ports = @(8123, 3000)
$stopped = $false

foreach ($port in $ports) {
    $pids = @(netstat -ano | Select-String -Pattern ":$port\s.*LISTENING" | ForEach-Object {
        ($_ -split '\s+')[-1]
    } | Sort-Object -Unique)
    foreach ($procId in $pids) {
        if ($procId -and $procId -match '^\d+$' -and [int]$procId -gt 0) {
            $name = (Get-Process -Id ([int]$procId) -ErrorAction SilentlyContinue).ProcessName
            Write-Host "Stopping $name (PID $procId) on port $port"
            taskkill /PID $procId /F /T 2>$null | Out-Null
            $stopped = $true
        }
    }
}

if ($stopped) {
    Write-Host 'All dev services stopped.'
} else {
    Write-Host 'No dev services are listening on ports 8123/3000 - nothing to stop.'
}
