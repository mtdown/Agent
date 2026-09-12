# ============================================================
#  start-dev.ps1 - one-click start for backend + frontend (dev)
#  Usage:  powershell -ExecutionPolicy Bypass -File .\start-dev.ps1
#          powershell -ExecutionPolicy Bypass -File .\start-dev.ps1 -Public
#  Starts: Spring Boot backend (127.0.0.1:8123) + Vite dev server
#          (0.0.0.0:3000, LAN accessible). Logs go to tmp/dev-backend.log
#          and tmp/dev-frontend.log.
#
#  Tunnel is ON by default: if cpolar/cloudflared/ngrok is installed, the
#  site is exposed to the internet right away (use -NoTunnel to skip).
#  Front and back now share one port (the vite dev server proxies /api to
#  8123), so tunneling the frontend port alone is enough. The tunnel PID
#  is written to tmp/tunnel.pid and .\stop-dev.ps1 stops it too.
#
#  -Public    kept for compatibility (tunnel is already the default)
#  -NoTunnel  stay on the LAN only, do not expose anything publicly
#  -Tunnel    cpolar | cloudflared | ngrok | none  (default: auto-detect)
#
#  NOTE: keep every string literal ASCII-only. Windows PowerShell 5.1
#        decodes a BOM-less .ps1 with the system ANSI code page, so
#        non-ASCII message text can be misdecoded at parse time.
# ============================================================
param(
    # Kept for compatibility: the tunnel now starts by default.
    [switch]$Public,
    [string]$Tunnel = 'auto',
    # -NoTunnel / -Tunnel none : stay on the LAN only (no public exposure)
    [switch]$NoTunnel,
    # -Preview: build first and serve the bundle instead of the dev server.
    # Use this for public tunnels - the dev server fires hundreds of module
    # requests and each one pays a round trip through the tunnel.
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

function Port-InUse($port) {
    return [bool](Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)
}

Write-Host "==> [1/4] Checking prerequisites ..."
if (-not (Port-InUse 3307)) { Write-Host '    WARN: MySQL (3307) not listening. Start the Docker MySQL container first.' }
if (-not (Port-InUse 6379)) { Write-Host '    WARN: Redis (6379) not listening. Start the Docker Redis container first.' }
function Assert-PortFree($port) {
    $owner = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $owner) {
        return
    }
    $proc = Get-Process -Id $owner.OwningProcess -ErrorAction SilentlyContinue
    $procName = if ($proc) { $proc.ProcessName } else { '<unknown>' }
    Write-Host "    ERROR: port $port already in use by $procName (PID $($owner.OwningProcess))."
    Write-Host '           Run .\stop-dev.ps1 first, or stop that process manually, then rerun. Aborting.'
    exit 1
}

Assert-PortFree $backendPort
Assert-PortFree $frontPort
$javaExe = Find-Java
if (-not $javaExe) { Write-Host '    ERROR: java not found. Set JAVA_HOME or install JDK 17.'; exit 1 }

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
    Write-Host '    ERROR: backend did not start in time. See tmp/dev-backend.log'; exit 1
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
#  Public tunnel (optional, -Public)
# ------------------------------------------------------------
$tunnelPidFile = Join-Path $tmpDir 'tunnel.pid'
$hostPattern = 'https?://[A-Za-z0-9\-_.]+\.(cpolar\.cn|cpolar\.io|ngrok-free\.app|ngrok\.io|trycloudflare\.com)'

function Get-TunnelPublicUrl {
    param([string]$Tool = '')
    # cpolar 2.x -> 9200/api/v1/tunnels ; cpolar 1.x / ngrok -> 4040/api/tunnels
    # The system proxy intercepts 127.0.0.1 too (shows up as a bogus 502),
    # so neutralise the default web proxy for the duration of the call.
    # cloudflared has no local API, and a resident cpolar service answering on
    # 9200 would shadow it - so for cloudflared skip the API probe entirely
    # and let the caller scrape cloudflared's own output instead.
    $savedProxy = [System.Net.WebRequest]::DefaultWebProxy
    [System.Net.WebRequest]::DefaultWebProxy = New-Object System.Net.WebProxy
    try {
        if ($Tool -ne 'cloudflared') {
            foreach ($uri in @('http://127.0.0.1:9200/api/v1/tunnels', 'http://127.0.0.1:4040/api/tunnels')) {
                try {
                    $resp = Invoke-RestMethod -Uri $uri -TimeoutSec 3 -ErrorAction Stop
                    $json = $resp | ConvertTo-Json -Depth 8 -Compress
                    $m = [regex]::Matches($json, $hostPattern)
                    if ($m.Count -gt 0) {
                        $https = @($m | ForEach-Object { $_.Value } | Where-Object { $_ -like 'https*' })
                        if ($https.Count -gt 0) { return $https[0] }
                        return $m[0].Value
                    }
                } catch { }
            }
        }
    } finally {
        [System.Net.WebRequest]::DefaultWebProxy = $savedProxy
    }
    return $null
}

