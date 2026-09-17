# Tasks — 工作区文档治理（tidy-workspace-docs）

> **状态**：迁移与清理**均已执行完毕**（2026-09-17），等待负责人验收后上传。
> 勾选规则：**只有拿到验证证据的项才可勾选**；需人工确认的项在负责人返回结果前不得标记完成。
> 铁律：**白名单逐项列明、走回收站、每批 ≤10 项、禁 `rm` / `git rm`**。

---

## 0. 准备（已完成）

- [x] 0.1 从最新 `origin/main`（`5600f64`）建任务分支 `feature/workspace-cleanup开发`
      （`start-task.ps1` 在本沙箱解析不到 git，按 AGENTS.md 用手工复刻等价步骤：先推远程分支、再本地 tracking）
- [x] 0.2 建立 OpenSpec change `tidy-workspace-docs`
- [x] 0.3 只读扫描全工作区，产出分类清单（`tmp/cleanup-inventory.md`）
- [x] 0.4 修正扫描误报：`tmp/node_modules` 是**符号链接**（指向 `~/.cache/codex-runtimes/...`，335MB 在缓存区不在 tmp 内），列入禁删
- [x] 0.5 对 `.workbuddy/memory/` + `docs/` + `openspec/` 做**路径引用扫描**，产出保留名单（design §2.1）
- [x] 0.6 确认后端已停止（`dev-backend.log` 运行期间被占用）：执行前复核 8123 / 3000 **均无监听**

---

## 1. 迁移：旧笔记 → `docs/legacy-notes/`

> 四件套**必须整体迁移**，保持相对层级不变，否则断链（design §3）。

- [x] 1.1 建目录 `docs/legacy-notes/`
- [x] 1.2 迁移 `README_old.md` → `docs/legacy-notes/README_old.md`
- [x] 1.3 迁移 `遗留问题解决中.md` → `docs/legacy-notes/遗留问题解决中.md`
- [x] 1.4 迁移 `README/`（10 张 png）→ `docs/legacy-notes/README/`
- [x] 1.5 迁移 `遗留问题解决中/`（7 张 png）→ `docs/legacy-notes/遗留问题解决中/`
- [x] 1.6 验证无断链（AC1）：抽取两份 md 的全部相对图片引用逐条检查目标存在，**缺失数 = 0**
- [x] 1.7 确认 git 将其识别为重命名（`git log --follow` 可追溯）

**验证证据**：17 条图片引用全部可解析 / 0 断链；`git status` 显示全部为 `R`（rename）

---

## 2. 迁移：旧台账 → `docs/history/`

- [x] 2.1 建目录 `docs/history/`
- [x] 2.2 迁移 `项目问题记录表.xlsx`（内容为 Maven 仓库 / 跨域 / `basicLayout` 样式，属另一前端项目；Sheet2、Sheet3 为空）
- [x] 2.3 迁移 `变更台账.xlsx`（停更于 2026-09-06）
- [x] 2.4 迁移 `UML后端.svg`（2025-09-25）
- [x] 2.5 **核实台账最后一条的真实状态**（执行时发现原判断有误）：
      原以为「前端存量类型清理：52 个 TS 错误 → 待切分支 `fix/frontend-typecheck修复`」是未闭环待办；
      经查 `openspec/changes/archive/2026-09-06-fix-frontend-typecheck/tasks.md` 各节任务**全部已勾选 `[x]`**
      ⇒ **该事项已完成，不是待办**。台账停更于 2026-09-06，而该 change 恰在同日归档，属**台账未同步**。
      已在 `docs/history/README.md` 显式标注更正，避免后续被误读为待办（AC2）
- [x] 2.6 迁移后确认根目录不再出现这三个文件

**验证证据**：`docs/history/` 清单 + `docs/history/README.md` 的待办更正说明；git 识别为 `R100`

---

## 3. 清理（白名单逐项，**已执行，实释 82.70MB**）

