## Why

工作区在近两个月的迭代中积累了约 200 个 `tmp/` 一次性产物，以及若干已被取代的旧文档与旧台账。数量本身不是问题，**真正的问题是风险结构**：

约 **31MB 有价值的资产**与约 **78MB 可重建垃圾**混在**同一个无版本保护区**（`.gitignore` 覆盖的 `tmp/`、`eval/tmp/`、`.workbuddy/`）里：

| 类别 | 体积 | 无版本保护时误删的后果 |
|---|---|---|
| 有价值资产（英文语料、答案层脚本归档、个人简历、回滚 SQL） | 约 31MB | **永久丢失**（`git restore` 救不回被忽略的文件） |
| 可重建垃圾（日志、诊断产物、字节码缓存） | 约 78MB | 无损失 |

这个不对称性已有先例：`eval/tmp/` 在一次 `git rm` 事故中整体丢失，英文 MHR 语料被迫重新下载；`eval/scripts/run_ask_eval.py` 从未提交过 git，如今只剩 `.workbuddy/archive/` 里一份 tar.gz。

同时，根目录混着两代文档：现行 `README.md`（云策库 · CloudPolicy Wiki）与旧项目学习笔记（`README_old.md`、`遗留问题解决中.md`、两个图片目录），以及三份台账（现行 `IssueLog.xlsx` 与两份被取代的旧表）。访客打开仓库无法一眼分辨哪份是现役。

## What Changes

| # | 变更 | 落点 |
|---|---|---|
| C1 | 旧项目学习笔记四件套**整体迁移**到 `docs/legacy-notes/` | 磁盘移动 + git 记录 |
| C2 | 被取代的旧台账迁移到 `docs/history/` | 磁盘移动 + git 记录 |
| C3 | 清理 `tmp/` 与 `eval/tmp/` 中**无记录的一次性产物**（约 78MB） | 磁盘删除（走回收站） |
| C4 | 更新 `README.md`：补目录结构说明、区分现役与历史文档 | 文档 |
| C5 | 把「有价值但无版本保护」的资产**登记成文**（不改变其存放位置） | `docs/asset-registry.md` |

## Capabilities

### New Capabilities

- `workspace-hygiene`: 规定仓库内文档分代规则（现役 / 历史 / 一次性产物）、无版本保护资产必须登记、以及清理作业的安全边界。

### Modified Capabilities

（无）

## Acceptance Criteria

- **AC1** 根目录不再出现旧项目文档：`README_old.md`、`遗留问题解决中.md`、`README/`、`遗留问题解决中/` 已迁至 `docs/legacy-notes/`，且**迁移后 markdown 中的相对图片引用仍然有效**（`README_old.md` 引用 `README/image-*.png` 共 2 处、`遗留问题解决中.md` 引用 8 + 7 处，迁移后不得出现断链）。
- **AC2** 旧台账（`项目问题记录表.xlsx`、`变更台账.xlsx`、`UML后端.svg`）已迁至 `docs/history/`。其中 `变更台账.xlsx` 最后一行写着「前端存量类型清理：52 个 TS 错误 → 待切分支 `fix/frontend-typecheck修复` | 待立项」，**经核实该事项已完成**（对应 change 已归档为 `2026-09-06-fix-frontend-typecheck`，其 tasks 各节全部 `[x]`），属**台账未同步**而非待办 —— 该结论 MUST 在 `docs/history/README.md` 中显式标注，避免后续被误读为未闭环工作。
- **AC3** 清理作业**SHALL NOT** 触碰下列对象（逐项列明，作为硬约束）：
  - `tmp/node_modules`（符号链接，指向 `~/.cache/codex-runtimes/.../node_modules`，删之既不释放空间又破坏工具链）
  - `tmp/resume_work/`（个人简历，**待负责人确认处置方式**）
  - `tmp/rollback_public_770.sql`、`tmp/cleanup_770_ids.txt`、`tmp/ids_770.txt`、`tmp/ids_534.txt`、`tmp/cleanup_public_770.sql`、`tmp/verify_after_cleanup.sql`（数据回滚能力）
  - `tmp/mvn.sh`、`tmp/mvnshim/`（沙箱内启动服务所需）
  - `eval/tmp/mhr_corpus.json`、`eval/tmp/mhr_qa.json`（英文语料与标准答案唯一本地副本）
  - `.workbuddy/archive/20260916-rag-answer-quality.tar.gz`（答案层三脚本唯一副本）
  - `tmp/quote_*.py`（4 个切块耦合探针）、`tmp/probe_pair.py`、`tmp/audit_p3_effect.py`、`tmp/latency-pool*-c1.json` —— **已被 `MEMORY-detail-评测体系.md` / `MEMORY-detail-召回优化.md` 引用的证据文件**
  - `eval/tmp/crud_3qa.parquet`（来源未确认）
- **AC4** 删除一律**走回收站**，不得使用 `rm` / `git rm`；每批 ≤10 项，每批完成后立即复核。
- **AC5** `README.md` 更新后，读者能据此分辨：现役文档、历史归档位置、以及无版本保护资产清单。
- **AC6** 清理后 `git status` 除本次 change 预期变更外无其他异常；被迁移文件的 git 历史可追溯（使用 `git mv` 语义或等价操作）。

## Non-goals

- ❌ 不删除任何**被 git 跟踪**的文件（本 change 只迁移跟踪文件、只删除被忽略文件）
- ❌ 不删除题目、不删除数据集、不改 `golden.v2.jsonl` / `golden.mhr.jsonl` 一个字节
- ❌ 不清理 `cloud_front/node_modules`（374MB 中的绝大部分是前端依赖，可重建但与本次无关）
- ❌ 不停用或修改 `start-dev.ps1` / `stop-dev.ps1` 等脚本
- ❌ 不删除 `tmp/node_modules` 符号链接（见 AC3）
- ❌ 不推进评测集内容修订（归 `EVAL-BACKLOG.md` 与 `decouple-eval-from-chunking`）

## Impact

- 受影响范围：仓库根目录 + `docs/` + `tmp/` + `eval/tmp/`。**后端 / 前端代码、数据库、向量索引零改动。**
- 迁移类变更（C1、C2）全部为 git 跟踪文件，任何一步都可用 `git restore` 回退。
- 删除类变更（C3）**不可逆**（被忽略文件不走 git），因此列明 AC3 硬约束清单并以回收站兜底。
- 回滚方案：C1 / C2 用 `git restore` + 反向移动；C3 从回收站还原；C4 / C5 为纯文档，直接还原。
