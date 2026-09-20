# rag-llm-generation Specification

## Purpose
让答案生成层的调用契约可控、可实验：生成请求的可选参数由配置门控且默认不改变既有链路，
更换生成模型档位的实验必须控制在单一变量，并在样本量不足以支撑结论时如实标注而非强行下结论。

## Requirements

### Requirement: 生成模型思考开关可选且默认不改变既有链路

`rag.llm` SHALL 提供 `enable-thinking` 配置项，用于控制生成请求是否携带供应商侧的思考开关字段
（DashScope 系模型对应请求体 `enable_thinking`）。该项 SHALL 默认为空，且**为空时请求体不得包含该字段**，
以保证不识别该字段的服务（如 DeepSeek）行为与引入前完全一致。系统 SHALL 仅在显式配置为
`true` 或 `false` 时下发该字段。思考开关 SHALL 与模型档位选择（`model` / `fast-model`）相互独立。

#### Scenario: 未配置思考开关

- **WHEN** `RAG_LLM_ENABLE_THINKING` 未设置或为空串
- **THEN** 绑定结果为 `null`
- **AND** 发往生成服务的请求体不包含 `enable_thinking` 字段
- **AND** 应用启动不因该配置项失败

#### Scenario: 显式关闭思考

- **WHEN** `RAG_LLM_ENABLE_THINKING=false`
- **THEN** 请求体携带 `"enable_thinking":false`
- **AND** 使用快速档模型（`fast-model`）时模型名不受该开关影响

#### Scenario: 显式开启思考

- **WHEN** `RAG_LLM_ENABLE_THINKING=true`
- **THEN** 请求体携带 `"enable_thinking":true`

### Requirement: 生成模型换档实验必须控制在单一变量

答案层实验 SHALL 通过环境变量切换生成模型，且 SHALL 保持题目集、判定模型与判定提示、
检索配置不变。结果 SHALL 落盘逐题明细，报告 SHALL 显式声明样本量与统计分辨率限制。

#### Scenario: 双臂可比

- **WHEN** 对同一题目集分别以非思考与思考两种参数调用生成服务
- **THEN** 两臂使用同一份题目集与同一个判官模型
- **AND** 检索侧配置（多查询、chunk 整理、池深、K）在两臂间一致

#### Scenario: 生效自证

- **WHEN** 一臂显式关闭思考
- **THEN** 结果记录中该臂配置体现 `enable_thinking=false`
- **AND** 报告不把"开关未生效导致的 0 差异"解释为"模型无差异"

#### Scenario: 结果不足以支撑结论时如实标注

- **WHEN** 两臂主口径差异小于样本量对应的统计分辨率
- **THEN** 报告标注该差异不构成改进证据
- **AND** 不得据此宣称生成模型档位有效或无效
