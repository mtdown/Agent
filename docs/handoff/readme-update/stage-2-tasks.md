# 阶段 2 任务卡

## 2. 恢复展示格式并补充已实现工作（阶段 2，按 2.1 → 2.2 → 2.3 执行）

基线：`bc93ed189fc98c3580a583b2d39f8f3635c6cf63`；唯一实施文件 `README.md`，预计 1 个。依次完成三个任务后冻结新快照并独立测试；阶段 1 的 PASS 及报告只适用于当时快照，不能作为阶段 2 的 PASS。以下静态验证不代表启动实测或评测重跑。不修改代码、脚本、配置、数据库、API、权限或其他项目实施文件；不重启废弃功能。

- [ ] 2.1 恢复基线展示结构和必要内容；逐节对照基线差异并核验保留情况。

  A1. README 保留基线居中项目标题、技术徽章，以及 `项目简介`、`演进路线`、`功能特性`、`RAG 检索与评测`、`技术亮点`、`技术架构`、`项目结构`、`快速开始`、`访问地址`、`默认端口`、`常用命令`、`开发规范`、`路线图`等基线一级展示章节，按基线顺序组织；若基线实际标题不同，以 `git show` 输出为准逐项核对。verify：`git show bc93ed189fc98c3580a583b2d39f8f3635c6cf63:README.md`；`rg -n '^## |<div align="center">|img.shields.io' README.md`。预期：上述结构及对应基线各级展示内容仍可找到，无整节或大量必要介绍被删除；差异中每处删除均有直接过时或冲突的证据及纠正说明。
  A2. 原有评测表、分组数据、技术亮点和可复现说明继续展示；历史数字注明可追溯来源和适用范围，不伪称当前部署效果。verify：`git show bc93ed189fc98c3580a583b2d39f8f3635c6cf63:README.md`；`rg -n '0\.793|0\.922|0\.905|0\.626|分组|eval/README.md|历史|基线' README.md`；对照 `eval/README.md` 和 `eval/results/baseline-20260916-201335-http.json`。预期：四项基线指标及分组展示可找到，数值、数据集及历史限制可追溯；不把历史结果当新一轮实测。

- [ ] 2.2 基于已合入提交和当前实现增补新能力；逐项核对代码及对外展示措辞。

  A1. README 在原格式中介绍 `cloud_agent/` 独立服务、流式 Agent 对话与工具调用、Wiki 目标选择与确认后写入、Skill／代码库和联网搜索／抓取；联网能力标明需配置及限制，代码发布不等于执行。verify：`git log -25 --oneline -- cloud_agent cloud_front/src cloud/src/main`；`rg -n 'cloud_agent|Agent|Skill|代码库|联网|搜索|抓取|确认' README.md`；对照 `cloud_agent/src/main/java/com/et/agent/AgentController.java`、`AgentLoop.java`、`AgentToolRegistry.java`、`cloud_agent/README-web-research.md` 和 `cloud_front/src/pages/aiAssistant/AgentLibraryPanel.vue`。预期：上述能力均有源码依据，涉及启用条件、确认步骤和未验证状态的描述与源码一致，不宣称无证据的效果。
  A2. 对照当前脚本修正直接过时的三组件、启动和端口信息，延续阶段 1 的固定验收；不添加真实密钥或默认重启废弃功能。verify：`rg -n 'backendPort|agentPort|frontPort|3307|6379|NoTunnel|tmpDir' start-dev.ps1`；`rg -n 'stop-dev|start-dev|cloud_agent|8124|3307|6379|NoTunnel|tmp/|ALIYUN_AI_API_KEY' README.md` 并逐项对照阶段 1 的 1.2 A1、A2。预期：三组件、端口和启停描述与脚本一致；README 不含 `ALIYUN_AI_API_KEY` 和真实密钥。

- [ ] 2.3 核验全文展示一致性、链接及范围；逐项记录差异依据。

  A1. 对照阶段 1 的全部固定验收和基线 README，逐项检查同一能力状态、展示内容、历史评测与新能力；保留原验证证据，不将旧 PASS 迁移到新快照。verify：`git diff bc93ed189fc98c3580a583b2d39f8f3635c6cf63 -- README.md`；`rg -n '规划中|已实现|路线图|检索层优化|政策解读|SQLite|IssueLog.xlsx|README-issue-tracker.md|openspec/' README.md`；逐项运行阶段 1 的验证命令并对照源码。预期：阶段 1 的各项固定条件在新快照单独复验；同一功能无互相矛盾的状态，旧入口不再作为当前入口；每处基线删除均记录来源和直接过时或冲突原因，无无法解释的大幅删减。
  A2. 相对 Markdown 链接有效、UTF-8 严格解码，实施差异仅限根 README。verify：运行阶段 1 的 1.3 A2 原 Python 链接检查命令；`git diff --name-only bc93ed189fc98c3580a583b2d39f8f3635c6cf63 -- README.md`；`git status --short` 并核对新建及修改文件归属。预期：链接检查退出 0 且 `missing: []`；仅 README 属于本阶段实施文件，其他任务或用户文件不混入。

阶段 2 开发记录每项命令、退出码、来源和删除／纠正理由，交由独立测试者对冻结快照逐项复验。问题编号及报告按项目规则记录，当前状态以 issue tracker 为准。回滚前核对 README 归属及差异；需要时从基线恢复该文件，保留所有规划及测试证据。
