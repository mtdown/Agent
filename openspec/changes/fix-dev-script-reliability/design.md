## Context

三个脚本位于仓库根目录，被 `AGENTS.md` 规定为启停本地服务与上传的唯一入口。当前实现的共同缺陷是**把外部原生命令（`netstat` / `taskkill` / `git`）当作可信依赖**：这些命令在本机开发环境下可能被安全策略拦截，返回空输出与非零/零退出码，而脚本既不检查退出码、又把 stderr 丢弃，导致"执行成功但什么也没做"。

改造的核心思路是：**能用 PowerShell 原生 cmdlet 的就不调外部 exe；必须调外部 exe 的就必须检查结果与退出码；任何失败都以非 0 退出码显式暴露。**

## Decisions

### 1. `stop-dev.ps1` 用原生 cmdlet 替代 netstat + taskkill

- 查询：`Get-NetTCPConnection -LocalPort <port> -State Listen -ErrorAction SilentlyContinue`，取 `OwningProcess`。
- 终止：`Stop-Process -Id <pid> -Force -ErrorAction Stop`，包在 `try/catch` 里，捕获后记录失败而不是忽略。
- 复核：停止后再次 `Get-NetTCPConnection` 复查（最多等待若干秒），仍监听则把端口与 PID 写入输出并 `exit 1`。
- 端口状态无法查询（cmdlet 本身抛错）时同样报错退出，不再打印 "nothing to stop"。
- 保留 8123 / 3000 两个端口；去重同一 PID（`Sort-Object -Unique`）。
- **理由**：`Get-NetTCPConnection` / `Stop-Process` 是 PowerShell 内置 cmdlet，不经过外部进程创建，在受限环境下仍可用（本轮已实测可用并成功释放 8123）。

### 2. `start-dev.ps1` 前置检查补齐 3000

- 把现有的 8123 检查抽成 `Assert-PortFree($port)`，对 8123 与 3000 依次执行。
- 被占用时输出占用进程名、PID，并追加一行"先执行 `stop-dev.ps1`"的处置提示，然后 `exit 1`。
- **理由**：Vite 默认行为是端口被占就顺延到 3001，静默顺延比直接失败更难排查。

### 3. `upload.ps1` 增加自检与推送后校验

- 开头增加 `Assert-GitAvailable`：执行 `git --version`，无输出或非 0 退出码则 throw "git 不可用"。
- 现有 `Run-Git` 已检查 `$LASTEXITCODE`，保留；补充 `Get-GitText` 场景的空值校验（分支名为空时立即 throw，而不是继续拿空字符串跑后续命令）。
- `push` 之后执行 `git rev-list --count '@{u}..HEAD'`，结果非 0 表示未真正推送，throw。
- **理由**：本轮 `upload.ps1` 的表现是"无输出、无提交、无推送"，负责人完全无法判断结果。加自检后至少能明确失败。

### 4. 不改 Maven 调用

`start-dev.ps1` 第 48 行的 `mvn -DskipTests package -q` 保持原样：本机 Maven bin 下同时存在无扩展名的 Unix shell 脚本 `mvn` 与 `mvn.cmd`，理论上写 `mvn.cmd` 更稳；`$ErrorActionPreference = 'Stop'` 在 Windows PowerShell 5.1 下也会把外部程序的 stderr 当错误终止脚本。但负责人已明确指示本轮不处理 Maven，**这两项只记入风险，不改动**。

## 自动化回归策略

新增 `cloud_front/devScriptReliability.test.mjs`（与既有 `wikiNavigationFlow.test.mjs` / `wikiBatchDocumentImportFlow.test.mjs` 同风格，纯静态文本校验 + `node --test`）：

- `stop-dev.ps1` 不含 `netstat` / `taskkill` / `2>$null` / `Out-Null`，且含 `Get-NetTCPConnection`、`Stop-Process`、`exit 1`。
- `start-dev.ps1` 对 3000 端口做了占用检查，且中止前提示 `stop-dev.ps1`。
- `upload.ps1` 含 git 可用性自检与 push 后的 `rev-list` 校验。
- 三个脚本语法可被 PowerShell 解析器接受（由会话内 `[System.Management.Automation.Language.Parser]::ParseFile` 单独验证，不进 node 测试）。

## Risks

- `Get-NetTCPConnection` 需要管理员/普通用户均可执行；本轮已实测在受限会话中可用。若某些环境下不可用，脚本会走到"无法查询端口"分支并显式报错，不会静默。
- `Stop-Process -Force` 对非自有进程会抛 Access denied；已用 try/catch 捕获并计入失败，最终以非 0 退出码暴露。
- 静态文本测试只能防回归，不能替代真机启停验证；真机验收仍由负责人执行 `stop-dev.ps1` / `start-dev.ps1` 完成。

## Alternatives Considered

- **保留 netstat/taskkill，仅补 `$LASTEXITCODE` 检查**：能解决"不报错"，但解决不了"外部 exe 被拦截导致查不到 PID"，仍会出现假成功。已否决。
- **改用 PowerShell 7**：环境未统一，且负责人未要求。已否决。
- **在脚本里加 `Write-Host` 输出到日志文件**：能留痕但不改变静默失败本质。已否决。
