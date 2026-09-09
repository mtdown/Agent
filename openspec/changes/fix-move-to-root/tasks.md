# fix-move-to-root Tasks

## 1. 后端修复

- [x] 1.1 `WikiFolderServiceImpl.moveFolder` 改用 `LambdaUpdateWrapper` 显式 `.set(WikiFolder::getParentId, parentId)`，保留 editTime 更新与 `wikiCacheManager.clearSpace`；编译通过（mvn compile）
- [x] 1.2 `DocumentWikiController.moveDocumentWiki` 改用 `documentWikiService.update(LambdaUpdateWrapper)` 显式 `.set(DocumentWiki::getFolderId, targetFolderId)`；编译通过（mvn compile）

## 2. 接口级验证（自动化）

- [x] 2.1 用临时测试文件夹验证「文件夹移到空间根目录」：调 `/api/wikiFolder/move` 不传 parentId，返回 code 0 且 DB parentId 变为 NULL；验证后物理删除测试数据（2026-09-09 通过，测试文件夹 move-diag-test）
- [x] 2.2 用临时测试文档验证「文档同空间移到根目录」与「文档跨空间移到根目录」两个方向：DB folderId 均变为 NULL；验证后清理测试数据（2026-09-09 通过，测试文档 move-diag-test-doc，跨空间移公开空间根后 spaceId 更新且 folderId 为 NULL；已物理删除）
- [x] 2.3 回归验证移动到具体文件夹（parentId / folderId 非 null）仍正常：文件夹与文档各一例，DB 落库正确（2026-09-09 通过；另 DocumentWikiController 相关单测 Cache/EditFormat/Import 共 8 例全过）

## 3. 汇报与人工验收

- [x] 3.1 向负责人汇报测试结果与问题（含 IssueLog 记录），等待确认后上传分支（2026-09-09 负责人已确认）
- [x] 3.2 负责人页面手测：导航树把 `政策文档` 空间下的 `04 媒体视角` 移到空间根目录，确认树刷新后 `04 媒体视角` 与 `03 新闻发布会` 平级（2026-09-09 负责人手测通过）
- [x] 3.3 负责人页面手测：任选一篇文档移到空间根目录，确认其出现在空间根文档列表（2026-09-09 负责人手测通过，允许推送）
