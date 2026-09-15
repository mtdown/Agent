# rag-eval-coverage Specification

## Purpose

让 RAG 评测集**在覆盖面上能代表全库、在统计上能证明优化有效**：出题入口不依赖单一文档结构，
配对判定有据可查，新题必须通过强制质量闸门，复核来源可区分，指标按题型分组报告，
且存量数据集保持不可变以维持基线可比。

## ADDED Requirements

### Requirement: 评测集内容覆盖可度量且达到目标水位

评测集 SHALL 以可复算的方式度量两个覆盖率：`docCoverage`（被 gold 命中的文档数 / 语料文档总数）
与 `chunkCoverage`（被 gold 命中的 chunk 数 / 有效 chunk 数）。
覆盖率 SHALL 写入质检报告，并 SHALL 能由仓库内脚本重新算出。

#### Scenario: 覆盖率可被独立复算

- **WHEN** 任何人拿到 `golden.v2.jsonl` 与语料清单
- **THEN** 能用仓库内脚本算出 `docCoverage` 与 `chunkCoverage`
- **AND** 算出的数值与质检报告中的记录一致

#### Scenario: 覆盖目标未达成时不得宣称全库覆盖

- **WHEN** 实际 `docCoverage` 低于 100%
- **THEN** 质检报告 SHALL 列出未被任何题目覆盖的文档清单
- **AND** 对外文档 SHALL NOT 声称评测集覆盖全部语料

### Requirement: 出题入口不依赖单一文档结构

出题能力 SHALL 支持至少两条入口：一条面向"政策↔解读"配对结构的跨文档出题，
一条面向任意单篇文档的出题。任一篇语料文档 SHALL 至少能通过其中一条入口产出候选题目。

#### Scenario: 无配对结构的文档仍能出题

- **WHEN** 一篇文档在配对表中不存在（如媒体视角新闻）
- **THEN** 单文档入口 SHALL 能为它产出候选题
- **AND** 其 `gold` SHALL 锚定该文档自身的 chunk

#### Scenario: 政策原文不支撑答案时降级而非丢弃

- **WHEN** 一篇解读/新闻类文档被出题，但其可匹配的政策原文没有任何 chunk 能通过摘抄校验
- **THEN** 该题 SHALL 降级为单文档题（gold 锚本文档）
- **AND** SHALL NOT 直接丢弃该文档的题目（否则覆盖率无法保证）

### Requirement: 配对判定有据可查

每条跨文档配对 SHALL 记录其判定依据：`matchType`（`exact` / `contains` / `docnum`）
与 `matchEvidence`（命中的原文片段）。配对 SHALL NOT 采信无显式文本证据的弱匹配。

#### Scenario: 正文文号匹配可审计

- **WHEN** 一篇新闻正文出现 `〔2025〕66号` 且与某政策文件的 `docNumber` 一致
- **THEN** 该配对 SHALL 记录 `matchType=docnum` 与命中的文号原文片段
- **AND** 该配对 SHALL 出现在配对审计文件中

#### Scenario: 弱匹配不被采信

- **WHEN** 仅能通过标题片段模糊匹配（无书名号、无文号）
- **THEN** 该配对 SHALL NOT 进入出题流程
- **AND** 该文档 SHALL 退化为走单文档入口

### Requirement: 新题必须通过强制质量闸门

新生成的题目 SHALL 在进入数据集前通过五道自动闸门：摘抄真实性校验、答案可定位性、
答案长度上限、同类别问题去重、指代表述检测。任一闸门不通过 SHALL 剔除该题，
且 SHALL 记录其被剔除的闸门与原因。

#### Scenario: 摘抄不匹配的题被剔除

- **WHEN** 模型给出的 `gold[].quote` 归一化后未真实出现在对应 chunk 中
- **THEN** 该 gold 段 SHALL 被剔除
- **AND** 若剔除后该题无可用 gold 段，该题 SHALL 被丢弃并记录原因

#### Scenario: 剔除原因分类可查

- **WHEN** 生成流程结束
- **THEN** 每一道被丢弃的题 SHALL 带可分类的原因（质检闸门名称）
- **AND** 各闸门的拦截数量 SHALL 汇总进质检报告
- **AND** 任一闸门拦截率超过 30% 时 SHALL 在报告中显式标记为异常

#### Scenario: 数据集校验器仍能拦截坏数据

- **WHEN** 新增题型后运行校验器的自检模式
- **THEN** 校验器 SHALL 仍能捕获注入的坏数据（如失效的 `chunkIndex`）
- **AND** 校验器的严格程度 SHALL NOT 因支持新题型而被放宽

