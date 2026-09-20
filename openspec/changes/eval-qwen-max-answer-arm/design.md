# 设计：生成模型换档实验 + 思考开关配置门控

## 1. 为什么用「配置门控」而不是硬编码 `enable_thinking=false`

最省事的做法是在 `RagLlmClient.doStream` 里无条件加 `body.set("enable_thinking", false)`。
否决理由：**这个字段不是 OpenAI 兼容协议的公共字段**，而是 DashScope/通义侧的扩展参数。
本项目默认生成模型是 DeepSeek（`rag.llm.base-url` 默认 `https://api.deepseek.com`），
给不识别该字段的服务写入未知参数，属于把实验需要夹带成生产行为。

采用门控后，不配置该键时请求体与本变更引入前**逐字节一致**（单测锁死），
DeepSeek 链路零风险，实验需求用环境变量满足。

## 2. 为什么用 `Boolean` + 空串缺省，而不是 `#{null}`

最初写成 `${RAG_LLM_ENABLE_THINKING:#{null}}`（Spring 里表达"缺省即 null"的常见写法）。
**实测失败**：SpEL 默认值在 `@ConfigurationProperties` 绑定时不会被求值，字面量 `#{null}`
会以字符串身份参与 `Boolean` 转换：

```
Failed to bind properties under 'rag.llm.enable-thinking' to java.lang.Boolean
Caused by: IllegalArgumentException: Invalid boolean value '#{null}'
```

`ignoreInvalidFields` 默认为 `false` ⇒ 直接**启动失败**，被 `CloudApplicationTests.contextLoads` 抓到。
（写入 yml 的属性名自带 `:` 空默认值时，Spring 的 `StringToBooleanConverter` 对空串返回 `null`，
因此 `:` 形式是可用的；这一点由 `RagPropertiesBindingTest` 固化，防止再次退回错误写法。）

## 3. 开关放哪一层：请求体构造处

`RagLlmClient.doStream` 是唯一组装 `/chat/completions` 请求体的地方，判断放在这里：

```java
body.set("model", config.resolveModel(deepThinking));
if (config.hasEnableThinking()) {
    body.set("enable_thinking", config.getEnableThinking());
}
```

- 与 `resolveModel(deepThinking)` 同层，模型档位与思考开关两个维度各自独立可控
- 重试路径（`streamChat` 的第二次 `doStream`）复用同一段代码，不需要额外处理

## 4. 实验设计：单变量

| 维度 | 基线臂（历史） | 非思考臂（本次） | 思考臂（本次） |
|---|---|---|---|
| 生成模型 | `deepseek-chat` | `qwen3.8-max` | `qwen3.8-max` |
| `enable_thinking` | 不适用（DeepSeek） | `false` | 不发送（服务默认 = 开） |
| 题目集 | `mhr-answer-100-fixed.jsonl`（100 题） | 同 | 同 |
| 判官 | `qwen3.8-flash` temperature 0 三分类 | 同 | 同 |
| 检索 | 多查询关、合块关、池深 50、K=6 | 同 | 同 |

- **判官必须与基线一致**：判官换了就无法判断差异来自生成还是来自判分。
- 三臂共用同一份题目集与同一空间（`2095544464810774534`，609 篇 / 4195 块）⇒ 逐题可比。
- 思考臂的价值：`max` 档的主要卖点是推理能力，若只测非思考臂，等于用最贵的模型测它被削掉一半能力的形态。

## 5. 度量与判据

沿用 `run_ask_eval_mhr.py` 的既有口径（与历史基线同源）：

- 主口径：LLM 判官三分类的 **CORRECT 率**；上沿 = CORRECT + PARTIAL
  （PARTIAL = 列了相符证据但没下结论，或只答了多问中的一问）
- 零 LLM 成本辅助项：有答案题正确率、**误拒率**（有答案题被判拒答的比例）、
  拒答率（应拒答题里判拒答的比例）、引用角标覆盖率
- 结果落盘 `eval/datasets/mhr-rag/results/ask-<arm>/`，含逐题明细，可离线复算

**判据强度声明（必须写进报告）**：100 题里 88 道有答案题，正确率标准误约 ±5pt
⇒ **两臂差异 < 5pt 不作为改进证据**；只报差异、不宣称结论。

## 6. 启动方式与影响面

- 后端用项目 `start-dev.ps1` 启动，通过环境变量切换生成模型：
  `RAG_LLM_BASE_URL`（DashScope compatible-mode）、`RAG_LLM_FAST_MODEL=qwen3.8-max`、
  `RAG_LLM_API_KEY`、`RAG_LLM_ENABLE_THINKING=false`（仅非思考臂）
- **不改任何默认配置**：环境变量不设时全部回到 DeepSeek 链路
- 评测只打 `POST /open/rag/ask`，不需要前端

## 7. 回滚

代码改动是两个文件各一处（`RagProperties` 加字段与判空方法、`RagLlmClient` 加条件写入）
外加 yml 一个键，均为**默认路径无行为变化**的增量。回滚 = `git revert` 本分支提交，
无数据迁移、无索引重建、无配置依赖。
