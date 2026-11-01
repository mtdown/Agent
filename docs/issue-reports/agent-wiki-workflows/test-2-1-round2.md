# 阶段 2.1 第 2 轮独立测试报告

Change agent-wiki-workflows，任务 2.1，第 2 轮，2026-11-01（Asia/Shanghai），实例 /root/test_import_local_r2。总体 BLOCKED、证据不足；不是 PASS。分支 feature/agent-wiki-workflows开发，HEAD bbc1819f7d44a0968638b193ff5509fec5786046。基线本轮未执行，不据此判断基线回归。

快照：tmp/agent-wiki-workflows-p2-r2-manifest.json SHA256 f258e54215a258145632403325a1cacdd38768fe340f0b1e0b377882ffdfa06b；用 Python json 逐路径计算 SHA256，测试前后各 58 项全部匹配。完整差异 tmp/agent-wiki-workflows-p2-r1/complete-diff.txt SHA256 029481b507d599a4c88de85a7bab2ad3a1aa76f2380ee34bc4952c09c110cd8a，另核对本轮测试夹具和测试规则变更。开发记录 developer-2-1-round1.md 与 developer-2-1-local-mysql-fixture.md 的 SHA256 分别为 6ac3d8140a509c3f232c3c9f01889f3955a3227107133692354cdfc81b547acc、4d3dd7ece69a7fa0cb0f6d7bc5f9db250bab674f8f2cc117307c15ec9fa15e61；独立测试结果见下，不能用自检代替验收。

使用 IDEA 2024.1 Maven、Windows、本机 MySQL；离线执行，凭据仅从本机 local 配置传给 Maven 子进程，不打印凭据。B7 夹具固定环回地址，创建随机 schema，核验空库、服务 UUID 和所有权标记；从部署 SQL 白名单提取建表，关闭时同连接核验标记并删除本次 schema。缓存、抓取、RAG 用替身，Redis 指向端口 1，文件根为新建测试临时目录；代码无 Docker 创建调用。本轮无操作现有业务数据。Maven 日志保存在 tmp，可能含环境信息，不应直接公开。

- B1 证据不足：Cloud 定向 36 项及 Agent 全量 92 项均通过，含选择目标、禁止自选用户、普通摘要不写入；本机真实 MySQL 集成测试通过且读取到超过 5000 字的正文，但未逐字断言正文完整且来源、标题的同一次端到端保存。问题 #7 继续追踪。
- B2 PASS：缺少/错误内部密钥、权限变化、非法目录不得保存；目标 ID 限制、合法目录、已选空间与真实用户绑定及投影不向文本泄露 ID 的测试通过；B7 对成员权限与目录删除并发也有验证。
- B3 PASS：逐项成功、失败、未知、未执行，网络中断不重试、私有地址拒绝和现有事件的定向断言通过。
- B4 BLOCKED：Agent 全量 `mvn -o -f cloud_agent/pom.xml test` 92 项、0 失败错误或跳过；Cloud 全量未执行。现有 CloudApplicationTests local 启动可触发旧标题全局更新、条件 DDL 及 Redis 通配清理，本机可安全隔离其真实目标的配置尚未确认，不能指向已有业务数据冒险。没有数据库迁移、依赖或公开 HTTP 接口新增的范围检查仍由总管结合完整差异核对。
- B5 证据不足：成功/失败的缓存重试各 2 次、保存各 1 次、提交未知无缓存及无重复保存的断言通过；未验证脱敏诊断日志的具体内容，#7 保留。
- B6 PASS：内部四态、未知中止后续写入、HTTP 中断未知、逐项失权与目录拒绝、旧公开接口 SUCCESS/FAILED 行为对应定向断言通过。
- B7 PASS（死锁限制见下）：`mvn -o -f cloud/pom.xml -Dtest=AgentUrlImportConcurrencyLocalIT test` 退出 0，15 项、0 失败错误或跳过。12 项覆盖撤权、改角色、退出、目标/祖先删除和空间删除双方先提交顺序；另有目录移动、空空间删除竞争、持久化失败，直接读回文档和目录状态。失败场景注入 DeadlockLoserDataAccessException，验证未知、后续未执行、保存一次，未复现真实 MySQL 死锁；不将其描述为真实死锁测试。测试 schema 删除日志确认，独立只读 information_schema 查询本次 schema 数为 0。固定要求覆盖失败不重复保存，不代表证明所有真实死锁调度。

其他命令：`mvn -o -f cloud/pom.xml -Dtest=AgentUrlImportTest,AgentPreviewProxyWikiEventTest,WikiBatchImportServiceImplTest test` 退出 0，36 项、0 失败错误或跳过；Agent 全量包含 AgentUrlImportToolTest 与 AgentWikiChoiceLoopTest。测试报告查看各测试源中断言而非仅以退出码判定。Cloud 全量阻塞相关既有问题 #5；#7 为长期证据不足。#1、#2、#4、#8 的特定复测证据见本报告；真实旧库及会话任务由其独立测试者负责。未做变异检验，任务卡未要求。无测试期业务代码修改；未启停本机服务，测试 schema 已清除，测试夹具创建的临时文件根未删除。没有提出修改实现或弱化固定验收。
