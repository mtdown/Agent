# Tasks

## 1. 本地 OpenSpec 技能

- [x] 1.1 原子任务 P1-T1：创建 local propose 技能；按 A1–A3 独立验证后方可勾选。
- [x] 1.2 原子任务 P1-T2：创建 local apply 技能；读取技能并验证仅执行任务卡范围、不勾选，保留 CLI 校验与原版。
- [x] 1.3 原子任务 P1-T3：创建 local explore 技能；读取技能并验证已授权范围及不重复确认、未授权变更停止。
- [x] 1.4 原子任务 P1-T4：创建 local update 技能；读取技能并验证失败记录与固定验收、需求设计变更边界。
- [x] 1.5 原子任务 P1-T5：创建 local sync 技能；读取技能并验证规格合并、校验和失败停止。
- [x] 1.6 原子任务 P1-T6：创建 local archive 技能；读取技能并验证同步先行、归档校验和授权边界。

### P1-T1 固定验收（每轮保持原文）

A1. `.agents/skills/openspec-propose-local/SKILL.md` 是名称与目录匹配、可发现的独立技能；它只接收总管已创建且归属已确认的 change，不创建 change，不调用 `openspec new change`；change 缺失或归属不明时停止上报。
    verify：`Get-Content -Raw -Encoding UTF8 .agents/skills/openspec-propose-local/SKILL.md`
    预期：退出码 0；逐条核对 frontmatter、已有 change、缺失或归属不明处置，且无创建 change 指令。

A2. local propose 保留 schema 依赖顺序、status/instructions、逐项工件生成和校验等必要能力；完整开发任务已授权时规划后可按角色交接继续，纯规划或用户指定停点不继续实施。
    verify：`Get-Content -Raw -Encoding UTF8 .agents/skills/openspec-propose-local/SKILL.md`
    预期：退出码 0；审阅两种授权场景与 CLI 依赖及工件检查完整且不矛盾。

A3. 原版 propose 技能不变，local 技能通过结构校验，OpenSpec change 严格校验通过。
    verify：`git diff --exit-code -- .agents/skills/openspec-propose/SKILL.md; python -X utf8 C:/Users/origin/.codex/skills/.system/skill-creator/scripts/quick_validate.py .agents/skills/openspec-propose-local; openspec validate openspec-local-workflow --strict`
    预期：每条命令退出码 0；技能校验通过，OpenSpec 输出 change valid。

### P1-T2 固定验收（每轮保持原文）

B1. `.agents/skills/openspec-apply-change-local/SKILL.md` 是名称与目录匹配的技能，只实施总管任务卡指定的一个原子任务，完成后交付自检与回执；不自行执行其他任务，不负责勾选 tasks.md。
    verify：`Get-Content -Raw -Encoding UTF8 .agents/skills/openspec-apply-change-local/SKILL.md`
    预期：退出码 0；逐条核对任务范围、交接和不勾选，技能内无任务完成后勾选指令。

B2. local apply 保留 change、store、status、instructions、schema 及上下文读取；仅授权范围内执行，缺失工件或无法判定时停止，不绕过阻塞；不自行声称独立验收 PASS。
    verify：`Get-Content -Raw -Encoding UTF8 .agents/skills/openspec-apply-change-local/SKILL.md`
    预期：退出码 0；逐条核对正常、阻塞和交接场景，保留必要校验。

B3. 原版 apply 技能不变，local 技能通过结构校验，OpenSpec change 严格校验通过。
    verify：`git diff --exit-code -- .agents/skills/openspec-apply-change/SKILL.md; python -X utf8 C:/Users/origin/.codex/skills/.system/skill-creator/scripts/quick_validate.py .agents/skills/openspec-apply-change-local; openspec validate openspec-local-workflow --strict`
    预期：每条命令退出码 0；技能校验通过，OpenSpec 输出 change valid。

### P1-T3 固定验收（每轮保持原文）

C1. `.agents/skills/openspec-explore-local/SKILL.md` 是名称与目录匹配的独立技能，探索任务、检查需求与已有 change；总管创建 change，技能不得创建 change 或实施业务代码。
    verify：`Get-Content -Raw -Encoding UTF8 .agents/skills/openspec-explore-local/SKILL.md`
    预期：退出码 0；包含任务探索、总管创建 change 和禁止自身创建或实施的清楚条款。

C2. 用户明确授权完整流程时，授权范围内可交接规划而不重复确认；用户只授权探索、设置停点、超出授权或存在需求／行为／设计歧义时不擅自推进；保留 store、status、instructions 与项目上下文处理。
    verify：`Get-Content -Raw -Encoding UTF8 .agents/skills/openspec-explore-local/SKILL.md`
    预期：退出码 0；分别核对完整授权、仅探索、停点、歧义和 CLI 上下文路径。

C3. 原版 explore 技能不变，local 技能通过结构校验，OpenSpec change 严格校验通过。
    verify：`git diff --exit-code -- .agents/skills/openspec-explore/SKILL.md; python -X utf8 C:/Users/origin/.codex/skills/.system/skill-creator/scripts/quick_validate.py .agents/skills/openspec-explore-local; openspec validate openspec-local-workflow --strict`
    预期：每条命令退出码 0；技能校验通过，OpenSpec 输出 change valid。

