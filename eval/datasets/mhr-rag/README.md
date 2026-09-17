# mhr-rag —— MultiHop-RAG 泛化评测数据集

## 定位

用公开多跳问答数据集 **MultiHop-RAG**（609 篇英文新闻 + 2556 题）测本系统检索管线的
**跨领域泛化能力**。与自有 216 篇重庆政务语料评测（`eval/golden.*.jsonl`）**完全隔离**。

## 隔离原则（重要）

| | 自有数据集 | mhr-rag（本目录） |
|---|---|---|
| 位置 | `eval/` 根目录（`golden.v1/v2.jsonl`、`results/`、`audit/`） | `eval/datasets/mhr-rag/` |
| 语料 | 216 篇中文政务 Markdown（F 盘） | 609 篇英文新闻 JSON |
| 脚本 | `eval/scripts/`（`generate_*.py` / `build_pairs.py` / …） | `eval/datasets/mhr-rag/scripts/` |
| 产物 | `eval/results/` | `eval/datasets/mhr-rag/results/` |
| 改动影响 | 改一个不会碰另一个 | 同左 |

**唯一共享的是"怎么量"，不是"量什么"：**

- 共享：`eval/scripts/lib_rag_eval.py` —— 取数适配器（`HttpRetriever`）+ 打分数学
  （`compute_metrics` / `METRIC_KEYS`）。**保持一份**，两个数据集的
  recall / docRecall / hitRate / mrr 定义因此不可能漂移，可横向比较。
- 共享：`eval/scripts/lib_eval.py` —— 环境加载、DB 连接、通用 HTTP。
- 不共享：切分器（真相在后端 Java）、gold 绑定算法、题型体系、报告渲染。

## 数据来源与版本锁定

数据文件默认放 `eval/tmp/`（该目录 **已 gitignore，属易失资产**，历史上被清过一次），
故用 sha256 锁定版本。原始来源：GitHub `yixuantt/MultiHop-RAG`。

| 文件 | 条目 | sha256 |
|---|---|---|
| `mhr_corpus.json` | 609 篇 | `20b61b5ab84de84a927420c5d265b7ec8d859ae49980699958a787ade9e4d28f` |
| `mhr_qa.json` | 2556 题 | `03cfb4926461f868684903aadc8024447bdda5bb3f6804741424cce338515bff` |

可用环境变量 `MHR_DATA_DIR` 指向别处。重新下载后**必须先核对 sha256**，否则历史指标不可比。

字段：
- corpus 条目：`title / author / source / published_at / category / url / body`
- qa 条目：`query / answer / question_type / evidence_list[]`，证据项为
  `title / author / url / source / category / published_at / fact`

## 评测口径（已拍板，改前先想清楚代价）

1. **gold 绑到 chunk 级，不绑文章级。** 上游只给 `url` + 一句 `fact`，`fact`
   逐字是 `body` 子串（实测 6084/6084），把它当自有数据集的 `quote` 用，
   算出 `chunkIndex`，产出**与 `golden.v1` 同形**的坐标
   `{docId, chunkIndex, why, quote}`。→ 复用同一套打分，零新机制。
2. **走 set 去重。** 同一题多条 `fact` 落在同一 chunk 时只算一个坐标，分母相应缩小。
   代价是这些题偏松；收益是与自有数据集口径一致。实测 39 道题出现重复坐标。
3. **`null_query` 是拒答类**（301 题，无证据），不进召回分母，只统计分布。
4. **英文文档走独立切分 profile：上限 1800 / 重叠 100 / 句界 `.!?;`。**
   这不是可选优化 —— 实测上限 600 时有 **55 条证据在任何单块里都找不到**，
   导致即使检索完美，`recall@K` 上限也只有 0.9898；1800 时为 1.0000。
5. **中文 profile 逐字节不变**（600/80/100，句界 `。；`），自有 216 篇的
   2061 个 chunk 文本与向量零变动，中文基线不用重跑。

### 三条必须声明的差异（不声明就等于误读指标）

