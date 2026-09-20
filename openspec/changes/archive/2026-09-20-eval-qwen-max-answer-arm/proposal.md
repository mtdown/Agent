# 答复生成模型换用 qwen3.8-max 的双臂评测

## Why

英文泛化赛道的答案层评测停在一个很难看的数字上：固定 100 题（`mhr-answer-100-fixed.jsonl`）上，
`deepseek-chat` 作为生成模型的正确率是 **0.6500**，且把检索层 `recall@6` 从 0.7187 抬到 0.7650
（仅 chunk 整理臂，**+4.6pt**）后，答案正确率**一点没动**（0.6500 → 0.6500），误拒率反而翻倍
（5.68% → 10.23%）。

也就是说，过去几轮全部投在「检索给得更多/更准」上，但答案层纹丝不动。这留下一个尚未验证的假设：

> 目前答案层的天花板，到底是**检索给的材料不够**，还是**生成模型判断力不够**？

要证伪/证实这个假设，唯一干净的做法是**只换生成模型、其他一切不动**，看答案正确率是否提升。
本次选用同一供应商的更高档模型 `qwen3.8-max`（基线用的是 `deepseek-chat`）。

同时暴露出一个工程缺口：`RagLlmClient` 无法控制「思考模式」。DashScope 系模型用请求体的
`enable_thinking` 控制，实测 `qwen3.8-max` **不传该字段时默认开思考**（返回里有 `reasoning_content`，
单题 2.84s；显式设 `false` 后 1.06s）。没有这个开关就无法把"模型档位"和"是否思考"两个变量分开。

## What Changes

1. **`rag.llm` 新增 `enable-thinking` 配置项（默认不发送）**
   - `RagProperties.Llm.enableThinking`（`Boolean`，默认 `null`）
   - `application.yml`：`enable-thinking: ${RAG_LLM_ENABLE_THINKING:}`
   - `RagLlmClient` 仅在显式配置时才把 `enable_thinking` 写进请求体
   - 配套单测：`RagLlmClientTest`（请求体是否携带）+ `RagPropertiesBindingTest`（绑定契约）

2. **英文答案层双臂评测（各 100 题，同题同判官）**
   - 非思考臂：`qwen3.8-max` + `RAG_LLM_ENABLE_THINKING=false`
   - 思考臂：`qwen3.8-max`，不设该变量（走服务默认，即开思考）
   - 判官固定 `qwen3.8-flash` 三分类，与历史基线 0.6500 **同口径**，保证差异可归因于生成模型
   - 检索配置与基线一致：多查询关、chunk 整理关、池深 50、K=6

## Non-Goals

- 不改检索链路（混合检索 / 重排 / 多查询 / 合块）任何参数
- 不改 `SYSTEM_PROMPT` 与答案层 prompt 组装
- 不引入多供应商抽象层，不为其他厂商的思考参数做适配
- **不把 `qwen3.8-max` 设为默认生成模型**；本次只产出可对比的实验数据
- 不评估中文主赛道（`golden.v2.jsonl`）——中文答案层基线在归档包内，口径未与英文对齐，留作后续

## Acceptance Criteria

1. `enable-thinking` 未配置时，`RagLlmClient` 发出的请求体与本变更引入前**逐字节一致**（单测断言不含该字段）。
2. 显式配置 `false` / `true` 时，请求体分别携带 `"enable_thinking":false` / `true`。
3. 全量后端单测通过（本次改动前基线 286 用例）。
4. **跑批（范围经负责人调整）**：非思考臂 100 题跑完、0 错误，判官与基线同一模型与提示 —— **已达成**。
   思考臂原计划同样跑 100 题，但撞上 `RagAskServiceImpl:66` 硬编码的 120s SSE 上限
   （单题均 55s、p95 127s；跑到 14/100 时 2 题触发 `AsyncRequestTimeoutException`，
   经 `onTimeout → future.cancel(true)` 掐断仍在流式的 LLM 调用），**本轮取消、不产出数据**。
   该臂的「无数据」状态在报告中如实标注，任何结论不得推断其高低；SSE 缺陷转 IssueLog 单独跟踪。
5. 产出对比报告：总体正确率 / 上沿 / 分题型 / 有答案题正确率 / 误拒率 / 拒答率 / 引用覆盖率 / 耗时，
   并与 `deepseek-chat` 基线 0.6500 并列。
6. 若任一臂正确率提升 ≥ 5pt 且误拒率未恶化，则给出"换生成模型"作为下一步方向的建议依据；
   否则明确写下"答案层瓶颈不在生成模型档位"这一否证结论。

## 风险

- 思考臂单题耗时不可预估（后端超时 120s），100 题可能需要 40–80 分钟；已开 checkpoint，可续跑。
- 成本：两臂各 100 次生成 + 100 次判官调用，`qwen3.8-max` 为最高档定价。
- 单臂 100 题、88 道有答案题，统计分辨率有限（正确率标准误约 ±5pt）⇒ **差值 < 5pt 不构成改进证据**。
