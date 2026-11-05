# Tasks

固定前提：本 change 属 L1 标准流程，任务分支 `feature/model-selector开发` 从 `origin/main` 派生。前三轮只改 `cloud_front/`；本轮按用户拍板扩展到本地密钥统一链路（`.gitignore` / 根 `.env.local` / `eval/.env` / `start-dev.ps1` / `eval/scripts/` / `vite.config.ts` / 三处文档注释），仍可排除生产接口、数据库、CI/CD、公开 API 与权限模型影响。验证命令从 `cloud_front/` 或仓库根运行；`dist/`、`tmp/` 被 gitignore，构建产物不作为交付物。问题编号见各任务和本机问题库。回滚办法见第 4 组与第 6 组。

2026-11-05：负责人获知第 1–6 组缺少完整可核对的独立验收记录，以及 4.2 回滚预案验证未勾选后，明确表示“这几个没问题，归档并提交推送”。对该范围按证据不足例外交付，Issue 38；原开发记录保留但不视为独立测试 PASS。负责人先前明确取消 Preview 专项验证，阶段 7 原 BLOCKED 结论和 Issue 33 保留。

## 1. 模型清单与显隐常量

- [x] 1.1 新增 `cloud_front/src/constants/modelProfiles.ts`，列出本地/云端对话模型并单列不可选的 embedding/rerank 模型，每项带 `baseUrl` + `model`。验证：逐项核对 `application.yml`、`cloud_agent/application.yml`、`eval/.env` 与本机 Ollama `/api/tags`；实测 5 个可选档位 + 4 个不可选检索模型。
- [x] 1.2 定义双条件显隐、localStorage key 与原默认档位（后续默认值变更见 7.1）。验证：`npm run type-check` 退出 0；`*.local` 已被忽略。
- [x] 1.3 新增自定义档位契约与 `isUsableCustomProfile` 校验；本轮改为只接受白名单 `apiKeyEnv`、拒绝明文。验证：第 5 组 T9/T10 覆盖合法/非法输入与明文拒绝。

## 2. 状态层与选择器组件

- [x] 2.1 新增 `useModelProfileStore.ts`：读取校验 id、无效回落默认、写入 try/catch；本轮增加历史明文凭据字段清洗并回写。验证：T1/T7/T8/T11 覆盖。
- [x] 2.2 新增 `ModelSelector.vue`：分组下拉、本地/云端标签、禁用不可选项、界面明示后端未随按钮切换。验证：`npm run build-only` 退出 0；`app.use(Antd)` 全局注册可用。
- [x] 2.3 触发器改为紧凑文字形态 + `placement="topLeft"` 向上展开。验证：dev server 返回源码含 `model-selector-trigger`。

## 3. 对话输入栏挂载、云端布局回落与显隐变异检验

- [x] 3.1 从 `GlobalHeader.vue` 摘除挂载与 import（最终无改动），改在 `AiAssistantPage.vue` 的 `.input-bar` 内新增 `.input-toolbar`。验证：`grep -n "ModelSelector"` 在 `GlobalHeader.vue` 无残留；类型检查退出 0。
- [x] 3.2 显隐三态变异检验（本轮重跑）。验证：无变量 `Kt=!1` → 注入 `VITE_ENABLE_MODEL_SELECTOR=true` `Kt=!0` → 恢复 `Kt=!1`；三种构建产物端点字符串命中数均为 0。常量名随压缩重命名，历次为 `LWe` / `At` / `Rt` / `Kt`。
- [x] 3.3 切分支与改动后的目录树完整性复核。验证：旧分支 329/329、新分支 324/324 tracked/disk 对照，`git status --short` 无成片 ` D`。
- [x] 3.4 云端布局回落 `display: contents`，仅 `with-model-selector` 时两行。验证：云端 CSS 产物含 `display:contents` 与 `with-model-selector`；发送 / 停止按钮绑定未改动。

## 4. 工件校验与回滚

- [x] 4.1 `openspec status --change add-model-selector` 与 `openspec validate add-model-selector --strict` 退出 0。验证：status `Progress: 4/4 artifacts complete`；validate 输出 `Change 'add-model-selector' is valid`，exit=0。
- [x] 4.2 回滚预案（不执行）：恢复工作区外备份的 `.env.dev` / `eval/.env` / 源码，撤回文档注释，删除 `.env.local`、`vite/localEnvPlugin.ts` 与 3 个前端新增文件，移除 `AiAssistantPage.vue` 的挂载/import/工具条样式。验证：回滚后 `npm run type-check` 退出 0 且云端构建产物无选择器与端点痕迹。上述回滚后验证未执行；负责人接受第 1–6 组证据不足并批准交付，Issue 38，不代表测试 PASS。备份目录在本机 `%TEMP%/cloudwiki-env-backup-20261104/`，仅核对文件存在，未验证恢复。

## 5. 用户自定义模型档位