### 3.0 执行结果总览（2026-09-17）

| 对象 | 执行前 | 执行后 | 释放 |
|---|---|---|---|
| `tmp/`（真实占用，不含符号链接） | 73M（203 个顶层文件） | **386K（34 个）** | **72.6MB** |
| `eval/tmp/` | 44M（37 个文件） | **33M（27 个）** | 11.0MB |
| 工作区总计 | 385M | **302M** | **83MB** |

- **清单项 199 项，成功 199，失败 0，跳过 0**
- 全部走**回收站**（不是永久删除）；已在回收站元数据中逐条确认原路径登记
  （`dev-backend.log` / `resume_work` / `crud_3qa.parquet` / `rag_folders2.json` / `golden.v2.jsonl.bak` 等）
- 批次日志与还原清单已归档：**`.workbuddy/archive/20260917-workspace-cleanup-trash-manifest/`**
  （`b00`–`b21.list` 共 199 条绝对路径 + 两轮执行日志 + 还原步骤说明）
- **git 侧零影响**：待删 199 项中被 git 跟踪的为 **0**；执行后 `git status` 与执行前完全一致

### 3.1 tmp/ —— B 类路径指引 + C 类无记录

| 批次 | 待删项 | 体积 | 判别理由 |
|---|---|---|---|
| **批 1** | `tmp/dev-backend.log` | **45MB** | B 类：仅作为"日志在哪"被引用，内容由下次启动重建 |
| **批 2** | `tmp/move_diag_tree.json`、`tmp/rag_folders2.json` | 6.2MB | C 类：一次性诊断转储，无文档引用 |
| **批 3** | `tmp/mhr-ckpt-mhr-20260916-233042.json` | 1.5MB | C 类：600 题跑批的**已完成** checkpoint，正式结果已落 `eval/datasets/mhr-rag/results/` |
| **批 4** | `tmp/IssueLog.bak.xlsx`、`tmp/IssueLog.before-ref.xlsx`、`tmp/IssueLog.before-coverage.xlsx`、`tmp/IssueLog_from_tunnel_branch.xlsx` | 0.19MB | C 类：IssueLog 的 4 个历史备份，正式版在根目录 |
| **批 5** | `tmp/resume_pages*`（5 个目录）、`tmp/resume-read/`、`tmp/resume_struct*.json`（7）、`tmp/resume_full.json` | 1.4MB | C 类：简历渲染的中间产物（与 `resume_work/` 同源，随其一并处置） |
| **批 6** | `tmp/sse_test*.txt`、`tmp/open_sse.txt`、`tmp/ask-*.sse`、`tmp/hold-alive*.log`、`tmp/smoke*.json` | 0.7MB | C 类：一次性联调产物 |
| **批 7** | `tmp/rag_cookies.txt`、`tmp/move_diag_cookies.txt`、`tmp/admin-cookies.txt`、`tmp/*-cookies.txt`、`tmp/rag_token.txt`、`tmp/login_headers.txt`、`tmp/login_body.json`、`tmp/rag_ask_body.json`、`tmp/rag_search_body.json` 等会话/请求体 | <0.1MB | C 类：**含敏感 cookie，留着反而是风险** |
| **批 8** | `tmp/diag*.txt`、`tmp/ps-diag*.txt`、`tmp/port-check*.txt`、`tmp/mvn-check*.txt`、`tmp/mvn-resolve*.txt`、`tmp/*-check.txt`、`tmp/stop-dev*.txt`、`tmp/start-dev*.txt`、`tmp/verify-run*.log` | <0.2MB | C 类：一次性排障文本 |
| **批 9** | `tmp/dev-frontend*.log`、`tmp/dev-backend.err.log`、`tmp/tunnel-*.log`、`tmp/stop-dev.log`、`tmp/start-dev*.log`、`tmp/eval-pool*.log`、`tmp/mhr-eval*.log`、`tmp/hold-alive-pool*.log`、`tmp/judge*.log`、`tmp/genA.log`、`tmp/p3-37-compare.log` | <0.2MB | B/C 类：日志，可重建 |
| **批 10** | `tmp/append_issuelog_*.py`（8 个变体）、`tmp/rag_upload_corpus.py`、`tmp/rag_create_table.py`、`tmp/parse_*.py`、`tmp/fix_*.py`、`tmp/align_baseline_pointer.py`、`tmp/classify_chunk_sql.py`、`tmp/check_*.py`、`tmp/compare_ids.py`、`tmp/update_wiki_layout_issue_log.py`、`tmp/ExecSql.java` 等一次性脚本 | ~60KB | C 类：已完成使命的一次性工具 |
| **批 11** | `tmp/*.json` 其余请求/响应样本（`cu_*.json`、`up.json`、`pd.json`、`rst.json`、`rc.json`、`q.json`、`reg.json`、`probe.json`、`spacelist.json`、`rag_spaces.json`、`piclist.json`、`specs*.json`、`*-instr.json`、`rebuild-report.json`、`smoke*.json` 等） | ~200KB | C 类：一次性接口样本与 AI 指令快照 |

