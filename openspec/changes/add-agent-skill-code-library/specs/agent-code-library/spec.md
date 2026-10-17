# Spec Delta

## Purpose

为 Skill 提供可在线编写、发布、验证和追溯的 Python/Java 代码项，使 Agent 能引用确定的代码版本和入口，而不依赖临时生成的未定位代码。

## ADDED Requirements

### Requirement: 在线代码项与发布版本
系统 SHALL 允许所有者和网页端 Agent 代表所有者创建、编辑并发布 Python 或 Java 代码项；条目 SHALL 包含名称、能力简介、语言、入口、输入输出契约、源代码、所有者、共享范围和不可变的发布版本。

#### Scenario: 用户发布代码
- **WHEN** 所有者在代码库编辑代码并发布
- **THEN** 系统保存新的不可变版本，旧版本仍可查看和被 Skill 引用

#### Scenario: Agent 发布代码
- **WHEN** Agent 为当前用户的任务编写代码并发布
- **THEN** 代码项归当前用户所有，且可在代码库查看其内容与版本

### Requirement: 代码可用性验证与绑定
Agent SHALL 在将代码版本写入 Skill 草稿前用声明的样例验证其入口、输入输出和执行结果；失败版本 SHALL 保留诊断信息且不得被绑定为该草稿的可用步骤。

#### Scenario: 样例通过
- **WHEN** 发布版本通过样例执行和结果检查
- **THEN** Agent 可将其代码项、版本和入口绑定到 Skill 草稿

#### Scenario: 样例失败
- **WHEN** 发布版本编译、运行或结果检查失败
- **THEN** 系统记录失败阶段和原因，Agent 不将该版本绑定为可用步骤

### Requirement: 固定引用和共享权限
已启用 Skill SHALL 固定引用明确的已发布代码版本；代码发布新版 SHALL NOT 自动更改已有 Skill；代码项默认私有，只有所有者可编辑和发布，共享用户仅可查看和使用被共享版本。

#### Scenario: 发布新版本
- **WHEN** 被 Skill 引用的代码项发布新版本
- **THEN** 原 Skill 仍执行原固定版本，直到所有者明确更新 Skill 引用

#### Scenario: 越权修改
- **WHEN** 共享用户尝试修改或发布不属于自己的代码项
- **THEN** 系统拒绝操作且原版本不变
