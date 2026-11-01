# 测试报告

Change／阶段／任务／轮次：agent-wiki-workflows／3／3.1b／1；2026-11-01 +08:00。测试实例：/root/test_confirmation_frontend。总体结论：证据不足；无本范围内已发现的 FAIL。

环境：Windows，本机 Node、npm；分支 feature/agent-wiki-workflows开发，HEAD bbc1819f7d44a0968638b193ff5509fec5786046。未运行基线，未切换分支。测试前后 git status 一致：既有修改和未跟踪文件均保持；报告位于已有未跟踪目录。测试时不访问用户账户及数据，未重启现有 3000、8123 服务，未创建容器。

快照：完整 51 项文件清单见 tmp/agent-wiki-workflows-confirm-r1/manifest.json，SHA-256 191b85943415140ef97746fda688126473e5b8a8a4da247e62ddfdbec2ebf78e；逐项使用 Get-FileHash SHA256 测试前后比对，均 51 项、0 不匹配。完整差异 tmp/agent-wiki-workflows-confirm-r1/complete-diff.txt SHA-256 f7a161403782ea0945d803fab643360f9ce9cc7f1b66d24f186ad3032f1d0dfe；前后匹配。

开发记录 docs/issue-reports/agent-wiki-workflows/developer-3-1b-round1.md SHA-256 2d1953e422128d6fc93c870503ff0527159caac23b1d20b2b108fbc5d7f4d6a6，前后匹配。四项前端文件哈希与记录匹配，独立重跑的数量、结果和构建警告与记录一致；未独立证实其开发过程、历史失败或未访问数据的叙述。

验收项：
- 前端定向 PASS：cloud_front 下 node --test importConfirmation.test.mjs，退出 0，6/6、跳过 0。真实 Vue 模板编译和自定义渲染检查全部 URL、分层目录、按钮及终态；函数断言过期、取消、unknown、存储锁；模拟 SSE 断言请求体仅 turnId 和服务端 token、旧选择和新确认事件；注入处理函数模拟断线及历史失败，断言锁先行且无重发。它们不是浏览器 DOM 或真实联合验证。
- 前端全量 PASS：node --test *.test.mjs，退出 0，104/104，跳过 0；包含旧 Wiki 选择等既有测试。
- type-check PASS：npm run type-check，退出 0；构建 PASS：npm run build-only，退出 0，Vite 报大 chunk 警告；git diff --check -- cloud_front 退出 0。
- E1 证据不足：定向模拟支持卡片字段和 token 提交；未运行真实浏览器与受控 Agent、Cloud 联合测试，不能证明确认前零写、真实确认后执行或真实逐项结果。
- E2 证据不足：前端模拟覆盖过期、取消及重复点击锁；跨用户、跨会话、参数篡改、服务端并发、失权及目录变更属于后端验收，非本实例范围，不能据此判定 E2 通过。
- E3 证据不足：函数替身支持断线锁和不盲重试；真实浏览器刷新、真实历史恢复及混合结果和未知状态的端到端呈现未验证。
- C1、C2、C3：本实例仅负责前端部分，指定 Maven 和 OpenSpec 命令未执行；后端独立验收另行汇总，不能据本报告判定整体通过。

固定验收缺陷：本范围未发现已证实的 FAIL。清单外交付阻断：无已证实项。问题 #14 标记证据不足，覆盖真实浏览器与前后端联合范围；#10 按交接要求未操作。现有端口已有服务，数据范围未确认；本轮没有配置独立模拟服务和真实浏览器，不能证明安全调用现有服务，故未联测。未执行变异检验，任务卡未指定。未新增一次性脚本或数据；构建更新既有 dist 产物，未清理用户文件。风格意见无。
