# Proposal

## Why

后续 BrowseComp-ZH 基础评测只评测本机 Ollama `qwen3:1.7b`，需要一个可查看、可切换、可持久化的模型档位入口；当前后端 LLM 完全由启动期环境变量固定，前端没有模型清单，也没有可被评测链读取的统一档位事实源。内置清单无法覆盖用户后续自行安装/接入的模型，因此还需要一个不写代码即可新增与删除档位的界面入口。

同时，本地密钥存放长期混乱：同一把 DashScope key 以三个名字存在三处 —— 根 `.env.dev` 的 `RAG_EMBEDDING_API_KEY`、`eval/.env` 的 `LLM_API_KEY` 与 `EMBEDDING_API_KEY`；此外系统用户级环境变量还单独提供了 `RAG_LLM_API_KEY`。云端则是 GitHub Actions Secrets → 服务器 `/opt/cloudwiki/.env`，变量名以入库的 `deploy/.env.example` 为登记表。用户明确提出「在前端会话界面存进去的 APIKEY 也能被记忆到 .env」，并拍板：**保留现有 `RAG_*` 变量名、密钥合并到一份项目根 `.env.local`、评测专属非密钥配置留在 `eval/.env`**。

## What Changes

- 新增前端模型档位清单常量：列出本机 Ollama 与云端 DeepSeek / DashScope 的对话（生成）模型，每项带 `baseUrl` / `model` / `provider` / `location`；embedding 与 rerank 模型单列且不可选。
- 在 AI 助手对话窗口的 `.input-bar` 底部工具条新增模型选择下拉：无边框紧凑文字触发器（模型名 + 本地/云端小字 + 下拉箭头），向上展开，分组展示，默认选中远程 `deepseek-flash`，选择持久化到 localStorage。
- 新增自定义模型档位的界面入口与「新增 / 管理模型」弹窗：可填写名称、模型 ID、Base URL、位置、提供商、备注与**密钥变量名**；新增后立即选中，已添加项可删除，删除当前选中项回落默认档位；内置档位不可编辑、不可删除。
- 档位中的凭据字段统一为 `apiKeyEnv`（**只存变量名引用，绝不存密钥值**）；内置云端档位按其实际凭据登记对应变量名。自定义清单按不可信输入逐项校验，并清洗历史版本可能残留的明文凭据字段。
- 构建期显隐规则：本地 dev 显示；标准生产构建 / preview 不渲染；本地 preview 可用未入库的 `cloud_front/.env.local` 显式开启。云端不渲染时输入栏工具条以 `display: contents` 回落，保证云端布局零可见变化。
- **新增本地密钥统一文件**：项目根 `.env.local`（已补 gitignore）只存放模型 API key，变量名与云端 Secrets / `deploy/.env.example` 对齐；`eval/.env` 只保留评测专属的 DB / URL / 模型名 / `EVAL_*` 凭据；删除旧 `.env.dev`（迁移前备份到工作区外）。
- **新增仅 dev 生效的写入端点**：浏览器无法写磁盘，由 Vite dev server 的 `configureServer` 插件把管理弹窗提交的明文写入根 `.env.local`；键名白名单取自 `deploy/.env.example`，校验值合法性与长度，覆盖既有键并删除重复行，原子写保留注释和其余键；`GET` 只返回「是否配置」布尔，不回传值。不再按来源、Origin 和自定义头验证；通过启动脚本启用临时隧道时不注册端点。生产构建不包含端点。
- **统一本地加载链**：`start-dev.ps1` 改读根 `.env.local`，只注入 `RAG_*` / `DEEPSEEK_*` 并跳过空值（避免覆盖系统用户级同名变量）；`lib_eval.load_env()` 改为 `os.environ` 优先 → 根 `.env.local` → `eval/.env`，并自动填充旧名 `LLM_API_KEY` / `EMBEDDING_API_KEY`，使全部历史脚本零改动；`smoke_test.py` 不再自带独立解析，复用同一函数。
- 开发启动脚本默认不创建临时公网隧道；需要时以 `-Tunnel` 显式开启，保留 `-NoTunnel` 并在同时指定时禁止开启。
- 同步更正现役注释：`application.yml` 的密钥注入说明、`docs/asset-registry.md` 的无版本保护资产登记、`eval/README.md` 的配置表。
- 明确边界：本次不改变后端按请求选择模型、不改任何生产请求契约；前端档位只存本地偏好，界面明示「后端尚未随按钮切换」。密钥写入仅本机开发期可用，云端 GitHub Secrets 是另一份物理存储，**只统一变量名、不统一密钥值**；写入后需重启后端与 Agent 才生效，界面明示。

## Capabilities

### New Capabilities

- `model-profile-selector`: 前端模型档位清单、分组展示、选择持久化、界面新增与删除自定义档位、对话输入栏内的紧凑呈现、云端隐身与布局回落，以及供后续评测链读取的档位与凭据引用契约。
- `local-env-secrets`: 本地模型密钥的单文件存放与统一变量名、仅开发期的写入端点、跨本地运行时（启动脚本 / Python 评测链）的一致加载顺序、越权与非法输入拒绝、云端隔离。

### Modified Capabilities

- `dev-public-tunnel`：临时公网隧道由默认开启改为显式 `-Tunnel` 开启，保留 `-NoTunnel` 并令其优先。

全局头部布局（`wiki-layout`）与 AI 助手（`ai-assistant-chat`）的既有需求不变；发送 / 停止按钮与输入框交互保持原样。`cloud` / `cloud_agent` 的 `${RAG_*}` 绑定、`deploy/`、CI workflow 与 `docker-compose.yml` 均不改动。

## Impact

L1 标准流程。前端侧：`cloud_front/` 新增 4 个文件（常量、store、组件、`vite/localEnvPlugin.ts`），修改 `vite.config.ts` 与 `AiAssistantPage.vue`，`GlobalHeader.vue` 无改动。本地链路侧：修改 `.gitignore`、`start-dev.ps1`、`eval/scripts/lib_eval.py`、`eval/scripts/smoke_test.py` 及三处文档注释；新增本地不入库的根 `.env.local`，精简本地不入库的 `eval/.env`，删除本地不入库的旧 `.env.dev`（迁移前已逐字节备份到工作区外 `/tmp/cloudwiki-env-backup-20261104/`）。

可明确排除生产接口、数据库、CI/CD、公开 API 与权限模型影响：新增端点由 `apply: 'serve'` 与 `configureServer` 限定在开发期，验收检查生产产物无端点字符串；云端构建不依赖任何未入库文件；密钥值只落在已 gitignore 的本机文件。回滚：恢复备份的 `.env.dev` 与 `eval/.env`、撤回上述源码与文档改动、删除新增文件与根 `.env.local`；无需回退数据库或部署配置。
