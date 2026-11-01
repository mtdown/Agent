# 测试报告

Change ID／阶段／原子任务／轮次：agent-wiki-workflows／1／1.1／1；2026-10-31（Asia/Shanghai）
测试实例标识：/root/test_wiki_answer_r1
总体结论：PASS
环境与基线：Windows、Java 17、任务卡指定 IDEA Maven 路径；分支 feature/agent-wiki-workflows开发，HEAD bbc1819f7d44a0968638b193ff5509fec5786046。基线未独立运行，不对基线结果作推断。未使用真实外网、真实密钥或数据库，未启停开发服务；网关测试仅监听本机临时端口并在 finally 关闭。

被测范围与快照：任务卡 manifest.json 共 14 项，SHA256 193f1cd54818c56fc81fa292ece6e6037310c0566e8055d04274e7f5ed1d7b7f；complete-diff.txt SHA256 d367da363c8bb97fe4efe5c5748090a7d444b6bc2abe7bded0d9003f837265c2。测试前后使用 Get-FileHash -Algorithm SHA256 逐项与 manifest 比较，全部匹配；测试后结果见 test-1-1-r1-snapshot-after.txt。两次 git status --short --untracked-files=all 除本轮报告与日志外一致，所有被测变动均在清单内。

开发记录核验：developer-1-1-round1.md SHA256 849f58ef391817bbf1834c735818cfefa500e6e7c858507315ff0fa0aa65bb14，符合任务卡。独立检查完整 diff：新增内置定义及读取类、修改目录与网关、两个新增测试和一个空结果断言，事实与记录一致；未见数据库、依赖、公开 HTTP 端点改动。开发过程先后顺序及其历史自检无法独立重演，以上独立测试不依赖该叙述。git diff --check 退出 0。

验收项：

- A1：PASS。执行 `mvn -f cloud/pom.xml "-Dtest=AgentWikiAnswerSkillTest" test`，退出 0，1 项通过。断言两个模拟用户、100 个用户条目时内置写入与问答位置、catalog 版本、读取一致、错误版本与禁用拒绝，查询绑定用户；定义验证 response 类型、证据／引用／不足和无用户内容。独立检查定义仅含现有证据响应步骤，无检索或执行工具。证据 test-1-1-r1-a1.log。
- A2：PASS。执行 `mvn -f cloud_agent/pom.xml "-Dtest=AgentWikiAnswerLoopTest" test`（退出 0，4 项）和 `mvn -f cloud/pom.xml "-Dtest=AgentPreviewControllerTest" test`（退出 0，4 项）。受控模型断言当前证据和版本目录、调用 get_skill 后读取内容；其他测试验证普通对话不调用 Skill、其他 Skill 可选、网关绑定用户。空检索测试验证搜索参数及 proxy 无交互，未声称模型调用。证据 test-1-1-r1-a2-agent.log、test-1-1-r1-a2-cloud.log。
- A3：PASS。执行 `mvn -f cloud/pom.xml "-Dtest=AgentSkillCatalogTest,AgentPreviewControllerTest" test`（退出 0，5 项）及 `mvn -f cloud_agent/pom.xml "-Dtest=AgentLoopTest,AgentToolRegistryTest" test`（退出 0，28 项）。断言目录条件、未授权空间不搜索、一般工具及 Skill 路径；A1 同时断言原内置写入项保留，diff 显示其读取分支不变。实现差异无数据库、依赖、公开 HTTP 接口变化。证据 test-1-1-r1-a3-cloud.log、test-1-1-r1-a3-agent.log。

固定验收缺陷：无。清单外交付阻断问题：无。未验证项：真实模型选择稳定性和真实外网均不在本次模拟范围。变异检验：未执行，任务卡未授权。问题记录：无。临时文件和清理：未新增探针；保留本轮报告、五份 Maven 日志和快照证据；Maven 常规 target 保留，模拟 HTTP 服务器已关闭，未启动开发服务或创建用户数据。风格意见：无。
