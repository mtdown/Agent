# 阶段 8 任务清单

## 8.1 启动脚本
目标：临时隧道默认关闭；`-Tunnel` 显式开启，保留 `-NoTunnel`，两者同时指定时关闭。允许文件：`start-dev.ps1`。依赖：无。验收 A1：使用任务 8.2 的测试命令，默认和 `-NoTunnel` 不调用隧道启动，`-Tunnel` 调用一次，两个参数同时传入不调用；脚本 PowerShell 5.1 语法解析无错误。

## 8.2 自动化验证
目标：不启动真实服务、不连接网络验证实际脚本参数与条件。允许文件：`scripts/test-start-dev-tunnel.ps1`。依赖：8.1。验收 A2：仓库根目录运行 `powershell -NoProfile -File scripts/test-start-dev-tunnel.ps1`，退出 0，断言四种参数组合和 PowerShell 5.1 解析；禁止实际启动隧道或其他服务。

## 8.3 文档同步
目标：现行 README 与公网演示文档说明默认关闭、`-Tunnel` 显式开启及 `-NoTunnel` 优先。允许文件：`README.md`、`docs/demo-public-tunnel.md`。依赖：8.1。验收 A3：`rg -n -- '-Tunnel|-NoTunnel|默认' README.md docs/demo-public-tunnel.md` 及逐项核对上述说明与脚本一致；保留历史交接记录。
