# Tasks — enable-port80-and-icp-footer

> 变更等级 **L2**（涉及 CI/CD 生成的部署配置）。允许修改文件共 10 个：4 个 OpenSpec 工件 + 6 个项目文件。
> 问题编号统一用 `ICP-01` ~ `ICP-03`；问题当前状态只在 SQLite（`scripts/issue_tracker.py`）维护，本文件不维护状态。
> 勾选规则：只有拿到验证证据的项才可勾选。

## 0. 准备（总管）

| ID | 任务 | 验证证据 |
|---|---|---|
| 0.1 | 工作区检查：无未提交/未跟踪改动、无分支占用 | `git status --porcelain` 输出 0 行；`ls-remote` 无同名分支 |
| 0.2 | 从最新 `origin/main` 建任务分支 `feature/icp-beian-port80开发` | `start-task.ps1` 等价步骤（本沙箱 PowerShell 解析不到 git，用系统 git 复刻） |
| 0.3 | 切换后复核目录完整性 | `git status --short` 为空；`find openspec -type f \| wc -l` = 334 |
| 0.4 | 建立 change `enable-port80-and-icp-footer` | `openspec status --change ... --json` |

**回滚**：删除本地/远程任务分支，不影响 main。

## 1. 端口单一来源（开发者）

允许文件：`.github/workflows/deploy.yml`、`deploy/.env.example`、`deploy/docker-compose.yml`、`deploy/nginx.conf`

| ID | 任务 | 固定验收 | 验证命令 / 预期 |
|---|---|---|---|
| 1.1 | `deploy.yml:106` 改为 `printf 'HTTP_PORT=80\n'`，保留 `:111` 的 `HTTP_PORT` 自检 | AC1 | `grep -n "HTTP_PORT" .github/workflows/deploy.yml` → 两处，一为 `=80`，一为自检列表 |
| 1.2 | `.env.example` 改为 `HTTP_PORT=80` 并更新注释 | AC2 | `grep -n "HTTP_PORT" deploy/.env.example` → `HTTP_PORT=80` |
| 1.3 | 修正 compose 与 nginx.conf 的注释，明确容器内固定 8080、宿主机由 HTTP_PORT 控制 | AC2 | `grep -n "80:80"` 在这两个文件 0 命中；`listen 8080;` 仍在 |
| 1.4 | 变异检验：在临时副本上把 `listen` 改成 80，1.3 的断言应能报出不一致 | 防假阳性 | 只改临时副本，不动工作区文件 |

**证据落点**：命令输出贴在交付回执；`deploy.yml` 的 diff 必须只有端口值一处变化。
**回滚**：`git restore` 这四个文件。

## 2. 部署文档（开发者）

允许文件：`deploy/README.md`

| ID | 任务 | 固定验收 | 验证命令 / 预期 |
|---|---|---|---|
| 2.1 | 重写「备案通过后切 80」章节，删除 `"80:80"` 与可直接照抄的证书签发命令 | AC4 | `grep -n "80:80\|certbot" deploy/README.md` → 0 命中 |
| 2.2 | 写明顺序：安全组放行 80 → 合并 main → CI 部署 → 验证 | AC4 | 章节内「安全组」出现在「合并」步骤之前 |
| 2.3 | 同步架构图「对外只暴露 8080」为备案后 80 | — | 全文无「只暴露 8080」 |

**回滚**：单文件 `git restore`。

## 3. 前端备案号（开发者）

允许文件：`cloud_front/src/layouts/BasicLayout.vue`

| ID | 任务 | 固定验收 | 验证命令 / 预期 |
|---|---|---|---|
| 3.1 | footer 增加 `渝ICP备2026028042号-1` 链接到 `http://beian.miit.gov.cn/`，保留「项目文件」 | AC3 | `grep -n "渝ICP备2026028042号-1\|beian.miit.gov.cn\|项目文件"` → 三处命中 |
| 3.2 | UTF-8 编码与前端构建校验 | AC3 | `npm run build-only` 退出码 0（依赖不可用时记为遗留项，不伪报 PASS） |

**回滚**：单文件 `git restore`。

## 4. 收尾（总管）

