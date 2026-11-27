# 按任务为协作角色选择模型

## Why

当前规则固定总管使用 GPT-6 Astra High，开发和测试统一使用 GPT-6.1 Sol Medium。希望日常工作采用 GPT-6.2 Sol，将更低成本的模型用于机械任务，并在设计风险或验收证据值得额外投入时选用 Astra。

## What Changes

由 AGENTS.md 维护一份角色选择表。总管在启动会话或派单时按下表选择；常规工作优先 Standard 速度。

| 角色 | 默认 | 可选择的例外 |
| --- | --- | --- |
| 总管 | GPT-6.2 Sol High | 权限模型变更、数据库迁移或跨系统契约冲突的设计阶段可选 GPT-6.2 Astra High。总管模型由会话启动者选择。 |
| 开发 | GPT-6.2 Sol Medium | 纯文档整理或可机械核验的批量替换可选 GPT-6 Luna Low；并发、缓存一致性或跨模块排错可用 GPT-6.2 Sol High。 |
| 独立测试 | GPT-6.2 Sol Medium | 主要依靠截图或桌面操作判断结果时可用 GPT-6.1 Terra Medium。 |
| 独立只读复核 | 按现行复核条件另派实例 | 验收证据冲突或有争议的高风险复核可用 GPT-6.2 Astra High。 |

总管在派发前确认当前工具支持所选模型和推理强度，显式传入对应的 model、reasoning_effort 和 fork_turns="none"。任务符合例外条件时可预先选型；所选组合不可用时沿用停止相关派发并上报的规则。修改后的新一轮测试仍需新实例、冻结快照及独立证据。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

无。现有 agent-development-workflow 已定义职责、验收与复核；本次只更新履行这些职责时的选型说明。

## Impact

L0 文档变更。规划实施范围为根目录 AGENTS.md、AGENTS_manager.md、AGENTS_rules.md、AGENTS_test.md 和 README.md 的协作角色表。角色文件引用共同表并说明各自的操作，README 只同步协作模型说明。产品运行时模型选择器、数据库、公开 API、权限实现及 CI/CD 均在范围之外。
