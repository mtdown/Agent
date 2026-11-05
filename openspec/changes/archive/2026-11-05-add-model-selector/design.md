# Design

## Context

`cloud` / `cloud_agent` 的 LLM 由启动期环境变量 `RAG_LLM_BASE_URL` / `RAG_LLM_API_KEY` / `RAG_LLM_FAST_MODEL` / `RAG_LLM_MODEL` 固定，`application.yml` 注释给出本地 Ollama 切换示例；云端部署由 CI 构建前端静态产物并通过 Nginx 提供服务，本地 Vite 开发服务监听 `0.0.0.0` 且 `allowedHosts: true`。`AiAssistantPage.vue` 的 `.input-bar` 是单行 flex，项目已全局 `app.use(Antd)`。本机 Ollama 已安装 `qwen3:1.7b` 及两个 embedding 模型。

密钥现状经取证：根 `.env.dev` 只有 `RAG_EMBEDDING_API_KEY` / `DEEPSEEK_WEB_API_KEY`；`eval/.env` 有 14 个键，其中 `LLM_API_KEY` / `EMBEDDING_API_KEY` 与前者实测同值（同一把 DashScope key 的三份副本）；系统用户级环境变量另有 `RAG_LLM_API_KEY`。`start-dev.ps1` 的 `Import-DevEnvFile` 逐行 `Set-Item Env:<KEY>` 注入进程环境；`lib_eval.load_env()` 只读 `eval/.env` 到 dict，`smoke_test.py` 又自带一份独立解析；`prepare_keys.py` 会往 `eval/.env` 写回 `EVAL_*_API_KEY`。云端由 GitHub Actions Secrets 生成服务器 `.env`，变量名登记表为入库的 `deploy/.env.example`。

## Goals / Non-Goals

**Goals:**

- 给本机一个可见、可切换、可持久化的模型档位入口，并让后续评测链有唯一读取口。
- 用户不写代码即可新增、查看、删除自定义模型档位，且不影响内置档位。
- 选择器贴合对话窗视觉语言：紧凑文字触发器，位于提问动作附近，不占用全局导航。
- 云端部署零可见变化、零接口变化；显隐开关不能被误判为恒 false 的假阳性。
- 模型 API key 在本地只存一份、只用一个名字；前端界面填写的 key 能被可靠「记忆」到本地文件，并被后端启动脚本与 Python 评测链读到。
- 明文密钥不进入浏览器存储、不进入前端产物、不进入版本库；写入能力在云端构建中完全不存在。

**Non-Goals:**

- 不实现运行时按请求切换后端模型（属改请求契约，需另行 L2 change）。
- 不搭建 BrowseComp-ZH 评测链、不写判分、不跑模型、不做微调。
- 不评测云端模型效果；云端模型仅列出以便核对现状。
- 不实现自定义档位导出为评测侧配置文件（用户选择「界面里直接新增/删除」；档位仍只存浏览器）。
- 不把本地 `.env.local` 与云端 GitHub Secrets 打通 —— 两者只统一变量名，值各自独立。
- 不引入新依赖；不改发送按钮、输入框与流式交互逻辑；不改 CI / compose / 生产接口。

## Decisions

1. **显隐用双条件 `import.meta.env.DEV || import.meta.env.VITE_ENABLE_MODEL_SELECTOR === 'true'`，而非单一 `DEV`。**
   理由：云端使用正式构建，本地 preview 演示同样 `DEV=false`；只看 `DEV` 会误隐藏。构建期静态折叠，云端无该变量时为 false。开启方式写入 `cloud_front/.env.local`（`*.local` 已忽略），云端构建拿不到。

2. **模型清单作为单一事实源，每项自带 `baseUrl` + `model`。**
   理由：后续评测 runner 按档位调用；两处各维护一份表会出现「前端切了、评测没切」的假对照。

3. **embedding / rerank 模型列出但禁用。**
   理由：不能替换问答链路 LLM，误选会造成零切片或重排报错；列出便于核对现状。

4. **状态用 pinia + localStorage，选中档位 key 为 `cloudwiki.selected-model-profile`。**
   理由：与项目 store 风格一致；刷新保持；读取校验 id 有效性，无效回落默认。

5. **默认档位 = 远程 `deepseek-flash`（`cloud-deepseek-flash`）。** 保留已有有效选择；无有效选择及删除当前自定义档位时回落该默认值。本地 `qwen3:1.7b` 仍为可选的基础评测目标。

