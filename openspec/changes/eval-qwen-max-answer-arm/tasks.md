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
- [ ] 4.2 非思考臂 100 题（`RAG_LLM_ENABLE_THINKING=false`），0 错误，`--recompute` 自检 PASS
- [ ] 4.3 思考臂 100 题（不设该变量），0 错误，`--recompute` 自检 PASS
- [ ] 4.4 生效自证：记录两臂单题耗时量级差异，确认思考开关确实改变行为（而非静默未生效）

## 5. 产出与收尾

- [ ] 5.1 报告 `results/ask-qwen38max/REPORT.md`：三臂（历史基线 / 非思考 / 思考）并列对比
- [ ] 5.2 报告须写明：样本量 100（有答案 88）⇒ 正确率标准误约 ±5pt，**差值 < 5pt 不作改进证据**
- [ ] 5.3 `IssueLog.xlsx` 记录本次踩坑（`#{null}` 绑定失败）与耗时/成本异常
- [ ] 5.4 回填本 `tasks.md` 的实测数字与 Verification 结论
- [ ] 5.5 更新工作区记忆（含判官一致性、结果文件位置）

## Verification / Result

> 待跑批完成后回填。

## 回滚方案

`git revert` 本分支提交即可。改动为默认路径无行为变化的增量配置项，
无数据迁移、无索引重建。
