# RAG 实验配置台账：每次实现的链路配置 + 指标

> **本文件是"每次实现到底改了什么、指标长什么样"的对照表。**
> 所有指标从磁盘上的 run 产物现算（`eval/scripts/list_run_ledger.py`），配置从 change 工件 +
> `application.yml` + git 提交交叉核对；不是从记忆或 README 里抄的。
> 复算与取证脚本：`eval/scripts/list_run_ledger.py`（指标，只读）、
> `eval/scripts/diag/`（答案层归因 + 拒答判据取证，只读）。

## 0. 读这条表的四条硬约束（不遵守就会误读）

1. **两条赛道不合并、不横比**。中文主赛道（216 篇重庆政务公文）与英文泛化赛道
   （MultiHop-RAG 609 篇英文新闻）语料、题型、切分 profile、gold 绑定全不同，
   共享的只有指标内核 `eval/scripts/lib_rag_eval.py`。
2. **"正确率"只有英文 MHR 100 题有**（LLM 判官三分类）。**中文侧从来没有正确率** ——
   中文答案层用的是四项零 LLM 成本指标（拒答率 / 误拒率 / 引用覆盖率 / 文号合规率）。
   所以下表中文行的"正确率"列是 `—`，不是"没跑"，是"这个口径不存在"。
3. **检索跑批与答案层跑批是两套 run**，题集不同（中文检索 399 题 / 英文检索 521~529 题 /
   答案层 100 题）。检索 `recall@6` 与答案正确率**不构成同一行的因果链**，
   只能并置看落差。
4. **run 文件不记录后端检索配置**（`config` 块只有 runner 侧字段：baseUrl / goldenFile /
   fetchK / ks / spaceId；答案层多了 judgeModel / answerSet）。
   因此"配置"列来自 change 工件 + `application.yml` + git 提交时间线交叉核对，
   每行都标了对应提交号，可 `git show <hash>` 复核。

---

## 1. 当前基线配置（从切块到输出，逐段）

### 1.1 切块（导入时，`WikiRagIndexServiceImpl` + `MarkdownChunker` + `ChunkerProfile`）

| 项 | 值 | 出处 |
|---|---|---|
| 允许的导入格式 | 仅 `md` / `html` / `htm` → 统一转 Markdown | `wiki-multiformat-document-import` |
| `contentFormat` 兜底 | 未传即默认 `markdown`（**防零切片**） | `DocumentWikiController:100-102` |
| profile 选择 | 按正文 CJK 字符占比 **≥ 0.5 判中文**，否则英文；不可判定回落中文 | `ChunkerProfile.resolve` |
| **中文 profile** | 上限 **600** / 最小 100 / 重叠 **80** / 句界 `。；` / 不要求词边界 | `ChunkerProfile.ZH` |
| **英文 profile** | 上限 **1800** / 最小 100 / 重叠 **100** / 句界 `.!?;` / 要求词边界 | `ChunkerProfile.EN` |
| 产出 | `wiki_chunk` 行：`chunkText`（含上一块尾部的重叠）、`chunkIndex`、`chunkHeading`、`docNumber` | — |
| 切分时机 | **AFTER_COMMIT 异步**，导入接口返回时 `wiki_chunk` 还没有行 | `WikiRagIndexListener` |

> 英文必须用独立 profile：中文 600/80 用于英文时，**55 条证据在任何单块里都找不到**，
> `recall@K` 上限只有 0.9898；1800/100 为 1.0000。中文 profile 逐字节未变。

### 1.2 embedding（入库）

| 项 | 值 |
|---|---|
| 端点 | DashScope compatible-mode `https://dashscope.aliyuncs.com/compatible-mode/v1` |
| 模型 | **`qwen3.7-text-embedding-flash`** |
| 维度 | **1024** |
| 存储 | `wiki_chunk.embedding` BLOB，**float32 小端**（`VectorCodec`） |
| 检索时载入 | `InMemoryCosineVectorStore`（按空间缓存、文档变更即失效重载），**暴力余弦** |
| batch / 超时 | `batch-embed-size=10` / `timeout-seconds=30` |
| 历史 | 2026-09-12 之前是本地 Ollama `qwen3-embedding:4b`（**2560 维**） |

### 1.3 检索（`RagSearchServiceImpl.search`）