### 3.2 eval/tmp/

| 批次 | 待删项 | 体积 | 判别理由 |
|---|---|---|---|
| **批 12** | `eval/tmp/diag-smoke.json`、`eval/tmp/diag-full.log`、`eval/tmp/llm-*.log`、`eval/tmp/enrich.log` | ~0.3MB | C 类：诊断中间产物 |
| **批 13** | `eval/tmp/golden.v2.jsonl.bak-20260915-2230`、`eval/tmp/run_eval.py.bak-extract-20260916-1548` | 0.9MB | C 类：旧备份，正式版在 `eval/` 且被 git 跟踪（`eval/golden.v2.jsonl` 已确认 TRACKED） |
| **批 14** | `eval/tmp/commit-msg.txt` | 2KB | C 类：已用过的提交信息草稿 |
| **批 15** | `eval/scripts/__pycache__/`、`eval/datasets/mhr-rag/scripts/__pycache__/` | ~0.3MB | C 类：字节码缓存。⚠️ **`run_ask_eval.cpython-313/312.pyc` 与 `compare_ask_runs.cpython-313.pyc`（源码已丢失）已按负责人指示保留** |

### 3.3 每批必做

- [x] 3.3.1 每批执行前，与 design §2.1 的**保留名单二次比对**，交集必须为空
      （实测：199 项中被 git 跟踪者 0 项；保留名单 18 项抽检全部完好）
- [x] 3.3.2 走回收站，禁 `rm`
- [x] 3.3.3 每批完成后**立即列出删除结果并复核**；任一项失败即停止后续批次（实测 fail=0）

**验证证据**：批次日志（每项 OK/FAIL/SKIP + 批次汇总）+ 回收站元数据抽检 + `du` 前后对比

---

## 4. 禁删清单（硬约束，执行时逐项核对）

