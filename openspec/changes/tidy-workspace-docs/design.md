# 设计：工作区文档治理

## 1. 问题定性

这不是"磁盘不够用"，而是**风险不对称**：无版本保护区（`.gitignore`）里同时躺着

- 删了**永久丢失**的资产（英文语料、答案层脚本归档、个人简历、数据回滚 SQL）
- 删了**毫无损失**的垃圾（日志、诊断中间产物、字节码缓存）

一旦有人（或 AI）拿着"清理 tmp"这个指令动手，二者在文件系统层面**没有任何可见差异**。因此本设计的第一目标不是删除，而是**先建立可判定的边界**。

## 2. 判定规则：文件三分类

以「**是否被仓库内文档引用、且引用它的是结论还是路径**」为判据，而不是以"看起来像不像垃圾"为判据。

| 类别 | 判据 | 处置 |
|---|---|---|
| **A. 证据底稿** | 被 `openspec/`、`docs/`、`.workbuddy/memory/` 引用，且引用语境是**支撑某个已发布结论**（"底稿""逐题落盘""产出"） | **保留**（删了对应指标不可复核） |
| **B. 路径指引** | 被引用但语境是**"出问题看这个文件"**之类运维指引，内容可由重跑再生 | 可删 |
| **C. 无记录** | 无任何仓库内文档引用 | 可删 |

### 2.1 扫描结果（已执行）

对 `.workbuddy/memory/` + `docs/` + `openspec/` 做路径正则提取，命中的 `tmp/` 文件如下（**这份清单就是保留名单的权威来源**）：

**A 类 · 证据底稿（保留）**

| 文件 | 体积 | 引用它的结论 |
|---|---|---|
| `eval/tmp/diag-rankings.json` | 14.9MB | 归档 change `boost-rag-passage-recall` tasks 0.3「深取 topK=200 逐题排名（399 题）」→ **支撑 oracle@20/50/100/200 整条曲线** |
| `eval/tmp/diag-hybrid-rankings.json` | 5.5MB | `MEMORY-detail-召回优化.md:46` 用它作为**反例**说明"离线 oracle 不比生产乐观" |
| `tmp/latency-pool50-c1.json` / `-pool100-c1.json` | 3.6KB | 延迟表（mean 516ms / p95 600ms）的**原始底稿** |
| `tmp/quote_judge_probe.py`、`quote_rechunk_sim.py`、`quote_semantic_chunk_sim.py`、`quote_fuzzy_fp_sim.py` | 17KB | `decouple-eval-from-chunking` 的四条硬约束（33.8% 跨文档重复、O≥40 全 100%、按句 92.9%、假阳性） |
| `tmp/probe_pair.py`、`tmp/audit_p3_effect.py` | 8KB | pair 类归因、P3 效果逐条核查 |
| `tmp/rag-pipeline-iteration-log.md`、`evidence-breadth-report.txt`、`gold-ceiling-report.txt`、`pool-recovery-report.txt`、`html-to-md-code-inventory.md` | 40KB | 迭代日志与三份分析报告 |
| `eval/tmp/anchor-map.json`、`eval/tmp/golden.v1.broken-docid.jsonl`、`eval/tmp/cov_stage1.json` | 0.6MB | gold 绑定与覆盖率构建过程 |
| `eval/tmp/mhr_corpus.json`、`eval/tmp/mhr_qa.json` | 12MB | 英文数据集唯一本地副本（负责人已明确保留） |

**B 类 · 路径指引（可删）**

| 文件 | 体积 | 为什么可删 |
|---|---|---|
| `tmp/dev-backend.log` | 45MB | 仅被 `docs/demo-public-tunnel.md:157` 与 memory 当作"日志在哪"提及；内容由下次启动重建。**文件路径本身才是被引用的东西，不是内容** |
| `tmp/tunnel-cpolar.log`、`tmp/tunnel-cloudflared.log` | ~3KB | 同上 |
| `tmp/rag_cookies.txt`、`tmp/move_diag_cookies.txt`、`tmp/*-cookies.txt` | <1KB | 会话痕迹，含敏感 cookie，**留着反而是风险** |
| `tmp/cleanup-inventory.md` | 6KB | 本 change 的过程产物（其最终内容已并入本 change） |

