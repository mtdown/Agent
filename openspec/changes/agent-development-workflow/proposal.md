# Proposal

## Why

现有三份协作规则的角色边界、测试隔离和交接证据不够清楚，且存在会掩盖重大缺陷或阻塞正常任务的规定。需要按照负责人确认的总管、开发、独立测试模式重写规则，使流程可执行并可追溯。

## What Changes

- 根 `AGENTS.md` 只保留共同规则和角色入口；总管职责单独放在 `AGENTS_manager.md`，并明确总管必须读取该文件。
- 开发规则规定实现、自检、问题分级记录及修复交接；测试规则规定每轮新建独立上下文、被测快照、证据和结论。
- 固定验收与清单外重大风险分别处理；证据不足或阻塞不视为通过；保留三轮上限。
- 测试报告由总管核对，独立记录复核按风险触发；一般试错不强制生成 HTML。
- 阶段通过后仍由总管检查范围并使用现有脚本上传，合并 main 须经负责人授权。

## Capabilities

### New Capabilities

- `agent-development-workflow`: 定义总管、开发与每轮全新测试智能体的任务交接、独立验证和阶段交付。

### Modified Capabilities

无。

## Impact

- 规则文件：`AGENTS.md`、`AGENTS_manager.md`、`AGENTS_rules.md`、`AGENTS_test.md`；OpenSpec 规划工件。
- 沿用现有任务分支、IssueLog、报告和上传脚本，不更改业务代码、API、数据库或脚本。
