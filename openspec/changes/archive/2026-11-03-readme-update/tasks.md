# Tasks

## 1. README 现状校准（阶段 1，按 1.1 → 1.2 → 1.3 执行）

允许实施文件：根 `README.md`，预计 1 个；不得改代码、脚本、配置、数据库、API、权限或其他项目文件。此阶段无需启停服务；以源码及脚本作静态核验，测试不得将静态核验称为启动实测。任务 1.3 依赖前两项。设计工件按 L0 省略：本次仅编辑文档，不涉及设计指令列出的架构变更、依赖、数据模型、安全、性能、迁移或需事先决定的歧义；CLI 仍可能将 design 显示为 ready、tasks 显示为 blocked。

- [x] 1.1 更新项目介绍、技术架构、目录和路线状态；对照仓库路径、配置及代码检查下列验收。

  A1. README 的项目结构包含真实存在的 `cloud/`、`cloud_agent/`、`cloud_front/`，说明三个组件的职责；技术架构涉及检索时描述与当前实现一致。verify：`rg --files cloud cloud_agent cloud_front`，`rg -n 'cloud_agent|混合检索|语义重排' README.md` 并对照对应源码及构建配置。预期：三目录存在且描述可追溯；不再只列出两组件。  
  A2. 同一功能在路线图和正文不得同时写成规划中与已实现；缺少证据的完成结论不标为已完成，评测数字保留时须标明来源及适用基线。verify：`rg -n '规划中|已实现|路线图|检索层优化|政策解读|评测|基线' README.md`，逐项对照 `eval/README.md` 和仓库证据。预期：无互相矛盾的状态，保留的数字有可追溯来源，无法核实者不作为当前事实。

- [x] 1.2 更新环境、配置与启动说明；对照启停脚本和项目配置检查下列验收。

  A1. README 写明根目录依次执行 `stop-dev.ps1` 和 `start-dev.ps1`，描述脚本启动三个组件、端口 3000/8123/8124、本地 MySQL 3307 与 Redis 6379；说明 `-NoTunnel`、默认隧道及日志 `tmp/`。verify：`rg -n 'backendPort|agentPort|frontPort|3307|6379|NoTunnel|tmpDir' start-dev.ps1` 与 `rg -n 'stop-dev|start-dev|8124|3307|6379|NoTunnel|tmp/' README.md`。预期：数值和动作与脚本一致，不再将默认本地 MySQL 端口写成 3306。  
  A2. 启动前提和配置以已跟踪示例、构建文件和脚本为依据；不披露密钥值，也不将废弃 `ALIYUN_AI_API_KEY` 功能列为默认需求。verify：对照 `cloud/pom.xml`、`cloud_agent/pom.xml`、`cloud_front/package.json`、`start-dev.ps1` 和仓库中已跟踪的配置示例，执行 `rg -n 'ALIYUN_AI_API_KEY' README.md`。预期：命令、依赖与来源一致；该命令无匹配（退出码 1），文档无真实密钥。

- [x] 1.3 更新开发流程与文档索引并检查全文；对照规则、问题跟踪说明及链接核验下列验收。

  A1. README 指向 `AGENTS.md`、`scripts/README-issue-tracker.md` 和 OpenSpec 目录，说明问题当前状态使用本机 SQLite 的 issue tracker；不将 `IssueLog.xlsx` 说成当前任务记录入口。verify：`rg -n 'AGENTS.md|README-issue-tracker.md|SQLite|IssueLog.xlsx|openspec/' README.md`，并与 `AGENTS.md`、`scripts/README-issue-tracker.md` 对照。预期：入口与规则一致且不出现过时入口描述。  
  A2. README 中所有仓库相对 Markdown 链接目标存在，UTF-8 可严格解码且差异仅限 README。verify：运行 `python -c "import pathlib,re,urllib.parse; p=pathlib.Path('README.md'); s=p.read_text(encoding='utf-8'); links=re.findall(r'(?<!!)\[[^\]]+\]\(([^)]+)\)',s); bad=[x for x in links if not x.startswith(('http://','https://','#','mailto:')) and not (p.parent / urllib.parse.unquote(x.split('#')[0])).exists()]; print('missing:', bad); raise SystemExit(bool(bad))"`；对照阶段基线执行 `git diff --name-only -- README.md` 和 `git diff -- README.md`，另检查 `git status --short` 中本任务项目实施文件。预期：Python 退出 0 且输出 `missing: []`；仅 README 为实施变更，无意外文件修改。

