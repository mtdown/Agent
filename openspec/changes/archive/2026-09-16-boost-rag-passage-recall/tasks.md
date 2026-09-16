# 任务清单：boost-rag-passage-recall

> 状态约定：`[x]` 已完成并验证；`[ ]` 未开始；`[~]` 被外部条件阻塞（原因写在条目内）。
> 本轮结论：**1~3 组全部落地并验证，4 组评测已完成（四档池深），5 组待负责人确认后上传。**
> **达标判定：`recall@6` 0.5623 → 0.7930（+23.1pt），未达 0.85（差 5.7pt）。** 详见 §4 与 `design.md` §3.5。

## 0. 前置：可行性与上限定界（已完成，证据见 design.md §3）

- [x] 0.1 建任务分支 `feature/rag-recall-boost开发`（从 `origin/main` @323c86c 切出并推送，本地跟踪）
- [x] 0.2 复跑基线确认可比：`recall@6 = 0.5636` vs 归档 0.5623（Δ0.13pt，在重跑极差内）
- [x] 0.3 深取候选（`topK=200`）产出逐题排名：`eval/tmp/diag-rankings.json`（399 题）
- [x] 0.4 计算完美排序上限与 oracle 曲线：上限 0.9783；纯向量 oracle@50=0.8148、oracle@100=0.8532
- [x] 0.5 定位病灶：126 道恒零题中 82% 是「gold 文档已在前 10、段落选错」
- [x] 0.6 实测混合通道收益：向量+BM25(RRF) → oracle@50 **0.8600**、oracle@100 **0.9061**
- [x] 0.7 实测 `gte-rerank-v2` 重排：top-100 = 0.5999，仅吃到 12.5% 可用空间 → 否决"只加重排"
- [x] 0.8 确认文号层必须钉位：RRF 融合会让 B 类 oracle@50 从 0.7506 掉到 0.4129
- [x] 0.9 选型对比并定稿重排模型：`qwen3.7-text-rerank`（相关文档得分 0.9973 vs gte 0.5897）；LLM listwise 0.6690 且慢 20 倍 → 否决

## 1. 词法通道与融合（无模型依赖）

- [x] 1.1 新增 `LexicalIndex`：在已授权语料的 chunk 上构建倒排 + BM25 打分；CJK bigram / 英文按词；随 `onChunksChanged` 失效重建
- [x] 1.2 新增 `RrfFusion`：多路有序列表按 RRF 融合（常数 60，可配），输出去重有序候选
- [x] 1.3 单元测试：bigram 切词、BM25 单调性、RRF 融合顺序、空通道输入不抛异常 —— `LexicalIndexTest` 13 例 + `RrfFusionTest` 9 例全绿

## 2. 检索链路改造

- [x] 2.1 `RagProperties.Retrieval` 新增 `candidatePoolSize`（**定稿 50**）、`fusionPoolSize`（50）、`topKMax`（50）与 `lexical` / `fusion` / `rerank` 子配置
- [x] 2.2 `RagSearchServiceImpl`：候选池深度与 `topK` 解耦；`topK` 加上限截断；池深不变式 `poolSize = max(candidatePoolSize, topK)`
- [x] 2.3 `RagSearchServiceImpl` 文号层：去掉 `orderByAsc("chunkIndex")` 的位置排序语义，改为钉位候选组 + 组内按相关性排序
- [x] 2.4 修复 `if (hits.size() < topK)` 导致文号层填满后**跳过向量检索**的缺陷
- [x] 2.5 `SearchTimings` 增加 lexical / fusion / rerank 阶段（未执行留空，**不写 0**）
- [x] 2.6 单元测试：文号钉位优先于融合结果、文号层不再吞掉语义通道、topK 超限被截断、各增强通道关闭时行为降级 —— `RagSearchServiceImplTest` 重写为 21 例
- [x] 2.7 修复实现缺陷：`orderDocNumberGroup` 在重排返回索引数少于组内文档数时**静默丢组成员** → 改为按原顺序补回未引用成员（组只增不减）
- [x] 2.8 修复实现缺陷：`SearchTimings.vectorMs/lexicalMs` 在通道未执行时误写 0，与"未执行留空"契约矛盾
- [x] 2.9 `WikiRagIndexServiceImpl` 索引变更同时通知词法索引；补 `WikiRagIndexServiceImplTest` 注入（修复被 NPE 掩盖的 3 个用例）

## 3. 重排接入

- [x] 3.1 新增 `RagRerankClient`（DashScope `qwen3.7-text-rerank` 原生 rerank 端点），超时/异常统一转 `RagRerankUnavailableException`
- [x] 3.2 接入 `RagSearchServiceImpl`：融合候选池 → 重排 → 截 `topK`；失败静默回退并记录；文号组仅组内重排
- [x] 3.3 重排输入带上文档身份：`《标题》+ 标题路径\n + 正文`
- [x] 3.4 单元测试：重排成功改序、关闭时跳过、异常时回退且接口仍成功 —— `RagRerankClientTest` 12 例（`HttpServer` 打桩）全绿
- [x] 3.5 后端全量回归：**285/285 用例 0 失败**

## 4. 评测验证（已完成，四档池深）

