## Why

本地开发脚本（`start-dev.ps1` / `stop-dev.ps1` / `upload.ps1`）在 `add-batch-document-import` 一轮开发中连续暴露可靠性问题，共同特征是**失败但不报错**：

- `stop-dev.ps1` 依赖 `netstat` + `taskkill` 两个外部 exe。当它们被环境策略拦截时（实测 `netstat -ano` 返回 0 字节），查不到任何 PID，脚本打印 `No dev services are listening on ports 8123/3000 - nothing to stop.` 并正常退出（退出码 0）。错误输出被 `2>$null | Out-Null` 完全吞掉，且从不检查 `$LASTEXITCODE`。结果是负责人以为已停服，实际端口仍被占用。
- `start-dev.ps1` 只检查后端 8123，不检查前端 3000。3000 被占用时不会中止，Vite 会静默落到 3001，负责人按 3000 访问会拿到连接失败。
- `upload.ps1` 内部 `& git` 为外部 exe，被拦截时日志为空、既不提交也不推送，且异常信息不落地；负责人看到"没输出"无法判断成功还是失败。

后果是负责人无法从脚本输出判断真实状态，只能靠人工复查端口，且本轮已实际造成"私自起后端 → 占住 8123 → `start-dev.ps1` 在 [1/4] `exit 1`"的连环阻塞。

## What Changes

- `stop-dev.ps1`：改用 PowerShell 原生 `Get-NetTCPConnection` / `Stop-Process` 替代 `netstat` / `taskkill`；杀掉进程后**复核端口是否真的释放**，仍被占用则以非 0 退出码报错；无法查询端口状态时显式报错，不再打印"nothing to stop"。
- `start-dev.ps1`：前置检查补齐前端 3000 端口，被占用时与 8123 一样中止并给出提示（提示先执行 `stop-dev.ps1`）。
- `upload.ps1`：增加 git 可用性自检，git 无响应时立即显式失败；`push` 后校验 `rev-list '@{u}..HEAD'` 为 0，未真正推送则报错。
- 新增 `cloud_front/devScriptReliability.test.mjs` 作为自动化回归，静态校验三个脚本不再包含静默失败写法，并覆盖本次修复的关键约定。

## Non-goals

- 不改动 `start-dev.ps1` 中 Maven 的调用方式（`mvn -DskipTests package -q`），负责人已明确指示暂不处理。
- 不修改 `start-task.ps1` / `start-main-dev.ps1` / `stop-main-dev.ps1`。
- 不改变后端 8123、前端 3000 的默认端口与日志目录约定。
- 不引入新的外部依赖或新增脚本。

## Acceptance Criteria

- `stop-dev.ps1` 在无占用端口时退出码 0；存在占用且成功停止时退出码 0；**无法停止或无法查询端口时退出码非 0 并在输出中明确写出端口与 PID**。
- `stop-dev.ps1` 源码中不再出现 `netstat`、`taskkill`、`2>$null`、`Out-Null`。
- `start-dev.ps1` 在 3000 被占用时中止（退出码非 0）并提示执行 `stop-dev.ps1`。
- `upload.ps1` 在 git 不可用时立即退出并报错，不产生"空输出成功"；`push` 后必须校验远端与本地一致。
- 新增的 `devScriptReliability.test.mjs` 全部通过；现有 `cloud_front` 测试与 openspec 校验不受影响。
