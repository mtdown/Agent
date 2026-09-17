# 无版本保护资产登记

> **为什么需要这份清单**：下列文件位于 `.gitignore` 覆盖范围内，**不受 git 保护**。
> 一旦被删除，`git restore` 无法找回 —— 只能靠重新下载、重新生成，或**永久丢失**。
>
> 本清单的目的不是限制删除，而是让"哪些东西删了就没了"这件事**有据可查**，
> 避免它们与同目录下的一次性产物在文件系统层面被混为一谈。
>
> 维护规则：新增长期驻留在忽略范围内的资产时，**必须同步登记到此表**。

最后核对：2026-09-17

---

## 一、唯一副本（丢失需重新获取，部分不可再生）

| 路径 | 体积 | 用途 | 唯一性 / 重建方式 |
|---|---|---|---|
| `.workbuddy/archive/20260916-rag-answer-quality.tar.gz` | 757KB | 答案层评测三脚本（`run_ask_eval.py` / `compare_ask_runs.py` / `export_spotcheck.py`）+ 4 份结果 + 3 份报告 | ⚠️ **唯一副本**。已核实 `run_ask_eval.py` **从未提交过 git**（`git log --all --diff-filter=A` 为空）。丢失后无法重建，只能重写 |
| `eval/tmp/mhr_qa.json` | 5.17MB | 英文 MultiHop-RAG **2556 题 + 标准答案** | ⚠️ 唯一本地副本。可重新下载：`hf-mirror.com/datasets/yixuantt/MultiHopRAG/resolve/main/` |
| `eval/tmp/mhr_corpus.json` | 6.79MB | 英文 MHR **609 篇语料** | 同上，可重新下载 |
| ~~`tmp/resume_work/`~~ | 18MB | **个人简历**（`AI简历_原始备份.docx`、`resume_src.docx`、`resume_v2/v3/v4.docx` + 5 个 `edit_step*.py` + `render.py`） | **已处置**：2026-09-17 按负责人指示删除，走**回收站**（`$I3CWNKP` + 12 个条目，原路径已登记）。⚠️ 清空回收站后不可恢复 |

> MHR 两份数据的 sha256 记录于 `eval/datasets/mhr-rag/README.md`，可用于校验重新下载的文件是否与评测所用一致。

## 二、能力型资产（丢失会损失某项能力，可重建但成本高）

| 路径 | 体积 | 用途 | 重建方式 |
|---|---|---|---|
| `tmp/rollback_public_770.sql` | 24KB | **数据回滚 SQL**：恢复 2026-09-16 在「公开文档」空间 `2095512793587744770` 被清理的 579 篇 MHR 语料副本。与应用实现同构（`restoreById` / `reactivateByDocIdAndVersion`） | 依赖同目录的 id 清单；id 清单不存在则无法重建 |
| `tmp/cleanup_770_ids.txt` | 11.6KB | 上述 SQL 的 579 条 id 来源 | 需重新查询数据库 |
| `tmp/ids_770.txt`、`tmp/ids_534.txt` | 各 12KB | 两个空间的文档 id 清单（`...770` 与 `...534`） | 可重新查询，但需知道当时口径 |
| `tmp/cleanup_public_770.sql`、`tmp/verify_after_cleanup.sql` | 6KB | 清理与清理后校验脚本 | 可重建 |
| `tmp/mvn.sh`、`tmp/mvnshim/mvn.cmd` | 1.2KB | **沙箱内启动后端所需**（绕过 POSIX 脚本 / `PATHEXT` 裁剪 / 重复代理变量三重障碍） | 可重建，但障碍组合的排查过程已记录在 `MEMORY-detail-召回优化.md` §7 |

## 三、证据底稿（支撑已发布结论，删除会导致结论不可复核）

