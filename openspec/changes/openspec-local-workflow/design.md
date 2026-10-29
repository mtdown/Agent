# Design

## Context

参见 proposal。用户确认在 feature/agent-development-workflow开发 上继续现有 change。当前 OpenSpec 原版技能可自动发现，但其通用行为与本项目角色交接存在冲突。

## Goals / Non-Goals

**Goals:** 六个 local 技能以项目规则为调用边界；保留原版 CLI 校验和规格同步安全条件；三角色使用同一验收和状态约定。

**Non-Goals:** 不更改原版技能或外部 OpenSpec CLI；不引入依赖、worktree、业务行为变更或自动合并 main。

## Decisions

1. 在 `.agents/skills/openspec-{explore,propose,apply-change,update-change,sync-specs,archive-change}-local/SKILL.md` 建立独立技能，名称与目录一致，描述明确适用于本项目流程。AGENTS 指向 local 版本；原版保持字节不变。
2. 总管执行 `start-task.ps1` 及 `openspec new change`。local propose 接收已有 change 并生成规划，不创建 change；不存在或归属不明时停止上报。避免重复创建。
3. 开发 local apply 只实施派发原子任务，不执行勾选；测试 PASS、证据和快照核对后总管勾选。测试 FAIL、BLOCKED、证据不足不勾选或上传。
4. 明确完整授权覆盖指定流程内的常规操作；仅规划、用户设置停点、超出授权和需求／对外行为／设计需变化等情况保留停止和决策。
5. 开发记录过程和实际修改，普通已解决错误不逐条主动上报；测试者按任务卡读取对应记录、独立验收并向总管汇总事实及问题。开发仍需交付完成状态，不能继续时报告 BLOCKED，防止任务无状态。测试任务卡允许指定记录位置及校验值作为验收上下文，开发者推测不能代替测试证据。
6. 问题与进度更新可记录 FAIL、修复及复测，不改变固定验收；需求、对外行为或设计变化经决定后更新相关工件。主规格区分环境／权限阻断为 BLOCKED、不能判断为证据不足。
7. 六个 local 副本保留 store 选择、status/instructions、schema、spec 合并、校验及归档防护等必要机制；只删除与三角色规则冲突或重复的内容，禁止剥离关键安全和数据完整性检查。

## Risks / Trade-offs

- [原版升级覆盖或 local 漂移] → local 和原版分离，并记录来源及检查方法。
- [缺少过程记录或无状态阻塞] → 记录路径交给测试者，完成或 BLOCKED 状态仍须交付。
- [盲测信息污染] → 测试者独立验证，过程记录作为事实材料，不接收开发结论作为 PASS 依据。

## Migration Plan

按语义阶段生成并验证 local 技能，再更新角色规则和规格；每轮由新的测试实例独立验证。回滚时恢复变更前角色规则和主规格，删除本 change 新增的 local 技能；保留原版与问题记录。