| 步骤 | 配置 |
|---|---|
| ① 权限过滤 | `user-visible ∩ requested` 先算，向量库只搜授权文档（权限红线，`leakCount` 一票否决） |
| ② 文号精确层 | 命中即**钉位候选组**（上限 200 条），**组内按相关性排序**；**不参与 RRF 融合**（融合会把 B 类 oracle@50 从 0.7506 摊薄到 0.4129） |
| ③ 通道 A（稠密） | 向量余弦，每分支取 `candidate-pool-size` 个 |
| ④ 通道 B（词法） | 中文 **BM25**，CJK **bigram** 切词，`k1=1.2` / `b=0.75`，零外部依赖 |
| ⑤ 融合 | **RRF**，`rrf-k=60`（文献默认值，刻意不为评测集调参） |
| ⑥ 候选池 | `candidate-pool-size=50` + `fusion-pool-size=50`（**默认池深定稿 50**） |
| ⑦ **重排** | DashScope 原生端点 `.../rerank/text-rerank/text-rerank`，模型 **`qwen3.7-text-rerank`**，`max-documents=50`，`timeout-seconds=20`；**失败静默回退融合顺序，检索不因此失败** |
| ⑧ 返回 | `top-k=6`（硬上限 `top-k-max=50`） |
| 增强开关（**默认全关**） | `multi-query`：`qwen3.8-flash`，权重 原问题 1.0 / 改写 0.7 / 假想答案 0.6，超时 20s<br>`evidence-assembly`：合并上限 3600 字符 / 每块最多 3 chunk / 索引间距 1 / 单文档最多 3 / 最终上下文 K=6 |

**池深取舍的实测依据**（399 题，`application.yml` 内注释同步记录）：

| 池深 | oracle@N（理论上限） | 实测 `recall@6` | 吃到上限 | 端到端 mean / p95 |
|---|---|---|---|---|
| 20 | 0.7690 | 0.7590 | 98.7% | — |
| **50（默认）** | 0.8600 | **0.7930** | 92.2% | **516ms / 600ms** |
| 100 | 0.9061 | 0.8030 | 88.6% | 720ms / 928ms |

### 1.4 输出（生成）

| 项 | 值 |
|---|---|
| `SYSTEM_PROMPT` | 4 条规则：①资料不足须明说"根据现有资料未能找到相关依据"、不得编造 ②引用标角标 `[n]` ③不得自编文号 ④用简体中文 |
| prompt 组装 | `问题：<query>\n\n资料：\n[1]《标题》（文号）\n<chunkText>\n---\n...` |
| 生成模型（快速档） | `fast-model` = **`deepseek-chat`**（默认走这条） |
| 生成模型（深度思考档） | `model` = `deepseek-v4-flash-vision-exp`（前端勾"深度思考"时才用） |
| 思考开关 | `enable-thinking` **默认不发送该字段**（各厂商保持自身默认行为，DeepSeek 链路逐字节不变） |
| 参数 | `max-tokens=8192`、`timeout-seconds=120`、流式 `/chat/completions` |
| 传输 | **SSE 长连接**，事件序列 `meta`（空间+授权文档数+引用列表）→ `reason`/`delta`（增量）→ `done`（含分步耗时）/`error`；SSE 连接上限 **硬编码 120s**（`RagAskServiceImpl:66`） |
| 拒答 | **唯一硬触发是 `hits.isEmpty()`**（零命中直接回固定话术，不调 LLM）；其余全靠 prompt 自律 |

---

## 2. 中文主赛道：每次实现的配置与指标

语料 216 篇重庆政务公文 / 2061 块；切分全程 ZH 600/80；生成全程 `deepseek-chat`。
题集：`golden.v1.jsonl` 99 题（75 计分）→ `golden.v2.jsonl` 423 题（**399 计分**）。

| # | 实现（提交） | 时间 | embedding | 检索 | 重排 | `recall@6` | `docRecall@6` | `hitRate@6` | `mrr` | 正确率 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 初版 RAG 管道 `add-wiki-rag-pipeline` (`9a7d052`) | 09-12 | Ollama `qwen3-embedding:4b` **2560维** | 纯向量，取 top-10 | 无 | **0.4338** (n=75) | 0.8533 | 0.7200 | 0.3975 | — |
| 2 | embedding 切云端 `switch-embedding-dashscope` (`1b98b05`) | 09-12 21:33 | **DashScope 1024维** | 纯向量 | 无 | 未单独跑批 | — | — | — | — |
| 3 | 扩题到 v2 `expand-rag-eval-coverage` (`8197b55`) | 09-15 09:12 | 1024维 | 纯向量，取 top-10 | 无 | **0.5611** (n=399) | 0.8722 | 0.6817 | 0.4473 | — |
| 4 | 同配置重跑（测非确定性） | 09-15 09:14 | 1024维 | 纯向量 | 无 | **0.5623** (n=399) | 0.8697 | 0.6842 | 0.4486 | — |
| 5 | 混合检索+重排 `boost-rag-passage-recall` (`505576b`)，池深 20 | 09-16 20:00 | 1024维 | **向量+BM25→RRF(k=60)**，池 20 | **`qwen3.7-text-rerank`** | **0.7590** (n=399) | 0.9098 | 0.8772 | 0.6159 | — |
| 6 | 同上，**池深 50（定稿）** | 09-16 20:09 | 1024维 | 同上，池 50 | 同上 | **0.7930** (n=399) | 0.9223 | 0.9048 | 0.6262 | — |
| 7 | 同上，池深 100 | 09-16 20:18 | 1024维 | 同上，池 100 | 同上 | **0.8030** (n=399) | 0.9223 | 0.9048 | 0.6292 | — |

