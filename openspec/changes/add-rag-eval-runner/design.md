# add-rag-eval-runner Design

## Context

`golden.v1.jsonl` 已冻结：99 题 / 390 gold 段 / 五类齐全，gold 一律用 `docId + chunkIndex` 逻辑坐标。
缺的是把这把尺子压到真实系统上读出数字的一段代码。

已核实的后端契约（`OpenRagController` / `RagSearchService`，均未改动）：

```
POST /open/rag/search        Header: X-API-Key
请求  { query, spaceIds?: number[], topK?: number }
响应  { hits: [{ chunkId, docId, spaceId, chunkIndex, chunkHeading, chunkText,
                 docTitle, docNumber, score }],
        effectiveSpaceIds: number[], authorizedDocCount: number }
```

三个决定设计的既有事实：

1. `ChunkHit` 直接带 `docId` + `chunkIndex` —— 与 gold 坐标**同构**，比对零映射
2. `RagSearchServiceImpl` 第一步就做权限过滤（`visible ∩ requested`），向量库只看到过滤后的集合；
   换 key = 换 owner = 换可见空间 —— **D 类泄漏率实验天然可做**
3. 检索内含一层**文号精确匹配**（注释：纯向量对文号不敏感，`〔2026〕24号` 会排在 14/6/34 号之后），
   B 类文号题正好用来验证这层混合检索是否真的有效

## Goals / Non-Goals

**Goals**：给出可复现的检索层指标，使切块参数、embedding 模型、检索策略的任何改动都能前后对比。

**Non-Goals**：评测 LLM 答案生成质量（需 SSE + 另一套判据）；改造切块或检索；执行 embedding 全量回填；
修改 `golden.v1.jsonl`。

## Decisions

### 决策 1：主路径调 HTTP 接口，不重写检索

**选择**：`HttpRetriever` 调 `POST /open/rag/search`。

**备选**：Python 端连库自己算余弦（`OfflineRetriever`）。

**理由**：对外引用的指标必须来自真实系统，否则测的是"我复现的检索算法"而不是"项目实现的检索"。
离线模式保留，但定位是**降级与 embedding 选型工具**（可一次对比多个模型，不必反复物理删库重建）。

**约束**：两种模式的分数**不可混合平均**，结果 JSON 必须标注 `retriever` 与 `embeddingModel`。

### 决策 2：一次请求 topK=10，本地截断算多组 K

一次检索取 10 条，在本地截断计算 K ∈ {1, 3, 5, 6, 10} 的全部指标。
99 题只需 99 次请求（D 类双跑则 +10 次），而不是 5 × 99 次。
`rag.retrieval.top-k` 当前默认 6，正好落在集合内。

### 决策 3：指标定义（精确）

记 `G` = 该题 gold 集合（`(docId, chunkIndex)` 对），`H_K` = 前 K 条命中的坐标集合，`|G| > 0`。

| 指标 | 定义 | 说明 |
|---|---|---|
| `recall@K` | `\|G ∩ H_K\| / \|G\|` | chunk 级，区分度最高，但对切分策略敏感 |
| `docRecall@K` | `\|{d : ∃(d,c)∈G} ∩ {d : ∃(d,c)∈H_K}\| / \|{d : ∃(d,c)∈G}\|` | 文档级，**最鲁棒**——切分策略变了也能比 |
| `mrr` | `1 / rank`，rank = 首个命中 gold 的位次（1-based）；未命中记 0 | 衡量"正确答案排第几" |
| `hitRate@K` | `1 if \|G ∩ H_K\| > 0 else 0` | 最宽松，"找没找到" |

**C 类（gold 为空）不参与上述计算**，单独统计（见决策 4）。
**D 类（gold 为空，靠 forbiddenDocIds）单独统计**（见决策 5）。

**不做 NDCG**：NDCG 需要分级相关性标注（0/1/2/3），`golden.v1.jsonl` 只有"命中/未命中"二值，
硬做只能退化为 DCG，信息量不比 MRR 多。

### 决策 4：C 类不拍阈值，只报分布

