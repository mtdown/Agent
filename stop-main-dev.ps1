# ============================================================
#  stop-main-dev.ps1 - stop the services started by
#  start-main-dev.ps1 (backend 8123 + frontend 3000 + tunnel).
#
#  main acceptance runs directly in the project root
#  (start-main-dev.ps1 checks out main there), so stopping is
#  simply delegating to the root stop-dev.ps1, which also stops
#  the public tunnel recorded in tmp/tunnel.pid.
#
#  All output is ASCII-only (see start-dev.ps1 for the reason).
# ============================================================
$ErrorActionPreference = 'Stop'
$ProjectRoot = "C:\Users\origin\IdeaProjects\Agent"
Set-Location $ProjectRoot

powershell -ExecutionPolicy Bypass -File .\stop-dev.ps1
exit $LASTEXITCODE
