# 提案：boost-rag-passage-recall

## Why

**目标**：把切片级召回 `recall@6` 从 **0.5623** 提升到 **≥0.85**（对外口径 `eval/results/baseline-20260915-091440-http.json`，399 题宏平均）。

**结论先行：本 change 已实现 +23.1pt（0.5623 → 0.7930），但未达 0.85，差 5.7pt。**
差距的成因已定位到机制层（见 `design.md` §2.5），不是"再调调参"能补的（详见下方"为什么 0.85 没到"）。

### 优化前的问题

| 口径 | 值 | 说明 |
| --- | --- | --- |
| 基线 `recall@6` | **0.5623** | 归档基线；本分支复跑 0.5636（Δ0.13pt，在重跑极差内） |
| 基线 `docRecall@6` | 0.8697 | 文档级召回 |
| 完美排序 `recall@6` 上限 | **0.9783** | 全库 2061 块、金标锚点不变，任何排序算法都到不了 1.0 |
| 纯向量候选池 oracle@50 | 0.8148 | 「只重排现有 top-50 就完美」的上限——**已低于目标** |
| 混合候选池（向量 + BM25）oracle@50 | **0.8600** | 加词法通道后上限才越过 0.85 |
| 混合候选池 oracle@100 | **0.9061** | |

**0.85 不是"再加点力气"就能到的数**：它要求把全库可用空间吃掉 86%
（(0.85−0.5623)/(0.9783−0.5623)）。`docRecall@6 0.8697` 与 `recall@6 0.5623` 之间那 30.7pt 的落差
说明**文档基本找到了、段落排错了**——病灶在排序层，因此候选池深度与排序质量必须同改：
纯向量池 top-50 上限只有 0.8148，**连目标都够不到**，必须补一条词法通道。

### 优化方向与实测增益

| 手段 | 实测 `recall@6` | 说明 |
| --- | --- | --- |
| 现状（纯向量 top-6） | 0.5636 | 基线复跑 |
| `gte-rerank-v2` over 纯向量池 top-50 | 0.5992 | 只吃到 14.2% 可用空间 → **否决"只加重排"** |
| LLM listwise 重排（deepseek）over 混合池 top-50 | 0.6690 | 慢 20 倍（~6.6s/次）且更差 → **否决** |
| **混合检索（向量 + BM25 + RRF）+ `qwen3.7-text-rerank` over top-50** | **0.7930** | **采纳**（+23.1pt） |
| 同上但候选池 top-100 | **0.8030** | 多 1.0pt、多 190ms → 默认池深取 **50** |

**`qwen3.7-text-rerank` 是本次的决定性选型**（与既有千问 key 同源）。
同文档集上相关文档得分 **0.9973**，而 `gte-rerank-v2` 只有 0.5897；无关文档被稳定挤出 top-3。
（注：`qwen3-reranker-8b/4b/0.6b` 在该 key 上返回 `Model not exist`，不可用。）

### 为什么 0.85 没到（机制层结论）

换到 `qwen3.7-text-rerank` 后，**"重排器在候选池内挑对 6 篇"的能力随池深显著衰减**：

| 池深 N | 混合池 oracle@N | 实测 `recall@6` | 吃到上限 |
| --- | --- | --- | --- |
| 20 | 0.7690 | 0.7590 | **98.7%** |
| 50 | 0.8600 | 0.7930 | **92.2%** |
| 100 | 0.9061 | 0.8030 | **88.6%** |

池深 20 时重排器几乎压满上限（98.7%），但从 20 推到 50 时，**新增的空间没被"挑得更好"吃掉，
而是被"在 50 篇里挑 6 篇的难度"吃掉了**。所以从 0.7930 到 0.85 需要**双重推进**
（继续加深池子 × 在大池里做更精细的排序），单加池深边际递减（50→100 只 +1.0pt）。
这条结论决定了后续路线，已写入 `design.md` §5 风险。

### 延迟（与召回并列的验收维度）

| 池深 | 端到端 mean | p50 | p95 | p99 | 重排 mean | embed mean | 吞吐 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 50（默认） | **516ms** | 510ms | **600ms** | 692ms | 336ms（占 68%） | 136ms | 1.94 qps |
| 100 | 720ms | 713ms | 928ms | 1343ms | 559ms | 133ms | 1.39 qps |

**延迟结构**：`embed`(136ms) + `rerank`(336ms) = 472ms，占端到端 516ms 的 **91%**；
本地检索本体（向量 2.2ms + BM25 0.7ms + 融合 0.0ms）**合计不到 4ms**。
即：延迟几乎全部是两次远程 API 调用，**扩池子的代价直接压在重排耗时上**。默认池深定稿 50
（50→100 只多 1.0pt 却让 p95 从 600ms 涨到 928ms，性价比不成立）。

