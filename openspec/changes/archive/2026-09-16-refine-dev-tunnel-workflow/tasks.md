## 1. start-dev.ps1 改造

- [x] 1.1 新增内联 `Stop-DevServices` 函数（按端口 8123/3000 定位监听进程并停止、复查端口、停止 tunnel.pid 对应隧道），并将启动前置检查改为"占用则提示进程名/PID、自动清理、复查失败才非 0 退出"；验证：源码含清理函数与复查逻辑，无 cpolar/ngrok 字样
- [x] 1.2 隧道固定 cloudflared：删除 cpolar/ngrok 探测、本地 API 抓取与 authtoken 识别，`$hostPattern` 收窄为 trycloudflare.com，URL 仅从 cloudflared 日志轮询抓取；cloudflared 缺失时输出 winget 安装指引并降级仅局域网；验证：源码检索无 9200/4040/authtoken/cpolar/ngrok 残留
- [x] 1.3 展示公网 URL 时同时标明被转发的本地端口 3000，成功用绿色高亮框、失败输出日志路径提示；验证：人工阅读输出区块代码路径完整（成功/失败两分支）
- [x] 1.4 启动完成后等待回车，回车触发 `Stop-DevServices` 全停后正常退出，文案说明"直接关窗口可保留服务"；`-NoPause` 行为保留不变；验证：回车分支调用清理函数且顺序为后端->前端->隧道
- [x] 1.5 删除 `-Public` 兼容开关与头部注释里过时的多工具说明，注释更新为 cloudflared 专用；验证：`param()` 块仅剩 `-Tunnel` 删除后的 `-NoTunnel` / `-Preview` / `-NoPause`

## 2. start-main-dev.ps1 与 stop-main-dev.ps1

- [x] 2.1 `start-main-dev.ps1`：默认开启隧道（不再默认 `-NoTunnel`），删除 `-Public` 开关，新增 `-NoTunnel` 透传；尾部删除自行 Read-Host + 调 stop 的逻辑，改为不带 `-NoPause` 调 `start-dev.ps1`；验证：脚本不再出现 `-NoPause`、`stop-dev.ps1`、`-Public` 字样（start-dev.ps1 内部除外）
- [x] 2.2 `stop-main-dev.ps1`：删除失效 worktree 引用，改为在项目根目录直接调用 `stop-dev.ps1` 并传播退出码；验证：uploadWorkflow.test.mjs 的 stop-main 相关断言通过

## 3. 测试与文档同步

- [x] 3.1 更新 `cloud_front/devScriptReliability.test.mjs` 中端口占用用例断言为新语义（清理函数存在、打印占用进程、失败才退出），其余用例不动；验证：`node --test cloud_front/devScriptReliability.test.mjs` 全绿
- [x] 3.2 README.md 脚本说明与 `docs/演示环境改动说明.md` / `docs/demo-public-tunnel.md` 中涉及 cpolar/多工具探测/回车行为的描述同步更新；验证：全文检索 docs 与 README 无与新行为矛盾的描述
- [x] 3.3 AGENTS.md 第 7 节关于 stop-main-dev.ps1"进入 main 专用工作目录"的过时描述修正为"调项目根目录 stop-dev.ps1"；验证：阅读比对第 6/7 节与脚本实际行为一致

## 4. 验证与收尾

- [x] 4.1 静态自动化校验：`node --test cloud_front/devScriptReliability.test.mjs` 11/11 全绿；`uploadWorkflow.test.mjs` 4/5 通过，唯一失败项为 upload.ps1 干跑既有 bug（与 origin/main 逐字节一致，非本任务引入，已记 IssueLog 待负责人确认）；4 个 ps1 通过 PowerShell Parser 语法解析，ASCII 断言通过（未启动任何服务）
- [x] 4.2 负责人人工验收：启动 start-dev.ps1 隧道地址展示正常、输出符合预期；负责人确认"没有问题"并授权推送（2026-09-12）
- [x] 4.3 IssueLog.xlsx 记录本任务过程中遇到的报错与处理；验证：新增行状态与实际一致