1. **本数据集测的是纯向量链路。** 后端的「文号精确命中层」
   （`RagSearchServiceImpl` 第 3 步，靠 `MarkdownChunker.DOC_NUMBER_PATTERN`
   匹配中文公文号）对英文查询恒不命中，**在这里是死代码**。
   自有 216 篇测的是「向量 + 文号命中」混合链路 —— 两者不是同一条管线，
   **绝对值不可直接横比**。
2. **每题都是跨文档多跳。** gold 覆盖 2–4 篇文档（实测 {2: 1169, 3: 774, 4: 312}），
   `recall@K` 的语义是"必须召回全部证据文档才满分"。自有 99 题是
   **100% 单文档**（0/75 跨文档），这是本数据集真正新增的能力维度。
3. **`OfflineRetriever` 不适用。** 它只做纯向量余弦、不过权限过滤，
   且不会跟随后端新增的 BM25/重排等算法。本数据集的 baseline 一律走 http 模式。

## 绑定为什么是两步

切分是 **AFTER_COMMIT 异步**的，导入接口返回时 `wiki_chunk` 还没有行。
所以：

```
步骤一  url  -> docId       导入接口逐项结果（BatchImportItemResult.documentId）
                            或按 document_wiki.sourceUrl 反查
步骤二  fact -> chunkIndex  等索引完成后查 wiki_chunk.chunkText（唯一权威）
```

`bind_anchor.py` 一次做完两步；`--step1-only` 可只看导入覆盖情况。

**未绑定必须显式保留**：绑不上的证据记入 `anchor/unbound-report.json`，
**不得**从 gold 里静默删掉 —— 那等于用缩小分母来抬高指标。

## 目录

| 路径 | 说明 |
|---|---|
| `scripts/mhr_lib.py` | 数据集专属约定：路径、题型、sha256、`bind_fact` 绑定算法 |
| `scripts/bind_anchor.py` | 两步绑定，产出 `golden.mhr.jsonl` + anchor map + 未绑定清单 |
| `scripts/run_eval.py` | 评测 runner（import 共享内核），按 `question_type` 分类报指标；支持 `--checkpoint-every` / `--resume` 断点续跑 |
| `scripts/compare_runs.py` | 对比两次 run（纯离线）。支持 `--subset-first N` 同题子集对比 |
| `anchor/` | 绑定产物：`golden.mhr.jsonl`、`anchor-map.json`、`unbound-report.json` |
| `results/` | 本数据集的评测结果（不写进 `eval/results/`） |

## 复现步骤

```bash
# 0) 语料就位并核对指纹（数据在 eval/tmp/，易失）
sha256sum eval/tmp/mhr_corpus.json eval/tmp/mhr_qa.json

# 1) 通过 JSON 导入接口把 609 篇灌进一个独立空间
#    （依赖变更 add-json-document-import，见下"阻塞"）
#    建议用独立空间，避免污染空间级对照；记下返回的 spaceId

# 2) 等异步索引完成后绑定 gold
python eval/datasets/mhr-rag/scripts/bind_anchor.py
python eval/datasets/mhr-rag/scripts/bind_anchor.py --step1-only   # 只看导入覆盖

# 3) 跑基线
python eval/datasets/mhr-rag/scripts/run_eval.py --space-id <spaceId> --probe
python eval/datasets/mhr-rag/scripts/run_eval.py --space-id <spaceId>
```

## 阻塞（已解除）

原先担心"导入 609 篇依赖 `add-json-document-import`（当时 0/45）"——**实测未阻塞**：
609 篇语料已在库（`bind_anchor.py` 步骤一按 `sourceUrl` 反查命中 609/609），
gold 绑定 6084 条全部成功（`unbound=0`），全量/子集均已跑通。

语料落在空间 **`2095544464810774534`**（609 篇 markdown）。
另有一个空间 `2095512793587744770` 含 610 篇同源文档，是导入过程的副产品，
**不要用于评测**——两个空间同时存在会让文档级指标虚高。

## 基线记录（英文 × 改造前 / 改造后，同题对比）

改造 = 混合检索（向量 + BM25 → RRF 融合，池深 50）+ `qwen3.7-text-rerank` 重排。
**同一批题（golden 前 600 题，其中 521 题计分、79 题 `null_query` 拒答）**，逐题可比：