### Requirement: 复核来源可区分

数据集 SHALL 标注每道题的复核来源（人工复核 / 自动生成），使引用方能区分哪些题的
gold 经人工确认。新题 SHALL NOT 被标记为人工复核。

#### Scenario: 存量题与新题可区分

- **WHEN** 检查 `golden.v2.jsonl` 中任一题的 `meta`
- **THEN** 该题 SHALL 带 `reviewedBy` 字段
- **AND** 经人工复核的存量题标为 `human`，自动生成的新题标为 `auto`

#### Scenario: 自动生成的题不得冒充人工复核

- **WHEN** 一道题仅有自动闸门把关
- **THEN** 其 `reviewedBy` SHALL 为 `auto`
- **AND** 质检报告 SHALL 披露 `auto` 题在数据集中的占比

### Requirement: 指标按题型分组报告

评测结果 SHALL 按题型分别报告指标，SHALL NOT 只给出一个混合平均。
单文档题（易）与跨文档题（难）SHALL 各自独立成组，其难度差异 SHALL 在报告中被显式说明。

#### Scenario: 新题型进入分组报告

- **WHEN** 数据集中存在单文档题与跨文档题
- **THEN** 评测报告 SHALL 为这两类分别给出 `recall@k` / `docRecall@k` / `hitRate@k` / `mrr`
- **AND** SHALL NOT 因新类别未被登记而静默省略

#### Scenario: 混合平均的含义变化被声明

- **WHEN** 报告给出 overall 指标
- **THEN** 报告 SHALL 说明该 overall 的题型构成
- **AND** 当题型构成与历史版本不同时，SHALL 显式标注两者不可直接比较

### Requirement: 存量数据集不可变

已发布并被基线引用的数据集文件（`golden.v1.jsonl`）SHALL 保持字节不变。
扩题 SHALL 以新增文件的方式产出（`golden.v2.jsonl`），SHALL NOT 就地修改v1。

#### Scenario: v1 内容与基线可追溯

- **WHEN** 扩题完成后检查 `golden.v1.jsonl`
- **THEN** 其内容 SHALL 与扩题前完全一致
- **AND** 基于 v1 的既有基线结果 SHALL 仍然有效

#### Scenario: v2 可独立回退

- **WHEN** v2 被判为不可用
- **THEN** 删除 v2 文件即可让评测回到 v1 状态
- **AND** SHALL NOT 影响后端服务、数据库或索引

### Requirement: 跨版本指标可比性有明确判定路径

当数据集版本变化导致题量或题型构成变化时，系统 SHALL 提供一种可复算的方式，
把「题目构成变化引起均值漂移」与「系统本身变化」区分开。仅有整体均值对比 SHALL NOT
被接受为系统改进的证据。

#### Scenario: 用共同题目子集判定系统变化

- **WHEN** 两次基线的数据集版本不同
- **THEN** 对比 SHALL 给出两次运行**共同包含的题目集合**上的指标
- **AND** 该子集上的差异 SHALL 被作为系统变化的判据
- **AND** 整体均值差异 SHALL 标注为受题型配比污染、不可单独采信

#### Scenario: 题型配比变化被声明

- **WHEN** 新旧版本的题型构成不同
- **THEN** 报告 SHALL 列出各题型的题数变化
- **AND** SHALL 说明新增题型的难度与既有题型的差距

### Requirement: 基线数值的可信下限被量化并声明

评测体系 SHALL 量化检索链路的非确定性抖动，并以该抖动幅度作为对外引用指标的
数值可信下限。跨版本比较时，小于该幅度的差异 SHALL NOT 被作为改进证据。

#### Scenario: 抖动幅度可复算

- **WHEN** 以同一数据集、同一后端连续运行两次评测
- **THEN** 对比脚本 SHALL 给出逐指标的极差与相对极差
- **AND** SHALL 给出逐题的命中集合变化清单，并区分「集合变化」与「仅排序/分数变化」
- **AND** 结论 SHALL 落盘到审计报告

#### Scenario: 小于抖动的差异不被采信

- **WHEN** 对外引用某一项指标的改进
- **THEN** 该改进幅度 SHALL 大于已量化的抖动幅度
- **AND** 若差异落在抖动范围内，SHALL 说明该差异无法与噪声区分，需重复运行确认

#### Scenario: 抖动来源被归因

- **WHEN** 声明抖动幅度
- **THEN** 报告 SHALL 说明抖动来自检索链路而非评测脚本
- **AND** SHALL 给出判据（同一份 hits 输入下，集合型指标的计算是确定的）
