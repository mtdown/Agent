# ============================================================
#  start-dev.ps1 - one-click start for backend + frontend (dev)
#  Usage:  powershell -ExecutionPolicy Bypass -File .\start-dev.ps1
#  Starts: Spring Boot backend (127.0.0.1:8123) + Vite dev server
#          (0.0.0.0:3000, LAN accessible). Logs go to tmp/dev-backend.log
#          and tmp/dev-frontend.log.
#
#  Public tunnel (cloudflared quick tunnel) is ON by default; the
#  public URL is scraped from the cloudflared log and shown in a
#  green box together with the local port it forwards. Front and
#  back share one port (the vite dev server proxies /api to 8123),
#  so tunneling the frontend port alone is enough. The tunnel PID
#  is written to tmp/tunnel.pid.
#
#  If ports 8123/3000 are already occupied by leftover services,
#  they are stopped automatically and startup continues; only an
#  unkillable occupant aborts the run.
#
#  At the end the script waits for Enter:
#    - Press Enter  -> stop backend + frontend + tunnel, then exit.
#    - Close window -> everything keeps running (recover later
#      with .\stop-dev.ps1).
#
#  -NoTunnel  stay on the LAN only, do not expose anything publicly
#  -Preview   build first and serve the bundle instead of the dev
#             server (far fewer requests through the tunnel)
#  -NoPause   do not wait for Enter at the end (used by parent
#             scripts; services then keep running)
#
#  NOTE: keep every string literal ASCII-only. Windows PowerShell 5.1
#        decodes a BOM-less .ps1 with the system ANSI code page, so
#        non-ASCII message text can be misdecoded at parse time.
# ============================================================
param(
    # -NoTunnel / : stay on the LAN only (no public exposure)
    [switch]$NoTunnel,
    # -Preview: build first and serve the bundle instead of the dev server.
    [switch]$Preview,
    # -NoPause: do not wait for Enter at the end (used by parent scripts)
    [switch]$NoPause
)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$cloudDir = Join-Path $root 'cloud'
$frontDir = Join-Path $root 'cloud_front'
$tmpDir = Join-Path $root 'tmp'
New-Item -ItemType Directory -Force -Path $tmpDir | Out-Null

$backendPort = 8123
$frontPort = 3000
$tunnelPidFile = Join-Path $tmpDir 'tunnel.pid'

function Find-Java {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin\java.exe') }
    $candidates += 'C:\Program Files\Java\jdk-17\bin\java.exe'
    $candidates += 'C:\Program Files\Java\jdk-11\bin\java.exe'
    foreach ($c in $candidates) { if (Test-Path $c) { return $c } }
    $g = Get-Command java -ErrorAction SilentlyContinue
    if ($g) { return $g.Source }
    return $null
}

function Get-PortListenerPids {
    param([Parameter(Mandatory = $true)][int]$Port)
    $conns = @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
    return @($conns | ForEach-Object { $_.OwningProcess } | Where-Object { $_ -gt 0 } | Sort-Object -Unique)
}

function Port-InUse($port) {
    return [bool](Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)
}

function Stop-DevTunnel {
    if (-not (Test-Path $tunnelPidFile)) { return }
    $rawTunnelPid = (Get-Content $tunnelPidFile -ErrorAction SilentlyContinue | Select-Object -First 1)
    $tunnelProcId = 0
    if ([int]::TryParse($rawTunnelPid, [ref]$tunnelProcId) -and $tunnelProcId -gt 0) {
        $tunnelProc = Get-Process -Id $tunnelProcId -ErrorAction SilentlyContinue
        if ($tunnelProc) {
            Write-Host "    Stopping public tunnel $($tunnelProc.ProcessName) (PID $tunnelProcId)"
            try { Stop-Process -Id $tunnelProcId -Force -ErrorAction Stop }
            catch { Write-Host "    WARN: could not stop tunnel PID ${tunnelProcId}: $($_.Exception.Message)" }
        } else {
            Write-Host "    Tunnel PID $tunnelProcId is no longer running"
        }
    }
    Remove-Item $tunnelPidFile -Force -ErrorAction SilentlyContinue
}

# Stop whatever listens on one port; returns $true only when the port
# ended up free (or nothing was listening in the first place).
function Clear-DevPort {
    param([Parameter(Mandatory = $true)][int]$Port)
    $listenerPids = Get-PortListenerPids -Port $Port
    if ($listenerPids.Count -eq 0) { return $true }
    foreach ($procId in $listenerPids) {
        $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
        $procName = if ($proc) { $proc.ProcessName } else { '<unknown>' }
        Write-Host "    Port $Port in use by $procName (PID $procId) - stopping it ..."
        try { Stop-Process -Id $procId -Force -ErrorAction Stop }
        catch { Write-Host "    ERROR: cannot stop $procName (PID $procId): $($_.Exception.Message)" }
    }
    Start-Sleep -Seconds 2
    if ((Get-PortListenerPids -Port $Port).Count -gt 0) {
        Write-Host "    ERROR: port $Port still occupied after cleanup - stop the process manually and rerun."
        return $false
    }
    Write-Host "    Port $Port released"
    return $true
}

