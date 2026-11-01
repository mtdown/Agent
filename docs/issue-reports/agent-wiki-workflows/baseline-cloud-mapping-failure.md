# Cloud 全量回归基线问题

2026-10-31，阶段 2 任务 2.1 开发自检：`mvn -f cloud/pom.xml test` 中 `AgentConversationPersistenceMappingTest.conversationStoreUsesMappedEntities` 在第 31 行失败，期望没有 JdbcTemplate 字段，实际存在。该项与 Wiki 导入修改无直接文件交叉。

为核对基线，使用 `git archive` 将提交 `bbc1819f7d44a0968638b193ff5509fec5786046` 的 cloud 目录导出到 `tmp/baseline-wiki-verification/`（不是 Git worktree；未切换分支、未覆盖共享代码），再在仓库根目录、指定 Maven PATH 下运行：

```text
mvn -f tmp/baseline-wiki-verification/cloud/pom.xml "-Dtest=AgentConversationPersistenceMappingTest" test
```

退出码 1：`Tests run: 1, Failures: 1, Errors: 0`，同一测试第 31 行 `expected: <false> but was: <true>`。该单元测试为反射检查，无数据库或网络访问。完整输出位于 `tmp/baseline-wiki-verification/result.log`。基线类包含 `JdbcTemplate` 字段，见该提交的 `AgentConversationStore.java`。阶段 2 未修改该业务代码或测试；此证据不改变 B4 固定验收要求。