| 指标 | 改造前（纯向量） | 改造后（混合+重排） | 提升 |
|---|---|---|---|
| `recall@6` | 0.4677 | **0.7121** | **+24.4pt** |
| `docRecall@6` | 0.6200 | 0.7815 | +16.2pt |
| `hitRate@6` | 0.8215 | 0.9635 | +14.2pt |
| `mrr` | 0.5712 | 0.8320 | +26.1pt |

按题型（`recall@6`）：comparison 0.5748→0.8006（+22.6pt）、
inference 0.3703→0.6550（**+28.5pt**）、temporal 0.4276→0.6434（+21.6pt）。

结果文件：
- 改造前全量：`results/mhr-20260916-184519.json`（2556 题，跑于改造提交之前）
- 改造后子集：`results/mhr-20260916-233042.json`（前 600 题）
- `results/mhr-20260916-231503.json` 是 **20 题烟雾测试，不是基线，禁止引用**

### 为什么这个对比能证明"泛化"

中文侧（自有 216 篇，399 题）同样改造是 `recall@6 0.5623 → 0.7930（+23.1pt）`；
英文侧是 **+24.4pt**。两者量级一致，且英文走的是**纯向量链路**（文号层对英文恒不生效），
说明收益来自"混合检索 + 重排"本身，**不是中文公文号钉位带来的语种特化**。

⚠️ 只跑了前 600 题（非全量 2556），故上表数值**不与全量基线直接横比**，
只能与同子集的 A 栏比。题序是打散的（前 200 题类型游程 142），
前 600 题题型占比与全量最大偏差 2.5pt；521 题的 `recall@6` 标准误约 ±2.2pt，
而观测到的提升是 +24.4pt，远超噪声。

## 跑批必须开 checkpoint（血泪）

一次全量 = 2556 题 ×（1 次 embedding + 1 次 50 篇候选的 rerank），约 30 分钟、
数千次付费调用。2026-09-16 首次全量跑到 ~800 题时 DashScope 返回
**`Arrearage`（账号欠费）**，后端连续抛 `RagEmbeddingUnavailableException`，
runner 按设计保护性中止 —— **已跑的 800 题因为没落盘全部作废**。

现在默认每 100 题写一次进度到 `tmp/mhr-ckpt-<runId>.json`（原子替换），
中断后 `--resume` 接着跑，只补没跑的题：

```bash
python eval/datasets/mhr-rag/scripts/run_eval.py --space-id 2095544464810774534 --limit 600
python eval/datasets/mhr-rag/scripts/run_eval.py --space-id 2095544464810774534 \
       --resume tmp/mhr-ckpt-<runId>.json
```

续跑前会校验 `corpusSha256` / `qaSha256` / `spaceId`，不一致直接拒绝——
把两批不同前提的结果拼在一起，比不跑更危险。

## 已知实测数字（用于日后回归对照）

用忠实移植的切分器在本地模拟，对全量 6084 条证据做绑定：

| 切分 profile | 总块数 | 每篇中位/最大块 | 单块唯一命中 | 未绑定 | `recall@K` 上限 |
|---|---|---|---|---|---|
| ZH 600 / 80（若误用于英文） | 13,721 | 17 / 163 | 5953 (97.85%) | **55** | **0.9898** |
| EN 1800 / 100（本数据集采用） | 4,195 | 5 / 44 | 6068 (99.74%) | **0** | **1.0000** |

其他实测：`url` 与 `title` 均 609/609 唯一；标题超 128 的 22 篇，截断后仍无碰撞；
`body` 609/609 含 `\n\n`、0/609 含 markdown `#`（故 `chunkHeading` 恒为空串，
`docNumber` 恒 NULL）；段落 28,711 段，超 600 的 4.6%、超 1800 的 0.15%。

切分器在重叠前缀与正文之间注入了一个 `\n`，会打断跨边界 fact 的精确子串匹配，
故 `bind_fact` 失败后会退到**去空白归一化**匹配，再按"匹配点是否落在开头重叠区"
消歧。这套规则在上表两列都恰好复现了预期数字。