C 类 `expectRefusal=true` 但 `gold=[]`，`recall` 分母为 0，无法算。
若强行定义"score < T 视为正确拒答"，阈值 T 无标定依据，等于编造指标。

**改为输出可解读的事实**：
- `nonEmptyReturnRate`：返回非空结果的题占比（= 幻觉诱导风险面）
- `top1Score` 的分位数分布（min / p25 / median / p75 / max）
- 逐题明细：`evidenceKey` / `outOfScopeReason` / 返回条数 / `top1Score`

**实施纠正**：design 初版设想按 `meta.unanswerableType` 分 `fictional_docnum` / `corpus_gap` /
`near_miss` 三个子类型，实际核对 `golden.v1.jsonl` 后发现**该字段并未落地**
（C 类 meta 只有 `qType` 且全为 `unanswerable`，另有 `outOfScopeReason` 自由文本）。
按 ID 区间硬凑分组会制造不存在的事实，故改为逐题列明细。

结论在报告里人工解读，不在代码里下判断。

### 决策 5：D 类做双向对照

D 类 `meta.mirrorOf` 指向被镜像的 A/B 题，因此可以借镜像题的 gold 做成员侧验证：

- **成员 key**：应当命中镜像题的 gold → `mirrorRecall@K`（证明权限没误伤正常检索）
- **非成员 key**：`forbiddenDocIds` 中任何一个出现在结果里即**泄漏**，一票否决

两个 key 各跑一次，D 类单独出 `leakCount` / `leakRate` / `mirrorRecall@K`，**不进 overall 平均分**
（沿用 `add-rag-eval-dataset` design 第 82 行的既有约定）。

### 决策 6：后端不可用时 fail fast，绝不静默降级

本项目踩过两次"假成功"（`stop-dev.ps1` 静默失败、`updateById` 跳过 null 字段假成功），
runner 必须显式避开同类陷阱：

- 开跑前先发一次探测请求；连接失败 → `exit 1`，打印"后端未启动，请先执行 `start-dev.ps1`"后退出
- 连续 5 次请求失败 → 中止整轮
- 收尾自检：若 `overall.recall@6 == 0` 且 `errorCount > 0` → 结果 JSON 标记 `invalid: true`，
  报告顶部写"环境故障，此轮结果不可用"，**不允许把故障跑出的 0 分当指标报出去**

### 决策 7：结果可复现

结果 JSON 记录 `goldenSha256`、`topK`、`retriever`、`embeddingModel`、起止时间、后端 base-url（不含密钥）。
同一份 golden + 同一配置重跑，应得到同一组数字（检索无随机性）。

## Risks / Trade-offs

- **Recall 偏低是预期的**：GT 只绑政策原文，命中解读 chunk 计为未命中（dataset design 第 44 行的既定约定）。
  数字不好看但真实，报告中要写明这一口径，避免被误读为"系统很差"
- **offline 模式的向量维度**：库内存量是 Ollama `qwen3-embedding:4b`（2560 维），
  若用 DashScope（1024 维）算 query 则维度不匹配 → offline 模式必须自己把 chunk 全量重算
  （2061 chunk ÷ 10 = 约 207 次批量请求），成本可控但需缓存到本地
- **D 类依赖两个 owner 的 key**：制备失败时 D 类标记 `skipped`，不混入总分

## Open Questions

1. **embedding 切换时机**：Ollama 2560 维 → DashScope `qwen3.7-text-embedding-flash` 1024 维的
   全量回填，是否在基线跑通后立刻做？本 change 只负责切换前后各跑一次做对比，不负责回填本身
2. **C 类是否需要阈值**：当前不给。若后续答案层评测（SSE）接入，可结合"是否真的拒答"再定
3. **是否需要 `--repeat` 验稳定性**：检索无随机性，暂不需要；若引入 rerank 或采样策略则需重跑多次

## Implementation Plan

1. `eval/scripts/prepare_keys.py`：在库内制备成员/非成员两个 key（sha256 入库）
2. `eval/scripts/run_eval.py`：适配器 + 指标 + 自检 + 产物
3. 跑基线 → `eval/results/baseline-<ts>-http.json` + `eval/audit/baseline-report.md`
