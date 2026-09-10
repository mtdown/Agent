# rag-eval-dataset Specification

## Purpose

为云策库的 RAG 链路提供一份可复现、可校验、长期可用的评测数据集与格式规范，使检索质量、切块策略、embedding 模型的任何改动都能用同一把尺子量化比较。

## ADDED Requirements

### Requirement: 语料体检产出只读报告

系统 SHALL 对 `F:\AIProject\my\corpus-2026` 的 216 篇语料执行只读体检，并产出 `eval/audit/corpus-audit.md`，且 SHALL NOT 修改语料或数据库内容。

#### Scenario: 体检覆盖既定质量维度
- **WHEN** 体检脚本执行
- **THEN** 报告包含空壳率（正文少于 200 字的篇数与占比）、表格拍平率、文号覆盖率、`pageText` 与入库文本的长度差异分布、跨辖区噪声清单
- **AND** 每个维度同时给出篇数、占比与最差样本的文件名

#### Scenario: 发现跨辖区噪声
- **WHEN** 某篇语料的文号不属于重庆辖区（例如 `川办发〔2026〕23号`）
- **THEN** 该篇被列入噪声清单
- **AND** 不因体检而修改或删除任何已入库数据

### Requirement: chunk 锚点映射可导出

系统 SHALL 从 `wiki_chunk` 导出 `metadataId → docId → (chunkIndex → chunkId)` 映射到 `eval/tmp/anchor-map.json`，供题目绑定 ground truth。

#### Scenario: 语料与库内文档对齐
- **WHEN** 导出脚本执行
- **THEN** 216 篇语料的规范化标题 SHALL 全部匹配到库内 `docTitle`
- **AND** 每篇给出 `docId`、chunk 总数与 `docNumber`

#### Scenario: 存在未对齐文档
- **WHEN** 某篇语料标题在库内无匹配
- **THEN** 该篇被列入未对齐清单并终止导出（不产出部分映射）

### Requirement: 政策原文与官方解读配对锚定

系统 SHALL 从解读类文档的标题书名号中提取政策名，与政策文件（`01-政策文件`）建立配对，产出 `eval/pairs.json`，并 SHALL 经人工逐条核对。

#### Scenario: 标题含书名号可配对
- **WHEN** 解读标题形如《重庆市地震应急预案》文字解读
- **THEN** 系统规范化书名号内的政策名并与政策文件标题匹配
- **AND** 配对记录政策 `docId`、解读 `docId`、匹配方式（精确 / 包含）

#### Scenario: 配对需人工确认
- **WHEN** `pairs.json` 生成完成
- **THEN** 每对配对 SHALL 带 `reviewState` 字段，人工核对后标记为 confirmed / rejected
- **AND** 被 reject 的配对不参与出题

#### Scenario: 无法配对的解读
- **WHEN** 解读标题不含书名号或匹配不到政策文件（例如媒体视角类新闻体标题）
- **THEN** 该篇标记为 unmatched 并不用于 A 类出题
- **AND** 仍保留在检索池中作为干扰项

### Requirement: 评测题采用逻辑坐标锚点

数据集 SHALL 以 JSONL 存储，每题的 ground truth 使用 `docId` + `chunkIndex` 逻辑坐标，而 SHALL NOT 使用 chunk 数据库主键。

#### Scenario: 题目记录 gold 锚点
- **WHEN** 一道题需要标注应命中的 chunk
- **THEN** `gold` 数组每项包含 `docId`、`chunkIndex`
- **AND** 不包含 `chunkId` 字段

#### Scenario: 索引重建后锚点仍有效
- **WHEN** `wiki_chunk` 因换 embedding 模型或改切块策略而重建（旧行置 INVALID、新行获得新主键）
- **THEN** 既有题目的 `gold` 锚点无需修改
- **AND** 运行时可重新解析为当前有效的 `chunkId`

### Requirement: 题目按五类来源生成且配比可控

系统 SHALL 生成约 90 道评测题，覆盖 A 配对题、B 文号题、C 无答案题、D 权限题、E 合成补充题，并在 `eval/manifest.json` 记录实际配比。

#### Scenario: A 类题从解读生成、证据绑政策原文
- **WHEN** 使用一对已确认的政策-解读配对出题
- **THEN** 问题由 LLM 基于整篇解读生成，标准答案取自解读原文
- **AND** `gold` 只绑定政策原文的 chunk，命中解读 chunk 不计为正确

#### Scenario: B 类文号题
- **WHEN** 生成文号题
- **THEN** 问题包含完整文号（例如 `渝府办发〔2026〕24号`）
- **AND** `gold` 绑定该文号对应文档的 chunk

#### Scenario: C 类无答案题
- **WHEN** 生成无答案题
- **THEN** `expectRefusal` 为 true
- **AND** 题目涉及知识库不存在的实体（外省政策或虚构文号）

#### Scenario: D 类权限题一票否决
- **WHEN** 生成权限题
- **THEN** 题目携带 `permission` 字段标注可见空间与禁止命中的文档
- **AND** 该题被标记为不参与平均分统计、泄漏即判定失败

#### Scenario: 配比偏离目标
- **WHEN** 实际题量与 40–50 / 10 / 15 / 10 / 10–15 的目标配比不一致
- **THEN** `manifest.json` 如实记录实际配比与偏差原因

### Requirement: 人工筛选工具可逐题审阅并导出

系统 SHALL 提供本地静态 HTML 页面 `eval/tools/review.html`，支持载入候选题、逐题审阅并导出最终结果。

#### Scenario: 审阅展示必要上下文
- **WHEN** 审阅一道候选题
- **THEN** 页面同时展示问题、标准答案、预期命中 chunk 的文本片段、来源文档标题与文号

#### Scenario: 导出通过校验的数据集
- **WHEN** 用户完成审阅并点击导出
- **THEN** 仅 `reviewState` 为 kept 或 edited 的题目写入 `golden.v1.jsonl`
- **AND** 导出内容包含每题的 `meta.reviewState`

### Requirement: 数据集校验作为门禁

系统 SHALL 提供 `eval/scripts/validate.py`，校验不通过时数据集不得标记为 v1。

#### Scenario: 锚点可解析性校验
- **WHEN** 校验脚本运行
- **THEN** 每个 `gold` 锚点 SHALL 能解析到当前 ACTIVE 的 chunk
- **AND** 无法解析的题目被列出并判定校验失败

#### Scenario: 字段与配比校验
- **WHEN** 校验脚本运行
- **THEN** 必填字段缺失、问题重复、跨辖区噪声文档出题、C/D 类题必填字段缺失 SHALL 全部被报告

#### Scenario: 校验通过产出清单
- **WHEN** 全部校验项通过
- **THEN** 输出题量、分类配比、覆盖文档数的统计摘要

### Requirement: 数据集全程只读不改业务

本能力 SHALL NOT 修改数据库表结构、后端业务代码或既有索引状态。

#### Scenario: 生成过程不触碰索引
- **WHEN** 执行体检、映射导出、出题、筛选、校验任一环节
- **THEN** `wiki_chunk` 的行数与状态分布保持不变
- **AND** 不触发任何重建索引操作
