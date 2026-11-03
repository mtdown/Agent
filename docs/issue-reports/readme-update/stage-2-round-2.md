# 测试报告

Change ID／阶段／任务／轮次／时间：readme-update／2／2.1、2.2、2.3／第 2 轮／2026-11-03 19:21 CST
测试实例标识：/root/test_stage2_r2（独立测试实例）
总体结论：PASS（静态验收；未启动服务或重跑评测）
环境与基线：Windows PowerShell、Python，分支 feature/readme-update开发，HEAD 和基线均为 bc93ed189fc98c3580a583b2d39f8f3635c6cf63。未运行基线服务；使用 git show 和 git diff 静态比较。未读取本地密钥。

被测范围与快照：唯一实施变更 README.md，无新增、删除或重命名实施文件。测试前后 `Get-FileHash README.md, docs/handoff/readme-update/stage-2-round-2-README.snapshot, docs/handoff/readme-update/stage-2-round-2.patch, docs/handoff/readme-update/stage-2-round-2-facts.md -Algorithm SHA256`：README 和 snapshot 均为 ef7c16bd2cc99578e68d9cc1f5390ffe7f344bb4c209a3930d6a2f7442ef3d4d；patch 为 5620b2c574f3a8dcc814a60f26e5f56cf91bf0021243467325aedb37f2232e75；facts 为 67b612707766dcbec24fd35280218858631eff759491c84300d95969ece4d5fc。Python subprocess 获取 `git diff bc93ed189fc98c3580a583b2d39f8f3635c6cf63 -- README.md` 字节与 patch 比较，结果 True。测试期间未观察到被测文件变化。

开发过程及修改事实记录核验：`docs/handoff/readme-update/stage-2-round-2-facts.md` 摘要如上；范围仅 README，十组删改理由与 diff、源码及脚本对应。独立检查发现手动后端 `cd cloud; mvn spring-boot:run`、前端 `cd cloud_front; npm install; npm run dev` 和 Node.js 18+ 均保留；新增 Agent 启动前提。事实记录中的自检不能代替本报告检查。比较十组：简介补充 Agent 职责；原演进与路线图的检索/政策状态不一致，改为区分已实现与待评测；重排按 RagProperties 与 RagSearchServiceImpl 配置和回退；评测依据历史 JSON 与 eval 文档；检索架构补 BM25/RRF；旧问题表入口依 AGENTS.md 与问题跟踪说明替换；环境及独立命令恢复；一键启停依固定验收和脚本调整；API context-path 及 MySQL 脚本检查端口依配置和脚本；常用命令新增 Agent 后相对目录随当前目录调整。逐组对应变更，不存在未解释的整节删除。

验收项结果：

