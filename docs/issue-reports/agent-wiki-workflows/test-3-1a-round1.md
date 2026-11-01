# 测试报告

Change ID／阶段 ID／原子任务 ID／轮次／时间：agent-wiki-workflows／3／3.1a／第1轮／2026-11-01（Asia/Shanghai）
测试实例标识：/root/test_confirmation_backend（独立测试子智能体，共享工作区）
总体结论：证据不足；本角色范围内未发现固定项 FAIL。E1–E3 全部交互验收未由本角色完成，不能代替前端联合验收。

环境与基线：Windows，本机 IntelliJ IDEA 2024.1 内置 Maven，Java；分支 feature/agent-wiki-workflows开发，HEAD bbc1819f7d44a0968638b193ff5509fec5786046。未切换或运行基线。受控网页替身，不访问真实外网；本机回环 MySQL 3307 使用随机专属 schema，不操作现有用户库。端口 1 预检未监听。未启停服务、未使用容器。

被测范围与快照：完整 51 文件及各自 SHA256 见 tmp/agent-wiki-workflows-confirm-r1/manifest.json（SHA256 191b85943415140ef97746fda688126473e5b8a8a4da247e62ddfdbec2ebf78e）；完整差异含新增文件见 complete-diff.txt（SHA256 f7a161403782ea0945d803fab643360f9ce9cc7f1b66d24f186ad3032f1d0dfe）。开始与结束均用 PowerShell ConvertFrom-Json、Get-FileHash 逐项核对 51 项，均 0 不匹配；HEAD 与两摘要均一致。任务清单为允许共享工作区的冻结输入，未声称基线回归。

开发过程及修改事实记录核验：docs/issue-reports/agent-wiki-workflows/developer-3-1a-round1.md SHA256 60f8196ad1a05c82f64f10c470c31f7ae2a339e9dc27237f6ee6906f23d35b92，前后匹配。独立核对服务端选择与导入调用、内部草案、确认存储与显示代码，以及下述测试；报告的范围跨 Cloud、Agent 及并发夹具，与清单和实际文件相符。原始任务开始前同文件未提交差异无法从本快照独立逐行归属，18 文件数量及历史自检仅为开发记录，非独立证明。

验收项：
- C1：证据不足。Agent 全量包含固定 AgentWikiWorkflowsLoopTest，Cloud 定向包含 AgentUrlImportSkillTest，均退出 0。受控模型 summaryMisfireStopsAtConfirmationDraft 和 Cloud 内部入口零写断言检查误调用；确认导入的真实数据库组件测试证明一次调用写入。模型、Skill 和 Cloud 之间仍未做完整受控端到端串联并向用户核对所有逐项结果。原 C1 第2轮 FAIL 原样保留；本轮按负责人修订后的确认前零写要求测试。问题 #10。
- C2：PASS。mvn -f cloud_agent/pom.xml test 退出 0，100 项、0 失败、0 错误、0 跳过，包含固定组合 AgentWikiWorkflowsLoopTest、AgentLoopTest、AgentToolRegistryTest、DeepSeekWebResearchTest。受控测试覆盖目标、时间、工具、恶意资料和未知边界，不保证自由模型表现。日志 tmp/agent-wiki-workflows-confirm-r1/independent-agent.log。
- C3：证据不足。Agent 全量如上；mvn -f cloud/pom.xml test 退出 0，397 项、0 失败、0 错误、1 跳过；跳过 AgentPersonalFileStoreTest.refusesExistingSymlinkAsParent，符号链接假设未满足，行为未验证（既有 #13）。openspec validate agent-wiki-workflows --strict 退出 0，git diff --check 退出 0。全量不是无跳过，不称全覆盖。
- E1：证据不足。mvn -f cloud/pom.xml "-Dtest=AgentImportConfirmationTest,AgentUrlImportTest,AgentUrlImportSkillTest,AgentPreviewProxyWikiEventTest,AgentPreviewControllerTest,AgentConversationStoreTest" test 退出 0，38 项、无跳过；确认卡保存前不发送和保存失败不发送为替身断言。真实 MySQL 集成测试使用模拟网页、登录用户和服务端组件，确认后写入、取消无新增；浏览器、完整 Agent/Cloud/前端串联及全部链接和逐项展示未验（#14）。内部入口和登录入口源码核对分别返回草案和由登录用户领取；未证明全部可能的部署入口。
- E2：后端定向 PASS，完整 E2 证据不足。mvn -f cloud/pom.xml "-Dtest=AgentUrlImportConcurrencyLocalIT,AgentImportConfirmationLocalIT" test 退出 0，24 项、0 失败、0 错误、0 跳过；两随机专属 schema 创建及删除记录各 2 且一一匹配。真实数据库确认竞争两个调用仅一个成功、一次 fetch、一篇文档并保存结果，取消不新增。权限、删除、空间改名、祖先改名、目录移动先后竞争含锁住期间另一操作未完成及提交后文档数量、目录断言；使用屏障和短等待，未直接观测数据库锁等待表，死锁为注入异常。跨用户、跨会话、伪造、过期、额外字段由模拟 JDBC 测试，不是独立真实数据库授权联验。源码 claim 使用登录用户、会话行 FOR UPDATE、最新轮、随机 token、一次消费，写事务内比较位置和权限；无法由单一单测推出完整部署边界。
- E3：证据不足。受控模拟结果保存故障、未知不重试、保存卡前发送顺序；真实数据库测试部分检查成功和取消持久状态，缓存异常为替身。没有浏览器断线、刷新与多项部分成功的完整交互恢复，不能证明用户实际看到的全部状态（#14）。

固定验收缺陷：无已证明 FAIL。清单外交付阻断问题：未发现。未验证项：上述跨组件及浏览器场景、真实数据库授权端到端、真实死锁、符号链接跳过及真实旧库升级；不以替身结论代替。变异检验：任务卡未指定，未执行。

问题记录：#10 按本次 C1 证据不足更新并保留旧第2轮 FAIL 与原证据；#13 保留全量跳过；#14 保留浏览器及联合验收证据不足。前端测试由独立测试者负责；本报告不修改其记录。

临时文件、数据及服务清理：所有本次 schema 由夹具以名称、服务器 UUID、所有权标记核验后删除，创建删除记录匹配；独立文件根由夹具创建并留存，未删除任何非本次文件或先前被拒删除的对象。独立测试汇总及日志位于允许的 tmp/agent-wiki-workflows-confirm-r1；未输出配置、凭据或数据库原始日志。测试脚本无凭据，仅进程内静默解析并注入；测试结束移除脚本。未启停本地服务。风格意见：无。
