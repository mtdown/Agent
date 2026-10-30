# Spec Delta

## Purpose

Allow users and the Agent to work with large personal UTF-8 text files through authorized bounded reads and stable, clickable references.

## ADDED Requirements

### Requirement: Personal text files are bounded and user-scoped
系统 SHALL 支持每用户个人 UTF-8 文本文件最大 50 MiB，拒绝越界路径及跨用户访问；写入和读取 SHALL 限制内存、并发及等待资源。

#### Scenario: File at the size limit
- **WHEN** 用户保存大小不超过 50 MiB 的个人文本文件
- **THEN** 写入成功并可按段读回；其他用户不能读取

#### Scenario: Oversize write
- **WHEN** 新建、覆盖或追加后的文件超过 50 MiB
- **THEN** 操作被拒绝且已存在文件不被部分覆盖

### Requirement: File content is read in bounded segments
系统 SHALL 返回有界文本段和可续传的位置，以当前文件版本防止跨版本混读，并支持有界文件内搜索。

#### Scenario: UTF-8 chunk boundaries
- **WHEN** 中文字符或长行跨越段边界
- **THEN** 顺序读取各段能无缺失、无乱码地还原原文

#### Scenario: File changes during reading
- **WHEN** 请求下一段时文件版本已变化
- **THEN** 系统拒绝混读并提示文件已变化

#### Scenario: Search large file
- **WHEN** 用户或 Agent 搜索大文件
- **THEN** 返回有界的匹配片段和可续传位置，不需要将整份文件放入模型上下文

### Requirement: Personal file references are authorized and version-bound
系统 SHALL 将可点击的文件片段引用绑定当前用户、消息、文件版本及位置；预览 SHALL 分段加载并在每次访问重新鉴权。

#### Scenario: Open referenced passage
- **WHEN** 用户点击自己会话中有效的个人文件引用
- **THEN** 右侧显示对应文本片段并定位引用位置，可继续加载其他片段

#### Scenario: Stale or unauthorized reference
- **WHEN** 文件已改变、被删除或当前用户无权访问
- **THEN** 系统提示引用不可用或文件已变化，不显示其他用户内容或错误位置
