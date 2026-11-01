# 测试报告

Change ID／阶段 ID／原子任务 ID／轮次／时间：agent-wiki-workflows／3／3.1／1／2026-11-01 15:00 +08:00
测试实例标识：/root/test_skill_r1（独立上下文，共享工作区）
总体结论：证据不足（C1、C2；C3 范围审查有限）
环境与基线：Windows，本机 Java、IntelliJ IDEA 2024.1 Maven 3.9.6；分支 feature/agent-wiki-workflows开发，HEAD bbc1819f7d44a0968638b193ff5509fec5786046，测试前后相同。基线未运行；未访问付费外网或用户数据，未启动容器或开发服务。开发者停止写被测文件为任务卡前提，散列复核未发现并发修改。
被测范围与快照：完整的 42 项逐文件清单及 SHA256 见 tmp/agent-wiki-workflows-p3-r1/manifest.json（SHA256 d469c88167ecf567961ac05b98d2344d341669ab596626b93a255e53face7bc0）；差异包括新增文件全文见 complete-diff.txt（SHA256 772a7362d97e371e4f5a6359bd1eb9c8e11f870bd705adec410be240d34fb905）。前后使用 PowerShell Get-FileHash 对 manifest 各项逐一比较均 42 项、0 不一致；后核对 git diff --name-only 与 git ls-files --others --exclude-standard（排除报告目录）与清单一致，0 未列或多列。测试前后分支及 HEAD 一致，git 状态中被测项一致；报告为允许新增。完整差异文件已读取并校验，重点审查本轮文件、测试及相关规格；差异规模大，未能逐行独立验证所有此前阶段的业务改动。
开发过程及修改事实记录核验与汇总：docs/issue-reports/agent-wiki-workflows/developer-3-1-round1.md SHA256 8103133ad5502046e0a95741b11b3175232b135df236a4697b432ad1bc0c9923，符合任务卡；记录所列七项文件 hash 与清单一致。独立检查内置 JSON、目录测试、Agent 测试及提示词，独立运行下列命令；开发自检不替代本报告。此前证据不足及既有问题保持原状。
验收项结果：
- C1：证据不足。操作及命令：IDEA Maven 执行 mvn -f cloud/pom.xml -Dtest=AgentUrlImportSkillTest test，mvn -f cloud_agent/pom.xml -Dtest=AgentWikiWorkflowsLoopTest test。退出码均 0，分别 1、3 项，失败/错误/跳过均 0。预期：可控模型核对工具选择、位置、逐项结果和摘要无写。实际：Skill 定义被读取，脚本模型按 skill、locations、plan、import 固定顺序调用自定义替身；断言传入目标和 unknown/not_executed 可见，但最终模型返回空结果，未断言逐项向用户报告；目录和导入替身不验证真实注册工具或权限链。摘要测试模型无条件返回空调用，只证明此替身不写，未覆盖模型尝试写入或证明服务端强制阻止。Gateway 的独立回环只核验 get_skill。相关限制记录 #10。证据：tmp/agent-wiki-workflows-p3-r1/test-c1-cloud.log、test-c1-agent.log；cloud_agent/src/test/java/com/et/agent/AgentWikiWorkflowsLoopTest.java。
- C2：证据不足。操作及命令：mvn -f cloud_agent/pom.xml -Dtest=AgentWikiWorkflowsLoopTest,AgentLoopTest,AgentToolRegistryTest,DeepSeekWebResearchTest test。退出码 0，35 项无失败/错误/跳过。预期：覆盖目标、时间、可信边界和实际结果、普通聊天、Wiki 写入、Skill/代码库及搜索。实际：注册测试以 mock gateway 验证 create_skill 和 create_code 返回的状态；AgentLoopTest 有既有 Wiki 写入和失败/未知阻断断言；Web 测试使用回环模拟搜索并断言 untrusted 等子串。没有看到对目标及时间限制、Wiki/工具/Skill 目录分别作为不可信指令的完整断言；模型拒绝不可信指令并未被测试，不将模型自由生成视作安全保证。AgentLoop.java:110 使用 Java 源码字面量双反斜杠 n，运行时为字面 \\n\\n，而非两个换行；实际模型影响未验证。相关限制记录 #11。证据：tmp/agent-wiki-workflows-p3-r1/test-c2-agent.log、上述测试及源码。
- C3：证据不足（命令通过，完整范围独立审查有限）。操作及命令：mvn -f cloud/pom.xml test、mvn -f cloud_agent/pom.xml test、openspec validate agent-wiki-workflows --strict，退出码均 0。Cloud 384 项、失败/错误 0、跳过 1；Agent 95 项、失败/错误/跳过 0；OpenSpec valid。跳过 AgentPersonalFileStoreTest.refusesExistingSymlinkAsParent，Surefire XML 记录 symlinks unavailable，该路径未验证。Cloud 前审查 LocalFullTestSchema：随机新 schema、空库和所有权标记、server UUID 检查后才删除，受守卫数据源；Redis 127.0.0.1:1，文件独立临时路径；本机 datasource 仅以子进程环境变量传递，未输出凭据。日志记录创建并确认删除 codex_full_it_fdf2cb202c2b4d038ed75588ef786e46（夹具查询 information_schema 为 0）。规格和重点权限路径已检查，但大范围差异未逐行独立验毕，不能将此前阶段证据不足归为通过。证据：tmp/agent-wiki-workflows-p3-r1/test-c3-cloud.log、test-c3-agent.log、cloud/target/surefire-reports/TEST-com.et.cloud.controller.AgentPersonalFileStoreTest.xml。
固定验收缺陷：未观察到明确功能 FAIL；#10、#11 为证据不足，复现与限制见 C1、C2。
清单外交付阻断问题：无已证实功能缺陷；字面分隔符事实及未经验证的影响见 #11。
未验证项与原因：C1 真实注册工具端到端及最终逐项报告；C2 各来源可信边界与用户目标/时间的完整断言；C3 全部早期变更逐行审查及符号链接跳过。未运行真实外网或自由模型，基线未运行。
变异检验：未执行，任务卡未授权修改被测文件。
问题记录：#10、#11 均标记证据不足，证据为本报告及指定日志。
临时文件、数据及服务清理状态：临时运行脚本已删除；允许的测试日志与 Maven 构建输出保留；独立 schema 夹具确认删除；夹具创建的系统临时文件目录未主动删除，未改动此前策略拒绝删除的路径。回环 mock 服务已随测试结束；未启停既有服务。
风格意见（不计 FAIL）：无。
