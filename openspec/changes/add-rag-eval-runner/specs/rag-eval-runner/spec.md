# rag-eval-runner Specification

## Purpose

用已冻结的 `golden.v1.jsonl` 驱动真实检索系统，产出可复现的检索层指标，使切块策略、embedding 模型、
检索策略的任何改动都能用同一把尺子前后对比。

## ADDED Requirements

### Requirement: 检索适配器可切换且主路径走真实系统

系统 SHALL 提供 `HttpRetriever`（调 `POST /open/rag/search`）与 `OfflineRetriever`
（连库取 chunk + embedding API + 余弦排序）两种实现同一接口的检索适配器，
且**对外引用的指标 SHALL 来自 `HttpRetriever`**。

#### Scenario: http 模式调用开放检索接口
- **WHEN** 以 `--retriever http` 运行
- **THEN** 每题发送 `POST {baseUrl}/open/rag/search`，Header 带 `X-API-Key`
- **AND** 请求体为 `{query, topK: 10}`（spaceIds 留空表示"该用户可见的全部空间"）
- **AND** 从 `hits[]` 提取 `(docId, chunkIndex)` 作为命中坐标

#### Scenario: 两种模式的分数不可混合
- **WHEN** 结果 JSON 被生成
- **THEN** 顶层记录 `retriever` 与 `embeddingModel` 字段
- **AND** 汇总区块不出现跨 `retriever` 的平均分

#### Scenario: offline 模式维度不匹配时报错
- **WHEN** offline 模式下 query 向量维度与 chunk 向量维度不一致
- **THEN** 脚本报错退出并说明当前库内向量来源模型
- **AND** SHALL NOT 静默跳过或产出全 0 指标

### Requirement: 检索层指标按定义计算且支持多个 K

系统 SHALL 对 `gold` 非空的题目计算 `recall@K`、`docRecall@K`、`mrr`、`hitRate@K`，
且 SHALL 用**一次 topK=10 的检索在本地截断**计算 K ∈ {1, 3, 5, 6, 10} 的全部指标。

#### Scenario: chunk 级与文档级召回同时产出
- **WHEN** 某题有 `n` 个 gold 坐标、命中 `m` 个
- **THEN** `recall@K = m / n`
- **AND** `docRecall@K` = gold 涉及的 docId 集合中被前 K 命中的比例

#### Scenario: MRR 取首个命中位次
- **WHEN** 首个命中 gold 的结果排在第 `r` 位（1-based）
- **THEN** `mrr = 1 / r`
- **AND** 前 10 条均未命中时 `mrr = 0`

#### Scenario: 多 K 指标来自同一次检索
- **WHEN** 一轮评测执行
- **THEN** 每题只发送一次检索请求（`topK=10`）
- **AND** 结果 JSON 同时包含 K ∈ {1, 3, 5, 6, 10} 的指标

### Requirement: C 类无答案题单独统计且不拍阈值

系统 SHALL 将 `expectRefusal=true` 的题目排除在召回类指标之外，
并 SHALL 输出非空返回率、`top1Score` 分位分布与子类型分布，且 SHALL NOT 用未标定的相似度阈值判定"是否正确拒答"。

#### Scenario: C 类不参与召回计算
- **WHEN** 某题 `gold` 为空数组
- **THEN** 该题不计入 `recall@K` / `mrr` / `hitRate@K` 的分母
- **AND** 该题进入 C 类专属统计区块

#### Scenario: 输出可解读的分布而非单一分数
- **WHEN** C 类统计生成
- **THEN** 包含 `nonEmptyReturnRate`、`top1Score` 的 min/p25/median/p75/max
- **AND** 逐题列出 `evidenceKey`、`outOfScopeReason`、返回条数与 `top1Score`
- **AND** 数据集不含 `unanswerableType` 字段时 SHALL NOT 按 ID 区间硬凑子类型分组

### Requirement: D 类权限题双向对照且泄漏一票否决

系统 SHALL 用成员与非成员两个 API Key 各跑一次 D 类题，
成员侧验证镜像题 gold 可命中，非成员侧检测 `permission.forbiddenDocIds` 泄漏，
且泄漏 SHALL 一票否决、D 类分数 SHALL NOT 混入 overall 平均。

#### Scenario: 成员 key 应命中镜像题证据
- **WHEN** 用成员 key 检索某 D 类题
- **THEN** 计算 `mirrorRecall@K`，基准为 `meta.mirrorOf` 指向题目的 gold
- **AND** 该值过低说明权限过滤误伤了正常检索

#### Scenario: 非成员 key 出现禁止文档即判泄漏
- **WHEN** 用非成员 key 检索某 D 类题，结果中出现任一 `forbiddenDocIds`
- **THEN** 该题记入 `leakCount`
- **AND** 结果 JSON 输出 `leakCount` 与 `leakRate`，并在报告中显著标注

#### Scenario: key 制备失败时跳过而非污染
- **WHEN** 成员或非成员 key 不可用
- **THEN** D 类标记 `skipped` 并说明原因
- **AND** D 类不进入 overall 汇总

### Requirement: 后端不可用时快速失败

系统 SHALL 在后端不可用时立即中止并给出明确指引，且 SHALL NOT 静默降级或把故障跑出的全 0 分当作指标输出。

#### Scenario: 开跑前探测失败
- **WHEN** http 模式下首个探测请求连接失败
- **THEN** 脚本以非零退出码终止
- **AND** 输出"后端未启动，请先执行 start-dev.ps1"及当前 base-url

#### Scenario: 连续请求失败中止
- **WHEN** 连续 5 次检索请求失败
- **THEN** 脚本中止整轮并输出已完成的题数与失败样例

#### Scenario: 故障结果被标记为无效
- **WHEN** 本轮 `errorCount > 0` 且 `overall.recall@6 == 0`
- **THEN** 结果 JSON 标记 `invalid: true`
- **AND** 报告顶部写明"环境故障，此轮结果不可用"

### Requirement: 结果产物可复现且逐题可追溯

系统 SHALL 产出 JSON 明细与 Markdown 报告，记录运行配置与 golden 文件指纹，
且 SHALL NOT 写入任何密钥原文。

#### Scenario: 结果 JSON 含完整运行配置
- **WHEN** 一轮评测完成
- **THEN** 产物写入 `eval/results/<run-id>.json`
- **AND** 顶层含 `runId`、`goldenFile`、`goldenSha256`、`retriever`、`embeddingModel`、
  `topK`、起止时间、后端 base-url（不含密钥）

#### Scenario: 逐题明细可追溯
- **WHEN** 查看结果 JSON 的 `perQuestion`
- **THEN** 每题含 `id`、`category`、各 K 的指标、`top1Score`、命中列表的 `(docId, chunkIndex, score)`
- **AND** 调用失败的题含 `error` 字段

#### Scenario: 可读报告含失败样例分析
- **WHEN** 生成 `eval/audit/baseline-report.md`
- **THEN** 报告含总览、分类明细、C 类分布、D 类泄漏结论
- **AND** 列出 recall 最低的若干题及其 gold 与命中情况