**C 类 · 无记录（可删）** —— 约 180 个文件，见 §4 清单。

## 3. 迁移方案：如何让旧笔记"搬家不断图"

旧笔记四件套是**自洽孤岛**：`README_old.md` + `遗留问题解决中.md` 引用两个同级图片目录（`README/image-*.png` 共 10 张、`遗留问题解决中/image-*.png` 共 7 张）。

因为引用是**相对路径且同级**，只要**整体迁移到同一个新目录、保持四者的相对层级不变**，引用即自动继续有效：

```
迁移前                          迁移后
README_old.md                   docs/legacy-notes/README_old.md
遗留问题解决中.md          →    docs/legacy-notes/遗留问题解决中.md
README/            (10 png)     docs/legacy-notes/README/          (10 png)
遗留问题解决中/      (7 png)     docs/legacy-notes/遗留问题解决中/    (7 png)
```

**硬约束：四者必须整体迁移，不得拆分。** 单独挪 md 或单独挪图片目录都会产生断链死图。
迁移后**必须逐条验证引用**（见 tasks T3.2）。

## 4. 删除安全边界

1. **白名单优先，而非黑名单。** 删除名单逐项列出，不写"删除 tmp 下所有非保留文件"这类通配表述 —— 通配是本项目已有事故的成因（`git rm` 曾删掉路径第一层整棵子树）。
2. **一律走回收站**，禁止 `rm` / `git rm`。被忽略文件不走 git，回收站是唯一兜底。
3. **每批 ≤10 项**，每批完成后立即复核；任一项失败即停止。
4. **删除前先确认没有进程占用**（`dev-backend.log` 在服务运行期间被写入，须先停服务）。
5. **符号链接不删**：`tmp/node_modules` 指向 `~/.cache/codex-runtimes/.../node_modules`，删除链接既不释放空间（335MB 在缓存区）又破坏工具链。

## 5. 风险与替代方案

| 风险 | 缓解 |
|---|---|
| 误删被引用文件导致结论不可复核 | §2.1 的扫描清单就是保留名单；执行前二次比对 |
| 图片相对引用断链 | 整体迁移 + migrated 后逐条验证引用（AC1） |
| 迁移后 git 历史断裂 | 用 `git mv` 语义（或 `git add -A` 后 git 能识别为 rename），保证 `git log --follow` 可追溯 |
| 回收站被清空 | 迁移类可 `git restore`；删除类**不可逆**，故删除范围限于 C 类（无任何文档引用） |
| `eval/tmp/crud_3qa.parquet`（10MB）来源不明 | **不删，列入待确认**。宁可留一个来源不明的 10MB，也不赌它没用 |

**替代方案（未采用）**：把 A 类证据底稿压缩后归档到 `.workbuddy/archive/` 再删原文件，可再省约 18MB。
未采用原因：A 类文件路径**被文档明文引用**，压缩后路径失效，反而破坏可复核性（除非同步改文档，成本高于收益）。
若负责人后续确认这些结论不再需要复核，可再执行压缩归档。

## 6. 与既有资产登记的关系

没有版本保护 ≠ 应该删。本 change 新增 `docs/asset-registry.md`（C5），把下列对象**登记成文**，使其"虽无 git 保护，但至少有文档记录"：

`eval/tmp/mhr_corpus.json`、`eval/tmp/mhr_qa.json`、`.workbuddy/archive/20260916-rag-answer-quality.tar.gz`、`tmp/resume_work/`、`tmp/rollback_public_770.sql`、`tmp/mvnshim/`

登记内容：路径、体积、用途、**重建方式或唯一性说明**（例如"唯一副本，丢失需重新联网下载"）。
