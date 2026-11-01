# 阶段 3.1 第 1 轮问题处理开发记录

Change agent-wiki-workflows，阶段 3／任务 3.1；任务分支 feature/agent-wiki-workflows开发，HEAD bbc1819f7d44a0968638b193ff5509fec5786046。本轮依据独立报告 test-3-1-round1.md 处理 #10、#11；这是开发自检，不是新一轮独立验收。被测文件停止写入时间：2026-11-01 15:07:38 +08:00。

## 本轮修改

- cloud_agent/src/main/java/com/et/agent/AgentLoop.java：将系统提示前的 Java 字面反斜杠 n 改成两个实际换行，SHA-256 4804ea621009911d527211861e875d3725f63208b070ed792527738ebd84ad68。
- cloud_agent/src/test/java/com/et/agent/AgentWikiWorkflowsLoopTest.java：补充模型看到的目标/时间规则和各类资料可信边界、受控模型拒绝恶意 Wiki/工具/目录内容、真实注册工具和可信用户/空间/目录别名、混合状态保留、违背提示时的可执行边界，SHA-256 1851a5fa5851c5c9dd9e34c5b76937cdf8a675d0dcb56099570734da60f62c5e。
- cloud/src/test/java/com/et/cloud/controller/AgentUrlImportSkillTest.java：调用真实 AgentWikiChoiceProjection 和 AgentWikiChoiceService 的当前显示/回答逻辑，模拟已授权文档查询结果，断言 success、failed、unknown、not_executed 的逐项顺序、来源、回答及内部 ID 隐藏，SHA-256 94629d6eb87b4c108bbd1e0af8a70fb044fbf05394176898200b35d682857836。

未修改此前阶段代码、业务导入、安全边界、数据库迁移、依赖、公开接口或 OpenSpec。原有测试断言保留。本轮模型与数据替身使用本地内存及回环，不访问真实外网或写真实用户数据。

## 已确认边界（#10）

固定 C1 原文要求用户只要求摘要时不得调用写工具，验证方式是可控模型断言无写分支；该措辞并未写明对不遵循提示的模型可例外。新增测试采用用户“只摘要网页，不要保存”，可控模型先调用真实注册的 list_writable_wiki_locations 获得别名，再主动调用真实注册的 import_wiki_urls；AgentToolRegistry.bind 按可信用户 7 和已选空间 11 校验参数后，调用被模拟的 gateway.importWikiUrls。该测试确认调用到达模拟 gateway；没有调用真实网络、数据库，也没有证明实际文档被创建。既有服务端权限和目标检查依然存在，但它们不识别用户当前是否有保存意图；文本提示约束无法阻止不遵从提示的模型发起合法工具调用。因此不能声称当前架构对 C1 无写行为提供确定性保证，也不能把受控模型正向测试当成这类保证。是否需要额外机制及其契约由负责人裁决，本轮没有擅自引入意图识别或更改安全边界。#10 更新为待处理。

正向测试使用真实 AgentToolRegistry 注册、参数校验、已选空间绑定及请求内 Wiki ID 别名，验证选择目录 22 和可信用户 7 进入模拟 gateway；导入结果由模拟 gateway 返回。单独的 Cloud 测试验证真实结果投影和用户可见文本，但已授权文档解析采用模拟，不是整个 Cloud 实例的真实权限链，也不声称真实外网端到端。先前受控模型中的选择规划工具仍为替身；不把它表述为真实选项 UI 集成。

## 系统规则（#11）

新增断言检查模型实际收到的提示有换行，并保留用户目标、限制、请求时间、网页/Wiki/工具内容及 Skill 目录不可信、实际结果和不确定写入不重试。另一受控模型在收到恶意 Wiki、工具和目录文本后返回摘要且不发起写入。该测试证明指令文本和指定替身行为，不证明任意自由模型会服从或能对注入作安全隔离。#11 自检后转待验证，等待独立复测。

## 固定命令与实际结果

Java 17、IDEA Maven 3.9.6，项目根目录。Agent 红灯测试最初确认真实换行断言失败；另一个新增测试初期错误地以第一个 Wiki 事件为导入事件，已按真实顺序查找导入事件。Cloud 投影测试初次因 Mockito 无法模拟 final 目录类而报夹具错误，去除未使用替身后通过。保留故障事实，不将上述初始失败误称业务问题。

- C1：mvn -f cloud/pom.xml "-Dtest=AgentUrlImportSkillTest" test：退出 0，2 项，失败/错误/跳过均 0；tmp/wiki-r2-cloud-c1.log。
- C1：mvn -f cloud_agent/pom.xml "-Dtest=AgentWikiWorkflowsLoopTest" test：退出 0，7 项，失败/错误/跳过均 0；tmp/wiki-r2-agent-c1.log。其中一项刻意验证上述 #10 的未强制阻止行为，因此命令通过不等于 C1 全部要求得到保障。
- C2：mvn -f cloud_agent/pom.xml "-Dtest=AgentWikiWorkflowsLoopTest,AgentLoopTest,AgentToolRegistryTest,DeepSeekWebResearchTest" test：退出 0，39 项，失败/错误/跳过均 0；tmp/wiki-r2-c2.log。
- C3：mvn -f cloud/pom.xml test：退出 0，385 项，失败/错误 0，跳过 1。AgentPersonalFileStoreTest.refusesExistingSymlinkAsParent 因本机 symlinks unavailable 跳过；tmp/wiki-r2-c3-cloud.log。
- C3：mvn -f cloud_agent/pom.xml test：退出 0，99 项，失败/错误/跳过均 0；tmp/wiki-r2-c3-agent.log。
- openspec validate agent-wiki-workflows --strict：退出 0，valid；git diff --check：退出 0。

Cloud 全量使用现有 LocalFullTestSchema，凭据仅通过子进程环境传入；本轮专属 schema codex_full_it_47545ebafd4243869df5bde4a20eea23 创建、删除均有日志，结束后 information_schema 精确查询为 0。Redis 指向本机不可用端口，文件根为临时目录；未访问既有业务库内容，未创建容器。日志可能包含调试信息，仅保留本地 tmp，不作为脱敏公开数据。以前的测试目录及策略拒绝处理的路径未触及。

## 状态与回滚

本轮状态 PARTIAL：#10 的确定性无写边界未得到实现且需负责人裁决；#11 为待验证。#5/#7 和此前独立证据不足未关闭或改称通过。请求总管保留该限制，不能仅根据 Maven 退出 0 判定 C1 PASS。回滚仅撤销上述三个文件本轮增量，不整体恢复它们至任务基线，以免丢失前期功能与测试；保留其他任务改动及用户数据。