- [x] 5.1 常量层独立存储 key、自定义分组、下拉选项与不可信输入校验。验证：T5/T6/T9 覆盖损坏数据与非数组回落。
- [x] 5.2 store 暴露 `customProfiles` / `allProfiles` / `addCustomProfile` / `removeCustomProfile`，删除选中项回落默认。验证：T1–T4、T7–T8。
- [x] 5.3 管理弹窗表单含名称 / 模型 ID / Base URL / 位置 / 提供商 / 备注，提交校验必填与 `http(s)`；已添加列表带 `a-popconfirm` 二次确认删除。验证：`npm run type-check` 与 `build-only` 均退出 0。
- [x] 5.4 界面双处明示「自定义档位只存本浏览器，Python 评测链读不到」。验证：菜单底部 `model-hint` 与弹窗 `manager-hint` 各一处引用 `CUSTOM_HINT`。
- [x] 5.5 用真实编译产物跑功能与变异检验（`tmp/verify-model-store.cjs`，被 gitignore，自清理 bundle）。验证：本轮 13/13 checks passed，exit=0（含 T10 白名单变量名、T11 旧明文清洗、T12 生产端点折叠、T13 内置档位只带引用）。

## 6. 本地密钥统一与前端写入（本轮新增）

- [x] 6.1 迁移前取证与备份。验证：确认 `RAG_EMBEDDING_API_KEY` / `LLM_API_KEY` / `EMBEDDING_API_KEY` 三份同源；系统用户级环境变量确有 `RAG_LLM_API_KEY`；9 个将改动文件逐字节备份到 `/tmp/cloudwiki-env-backup-20261104/` 并 `cmp` 校验。
- [x] 6.2 补根 `.gitignore:47` 的 `.env.local` 规则（**先补规则再写文件**）。验证：`git check-ignore -v .env.local` 命中 `.gitignore:47`；对照 `cloud_front/.env.local` 仍由子目录 `*.local` 覆盖。
- [x] 6.3 生成根 `.env.local`（2 个有值键，不登记空键）、精简 `eval/.env`（删 2 行重复 key，保留 12 个评测专属键）、删除旧 `.env.dev`。验证：三份同源断言通过；两文件键名清单复核；旧文件已删除且备份在位。
- [x] 6.4 改 `start-dev.ps1`：路径 `.env.dev` → `.env.local`，注入白名单 `^(RAG_|DEEPSEEK_)[A-Z0-9_]+$`，跳过空值。验证：带命中数断言的替换脚本 5 处全部命中；`application.yml` / `asset-registry.md` / `eval/README.md` 注释同步。
- [x] 6.5 改 `lib_eval.load_env()` 为 `os.environ` > 根 `.env.local` > `eval/.env` 并填充旧名别名；`smoke_test.py` 复用同一函数。验证：`py_compile` 通过；`load_env()` 17 键齐全、别名同源、评测配置仍在、`os.environ` 覆盖生效；`prepare_keys.py` 无需改动（仍写 `eval/.env` 的评测专属凭据）。
- [x] 6.6 新增 `cloud_front/vite/localEnvPlugin.ts` 并在 `vite.config.ts` 注册；前端管理弹窗增加密钥变量名下拉、API Key 输入、状态列表与保存按钮，明文只 POST、不落浏览器存储。验证：`npm run type-check` / `build-only` 退出 0；生产产物端点字符串 0 命中。
- [x] 6.7 端点端到端验证（`tmp/verify-local-env-endpoint.cjs`，真实插件 + 临时 http server + 占位串写入 + 逐字节恢复）。验证：31/31 passed，含缺头/恶意 Origin/非法键/换行注入/空值/超长值/错误方法的拒绝且文件未改动、写入不回传明文、注释与其他键保留、无重复行、`Python load_env()` 读到新写入值（写入→读取闭环）、真实 `.env.local` 逐字节恢复。
- [x] 6.8 preview 端点变异检验（`tmp/verify-preview-endpoint.mjs`）。验证：preview `POST /__cw/local-env` 返回 404、响应不是端点 JSON，exit=0。
- [x] 6.9 OpenSpec 工件同步：新增 `local-env-secrets` capability delta，`model-profile-selector` spec 增补凭据引用与密钥录入需求，proposal / design / tasks 重写。验证：`openspec validate add-model-selector --strict` 退出 0。

## 实测记录