function Start-PublicTunnel {
    param([int]$Port)

    $tool = $null
    if ($Tunnel -in @('cpolar', 'cloudflared', 'ngrok')) {
        if (Get-Command $Tunnel -ErrorAction SilentlyContinue) { $tool = $Tunnel }
        else { Write-Host "    WARN: '$Tunnel' not found in PATH." }
    } elseif ($Tunnel -eq 'none') {
        return
    } else {
        foreach ($t in @('cpolar', 'cloudflared', 'ngrok')) {
            if (Get-Command $t -ErrorAction SilentlyContinue) { $tool = $t; break }
        }
    }
    if (-not $tool) {
        Write-Host '    No tunnel tool found - LAN access only.'
        Write-Host '    Install one: cpolar (https://www.cpolar.com) | cloudflared | ngrok'
        return
    }

    $toolArgs = switch ($tool) {
        'cloudflared' { @('tunnel', '--url', "http://127.0.0.1:$($Port)") }
        default       { @('http', "$($Port)") }
    }
    $tunnelLog = Join-Path $tmpDir "tunnel-$tool.log"
    $tunnelErr = Join-Path $tmpDir "tunnel-$tool.err.log"

    Write-Host "==> Starting public tunnel via $tool on port $Port ..."
    try {
        $proc = Start-Process -FilePath $tool -ArgumentList $toolArgs `
            -RedirectStandardOutput $tunnelLog -RedirectStandardError $tunnelErr `
            -WindowStyle Minimized -PassThru -ErrorAction Stop
    } catch {
        Write-Host "    ERROR: failed to start $tool - $($_.Exception.Message)"
        return
    }
    Set-Content -Path $tunnelPidFile -Value $proc.Id -Encoding ASCII
    Write-Host "    tunnel PID: $($proc.Id) (log: $tunnelLog)"

    $publicUrl = $null
    for ($i = 0; $i -lt 20; $i++) {
        Start-Sleep -Seconds 1
            $publicUrl = Get-TunnelPublicUrl -Tool $tool
        if ($publicUrl) { break }
        # A tool that dies at once will never serve an API (e.g. cpolar without authtoken)
        if ($i -ge 2 -and $proc.HasExited) { break }
    }
    # cloudflared has no local API - fall back to scraping its own output
    if (-not $publicUrl) {
        foreach ($f in @($tunnelLog, $tunnelErr)) {
            if (-not (Test-Path $f)) { continue }
            $txt = Get-Content $f -Raw -ErrorAction SilentlyContinue
            if (-not $txt) { continue }
            $m = [regex]::Match($txt, $hostPattern)
            if ($m.Success) { $publicUrl = $m.Value; break }
        }
    }
    if ($publicUrl) {
        Write-Host "  Public : $publicUrl" -ForegroundColor Green
        Write-Host '           (random domain changes on every restart; keep the tunnel window open)'
    } else {
        Write-Host "    Tunnel process started, but no public URL was obtained."
        $joined = ''
        foreach ($f in @($tunnelLog, $tunnelErr)) {
            if (Test-Path $f) { $joined += (Get-Content $f -Raw -ErrorAction SilentlyContinue) }
        }
        if ($proc.HasExited) {
            Write-Host "    NOTE: $tool exited immediately (exit code $($proc.ExitCode))." -ForegroundColor Yellow
        }
        if ($joined -match 'authtoken') {
            Write-Host "    => $tool is not authenticated yet (log says: $($joined.Trim()))." -ForegroundColor Yellow
            Write-Host "       Fix once :  cpolar authtoken <your-token>" -ForegroundColor Yellow
            Write-Host "       Token    :  https://dashboard.cpolar.com (free signup)" -ForegroundColor Yellow
            Write-Host "       No account? use the registration-free tool instead:" -ForegroundColor Yellow
            Write-Host "         winget install Cloudflare.cloudflared" -ForegroundColor Yellow
            Write-Host "         then rerun: .\\start-dev.ps1 -Tunnel cloudflared" -ForegroundColor Yellow
        } else {
            Write-Host "    Check the minimized $tool window, or: Get-Content $tunnelLog"
        }
    }
    return $publicUrl
}

$publicUrl = $null
$wantTunnel = -not ($NoTunnel -or $Tunnel -eq 'none')
if ($wantTunnel) {
    # Default is ON: if a tunnel tool is installed the site goes public.
    # Use -NoTunnel to keep it on the LAN only.
    $publicUrl = Start-PublicTunnel -Port $frontPort
} else {
    Write-Host 'Public tunnel disabled (-NoTunnel) - LAN access only.'
}
Write-Host ''
if ($publicUrl) {
    Write-Host '================================================================' -ForegroundColor Green
    Write-Host "  Public URL (share this) : $publicUrl" -ForegroundColor Green
    Write-Host '================================================================' -ForegroundColor Green
}
Write-Host ''
Write-Host 'Stop everything with: powershell -ExecutionPolicy Bypass -File .\stop-dev.ps1'
if (-not $NoPause) {
    Write-Host ''
    Read-Host 'Press Enter to close this window (services keep running)'
}
