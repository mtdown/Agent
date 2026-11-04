# 阶段 1 第 1 轮独立测试报告

Change ID：agent-wiki-write-idempotency；阶段 1；任务 1.1–1.3；2026-11-04（Asia/Shanghai）。测试实例：`/root/stage1_independent_test`，独立分派，共享工作区。固定验收 A1–A3 均 PASS；总体：存在清单外安全风险 #31，交总管判断交付阻断及处理。未声称生产端到端结果。

环境及快照：分支 `feature/agent-write-idempotency开发`，HEAD `f5ecb69bc2ccc292c44fa7ea1ee372abaa34f612`。`git status --short --branch` 显示测试源码、交接材料、报告和 OpenSpec 为未跟踪，无已跟踪文件差异；测试时没有切换基线。测试前后以 `Get-FileHash -Algorithm SHA256` 逐项校验 `docs/handoff/agent-wiki-write-idempotency/stage-1-manifest.json` 所列六个文件，全部匹配；完整补丁测试前后均为 `cdd4f97cf759bbc633fd155d681b42ccc74b2c37285f36249e2cec17c1790b02`。`git apply --reverse --check` 默认因空白差异失败，加 `--ignore-space-change --ignore-whitespace` 后退出 0；六项原文件字节哈希均匹配清单。清单包含两份新增测试源码、开发报告、两份原始 CSV、任务卡；本测试新增两份 CSV 和本报告。

开发记录核验：`stage-1-baseline.md` 及两份原始 CSV 的哈希均匹配；报告所述链路和注入代码经只读检查。对原始 90 行与本轮新增 90 行分别按合成空间和标题在 MySQL 执行只读分组计数对照，180 组、0 不符；两次各 150 篇。原始及新 Agent CSV 各 30 行、组号无重复，均 cloud_success_response=1、forwarded_requests=1、status=unknown、second_write_blocked=1。计数、场景及报告一致。逐组原始数据见下列文件：

- 开发记录：`awib-b8a8d5c04a7e417d8e3ec946cb6c2b65-agent.csv`、`awib-b8a8d5c04a7e417d8e3ec946cb6c2b65-db.csv`。
- 本轮：`awib-95c7879dcab04d2bb56d8f4ec2252b7c-agent.csv`、`awib-95c7879dcab04d2bb56d8f4ec2252b7c-db.csv`，SHA-256 分别为 `fd070365e125cb29d1329f44b043bb8f7d7c7ef13b48cbfc9e57b3a564272d76`、`23df513cd74d12789202f486ce7e19c4089ff7fbd161c14dc3f1de24aa6eced4`。内容相同因 CSV 不含运行标识。

A1 / 1.1：PASS。`git rev-parse HEAD`、`git status --short --branch`、`docker inspect`、`docker network inspect` 和专用 MySQL 只读身份查询。内网 `Internal=true`，网络唯一成员为专用 MySQL，runner 共享其网络命名空间；MySQL 仅宿主 `127.0.0.1:13317`，容器内部 `127.0.0.1:3306`；UUID、schema、owner marker 与开发记录一致。数据库回环探针成功，指定外部测试地址探针失败；该单点探针不证明所有出口不可达。博客和 Maven 缓存挂载只读。Redis 使用替身，无实际 Redis 连接；本地配置的 Redis 地址为 `127.0.0.1:6379`，默认配置可引用远端，但本夹具不加载 Redis。旧网关发送 spaceId、folderId、title、content，返回原结构；真实 AgentLoop 对 unknown 阻断同轮后续写入。未访问现有业务库或外部服务。已核样本大小 9161 和 SHA-256 `847e0bc96f9ce6025abce5f0b6a8dfb8ce1c4df005dbd9c30f38b6faf69f6a8a`，未记录正文。

A2 / 1.2：PASS。使用开发报告“隐私与复现”中完整 PowerShell 命令及隔离变量，runner 的 `/workspace` 执行 `mvn -f cloud/pom.xml -Dtest=AgentWikiIdempotencyBaselineLocalIT test`，退出 0；runner 私有日志 `/tmp/awib-081fb6d4ab9f/retest-061a6ac252ff428fa940846a9442a634.log`。本轮 30 个提交后响应丢弃组均各 1 篇并标记 unknown、阻断后续写入；30 组主动顺序重放和 30 组主动并发均各 2 篇。逐组独立 SQL 查询方式：按空间名与 `SUBSTRING_INDEX(title,'（',1)` 分组，和 CSV 的 scenario/group/document_count 对照。故障注入在真实创建服务事务成功结果返回测试适配器之后，由回环代理读取成功响应并丢弃响应体、向真实网关返回空 HTTP 200；不是 TCP 断线。使用真实 MySQL、MyBatis、创建服务、AgentLoop 和网关；模型、权限、缓存、位置列表和 HTTP 控制器是替身，索引监听未加载。重放及并发为主动实验，不能推断自然重复发生率。

A3 / 1.3：PASS，适用以下明确限制。报告为 UTF-8，包含基线、样本摘要、环境、命令、注入点、数据、分别统计、局限和不清理的回滚安排。通过 UTF-8 解码、逐组数据和数据库对照；对报告及四个 CSV 检查样本长行和邮箱模式均无命中，报告中凭据赋值模式的命中为复现命令变量引用，当前数据库密码值不在报告内。本轮私有日志及两份 Surefire XML 检查当前数据库密码和样本第一条长行均无命中；此有限模式检查不能证明没有全部正文或个人信息。开发记录披露第四次私有日志含样本摘录；本轮没有读取或输出该正文，须作为敏感测试数据保留并交管理员处理。

固定验收缺陷：无。清单外安全风险：#31，本轮审计时过宽的只读 `rg` 上下文在工具输出中显示了未被 Git 跟踪的 `cloud/src/main/resources/application-local.yml` 第 97–98 行疑似对象存储凭据，未连接服务、未确认有效性，不在本报告记录值；请求总管按安全流程处理工具输出与相关材料。问题 #30 的隔离 MySQL 前置条件已实测满足，按本轮结果更新。

未验证：真实网络断线、真实模型、生产 HTTP/鉴权/授权/缓存/索引、线上环境及所有可能出口；样本敏感性无法靠有限模式扫描完全证明。未做变异检验：任务卡未授权。

临时资源：未删除任何资源。专用 MySQL 容器、runner、内网、卷及 runner 内 `/tmp/awib-081fb6d4ab9f`（包含私有日志），以及测试生成的 Maven target，实际仍保留。开发报告已向总管交付容器、网络、卷的精确名称和命令；本轮新增日志位于该已有目录内，已交总管转管理员处理，实际删除状态未知。限定命令仍为依次 `docker rm -f agent-wiki-baseline-runner-081fb6d4ab9f`、`docker rm -f agent-wiki-baseline-081fb6d4ab9f`、`docker network rm agent-wiki-baseline-net-081fb6d4ab9f`、`docker volume rm agent-wiki-baseline-data-081fb6d4ab9f`，未执行；禁止对其他目录使用递归清理。风格意见：无。