# 阶段 1 开发任务卡

请读取 AGENTS_rules.md 与 .agents/skills/openspec-apply-change-local/SKILL.md，按当前 OpenSpec 工件执行。

Change ID：agent-wiki-write-idempotency；阶段：1；原子任务：1.1、1.2、1.3；轮次：1。
目标：只对既有单篇创建路径建立可复现实验与基线证据，不改产品代码。严格本机隔离、禁止线上或真实用户数据；博客 C:/blog/source/_posts 只读选择非敏感 Markdown，报告只记录文件名、字节数与 SHA256，不暴露正文。

允许修改：cloud/src/test/java/com/et/cloud/ 下新建 AgentWikiIdempotencyBaselineLocalIT.java 及该测试专属最少辅助文件；为实现固定 A2 的真实 AgentLoop 观测，可新增 cloud_agent/src/test/java/com/et/agent/ 下专属测试辅助代码；docs/issue-reports/agent-wiki-write-idempotency/ 下本阶段记录；必要时本机被 Git 忽略的临时测试日志目录。总计预计不超过 8 文件。禁止改业务源码、现有测试或现有 schema、配置、依赖、构建文件、OpenSpec 工件或别人的文件；不得调用线上、模型、向量、对象存储；测试前验证服务端仅 127.0.0.1，核验新建隔离 schema 名称、服务器身份、所有权标记，不得连接或写入 Cloud 等现有业务库。不得 DROP schema、删除临时目录或通过脚本清理；给总管登记测试资源的准确路径或对象及具体清理命令，不自行执行。基线 HEAD f5ecb69。

契约：不修改任何公开或内部接口、数据库表、权限、缓存或并发逻辑。测试独立用户和空间，显式模拟提交后响应丢失；尽可能使用真实现有服务路径和真实 MySQL；若模拟部分需精确列明，不得声称真实网络或整链路。需支持独立测试重复运行并创建新的专用数据，不能自动清理。环境由环境变量安全提供，禁止打印凭据；若无本机 MySQL、无安全隔离授权或无法验证前提，标记 BLOCKED，禁止转而写现有库。实验记录自然流程和人工重放明确分离，不推断真实发生率。

固定 acceptance（源自 tasks.md 原文）：

A1 / 1.1：只读核对当前创建链路、旧客户端请求和结果、已有停止规则，记录代码基线提交及本地 MySQL/Redis 地址和隔离证明；验证：`git rev-parse HEAD`、`git status --short --branch` 与本地连接信息审计；预期报告列出实际提交、未使用线上资源的证据和不清楚项，不能确认隔离则 BLOCKED。

A2 / 1.2：建立仅测试夹具和记录，不改产品路径；对 30 次独立创建在提交后丢弃响应，记录每次数据库文档数、响应与 Agent 是否停止；另对同一请求重复提交和 2 个并发请求分别运行 30 组，单列实际结果，不把主动重放称自然重复率。验证：`mvn -f cloud/pom.xml -Dtest=AgentWikiIdempotencyBaselineLocalIT test`（预先配置专用隔离 MySQL、本机服务及测试样本）；预期报告各场景恰有 30 组、每组原始记录及实际文档数，无法执行则 BLOCKED，不以预期代替结果。

A3 / 1.3：用 UTF-8 写阶段报告 `docs/issue-reports/agent-wiki-write-idempotency/stage-1-baseline.md`，标明基线、样本摘要而非正文、环境、测试命令、故障注入点、原始记录路径、结果和限制；验证：检查报告与原始记录逐组一致，重放、自然流程分别统计，且无博客正文、凭据或个人信息；回滚为停用测试夹具、保留隔离数据待管理员处理。

依赖：1.1→1.2→1.3；优先交付不依赖数据库的可复核链路与夹具设计，无法满足真实故障注入或数据库隔离则对应项 BLOCKED，清楚分开，不运行未经隔离的测试。需记录实际命令、退出码、精确文件范围、假设、原因、清理交接和每任务状态；以 AGENTS_rules.md 格式逐任务交付，测试前停止写入。无需运行已知连接真实库的旧 AgentWikiCreationLocalIT；不能将 mock 作为真实持久化。Java/Maven 可使用 IDEA 自带 JBR 与 Maven，本机不存在则说明。现有经验：MySQL REPEATABLE READ 旧快照、19 位 ID、可能已提交但响应丢失、成员撤权复核。
