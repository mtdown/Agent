# 测试报告

Change readme-update／阶段 2／任务 2.1、2.2、2.3／第 1 轮／2026-11-03（UTC+8）；测试实例 `/root/test_stage2`。
总体结论：FAIL（2.1 A1、2.3 A1）；其余结果见下。静态验收，未启动服务、未重跑评测，基线未运行。

环境与快照：Windows PowerShell，分支 `feature/readme-update开发`，HEAD 和基线均为 `bc93ed189fc98c3580a583b2d39f8f3635c6cf63`。`git diff --name-status <基线>` 仅 `M README.md`；`git status --short` 为 README 修改和 `docs/handoff/`、`docs/issue-reports/readme-update/`、`openspec/changes/readme-update/` 未跟踪，分别为交接、报告及规划材料；未发现其他实施文件。测试前后 `Get-FileHash README.md -Algorithm SHA256` 均为 `30dabc620302088ec52cc98ffe9273119b60238970af1eaea3b9ec47b8a87401`；交接快照同值。补丁 SHA256 为 `c7a6bfb734ea19ff1fabd53c0bb24afb700d068e0c28bcdb0865e4eb8c51a23a`，事实记录为 `dcd861cdc0163192504760e74faea7f94b607fac01b545d7e6de6a01daa473f4`。`git diff --no-ext-diff <基线> -- README.md | git hash-object --stdin` 与补丁 `git hash-object` 均为 `38c9089f4aa0f1851f5b4f7dc0e72611025706ce`。无新增、删除、重命名实施文件。

事实记录核验：`docs/handoff/readme-update/stage-2-facts.md` 摘要与卡一致，内容仅称基线、唯一文件、恢复结构和校正过时信息、静态自检与停止写入；基线、范围及差异可由 Git 核对，停止写入和未启动服务属于过程声明，不能由该记录独立证明。记录未列每项命令、退出码、来源或逐处删除理由。测试前后摘要一致，但不能证明期间绝对无瞬时写入。

## 阶段 2 固定验收

- 2.1 A1：**FAIL**。执行 `git show bc93ed189fc98c3580a583b2d39f8f3635c6cf63:README.md`、`rg -n '^## |<div align="center">|img.shields.io' README.md`（均有输出，退出 0），逐段检查 `stage-2.patch`。预期基线章节、内容保留且每处删除有直接过时或冲突依据及纠正说明。实际居中标题、10 个徽章和 12 个二级展示章节按基线顺序保留；快速开始的独立启动后端 `mvn spring-boot:run` 与前端 `npm run dev` 小节及 Node.js 18+ 明确最低版本被移除，事实记录没有逐项依据；`cloud_front/package.json` 仍定义 dev，`start-dev.ps1:265` 仍调用它。未发现直接证据说明这些示例已经过时或与新增一键启动冲突。故该固定条件不满足。见问题 29。
- 2.1 A2：**PASS**。执行基线 `git show`、`rg -n '0\.793|0\.922|0\.905|0\.626|分组|eval/README.md|历史|基线' README.md`，对照 `eval/README.md:231-270` 与 `eval/results/baseline-20260916-201335-http.json:23-141`，均可读取、检索退出 0。预期四指标、分组、来源和历史限制可追溯；实际总数 423、计分 399，四项原值 0.793046、0.922306、0.904762、0.62616 与展示舍入一致，X/A/S/B 分组与源一致；README 95、138 行注明历史配置和非当前实测，技术亮点及复现链接保留。
- 2.2 A1：**PASS（静态）**。执行 `git log -25 --oneline -- cloud_agent cloud_front/src cloud/src/main`、`rg -n 'cloud_agent|Agent|Skill|代码库|联网|搜索|抓取|确认' README.md`，并对照任务卡列出的 Controller、Loop、Registry、联网说明及前端面板，退出 0。预期新增能力和限制有源码依据；实际 `AgentLoop.java:8,113-115,138`、`AgentController.java:68-76,103`、`AgentToolRegistry.java:91-110,158-160` 支持流式调用和目标确认，前端面板 7、45-51、68、93、233-240 行支持版本、启停、发布及待验证；`cloud_agent/README-web-research.md` 支持独立密钥、发送内容、三次含重试及费用限制。未实测行为。
- 2.2 A2：**PASS（静态）**。执行 `rg -n 'backendPort|agentPort|frontPort|3307|6379|NoTunnel|tmpDir' start-dev.ps1` 和 `rg -n 'stop-dev|start-dev|cloud_agent|8124|3307|6379|NoTunnel|tmp/|ALIYUN_AI_API_KEY' README.md`（均退出 0），另执行 `rg -n 'ALIYUN_AI_API_KEY' README.md`（退出 1，无匹配）。预期端口、启停、日志和依赖与脚本一致，无真实密钥。实际脚本 47-52、146-147、203-265、386-391 行与 README 228-253、265-273 行一致；检查 README 可见文本无真实密钥。
- 2.3 A1：**FAIL**。执行 `git diff bc93ed189fc98c3580a583b2d39f8f3635c6cf63 -- README.md` 和 `rg -n '规划中|已实现|路线图|检索层优化|政策解读|SQLite|IssueLog.xlsx|README-issue-tracker.md|openspec/' README.md`（退出 0），阶段 1 各项另见下。预期所有删除均记录直接依据、无无法解释的大幅删减；实际启动示例删除未有直接依据，事实记录未列任何逐项删除说明，见问题 29。其他状态、历史评测、新能力和旧入口核验如各项所记。
- 2.3 A2：**PASS**。运行阶段 1 的原 Python 命令（下文逐字列出），退出 0 输出 `missing: []`；执行 `git diff --name-only bc93ed189fc98c3580a583b2d39f8f3635c6cf63 -- README.md` 输出 README.md，`git status --short` 如快照段。预期 UTF-8 严格解码、相对链接存在及仅 README 为实施文件；实际符合。