- `npm run type-check`：exit 0
- `npm run build-only`：exit 0（10.68s / 10.90s，另有 chunk >500 kB 的既有告警，与本次改动无关）
- 显隐三态变异：A 无变量 `Kt=!1` → B 注入变量 `Kt=!0` → C 恢复 `Kt=!1`；端点字符串命中 0 / 0 / 0
- 生产产物：`__cw/local-env` 命中 0 个文件；preview POST 端点 404
- store 契约：`node tmp/verify-model-store.cjs` → 13/13 passed
- 端点端到端：`node tmp/verify-local-env-endpoint.cjs` → 31/31 passed，`.env.local` 逐字节恢复
- Python 加载链：`py_compile` OK；`load_env()` 17 键、别名同源、评测配置仍在、`os.environ` 覆盖生效
- `git check-ignore -v .env.local`：命中 `.gitignore:47:.env.local`
- `openspec validate add-model-selector --strict`：exit 0，`Change 'add-model-selector' is valid`
- `openspec status --change add-model-selector`：`Progress: 4/4 artifacts complete`
- 本轮**未启动**后台开发服务（用户此前已取消 `start-dev.ps1` 后台任务）；验证均通过独立临时进程完成，用完即停

## 7. 默认档位调整

- [x] 7.1 将常量与档位查询回落调整为远程 `cloud-deepseek-flash`。固定验收：默认 ID 和未知 ID 查询均指向 `cloud-deepseek-flash`；已知 ID 查询不变。证据：`docs/issue-reports/add-model-selector/stage-7-round-1.md` A1。回滚：恢复常量与查询回落。
- [x] 7.2 将状态层回落统一为该默认值。固定验收：无存储、无效 ID、存储不可用、删除当前自定义档位及选中项失效时均回落 `cloud-deepseek-flash`；有效已保存选择保持不变。不修改后端模型选择契约。证据：同上 A2。回滚：恢复状态层回落。
- [x] 7.3 验证默认行为。固定验收：自动化断言覆盖 7.1、7.2 所列情形并通过；`npm run type-check` 退出 0。证据：同上 A3。回滚：移除新增验证代码。
- [x] 7.4 修复本次 Vite 插件接入导致的类型检查错误。原固定验收：在 `cloud_front` 运行 `npm run type-check` 和 `npm run build-only` 均退出 0；开发插件仍仅在 dev server 注册，preview 不提供写入端点。证据：同上 A4，Issue 33。Preview HTTP 验收原结论 BLOCKED，负责人明确取消后不再是交付要求；未将该测试记为 PASS，类型检查与构建另有阶段 9 独立证据。回滚：恢复 Vite 配置与 Node TypeScript 配置。

## 8. 开发启动脚本默认关闭隧道

- [x] 8.1 脚本默认不启动临时隧道；`-Tunnel` 显式启用，`-NoTunnel` 优先且兼容。证据：`docs/issue-reports/add-model-selector/stage-8-round-1.md` A1。回滚：恢复隧道参数与启动条件。
- [x] 8.2 添加无真实服务、无网络的启动条件自动化检查，覆盖默认、`-Tunnel`、`-NoTunnel` 以及同时指定两者，并验证 PowerShell 5.1 解析。证据：同上 A2。回滚：移除新增测试。
- [x] 8.3 同步现行 README 和公网演示文档的参数与默认行为。证据：同上 A3，Issue 36；`docs/issue-reports/add-model-selector/stage-8-history-supplement.md` 对历史交接记录补证通过。回滚：恢复相应说明。

## 9. 密钥写入覆盖与隧道隔离

负责人明确取消来源、Origin 和自定义头校验；脚本启用临时隧道时不注册密钥端点。此前 7.4 的 preview 验证按负责人要求不再作为新验收，原 BLOCKED 和 Issue 33 证据保留，不改写为 PASS。保留生产构建隔离检查。

- [x] 9.1 将已有同名键覆盖并去重，保留注释和其他键；不存在时新增一次。固定验收：用不接触真实密钥的隔离验证覆盖零、一、多行及其他键和注释保留。证据：`docs/issue-reports/add-model-selector/stage-9-retest-3.md` A1；Issue 34。回滚：恢复原写入逻辑。
- [x] 9.2 移除来源、Origin、自定义头校验及前端无用头；隧道标志生效时不注册端点，端点不可用时界面禁用密钥区。固定验收：无头有效请求通过输入校验；隧道标志为启用时不注册；生产构建不含端点。证据：同上 A2；Issue 35。回滚：恢复插件及前端调用。
- [x] 9.3 启动脚本依据有效隧道选项向前端进程传递端点禁用标志。固定验收：默认、`-NoTunnel`、`-Tunnel` 和两者同时指定时分别传递关闭、关闭、开启、关闭；不启动真实服务。证据：同上 A3。回滚：恢复脚本隧道标志传递。
- [x] 9.4 为上述写入和隔离提供无真实密钥、无真实服务的可重复测试，运行类型检查和生产构建。固定验收与完整命令见阶段 9 任务卡。证据：同上 A4 和 Issue 37。回滚：移除测试代码。

阶段 9 第一次独立验收 A1–A4 通过，但发现清单外阻断 Issue 37（JSON `null` 引发未处理异常）；原始证据见 `docs/issue-reports/add-model-selector/stage-9-retest-2.md`。按原固定验收修复后重新冻结快照并定向复测及回归，不降低验收。