## What Changes

1. **新增词法检索通道**：在既有内存向量库同一份语料上实现中文 BM25（CJK 用 bigram 切词，无需外部分词器、无需 DDL），与向量通道做 RRF 融合，形成混合候选池。混合池 oracle@50 由 0.8148 → **0.8600**。
2. **修复文号精确层的三点缺陷**（`RagSearchServiceImpl`）：
   - 去掉 `orderByAsc("chunkIndex")` 的位置排序语义 → 改为钉位候选组 + 组内按相关性排序；
   - 修复 `if (hits.size() < topK)` 导致文号层填满后**向量检索整段被跳过**的真实召回损失；
   - 文号组**钉住前几位**，不进入外层 RRF（实测 RRF 会把 B 类从 0.7506 稀释到 0.4129）。
3. **候选池与 topK 治理**：候选池深度（默认 50）与返回条数解耦，`topK` 加上限；池深不得浅于答案集。
4. **重排接入**：新增 `RagRerankClient`（DashScope `qwen3.7-text-rerank`），对混合候选池重排后再截 top-K；未配置或调用失败时**静默回退**到融合排序，检索绝不因此失败。文号钉位组**只在其内部重排**且组内成员只增不减。

### 非目标

- 不改切块策略、不改 embedding 模型、不动 `golden.v2.jsonl` 的 gold 锚点（全程使用 `docId + chunkIndex` 逻辑坐标）。
- 不做答案层改造（`RagAskServiceImpl` 的 prompt / 拒答逻辑不在本 change 范围）。
- 不以"调评测集参数"的方式刷分：所有阈值只允许用非评测数据确定。

## Acceptance Criteria

| # | 判据 | 结果 |
| --- | --- | --- |
| AC1 | `recall@6 ≥ 0.85`（399 题，`golden.v2.jsonl`） | **未达标**：实测 0.7930（池深 50）/ 0.8030（池深 100） |
| AC2 | 相对基线不得倒退，且增益有数据支撑 | **达标**：0.5623 → 0.7930，**+23.1pt** |
| AC3 | `docRecall@6` / `hitRate@6` / `mrr` 同步改善 | **达标**：0.8697→0.9223 / 0.6842→0.9048 / 0.4486→0.6262 |
| AC4 | 权限无泄漏（`leakCount = 0`） | **达标**：0 泄漏，0 错误，`invalid=false` |
| AC5 | 平均响应时间不得显著劣化 | **达标**：端到端 mean 516ms、p95 600ms；本地检索本体 <4ms，开销集中在 embed+rerank 两次远程调用 |
| AC6 | 增强通道任一失效时检索仍可用 | **达标**：重排关闭/异常均回退到融合排序，接口不报错（含单测覆盖） |

## Capabilities

### New Capabilities

- `wiki-rag-retrieval`：RAG 检索的候选生成与排序——多通道候选生成（向量 + 词法）、融合排序、文号精确层的钉位语义、候选池与返回条数分离、重排适配器与失败回退。

### Modified Capabilities

（无。`wiki-rag-pipeline` 现有需求描述的是切块、索引生命周期与权限过滤，本 change 不改变其语义；检索排序的新增契约独立成 `wiki-rag-retrieval`。）

## Impact

- 后端：新增 `cloud/src/main/java/com/et/cloud/rag/` 下 `LexicalIndex.java`（BM25 + bigram 切词）、`RrfFusion.java`、`RagRerankClient.java`、`RagRerankUnavailableException.java`；修改 `RagSearchServiceImpl`（文号层钉位、候选池、融合、重排）、`RagProperties`（`candidatePoolSize` / `fusionPoolSize` / `lexical` / `fusion` / `rerank` 配置节）、`SearchTimings`（新增 lexical/fusion/rerank 阶段）、`WikiRagIndexServiceImpl`（索引变更同时通知词法索引）、`application.yml`。
- 前端：无（`/open/rag/search` 与站内 `/rag/search` 出参结构不变）。
- 测试：新增 `LexicalIndexTest`(13) / `RrfFusionTest`(9) / `RagRerankClientTest`(12)，重写 `RagSearchServiceImplTest`(21)；后端全量 **285/285 全绿**。
- 评测：`eval/` 不改打分口径；新增 `eval/scripts/bench_rag_latency.py`（召回-延迟权衡量化工具）。
- 成本与依赖：词法与融合为**本地计算、零外部调用**；重排与 embedding 依赖 DashScope 额度。
- **已知未处理项**：`ChunkHit.score` 语义在改动后被静默改变（原为余弦相似度，现为融合 RRF 分；重排启用时顺序与 score 不再单调对应）。后端与前端均未消费该字段，风险有限，已记入 `IssueLog.xlsx`，建议改为回填最终排序阶段的相关度。