# Full stop used by the Enter handler: backend + frontend + tunnel.
function Stop-DevServices {
    Write-Host '==> Stopping dev services (backend, frontend, tunnel) ...'
    foreach ($port in @($backendPort, $frontPort)) {
        $listenerPids = Get-PortListenerPids -Port $port
        if ($listenerPids.Count -eq 0) {
            Write-Host "    Port $port has no listener"
            continue
        }
        foreach ($procId in $listenerPids) {
            $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
            $procName = if ($proc) { $proc.ProcessName } else { '<unknown>' }
            Write-Host "    Stopping $procName (PID $procId) on port $port"
            try { Stop-Process -Id $procId -Force -ErrorAction Stop }
            catch { Write-Host "    ERROR: failed to stop $procName (PID $procId): $($_.Exception.Message)" }
        }
    }
    Stop-DevTunnel
    Start-Sleep -Seconds 2
    $busy = @()
    foreach ($port in @($backendPort, $frontPort)) {
        if ((Get-PortListenerPids -Port $port).Count -gt 0) { $busy += $port }
    }
    if ($busy.Count -eq 0) {
        Write-Host 'All dev services stopped.'
    } else {
        Write-Host "    ERROR: ports still busy: $($busy -join ', '). Run .\stop-dev.ps1 or stop them manually."
    }
}

Write-Host "==> [1/4] Checking prerequisites ..."
if (-not (Port-InUse 3307)) { Write-Host '    WARN: MySQL (3307) not listening. Start the Docker MySQL container first.' }
if (-not (Port-InUse 6379)) { Write-Host '    WARN: Redis (6379) not listening. Start the Docker Redis container first.' }
# Leftover services from a previous run are cleaned automatically;
# a leftover tunnel would point at a dead port, so it is killed too.
Stop-DevTunnel
$portsClear = $true
foreach ($p in @($backendPort, $frontPort)) {
    if (-not (Clear-DevPort -Port $p)) { $portsClear = $false }
}
if (-not $portsClear) { exit 1 }
$javaExe = Find-Java
if (-not $javaExe) { Write-Host '    ERROR: java not found. Set JAVA_HOME or install JDK 17.'; exit 1 }

