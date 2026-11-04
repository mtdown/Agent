# 验证任务卡
## acceptance
请读取 AGENTS_test.md。Change ID：agent-wiki-write-idempotency；阶段 1；任务 1.1、1.2、1.3；第 1 轮。
工作区与前提：分支 feature/agent-write-idempotency开发，基线 f5ecb69；专用容器、schema、离线 runner、样本与复现命令见指定阶段记录。仅本地隔离运行，不调用外部服务或现有业务数据库。
被测快照：stage-1-manifest.json 列出两个新增测试源码、报告、原始记录、开发任务卡的 SHA-256；测试前后逐项核验，清单新增源码以外无产品实现改动。完整新增源码差异见 stage-1-full.patch，SHA256 cdd4f97cf759bbc633fd155d681b42ccc74b2c37285f36249e2cec17c1790b02。
测试可写范围：新建本阶段独立测试报告和唯一编号原始证据、专用容器中的隔离测试数据和日志；按规则更新问题库。不得修改快照文件、清理资源或访问线上及业务库。报告切勿记录正文、凭据、个人信息。
开发过程及修改事实记录：docs/issue-reports/agent-wiki-write-idempotency/stage-1-baseline.md，及对应原始 CSV；内容校验值在 stage-1-manifest.json。逐项核对 SHA-256 并验证记录。
问题记录目录：docs/issue-reports/agent-wiki-write-idempotency/。

A1 / 1.1：只读核对当前创建链路、旧客户端请求和结果、已有停止规则，记录代码基线提交及本地 MySQL/Redis 地址和隔离证明；验证：`git rev-parse HEAD`、`git status --short --branch` 与本地连接信息审计；预期报告列出实际提交、未使用线上资源的证据和不清楚项，不能确认隔离则 BLOCKED。

A2 / 1.2：建立仅测试夹具和记录，不改产品路径；对 30 次独立创建在提交后丢弃响应，记录每次数据库文档数、响应与 Agent 是否停止；另对同一请求重复提交和 2 个并发请求分别运行 30 组，单列实际结果，不把主动重放称自然重复率。验证：`mvn -f cloud/pom.xml -Dtest=AgentWikiIdempotencyBaselineLocalIT test`（预先配置专用隔离 MySQL、本机服务及测试样本）；预期报告各场景恰有 30 组、每组原始记录及实际文档数，无法执行则 BLOCKED，不以预期代替结果。

A3 / 1.3：用 UTF-8 写阶段报告 `docs/issue-reports/agent-wiki-write-idempotency/stage-1-baseline.md`，标明基线、样本摘要而非正文、环境、测试命令、故障注入点、原始记录路径、结果和限制；验证：检查报告与原始记录逐组一致，重放、自然流程分别统计，且无博客正文、凭据或个人信息；回滚为停用测试夹具、保留隔离数据待管理员处理。

## diff
完整可读取补丁：docs/handoff/agent-wiki-write-idempotency/stage-1-full.patch，SHA256 cdd4f97cf759bbc633fd155d681b42ccc74b2c37285f36249e2cec17c1790b02。
