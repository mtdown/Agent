# 3.1a 导入确认后端开发记录，第 1 轮

任务分支 `feature/agent-wiki-workflows开发`，基线 `bbc1819f7d44a0968638b193ff5509fec5786046`。负责人明确调整 C1：模型误触发时允许生成确认草案，但未经用户明确确认不得实际导入；原 C1 第 2 轮 FAIL 和证据保留，不改判。#10 自检后设为待验证，独立验收及前端联验尚未完成。

实现：旧 Agent 内部 URL 导入入口只返回受控草案，不再调用导入服务。Agent 检测草案并停止，Cloud 将用户、会话、轮次、目标、完整 URL、路径快照、期限和确认/取消独立随机令牌存入 interactionJson；复用现有 wiki-choice 端点和 SSE，服务端依据持久草案类型选择行为，锁定会话并原子消费。导入确认拒绝附带额外参数；原空间选择的额外字段行为保持。保存确认卡失败不发送卡片；领取先落 unknown，再执行，成功/部分结果投影保存，结果持久化失败仍是未知，不自动重试。用户取消落 cancelled。旧端点 URL 和请求字段不变，交互数据新增 import_confirmation 类型。

空间和文件夹重命名加入与导入/移动相同空间锁并锁后复查；按用户展示的空间标签和每级目录 ID、名称快照在写入事务锁内比对，不一致拒绝写入并提示重新确认。缓存失败后原有改名已提交的行为使用 noRollbackFor 保留，经真实数据库自检。空间锁内现有权限/目录复查继续生效。

因一个 interactionJson 无法同时表达先前写入结果与待确认草案，本轮已出现 Wiki 写入事件时再请求网页导入会标 blocked，需要用户在新轮再次提出；防止覆盖前次结果及发出不可领取的卡片。模型误触发只能生成草案；不是声称能杜绝模型误调用。用户侧路径提供 folderSegments 数组，避免名称分隔符歧义。

修改事实：本任务涉及 18 个代码/测试文件，超过预计 8–15 个，原因是为卡片持久、鉴权和状态投影跨 Cloud/Agent、扩展负责人授权的空间/目录锁、隔离数据库竞争测试及现有测试适配。相对于本任务开始前已存在的其他开发内容，修改 AgentConversationStore、AgentPreviewController、AgentPreviewProxy、AgentResourceInternalController、AgentWikiChoiceProjection、AgentWikiChoiceService、AgentUrlImportService、WikiFolderServiceImpl、WikiSpaceServiceImpl、AgentLoop、AgentWikiBoundary；测试新增 AgentImportConfirmationTest、AgentImportConfirmationLocalIT，并修改 AgentPreviewProxyWikiEventTest、AgentUrlImportSkillTest、AgentUrlImportTest、AgentUrlImportConcurrencyLocalIT、AgentWikiWorkflowsLoopTest。部分文件在任务前已存在未提交改动，不可整体回滚这些文件。未修改前端、OpenSpec、迁移或依赖；前端由并行任务处理。

实际自检（Java 17）：
- 修复前仅运行 mock 的 `AgentUrlImportTest#internalImportRequestDoesNotWriteWithoutUserConfirmation`：1 项失败，确认旧入口调用实际服务；修复后 1 项通过。
- `mvn -f cloud/pom.xml "-Dtest=AgentUrlImportSkillTest" test`：退出 0，3 项通过；`mvn -f cloud_agent/pom.xml "-Dtest=AgentWikiWorkflowsLoopTest" test`：退出 0，8 项通过，含误调用产生草案及先前写入阻断。
- `mvn -f cloud_agent/pom.xml "-Dtest=AgentWikiWorkflowsLoopTest,AgentLoopTest,AgentToolRegistryTest,DeepSeekWebResearchTest" test`：退出 0，40 项通过。
- Cloud 定向 `AgentImportConfirmationTest,AgentUrlImportTest,AgentUrlImportSkillTest,AgentPreviewProxyWikiEventTest,AgentPreviewControllerTest`：退出 0，32 项通过；包括 mock 的跨用户/跨会话/伪造/过期/取消/重复、篡改额外字段、结果持久失败不重试、确认卡持久前不发送及保存失败不发送。测试替身并非真实权限并发证明。
- `mvn -f cloud_agent/pom.xml test`：退出 0，100 项通过，无跳过。
- `mvn -f cloud/pom.xml test`：退出 0，397 项、失败和错误 0、跳过 1；跳过 `AgentPersonalFileStoreTest.refusesExistingSymlinkAsParent`，Windows 当前账户无法创建符号链接触发原 assumption，该行为未验证，不改断言。Cloud 使用本机 3307 的随机专属 schema、数据源归属检查、缓存及 RAG 替身、独立文件根；schema `codex_full_it_94ccd0b8deda4d938376c6aa13887b12` 创建及删除已核验。
- `mvn -f cloud/pom.xml "-Dtest=AgentUrlImportConcurrencyLocalIT,AgentImportConfirmationLocalIT" test`：退出 0，24 项通过、无跳过。验证同确认并发只创建一篇、取消不新增，已成功交互持久化；空间改名、祖先改名、目录移动各先于和后于导入的六个屏障竞争，直接读回文档位置与数量；另验证改名后缓存异常仍保留重命名。两个专属 schema `codex_full_it_5c39e75b027d40e8bf3eb797e248f6d1` 和 `codex_wiki_it_4bb6a5fb584e47299c419e41f45a7984` 均已核验删除。持久化故障、死锁采用注入异常，不代表真实死锁复现。
- `openspec validate agent-wiki-workflows --strict`：退出 0；`git diff --check` 退出 0。以上均为开发自检，不替代独立验收。

测试隔离：已有本机 MySQL，未创建容器；仅使用通过服务器、随机名及所有权标记核查的专属 schema；缓存 mock，端口 1 预检无服务，网页模拟；独立临时文件目录保留，未访问真实用户数据。凭据仅在静默子进程环境内注入，输出限定为结果计数和无敏感库标识；未主动输出配置或凭据。没有启停服务或删除其他目录。

尚未验证：前端确认/取消显示和断线刷新须与 3.1b 联合验收；浏览器中断及真实多项部分结果恢复未做完整端到端验证；确认后进程中断默认未知，不能恢复全部部分写入，也不承诺恰好一次；真实旧库升级不在本任务范围。测试期间任何需冻结快照的文件均由总管确定；本任务停止写入后等待独立测试。

回滚按上述文件内 3.1a 差异精确回退，保留同文件中任务 1.1/2.1/2.2/3.1 的已有修改和所有用户 Wiki 数据；不可直接整文件 checkout。未提交、推送、归档或勾选任务。
