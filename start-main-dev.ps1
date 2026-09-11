param(
    # -Public: 同时启动公网穿透（参数透传给 start-dev.ps1）
    [switch]$Public,
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
if ($Public) {
    powershell -ExecutionPolicy Bypass -File "$ProjectRoot\start-dev.ps1" -Public -NoPause
} else {
    powershell -ExecutionPolicy Bypass -File "$ProjectRoot\start-dev.ps1" -NoPause
}
if ($LASTEXITCODE -ne 0) {
    Write-Host "[警告] 启动服务可能失败，请检查服务状态。" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
if ($Stay) {
    Write-Host "服务已启动（保持在当前分支，未切换到 main）。" -ForegroundColor Green
} else {
    Write-Host "服务已启动，当前运行在最新的 main 分支。" -ForegroundColor Green
}
Write-Host "按回车键将停止服务并退出脚本。" -ForegroundColor Yellow
Write-Host "如果不想停止服务，请直接关闭此窗口（服务将保持运行）。" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Green

# 等待用户按回车
Read-Host

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "正在停止服务..." -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
powershell -ExecutionPolicy Bypass -File "$ProjectRoot\stop-dev.ps1"
if ($LASTEXITCODE -ne 0) {
    Write-Host "[警告] 停止服务可能失败，请检查服务状态。" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "服务已停止（公网隧道随 stop-dev.ps1 一并关闭），脚本将在 2 秒后退出。" -ForegroundColor Green
Start-Sleep -Seconds 2
# 脚本结束，窗口自动关闭