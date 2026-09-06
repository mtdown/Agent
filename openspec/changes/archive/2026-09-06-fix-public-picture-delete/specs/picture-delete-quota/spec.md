## Purpose

约束删除图片时空间额度更新的条件性行为：公共图库图片必须可以删除成功，空间图片删除时必须正确释放所属空间额度。

## ADDED Requirements

### Requirement: 公共图库图片删除成功

系统 SHALL 允许删除不隶属于任何空间（spaceId 为空）的公共图库图片，且删除过程不触发空间额度更新。

#### Scenario: 管理员删除公共图库图片

- **WHEN** 已登录的管理员或图片所有者请求删除一张 spaceId 为空的公共图库图片
- **THEN** 该图片记录 SHALL 被删除
- **AND** 接口 SHALL 返回成功（code 0）
- **AND** 系统 SHALL 不对任何空间执行额度更新

#### Scenario: 删除后图片不可再查询

- **WHEN** 一张公共图库图片删除成功后，按其 id 再次查询
- **THEN** 系统 SHALL 返回数据不存在或等价的失败响应

### Requirement: 空间图片删除释放额度

系统 SHALL 在删除隶属于某空间（spaceId 非空）的图片时，同步释放该空间的存储额度，且该行为不因本变更受损。

#### Scenario: 删除空间内图片

- **WHEN** 有权限的用户删除一张 spaceId 非空的图片
- **THEN** 该图片记录 SHALL 被删除并返回成功（code 0）
- **AND** 所属空间的 totalSize SHALL 减少该图片的 picSize，totalCount SHALL 减 1

#### Scenario: 删除失败时事务回滚

- **WHEN** 删除空间图片过程中额度更新失败
- **THEN** 整个删除事务 SHALL 回滚，图片记录与空间额度均保持删除前的值
