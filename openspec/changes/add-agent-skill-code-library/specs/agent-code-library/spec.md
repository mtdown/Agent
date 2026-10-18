# Spec Delta

## Purpose

为 Skill 提供可在线编写、发布、验证和追溯的 Python/Java 代码项，使 Agent 能引用确定的代码版本和入口，而不依赖临时生成的未定位代码。

## ADDED Requirements

### Requirement: 在线代码项与发布版本
系统 SHALL 允许所有者和网页端 Agent 代表所有者创建、编辑 Python 或 Java 代码项；每次保存 SHALL 自动发布新的不可变版本，不另设手动发布步骤。条目 SHALL 包含名称、能力简介、语言、入口、输入输出契约、源代码、所有者和共享范围。

#### Scenario: 用户保存代码
- **WHEN** 所有者在代码库创建或修改代码并保存
- **THEN** 系统保存新的不可变版本，旧版本仍可查看和被 Skill 引用

#### Scenario: Agent 保存代码
- **WHEN** Agent 为当前用户的任务编写代码并保存
- **THEN** 代码项归当前用户所有，且可在代码库查看其内容与版本

### Requirement: 发布版本直接绑定与验证状态
系统 SHALL 允许当前用户把有权访问的已发布代码版本和实际存在的入口直接绑定到 Skill 草稿，不以样例验证状态为前置条件。系统 SHALL 独立记录 `UNVERIFIED`、`PASSED` 或 `FAILED`，并不得把发布或绑定表述为已运行成功。

#### Scenario: 未验证版本绑定
- **WHEN** 当前用户把刚保存的 `UNVERIFIED` 代码版本和其入口写入 Skill 草稿
- **THEN** 系统允许绑定固定版本，并继续显示该版本为 `UNVERIFIED`

#### Scenario: 样例失败
- **WHEN** 发布版本编译、运行或结果检查失败
- **THEN** 系统记录失败阶段和原因；绑定关系不自动改写，Agent 不声称该版本已成功运行

#### Scenario: 无权或不存在的版本
- **WHEN** 用户绑定无权访问、已删除或不存在的代码版本或入口
- **THEN** 系统拒绝保存 Skill，既有版本不变

### Requirement: 固定引用和共享权限
已启用 Skill SHALL 固定引用明确的已发布代码版本；代码保存新版 SHALL NOT 自动更改已有 Skill；代码项默认私有，只有所有者可编辑并保存新版本，共享用户仅可查看和使用被共享版本。

#### Scenario: 保存新版本
- **WHEN** 被 Skill 引用的代码项保存新版本
- **THEN** 原 Skill 仍执行原固定版本，直到所有者明确更新 Skill 引用

#### Scenario: 越权修改
- **WHEN** 共享用户尝试修改或发布不属于自己的代码项
- **THEN** 系统拒绝操作且原版本不变
