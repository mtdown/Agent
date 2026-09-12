## Why

`start-dev.ps1 -Preview -Tunnel cloudflared` 无法完成启动：前端构建步骤把 `-RedirectStandardOutput` 与 `-RedirectStandardError` 指向同一个日志文件，Windows PowerShell 5.1 的 `Start-Process` 直接抛异常终止脚本；同时公网地址探测固定优先请求 cpolar 的 9200 API，常驻的 cpolar Windows 服务会抢答，导致即使选了 cloudflared 也打印出 cpolar 的 URL。负责人需要用 cloudflared 免费隧道做公网演示，该路径当前不可用。

## What Changes

- `start-dev.ps1` 的 `-Preview` 前端构建步骤改为把 stdout / stderr 重定向到两个不同文件（`dev-frontend-build.log` / `dev-frontend-build.err.log`），消除 `Start-Process` 的"重定向文件相同"异常。
- 公网地址探测函数 `Get-TunnelPublicUrl` 增加当前隧道工具参数：显式选择 cloudflared 时跳过 9200 / 4040 API 探测，只从 cloudflared 自身日志抓取 `*.trycloudflare.com` 地址，避免被常驻 cpolar 服务抢答。
- 其余行为（工具自动探测顺序、cpolar / ngrok 路径、端口检查、启停流程）不变。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `dev-script-reliability`: 新增两条 start-dev.ps1 可靠性需求——`-Preview` 前端构建不得因重定向配置崩溃；显式指定隧道工具时公网地址必须来自所选工具。

## Impact

- 受影响文件：`start-dev.ps1`（仅此一个脚本）。
- 不影响后端 / 前端代码、数据库、OpenAPI、既有启动流程。
- 回滚方案：还原 `start-dev.ps1` 该两处改动即可，无数据或状态迁移。
