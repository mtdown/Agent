# 提案：boost-rag-passage-recall

## Why

**目标**：把切片级召回 `recall@6` 从 **0.5623** 提升到 **≥0.85**（对外口径 `eval/results/baseline-20260915-091440-http.json`，399 题宏平均）。

**本轮先做了可行性定界，结论必须先说清楚**（详见 `design.md` 的证据链）：

| 口径 | 值 | 说明 |
| --- | --- | --- |
| 当前 `recall@6` | **0.5623** | 归档基线；本分支复跑 0.5636（Δ0.13pt，在重跑极差内） |
| 完美排序 `recall@6` 上限 | **0.9783** | 全库 2061 块、金标锚点不变，任何排序算法都到不了 1.0 |
| 当前向量候选池 oracle@50 | 0.8148 | 「只重排现有 top-50 就完美」的上限 |
| 当前向量候选池 oracle@100 | 0.8532 | 恰好刚过 0.85 |
| 混合候选池（向量 + BM25）oracle@100 | **0.9061** | 加词法通道后上限明显抬升 |

也就是说：**0.85 不是"再加点力气"就能到的数，它要求把可用空间吃掉 86%**（(0.85−0.5623)/(0.9783−0.5623)）。
重排类模型只负责「从候选里挑」，所以候选池的天花板必须先过 0.85 —— 纯向量池在 top-50 只到 0.8148，**必须补一条词法通道**。

**实测得到的增益（离线原型，均在同一 399 题、同一打分口径下）**：

| 手段 | 实测 `recall@6` | 候选池 oracle 变化 |
| --- | --- | --- |
| 现状（纯向量） | 0.5636 | oracle@50 0.8148 / oracle@100 0.8532 |
| 向量 + BM25 经 RRF 融合 | **0.5957**（+3.2pt） | oracle@50 **0.8600** / oracle@100 **0.9061** |
| `gte-rerank-v2` 重排纯向量池 top-100 | 0.5999 | 只吃到 12.5% 可用空间 |
| LLM listwise 重排 | **未能测完** | DashScope 账户欠费中断 |

**阻塞项（必须先解决才能继续）**：DashScope 账户已欠费，`chat` / `gte-rerank-v2` / `embeddings` 三个接口全部返回
`Arrearage`。后端检索必须调 embedding 生成查询向量，因此**当前连一轮评测都跑不了**，所有依赖模型调用的改进手段全部停摆。

## What Changes

（按"已获批价值"排序；1~3 不依赖外部模型，4 依赖额度恢复）

1. **新增词法检索通道**：在既有内存向量库同一份语料上实现中文 BM25（CJK 用 bigram 切词，无需外部分词器、无需 DDL），与向量通道做 RRF 融合，形成混合候选池。实测把候选池 oracle@50 从 0.8148 抬到 0.8600，top-6 实测 +3.2pt。
2. **修复文号精确层的三点缺陷**（`RagSearchServiceImpl:130-155`）：
   - 现状 `orderByAsc("chunkIndex")` → 返回的是"该文档位置最靠前的 N 块"，与相关性无关（B 类 `recall@6` 结构性上限低至 6/33=0.18）；
   - 现状 `if (hits.size() < topK)`（L103）→ 文号层填满 topK 后**向量检索整段被跳过**，是一处真实召回损失；
   - 文号层必须**钉住前几位**而不是被 RRF 稀释（原型实测 RRF 会把 B 类从 0.6525 拉到 0.4129）。
3. **候选池与 topK 治理**：候选池深度与返回条数解耦（检索取 `candidatePoolSize`，返回 `topK`），`topK` 加上限，避免 prompt 被稀释。
4. **重排接入（额度恢复后启用）**：新增可插拔 rerank 适配器（DashScope `gte-rerank-v2`），对混合候选池重排后再截 top-K；未配置或调用失败时**静默回退**到融合排序，检索绝不因此失败。

### 非目标

- 不改切块策略、不改 embedding 模型、不动 `golden.v2.jsonl` 的 gold 锚点（全程使用 `docId + chunkIndex` 逻辑坐标）。
- 不做答案层改造（`RagAskServiceImpl` 的 prompt / 拒答逻辑不在本 change 范围）。
- 不以"调评测集参数"的方式刷分：所有阈值只允许用非评测数据确定。

## Capabilities

### New Capabilities

- `wiki-rag-retrieval`：RAG 检索的候选生成与排序——多通道候选生成（向量 + 词法）、融合排序、文号精确层的钉位语义、候选池与返回条数分离、重排适配器与失败回退。

### Modified Capabilities

（无。`wiki-rag-pipeline` 现有需求描述的是切块、索引生命周期与权限过滤，本 change 不改变其语义；检索排序的新增契约独立成 `wiki-rag-retrieval`。）

## Impact

- 后端：新增 `cloud/src/main/java/com/et/cloud/rag/LexicalIndex.java`（BM25 + bigram 切词）、`RrfFusion.java`、`RagRerankClient.java`；修改 `RagSearchServiceImpl`（文号层钉位、候选池、融合）、`RagProperties.Retrieval`（新增 `candidatePoolSize` / `lexical` / `fusion` / `rerank` 配置节）、`application.yml`。
- 前端：无（`/open/rag/search` 与站内 `/rag/search` 出参结构不变，仅在 `ChunkHit` 上可选新增通道来源字段）。
- 评测：`eval/` 不改打分口径；本轮新增的离线实验脚本只落在 `eval/tmp/`（已 gitignore）。
- 成本与依赖：词法与融合为**本地计算、零外部调用**；重排与 embedding 依赖 DashScope 额度。