- **对外基线 = 第 6 行**（`eval/results/baseline-20260916-201335-http.json`，pool=50）。
- 第 3、4 行是**同配置重跑**，差 0.12pt ⇒ 与"`recall@6` 差异 < 0.2pt 不作改进证据"同源。
- 第 5~7 行是同一次实现只改池深，池深 50→100 只 +1.0pt 却多 190ms ⇒ 默认定稿 50。

### 中文答案层（**零 LLM 成本四项，无正确率口径**）

| runId | 时间 | 题量 | 检索侧 | 生成 | 拒答率(应拒答) | 误拒率(有答案) | 引用覆盖率 | 文号合规率 |
|---|---|---|---|---|---|---|---|---|
| `ask-baseline-20260915-185405-rejudged` | 09-15 18:47 | 54 | 纯向量 1024维 | `deepseek-chat` | 1.000 (14/14) | 0.025 (1/40) | 0.8536 | 0.9444 (51/54) |
| `ask-baseline-20260915-231820-rejudged`（P3 后） | 09-15 23:15 | 58 | 纯向量 1024维 | `deepseek-chat` | 0.800 (16/20) | 0.0263 (1/38) | 0.8030 | 0.9483 (55/58) |

**P3 前后同题对跑**（54 共同题，两侧按当前判据重判，零 LLM 调用）：

| 指标 | P3 后 | P3 前 | 差值 |
|---|---|---|---|
| 拒答率(应拒答) | 0.9375 | 0.9375 | **0** |
| 误拒率(有答案) | 0.0263 | 0 | +0.0263 |
| 引用覆盖率 | 0.8030 | 0.8497 | **−0.0467** |
| 文号合规率 | 0.9444 | 0.9444 | **0** |

⇒ P3（下发相关度信号 + 两态 prompt）**无可测增益**，引用覆盖率反而下降。

> ⚠️ **中文答案层的最大盲区**：最后一次答案层跑批是 **09-15**（纯向量时代）。
> **09-16 的混合检索+重排（`recall@6` 0.5623 → 0.7930）之后，中文答案层从未重跑。**
> 即"检索 +23pt 到底有没有传导到答案层"在中文侧**没有数据**。
> （英文侧跑过：见 §3，结论是"没有传导"。）
> ⚠️ 这 4 份 PDF/JSON 位于归档包 `.workbuddy/archive/20260916-rag-answer-quality.tar.gz`，
> 工作区 `eval/results/` 只有 2 题的探针残留 `ask-baseline-20260915-184655.json`（**不可引用**）。

---

## 3. 英文泛化赛道：每次实现的配置与指标

语料 MultiHop-RAG 609 篇英文新闻 / **4195 块**（`EN 1800/100`）；空间 `2095544464810774534`。

### 3.1 检索层

| # | runId | 时间 | 题集 | 切分 | embedding | 检索 | 重排 | `recall@6` | `docRecall@6` | `hitRate@6` | `mrr` |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | `mhr-20260916-184519` | 09-16 18:45 | 全量 2556（2255 计分） | EN 1800/100 | 1024维 | **纯向量**，fetchK=10 | 无 | **0.4555** | 0.6121 | 0.7996 | 0.5654 |
| 2 | `mhr-20260916-231503` | 09-16 23:15 | 20 题烟雾测试 | 同 | 1024维 | 混合（改造后） | ✅ | 0.6716 | 0.7010 | 0.9412 | 0.8588 |
| 3 | `mhr-20260916-233042` | 09-16 23:30 | **前 600（521 计分）** | 同 | 1024维 | 混合（改造后） | ✅ | **0.7121** | 0.7815 | 0.9635 | 0.8320 |
| 4 | `mhr-20260917-145506` | 09-17 | 固定 600（529 计分） | 同 | 1024维 | 混合 + **多查询 ON**（整理 OFF） | ✅ | **0.7187** | 0.7826 | 0.9698 | 0.8344 |
| 5 | `mhr-20260917-153446` | 09-17 | 固定 600（529 计分） | 同 | 1024维 | 混合 + **证据整理 ON**（多查询 OFF） | ✅ | **0.7650** | 0.8218 | 0.9849 | 0.8649 |

