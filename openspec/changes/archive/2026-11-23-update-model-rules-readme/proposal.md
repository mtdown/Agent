## Why

项目尚未固定总管、开发与测试的模型分工；README 的本地配置和近期开发能力介绍滞后，评测仍使用 @6 展示口径。负责人要求固定角色模型、沿用 README 现有展示格式更新开发内容，并改用 @10 口径。

## What Changes

- AGENTS.md 固定总管 GPT-6 Astra / high、开发和测试 GPT-6.1 SOL / medium；AGENTS_manager.md 明确显式派发参数及不可用时上报，不替换模型。
- README 保留标题、徽章、章节顺序和表格形式，补充本地模型档位、统一密钥入口、站点访问与备案、协作模型分工，纠正旧配置说明。
- 从现有评测 JSON 提取 recall@10、docRecall@10、hitRate@10，更新总表、分组、对比和复现性说明；历史结果不冒充本次实测，不改评测代码或运行配置。

## Capabilities

### New Capabilities

无。纯文档改动，设置 `skip_specs: true`。

### Modified Capabilities

无产品行为变更；不修改主规格或新增 delta。

## Impact

L0：实施文件仅 AGENTS.md、AGENTS_manager.md、README.md；不修改业务代码、依赖、数据库、CI/CD、公开 API、权限模型或部署配置。设计指令中的架构、依赖、迁移及安全设计条件均不适用，省略 design.md。

分支：`feature/model-rules-readme开发`；基线：`a8784092ed583b76cade5e80f159d70b312d8fc0`。负责人明确要求本次仅由主智能体完成，不创建开发或测试子智能体，采用本人静态核验，结果不得称为独立测试 PASS。该次授权仅记入本 change，不改变长期角色隔离规则。

验收：模型及强度精确对应；派发显式指定参数；README 结构保留、能力有源码依据、所有 @10 数值可追溯、无 @6 指标残留、相对链接有效、UTF-8 解码和差异检查通过。任务及回滚见 tasks.md。
