# add-rag-eval-runner Proposal

## Why

`add-rag-eval-dataset` 已经交付了 99 道题的 `golden.v1.jsonl`（390 个 gold 段落、五类齐全、校验全绿），
但**它只是考卷，没有阅卷器**——`eval/scripts/` 下 15 个脚本全是生题、筛选、校验，
没有一个会去调后端检索。于是 proposal 里列的四个痛点只解决了一个半：

| 痛点 | 现状 |
|---|---|
| 切块参数 600/100/80 是拍脑袋定的 | **未解决**——无法量化换切块策略的收益 |
| 任何检索优化都无法证明有效 | **未解决** |
| 换 embedding 模型拿不到前后对比 | **未解决**——Ollama 2560 维 → DashScope 1024 维的全量回填做完了也没有尺子量 |
| 简历只能讲"做了"讲不出"好多少" | **未解决** |

现在建的成本最低：`OpenRagController` 的 `POST /open/rag/search` 已经是现成的评测入口——
`X-API-Key` 认证（无需登录 session）、走与站内**完全相同**的权限过滤 service 层，
且返回的 `ChunkHit` 直接带 `docId` + `chunkIndex`，正是数据集 gold 的逻辑坐标，
**比对不需要任何映射**。D 类权限题的泄漏率实验也因此天然可做：换一个 key 就是换一个 owner。

## What Changes

- 新增**评测 runner** `eval/scripts/run_eval.py`：读 `golden.v1.jsonl` → 逐题调检索 → 与 gold 比对 → 产出指标
- 新增**两种检索适配器**（同一接口，可切换）：
  - `http`：调 `POST /open/rag/search`，测的是**真实系统**（主路径，对外引用的指标一律来自此模式）
  - `offline`：连库取 chunk + 调 embedding API 算 query 向量 + 余弦排序，用于**无后端时的降级**与
    **embedding 模型选型**（可一次对比多个模型，不必反复物理删库重建）
- 新增**指标定义与计算**：`Recall@K`（chunk 级与文档级）、`MRR`、`HitRate@K`、
  C 类**拒答率**、D 类**跨空间泄漏率**
- 新增**结果产物**：`eval/results/<run-id>.json`（逐题明细 + 汇总）与
  `eval/audit/baseline-report.md`（可读报告 + 失败样例分析）
- 新增**评测用 API Key 制备脚本** `eval/scripts/prepare_keys.py`：`rag_api_key` 表以 sha256 存 key，
  可直接在库内制备「成员」与「非成员」两个 owner 的 key，供 D 类权限题对照

- **不改后端业务代码、不改数据库 schema、不重训、不评测答案生成质量**

## Non-goals

- **不评测 LLM 答案的生成质量**（答案与 gold answer 的语义相似度、引用正确性）——
  那依赖 `POST /open/rag/ask` 的 SSE 流式接口与另一套判据，属下一个 change
- **不做切块策略改造**——本 change 只提供"换了能算出好坏"的尺子，不负责换
- **不做 embedding 全量回填**——那是回填脚本的事；本 change 只负责回填前后各跑一次做对比
- **不改 `golden.v1.jsonl`**——数据集已冻结为 v1，runner 只读

## Capabilities

### New Capabilities

- `rag-eval-runner`: 用评测数据集驱动真实检索系统并产出标准化指标；含检索适配器抽象、
  指标定义、失败快速暴露规则与可复现的结果产物

### Modified Capabilities

（无——不改动既有 spec 的需求行为；检索、问答、权限过滤的对外行为均不变）

## Impact

- **新增目录**：`eval/results/`（进仓）、`eval/scripts/run_eval.py`、`eval/scripts/prepare_keys.py`、
  `eval/audit/baseline-report.md`
- **运行时依赖**：Python 3.13 隔离环境 + `requests`（http 模式）/ `pymysql`（offline 模式）；
  http 模式额外要求后端在 `localhost:8123` 运行且 `rag.embedding` 已配置
- **外部输入**：`eval/golden.v1.jsonl`（只读）、数据库 `localhost:3307/Cloud`（只读，offline 模式与 key 制备）
- **风险**：
  - **后端未启动会静默产出全 0 指标** → 这是本项目踩过的"假成功"同类陷阱。
    runner 必须在后端不可用时 **fail fast 并给出明确指引**，绝不静默降级
  - `Recall@K` 受 topK 影响，不同 topK 的分数不可比 → 结果 JSON 必须记录 topK 与运行配置
  - D 类泄漏率依赖两个 owner 的 key → key 制备失败时 D 类单独标记 `skipped`，不混入总分
  - offline 模式的向量与库内存量向量可能不同模型 → 结果 JSON 必须标注 `retriever` 与 `embeddingModel`，
    两种模式的分数**不可混合平均**
