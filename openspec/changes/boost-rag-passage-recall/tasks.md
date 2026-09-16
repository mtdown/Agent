# 任务清单：boost-rag-passage-recall

> 状态约定：`[x]` 已完成并验证；`[ ]` 未开始；`[~]` 被外部条件阻塞（原因写在条目内）。
> 本轮阻塞项：**DashScope 账户欠费**（`chat` / `gte-rerank-v2` / `embeddings` 均返回 `Arrearage`），
> 后端检索依赖 embedding 生成查询向量 → 当前无法跑任何一轮评测。

## 0. 前置：可行性与上限定界（已完成，证据见 design.md §2）

- [x] 0.1 建任务分支 `feature/rag-recall-boost开发`（从 `origin/main` @323c86c 切出并推送，本地跟踪）
- [x] 0.2 复跑基线确认可比：`recall@6 = 0.5636` vs 归档 0.5623（Δ0.13pt，在重跑极差内）
- [x] 0.3 深取候选（`topK=200`）产出逐题排名：`eval/tmp/diag-rankings.json`（399 题）
- [x] 0.4 计算完美排序上限与 oracle 曲线：上限 0.9783；纯向量 oracle@50=0.8148、oracle@100=0.8532
- [x] 0.5 定位病灶：126 道恒零题中 82% 是「gold 文档已在前 10、段落选错」
- [x] 0.6 实测混合通道收益：向量+BM25(RRF) → oracle@50 **0.8600**、oracle@100 **0.9061**，top-6 **0.5957**
- [x] 0.7 实测 `gte-rerank-v2` 重排：top-100 = 0.5999，仅吃到 12.5% 可用空间 → 否决"只加重排"
- [x] 0.8 确认文号层必须钉位：RRF 融合会让 B 类从 0.6525 掉到 0.4129

## 1. 词法通道与融合（无模型依赖）

- [ ] 1.1 新增 `LexicalIndex`：在已授权语料的 chunk 上构建倒排 + BM25 打分；CJK bigram / 英文按词；随 `onChunksChanged` 失效重建
- [ ] 1.2 新增 `RrfFusion`：多路有序列表按 RRF 融合（常数 60，可配），输出去重有序候选
- [ ] 1.3 单元测试：bigram 切词、BM25 单调性、RRF 融合顺序、空通道输入不抛异常

## 2. 检索链路改造

- [ ] 2.1 `RagProperties.Retrieval` 新增 `candidatePoolSize`（默认 50）、`fusionPoolSize`、`topKMax`（默认 50）与 `lexical` / `fusion` / `rerank` 子配置
- [ ] 2.2 `RagSearchServiceImpl`：候选池深度与 `topK` 解耦；`topK` 加上限截断
- [ ] 2.3 `RagSearchServiceImpl.findDocNumberHits`：去掉 `orderByAsc("chunkIndex")` 的位置排序语义，改为钉位候选组 + 组内按相关性排序
- [ ] 2.4 修复 `if (hits.size() < topK)` 导致文号层填满后**跳过向量检索**的缺陷
- [ ] 2.5 `SearchTimings` 增加 lexical / fusion / rerank 阶段（未执行留空）
- [ ] 2.6 单元测试：文号钉位优先于融合结果、文号层不再吞掉语义通道、topK 超限被截断、各增强通道关闭时行为与改造前一致

## 3. 重排接入（依赖额度恢复）

- [ ] 3.1 新增 `RagRerankClient`（DashScope `gte-rerank-v2`），超时/异常统一转可降级错误
- [ ] 3.2 接入 `RagSearchServiceImpl`：融合候选池 → 重排 → 截 `topK`；失败静默回退并记录
- [ ] 3.3 单元测试：重排成功改序、关闭时跳过、异常时回退且接口仍成功

## 4. 评测验证（**阻塞：需 DashScope 额度**）

- [~] 4.1 `stop-dev.ps1` + `start-dev.ps1` 起服务并跑通 `run_eval.py --probe`
      — 阻塞原因：embedding 返回 `Arrearage`，后端无法生成查询向量
- [~] 4.2 http 模式跑官方 runner，产出改动后结果 JSON 并与基线逐 K 对比
      — 阻塞原因同上
- [~] 4.3 产出分组对比（A/B/E/X/S + `docRecall@6` / `mrr` / 权限 `leakCount` 回归）
      — 阻塞原因同上
- [ ] 4.4 回填实测数字到 `proposal.md` 与 `EVAL-REFERENCE.md`；**明确写出实际达到的 `recall@6` 与 0.85 的差距**

## 5. 收尾

- [ ] 5.1 `IssueLog.xlsx` 记录：DashScope 欠费（阻塞）、文号层位置排序缺陷、RRF 稀释文号层
- [ ] 5.2 更新 `.workbuddy/memory/` 的项目记忆（上限结论、混合通道收益、额度依赖）
- [ ] 5.3 汇报实际指标与结论，由负责人决定：是否追加额度启用重排 / 是否改用可达成口径
- [ ] 5.4 经负责人确认后 `upload.ps1` 上传分支并发起 merge request（不自行合并 main）