## 阶段 1 在新快照复验

- 1.1 A1：**PASS（静态）**。`rg --files cloud cloud_agent cloud_front` 管道计数 493 行；`rg -n 'cloud_agent|混合检索|语义重排' README.md` 退出 0。预期三个真实目录及检索描述有依据；实际 README 163-193 行列出三组件，`cloud/src/main/java/com/et/cloud/rag/LexicalIndex.java`、`RagProperties.java`、`RagRerankClient.java` 有 BM25、重排配置和失败回退依据，两份 pom 均为 Spring Boot/Java 11。
- 1.1 A2：**PASS（静态）**。`rg -n '规划中|已实现|路线图|检索层优化|政策解读|评测|基线' README.md` 退出 0，检查 `eval/README.md` 与上述源码。预期状态不矛盾及历史数字可追溯；实际混合检索与可配置重排标已实现，效果继续评测，政策对话仍为目标；数字和范围见 2.1 A2。
- 1.2 A1：**PASS（静态）**。运行 `rg -n 'backendPort|agentPort|frontPort|3307|6379|NoTunnel|tmpDir' start-dev.ps1` 和 `rg -n 'stop-dev|start-dev|8124|3307|6379|NoTunnel|tmp/' README.md`，退出 0。预期依次 stop/start、三组件 3000/8123/8124、检查 MySQL 3307 和 Redis 6379、默认隧道、`-NoTunnel` 和 `tmp/`；实际匹配脚本，见 2.2 A2。
- 1.2 A2：**PASS（静态）**。对照 `cloud/pom.xml`、`cloud_agent/pom.xml`、`cloud_front/package.json`、`start-dev.ps1` 和已跟踪的 `application.yml` 配置入口；`rg -n 'ALIYUN_AI_API_KEY' README.md` 退出 1。预期命令及依赖有来源、不含废弃密钥及真实值；实际 Java 11、Maven、npm、脚本及配置链接与上述文件一致，未发现真实密钥，未读取本地密钥。
- 1.3 A1：**PASS**。`rg -n 'AGENTS.md|README-issue-tracker.md|SQLite|IssueLog.xlsx|openspec/' README.md` 退出 0；对照 `AGENTS.md` 和 `scripts/README-issue-tracker.md`。预期 SQLite issue tracker 为当前入口；实际 README 300 行提供三入口并说明 SQLite，未出现 IssueLog.xlsx。
- 1.3 A2：**PASS**。原命令：

```powershell
python -c "import pathlib,re,urllib.parse; p=pathlib.Path('README.md'); s=p.read_text(encoding='utf-8'); links=re.findall(r'(?<!!)\[[^\]]+\]\(([^)]+)\)',s); bad=[x for x in links if not x.startswith(('http://','https://','#','mailto:')) and not (p.parent / urllib.parse.unquote(x.split('#')[0])).exists()]; print('missing:', bad); raise SystemExit(bool(bad))"
```

退出 0、`missing: []`。`git diff --name-only -- README.md` 输出 README.md；`git diff -- README.md` 与指定补丁通过 Git blob 哈希一致，状态见快照段。预期 UTF-8、链接及唯一实施文件均符合；实际符合。

## 基线删除逐组审查

完整逐行差异为 `docs/handoff/readme-update/stage-2.patch`。旧项目简介后端职责改为知识库后端并增加 Agent：新服务代码和构建存在；路线图原规划/已完成相互冲突：源码支持混合与重排、效果未重跑；固定重排步骤改为配置/回退：`RagProperties` 和 `RagRerankClient` 支持；“当前基线”改“历史”：基线 JSON 文件标 2026-09-16 且本轮未重测；架构检索描述替换：`LexicalIndex` 与配置支持混合；IssueLog 目录与开发规范替换：`AGENTS.md`、`scripts/README-issue-tracker.md` 指定 SQLite；本地默认 MySQL 3306 改脚本检查 3307：`start-dev.ps1:146`；新增 Agent 后调整常用命令相对目录：原后端命令后当前目录为 cloud，`cd ../cloud_agent` 和再 `cd ../cloud_front` 路径成立。访问 API 加 `/api`：可从后端配置的 context path 核对。原手动启动步骤、明确 Node.js 18+ 及原步骤标题被移除并换成一键启动：脚本支持新流程，但不足以直接证明旧命令和最低版本过时或冲突，且事实记录未给出逐项理由；一键启动旧自动清理/关闭行为由顺序 stop/start 和说明取代，但记录未逐项说明直接依据。所有组的开发事实记录均未逐条列出来源、命令、退出码和纠正理由，故不能视为逐项记录完成。

固定验收缺陷：问题 29，2.1 A1、2.3 A1；复现为比较基线和补丁及阅读事实记录。预期每处删除有直接过时或冲突证据和理由；实际手动启动示例及版本条件无此证据，事实记录仅总述。影响外部读者失去基线启动选择，也无法审查删减是否必要。无清单外阻断问题。

未验证项与原因：未执行运行时启动、联网、评测，任务卡限定静态验收；未读取本地密钥。变异检验未做，任务卡未要求。无新增临时目录、服务或测试数据，未执行清理；报告及问题库记录为任务授权产物。风格意见无。
