## Why

`start-dev.ps1` 的公网隧道目前靠 cpolar/cloudflared/ngrok 自动探测（cpolar 优先），但本机只安装了 cloudflared，cpolar 免费版还曾多次出现打不开的问题，行为不确定；同时"启动窗口按回车"目前只是关闭窗口、服务继续运行，负责人需要另开终端跑 `stop-dev.ps1` 才能回收服务，日常演示流程偏繁琐。`start-main-dev.ps1` 默认关闭隧道，与演示需求不一致；`stop-main-dev.ps1` 引用了早已不存在的 worktree 路径，一运行就报错，是死文件。

## What Changes

- `start-dev.ps1` 隧道固定为 cloudflared：删除 cpolar/ngrok 适配（本地 API 探测、authtoken 识别、多工具探测顺序），保留 `-NoTunnel` 关闭开关；公网 URL 继续从 cloudflared 日志抓取并以绿色高亮展示，同时展示被转发的本地端口。
- `start-dev.ps1` 回车语义变更（**BREAKING**）：启动完成后按回车不再只是关窗口，而是停止全部服务（后端 8123、前端 3000、cloudflared 隧道）；直接关闭窗口仍保留服务运行。
- `start-dev.ps1` 端口占用行为变更（**BREAKING**）：启动前置检查发现 8123/3000 被占用时，不再报错退出，而是提示占用进程信息后自动清理旧服务并继续启动。
- `start-main-dev.ps1` 默认开启 cloudflared 隧道（原先默认 `-NoTunnel`，仅 `-Public` 时开启）；删除已无意义的 `-Public` 开关；尾部不再自行等待回车并调用 `stop-dev.ps1`，直接复用 `start-dev.ps1` 内置的"回车全停"。
- `stop-main-dev.ps1` 修复为调用项目根目录的 `stop-dev.ps1`，与现行 `start-main-dev.ps1` 直接在项目根目录切 main 的行为对齐，删除失效的 worktree 引用。
- `cloud_front/devScriptReliability.test.mjs` 中针对"端口被占必须中止"的静态断言同步更新为"自动清理"语义。
- 取消此前讨论的 cpolar/preview 隧道性能对照测试：不测试 cpolar 免费版功能，运行时验证由负责人自行执行。

## Capabilities

### New Capabilities
- `dev-public-tunnel`: 本地开发脚本公网隧道能力——固定使用 cloudflared、公网 URL 展示、随服务一键停止、`-NoTunnel` 逃生口。

### Modified Capabilities
- `dev-script-reliability`: `start-dev.ps1` 端口被占时的行为由"中止启动并提示先执行 stop-dev.ps1"改为"提示占用信息后自动清理旧服务并继续启动"；停止逻辑被吸收进 `start-dev.ps1`（回车全停）后 `stop-dev.ps1` 保留为独立兜底入口，其端口复核与退出码语义不变。

## Impact

- 脚本：`start-dev.ps1`、`start-main-dev.ps1`、`stop-main-dev.ps1`（`stop-dev.ps1` 不改）。
- 测试：`cloud_front/devScriptReliability.test.mjs` 静态断言更新。
- 文档：`README.md` 脚本说明与 `docs/` 演示文档中涉及隧道/回车行为的描述同步微调。
- 依赖：仅本机已安装的 cloudflared；不再引用 cpolar/ngrok。
- 不改任何后端/前端业务代码，不影响数据库与接口。