# Load local secrets (.env.dev, git-ignored): each KEY=VALUE line becomes a
# process env var so the backend jar started below inherits it. Values may be
# quoted ("v" or 'v'); blank lines and # comments are skipped. Only variable
# NAMES are printed - never the values.
function Import-DevEnvFile {
    $envFile = Join-Path $root '.env.dev'
    if (-not (Test-Path $envFile)) {
        Write-Host '    NOTE: .env.dev not found - no RAG_* secrets injected (RAG embedding stays unconfigured).'
        return
    }
    $loaded = @()
    # ReadAllLines (UTF-8) instead of Get-Content (ANSI in PS 5.1): non-ASCII
    # bytes must never corrupt line splitting here, or keys get silently lost.
    foreach ($line in [System.IO.File]::ReadAllLines($envFile)) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#')) { continue }
        $idx = $trimmed.IndexOf('=')
        if ($idx -le 0) { continue }
        $key = $trimmed.Substring(0, $idx).Trim()
        $value = $trimmed.Substring($idx + 1).Trim()
        if ($value.Length -ge 2 -and
            (($value.StartsWith('"') -and $value.EndsWith('"')) -or
             ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        if ($key.Length -eq 0) { continue }
        Set-Item -Path ("Env:" + $key) -Value $value
        $loaded += $key
    }
    if ($loaded.Count -gt 0) {
        Write-Host ("    .env.dev injected env vars: " + ($loaded -join ', '))
    } else {
        Write-Host '    WARN: .env.dev exists but contains no KEY=VALUE entries.'
    }
}
Import-DevEnvFile

Write-Host "==> [2/4] Building backend jar (mvn -DskipTests package) ..."
Push-Location $cloudDir
try { mvn -DskipTests package -q; if ($LASTEXITCODE -ne 0) { throw 'maven build failed' } }
finally { Pop-Location }

Write-Host "==> [3/4] Starting backend on 127.0.0.1:$backendPort ..."
$jar = Join-Path $cloudDir 'target\cloud-0.0.1-SNAPSHOT.jar'
$backendLog = Join-Path $tmpDir 'dev-backend.log'
$backendErr = Join-Path $tmpDir 'dev-backend.err.log'
$backendProc = Start-Process -FilePath $javaExe `
    -ArgumentList @('-jar', "`"$jar`"", '--spring.profiles.active=local') `
    -WorkingDirectory $cloudDir `
    -RedirectStandardOutput $backendLog `
    -RedirectStandardError $backendErr `
    -WindowStyle Hidden -PassThru
Write-Host "    backend PID: $($backendProc.Id) (log: $backendLog)"

$frontLog = Join-Path $tmpDir 'dev-frontend.log'
$frontErr = Join-Path $tmpDir 'dev-frontend.err.log'
if ($Preview) {
    Write-Host '==> [4/4] Building frontend bundle (npm run build) - needed for public tunnels ...'
    # PS 5.1 Start-Process rejects identical stdout/stderr redirect files,
    # so they must be two separate logs.
    $buildLog = Join-Path $tmpDir 'dev-frontend-build.log'
    $buildErr = Join-Path $tmpDir 'dev-frontend-build.err.log'
    Start-Process -FilePath 'cmd.exe' -ArgumentList @('/c', 'npm run build') `
        -WorkingDirectory $frontDir -RedirectStandardOutput $buildLog `
        -RedirectStandardError $buildErr -WindowStyle Hidden -Wait
    $distIndex = Join-Path $frontDir 'dist\index.html'
    if (-not (Test-Path $distIndex)) {
        Write-Host "    ERROR: build failed (no dist\index.html). See $buildLog / $buildErr"
        Stop-DevServices
        exit 1
    }
    Write-Host "    build ok (log: $buildLog)"
    Write-Host "==> [4/4] Serving production bundle on 0.0.0.0:$frontPort ..."
    $frontProc = Start-Process -FilePath 'cmd.exe' `
        -ArgumentList @('/c', "npx vite preview --port $frontPort --host 0.0.0.0 --strictPort") `
        -WorkingDirectory $frontDir `
        -RedirectStandardOutput $frontLog `
        -RedirectStandardError $frontErr `
        -WindowStyle Hidden -PassThru
} else {
    Write-Host "==> [4/4] Starting frontend dev server on 0.0.0.0:$frontPort (LAN accessible) ..."
    $frontProc = Start-Process -FilePath 'cmd.exe' `
        -ArgumentList @('/c', "npm run dev -- --port $frontPort --host 0.0.0.0") `
        -WorkingDirectory $frontDir `
        -RedirectStandardOutput $frontLog `
        -RedirectStandardError $frontErr `
        -WindowStyle Hidden -PassThru
}
Write-Host "    frontend launcher PID: $($frontProc.Id) (log: $frontLog)"

Write-Host ''
Write-Host 'Waiting for services to come up ...'
$backendUp = $false
$frontUp = $false
for ($i = 0; $i -lt 90; $i++) {
    if (-not $backendUp -and (Port-InUse $backendPort)) { $backendUp = $true; Write-Host "    backend up on $backendPort" }
    if (-not $frontUp -and (Port-InUse $frontPort)) { $frontUp = $true; Write-Host "    frontend up on $frontPort" }
    if ($backendUp -and $frontUp) { break }
    Start-Sleep -Seconds 1
}
if (-not $backendUp) {
    Write-Host '    ERROR: backend did not start in time. See tmp/dev-backend.log'
    Stop-DevServices
    exit 1
}
if (-not $frontUp) {
    Write-Host '    WARN: frontend not up yet, check tmp/dev-frontend.log'
}
Write-Host ''
# Pick the adapter that owns the default route. Sorting by InterfaceIndex
# picks a virtual adapter (VMware / Hyper-V, e.g. 192.168.206.1) whose
# subnet the phone is not on - that address looks valid but is unreachable.
$lanIp = $null
$lanAdapter = $null
$defRoute = Get-NetRoute -DestinationPrefix '0.0.0.0/0' -ErrorAction SilentlyContinue |
    Where-Object { $_.NextHop -ne '0.0.0.0' } |
    Sort-Object RouteMetric | Select-Object -First 1
if ($defRoute) {
    $addr = Get-NetIPAddress -InterfaceIndex $defRoute.InterfaceIndex -AddressFamily IPv4 -ErrorAction SilentlyContinue |
        Where-Object { $_.IPAddress -notlike '169.254.*' } | Select-Object -First 1
    if ($addr) {
        $lanIp = $addr.IPAddress
        $netAdapter = Get-NetAdapter -InterfaceIndex $defRoute.InterfaceIndex -ErrorAction SilentlyContinue
        if ($netAdapter) { $lanAdapter = $netAdapter.Name }
    }
}
if (-not $lanIp) {
    $lanIp = (Get-NetIPAddress -AddressFamily IPv4 |
        Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' } |
        Sort-Object InterfaceIndex | Select-Object -First 1).IPAddress
}
Write-Host 'All done. (admin / 12345678)'
Write-Host "  PC     : http://127.0.0.1:$frontPort"
# NOTE: $($lanIp) - an unbraced `$lanIp:$frontPort` parses as a scoped variable and breaks the script
if ($lanIp) {
    Write-Host "  Phone  : http://$($lanIp):$($frontPort)   (same WiFi$(if ($lanAdapter) { " / via '$lanAdapter'" }))"
}
# ------------------------------------------------------------
#  Public tunnel via cloudflared (quick tunnel, registration-free).
#  cloudflared has no local API to query, so the public URL is
#  scraped from its own log output.
# ------------------------------------------------------------
$hostPattern = 'https?://[A-Za-z0-9\-_.]+\.trycloudflare\.com'

function Start-PublicTunnel {
    param([int]$Port)

    $tool = 'cloudflared'
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
        Write-Host '    cloudflared not found in PATH - no public tunnel, LAN access only.'
        Write-Host '    Install once:  winget install Cloudflare.cloudflared'
        return $null
    }

    $toolArgs = @('tunnel', '--url', "http://127.0.0.1:$($Port)")
    $tunnelLog = Join-Path $tmpDir "tunnel-$tool.log"
    $tunnelErr = Join-Path $tmpDir "tunnel-$tool.err.log"

    Write-Host "==> Starting public tunnel via $tool on port $Port ..."
    try {
        $proc = Start-Process -FilePath $tool -ArgumentList $toolArgs `
            -RedirectStandardOutput $tunnelLog -RedirectStandardError $tunnelErr `
            -WindowStyle Minimized -PassThru -ErrorAction Stop
    } catch {
        Write-Host "    ERROR: failed to start $tool - $($_.Exception.Message)"
        return $null
    }
    Set-Content -Path $tunnelPidFile -Value $proc.Id -Encoding ASCII
    Write-Host "    tunnel PID: $($proc.Id) (log: $tunnelLog)"

    $publicUrl = $null
    for ($i = 0; $i -lt 25; $i++) {
        Start-Sleep -Seconds 1
        if ($proc.HasExited) { break }
        foreach ($f in @($tunnelLog, $tunnelErr)) {
            if (-not (Test-Path $f)) { continue }
            $txt = Get-Content $f -Raw -ErrorAction SilentlyContinue
            if (-not $txt) { continue }
            $m = [regex]::Match($txt, $hostPattern)
            if ($m.Success) { $publicUrl = $m.Value; break }
        }
        if ($publicUrl) { break }
    }
    if ($publicUrl) {
        Write-Host "  Public : $publicUrl" -ForegroundColor Green
    } else {
        Write-Host '    Tunnel process started, but no public URL was obtained.'
        if ($proc.HasExited) {
            Write-Host "    NOTE: cloudflared exited immediately (exit code $($proc.ExitCode))." -ForegroundColor Yellow
        }
        Write-Host "    Check the minimized cloudflared window, or: Get-Content $tunnelErr"
    }
    return $publicUrl
}

$publicUrl = $null
if (-not $NoTunnel) {
    # Default is ON: the site is exposed via cloudflared. Use -NoTunnel
    # to keep it on the LAN only.
    $publicUrl = Start-PublicTunnel -Port $frontPort
} else {
    Write-Host 'Public tunnel disabled (-NoTunnel) - LAN access only.'
}
Write-Host ''
if ($publicUrl) {
    Write-Host '================================================================' -ForegroundColor Green
    Write-Host "  Public URL (share this) : $publicUrl" -ForegroundColor Green
    Write-Host "  Forwards to local port  : $frontPort (frontend; /api proxied to backend)" -ForegroundColor Green
    Write-Host '  (random domain changes on every restart)' -ForegroundColor Green
    Write-Host '================================================================' -ForegroundColor Green
}
Write-Host ''
if ($NoPause) {
    Write-Host 'Services keep running (started with -NoPause).'
    Write-Host 'Stop later with: powershell -ExecutionPolicy Bypass -File .\stop-dev.ps1'
} else {
    Write-Host 'Press Enter to STOP all services (backend + frontend + tunnel).'
    Write-Host 'Close this window directly to KEEP everything running'
    Write-Host '(recover later with .\stop-dev.ps1).'
    Read-Host 'Press Enter to stop' | Out-Null
    Write-Host ''
    Stop-DevServices
}
