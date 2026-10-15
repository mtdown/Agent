# Tasks

## 1. 会话存储与权限

- [ ] 1.1 增加会话和消息 DDL、实体及 Mapper，验证迁移 SQL 可重复执行且后端编译通过。
- [ ] 1.2 增加按当前用户限定的会话列表、详情、创建和删除接口，验证普通用户和管理员均无法通过接口读取他人会话。
- [ ] 1.3 保存用户问题、完成回答、失败状态和每轮引用/指标，验证刷新后顺序与内容完整且失败回答不进入上下文。

## 2. Agent 多轮与 RAG 编排

- [x] 2.1 修改内部 Agent 输入以接收有界历史和当前依据，验证追问能够读到上一轮且新会话隔离。
- [ ] 2.2 Cloud 按 OFF/ALL/SELECTED 执行权限约束的检索与流式代理，验证空指定集合不会检索全部空间。
- [ ] 2.3 在 SSE 完成/失败路径持久化本轮结果，验证引用、token 和耗时与会话消息对应。

## 3. 前端会话体验

- [ ] 3.1 增加 Agent 会话列表、新会话、重开和继续对话，验证刷新后能恢复当前用户历史。
- [ ] 3.2 增加 Agent 逐轮 RAG 开关和授权空间选择，验证当前选择随请求发送且经典 RAG 不回归。
- [ ] 3.3 按消息渲染引用并走现有文档权限入口，验证多轮引用不串位。
- [ ] 3.4 将 Agent 历史改为左侧列表、新聊天入口与三点菜单，支持本人重命名和删除；首问标题及旧乱码标题正确显示。（删除确认框已出现，最终删除待验）
- [ ] 3.5 调整 Cloud/Agent 流式异步超时，验证超过 30 秒的请求不因 MVC 默认超时中断。（原 30 秒点已越过，但 Agent 在 150 秒返回 503）
- [x] 3.6 将 Agent 默认模型从已退役的 `deepseek-chat` 改为 `deepseek-flash`，验证真实模型接口响应和页面流式问答。
- [x] 3.7 修复模型 SSE 响应体阻塞时 Agent 截止时间失效的问题，并为 DeepSeek Flash 显式关闭默认思考模式；以本地假上游验证超时错误帧与正常增量。

## 4. 集成验收

- [x] 4.1 执行 OpenSpec 严格校验、后端单元测试/构建及前端类型检查，记录测试结果与问题至本文件和 IssueLog.xlsx。
- [ ] 4.2 自动化验证通过后按 stop-dev.ps1、start-dev.ps1 启动本地服务，列出地址、账号前提和负责人页面手测清单；负责人反馈前保持手测任务未完成。
- [ ] 4.3 按负责人本轮授权进行页面自测：新建、首问命名、重命名、删除、刷新恢复、本人隔离和流式问答。

## Verification / Result

第一轮自动化验证：`openspec validate add-agent-conversation-history --strict` 通过。`npm run type-check` 失败：Agent 请求的 `spaceIds: string[]` 与旧流式函数的 `number[]` 参数冲突。两条 Maven 定向测试命令被 PowerShell 的逗号参数解析拒绝，Java 测试尚未运行。两项已记入 `IssueLog.xlsx`；按 AGENTS.md 第 4 节等待负责人确认修复方案后才进入下一轮修复与测试。

第二轮（负责人于 2026-10-15 确认继续）：将 SSE 传输与两种请求类型分开。`npm run type-check`、`npm run build`、Cloud 与 cloud_agent 定向测试、两模块 `mvn -q verify`、OpenSpec 严格校验均通过；Cloud 共 305 项测试、Agent 共 13 项测试，均无失败或错误。已按 `stop-dev.ps1` → `start-dev.ps1 -NoTunnel -NoPause` 启动本地服务，8123/8124/3000 均报告就绪。IssueLog.xlsx 的两项问题已更新为已修复。负责人页面手测待反馈。

负责人手测反馈（2026-10-15）：Agent 问答返回 HTTP 500。Cloud 日志中 `/api/agent/stream` 在约 30 秒后出现 `AsyncRequestTimeoutException`，Agent 日志同一时刻也记录异步超时；两个服务仍在监听。两个 `StreamingResponseBody` 接口没有显式 MVC 异步超时配置，现有 Agent 循环上限为 120 秒、Cloud 代理请求上限为 3 分钟。初步修复方案是在两模块分别设置与内部上限一致的 MVC 异步超时，增加超过 30 秒的流式回归测试，再按项目脚本重启并交负责人复测。问题已记入 IssueLog.xlsx；依据 AGENTS.md 第 4 节，等待负责人确认后进入下一轮修复和测试。手测任务保持未完成。

