# Tasks

固定前提：开发者读取 `AGENTS_rules.md`，每轮独立测试者读取 `AGENTS_test.md`；每阶段先由总管发阶段简报并冻结任务卡与快照，测试期间开发者停止写被测文件。所有测试服务绑定本机、数据库为专用隔离 schema 且明确核验连接信息，使用合成用户和本地博客非敏感样本，不连接现有业务库或线上服务。禁止脚本 DROP schema、删除测试临时目录或业务数据；完整绝对路径与仅针对本任务目录的清理命令交管理员并记“已交管理员处理”。本机数据库不可用或隔离性无法证明时对应测试 BLOCKED，不以模拟结果替代。命令从项目根运行，指定的测试类由所属阶段提供；测试必须含断言并保存原始记录，命令退出 0 不代替行为证据。问题编号由 issue tracker 实际产生后附对应项，当前状态只在 SQLite。

## 1. 基线与可复现实验

- [x] 1.1 只读核对当前创建链路、旧客户端请求和结果、已有停止规则，记录代码基线提交及本地 MySQL/Redis 地址和隔离证明；验证：`git rev-parse HEAD`、`git status --short --branch` 与本地连接信息审计；预期报告列出实际提交、未使用线上资源的证据和不清楚项，不能确认隔离则 BLOCKED。
- [x] 1.2 建立仅测试夹具和记录，不改产品路径；对 30 次独立创建在提交后丢弃响应，记录每次数据库文档数、响应与 Agent 是否停止；另对同一请求重复提交和 2 个并发请求分别运行 30 组，单列实际结果，不把主动重放称自然重复率。验证：`mvn -f cloud/pom.xml -Dtest=AgentWikiIdempotencyBaselineLocalIT test`（预先配置专用隔离 MySQL、本机服务及测试样本）；预期报告各场景恰有 30 组、每组原始记录及实际文档数，无法执行则 BLOCKED，不以预期代替结果。
- [x] 1.3 用 UTF-8 写阶段报告 `docs/issue-reports/agent-wiki-write-idempotency/stage-1-baseline.md`，标明基线、样本摘要而非正文、环境、测试命令、故障注入点、原始记录路径、结果和限制；验证：检查报告与原始记录逐组一致，重放、自然流程分别统计，且无博客正文、凭据或个人信息；回滚为停用测试夹具、保留隔离数据待管理员处理。

阶段 1 独立验收：A1/A2/A3 均 PASS，证据见 `docs/issue-reports/agent-wiki-write-idempotency/stage-1-round-1.md`；原有本地配置疑似凭据暴露风险 #31 待处理，不作为测试通过依据。

## 2. 单篇文档幂等写入

- [ ] 2.1 增加可检测的建表迁移和唯一约束，基于用户及服务端生成 ID 保存版本化请求摘要与结果，正文不入操作表；验证：`mvn -f cloud/pom.xml -Dtest=AgentWikiIdempotencyMigrationLocalIT test`（专用隔离 MySQL）；预期同一用户同一 ID 第二条记录被唯一约束拒绝、不同用户或不同 ID 可存储，迁移再次执行不破坏既有数据，表不含正文。
- [ ] 2.2 实现单篇创建原子事务、重复请求复用结果和内容冲突拒绝，旧请求不传 ID 保持原结果字段及原语义；验证：`mvn -f cloud/pom.xml -Dtest=AgentWikiIdempotencyCreationLocalIT test`；预期同 ID 相同内容顺序重放及 30 组每组 2 并发调用均只增加 1 个文档且成功结果 ID 相同，同 ID 改正文或目标被拒绝且新增 0 文档；注入保存前及记录前异常均无文档及成功记录；旧请求和不同 ID 各自仍可创建。
- [ ] 2.3 服务端生成、绑定并传递可信操作 ID，禁止模型参数提供 ID；记录本阶段结果与兼容部署顺序。验证：`mvn -f cloud_agent/pom.xml -Dtest=AgentWikiIdempotencyIdentityTest test` 及 `mvn -f cloud/pom.xml -Dtest=AgentWikiIdempotencyCreationLocalIT test`；预期同一次调用的受控重传 ID 一致、不同工具调用 ID 不同、模型传入 ID 被拒绝或忽略且不能改变服务端 ID，旧请求不报新必填错误；报告包含实际运行证据。回滚：停用 ID 发送、退回兼容应用，保留表及文档。

## 3. 查询与安全恢复

- [ ] 3.1 增加内部状态查询及受信调用身份校验，返回成功或不可确认，不暴露原文；验证：`mvn -f cloud/pom.xml -Dtest=AgentWikiIdempotencyStatusLocalIT test`；预期同用户当前有权限可查回原 ID；其他用户、撤权、文档删除或移动导致无访问权限时不返回成功及目标详情，缺失和未提交不宣称失败，未认证调用被拒绝，查询新增文档数 0。
- [ ] 3.2 Agent 在不确定响应后最多查询 3 次，确认成功则复用原结果，否则保留 unknown、阻止本轮后续写入；验证：`mvn -f cloud_agent/pom.xml -Dtest=AgentWikiIdempotencyRecoveryTest test`；预期模拟提交后断连可恢复同一文档结果且写请求数为 1；查询缺失、超时、旧服务不支持时查询数不超过 3、写请求数为 1、后续写工具执行数为 0、结果为 unknown。
- [ ] 3.3 验证可见结果及历史投影兼容，并记录本阶段证据；验证：`mvn -f cloud/pom.xml -Dtest=AgentWikiUnknownProjectionTest,AgentWikiChoiceLocalIT test` 与 `mvn -f cloud_agent/pom.xml -Dtest=AgentWikiIdempotencyRecoveryTest test`（仅在核实相关测试安全使用专用隔离库后运行；不安全则使用本阶段新建的隔离等价测试并记录原测试 BLOCKED）；预期确认成功展示既有结构化结果、不向可见文本输出原始 ID；无法确认仍为 unknown，原有部分成功不变。回滚为禁用恢复并维持旧 unknown 阻断，不删除操作记录。

## 4. 综合验证与成果记录

- [ ] 4.1 在与阶段 1 相同的本机隔离环境、样本及注入点重复 30 次提交后丢响应；验证：`mvn -f cloud/pom.xml -Dtest=AgentWikiIdempotencyEndToEndLocalIT test`；预期每次真实提交后仅 1 篇文档，统计并逐次记录状态恢复成功、unknown、重复文档数及总数；与阶段 1 对照仅比较相同定义和分母，不承诺预设提升率。
- [ ] 4.2 覆盖 30 组并发、相同 ID 不同请求、事务回滚、权限撤销、无认证、服务重启后查询及旧客户端；验证：`mvn -f cloud/pom.xml -Dtest=AgentWikiIdempotencyEndToEndLocalIT,AgentWikiIdempotencyStatusLocalIT test` 和 `mvn -f cloud_agent/pom.xml -Dtest=AgentWikiIdempotencyRecoveryTest,AgentWikiIdempotencyIdentityTest test`；预期并发每组不超过 1 个文档，冲突/无权限不增加文档且无详情泄露，回滚零文档零成功记录，重启保留已提交结果，旧客户端仍按原语义运行；每项单独保存断言证据。
- [ ] 4.3 汇总四阶段实际报告、原始数据、问题编号、权限及数据影响、回滚步骤和未验证限制，给出仅基于实测分母的简历候选；验证：逐项对照报告与原始数据、快照哈希、独立测试结果，检查没有宣称整体成功率或外部副作用 exactly-once；`openspec validate agent-wiki-write-idempotency --strict` 退出 0。回滚不清除文档、操作记录或测试目录，清理交管理员。