- 2.1 A1：PASS。命令 `git show bc93ed189fc98c3580a583b2d39f8f3635c6cf63:README.md`、`git diff bc93ed189fc98c3580a583b2d39f8f3635c6cf63 -- README.md`、`rg -n '^## |<div align="center">|img.shields.io' README.md`，退出 0。预期基线标题、徽章与各展示章节按原顺序保留，删除有依据；实际 13 个基线二级章节均按顺序存在，居中标题和 10 个徽章存在；逐组删改及依据见上，独立启动示例和 Node 18+ 已恢复。
- 2.1 A2：PASS。命令 `rg -n '0\.793|0\.922|0\.905|0\.626|分组|eval/README.md|历史|基线' README.md`，对照 `eval/README.md` 和 `eval/results/baseline-20260916-201335-http.json`，退出 0。预期四指标、分组与复现及范围可追溯；实际四指标 0.793/0.922/0.905/0.626 对应 JSON 精度取三位，423 总题、399 召回计分一致；分组表和技术亮点保留，标注历史配置和非当前部署效果，链接评测复现说明。未重跑。
- 2.2 A1：PASS。命令 `git log -25 --oneline -- cloud_agent cloud_front/src cloud/src/main` 与 `rg -n 'cloud_agent|Agent|Skill|代码库|联网|搜索|抓取|确认' README.md`，退出 0；对照 `AgentController.java`、`AgentLoop.java`、`AgentToolRegistry.java`、`cloud_agent/README-web-research.md`、`AgentLibraryPanel.vue`。预期 Agent 流式、工具、Wiki 选择确认、Skill/代码库、联网条件及限制有依据；实际控制器与循环、Wiki 工具及选择约束、前端发布和 UNVERIFIED 状态、网页说明中独立密钥、搜索词/URL 上送、上下文、各三次含重试与费用均对应 README。代码保存发布不等于执行，未宣称实测效果。
- 2.2 A2：PASS。命令 `rg -n 'backendPort|agentPort|frontPort|3307|6379|NoTunnel|tmpDir' start-dev.ps1` 及 `rg -n 'stop-dev|start-dev|cloud_agent|8124|3307|6379|NoTunnel|tmp/|ALIYUN_AI_API_KEY' README.md` 均退出 0；另独立 `rg -n 'ALIYUN_AI_API_KEY' README.md` 退出 1（无匹配）。预期与脚本一致且无废弃变量或真实密钥；实际三服务 8123/8124/3000、MySQL 3307 和 Redis 6379 检查、默认隧道、NoTunnel、日志与先停止后启动均对应；未发现密钥值。静态对照，未运行服务。
- 2.3 A1：PASS。命令 `git diff bc93ed189fc98c3580a583b2d39f8f3635c6cf63 -- README.md` 与 `rg -n '规划中|已实现|路线图|检索层优化|政策解读|SQLite|IssueLog.xlsx|README-issue-tracker.md|openspec/' README.md` 退出 0；阶段 1 全部固定项在此快照重新核验：
  - 1.1 A1 PASS：`rg --files cloud cloud_agent cloud_front` 退出 0、共 493 行；`rg -n 'cloud_agent|混合检索|语义重排' README.md` 退出 0。三组件存在；`RagSearchServiceImpl.java` 的 LexicalIndex、RrfFusion、rerankOrFallback 及 RagProperties 支持描述。
  - 1.1 A2 PASS：`rg -n '规划中|已实现|路线图|检索层优化|政策解读|评测|基线' README.md` 退出 0；与 eval 文档和源码对照，路线与正文区分现有功能和效果待评测，历史数字标明范围。
  - 1.2 A1 PASS：执行上列脚本与 README 端口和启停检索，实际符合三服务、依赖检查、隧道与日志要求。
  - 1.2 A2 PASS：对照 cloud 和 cloud_agent 的 pom、cloud_front/package.json、start-dev.ps1、已跟踪 application.yml 与 deploy/.env.example；Java 11、Maven、前端 npm 命令及配置来源对应；废弃变量检索退出 1，未发现真实密钥。
  - 1.3 A1 PASS：`rg -n 'AGENTS.md|README-issue-tracker.md|SQLite|IssueLog.xlsx|openspec/' README.md` 退出 0；与 AGENTS.md 和 scripts/README-issue-tracker.md 对照，SQLite 为新问题当前状态入口，未把旧表作为当前入口。
  - 1.3 A2 PASS：执行下列原 Python 命令，严格 UTF-8 解码成功，输出 `missing: []`、退出 0；diff 和状态仅 README 为实施变更。
- 2.3 A2：PASS。原命令 `python -c "import pathlib,re,urllib.parse; p=pathlib.Path('README.md'); s=p.read_text(encoding='utf-8'); links=re.findall(r'(?<!!)\[[^\]]+\]\(([^)]+)\)',s); bad=[x for x in links if not x.startswith(('http://','https://','#','mailto:')) and not (p.parent / urllib.parse.unquote(x.split('#')[0])).exists()]; print('missing:', bad); raise SystemExit(bool(bad))"` 输出 `missing: []`，退出 0；`git diff --name-only bc93ed189fc98c3580a583b2d39f8f3635c6cf63 -- README.md` 输出 README.md；`git status --short` 显示 README 修改及 docs/handoff、docs/issue-reports/readme-update、openspec/changes/readme-update 未跟踪，后三者属交接、报告和规划，不是实施文件；`git diff --check` 退出 0。

固定验收缺陷：无。问题 29 对应独立启动和删除理由复测通过。
清单外交付阻断问题：无。
未验证项与原因：未启动服务、未进行网络访问及评测重跑；任务卡明确为静态验收。
变异检验：未做，任务卡未指定。
问题记录：29；本报告为复测证据，原始失败证据 `docs/issue-reports/readme-update/stage-2-round-1.md`。
临时文件、数据及服务清理状态：未创建临时目录或测试数据，未启停服务。
风格意见：无。