### P1-T4 固定验收（每轮保持原文）

D1. `.agents/skills/openspec-update-change-local/SKILL.md` 是名称与目录匹配的独立技能；仅更新总管确认的已有 change 规划，不修改业务代码、不创建 change，保留 store、status、已有工件与一致性检查。
    verify：`Get-Content -Raw -Encoding UTF8 .agents/skills/openspec-update-change-local/SKILL.md`
    预期：退出码 0；检查技能身份、已有 change、规划范围、CLI 与一致性约束。

D2. 测试失败可按需记录状态、修复任务及复测证据，不降低固定验收；需求、对外行为或设计需要变化时向总管报告并按决定更新有关工件；完整授权内不重复确认，超出授权或存在歧义时停止。
    verify：`Get-Content -Raw -Encoding UTF8 .agents/skills/openspec-update-change-local/SKILL.md`
    预期：退出码 0；逐项核对失败记录、固定验收、需要决策的变化及授权边界。

D3. 原版 update 技能不变，local 技能通过结构校验，OpenSpec change 严格校验通过。
    verify：`git diff --exit-code -- .agents/skills/openspec-update-change/SKILL.md; python -X utf8 C:/Users/origin/.codex/skills/.system/skill-creator/scripts/quick_validate.py .agents/skills/openspec-update-change-local; openspec validate openspec-local-workflow --strict`
    预期：每条命令退出码 0；技能校验通过，OpenSpec 输出 change valid。

### P1-T5 固定验收（每轮保持原文）

E1. `.agents/skills/openspec-sync-specs-local/SKILL.md` 是名称与目录匹配的独立技能；仅按总管确认的 change 和选定 store 同步已存在的 delta spec，明确路径来自 status，保留主规格合并、未涉及内容及有效性校验。
    verify：`Get-Content -Raw -Encoding UTF8 .agents/skills/openspec-sync-specs-local/SKILL.md`
    预期：退出码 0；逐项核对 change、store、delta 路径、主规格合并与校验。

E2. 技能在写入主规格前获取有效的 specs instructions 并应用规则；无 delta、不明确或规则获取失败时停止；同步失败或与 delta 不一致时不得报告成功或归档。
    verify：`Get-Content -Raw -Encoding UTF8 .agents/skills/openspec-sync-specs-local/SKILL.md`
    预期：退出码 0；逐项检查写前前提、无 delta、失败与验证结果。

E3. 原版 sync 技能不变，local 技能通过结构校验，OpenSpec change 严格校验通过。
    verify：`git diff --exit-code -- .agents/skills/openspec-sync-specs/SKILL.md; python -X utf8 C:/Users/origin/.codex/skills/.system/skill-creator/scripts/quick_validate.py .agents/skills/openspec-sync-specs-local; openspec validate openspec-local-workflow --strict`
    预期：每条命令退出码 0；技能校验通过，OpenSpec 输出 change valid。

### P1-T6 固定验收（每轮保持原文）

F1. `.agents/skills/openspec-archive-change-local/SKILL.md` 是名称与目录匹配的独立技能，按总管确认的 change 和选定 store 检查工件与 tasks，所有阶段及独立验收完成后方可归档。
    verify：`Get-Content -Raw -Encoding UTF8 .agents/skills/openspec-archive-change-local/SKILL.md`
    预期：退出码 0；核对 change、store、工件、tasks 和独立验收的前提。

F2. 存在 delta 时须先通过 local sync 并核对主规格；同步失败、不一致、校验失败、任务未完成或目标已存在时停止，不归档；归档保留元数据、验证位置与状态；已明确授权时不重复询问，超出授权或用户停点仍停止。
    verify：`Get-Content -Raw -Encoding UTF8 .agents/skills/openspec-archive-change-local/SKILL.md`
    预期：退出码 0；逐项核对同步、失败、冲突、归档验证和授权分支。

F3. 原版 archive 技能不变，local 技能通过结构校验，OpenSpec change 严格校验通过。
    verify：`git diff --exit-code -- .agents/skills/openspec-archive-change/SKILL.md; python -X utf8 C:/Users/origin/.codex/skills/.system/skill-creator/scripts/quick_validate.py .agents/skills/openspec-archive-change-local; openspec validate openspec-local-workflow --strict`
    预期：每条命令退出码 0；技能校验通过，OpenSpec 输出 change valid。

## 2. 角色与主规格衔接

- [ ] 2.1 原子任务 P2-T1：四份 AGENTS 指向六个 local 技能，落实总管勾选、开发过程记录及独立测试汇总；读取四份文件核对前述角色、失败及 BLOCKED 分支，严格校验 change。

## 交付约束

每个 local 技能单独开发和全新测试，验证后才开始下一个；原版六个技能字节保持不变。每个任务派单时根据以上任务及设计写明固定验收和命令，分发后不降低。阶段内任务全部 PASS、核对文件范围后上传。开发普通已解决错误只留记录；测试按任务卡读取记录、独立验收并汇总。总管创建 change，local propose 不创建；测试 PASS 后总管勾选。回滚恢复变更前四份规则与主规格，删除本 change 新建的 local 技能，保留问题证据。
