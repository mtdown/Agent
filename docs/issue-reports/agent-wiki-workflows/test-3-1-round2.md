# 测试报告

Change ID／阶段／任务／轮次：agent-wiki-workflows／3／3.1／第 2 轮；2026-11-01 +08:00。测试实例：/root/test_skill_r2（独立测试子智能体）。总体结论：FAIL（C1，#10）；另有 C3 跳过项导致覆盖证据不足及安全事件 #12。

环境与基线：Windows 本机、Java 17、IntelliJ IDEA 2024.1 内置 Maven；分支 feature/agent-wiki-workflows开发，HEAD 与指定基线 bbc1819f7d44a0968638b193ff5509fec5786046 一致。未切换或运行基线。测试未使用容器或真实外网。Cloud 全量使用本机 MySQL 随机专属 schema、进程环境变量凭据；Redis 设为本机不可用端口 1，文件根为系统临时目录；测试前确认端口 1 无监听。上下文测试 mock 缓存及 RAG，并以 marker、服务器身份和 schema 名校验数据源。

被测范围与快照：完整 42 文件列于 tmp/agent-wiki-workflows-p3-r2/manifest.json；含新增文件完整差异为同目录 complete-diff.txt。测试前后分别用 Python SHA-256 核对每个文件并比较 `git diff --name-only HEAD` 与 `git ls-files --others --exclude-standard`（排除报告目录），均无摘要或清单差异；manifest SHA-256 a7ebbbce1eb59af55dc05b7e3e634f33691412ec1a40099204ee4e96d6dd05b6，complete-diff SHA-256 43cd0186f62c0ceede637ffd8597bb4287ebc5d1765ffe39ee618647240a7b34。git diff --check 退出 0。测试期间未观察到被测文件变化；无法据此证明中间瞬时写入不存在。

开发过程及修改事实记录：docs/issue-reports/agent-wiki-workflows/developer-3-1-round2.md SHA-256 f7069c47863dc681f4947dc321e5546b0a1efe5f465917505be8d3ae97fbc8c6，与任务卡一致。独立阅读相关测试和提示实现，复核其真实注册工具、mock gateway、混合结果投影、换行及受控模型限制；测试结果与其报告的数量一致。本轮未重做其红灯及初始夹具错误，未单独核实其历史过程时间和此前 schema 查询，不能把开发自检当独立证据。

验收项结果：

- C1：FAIL。命令 `mvn -f cloud/pom.xml "-Dtest=AgentUrlImportSkillTest" test` 退出 0，2 项通过；`mvn -f cloud_agent/pom.xml "-Dtest=AgentWikiWorkflowsLoopTest" test` 退出 0，7 项通过。正向受控模型按步骤请求 Skill、位置及导入；真实注册工具将可信用户、所选空间和目录传给 mock gateway；Cloud 测试在模拟授权文档查询下核对逐项状态和投影。关键反例 `summaryIntentIsPromptOnlyWhenModelStillRequestsAWrite` 输入“只摘要网页，不要保存”，受控模型违反提示后真实注册的导入工具仍调用 mock gateway，违背固定要求“用户只要求摘要不得调用写工具”。未访问真实网络和数据库，不证明真实文档创建。规划工具部分为替身，不能声称完整真实 UI 或权限端到端。证据：tmp/agent-wiki-workflows-p3-r2/test-cloud-c1.log、test-agent-c1.log 和测试源码；#10 保持待处理。
- C2：PASS，范围为固定命令及可控模型断言。`mvn -f cloud_agent/pom.xml "-Dtest=AgentWikiWorkflowsLoopTest,AgentLoopTest,AgentToolRegistryTest,DeepSeekWebResearchTest" test` 退出 0，39 项通过、无跳过。断言实际系统提示包含目标、时间、可信边界、真实状态、未知写入不重试和真实换行；受控模型将恶意资料当数据、不调用写工具，另有普通对话、工具及搜索测试。受控模型行为不能证明自由模型必遵循指令；#11 对所记录缺陷的复测通过。证据：tmp/agent-wiki-workflows-p3-r2/test-agent-c2.log。
- C3：证据不足。`mvn -f cloud/pom.xml test` 退出 0，385 项、失败和错误 0、跳过 1：AgentPersonalFileStoreTest.refusesExistingSymlinkAsParent 因 symlinks unavailable 被假设跳过，该行为未验证；`mvn -f cloud_agent/pom.xml test` 退出 0，99 项、失败错误跳过均 0；`openspec validate agent-wiki-workflows --strict` 退出 0，valid。核对 diff、清单和资源，未发现本轮新增数据库迁移、依赖或公开 HTTP API；权限端到端及被跳过项不由此证明。证据：tmp/agent-wiki-workflows-p3-r2/test-cloud-full.log、test-agent-full.log、test-openspec.log 和 Cloud Surefire XML。

固定验收缺陷：#10 如 C1。清单外交付阻断问题：安全事件 #12，配置脱敏表达式失效导致一次工具输出包含本机数据源配置及凭据，时间为本轮测试期间，输出可访问范围不明；没有在报告复述值，未擅自轮换。对本轮 6 个测试日志做内存中密码值匹配，仅输出布尔结果，未发现该密码值；不代表其他输出或系统均无暴露。需负责人处理。

未验证项：C3 的符号链接防护跳过；自由模型遵循及真实外网端到端不在受控模型证据中。变异检验：任务卡未授权，未执行。问题记录：#10 待处理；#11 独立复测关闭；#12 待处理；#13 符号链接跳过证据不足。已有 #5/#7 未在本轮裁定。

清理：Cloud 全量日志记录专属 schema codex_full_it_79af351212cc439a94a13a9a8050788e 创建和删除；fixture 删除后在同连接以 information_schema 精确查询为 0 才输出 removed，故确认清理。Redis 未连接已有服务；未启动或停止开发服务。测试构建产物、系统临时目录及本轮 tmp 日志保留以供核查，未清理其他测试目录或策略拒绝路径。风格意见：无。
