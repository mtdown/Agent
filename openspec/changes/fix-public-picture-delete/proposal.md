## Why

删除公共图库图片（`spaceId = null`）必然失败：`PictureServiceImpl.deletePicture` 对所有图片无条件执行空间额度更新，`eq(Space::getId, null)` 生成的 `WHERE id = NULL` 匹配不到任何行，`update()` 返回 false 后抛出 `50001 额度更新失败`，事务回滚导致图片永远删不掉。该缺陷由 2025-10-26 提交 `48a1155f` 引入，在 fix-frontend-typecheck 回归中复现并记录于 IssueLog（暂缓项），现单独立项修复。

## What Changes

- 修复 `deletePicture`：仅当被删图片的 `spaceId` 非空时才更新对应空间的 `totalSize`/`totalCount` 额度；公共图库图片只删除记录，不做额度更新。
- 行为对齐上传路径 `uploadPicture` 已有的 `finalSpaceId != null` 判空逻辑，保持同一文件内两处额度逻辑对称。
- 顺带清理：fix-frontend-typecheck 回归期间因该缺陷无法删除而残留于公共图库的 2 张 72B 测试图片（id `2096509129564987394`、`2096509302319980545`），修复后作为真实回归用例删除。
- 不改变空间内图片的删除与额度释放行为；不改前端；不引入接口签名或返回结构变化。

## Capabilities

### New Capabilities

- `picture-delete-quota`: 约束删除图片时额度更新的条件性行为——空间图片删除须释放额度，公共图库图片删除须成功且不触发额度更新。

### Modified Capabilities

- None.

## Impact

- **后端**：`cloud/src/main/java/com/et/cloud/service/impl/PictureServiceImpl.java` 的 `deletePicture` 方法（约 532-555 行），新增一个判空分支，无接口签名变化。
- **数据库**：公共图库图片删除从"必失败回滚"变为"成功删除"；space 表不再收到 `id = NULL` 的无效更新。
- **前端**：无改动（详情页/管理页的删除按钮行为自动恢复）。
- **回归风险**：需确认空间内图片删除后额度仍正确释放（totalSize/totalCount 变化）。
- **明确不做**：公共图库首页缓存不失效问题（5 分钟 Caffeine 时差）不在本次范围，维持已知行为。
