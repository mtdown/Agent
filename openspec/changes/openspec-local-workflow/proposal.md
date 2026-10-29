# Proposal

## Why

现有 OpenSpec 技能的创建 change、任务勾选及确认停点与本项目总管、开发、测试的职责不一致。需要独立的项目本地副本，使已授权的三角色流程可连续运行，并保留原版技能。

## What Changes

- 为 explore、propose、apply、update、sync、archive 建立可发现的 openspec local 技能副本，保留原版及其必要 CLI 校验。
- 总管检查工作区、建立任务分支并创建 change；local propose 只使用已有 change，不创建或重复询问。
- local apply 仅执行一个原子任务，不操作任务复选框；总管在独立验收 PASS 后勾选。
- 用户已明确授权的操作连续执行；超出授权及需求、对外行为或设计需要变化时仍需决策。
- 开发普通试错记录供验收者查阅汇总；需求、对外行为或设计需要变化时向总管报告。无法完成任务时交付 BLOCKED 状态，不隐瞒阻塞。
- 统一 PASS、FAIL、BLOCKED、证据不足判定，允许在不改变固定验收时记录修复与复测。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `agent-development-workflow`: 为三角色编排指定项目本地 OpenSpec 技能，明确任务勾选、过程记录与结果分类。

## Impact

新增六个项目技能副本，调整四份角色规则及本 change 工件。保留原版技能，不修改业务、脚本、数据库、CI 或公开 API。
