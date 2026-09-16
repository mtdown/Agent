# dev-script-reliability Specification

## Purpose
TBD - created by archiving change fix-dev-script-reliability. Update Purpose after archive.

## Requirements

### Requirement: 停止脚本必须复核端口释放状态

`stop-dev.ps1` MUST 在停止端口 8123 / 3000 上的监听进程后重新查询该端口状态确认已释放；未释放、停止失败或根本无法查询端口状态时，MUST 以非 0 退出码退出并在输出中写明端口号与 PID，不得静默打印"没有需要停止的服务"。

#### Scenario: 端口上的进程被成功停止

- **WHEN** 8123 上存在监听进程，且 `Stop-Process` 成功终止它
- **THEN** 脚本复查该端口确认不再处于 Listen 状态，输出被停止的进程名与 PID，退出码为 0

#### Scenario: 进程无法被停止

- **WHEN** 8123 上存在监听进程，但 `Stop-Process` 抛出 Access denied 等异常
- **THEN** 脚本输出失败的端口与 PID 以及具体异常，最终退出码非 0

#### Scenario: 端口状态无法查询

- **WHEN** `Get-NetTCPConnection` 本身执行失败，导致无法判断端口占用情况
- **THEN** 脚本输出"无法查询端口状态"并退出码非 0，不得打印 "No dev services are listening" 之类的成功提示

#### Scenario: 确实没有任何服务在监听

- **WHEN** 8123 与 3000 都没有监听进程
- **THEN** 脚本输出没有需要停止的服务，退出码为 0

### Requirement: 停止脚本不得依赖外部原生命令

`stop-dev.ps1` MUST 使用 PowerShell 原生 cmdlet 完成端口查询与进程终止，源码中不得出现 `netstat`、`taskkill`，也不得使用 `2>$null`、`Out-Null` 等丢弃错误输出的写法。

#### Scenario: 静态检查通过

- **WHEN** 对 `stop-dev.ps1` 执行静态回归测试
- **THEN** 文件中不包含 `netstat` / `taskkill` / `2>$null` / `Out-Null`，且包含 `Get-NetTCPConnection` 与 `Stop-Process`

### Requirement: 启动脚本必须同时检查前后端端口

`start-dev.ps1` 的前置检查 MUST 覆盖后端 8123 与前端 3000 两个端口；任一被占用时，脚本 MUST 输出占用进程名与 PID，随后自动清理（停止）占用这些端口的旧服务进程并确认端口释放，然后继续正常启动流程；仅当清理失败（进程无法停止或端口无法释放/查询）时才中止启动并以非 0 退出码退出。独立停止脚本 `stop-dev.ps1` 保留为关窗后回收服务的兜底入口，其行为不受本变更影响。

#### Scenario: 前端端口被占用

- **WHEN** 3000 已被上次运行遗留的前端进程监听，而 8123 空闲
- **THEN** 脚本输出占用 3000 的进程名与 PID，自动停止该进程并确认端口释放，随后继续构建与启动流程，无需人工先执行 `stop-dev.ps1`

#### Scenario: 端口被无法停止的进程占用

- **WHEN** 8123 被某个 `Stop-Process` 无法终止的进程占用
- **THEN** 脚本输出清理失败的端口、PID 与原因，中止启动（不进入 Maven 构建、不启动任何服务），退出码非 0

#### Scenario: 两个端口都空闲

- **WHEN** 8123 与 3000 均未被监听
- **THEN** 脚本继续原有流程（构建 jar、启动后端、启动前端）

### Requirement: 上传脚本不得静默失败

`upload.ps1` MUST 在开头自检 git 是否可用；git 无响应时立即报错退出，不得产生"无输出、无提交、无推送"的成功假象。`push` 之后 MUST 校验远端分支已包含本地 HEAD，不一致则报错。

#### Scenario: git 不可用

- **WHEN** 执行 `upload.ps1` 时 `git --version` 无输出或退出码非 0
- **THEN** 脚本立即输出 git 不可用的错误并退出，不执行 `add` / `commit` / `push`

#### Scenario: 推送后远端与本地不一致

- **WHEN** `git push` 返回成功，但 `git rev-list --count '@{u}..HEAD'` 不为 0
- **THEN** 脚本输出推送未生效并退出码非 0

#### Scenario: 推送成功

- **WHEN** 提交并推送完成，且 `rev-list '@{u}..HEAD'` 为 0
- **THEN** 脚本输出上传完成的分支名，并提示由负责人在远程平台发起 merge request / pull request
