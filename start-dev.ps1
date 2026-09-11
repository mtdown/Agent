# ============================================================
#  start-dev.ps1 - one-click start for backend + frontend (dev)
#  Usage:  powershell -ExecutionPolicy Bypass -File .\start-dev.ps1
#  Starts: Spring Boot backend (127.0.0.1:8123) + Vite dev server
#          (127.0.0.1:3000). Logs go to tmp/dev-backend.log and
#          tmp/dev-frontend.log.
# ============================================================
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

Write-Host "==> [4/4] Starting frontend dev server on 0.0.0.0:$frontPort (LAN accessible) ..."
$frontLog = Join-Path $tmpDir 'dev-frontend.log'
$frontErr = Join-Path $tmpDir 'dev-frontend.err.log'
$frontProc = Start-Process -FilePath 'cmd.exe' `
    -ArgumentList @('/c', "npm run dev -- --port $frontPort --host 0.0.0.0") `
    -WorkingDirectory $frontDir `
    -RedirectStandardOutput $frontLog `
    -RedirectStandardError $frontErr `
    -WindowStyle Hidden -PassThru
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
$lanIp = (Get-NetIPAddress -AddressFamily IPv4 |
    Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' } |
    Sort-Object InterfaceIndex | Select-Object -First 1).IPAddress
Write-Host 'All done. (admin / 12345678)'
Write-Host "  PC     : http://127.0.0.1:$frontPort"
if ($lanIp) { Write-Host "  Phone  : http://$lanIp:$frontPort   (same WiFi)" }
Write-Host ''
Write-Host 'Need access from outside the LAN? (front & back now share one port)'
Write-Host "  cpolar http $frontPort        # open the https://*.cpolar.cn URL it prints"
Write-Host '  (stop the tunnel after the demo)'
Write-Host ''
Write-Host 'Stop everything with: powershell -ExecutionPolicy Bypass -File .\stop-dev.ps1'
