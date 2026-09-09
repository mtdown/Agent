## 1. 重构 stop-dev.ps1

- [x] 1.1 用 `Get-NetTCPConnection -LocalPort <port> -State Listen` 替换 `netstat -ano` 查询占用 PID
- [x] 1.2 用 `Stop-Process -Id <pid> -Force` 替换 `taskkill`，包 try/catch 收集失败项
- [x] 1.3 停止后重新查询端口复核是否真的释放
- [x] 1.4 端口状态无法查询时输出明确错误并 `exit 1`，不再打印 "nothing to stop"
- [x] 1.5 存在停止失败或复核仍未释放时 `exit 1`，并列出端口与 PID
- [x] 1.6 移除全部 `2>$null` / `Out-Null` 错误吞没写法

## 2. 加固 start-dev.ps1

- [x] 2.1 抽出 `Assert-PortFree($port)`，对 8123 与 3000 依次检查
- [x] 2.2 端口被占用时输出进程名、PID，并追加"先执行 stop-dev.ps1"的处置提示后 `exit 1`
- [x] 2.3 不改动 Maven 调用（`mvn -DskipTests package -q`），按负责人指示保持原样

## 3. 加固 upload.ps1

- [x] 3.1 增加 git 可用性自检，无响应时立即报错退出
- [x] 3.2 分支名、上游名为空时立即 throw，不再用空字符串继续后续命令
- [x] 3.3 `push` 后校验 `git rev-list --count '@{u}..HEAD'` 为 0，否则报错

## 4. 自动化回归

- [x] 4.1 新增 `cloud_front/devScriptReliability.test.mjs`，静态校验三个脚本的静默失败写法已消除、关键 cmdlet 与提示存在
- [x] 4.2 用 PowerShell 解析器 `[System.Management.Automation.Language.Parser]::ParseFile` 校验三个脚本语法无误
- [x] 4.3 运行 `cloud_front` 全部 `node --test *.test.mjs` 确认无回归

## 5. 功能验证

- [x] 5.1 在无服务监听时执行 `stop-dev.ps1`，确认退出码 0 且输出"没有需要停止的服务"
- [x] 5.2 用 `Get-NetTCPConnection` 复核 8123 / 3000 仍为空闲
- [x] 5.3 执行 `openspec validate fix-dev-script-reliability --strict` 通过

## 6. 人工验收

- [x] 6.1 启动服务后向负责人汇报前端/后端地址与手测清单
- [x] 6.2 负责人在 8123 或 3000 被占用时执行 `start-dev.ps1`，确认脚本中止并提示 `stop-dev.ps1`
- [x] 6.3 负责人执行 `stop-dev.ps1`，确认端口被真正释放（用浏览器或 `Get-NetTCPConnection` 复核）
- [x] 6.4 负责人执行 `upload.ps1`，确认输出完整且推送生效
- [x] 6.5 负责人返回手测结果前，以上任务不得标记完成

## 7. 上传送审

- [x] 7.1 自动化验证与负责人验收完成后，执行 `upload.ps1` 推送 `fix/dev-script-reliability修复`
- [x] 7.2 汇报已推送分支，并提醒合并到 `main` 必须由负责人通过 merge request / pull request 完成
> 2026-09-09 补勾：负责人确认上述人工验收与上传任务均已实际完成，此前仅漏勾选。
