param(
    # -NoTunnel：不开启公网隧道，只走局域网（默认开启 cloudflared 隧道）
    [switch]$NoTunnel,
    # -Stay: 不切回 main 分支，直接在当前分支启动（演示未合入 main 的功能时用）
    [switch]$Stay
)
$ErrorActionPreference = 'Stop'

# 项目根目录（请按需修改）
$ProjectRoot = "C:\Users\origin\IdeaProjects\Agent"
Set-Location $ProjectRoot

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "1. 正在获取远程 main 分支最新代码..." -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
git fetch origin main
if ($LASTEXITCODE -ne 0) {
    Write-Host "[错误] git fetch 失败！" -ForegroundColor Red
    Read-Host "按回车键退出"
    exit 1
}

if ($Stay) {
    Write-Host ""
    Write-Host "============================================================" -ForegroundColor Yellow
    Write-Host "2-3. 已指定 -Stay：跳过切分支与拉取，保持当前分支启动。" -ForegroundColor Yellow
    Write-Host "     （未合入 main 的功能只有这样才能演示）" -ForegroundColor Yellow
    Write-Host "============================================================" -ForegroundColor Yellow
    Write-Host "当前分支：$(git branch --show-current)"
} else {
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "2. 切换到 main 分支..." -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
git checkout main
if ($LASTEXITCODE -ne 0) {
    Write-Host "[错误] 切换分支失败！请先提交或暂存当前更改。" -ForegroundColor Red
    Read-Host "按回车键退出"
    exit 1
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "3. 拉取最新代码到本地 (快进合并)..." -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
git pull --ff-only origin main
if ($LASTEXITCODE -ne 0) {
    Write-Host "[错误] 拉取失败！可能存在冲突或网络问题。" -ForegroundColor Red
    Read-Host "按回车键退出"
    exit 1
}

}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan

Write-Host "4. 正在启动本地服务..." -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
# 默认开启 cloudflared 公网隧道；启动完成后的"按回车停止全部服务"
# 由 start-dev.ps1 自身负责，本脚本不再单独处理回车与停止。
if ($NoTunnel) {
    powershell -ExecutionPolicy Bypass -File "$ProjectRoot\start-dev.ps1" -NoTunnel
} else {
    powershell -ExecutionPolicy Bypass -File "$ProjectRoot\start-dev.ps1"
}
if ($LASTEXITCODE -ne 0) {
    Write-Host "[警告] 启动服务可能失败，请检查服务状态。" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
if ($Stay) {
    Write-Host "流程结束（本次保持在当前分支，未切换到 main）。" -ForegroundColor Green
} else {
    Write-Host "流程结束（本次运行在最新的 main 分支）。" -ForegroundColor Green
}
Write-Host "如服务仍在运行，可执行 stop-dev.ps1 或 stop-main-dev.ps1 回收。" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
