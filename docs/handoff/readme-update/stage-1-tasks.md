# 阶段 1 任务卡

# Tasks

## 1. README 现状校准（单阶段，按 1.1 → 1.2 → 1.3 执行）

允许实施文件：根 `README.md`，预计 1 个；不得改代码、脚本、配置、数据库、API、权限或其他项目文件。此阶段无需启停服务；以源码及脚本作静态核验，测试不得将静态核验称为启动实测。任务 1.3 依赖前两项。设计工件按 L0 省略：本次仅编辑文档，不涉及设计指令列出的架构变更、依赖、数据模型、安全、性能、迁移或需事先决定的歧义；CLI 仍可能将 design 显示为 ready、tasks 显示为 blocked。

- [ ] 1.1 更新项目介绍、技术架构、目录和路线状态；对照仓库路径、配置及代码检查下列验收。

  A1. README 的项目结构包含真实存在的 `cloud/`、`cloud_agent/`、`cloud_front/`，说明三个组件的职责；技术架构涉及检索时描述与当前实现一致。verify：`rg --files cloud cloud_agent cloud_front`，`rg -n 'cloud_agent|混合检索|语义重排' README.md` 并对照对应源码及构建配置。预期：三目录存在且描述可追溯；不再只列出两组件。  
  A2. 同一功能在路线图和正文不得同时写成规划中与已实现；缺少证据的完成结论不标为已完成，评测数字保留时须标明来源及适用基线。verify：`rg -n '规划中|已实现|路线图|检索层优化|政策解读|评测|基线' README.md`，逐项对照 `eval/README.md` 和仓库证据。预期：无互相矛盾的状态，保留的数字有可追溯来源，无法核实者不作为当前事实。

- [ ] 1.2 更新环境、配置与启动说明；对照启停脚本和项目配置检查下列验收。

  A1. README 写明根目录依次执行 `stop-dev.ps1` 和 `start-dev.ps1`，描述脚本启动三个组件、端口 3000/8123/8124、本地 MySQL 3307 与 Redis 6379；说明 `-NoTunnel`、默认隧道及日志 `tmp/`。verify：`rg -n 'backendPort|agentPort|frontPort|3307|6379|NoTunnel|tmpDir' start-dev.ps1` 与 `rg -n 'stop-dev|start-dev|8124|3307|6379|NoTunnel|tmp/' README.md`。预期：数值和动作与脚本一致，不再将默认本地 MySQL 端口写成 3306。  
  A2. 启动前提和配置以已跟踪示例、构建文件和脚本为依据；不披露密钥值，也不将废弃 `ALIYUN_AI_API_KEY` 功能列为默认需求。verify：对照 `cloud/pom.xml`、`cloud_agent/pom.xml`、`cloud_front/package.json`、`start-dev.ps1` 和仓库中已跟踪的配置示例，执行 `rg -n 'ALIYUN_AI_API_KEY' README.md`。预期：命令、依赖与来源一致；该命令无匹配（退出码 1），文档无真实密钥。

- [ ] 1.3 更新开发流程与文档索引并检查全文；对照规则、问题跟踪说明及链接核验下列验收。

  A1. README 指向 `AGENTS.md`、`scripts/README-issue-tracker.md` 和 OpenSpec 目录，说明问题当前状态使用本机 SQLite 的 issue tracker；不将 `IssueLog.xlsx` 说成当前任务记录入口。verify：`rg -n 'AGENTS.md|README-issue-tracker.md|SQLite|IssueLog.xlsx|openspec/' README.md`，并与 `AGENTS.md`、`scripts/README-issue-tracker.md` 对照。预期：入口与规则一致且不出现过时入口描述。  
  A2. README 中所有仓库相对 Markdown 链接目标存在，UTF-8 可严格解码且差异仅限 README。verify：运行 `python -c "import pathlib,re,urllib.parse; p=pathlib.Path('README.md'); s=p.read_text(encoding='utf-8'); links=re.findall(r'(?<!!)\[[^\]]+\]\(([^)]+)\)',s); bad=[x for x in links if not x.startswith(('http://','https://','#','mailto:')) and not (p.parent / urllib.parse.unquote(x.split('#')[0])).exists()]; print('missing:', bad); raise SystemExit(bool(bad))"`；对照阶段基线执行 `git diff --name-only -- README.md` 和 `git diff -- README.md`，另检查 `git status --short` 中本任务项目实施文件。预期：Python 退出 0 且输出 `missing: []`；仅 README 为实施变更，无意外文件修改。

### 验证与回滚

开发者逐项记录实际命令、退出码、源码依据和结果；独立测试者对冻结快照逐项复核，测试期间停止写入。固定验收的核对不需要读取 `.env.dev` 密钥值，也无需启动服务。问题编号及证据在测试后按项目规则补充，问题当前状态只由 issue tracker 维护。

若需回滚，确认 README 不含其他人的工作后，从本任务的基线提交恢复根 README（例如 `git restore --source=<基线提交> -- README.md`）；保留规划与测试证据。对未提交文件执行恢复前先核对差异和归属。