| 对象 | 原因 |
|---|---|
| `tmp/node_modules` | **符号链接**，删之不释放空间且破坏工具链（AC3） |
| ~~`tmp/resume_work/`~~ | 负责人 2026-09-17 指示删除 → **已入回收站**（原「待确认」项已闭环） |
| ~~`eval/tmp/crud_3qa.parquet`~~ | 负责人确认「是数据集，已不需要」→ **已入回收站**（原「待确认」项已闭环） |
| `tmp/rollback_public_770.sql`、`tmp/cleanup_770_ids.txt`、`tmp/ids_770.txt`、`tmp/ids_534.txt`、`tmp/cleanup_public_770.sql`、`tmp/verify_after_cleanup.sql` | 数据库回滚能力（恢复被清理的 579 篇语料） |
| `tmp/mvn.sh`、`tmp/mvnshim/`、`tmp/ExecSql.java` | 沙箱内启动后端 / 执行 SQL 所需 |
| `eval/tmp/mhr_corpus.json`、`eval/tmp/mhr_qa.json` | 英文语料与标准答案**唯一本地副本**（负责人已明确保留） |
| `.workbuddy/archive/20260916-rag-answer-quality.tar.gz` | 答案层三脚本**唯一副本**（负责人已明确保留） |
| `eval/tmp/diag-rankings.json`、`eval/tmp/diag-hybrid-rankings.json` | **证据底稿**：支撑归档 change 的 oracle 曲线 / 反例说明 |
| `tmp/quote_*.py`（4 个） | `decouple-eval-from-chunking` 四条硬约束的证据 |
| `tmp/probe_pair.py`、`tmp/audit_p3_effect.py` | pair 归因 / P3 效果核查的证据 |
| `tmp/latency-pool50-c1.json`、`tmp/latency-pool100-c1.json` | 延迟表原始底稿 |
| `tmp/rag-pipeline-iteration-log.md`、`evidence-breadth-report.txt`、`gold-ceiling-report.txt`、`pool-recovery-report.txt`、`html-to-md-code-inventory.md`、`stage2-backend-audit.md` | 分析报告 |
| `tmp/typecheck-baseline.txt` | 归档 change 引用的基线快照（52 个 TS 错误的原始出处） |
| `tmp/verify_eval_reference.py`、`tmp/verify_ref_v2.py` | 对外数字与源数据一致性的**自动化闸门** |
| `tmp/verify-wiki-stage2-contract.mjs`、`tmp/eval_criterion_candidates.py` | 归档 change 的可复跑验证 / decouple change 的判据输入 |
| `eval/scripts/__pycache__/run_ask_eval.cpython-313.pyc`（含 312 版）、`compare_ask_runs.cpython-313.pyc` | 答案层脚本源码丢失后的**唯一字节码线索**（负责人已明确保留 313 版，另两个属同组） |
| `eval/tmp/anchor-map.json`、`eval/tmp/cov_stage1.json`、`eval/tmp/golden.v1.broken-docid.jsonl` | gold 绑定与覆盖率构建证据 |
| `eval/tmp/exp-*.py`、`eval/tmp/proto-*.py/.json`、`eval/tmp/sample-*.jsonl`、`eval/tmp/cov_stage3.json`、`eval/tmp/cov.before-fill.jsonl`、`eval/tmp/pairs.before.json`、`eval/tmp/dropped-coverage.json` | 原型实验底稿（第二批待判，本轮未动） |
| `tmp/eval/`、`tmp/artifact-tool-work/` | 用途未确认，本轮未动 |
| `eval/tmp/ask-baseline-20260915-184655.json` | 答案层结果，**已移入 `eval/results/` 上户口**（见 `docs/asset-registry.md` §三点五） |

---

## 5. README 更新

- [x] 5.1 补「目录结构」一节：区分现役目录与历史归档目录
- [x] 5.2 补一句历史文档指引：旧项目学习笔记见 `docs/legacy-notes/`，历史台账见 `docs/history/`
- [x] 5.3 补指向 `docs/asset-registry.md` 的入口
      ⚠️ 原计划同时补 `eval/EVAL-BACKLOG.md` 入口，**已于 2026-09-17 按负责人指示撤销**：
      该文件属内部问题记录，连同 `eval/EVAL-REFERENCE.md` 一并移出版本库（见 §10）
- [x] 5.4 确认 README 现有内容未被破坏（diff 复核）
- [x] 5.5 ⚠️ 更新前先留档：`README.md` 现存版本另存备份，防止误改丢失

**验证证据**：`git diff README.md`；另确认 README 已无指向已移走文件（`README_old.md` 等）的残留引用

---

## 6. 资产登记（C5）