| ID | 任务 | 验证证据 |
|---|---|---|
| 4.1 | `openspec validate enable-port80-and-icp-footer --strict` | AC5，退出码 0 |
| 4.2 | 全量静态断言复核 AC1–AC4、AC6 | 逐条命令输出 |
| 4.3 | `git status --short` 文件清单 = 10 个预期文件 | AC7 |
| 4.4 | 用系统 git 复刻 `upload.ps1`：add / commit / push，并 `fetch` 后验 `rev-list --count upstream..HEAD` = 0 | 推送落地校验 |
| 4.5 | 确认本分支未触发 CI（不在 `on.push.branches`） | AC6 |

**禁止**：合并 main、直接改服务器 `.env`、放行安全组（负责人操作）。

## 5. 问题编号

| 编号 | 描述 | 影响面 | 处理办法 |
|---|---|---|---|
| ICP-01 | `deploy/README.md` 原「改 ports 为 80:80」与 `nginx.conf listen 8080` 冲突 | 照做会导致站点不可用 | 本 change 修正文档（任务 2.1） |
| ICP-02 | CI 每次部署重写 `.env`，只改服务器不是永久修复 | 端口配置会被覆盖 | 本 change 改 `deploy.yml:106`（任务 1.1） |
| ICP-03 | 安全组 80 状态本机不可核实 | 决定切端口后能否访问 | 负责人控制台确认；写入 README 顺序约束 |

## 6. 回滚办法汇总

| 触发条件 | 动作 |
|---|---|
| 合并后 80 不通 | 安全组补放行；仍不通则 `deploy.yml:106` 改回 8080 并重新部署 |
| footer 文案错误 | `git revert` 单文件并重建前端 dist |
| 需要整体撤销 | 不合并 main；已合并则 revert PR |
| 文档描述不符 | 直接修正 `deploy/README.md`，无运行时影响 |

## 7. 验证记录（2026-11-05，总管执行）

> 本轮**未建立独立测试实例**，以下为总管静态验证、自检与线上运行证据；按规则不等价于独立验收 PASS。
> 负责人已于 2026-11-05 明确批准「证据不足例外」并完成归档，详见 §8；测试结论仍记为证据不足，**不改写为 PASS**。

| 项 | 命令 / 动作 | 实际结果 |
|---|---|---|
| AC1 | 查 `.github/workflows/deploy.yml` 的 `HTTP_PORT` | `:106 printf 'HTTP_PORT=80\n'`；`:111` 自检列表仍含 `HTTP_PORT` |
| AC2 | 查 `80:80` 在 README / nginx.conf / compose | 0 命中；`nginx.conf:6 listen 8080;`、`compose:127 "${HTTP_PORT:-8080}:8080"` 均在 |
| AC3 | 查 `BasicLayout.vue` 的备案号 / 工信部链接 / 项目文件 | 三处内容齐全；footer 仍 `text-align:center` |
| AC4 | 查 README 的安全组 / 永久修复 / 合并步骤行号 | 207 < 210 < 212，顺序正确；`certbot`、`只暴露 8080` 均 0 命中 |
| AC5 | `openspec validate enable-port80-and-icp-footer --strict` | 退出码 0，`Change ... is valid` |
| AC6 | 读 `deploy.yml` 的 `on.push.branches` | 仅 `main` 与 `feature/deploy-cicd开发`，本分支不在内 |
| AC7 | `git status --short` + 展开新目录 | 6 个 `M` + 5 个新文件（4 个工件 + CLI 生成的 `.openspec.yaml`）= 11 个 git 文件 / 10 个内容文件，无范围外改动 |
| 3.2 | `npm --prefix cloud_front run build-only` | 退出码 0，`✓ built in 11.18s`（仅原有 chunk >500kB 警告） |
| 1.4 | 内存副本把 `listen 8080;` 改成 `listen 80;` 后重跑断言 | 原文 True / 副本 False ⇒ 断言能检出变异，非恒真 |
| 合并落地 | `git ls-remote origin main` + `merge-base --is-ancestor 140dac4 FETCH_HEAD` | `main=25acea4`（Merge PR #60），**YES 含 140dac4**；工作区干净、openspec 339 文件 |
| 安全组 / 端口三态 | `curl --noproxy '*' http://cloudwiki.club/` | 放行前 `curl_exit=28`（超时）→ 放行后 `curl_exit=7`（拒绝）→ 部署后 **`http_code=200`，remote `8.137.56.125:80`** |
| 旧端口下线 | `curl --noproxy '*' http://cloudwiki.club:8080/` | 合并前 `http_code=200` → 合并后 `http_code=000 / curl_exit=7`，`:8080` 已不再映射 |

