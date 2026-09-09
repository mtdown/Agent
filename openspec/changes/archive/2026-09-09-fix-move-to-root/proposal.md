# fix-move-to-root

## Why

导航树与文档移动弹窗都提供「空间根目录」作为目标位置，但后端持久化层存在缺陷：MyBatis-Plus `updateById` 的默认 NOT_NULL 策略会跳过实体中的 null 字段，而「移动到空间根目录」恰恰需要把 `parentId` / `folderId` 写成 null。结果是接口返回成功、前端提示「已移动」，数据库却原封不动（2026-09-09 已用临时测试文件夹实证：调 `/api/wikiFolder/move` 不传 parentId，返回 code 0 但 DB parentId 未变）。跨空间把文档移到根目录时后果更糟：`spaceId` 被更新而 `folderId` 残留旧空间引用，文档从导航树中直接消失。

## What Changes

- `WikiFolderServiceImpl.moveFolder`：持久化改用 `LambdaUpdateWrapper` 显式 `.set(parentId, null)`，确保移到空间根目录时 `wiki_folder.parentId` 真正置空。
- `DocumentWikiController.moveDocumentWiki`：持久化改用 `LambdaUpdateWrapper` 显式 `.set(folderId, targetFolderId)`，确保文档移到空间根目录时 `document_wiki.folderId` 真正置空（含同空间与跨空间两个方向）。
- 不改全局 `update-strategy` 配置（影响面不可控，仅在这两处显式写 null）。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `wiki-folder`：Requirement「Folder movement stays within its space」补充场景——移动到空间根目录（无父文件夹）必须真实生效，不得假成功。
- `wiki-document`：Requirement「Move documents between spaces and folders」补充场景——移动到目标空间根目录（无文件夹）必须真实生效；跨空间移动到根目录后旧 folderId 必须清除。

## Impact

- 后端：`cloud/src/main/java/com/et/cloud/service/impl/WikiFolderServiceImpl.java`（moveFolder）、`cloud/src/main/java/com/et/cloud/controller/DocumentWikiController.java`（moveDocumentWiki）。
- API 契约不变：`/api/wikiFolder/move`、`/api/documentWiki/move` 的请求/响应结构均不变，仅修复持久化行为。
- 前端无需改动（移动弹窗与选项列表本身正确）。
- 缓存清理逻辑保持现状（`wikiCacheManager.clearSpace` / `clearDocument` 照旧调用）。