| 路径 | 体积 | 支撑的结论 |
|---|---|---|
| `eval/tmp/diag-rankings.json` | 14.9MB | 归档 change `boost-rag-passage-recall` 的 **oracle@20/50/100/200 整条曲线**（tasks 0.3「深取 topK=200 逐题排名，399 题」） |
| `eval/tmp/diag-hybrid-rankings.json` | 5.5MB | `MEMORY-detail-召回优化.md` 中"离线 oracle 不比生产乐观"的**反例依据** |
| `tmp/latency-pool50-c1.json`、`tmp/latency-pool100-c1.json` | 3.6KB | 延迟表（mean 516ms / p95 600ms）的**原始底稿** |
| `tmp/quote_judge_probe.py`、`tmp/quote_rechunk_sim.py`、`tmp/quote_semantic_chunk_sim.py`、`tmp/quote_fuzzy_fp_sim.py` | 17KB | `decouple-eval-from-chunking` 的四条硬约束（33.8% quote 跨文档重复、O≥40 全 100%、按句切 92.9%、假阳性率） |
| `tmp/probe_pair.py`、`tmp/audit_p3_effect.py` | 8KB | pair 类归因分析、P3 改造效果逐条核查 |
| `tmp/rag-pipeline-iteration-log.md`、`tmp/evidence-breadth-report.txt`、`tmp/gold-ceiling-report.txt`、`tmp/pool-recovery-report.txt`、`tmp/html-to-md-code-inventory.md` | 40KB | 迭代日志与三份分析报告 |
| `eval/tmp/anchor-map.json`、`eval/tmp/cov_stage1.json`、`eval/tmp/golden.v1.broken-docid.jsonl` | 0.6MB | gold 绑定与覆盖率构建过程 |
| ~~`eval/tmp/crud_3qa.parquet`~~ | 10.2MB | 负责人确认：**是一个数据集，现已不需要** | **已处置**：2026-09-17 删除，走回收站。来源未做进一步确认（负责人直接判定不需要） |
| `eval/scripts/__pycache__/run_ask_eval.cpython-313.pyc`<br>`run_ask_eval.cpython-312.pyc`<br>`compare_ask_runs.cpython-313.pyc` | 131KB | 答案层脚本**源码丢失后仅存的字节码**（313 版 mtime 与归档源码一致，即最后一版） | ⚠️ 源码同一份在归档 tar.gz 内；字节码是**第二条恢复途径**（反编译）。说明见 `eval/scripts/README-run-ask-eval.md` |
| `.workbuddy/archive/20260917-workspace-cleanup-trash-manifest/` | 63KB | **回收站还原清单**：本次清理送进回收站的 199 项逐条绝对路径（`b00`–`b21.list`）+ 两轮执行日志 + 还原步骤 | 本机、且仅在回收站未清空期间有用；**回收站清空后不可恢复** |
| `tmp/typecheck-baseline.txt` | 3.4KB | 归档 change `fix-frontend-typecheck` 引用的**基线快照**（52 个存量 TS 错误） | 结论已归档；文件是"52"这个数字的原始出处 |
| `tmp/verify_eval_reference.py`、`tmp/verify_ref_v2.py` | 13KB | **可复用闸门**：校验 `eval/EVAL-REFERENCE.md` 的对外数字与源数据一致 | 可重写，但这是对外引用的唯一自动化校验 |
| `tmp/eval_criterion_candidates.py` | 3.2KB | 判据候选生成，是 `decouple-eval-from-chunking` P2/P3 的输入 | 可重写 |
| `tmp/verify-wiki-stage2-contract.mjs` | 1.9KB | 归档 change `add-wiki-space-folder-model` 的验证命令（"`node tmp/...` passed"） | 已验证能力的可复跑手段 |
| `tmp/stage2-backend-audit.md`、`tmp/cleanup-inventory.md` | 14KB | 后端阶段性审计报告；本次清理的扫描底稿 | 文档，留档 |
| `tmp/ExecSql.java` | 1.6KB | **沙箱内执行 SQL 的唯一手段**（无 mysql 客户端，单文件 JDBC） | 可重写，但依赖 maven 仓库 |
| `tmp/eval/`、`tmp/artifact-tool-work/` | 64KB | ⚠️ **用途未确认**（前者含 `scripts/`，后者含 `update-issue-log.mjs/.py` + node_modules 链接） | 未核实用途 → 本轮未动 |

## 三点五、已"上户口"（移入受版本控制目录，不再受本清单约束）

| 路径 | 原位置 | 说明 |
|---|---|---|
| `eval/results/ask-baseline-20260915-184655.json` | `eval/tmp/`（被忽略） | 答案层**唯一留在工作区的结果文件**（`layer=answer`，2 题烟雾运行、`recomputeConsistent=true`）。2026-09-17 移入 `eval/results/`（该目录受 git 跟踪）⇒ 已获版本保护 |

> ⚠️ 答案层的**正式**基线（4 份 `ask-baseline-*.json`）与 3 份报告**仍只存在于**
> `.workbuddy/archive/20260916-rag-answer-quality.tar.gz` 内，尚未落回受控目录。
> 归档包一旦损坏，答案层的全部历史结果与三份脚本将同时丢失 —— 建议后续择机解压落回。

## 三点六、主动移出版本库（对外不发布）

2026-09-17 按负责人指示，把**内部问题记录**移出对外仓库（`.gitignore` + `git update-index --force-remove`）。
两份文件**仍在本地工作区**，功能不受影响；但性质自此改变：

⚠️ 移出**前**若有提交历史，历史版本仍可 `git show HEAD:<path>` 取回；
**移出后的任何修改都不再受版本保护**。

| 对象 | 体积 | 用途 | 风险 |
|---|---|---|---|
| `eval/EVAL-BACKLOG.md` | 17.3KB | 内部问题记录：中文评测集待修清单（P0–P7）+ 由 `eval/README.md` 迁入的「已知问题」10 条 | ⚠️ **唯一副本** —— 该文件 2026-09-17 新建，**从未提交过 git**（只在索引中待提交），**无任何历史版本可取** |
| `eval/EVAL-REFERENCE.md` | 24.8KB | 内部评测参考：题目分类与来源、指标口径、§2.7 本分支实测、§三 下一步计划（P0 答案层盲区等） | 移出前有提交历史，可 `git show HEAD:eval/EVAL-REFERENCE.md` 取回；移出后改动无保护 |

> 相关：对外展示口径同步改为 pool=50（`README.md` 与 `eval/README.md`），详见 change `tidy-workspace-docs` §10。

## 四、明确不在此清单内

| 对象 | 原因 |
|---|---|
| `tmp/node_modules` | **符号链接**，指向 `~/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules`。335MB 实际位于缓存区，删链接既不释放空间又破坏工具链 |
| `cloud_front/node_modules`、`cloud/` 构建产物 | `npm install` / `mvn` 可重建，无唯一性 |
| `tmp/dev-backend.log` 等运行日志 | 内容由服务重启再生 |

## 五、相关提醒

- **`.env.dev`**（仓库根目录）含 API 密钥，同样被忽略。**不登记其内容，仅提醒不要误传或误删。**
- 本清单本身是**文档**，不是访问控制。它不能阻止误删，只能让误删的后果可预判。
- 若某项资产需要真正的版本保护，正确做法是**移出忽略范围并提交**（如体积允许），而不是继续留在原地 + 登记。
