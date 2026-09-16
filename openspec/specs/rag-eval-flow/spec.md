# rag-eval-flow Specification

## Purpose
让 RAG 评测从「跑得通」变成「讲得清、可维护」：评估流程有单一权威说明，改动影响面可查，
对外引用的基线唯一且模型可归因，对外文档与项目实际状态一致。

## Requirements

### Requirement: 评估流程有单一权威说明

`eval/README.md` SHALL 以端到端流程的形式说明评测如何从语料走到报告，覆盖「语料准备 →
数据集构建 → 运行评测 → 产出报告」四个阶段，且每个阶段 SHALL 注明输入、执行命令与产出物。
说明 SHALL 足以让未参与过本项目的人照着复现一次完整评测。

#### Scenario: 新人按文档复现完整评测

- **WHEN** 一名未参与过本项目的开发者只阅读 `eval/README.md`
- **THEN** 文档能指明每个阶段的输入从哪来、执行哪条命令、产出什么文件
- **AND** 文档能指明哪些步骤可跳过（如数据集已冻结时无需重新出题）

#### Scenario: 流程阶段与脚本的对应关系无歧义

- **WHEN** 阅读流程说明
- **THEN** 每个阶段 SHALL 列出该阶段用到的脚本路径
- **AND** 每个产出的文件 SHALL 标明落盘位置与是否进版本库

### Requirement: 改动影响面可查

`eval/README.md` SHALL 提供一张「改动影响面」映射表，列出常见改动项、涉及的文件、
以及该改动是否会迫使重新生成数据集或重建索引。映射表 SHALL 至少覆盖：
切块参数、embedding 模型、题型配比、指标口径、检索参数、语料来源。

#### Scenario: 调整切块参数时能查到影响面

- **WHEN** 查阅映射表中的「切块参数」一行
- **THEN** 表内给出承载该参数的代码位置
- **AND** 说明该改动会使 `gold[].chunkIndex` 失效、需要重新对齐或依赖 `quote` 重定位
- **AND** 说明需要重建索引

#### Scenario: 切换 embedding 模型时能查到影响面

- **WHEN** 查阅映射表中的「embedding 模型」一行
- **THEN** 表内说明必须全量回填库内 chunk 向量
- **AND** 说明离线模式与 http 模式的结果不可混合平均

### Requirement: 对外引用的基线唯一且模型可归因

`eval/results/` SHALL 只保留当前对外引用的基线结果，不得混放已被取代的历史结果。
基线结果 SHALL 能确定其 `retriever`；其 http 模式的 embedding 模型归属 SHALL 可确定——
要么记录在该结果 JSON 的 `config` 内，要么在 `eval/README.md` 中显式记录并注明来源。

#### Scenario: 目录中只有唯一基线

- **WHEN** 列出 `eval/results/`
- **THEN** 只存在当前基线结果文件
- **AND** 被取代的历史结果已移除，不再与基线并列

#### Scenario: 模型归属可确定

- **WHEN** 引用基线指标
- **THEN** 结果 JSON 的 `config.retriever` 足以确定评测通道
- **AND** embedding 模型归属可从 JSON 或文档中的显式记录确定
- **AND** 若归属依赖人工核对而非程序探测，文档 SHALL 明确标注这一点及其来源

### Requirement: 对外文档与项目实际状态一致

根 `README.md` SHALL 反映项目当前的真实状态：不得将已实现的功能描述为规划中或未开始；
不得保留无实测来源的性能或规模声明。

#### Scenario: 已实现的阶段不得标为规划中

- **WHEN** 某功能已合入 `main` 并可用
- **THEN** `README.md` 的愿景表与路线图 SHALL 标注其为已实现
- **AND** 项目简介 SHALL 不再将其描述为"下一步"

#### Scenario: 性能声明必须有实测来源

- **WHEN** `README.md` 出现延迟、吞吐、规模类数字
- **THEN** 该数字 SHALL 能在仓库内找到对应的实测记录
- **AND** 找不到来源的声明 SHALL 被移除或改写为不含具体数字的定性描述

#### Scenario: 技术亮点反映当前项目重心

- **WHEN** 项目重心已包含 RAG 检索与评测
- **THEN** 「技术亮点」章节 SHALL 包含与之对应的条目
- **AND** 条目内容 SHALL 与仓库中实际存在的实现一致