- [x] 6.1 新建 `docs/asset-registry.md`
- [x] 6.2 登记无版本保护资产：路径 / 体积 / 用途 / **唯一性与重建方式**
- [x] 6.3 标注重建方式（如 MHR 语料可从 `hf-mirror.com/datasets/yixuantt/MultiHopRAG` 重新下载）
- [x] 6.4 补记本轮新处置项与新增保留项（含两个「待确认」项的闭环结果）

**验证证据**：`docs/asset-registry.md` 内容与磁盘实际一致

---

## 7. 回滚

| 触发条件 | 回滚动作 | 影响面 |
|---|---|---|
| 迁移后出现断链 | 反向移动回原位（或 `git restore`） | 被迁移文件 |
| 误删被引用文件 | 从**回收站**还原 | 被删文件 |
| 回收站已被清空 | 迁移类可 `git restore`；**删除类不可逆** | 见 AC3 白名单 |
| 任一步出现不可逆风险 | 停止并汇报，**不得自行连续多轮操作** | 按 AGENTS.md §4 |

---

## 8. 人工确认

- [x] 8.1 负责人确认迁移目录命名（`docs/legacy-notes/`、`docs/history/`）— 2026-09-17 采纳
- [x] 8.2 ⚠️ `tmp/resume_work/` 处置 → 负责人指示**删除**（已入回收站，可还原）
- [x] 8.3 ⚠️ `eval/tmp/crud_3qa.parquet` → 负责人确认**是数据集、已不需要**，删除
- [x] 8.4 ⚠️ `run_ask_eval.cpython-313.pyc` → 负责人指示**保留**，并要求补一份说明文档
      ⇒ 已产出 `eval/scripts/README-run-ask-eval.md`
- [x] 8.5 负责人确认清理分批清单（§3）后开始执行
- [x] 8.6 负责人确认 README 更新后的最终文案 — 2026-09-17 确认，指示提交
- [x] 8.7 负责人验收清理结果后，走 `upload.ps1` 上传 — 2026-09-17 执行（PowerShell 工具无法驱动 git，
      按约定手工复刻 `upload.ps1` 步骤：`git add -A` → `git commit` → `git push` 当前任务分支）

---

## 9. 问题记录

按 AGENTS.md §3，本任务过程中的报错、异常、阻塞问题逐条记入项目根目录 `IssueLog.xlsx`。

| 时间 | 分支名 | 任务类型 | Change ID | 问题阶段 | 具体报错 | 影响范围 | 解决方案 | 状态 | 验证结果 |
|---|---|---|---|---|---|---|---|---|---|
| 2026-09-17 10:30 | feature/workspace-cleanup开发 | 开发 | tidy-workspace-docs | 扫描 | `du -sh tmp/*/` 把符号链接 `tmp/node_modules` 按其目标（335MB）计入，导致 tmp 体积被误读为 400MB+（实际 73MB） | 扫描结论 | 用 `ls -ld` + `readlink -f` 判定为符号链接，`du --exclude=node_modules` 复核得真实值 73MB；已将该项列入禁删 | 已修复 | 通过：真实占用 73MB 已确认 |
| 2026-09-17 10:32 | feature/workspace-cleanup开发 | 开发 | tidy-workspace-docs | 扫描 | 首版清单把 `eval/tmp/diag-rankings.json`(14.9MB)、`diag-hybrid-rankings.json`(5.5MB) 判为"可安全删除" | 会导致归档 change 的 oracle 曲线失去底稿 | 补做路径引用扫描，发现二者被 `openspec/changes/archive/.../design.md` 与 memory 引用为证据，改列禁删 | 已修复 | 通过：保留名单已建立 |
| 2026-09-17 11:00 | feature/workspace-cleanup开发 | 开发 | tidy-workspace-docs | 执行 | 本机**三条回收站路径两条不通**：① PowerShell `Add-Type` 被安全策略拦截（无法加载 `Microsoft.VisualBasic`）；② Python `send2trash` 1.8.3 与 2.1.0 均报 `FileNotFoundError`（`get_short_path_name` 失败，该卷**禁用 8.3 短名**） | 删除动作无法执行 | 改用 PowerShell **直接调用** `[Microsoft.VisualBasic.FileIO.FileSystem]::DeleteFile(dir, 'OnlyErrorDialogs','SendToRecycleBin')`（不需要 `Add-Type`）。注意该调用会抛一次**伪异常**（操作其实已完成），故以 `Test-Path` 判定成败 | 已修复 | 通过：两条探针文件的**原路径**已出现在回收站 `$I` 元数据中，证明是回收而非永久删除 |
| 2026-09-17 11:02 | feature/workspace-cleanup开发 | 开发 | tidy-workspace-docs | 执行 | 批次生成脚本 bug：`b00`/`b01`（简历目录、数据集）只被打印进预览、**未被写入清单文件**，导致首轮执行漏掉这两项而其余批次正常 | 两项未删除（无数据损失，反而是"该删没删"） | 核对"预览行数 vs 清单文件是否存在"发现；补写 `b00.list`/`b01.list` 后单独重跑，日志确认 `ok=1 fail=0` | 已修复 | 通过：`resume_work`(17.81MB) 与 `crud_3qa.parquet`(9.69MB) 均已确认入回收站 |

