# 任务清单：qwen3.8-max 答案层双臂评测

## 1. 前置核实

- [x] 1.1 确认 `qwen3.8-max` 在现用 DashScope key 的可用模型清单内（同清单含 `qwen3.8-max-0902`）
- [x] 1.2 确认模型切换无需改代码：`RagProperties.Llm.resolveModel(deepThinking)` 走 `fast-model`，可由环境变量覆盖
- [x] 1.3 确认 MHR 语料仍在库：空间 `2095544464810774534` = 609 篇 / 4195 块，`embedding` 全非空
- [x] 1.4 确认 100 题答案集与 runner 已在 main（PR #26 已合）：`anchor/mhr-answer-100-fixed.jsonl`、`scripts/run_ask_eval_mhr.py`
- [x] 1.5 API 级探测思考开关行为：不传 → 2.84s 且有 `reasoning_content`；`enable_thinking:false` → 1.06s 且无（参数被接受）

## 2. 实现（思考开关配置门控）

- [x] 2.1 `RagProperties.Llm` 新增 `enableThinking`（`Boolean`，默认 `null`）与 `hasEnableThinking()`
- [x] 2.2 `application.yml` 新增 `rag.llm.enable-thinking: ${RAG_LLM_ENABLE_THINKING:}`（含踩坑说明注释）
- [x] 2.3 `RagLlmClient.doStream` 仅在 `hasEnableThinking()` 时写入 `enable_thinking`
- [x] 2.4 `RagLlmClientTest` 新增用例：未配置/`false`/`true` 三种情形的请求体断言
- [x] 2.5 `RagPropertiesBindingTest` 新增：空串 → `null`、显式值 → `Boolean`、缺键 → `null`

## 3. 验证

- [x] 3.1 定向用例：`RagPropertiesBindingTest`(3) + `RagLlmClientTest`(8) + `CloudApplicationTests`(1) 全绿
      —— 其中 `CloudApplicationTests.contextLoads` 捕获了首次实现 `#{null}` 导致的绑定失败，修复后通过
- [x] 3.2 全量单测：**289/289 全绿**（改动前基线 285，本次 +4 = `RagLlmClientTest` +1、`RagPropertiesBindingTest` +3）
- [x] 3.3 `openspec validate eval-qwen-max-answer-arm --strict` 通过

## 4. 跑批

- [x] 4.1 后端以 `qwen3.8-max` + DashScope 端点启动，探活 1 题确认走通且记录耗时
      —— **含负控生效自证**：先用不存在的模型名 `model-does-not-exist-xyz` 跑一题，后端返回
      `LLM 响应异常 HTTP 404: The model \`model-does-not-exist-xyz\` does not exist...`（DashScope 兼容端
      OpenAI 风格报错体），证明环境变量确实透到 LLM 请求体且 base-url 已指向 DashScope；
      换回 `qwen3.8-max` 后同一题 5.02s 正常作答，且 SSE 无 `reason` 事件（思考已关）
- [x] 4.2 非思考臂 100 题（`RAG_LLM_ENABLE_THINKING=false`），0 错误，`--recompute` 自检 PASS
      —— `results/ask-qwen38max-nonthinking/mhr-ask-20260920-212222.json`，`answeredCount=100 / errorCount=0`，
      复算 `PASS 差异={}`。耗时 mean 8671ms / p50 8093 / p95 13773 / max 62186
- [x] 4.3 思考臂 100 题（不设该变量）—— **本轮取消，不再重跑（负责人决定）**，无数据
      —— 跑到 14/100 时主动终止：`RagAskServiceImpl:66` 硬编码 `new SseEmitter(120_000L)`，
      开思考后单题 11.5s~126.7s，14 题中 2 题触发 `AsyncRequestTimeoutException`，
      经 `onTimeout → future.cancel(true)` 掐断仍在流式的 LLM 调用，runner 记 `error` 不计入 answered；
      被砍的恰是思考最久的题 ⇒ 结构性有偏、不可用于对比。落盘 0 文件。
      **处置**：负责人判定单题均 55s / p95 127s 的档位即便正确率有提升也不可上线，诊断价值有限，
      不再投入一轮跑批。该臂结论**永久标记为「无数据」**，任何报告不得据此推断其高低。
      它揭出的 SSE 超时缺陷独立于本实验，转 IssueLog 第 187 行按「待处理」跟踪。
- [x] 4.4 生效自证：记录两臂单题耗时量级差异，确认思考开关确实改变行为（而非静默未生效）
      —— 同一题：不传 25.52s / `reason` 事件 3082 字节；传 `false` 5.02s / `reason` 事件 **0 字节**。
      后端日志 `thinking=` 非零占比：思考态 13/13，非思考态 0/13

## 5. 产出与收尾

- [x] 5.1 报告 `results/ask-qwen38max-nonthinking/REPORT.md`：非思考臂 vs 双关闭基线并列对比
      —— **路径修正**：原计划写 `results/ask-qwen38max/REPORT.md`，但两臂各自需要独立 out-dir，
      故按既有惯例（`results/<arm>/REPORT.md`，见 `ask-evidence-assembly-only/REPORT.md`）
      落到方案臂目录内；思考臂无数据，其阻塞情况写入该报告第 5 节，不另建空目录
