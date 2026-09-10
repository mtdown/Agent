# add-rag-eval-dataset Proposal

## Why

RAG 检索、AI 对话、开放 API 三件事都已完成并合入 main（216 篇政务语料 → 2061 个 chunk，全部 ACTIVE），但**评测集是零**：`openspec/` 与 `cloud/src` 下检索 `评测题 / 评测集 / golden / benchmark` 无任何命中。后果是四件事同时说不清楚——切块参数（600/100/80）是拍脑袋定的、任何检索优化都无法证明有效、换 embedding 模型拿不到前后对比、简历与面试只能讲"做了"而讲不出"好多少"。

现在建的成本最低：语料侧有 25 组天然的**官方「政策原文 ↔ 部门解读」配对**（部门解读 21 篇中 17 篇、新闻发布会 10 篇中 8 篇可通过标题书名号配到政策原文），这比 LLM 合成数据高一个质量档次——解读是官方撰写的，用它做标准答案天然免疫"合成数据虚高"陷阱；库侧 216 篇已全量入库且 ACTIVE，`docTitle`/`docNumber` 与 F 盘 `title`/`fileNum` 精确匹配 216/216，ground truth 可直接绑到 chunk。

## What Changes

- 新增**语料体检**：统计空壳率（正文 <200 字）、表格拍平率、文号覆盖率、`pageText` 与入库文本差异、跨省噪声（已发现 1 条 `川办发〔2026〕23号`），产出体检报告
- 新增**锚点映射导出**：从 `wiki_chunk` 导出 `metadataId → docId → (chunkIndex → chunkId)` 映射，供题目绑定 ground truth
- 新增**配对锚定**：政策原文 ↔ 官方解读配对（约 25 组）+ 人工核对，产出 `pairs.json`
- 新增**评测题格式规范**：JSONL，ground truth 采用 **`docId` + `chunkIndex` 逻辑坐标**（而非数据库主键 id），规避重建索引后主键漂移
- 新增**题目生成**：A 类配对题（LLM 从解读生成问题、答案取解读原文、证据绑政策 chunk）、B 类文号题、C 类无答案题、D 类权限题、E 类合成补充题，产出 `candidates.jsonl`
- 新增**人工筛选工具**：本地 HTML 页面，逐题展示问题/答案/预期命中片段，支持保留·修改·删除，一键导出 `golden.jsonl`
- 新增**数据集校验脚本**：schema 合法性、锚点可解析性（能否映射到真实 chunk）、题目去重、权限题一票否决标记、配比校验

- 不新增数据库表，不改后端业务代码（只读库 + 独立脚本 + 静态页面）

## Capabilities

### New Capabilities

- `rag-eval-dataset`: RAG 评测数据集的生成、配对锚定、人工筛选与质量校验；含题目格式规范与 ground truth 锚定规则

### Modified Capabilities

（无——不改动既有 spec 的需求行为；检索、问答、导入的对外行为均不变）

## Impact

- **新增目录 `eval/`**（进仓）：`README.md`（格式规范）、`pairs.json`、`candidates.jsonl`、`golden.jsonl`、`audit/corpus-audit.md`、`scripts/`（映射导出·生题·校验）、`tools/review.html`（筛选页）
- **外部输入**：语料 `F:\AIProject\my\corpus-2026`（216 篇，不进仓）；数据库 `localhost:3307/Cloud`（只读查询）
- **运行时依赖**：Python 3.13 隔离环境（生题/校验脚本，仅开发期）；LLM 调用走 OpenAI 兼容 HTTP 接口，base-url / api-key / model 从环境变量读取，复用现有 DashScope 或本地 Ollama 配置
- **中间产物**（`.gitignore`）：`eval/tmp/`、LLM 原始响应、筛选草稿
- **风险**：
  - LLM 合成题质量虚高 → 靠人工筛选与"答案必须可溯源到原文"约束兜底
  - 配对误匹配会让整道题失效 → 25 组配对必须逐条人工核对
  - chunk 主键在重建索引后会变 → ground truth 一律用 `docId + chunkIndex` 逻辑坐标
  - 生题需调 LLM，有 token 成本与随机性 → 固定 temperature、记录模型版本与生成时间到数据集元数据

## Non-goals

- 不做评测 runner、Recall@K / MRR / NDCG / 文号命中率计算与报告（下一个 change）
- 不做 LLM-as-Judge 生成层评测（Faithfulness / Answer Relevancy）
- 不改 `MarkdownChunker` 切块参数（等基线出来后再做参数扫描实验）
- 不换 embedding 模型（作为数据集建好后的第一个对比实验）
- 不做政策附件 PDF/DOC 解析（附件审计已确认正文在页面上）
- 不做前端或后端业务功能改动