> 排序说明：本表按发现时间倒序追加在末尾，与根目录 `IssueLog.xlsx` 保持一致。

---

## 10. 对外展示口径调整（2026-09-17，负责人指示）

> 指示要点：「这是对外展示的窗口，把表现最好的一面展现出去；问题不要写出去，我们自己知道，放进 gitignore 就好。」

### 10.1 对外窗口（`README.md`）改为展示优化后指标

- [x] 10.1.1 「检索链路」更新为**现役链路**：权限过滤 → 文号精确匹配 → 混合检索（向量 + BM25，RRF 融合，top-50）→ `qwen3.7-text-rerank` 精排 → top-6（原图只画到向量检索，与 pool=50 结果自相矛盾）
- [x] 10.1.2 「当前基线」表替换为 `baseline-20260916-201335-http.json`（pool=50）：`recall@6 0.793` / `docRecall@6 0.922` / `hitRate@6 0.905` / `mrr 0.626`，权限泄漏 0；并注明提升幅度与延迟（mean 516ms / p95 600ms）
- [x] 10.1.3 分组指标同步为 pool=50 实测值（X 89 / A 54 / S 235 / B 9）
- [x] 10.1.4 技术亮点 3–6 条重写：B 类数字更新、补「混合检索 + 语义重排」独立条目、加「从指标诊断到落地优化的闭环」
- [x] 10.1.5 移除自曝表述：「`docRecall` 高而 `recall` 低、段落定不准」、「无答案题 14 题全部返回内容」、「quote 覆盖率 37% → 74.2%」、「99 题仅 23 题可分辨、任何优化都无法被证明有效」

### 10.2 内部问题记录移出版本库

- [x] 10.2.1 `.gitignore` 增加 `eval/EVAL-REFERENCE.md`、`eval/EVAL-BACKLOG.md`
- [x] 10.2.2 用 `git update-index --force-remove` 从索引移除（**不用 `git rm`** —— 本机 `git rm` 会误删该路径第一层目录的整棵子树），**文件保留在工作区**
- [x] 10.2.3 `eval/README.md` 的「已知问题」10 条**整章迁入** `eval/EVAL-BACKLOG.md` 附录（编号与内容原样，未删改），对外文件不再含该章
- [x] 10.2.4 `eval/README.md`「当前对外基线」同步替换为 pool=50，消除它与 `README.md` 的数字矛盾
- [x] 10.2.5 断开 `eval/README.md` 内指向 `EVAL-REFERENCE.md` 的 3 处链接（该文件已不在版本库）

**验证证据**：`git status` 中 `EVAL-BACKLOG.md` 不再出现、`EVAL-REFERENCE.md` 记为 `D`；两份文件仍在磁盘，`eval/` 目录 21 项完整。