- 第 2 行是**烟雾测试，禁用引用、非基线，禁止引用其数字**。
- 第 1 行 vs 第 3 行：`recall@6` **+24.4pt**，与中文侧同改造 +23.1pt 量级一致 ⇒
  收益来自「混合检索 + 重排」本身，**不是中文公文号特化**（英文无文号钉位加成）。
  ⚠️ 但两行题集不同（2556 vs 前 600），严格而言第 1 行只能与第 3 行**同子集**比。
- 第 4 / 5 行 vs 第 3 行：需注意题集与 `fetchK` 都变了（10 → 50），
  且 `feature/optimize-rag-query-and-chunk-merge开发` 的结论是
  **多查询无可测增益（+0.26pt）**、**证据整理 +4.63pt 但含"关掉多查询"的成分**。

### 3.2 答案层（**唯一有"正确率"的地方**，判官 `qwen3.8-flash` 三分类）

固定 100 题 `anchor/mhr-answer-100-fixed.jsonl`（comparison 34 / inference 32 / temporal 22 / null_query 12）。

| runId | arm | 时间 | 检索侧 | 生成模型 | **正确率** | 上沿 | 有答案题正确率 | 误拒率 | 引用覆盖率 | 平均耗时 |
|---|---|---|---|---|---|---|---|---|---|---|
| `mhr-ask-20260917-155551` | `baseline-off` | 09-17 15:55 | 混合+重排，**两开关 OFF** | `deepseek-chat` | **0.6500** | 0.8000 | 0.6023 | 0.0568 | 0.5993 | 4359ms |
| `mhr-ask-20260917-154433` | `evidence-assembly-only` | 09-17 15:44 | 混合+重排，**整理 ON** | `deepseek-chat` | **0.6500** | 0.7700 | 0.6023 | 0.1023 | 0.6072 | 4311ms |
| `mhr-ask-20260920-212222` | `qwen38max-nonthinking` | 09-20 21:22 | 混合+重排，两开关 OFF | **`qwen3.8-max`**（`enable_thinking=false`） | **0.7200** | 0.8000 | 0.6818 | 0.2500 | 0.5171 | 8671ms |
| （`qwen38max-thinking`） | — | 09-20 | 同 | `qwen3.8-max`（开思考） | **无数据** | — | — | — | — | — |

- 第 1 / 2 行结论：**检索层 +4.6pt 完全没有传导到答案层**，任务判据「答案正确率提升」**未达成**。
- 第 3 行结论：**+7pt 不显著**（McNemar p=0.1671）、**INCORRECT 20→20 一道未修好**、
  误拒率显著恶化、引用覆盖率显著下降、耗时 ×1.99。
- 第 4 行（思考臂）**无数据**：阻塞于 `RagAskServiceImpl:66` 硬编码的 120s SSE 上限，
  跑到 14/100 主动止损。详见 `results/ask-qwen38max-nonthinking/REPORT.md` §5。
- 判官一致性：三次 run 判官均为 `qwen3.8-flash`、temperature 0；
  但**第 3 行的回答模型与判官同家族**（`qwen3.8-max` 答 / `qwen3.8-flash` 判）⇒ +7pt 须打折看。

---

## 4. 一句话总结（可作为对外口径）

**检索层的 24pt 级收益来自"混合检索（向量+BM25/RRF）+ `qwen3.7-text-rerank` 重排"，
它在两条赛道复现（+23.1pt / +24.4pt）；但同样的收益在答案层两次测量中都没有显现 ——
无论开不开"证据整理"，还是换 `qwen3.8-max` 更强的生成模型，答案正确率都没有拿到统计显著的提升。**

---

## 附：本台账的复算方式

```bash
# 全部 run 的题量/指标/文件时间（只读，不改任何文件）
python eval/scripts/list_run_ledger.py            # 两条赛道
python eval/scripts/list_run_ledger.py --dataset mhr

# 答案层两臂差异归因（迁移矩阵 + McNemar + 误拒校准 + 配对检验）
python eval/scripts/diag/diag_arm_attribution.py                  # 默认复现 §3.2 那次对比
python eval/scripts/diag/diag_arm_attribution.py <基线.json> <实验臂.json>

# 拒答判据取证（误拒构成 / 逐题分类 / 判据决策路径 / 答案原文）
python eval/scripts/diag/diag_false_refusal.py
python eval/scripts/diag/diag_refusal_triage.py
python eval/scripts/diag/diag_refusal_path.py                     # 复现 4 例误伤的决策分支
python eval/scripts/diag/diag_refusal_evidence.py                 # 逐题打印答案原文

# 以上脚本均按仓库根相对路径读 run 产物，请在仓库根目录执行

# 中文答案层 4 份基线在归档包内，引用前先解包
tar -xzf .workbuddy/archive/20260916-rag-answer-quality.tar.gz -C <临时目录>
```