### 验证与回滚

开发者逐项记录实际命令、退出码、源码依据和结果；独立测试者对冻结快照逐项复核，测试期间停止写入。固定验收的核对不需要读取 `.env.dev` 密钥值，也无需启动服务。问题编号及证据在测试后按项目规则补充，问题当前状态只由 issue tracker 维护。

若需回滚，确认 README 不含其他人的工作后，从本任务的基线提交恢复根 README（例如 `git restore --source=<基线提交> -- README.md`）；保留规划与测试证据。对未提交文件执行恢复前先核对差异和归属。

阶段 1 判定（第 1 轮）：1.1 A1 PASS、A2 PASS；1.2 A1 PASS、A2 PASS；1.3 A1 PASS、A2 PASS。独立静态验收证据：`docs/issue-reports/readme-update/stage-1-round-1.md`。未启动服务或重跑评测。

## 2. 恢复展示格式并补充已实现工作（阶段 2，按 2.1 → 2.2 → 2.3 执行）

基线：`bc93ed189fc98c3580a583b2d39f8f3635c6cf63`；唯一实施文件 `README.md`，预计 1 个。依次完成三个任务后冻结新快照并独立测试；阶段 1 的 PASS 及报告只适用于当时快照，不能作为阶段 2 的 PASS。以下静态验证不代表启动实测或评测重跑。不修改代码、脚本、配置、数据库、API、权限或其他项目实施文件；不重启废弃功能。

- [x] 2.1 恢复基线展示结构和必要内容；逐节对照基线差异并核验保留情况。

  A1. README 保留基线居中项目标题、技术徽章，以及 `项目简介`、`演进路线`、`功能特性`、`RAG 检索与评测`、`技术亮点`、`技术架构`、`项目结构`、`快速开始`、`访问地址`、`默认端口`、`常用命令`、`开发规范`、`路线图`等基线一级展示章节，按基线顺序组织；若基线实际标题不同，以 `git show` 输出为准逐项核对。verify：`git show bc93ed189fc98c3580a583b2d39f8f3635c6cf63:README.md`；`rg -n '^## |<div align="center">|img.shields.io' README.md`。预期：上述结构及对应基线各级展示内容仍可找到，无整节或大量必要介绍被删除；差异中每处删除均有直接过时或冲突的证据及纠正说明。
  A2. 原有评测表、分组数据、技术亮点和可复现说明继续展示；历史数字注明可追溯来源和适用范围，不伪称当前部署效果。verify：`git show bc93ed189fc98c3580a583b2d39f8f3635c6cf63:README.md`；`rg -n '0\.793|0\.922|0\.905|0\.626|分组|eval/README.md|历史|基线' README.md`；对照 `eval/README.md` 和 `eval/results/baseline-20260916-201335-http.json`。预期：四项基线指标及分组展示可找到，数值、数据集及历史限制可追溯；不把历史结果当新一轮实测。