6. **挂载在 AI 助手对话窗口 `.input-bar` 底部工具条，而非全局头部。** 档位是关于「这一轮对话用什么模型」的上下文。全局头部最终不改动。

7. **工具条用 `display: contents` 作为云端回落，保证布局零变化。** 仅 `with-model-selector` class 存在时切换为两行。

8. **触发器用无边框原生 button + `placement="topLeft"` 向上展开。** 贴合对话窗紧凑文字形态，避免视口底部裁切。

9. **界面明示「后端尚未随按钮切换」。** 最重要的防误导边界。

10. **自定义档位存独立 localStorage key `cloudwiki.custom-model-profiles`。** 两份数据生命周期与校验规则不同；代价是 Python 评测链读不到，界面双处明示。

11. **自定义清单按不可信输入逐项校验，删除当前选中项时回落默认。** `isUsableCustomProfile` 校验字段完整性、`http(s)` 协议、`custom-` 前缀、枚举值；非数组或 JSON 损坏整体回落空列表。

12. **新增与删除集中在同一个管理弹窗。** 避免菜单项内联删除与 `a-menu-item` 选中事件冲突。

13. **档位只存 `apiKeyEnv` 变量名引用，删除 `apiKeyMode` / `apiKeyRef` 及 literal 明文模式。**
    理由：浏览器 localStorage 可被 XSS、扩展、本机其他用户、浏览器同步读走；且 Python 评测链读不到浏览器存储，存明文既不解决统一问题又新增泄露面。内置云端档位按实际凭据登记 `RAG_LLM_API_KEY` / `RAG_EMBEDDING_API_KEY`，本地 Ollama 无字段。`isUsableCustomProfile` 只接受白名单变量名，拒绝明文；store 读取时清洗历史 `apiKeyMode` / `apiKeyRef` 并回写，确保浏览器里不残留任何密钥值。

14. **密钥合并范围 = 只合并模型 API key，评测专属配置留 `eval/.env`。**
    理由：`eval/.env` 14 个键里只有 5 个是凭据类，其余是 DB / URL / 模型名；`prepare_keys.py` 还会写回 `EVAL_*_API_KEY` 到该文件。全量合并会让 `DB_*` / `EVAL_*` 被注入后端进程环境 —— 项目已被 `SERVER__PORT` 被 Spring relaxed binding 误认成 `server.port` 坑过。因此 `.env.local` 只放 `RAG_LLM_API_KEY` / `RAG_EMBEDDING_API_KEY` / `DEEPSEEK_WEB_API_KEY`（以 `deploy/.env.example` 为白名单），`eval/.env` 删除两份重复 key 后保留 12 个评测专属键，`prepare_keys.py` 无需改动。

15. **「前端写入 .env」通过 Vite dev server 代写，而不是浏览器直接写文件。**
    理由：浏览器 JS 没有任意路径磁盘权限，File System Access API 需每次用户手势、仅 Chromium、拿不到固定路径。Vite dev server 是 Node 进程且前端本就与它同源通信，用 `configureServer` 挂一个 `POST /__cw/local-env` 即可。`LOCAL_ENV_ENDPOINT` 写成 `import.meta.env.DEV ? '/__cw/local-env' : null`，生产构建折叠为 `null`，端点字符串一并从产物消失（实测三种构建均 0 命中）。

16. **按负责人决定移除来源校验，脚本启用临时隧道时禁用端点。** 启动脚本将当前隧道选择传给前端开发进程；启用 `-Tunnel` 且未指定 `-NoTunnel` 时插件不注册密钥端点，其他情况注册。直接运行 Vite 或使用其他隧道不受该脚本保护；默认服务监听局域网，能够访问开发服务的设备可调用端点。保留键名白名单和空值、换行、长度校验；原子覆盖同名键、清除重复项并保留其他配置，响应不回传明文。

17. **`start-dev.ps1` 加注入白名单与空值跳过。**
    理由：`.env.local` 只注入 `^(RAG_|DEEPSEEK_)[A-Z0-9_]+$`，保留原有 `RAG_*` 覆盖能力，同时排除会被 relaxed binding 误认的键。关键回归：系统用户级环境变量已提供 `RAG_LLM_API_KEY`（实测 35 位），若把文件中留空的同名键注入进程环境，会把系统值覆盖成空并导致 `/ask` 报「LLM 未配置」；因此 `.env.local` 不登记空键，脚本也跳过空值。