- [x] 4.1 起服务并跑通 `run_eval.py --probe` —— 后端 8123 / 前端 3000 正常监听
- [x] 4.2 http 模式跑官方 runner，四档全部完成（每轮 423 题、0 错误、`invalid=false`、`leakCount=0`）
      - 基线 `baseline-20260915-091440-http.json` → `recall@6` 0.5623
      - pool=20 `baseline-20260916-200304-http.json` → **0.7590**
      - **pool=50（默认）`baseline-20260916-201335-http.json` → 0.7930**
      - pool=100 `baseline-20260916-202351-http.json` → **0.8030**
- [x] 4.3 产出分组对比：`crossdoc` 0.4064 → **0.8090**（最大单项跃升）；`docnum` 0.6525 → 0.6848；`docRecall@6` 0.8697 → 0.9223；`mrr` 0.4486 → 0.6262；权限 `leakCount = 0`（无回归）
- [x] 4.4 **延迟量化**：新增 `eval/scripts/bench_rag_latency.py`，单并发 120 题
      - pool=50：端到端 mean **516ms** / p95 **600ms**；重排 mean 336ms（占 68%）、embed 136ms
      - pool=100：端到端 mean 720ms / p95 928ms；重排 mean 559ms
      - 结论：本地检索本体（向量+BM25+融合）合计 <4ms，延迟 91% 来自 embed+rerank 两次远程调用 → 默认池深定稿 50
- [x] 4.5 回填实测数字到 `proposal.md` / `design.md` / `EVAL-REFERENCE.md`，**明确写出 0.7930 与 0.85 的 5.7pt 差距**及机制归因（见 §4.6）
- [x] 4.6 归因分析：**"吃到上限"随池深衰减** —— 池深 20/50/100 分别为 98.7% / 92.2% / 88.6%。
      即加深池子拿到的 oracle 增量未被动兑现（20→50 的 +9.1pt oracle 只兑现 +3.4pt）。
      0.85 需要"池深 × 精排"双重推进，单加池深边际递减（50→100 仅 +1.0pt）
- [x] 4.7 追加分析：**分类别槽位天花板分解**（新增 `eval/scripts/diag_gold_ceiling.py`，纯离线）。
      `pair` 0.8810 / `docnum` **0.7506** / `synthetic`·`crossdoc`·`single` 均 1.0000 → 全体 0.9783。
      **修正一条此前判断**：`docnum` 已兑现 91.2%（实测 0.6848 ÷ 天花板 0.7506），剩余仅 6.6pt，
      均值被 B-06（gold 33 段）/ B-08（gold 25 段）拉死 → **从优化清单移除**。
      `pair` 分层后靶子明确：`|gold|≤6` 的 40 题天花板 1.0 但实测仅 0.55（**45pt 纯系统空间**）
- [x] 4.8 追加分析：**切块粒度对指标本身的污染**（同脚本 ⑥ 段）。
      同一批 hits，评测单元从"切块坐标"换成"连续证据区"（`max_gap=0`）：全体 **0.7930 → 0.8533**
      （`docnum` 1.0000、`pair` 0.6107），而 `single`/`crossdoc`（324 题，占 81%）几乎不变（+2.9~+3.8pt）
      —— 后两者不涨**反证该口径并非放水**。结论：`recall@6` 里约 6pt 是切块粒度造成的**度量失真**
- [x] 4.9 追加分析：**行业参照阈值对照**（design.md §8）。可检索到 `HitRate@3≥90%`、`HitRate@5≥95%`、
      `Recall@10≥0.90`、`MRR≥0.75`、`Context Recall≥0.80~0.85` 等生产建议阈值；
      本系统 `hitRate@3` 0.7794 / `hitRate@5` 0.8647 / `recall@10` 0.8515 / `mrr` 0.6262 **均未达**，
      `docRecall@6` 0.9223 达标 → 定位为「**文档级达标、段落级接近但未达**」

## 5. 收尾

- [x] 5.1 `IssueLog.xlsx` 记录 8 条：启动环境三处障碍、文号组缩水、timings 误导性 0、测试缺依赖、RRF 稀释文号层、runner 无耗时记录、diag 脚本路径、`score` 语义变化（第 152~159 行）
- [x] 5.2 更新 `.workbuddy/memory/` 的项目记忆（oracle 框架、qwen3.7 选型、池深-延迟权衡、沙箱启动障碍）
- [x] 5.3 汇报实际指标与结论（召回 +23.1pt / 未达 0.85 / 延迟 p95 600ms / 机制归因），由负责人决定：
      接受 0.7930 收口，或另立 change 主攻"池深 × 精排"（分组重排 / 查询改写）
      → **负责人 2026-09-16 决策：接受 0.7930 收口**（原 0.85 目标未达成，理由见 design.md §7：
      剩余 5.7pt 中 73.3% 属"池里已有、重排没排进 top-6"，需另立 change 主攻一阶段排序质量）
- [ ] 5.4 经负责人确认后 `upload.ps1` 上传分支并发起 merge request（不自行合并 main）
- [x] 5.5 同步主 spec `wiki-rag-retrieval`（本 change 新增能力，4 条 ADDED requirement 转正）并归档到
      `openspec/changes/archive/2026-09-16-boost-rag-passage-recall/`

## 未纳入本 change（登记备查）

- `ChunkHit.score` 语义变化：改动后写入的是融合 RRF 分（约 0.016 量级），重排启用时顺序与 score 不再单调对应；
  后端与前端均未消费该字段。建议回填最终排序阶段相关度。已记 IssueLog（待处理）。
- `eval/EVAL-REFERENCE.md` 的对外基线仍是 2026-09-15 的 0.5623 —— 本轮结果在负责人确认合并后才更新对外口径。
