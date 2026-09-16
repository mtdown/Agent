## Context

见 proposal.md 的 Why。现状约束：`start-dev.ps1` 是 Windows PowerShell 5.1 目标脚本，全部字符串必须保持 ASCII（devScriptReliability.test.mjs 有静态断言）；前端经 `cmd.exe /c npm run dev` 启动，脚本拿到的 PID 是 cmd 包装进程的 PID，直接按 PID 杀会漏掉 node 子进程，因此停止必须按端口定位实际监听进程（`stop-dev.ps1` 即如此实现）。`start-main-dev.ps1` 通过 `-NoPause` 复用 `start-dev.ps1` 并自行处理回车停止；`stop-dev.ps1` 行为保持不变。

## Goals / Non-Goals

**Goals:**

- 隧道确定性走 cloudflared，删除 cpolar/ngrok 适配代码路径。
- `start-dev.ps1` 内置"回车全停"，与 `start-main-dev.ps1` 现有交互一致。
- 端口被占时自动清理旧服务再启动，免去人工先跑 stop 脚本。
- `stop-main-dev.ps1` 恢复可用（调项目根目录 `stop-dev.ps1`）。

**Non-Goals:**

- 不改 `stop-dev.ps1` 的任何行为与退出码语义。
- 不做 cpolar/preview 隧道性能对照测试（负责人明确取消）。
- 不引入 cloudflared 命名隧道/自定义域名/配置文件，继续用免注册的 quick tunnel（随机 trycloudflare.com 域名）。
- 不改后端/前端业务代码。

## Decisions

### D1: 停止逻辑以内联函数形式进 `start-dev.ps1`，`stop-dev.ps1` 保持独立

回车全停需要杀后端、前端、隧道三样。前端是 cmd 包装 + node 子进程树，按启动时记录的 PID 杀不干净，因此内联一个 `Stop-DevServices` 函数：按端口 8123/3000 用 `Get-NetTCPConnection` 定位监听 PID 并 `Stop-Process -Force`，停止后复查端口（吸收 `stop-dev.ps1` 的复核与失败上报思路，但不改 `stop-dev.ps1` 本身）；隧道按 `tmp/tunnel.pid` 停止。启动前清理（D3）复用同一函数。
备选：回车时调起 `stop-dev.ps1` 子进程。放弃原因：清理函数在启动阶段就要用（端口被占场景），抽成函数一处实现两处复用，避免脚本间新增输出/退出码耦合；且 `stop-dev.ps1` 的严格退出码语义是为独立兜底场景设计的，内联路径只需要人读的失败清单。

### D2: 隧道固定 cloudflared，URL 抓取只扫日志

删除 `Get-TunnelPublicUrl` 对 cpolar(9200)/ngrok(4040) 本地 API 的探测和 authtoken 识别分支，`$hostPattern` 收窄为 trycloudflare.com。URL 抓取沿用现有回退路径：轮询读 `tmp/tunnel-cloudflared.log` / `.err.log` 正则匹配。cloudflared 缺失时输出 winget 安装指引并降级为仅局域网，不阻塞服务。
备选：保留 `-Tunnel` 参数做多工具切换。放弃原因：负责人明确只保留 cloudflared，多余参数即多余维护面。

### D3: 端口占用从"报错退出"改为"警告 + 清理 + 继续"

`Assert-PortFree` 改为 `Clear-Port`：查出占用进程名/PID 并打印 → `Stop-Process -Force` → 复查端口。复查仍被占（杀不掉/查不了）才报错退出、退出码非 0，与 delta spec 的失败场景对齐。MySQL(3307)/Redis(6379) 预检警告保持不变。

### D4: `start-main-dev.ps1` 尾部简化，回车全停下沉

main 验收脚本完成 fetch/checkout/pull 后，直接以不带 `-NoPause` 的方式调用 `start-dev.ps1`（默认开隧道），由其内置回车全停收尾；`-Public` 开关删除，新增 `-NoTunnel` 透传。这样 main 验收与日常开发共用同一条启动-停止路径，行为完全一致。

### D5: `stop-main-dev.ps1` 去掉 worktree，直调根目录 `stop-dev.ps1`

服务只认端口不认分支，main 验收启动的服务与任务分支服务在端口层面无法区分，直接在项目根目录调 `stop-dev.ps1` 即可等效回收。同时删除 AGENTS.md 第 7 节里"进入 main 专用工作目录"的过时描述，改为如实描述（start 拉取 main 后在项目根目录启动；stop 调根目录 stop-dev.ps1）。

### D6: 测试断言同步更新

`devScriptReliability.test.mjs` 中"start script checks both the backend and the frontend port"用例的断言从 `Run \.\\stop-dev\.ps1 first` / `exit 1`（占用即中止）改为断言新语义：存在清理函数、占用时输出进程信息并停止占用进程、清理失败才非 0 退出。ASCII 断言与其他用例不动。

## Risks / Trade-offs

- [自动清理可能杀掉非本项目的进程] → 仅当占用进程监听的正是 8123/3000 时才杀，打印进程名/PID 留痕；这两个端口是本项目约定端口，风险可控；查不到占用者时清理失败路径会中止而不是盲杀。
- [cloudflared quick tunnel 域名每次重启都变] → 沿现状，展示文案已说明；不改。
- [回车全停与 `-NoPause` 并存，父脚本误用交互模式] → `start-main-dev.ps1` 已改为不传 `-NoPause`，交互归属单一入口；`-NoPause` 保留供未来脚本化调用，文档标注清楚。
- [Windows PowerShell 5.1 编码] → 新增/修改的脚本字符串全部 ASCII，静态测试继续把关。

## Migration Plan

1. 任务分支上完成脚本与测试修改，跑 `node --test` 静态用例与 PowerShell 语法解析。
2. 负责人本机运行时验收（启动、隧道地址、回车全停、端口自清理、main 流程）。
3. 合并回 main 后无持久化状态需要迁移；`tmp/tunnel.pid` 格式不变，`stop-dev.ps1` 兜底行为兼容。
4. 回滚：revert 该提交即可恢复旧脚本行为，无数据回滚需求。
