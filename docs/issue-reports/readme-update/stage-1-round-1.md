# 测试报告

Change ID／阶段／任务／轮次／时间：readme-update／1／1.1、1.2、1.3／第 1 轮／2026-11-03 17:55 +08:00  
测试实例：/root/test（独立测试角色，共享工作区）  
总体结论：PASS（六项固定静态验收）；不表示服务已启动或运行验证。

环境与基线：Windows PowerShell，本地工作区；分支 `feature/readme-update开发`，HEAD `bc93ed189fc98c3580a583b2d39f8f3635c6cf63`。基线未运行：本任务仅需静态检查，未切换或覆盖共享工作区。没有读取本地密钥、启动服务或使用测试数据。

被测范围与快照：测试前后 `Get-FileHash README.md -Algorithm SHA256` 均为 `dc1ead2866617277f610950793adbbad578b07f89906c089ec2bffd045682a0e`；冻结 snapshot 同值；补丁 SHA256 为 `b1fbf8ae1a8acb04c7c375a45d90e55824c8a15dfd1fc440ff311541b370de14`。`git diff --binary -- README.md | git hash-object --stdin` 与补丁文件 `git hash-object` 均为 `33e326e2d86a082ba26300552313fb60ab8523aa`。测试前后 `git diff --name-status bc93ed189fc98c3580a583b2d39f8f3635c6cf63` 均为 `M README.md`，无新增、删除或重命名实施文件。`git status --short --untracked-files=all` 显示另有任务工件 docs/handoff/readme-update 与 openspec/changes/readme-update（及本测试报告），非实施文件。

开发过程及修改事实：`docs/handoff/readme-update/stage-1-facts.md` 的 SHA256 `395579075e4f7d36966cfd970a07c382f3c353cf786ed84a79f0dc2bbc5b27e9` 与任务卡一致。它声称仅修改 README、更新架构启动协作、保留历史评测及命令、自检并停止写入、未启动服务。独立 diff 与内容核对支持文件范围及文档变更；摘要前后一致支持测试期间快照稳定，但不能独立证明开发者自检、停止写入的全过程或其先前未启动服务。测试自身未启动服务。

## 验收项

- 1.1 A1：PASS。命令 `rg --files cloud cloud_agent cloud_front`（退出 0，493 行），`rg -n 'cloud_agent|混合检索|语义重排' README.md`（退出 0），以及检索 `cloud/src/main/java/com/et/cloud/rag`、两个 pom 和前端 package.json。预期三目录存在、职责和检索描述可追溯；实际 README 第 23–28、47–49 行分别列出三个组件及职责；两个 pom 指明 Java 11、Spring Boot 2.7.6，前端 package.json 声明 Vue、TypeScript、Vite。`RagSearchServiceImpl.java` 第 97–115、152、188–220、237–269 行及 `LexicalIndex.java`、`RrfFusion.java` 支持权限过滤、文号、词法和融合、可用重排及回退。初次使用 Windows 不支持的通配路径 rg 返回错误，随后改为目录加 `-g '*.java'` 成功核对。
- 1.1 A2：PASS。命令 `rg -n '规划中|已实现|路线图|检索层优化|政策解读|评测|基线' README.md`（退出 0），对照 `eval/README.md` 第 226–256 行。预期状态无矛盾、数字有来源与基线；实际 README 第 10 行限定检索实现与效果待评估，政策解读为应用方向，无冲突路线图；第 32–42 行列明历史结果文件、数据集和配置，0.7930、0.9223、0.9048、0.6262、泄漏 0 与 423／399 题均对应评测说明。未重跑评测。
- 1.2 A1：PASS。执行任务卡指定两个 `rg` 命令（均退出 0）。预期启停顺序、三个组件、端口、隧道和日志与脚本一致；实际 README 第 70、79–85 行与 `start-dev.ps1` 第 47–52、146–147、203–265、328–391 行一致：3307／6379 为监听检查，8123／8124／3000 为组件端口，默认隧道及 NoTunnel、tmp 日志均有对应。属于静态核验。
- 1.2 A2：PASS。对照两个 pom、前端 package.json、启动脚本，`git ls-files` 确认两个 application.yml 及 deploy/.env.example 均已跟踪；README 第 64–72、90–115 行所述依赖、命令、配置来源与文件一致。`rg -n 'ALIYUN_AI_API_KEY' README.md` 退出 1，无匹配；人工审阅 README 未发现真实密钥值。没有读取 .env.dev 或本地密钥内容。
- 1.3 A1：PASS。执行 `rg -n 'AGENTS.md|README-issue-tracker.md|SQLite|IssueLog.xlsx|openspec/' README.md` 及对规则和说明的 `rg -n 'SQLite|状态' AGENTS.md scripts/README-issue-tracker.md`（均退出 0）。README 第 119 行指向三个入口、说明 SQLite 为问题状态来源；未出现 IssueLog.xlsx，符合 AGENTS.md 第 15 行。
- 1.3 A2：PASS。执行任务卡完整 Python 链接和严格 UTF-8 检查，退出 0，输出 `missing: []`。`git diff --name-only -- README.md` 输出 README.md；已审阅 `git diff -- README.md` 及完整 untracked 状态，实施变更仅 README，其他文件为交接和 OpenSpec 工件。测试前后摘要一致。

固定验收缺陷：无。  
清单外交付阻断问题：无。  
未验证项与原因：运行时启动、外部服务、实际评测与开发者历史操作均不在静态验收范围；上述事实记录的过程性声明无法由本次独立确认。  
变异检验：未做；任务卡未指定。  
问题记录：无。  
临时文件、数据及服务：未创建临时目录、测试数据或启动服务；本报告为允许范围内的持久证据。  
风格意见：无。
