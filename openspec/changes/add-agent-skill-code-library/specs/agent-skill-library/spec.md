# Spec Delta

## Purpose

为网页端 Agent 提供独立于 RAG 文档的版本化 Skill 目录，使成功任务可沉淀为默认启用的 Skill，按适用条件查找、读取并复用。

## ADDED Requirements

### Requirement: 结构化 Skill 与版本
系统 SHALL 将 Skill 的名称、能力简介、适用及不适用条件、输入参数及校验、执行步骤、预期结果、失败回退、代码版本引用、验收样例、所有者、共享范围和状态保存为可查看的版本；每个涉及代码的步骤 MUST 指向明确的代码项、版本和入口。

#### Scenario: 查看 Skill 详情与来源
- **WHEN** 用户打开可访问的 Skill 卡片及某一版本
- **THEN** 页面显示结构化内容、版本和代码引用，并能跳转到所引用的代码版本

#### Scenario: 修改已启用 Skill
- **WHEN** 所有者编辑已启用 Skill 并保存
- **THEN** 系统产生新版本并立即设为启用版本，既有版本及其代码引用仍可追溯

### Requirement: 保存即启用
Agent SHALL 能在成功完成任务后主动提议或自动生成 Skill；创建和修改成功后，最新版本 SHALL 默认启用。所有者 SHALL 仍可手动停用或重新启用某个历史版本。保存失败 SHALL NOT 改变原启用版本。

#### Scenario: 内置创建规则结构化填充
- **WHEN** 用户用自然语言要求网页端 Agent 创建一个不依赖代码的 Skill
- **THEN** Agent 使用预置创建规则填充现有 Skill 字段，由程序组装、校验并保存为当前用户私有且已启用的 v1，聊天中无需展示空白模板
- **AND** 如果字段无效，工具返回具体字段路径；只有收到 Skill ID 后，Agent 才能声称已保存并启用

#### Scenario: 自动沉淀
- **WHEN** Agent 将成功任务沉淀为 Skill
- **THEN** 新版本立即进入所有者可用目录，且可由所有者查看、编辑或停用

#### Scenario: 修改已停用 Skill
- **WHEN** 所有者修改已停用 Skill 并保存有效定义
- **THEN** 新版本立即启用并进入本人及获共享用户的可用目录

#### Scenario: 保存无效定义
- **WHEN** 所有者提交缺少必需字段或无权引用代码的定义
- **THEN** 保存失败，既有版本、启用状态和可用目录保持不变

### Requirement: 按需选择与失败回退
`skill_system` SHALL 只预载当前用户可用且已启用 Skill 的简要总览；Agent SHALL 先判断适用条件，再读取选中 Skill 的完整版本；无匹配 Skill 时 SHALL 保持普通对话能力。

#### Scenario: 匹配已启用 Skill
- **WHEN** 当前用户提出符合某可用 Skill 适用条件的问题
- **THEN** Agent 可依据目录选择该 Skill，并读取完整步骤后执行

#### Scenario: 无匹配或已停用
- **WHEN** 没有适用的已启用 Skill，或匹配条目已停用
- **THEN** Agent 不执行该条目，并按普通对话处理

### Requirement: Skill 归属与共享
系统 SHALL 将 Skill 默认设为创建者私有，仅所有者可编辑、启停及共享；受共享用户可查看和使用，不能修改所有者的 Skill。

#### Scenario: 未共享 Skill
- **WHEN** 其他用户请求未共享 Skill 的目录或详情
- **THEN** 系统不返回该 Skill

#### Scenario: 已共享 Skill
- **WHEN** 所有者将已启用 Skill 共享给另一用户
- **THEN** 接收者可查找和查看该版本，不能编辑、停用或发布新版本；其依赖代码也已授权且执行资源符合本人权限时才可使用