- [x] 2.2 基于已合入提交和当前实现增补新能力；逐项核对代码及对外展示措辞。

  A1. README 在原格式中介绍 `cloud_agent/` 独立服务、流式 Agent 对话与工具调用、Wiki 目标选择与确认后写入、Skill／代码库和联网搜索／抓取；联网能力标明需配置及限制，代码发布不等于执行。verify：`git log -25 --oneline -- cloud_agent cloud_front/src cloud/src/main`；`rg -n 'cloud_agent|Agent|Skill|代码库|联网|搜索|抓取|确认' README.md`；对照 `cloud_agent/src/main/java/com/et/agent/AgentController.java`、`AgentLoop.java`、`AgentToolRegistry.java`、`cloud_agent/README-web-research.md` 和 `cloud_front/src/pages/aiAssistant/AgentLibraryPanel.vue`。预期：上述能力均有源码依据，涉及启用条件、确认步骤和未验证状态的描述与源码一致，不宣称无证据的效果。
  A2. 对照当前脚本修正直接过时的三组件、启动和端口信息，延续阶段 1 的固定验收；不添加真实密钥或默认重启废弃功能。verify：`rg -n 'backendPort|agentPort|frontPort|3307|6379|NoTunnel|tmpDir' start-dev.ps1`；`rg -n 'stop-dev|start-dev|cloud_agent|8124|3307|6379|NoTunnel|tmp/|ALIYUN_AI_API_KEY' README.md` 并逐项对照阶段 1 的 1.2 A1、A2。预期：三组件、端口和启停描述与脚本一致；README 不含 `ALIYUN_AI_API_KEY` 和真实密钥。

- [x] 2.3 核验全文展示一致性、链接及范围；逐项记录差异依据。

  A1. 对照阶段 1 的全部固定验收和基线 README，逐项检查同一能力状态、展示内容、历史评测与新能力；保留原验证证据，不将旧 PASS 迁移到新快照。verify：`git diff bc93ed189fc98c3580a583b2d39f8f3635c6cf63 -- README.md`；`rg -n '规划中|已实现|路线图|检索层优化|政策解读|SQLite|IssueLog.xlsx|README-issue-tracker.md|openspec/' README.md`；逐项运行阶段 1 的验证命令并对照源码。预期：阶段 1 的各项固定条件在新快照单独复验；同一功能无互相矛盾的状态，旧入口不再作为当前入口；每处基线删除均记录来源和直接过时或冲突原因，无无法解释的大幅删减。
  A2. 相对 Markdown 链接有效、UTF-8 严格解码，实施差异仅限根 README。verify：运行阶段 1 的 1.3 A2 原 Python 链接检查命令；`git diff --name-only bc93ed189fc98c3580a583b2d39f8f3635c6cf63 -- README.md`；`git status --short` 并核对新建及修改文件归属。预期：链接检查退出 0 且 `missing: []`；仅 README 属于本阶段实施文件，其他任务或用户文件不混入。

阶段 2 开发记录每项命令、退出码、来源和删除／纠正理由，交由独立测试者对冻结快照逐项复验。问题编号及报告按项目规则记录，当前状态以 issue tracker 为准。回滚前核对 README 归属及差异；需要时从基线恢复该文件，保留所有规划及测试证据。

阶段 2 第 1 轮：2.1 A1 FAIL、2.1 A2 PASS；2.2 A1 PASS、A2 PASS；2.3 A1 FAIL、A2 PASS。问题 29；证据：`docs/issue-reports/readme-update/stage-2-round-1.md`。按固定验收修复并复测，不降低原条件。

阶段 2 第 2 轮判定：2.1 A1 PASS、A2 PASS；2.2 A1 PASS、A2 PASS；2.3 A1 PASS、A2 PASS，阶段 1 固定项复验 PASS。问题 29 已关闭；证据：`docs/issue-reports/readme-update/stage-2-round-2.md`。未启动服务或重跑评测。

### 归档判定（2026-11-03）

本次为 L0 纯文档变更。项目共同规则明确免 design.md；`openspec instructions design --change readme-update --json` 将设计稿限定于跨模块架构、依赖／数据模型、安全／性能／迁移或需要预先技术决策的歧义等条件，本次均不适用。归档要求的必需工件是 proposal 和 tasks，二者均已完成；CLI 1.13.2 仍显示 design 为 `ready`，不是 `skipped`，也不代表已生成设计稿。`skip_specs: true` 仅跳过规格增量，状态输出中规格 `existingOutputPaths` 为空，无需同步。六项任务完成，阶段 2 第 2 轮独立静态验收通过；未启动服务或重跑评测。