- [x] 5.2 报告须写明：样本量 100（有答案 88）⇒ 正确率标准误约 ±5pt，**差值 < 5pt 不作改进证据**
      —— 已写明（实际标准误 ±6.5pt，本次 +7.0pt 虽超线但 **McNemar p=0.1671 不显著**，仍不作改进证据）
- [x] 5.3 `IssueLog.xlsx` 记录本次踩坑（`#{null}` 绑定失败）与耗时/成本异常
      —— 追加 3 行（第 185–187 行）：①`#{null}` 绑定失败；②`git switch` 清空 `openspec/` 树；
      ③SSE 120s 硬编码超时导致思考臂不可用（**待处理**）。追加前已备份
- [x] 5.4 回填本 `tasks.md` 的实测数字与 Verification 结论
- [x] 5.5 更新工作区记忆（含判官一致性、结果文件位置）

## Verification / Result

**跑批结论：非思考臂数据有效；思考臂因 SSE 超时缺陷无数据。方案未达"可替换默认生成模型"的门槛。**

前置声明：n=100（有答案 88），正确率标准误约 ±6.5pt；**差值 < 5pt 不作改进证据**。
两臂检索侧完全一致（MQ off / 整理 off），唯一变量是生成模型。

| 指标 | 基线 `deepseek-chat` | `qwen3.8-max` 非思考 | delta | 显著性 |
|---|---|---|---|---|
| 正确率（CORRECT） | 0.6500 (65/100) | 0.7200 (72/100) | +0.0700 | **不显著**（McNemar p=0.1671） |
| 上沿（CORRECT+PARTIAL） | 0.8000 | 0.8000 | ±0.0000 | — |
| PARTIAL / INCORRECT | 15 / 20 | 8 / 20 | −7 / **±0** | — |
| 有答案题正确率 | 0.6023 (53/88) | 0.6818 (60/88) | +0.0795 | — |
| 误拒率（判据口径） | 0.0568 (5/88) | 0.2500 (22/88) | **+0.1932** | **显著**（z=3.69, p=0.0002） |
| 误拒率（扣判据误伤） | 0.0568 (5/88) | 0.2045 (18/88) | **+0.1477** | **显著**（z=2.98, p=0.0029） |
| 拒答率（应拒答 12 题） | 1.0000 | 1.0000 | ±0.0000 | — |
| 引用角标覆盖率 | 0.5993 | 0.5171 | **−0.0822** | **显著**（配对 t=−3.12） |
| 平均耗时 | 4359 ms | 8671 ms | **×1.99** | — |

**关键归因（判决迁移矩阵）**：`+7pt` 的唯一来源是 **PARTIAL → CORRECT 的 9 题迁移**
（基线 PARTIAL 15 题里有 9 题升为 CORRECT），而非"补上了不会的题"——
**INCORRECT 两臂均 20 题、净变化为零**（修好 6 题：0555/1385/1678/2054/2437/2529；
同时新错 6 题：0855/0880/1288/1840/1875/2069）。上沿持平 0.8000 与之一致：
证据覆盖面没变宽，只是更敢下结论。

**误拒率翻两番的定性**：22 题误拒 = 13 题真失败（INCORRECT）+ 5 题部分作答（PARTIAL）
+ **4 题判据误伤**（判官判 CORRECT，措辞是"未能找到相关依据"的套话前缀，正文照样作答，
如 MHR-0258 先写套话再答出 sportsbooks）。扣除误伤后仍 18/88 = 20.45%，**显著**
⇒ 不是纯度量假象。该措辞本身是有效失败信号（带措辞题正确率 0.18 vs 不带 0.85），
但**误报率 4/22 ≈ 18%**，判据需改造。

**明确判定：方案未过门槛 —— 不建议把 `qwen3.8-max` 非思考档作为默认生成模型。**
理由：① +7pt 不显著且判官与方案臂同家族（`qwen3.8-max` 答 / `qwen3.8-flash` 判，存在自我偏袒可能，
+7pt 若真有也应打折）；② INCORRECT 绝对量一道未改善；③ 实质未答率显著恶化；
④ 引用支撑显著变差（−8.2pt）；⑤ 耗时翻倍（p95 13.8s、max 62.2s）。

**配置门控改动保留（`enable-thinking`）**：默认路径逐字节无行为变化
（未配置时不发送该字段），DeepSeek 链路完全不受影响；该开关本身是本轮的可复用增量，
与"是否采用 qwen3.8-max"是两件事，故不回滚。

**取消项（非未完成项）**：思考臂 100 题（4.3）阻塞于 `RagAskServiceImpl:66` 的 120s 硬编码 SSE 超时，
已按 AGENTS.md 第 4 节上报；负责人判定**本轮不再重跑**，该臂不产出数据。
SSE 超时缺陷与本实验解耦，转 IssueLog 第 187 行「待处理」，不阻塞本变更归档。

## 回滚方案

`git revert` 本分支提交即可。改动为默认路径无行为变化的增量配置项，
无数据迁移、无索引重建。
