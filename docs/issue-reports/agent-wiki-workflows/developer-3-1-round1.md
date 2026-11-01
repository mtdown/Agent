# 阶段 3.1 第 1 轮开发过程与修改事实

Change agent-wiki-workflows；阶段 3／任务 3.1；分支 feature/agent-wiki-workflows开发；HEAD bbc1819f7d44a0968638b193ff5509fec5786046。本轮工作停止写被测文件时间：2026-11-01 14:52:11 +08:00。开发自检不代替独立验收；此前阶段证据不足并未因此转为通过。

## 本轮文件及 SHA-256

- 修改 cloud/src/main/java/com/et/cloud/controller/AgentLibraryStore.java：273c636ae5b467e16623671e9683fb7763adb1945cf2711c4ad31cca52b43e1a
- 新增 cloud/src/main/java/com/et/cloud/controller/AgentBuiltInWikiUrlImportSkill.java：5c2da2fe126ec259e033b6e4d638aeb004e10a15d5b19c386116f402d68dca14
- 新增 cloud/src/main/resources/agent-wiki-url-import-skill.json：d88172b22ea0dacc59305d327ecc8b13490594e8da6e21c27119758f728c520b
- 新增 cloud/src/test/java/com/et/cloud/controller/AgentUrlImportSkillTest.java：ba81c7eec85797f45d60c6ef840840af2dee2d2f5d92080e0bcadc356d9791cf
- 修改 cloud_agent/src/main/java/com/et/agent/AgentLibraryHttpGateway.java：4574fdf2e2b3afa6786577831023b580064df73503b0b5270e9d13e680f3385b
- 修改 cloud_agent/src/main/java/com/et/agent/AgentLoop.java：e1ea9847e6ac5b39a91991c0c87524f5d725ec3efaE85e7fb390e11b1317903c
- 新增 cloud_agent/src/test/java/com/et/agent/AgentWikiWorkflowsLoopTest.java：101fbcd071ed97f1fae6630adf4436b77c30b1403b74d4c7863a53827b674de4

目录新增 builtin:wiki-url-import@1 并优先于用户数量上限，复用固定版本读取。Skill 以受控响应步骤描述已有的目录查询、目标选择和 import_wiki_urls；仅明确保存时使用并如实逐项报告，unknown 不可重写。系统提示保留通用任务、时效、不可信资料、实际结果及既有能力，并把 Wiki 详细流程交给内置 Skill；原始用户提示和所有原权限及导入逻辑未修改。HTTP 签名、依赖、数据库结构和用户数据无本轮修改。

## 自检和过程

Java 17，IDEA Maven 3.9.6，命令在项目根目录。先写 Cloud 新测试并运行得到预期缺失 Skill 失败 1 项；实现后绿色。Agent 新测试先对缺失提示断言失败 2 项；实现后绿色。新增 Agent 测试后来增强为加载 Cloud 的实际 Skill 定义，重跑了 Agent 固定组合及全量。

- C1：mvn -f cloud/pom.xml "-Dtest=AgentUrlImportSkillTest" test：退出 0，1 项、无失败/错误/跳过；tmp/wiki-c1-cloud.log。
- C1：mvn -f cloud_agent/pom.xml "-Dtest=AgentWikiWorkflowsLoopTest" test：退出 0，3 项、无失败/错误/跳过；tmp/wiki-c1-agent-final.log。固定模型读取实际 Skill 定义、目录规划和已有目录别名、导入逐项 unknown/not_executed，以及摘要/普通对话无写入；另用本机回环 HTTP 核验可信用户的 Skill 读取。
- C2：mvn -f cloud_agent/pom.xml "-Dtest=AgentWikiWorkflowsLoopTest,AgentLoopTest,AgentToolRegistryTest,DeepSeekWebResearchTest" test：退出 0，35 项、无失败/错误/跳过；tmp/wiki-c2-agent.log。
- C3：mvn -f cloud/pom.xml test：退出 0，384 项、失败 0、错误 0、跳过 1；跳过 AgentPersonalFileStoreTest.refusesExistingSymlinkAsParent，系统不支持创建符号链接。tmp/wiki-c3-cloud.log。
- C3：mvn -f cloud_agent/pom.xml test：退出 0，95 项、无失败/错误/跳过；tmp/wiki-c3-agent.log。
- openspec validate agent-wiki-workflows --strict：退出 0，valid。git diff --check：退出 0。

Cloud 全量调用现有 LocalFullTestSchema 夹具，从本机配置安全传入测试专用环境变量；其动态属性和受守卫数据源确保使用临时 schema，实际创建和删除 codex_full_it_aa1037a7c75141c1a5fd43cc1c5e1045。只读 information_schema 复查该 schema 数量为 0；Redis 指向本机不可用端口、文件目录为独立临时路径，测试使用本机 mock/回环，无真实外网和容器操作。日志在 tmp，可能有测试调试数据，不能当作公开脱敏证据。

## 限制、问题和回滚

C1 的模型是按指定步骤的可控替身，工具调用和用户位置以模拟结果驱动；它不证明自由模型始终选择正确，不作为服务端鉴权或无保存意图的强制安全边界。多项导入的实际权限、状态和未知后阻止写入仍依靠已有代码和先前单元测试。本轮没有修复或关闭已有问题，尤其 #5、#7 等先前未决证据保持原状态。C3 有一项环境跳过，独立测试应据实评估，不能用退出码代替覆盖。

回滚仅恢复本轮 AgentLibraryStore.java、AgentLibraryHttpGateway.java、AgentLoop.java 对本轮内容的改动，并移除本轮四个新增 Skill、资源与测试文件；这些被修改文件存在早期阶段变更，不可整体恢复到基线。不得清除已存在用户数据、Skill 或前期工作。任务勾选、验收、同步和归档由总管处理。

