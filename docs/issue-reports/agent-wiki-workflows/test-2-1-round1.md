# 测试报告

Change ID：agent-wiki-workflows；阶段 2，原子任务 2.1，第 1 轮；2026-11-01。测试实例：/root/test_import_local。

总体结论：BLOCKED，另有证据不足。未确认固定验收全部通过，开发自检不能替代独立测试。

环境与基线：Windows 本机；分支 feature/agent-wiki-workflows开发；基线 bbc1819f7d44a0968638b193ff5509fec5786046；未重新运行基线。定向测试在本机 Maven、mock 和仅绑定随机回环端口的本机 HTTP 服务上运行；未使用 MySQL 或 Redis。总管任务卡限制本轮不运行可能访问本机已有数据库的 Cloud 全量和 MySQL 并发，不创建容器。

被测范围与快照：任务卡给出 57 文件完整清单 tmp/agent-wiki-workflows-p2-r1/manifest.json，SHA256 5190c746e4894b0263124c0a038ca0f1cc725c94bd2c288e1115229df18aee7e；完整 diff tmp/agent-wiki-workflows-p2-r1/complete-diff.txt，SHA256 029481b507d599a4c88de85a7bab2ad3a1aa76f2380ee34bc4952c09c110cd8a。测试前后使用 Python hashlib 读取清单逐文件对照 SHA256，均 57 项、0 不一致。测试后复核结果适用于此快照。AGENTS_test.md 更新不在源码快照中。

开发记录核验：docs/issue-reports/agent-wiki-workflows/developer-2-1-round1.md SHA256 6ac3d8140a509c3f232c3c9f01889f3955a3227107133692354cdfc81b547acc，与任务卡一致。独立定向测试复核了其中的 36 项 Cloud 和 8 项 Agent 断言；开发记录的并发与全量自检并非本轮独立证据。其所记全量曾连接现有数据库事故已登记 #5，不能推断不存在副作用。

独立执行命令（仓库根目录，PATH 前置 IntelliJ IDEA 2024.1 Maven bin）：

- `mvn -f cloud/pom.xml "-Dtest=AgentUrlImportTest,AgentPreviewProxyWikiEventTest,WikiBatchImportServiceImplTest" test`：退出 0；36 项，0 失败、0 错误、0 跳过；各类 11、3、22 项。证据 cloud/target/surefire-reports 中相应 XML；包含模拟提交未知、权限变化、缓存重试、私有地址、结果投影等断言。
- `mvn -f cloud_agent/pom.xml "-Dtest=AgentUrlImportToolTest,AgentWikiChoiceLoopTest" test`：退出 0；8 项，0 失败、0 错误、0 跳过；各类 6、2 项。证据 cloud_agent/target/surefire-reports 中相应 XML；断言用户及目标绑定、未知后禁止写、摘要不写、HTTP 中断和事件投影。

验收项：

- B1 证据不足：上述两条合并命令覆盖对应固定测试类且通过，但 AgentUrlImportTest 模拟了 prepareUrl 和 buildDocument；未发现大于 5000 字正文完整经过真实导入链并保存的断言，不能由退出码证明。#7。
- B2 证据不足：定向断言密钥、跨空间/删除目录、失权、目标绑定和 ID 投影，均通过；真实并发撤权及共享锁由 B7 验证，未独立执行。
- B3 证据不足：定向模拟提交异常、逐项混合状态、HTTP 中断、私有地址拒绝和 Wiki 事件通过；对复用网页服务全部 URL 限制没有单独验证。
- B4 BLOCKED：依任务卡暂不执行 Cloud 或 Agent 全量；Cloud 全量会启动 local Spring 上下文、曾连接现有本机数据库，隔离数据源及副作用尚未在本轮核实。不以开发记录取代独立执行；未完成全部范围审查。
- B5 证据不足：断言首次缓存失败重试一次、成功/失败均只保存一次、确认成功后警告以及未知不清理，均通过。实现源码可见两次异常类别级 WARN，但测试未断言日志被输出、保留或脱敏，#7。
- B6 证据不足：定向 mock 测试验证成功、失败、未知、未执行，未知停止剩余写入、HTTP 中断不重试、目标及权限复查；公开接口兼容断言部分覆盖。共享锁的真实事务与并发覆盖未在本轮验证。
- B7 BLOCKED：任务卡明确禁止本轮对 MySQL 写入；未运行真实 MySQL 并发固定命令。开发者记录了 15 项容器自检，但不作为独立验收；其死锁异常为替身注入，并非真实死锁复现。问题 #2、#4 保持待验证。

固定验收缺陷：无已确认业务缺陷；#7 为长期跟踪证据不足。清单外交付阻断：无新增确认事项；既有 #5 保持待处理。

未验证项：B1 长正文真实链路与 B5 诊断日志；B4 Cloud、Agent 全量；B7 本机真实 MySQL；其余按上项列明。未进行变异检验：任务卡未授权。未修改业务源码或验收；未清理任何现有数据，未新建容器。Maven 正常生成 target 构建及测试报告；临时回环 HTTP 服务由测试自身停止，未创建测试数据库、Redis 数据或专属临时目录。风格意见：无。
