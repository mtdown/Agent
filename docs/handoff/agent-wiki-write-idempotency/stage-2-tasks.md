# 阶段 2 开发任务卡
请读取 AGENTS_rules.md 与 .agents/skills/openspec-apply-change-local/SKILL.md，以及本 change 的 proposal、design、specs 与 tasks。Change ID：agent-wiki-write-idempotency；阶段 2；任务 2.1、2.2、2.3；轮次 1。
目标：只针对单篇 Wiki Agent 创建实现数据库原子幂等、可重复检测的迁移和服务端操作身份。逐项执行以下固定验收，不降低标准。范围预计 10–15 文件；如实质超范围或发现安全/契约冲突则停止报告。不得开发阶段 3 查询恢复功能。
基线：feature/agent-write-idempotency开发 的 f5ecb69 及已验收阶段 1 未提交测试证据。不得修改阶段 1 测试及报告、其他人文件、公开 API、其他写工具或现有用户业务数据。允许在 cloud、cloud_agent 下为实现和测试新增/修改必要业务文件、单次操作表增量迁移、专属测试，并在 docs/issue-reports/agent-wiki-write-idempotency/ 写阶段报告及新证据。不可自行删除资源、数据和测试目录，不使用 worktree；隔离测试沿用阶段 1 专用环境，核验身份及本地网络隔离；不得连接真实业务库、线上或模型服务。禁止打印凭据或博客正文。注意本地 application-local.yml 含疑似凭据，避免输出配置或重复暴露；问题 #31 交总管处理。
对外契约：仅内部单篇创建请求可选 operationId，旧客户端无 ID 保持结果字段及行为。由服务器生成 UUID，不能由模型指定；同次受控重传沿用，不同调用不同 ID。单用户+操作 ID 唯一，稳定 UTF-8 长度界定摘要含空间、目录、标题和正文的区别；不保存正文。文档及成功记录必须同事务提交或回滚；重复读取重验当前权限及目标状态，冲突不创建。缓存与索引外部副作用不承诺 exactly-once；隔离级别和并发按 design 明确实施。迁移可检测、重复执行不破坏数据，回滚保留表和文档。不要把数据库操作状态等同于真实用户体验。
项目经验：MySQL REPEATABLE READ 旧快照，唯一约束与锁等待，19 位 ID 以字符串序列化；写前权限状态须真实重新核验，cache/index 不在原子保证中。禁止改变固定验收；依赖 2.1→2.2→2.3，无法实现或安全运行按 BLOCKED 报告。测试前停止修改被测文件，保留数据及完整可复现实验记录，不清理资源。逐任务回执、范围、命令、退出码、证据、已知限制、精确资源和回滚方式；问题按 issue tracker 处理。

## 固定验收（tasks.md 原文）
- [ ] 2.1 增加可检测的建表迁移和唯一约束，基于用户及服务端生成 ID 保存版本化请求摘要与结果，正文不入操作表；验证：`mvn -f cloud/pom.xml -Dtest=AgentWikiIdempotencyMigrationLocalIT test`（专用隔离 MySQL）；预期同一用户同一 ID 第二条记录被唯一约束拒绝、不同用户或不同 ID 可存储，迁移再次执行不破坏既有数据，表不含正文。
- [ ] 2.2 实现单篇创建原子事务、重复请求复用结果和内容冲突拒绝，旧请求不传 ID 保持原结果字段及原语义；验证：`mvn -f cloud/pom.xml -Dtest=AgentWikiIdempotencyCreationLocalIT test`；预期同 ID 相同内容顺序重放及 30 组每组 2 并发调用均只增加 1 个文档且成功结果 ID 相同，同 ID 改正文或目标被拒绝且新增 0 文档；注入保存前及记录前异常均无文档及成功记录；旧请求和不同 ID 各自仍可创建。
- [ ] 2.3 服务端生成、绑定并传递可信操作 ID，禁止模型参数提供 ID；记录本阶段结果与兼容部署顺序。验证：`mvn -f cloud_agent/pom.xml -Dtest=AgentWikiIdempotencyIdentityTest test` 及 `mvn -f cloud/pom.xml -Dtest=AgentWikiIdempotencyCreationLocalIT test`；预期同一次调用的受控重传 ID 一致、不同工具调用 ID 不同、模型传入 ID 被拒绝或忽略且不能改变服务端 ID，旧请求不报新必填错误；报告包含实际运行证据。回滚：停用 ID 发送、退回兼容应用，保留表及文档。