18. **`lib_eval.load_env()` 统一优先级 `os.environ` > 根 `.env.local` > `eval/.env`，并填充旧名别名。**
    理由：`lib_eval.py:103` 用 `cfg["LLM_API_KEY"]` 直接索引、20+ 脚本调用 `load_env()`，逐个改调用点风险大且易漂移。由 `load_env()` 从 `RAG_EMBEDDING_API_KEY` 自动 `setdefault` 出 `LLM_API_KEY` / `EMBEDDING_API_KEY`，历史脚本零改动；`smoke_test.py` 删掉自带的独立解析改为复用同一函数，避免合并后静默读不到密钥。别名用 `setdefault`，旧配置里若仍有旧名则旧名优先（向后兼容）。

19. **先补 gitignore，再写密钥文件。** 实测 `git check-ignore -v .env.local` 在改动前返回 exit 1 —— 根 `.gitignore` 只有 `.env.dev` / `eval/.env`，`*.local` 规则在 `cloud_front/.gitignore` 里只覆盖前端子目录。若不先补规则，一次 `git add -A` 就会把新密钥文件推上去。

## Risks / Trade-offs

启动脚本默认关闭 cloudflared 临时隧道，`-Tunnel` 显式开启；`-NoTunnel` 保留兼容并优先于 `-Tunnel`。Windows 独立 Cloudflared 服务不由脚本控制。本次不改变生产部署。

- [按钮被误认为已切换后端模型] → title 与菜单底部固定提示；本次仍未改变请求契约。
- [自定义档位被误认为评测链可用] → 菜单与弹窗双处明示；档位仍只存浏览器。
- [显隐开关恒 false 假阳性] → 三态变异检验：无变量 `Kt=!1` → 注入变量 `Kt=!0` → 恢复 `Kt=!1`（压缩变量名历次为 `LWe` / `At` / `Rt` / `Kt`）。
- [端点残留在云端产物] → `LOCAL_ENV_ENDPOINT` 在生产构建中折叠为 `null`；验收检查生产产物不包含端点字符串。
- [局域网或独立隧道访问开发端点] → 不再进行来源验证；启动脚本显式启用临时隧道时不注册端点。其他隧道及能连接开发服务的局域网设备不在该限制内。键名与值校验、原子写仍保留。
- [系统用户级同名变量被空值覆盖] → 见决策 17；`.env.local` 不登记空键，脚本跳过空值。
- [历史评测脚本读不到迁移后的密钥] → 旧名别名 + `smoke_test.py` 复用；实测 `load_env()` 17 个键齐全、别名同源、评测配置仍在、`os.environ` 覆盖生效。
- [根 `.env.local` 误入库] → 已补 `.gitignore:47` 并用 `git check-ignore` 复核命中。
- [写入后误以为立即生效] → 界面明示「重启后端与 Agent 后生效」；`start-dev.ps1` 只在启动期注入，Python 评测链每次重跑重读无此问题。
- [状态显示误导] → 密钥区「已配置/未配置」注明只反映 `.env.local` 文件本身，系统环境变量同名键仍会生效。
- [删除 `.env.dev` 断链] → 迁移前逐字节备份到工作区外；`eval/tmp/rerank-probe.py` 等临时脚本仍引用旧路径，属可接受的本地临时资产失效，已在交付说明中列明。

## Migration Plan

1. 迁移前把 `.env.dev`、`eval/.env`、`start-dev.ps1`、`lib_eval.py` 等九个将改动文件逐字节备份到工作区外 `/tmp/cloudwiki-env-backup-20261104/`。
2. 先补根 `.gitignore` 的 `.env.local`，再由脚本校验三份同源后生成根 `.env.local`；精简 `eval/.env` 的两行重复 key；删除旧 `.env.dev`。
3. 改加载链与写入端点，跑类型检查、构建、端到端验证；真实 `.env.local` 在写入验证中用占位串，验证结束逐字节恢复。
4. 云端零迁移：CI、compose、`application.yml` 的 `${...}` 绑定与服务器 `.env` 均不改动，变量名本来就一致。
5. 回滚：从备份恢复 `.env.dev` / `eval/.env`，撤回源码与文档改动，删除 `.env.local` 与新增前端文件；无需回退数据库或部署配置。