第三轮（负责人于 2026-10-15 要求双栏历史、修复乱码并由 AI 自测）：Cloud MVC 异步超时设为 210 秒，Agent 设为 150 秒；Agent 模式增加左侧历史、新聊天、重命名和删除菜单。数据库 4 条原有乱码标题已确认均为同一错误默认值，启动后仅将该值按首问修复。Cloud/Agent `mvn -q verify`、前端 `npm run type-check` 与 `npm run build`、OpenSpec 严格校验全部通过。页面自测通过：左侧列表、4 条旧标题恢复、新聊天、首问命名、重命名、刷新后名称保持、重新打开失败轮问题；删除确认框显示正常，最终删除尚未执行。流式问题超过原 30 秒后仍等待，但约 150 秒后返回 `Agent service returned HTTP 503`，内部 Agent 日志记录 `AsyncRequestTimeoutException`。推断模型调用没有在 Agent 循环 120 秒上限处主动结束，MVC 150 秒先触发，需进一步定位调用超时与取消传播。已记入 IssueLog.xlsx 第 211、212 行；依据 AGENTS.md 第 4 节，暂停下一轮修复与测试，等待负责人确认方案。

第四轮（负责人于 2026-10-15 要求改为 `deepseek-flash`）：DeepSeek 官方文档确认该模型名可用于 Chat Completions，旧 `deepseek-chat` 已退役。仅修改 Agent 默认模型，保持环境变量覆盖；`cloud_agent` 的 `mvn -q verify` 与 OpenSpec 严格校验通过。当前 API Key 对官方接口的最小非流式请求在 25 秒读取超时，因此无法认定更换模型已解决流式失败。已记入 IssueLog.xlsx 第 213 行。按 AGENTS.md 第 4 节，暂停后续修复和测试，等待负责人确认排查网络/代理与上游响应的方案。

第五轮（负责人于 2026-10-15 要求从代码排查）：运行中线程栈确认卡在 `OpenAiStreamModel.stream` 的 `lines.iterator().hasNext()`；本地假 SSE 上游在返回 HTTP 200 后保持连接，现有 200ms 模型期限直到 3 秒对端关闭才返回，重现了截止时间失效。DeepSeek 官方协议表明 Flash 默认高强度思考且可能先发送 `: keep-alive`；直连 `/models` 返回 200 且列出 Flash，最小流式请求返回 200 和保活但在观察窗口没有正文。代码增加响应体主动关闭的期限、显式非思考模式和 2048 token 上限。

第五轮验证结果：`cloud_agent` 执行 `mvn -q verify` 通过，OpenSpec 严格校验通过；按 `stop-dev.ps1` → `start-dev.ps1 -NoTunnel -NoPause` 重启本地服务。页面 Agent 模式新会话发送 `1+1=?`，实际流式返回 `1+1 = 2`，41 tokens、首字约 1.5 秒、总生成约 1.5 秒；刷新页面后从历史打开，问题、回答与指标均恢复。IssueLog.xlsx 第 213、214 行更新为已修复。未进行跨用户隔离、RAG 引用和删除最终操作的页面验收，相关总任务仍保持未完成。

提交前验证（2026-10-15，负责人反馈本轮验收通过后）：`git diff --check` 与 OpenSpec 严格校验通过；Agent Surefire 报告 15 项测试均无失败或错误，前端构建输出显示完成。首次 Cloud `mvn -q verify` 返回退出码 1，但 Surefire 报告未发现失败或错误的单测。负责人指出当时服务未停止；按 `stop-dev.ps1` 停服后复测，Cloud `mvn -q verify` 退出码 0，308 项测试无失败或错误，新生成的 JAR 包含 `BOOT-INF`。故将运行中的服务占用打包文件作为高度可信原因；因首次失败完整输出未保留，不认定文件锁定已直接证实。IssueLog.xlsx 第 215 行已更新为已修复。

## Rollback

回退 Cloud、Cloud Agent 与前端版本；新表暂不删除以保留会话数据。
