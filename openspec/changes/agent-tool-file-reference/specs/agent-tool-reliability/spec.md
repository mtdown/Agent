# Spec Delta

## Purpose

Provide dependable and inspectable Agent tool invocations with consistent validation, business errors, and truthful outcomes.

## ADDED Requirements

### Requirement: Tool results preserve business errors
工具结果 SHALL 使用项目现有业务错误码、消息及数据，保留 Cloud 错误码并区分成功、失败与写入结果未知。

#### Scenario: Invalid or unauthorized operation
- **WHEN** 工具参数不合法或用户无权访问目标
- **THEN** 工具返回对应业务错误码和安全说明，不声称操作成功

#### Scenario: Write response times out
- **WHEN** 写入请求超时且无法确定是否已提交
- **THEN** 系统报告结果未知，不自动重复写入或声称未保存

### Requirement: Tool parameters and outcomes are inspectable
系统 SHALL 在工具执行前校验声明的参数契约，并提供有界、不含敏感正文的调用状态及耗时；写入部分成功 SHALL 如实反馈。

#### Scenario: Invalid tool arguments
- **WHEN** 模型传入缺失、错误类型、无效枚举或多余字段
- **THEN** 系统拒绝执行并返回可理解的参数错误

#### Scenario: Partial write
- **WHEN** 多步骤写入的前一步成功、后一步失败
- **THEN** 用户可以看到已完成与失败的操作，系统不声称全部成功

### Requirement: Multiple Wiki results retain their own document relationships
系统 SHALL 分别保留同一轮的每个 Wiki 写入结果，并通过 SQL 查询已确认文档与文件夹的当前数据库关系以展示路径，不使用其他操作的临时位置替代；展示或打开时 SHALL 复核当前权限。

#### Scenario: Multiple folders and documents
- **WHEN** 同一轮先在文件夹 A 创建文档甲，又在文件夹 B 创建文档乙
- **THEN** 两个结果均保留，甲和乙分别显示数据库中的当前所属位置

#### Scenario: Success followed by unknown write
- **WHEN** 一份文档创建成功，另一次写入结果未知
- **THEN** 成功文档及其实际归属与未知操作分别展示，不猜测未知操作是否创建了文档

#### Scenario: Rename move and access loss
- **WHEN** 打开旧结果时文件夹已重命名、文档已移动或当前用户失权
- **THEN** 有权时展示数据库当前路径，失权时不泄露路径或内容；历史快照如展示须标明当时位置

#### Scenario: Root and deleted targets
- **WHEN** 已确认文档位于空间根目录或文档、文件夹已删除
- **THEN** 根目录被正确表示，不可用对象明确提示，不与其他结果混合