**构建产物**：`cloud_front/dist/` 被 `cloud_front/.gitignore:12` 忽略，未进入 `git status`。

**遗留项（归档时状态）**：安全组放行 TCP 80、合并 main 触发部署已于 2026-11-05 完成并有线上证据；独立测试实例验收由 §8 的负责人例外批准覆盖；HTTPS/443 与公安备案属本 change 范围外后续事项，不是交付阻断。

## 8. 任务完成状态与负责人例外批准（2026-11-05）

> 原 tasks.md 为纯表格、无勾选框，`openspec list` 显示 `No tasks` 且 `validate --strict` 报 0 tasks；归档前按 OpenSpec 格式补为可勾选清单。
> **勾选含义**：任务按 §7 证据与下方负责人例外批准完成交付；**不代表独立验收 PASS**，独立测试实例未建立。

- [x] 8.1（原 0.1）工作区检查 — `git status --porcelain` 0 行；`ls-remote` 无同名分支
- [x] 8.2（原 0.2）从最新 origin/main 建任务分支 — 基于 `ae99e3a`
- [x] 8.3（原 0.3）切换后复核目录完整性 — status 空、openspec 334 文件
- [x] 8.4（原 0.4）建立 change — `openspec status --json` 四工件 done
- [x] 8.5（原 1.1）`deploy.yml:106` 写 `HTTP_PORT=80` — AC1
- [x] 8.6（原 1.2）`.env.example` 同步 — AC2
- [x] 8.7（原 1.3）compose / nginx.conf 注释修正 — AC2，`80:80` 0 命中、`listen 8080;` 保持
- [x] 8.8（原 1.4）变异检验 — 断言能检出 `listen 80;` 变异，非恒真
- [x] 8.9（原 2.1）README 删除 `"80:80"` 与 certbot — AC4
- [x] 8.10（原 2.2）README 写明「安全组 → 合并 → CI → 验证」顺序 — AC4，行号 207 < 210 < 212
- [x] 8.11（原 2.3）架构图同步为对外 80 — `只暴露 8080` 0 命中
- [x] 8.12（原 3.1）footer 备案号 + 工信部链接 — AC3
- [x] 8.13（原 3.2）UTF-8 与前端构建 — `build-only` 退出码 0
- [x] 8.14（原 4.1）`openspec validate --strict` — AC5，归档前复跑退出码 0
- [x] 8.15（原 4.2）AC1–AC4、AC6 静态断言复核 — §7 逐条输出
- [x] 8.16（原 4.3）改动清单 = 预期文件 — AC7，11 个 git 文件 / 10 个内容文件
- [x] 8.17（原 4.4）add / commit / push 并验 upstream — `140dac4`，`upstream..HEAD = 0`
- [x] 8.18（原 4.5）本分支未触发 CI — AC6，`on.push.branches` 仅 main 与 deploy-cicd

**负责人例外批准记录**

- 批准人／角色：负责人（李海东）。
- 时间与明确指令：2026-11-05，在被告知「缺独立验收 PASS 会阻塞归档」后，明确选择「**批准例外并归档**」。
- 适用范围：本 change 阶段 0–4 的全部任务，**仅针对「未建立独立测试实例、无独立验收 PASS」这一证据不足项**。
- 理由：L2 部署配置类改动，已有 AC1–AC7 静态断言、变异检验、前端生产构建，以及合并后在真实环境跑通的端到端证据（`80 → 200`、`:8080 → 拒绝`）；不建立独立测试实例不影响对主要风险的判断。
- 证据：§7 全部行，含合并落地、安全组/端口三态、旧端口下线。
- 决定：按 `openspec-archive-change-local` 第 2 条的「证据不足例外」路径继续同步主规格与归档；**测试结论保留「证据不足」，不写成独立验收 PASS，不虚构批准**。
- 交付阻断核对：无 FAIL、无 BLOCKED；HTTPS/443 与公安备案为范围外后续事项。
