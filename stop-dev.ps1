# ============================================================
#  stop-dev.ps1 - stop backend (8123), frontend (3000) and the
#  optional public tunnel (cpolar/cloudflared/ngrok) started by
#  start-dev.ps1 -Public. Uses PowerShell native cmdlets
#  (Get-NetTCPConnection / Stop-Process) instead of external
#  port-query and process-kill utilities, and re-checks every
#  port after stopping it.
#
#  All output is ASCII-only on purpose: Windows PowerShell 5.1
#  decodes a BOM-less .ps1 with the system ANSI code page, so
#  non-ASCII message text can be misdecoded at parse time.
#
#  Exit codes:
#    0 - every port is free (nothing was listening, or all
#        listeners were stopped and verified free)
#    1 - a port could not be released, or the port state could
#        not be determined at all
#
#  A port that is still occupied is NEVER reported as success.
# ============================================================
$ports = @(8123, 3000)
$failures = New-Object System.Collections.Generic.List[string]
$stoppedAny = $false

# Get-NetTCPConnection raises CmdletizationQuery_NotFound when the port simply
# has no listener. That is not a query failure, and it must not be reported as
# one - otherwise a clean machine would always look broken. Any other error id
# means the port state really is unknown, which is a failure.
function Test-NoListenerError($errorRecord) {
    return $errorRecord.FullyQualifiedErrorId -like 'CmdletizationQuery_NotFound*'
}

function Get-ListeningPids {
    param([Parameter(Mandatory = $true)][int]$Port)

    try {
        $conns = @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction Stop)
    } catch {
        if (Test-NoListenerError $_) {
            return @()
        }
        $failures.Add("Unable to query port ${Port}: $($_.Exception.Message)")
        return @()
    }
    return @($conns | ForEach-Object { $_.OwningProcess } | Where-Object { $_ -gt 0 } | Sort-Object -Unique)
}

function Test-PortListening {
    param([Parameter(Mandatory = $true)][int]$Port)

    try {
        return [bool](Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction Stop | Select-Object -First 1)
    } catch {
        if (Test-NoListenerError $_) {
            return $false
        }
        $failures.Add("Unable to re-check port ${Port}: $($_.Exception.Message)")
        return $true
    }
}

foreach ($port in $ports) {
    $pids = Get-ListeningPids -Port $port
    if ($pids.Count -eq 0) {
        Write-Host "Port $port has no listener"
        continue
    }

    foreach ($procId in $pids) {
        $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
        $procName = if ($proc) { $proc.ProcessName } else { '<unknown>' }
        Write-Host "Stopping $procName (PID $procId) on port $port"
        try {
            Stop-Process -Id $procId -Force -ErrorAction Stop
            $stoppedAny = $true
        } catch {
            $failures.Add("Failed to stop $procName (PID $procId) on port ${port}: $($_.Exception.Message)")
        }
    }

    Start-Sleep -Seconds 2
    if (Test-PortListening -Port $port) {
        $failures.Add("Port $port is still listening and could not be released")
    } else {
        Write-Host "Port $port released"
    }
}

# ------------------------------------------------------------
#  Stop the public tunnel started by start-dev.ps1 -Public.
#  Its PID is written to tmp/tunnel.pid at startup.
# ------------------------------------------------------------
$tunnelPidFile = Join-Path $PSScriptRoot 'tmp\tunnel.pid'
if (Test-Path $tunnelPidFile) {
    $rawPid = (Get-Content $tunnelPidFile -ErrorAction SilentlyContinue | Select-Object -First 1)
    $tunnelPid = 0
    if ([int]::TryParse($rawPid, [ref]$tunnelPid) -and $tunnelPid -gt 0) {
        $tunnelProc = Get-Process -Id $tunnelPid -ErrorAction SilentlyContinue
        if ($tunnelProc) {
            Write-Host "Stopping public tunnel $($tunnelProc.ProcessName) (PID $tunnelPid)"
            try {
                Stop-Process -Id $tunnelPid -Force -ErrorAction Stop
            } catch {
                $failures.Add("Failed to stop tunnel PID ${tunnelPid}: $($_.Exception.Message)")
            }
        } else {
            Write-Host "Tunnel PID $tunnelPid is no longer running"
        }
    }
    Remove-Item $tunnelPidFile -Force -ErrorAction SilentlyContinue
}

if ($failures.Count -gt 0) {
    Write-Host ''
    Write-Host 'The following ports could not be stopped - handle them manually and retry:'
    foreach ($failure in $failures) {
        Write-Host "  - $failure"
    }
    exit 1
}

if ($stoppedAny) {
    Write-Host 'All dev services stopped.'
} else {
    Write-Host 'No dev services are listening on ports 8123/3000 - nothing to stop.'
}
exit 0
