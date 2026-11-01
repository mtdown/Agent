# 阶段 2、任务 2.2 独立测试第 2 轮

- 测试实例：`/root/test_cloud_local_r2`；时间：2026-11-01（中国标准时间）。分支 `feature/agent-wiki-workflows开发`，HEAD `bbc1819f7d44a0968638b193ff5509fec5786046`；未运行基线或切换分支。
- 总体结论：证据不足。D1、D2 的定向命令及 B4 两模块全量均通过，但真实历史数据库升级、历史消息投影及 Wiki 写入后会话重开仍无验证，不能认定 D1/D2 固定验收全部通过。
- 被测清单：`tmp/agent-wiki-workflows-p2-r3-manifest.json` SHA-256 `3656474334772207445b3152271e87999689d193505b2dd7706122c91fc102c8`；60 个文件逐项 SHA-256 测试前、后均匹配。完整差异 `tmp/agent-wiki-workflows-p2-r1/complete-diff.txt` SHA-256 `029481b507d599a4c88de85a7bab2ad3a1aa76f2380ee34bc4952c09c110cd8a`，另外核对了任务卡所列新增测试夹具、规则及 Cloud 测试 diff。开发记录 `developer-2-2-local-isolation.md` SHA-256 `7fdf35a41b74814fb62d5cbe71db5447b20c197f0936115d1c816a6c7f364197`，本轮独立复现全量与临时库清理；未复现开发自检的特定临时库。
- 环境：本机 Windows、IDEA 2024.1 内置 Maven、本机 MySQL `127.0.0.1:3307`。测试进程设置数据库凭据，未在报告输出凭据；本轮未使用容器。

## 逐项结果

- D1：**证据不足**。`mvn -f cloud/pom.xml "-Dtest=AgentConversationPersistenceMappingTest,AgentConversationStoreTest" test` 退出 0，7 项，0 失败、错误、跳过。原映射断言文件无 diff；针对旧字段的新增测试用 mock 验证 ALTER 和存在时不 ALTER，并非在真实旧库验证升级和数据保留。记录 `tmp/cloud-d1-independent-r2.log`。
- D2：**证据不足**。`mvn -f cloud/pom.xml "-Dtest=AgentConversationStoreTest,AgentPreviewControllerTest,AgentPreviewProxyWikiEventTest" test` 退出 0，13 项，0 失败、错误、跳过。未证明真实旧数据历史投影、Wiki 写入后重开；不能仅由 mock 和已有事件测试推定。记录 `tmp/cloud-d2-independent-r2.log`。
- B4 两模块回归命令：**通过（命令层面）**。`mvn -f cloud/pom.xml test` 退出 0，383 项，0 失败、0 错误、1 跳过；`mvn -f cloud_agent/pom.xml test` 退出 0，92 项，0 失败、错误、跳过。唯一跳过 `AgentPersonalFileStoreTest.refusesExistingSymlinkAsParent`，原因是本机符号链接不可用、原有 assumption 跳过。记录 `tmp/cloud-independent-local-r2.log`、`tmp/agent-independent-local-r2.log`。此前运行残留的 Surefire XML 不能混入本次总数，以本次 Maven 汇总为准。范围与其他 B 项由对应测试者核对。

## 安全检查与清理

默认 Surefire 测试扫描不包含 `*IT`；本轮全量启动的 `CloudApplicationTests` 使用动态生成的随机 schema、DataSource 获取连接时核对端点及所有权标识，Wiki 缓存和 RAG 索引 mock，Redis 指向不可用本机端口，文件路径指向新临时目录。测试日志显示仅创建并确认删除本次 `codex_full_it_c82e642231624090bbb0b1d7886fdd53` schema；未操作用户已有 schema 或 Redis 业务数据。该结论基于代码和日志，不构成对所有外部副作用的绝对证明。

夹具未删除其临时文件目录；本次对应 `codex-full-it-files-16008564144957170468` 保留于系统临时目录。临时 Maven 日志保留于上述 `tmp/` 路径，可能含调试数据，不作为公开附件。未重试先前被策略拒绝的删除操作。未做变异检验，任务卡未指定。

## 问题与限制

问题 #3 的原映射失败在本轮独立定向及 Cloud 全量不再复现；问题 #6 的真实旧库升级、历史投影和写后重开证据不足仍未解决。开发记录另述 #9 配置值出现在开发者工具输出中，本轮未核查或重复输出该值。无新增已确认的固定验收缺陷；未做代码修复或改写验收。
