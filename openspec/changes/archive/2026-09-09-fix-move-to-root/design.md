# fix-move-to-root Design

## Context

见 proposal.md 的 Why。现状：两处移动持久化都走 MyBatis-Plus `updateById(实体)`，项目未覆盖全局 `update-strategy`（application.yml 无该项），默认 `NOT_NULL` 策略使实体中的 null 字段不进入 UPDATE 的 SET 子句。而「移到空间根目录」语义上就是把 `wiki_folder.parentId` / `document_wiki.folderId` 写成 NULL，于是被静默跳过，接口仍返回成功。

- `WikiFolderServiceImpl.moveFolder`（cloud/src/main/java/com/et/cloud/service/impl/WikiFolderServiceImpl.java:96）
- `DocumentWikiController.moveDocumentWiki`（cloud/src/main/java/com/et/cloud/controller/DocumentWikiController.java:266）

## Goals / Non-Goals

**Goals**
- 移动到空间根目录（目标为 null）真实持久化：文件夹与文档、同空间与跨空间四个方向全部生效。
- 保持 API 契约、缓存清理调用点、权限校验逻辑完全不变。

**Non-Goals**
- 不改全局 MyBatis-Plus `update-strategy`（影响所有实体的所有 updateById 调用点，风险不可控）。
- 不改前端（移动弹窗与选项列表行为正确）。
- 不修数据（历史上被假成功"移动"的数据本就没有变化，无脏数据残留；跨空间移根导致的 folderId 残留属于既有脏数据，本次只保证今后不再产生，不批量清洗）。

## Decisions

### D1：用 LambdaUpdateWrapper 显式 set，而非改全局策略或字段级注解

- 方案 A（选定）：`update(new LambdaUpdateWrapper<>()...set(WikiFolder::getParentId, parentId)...)` / `documentWikiService.update(...set(DocumentWiki::getFolderId, ...)...)`，在两处调用点显式写 null。
- 方案 B（否决）：全局 `update-strategy: ignored` —— 所有实体的 null 字段都会进 UPDATE，任何误置 null 的实体更新都会清掉数据库字段，影响面是全项目。
- 方案 C（否决）：实体字段加 `@TableField(updateStrategy = FieldStrategy.IGNORED)` —— 只解决单个字段，但把"允许写 null"的语义固化到实体上，所有用到该实体的 updateById 都会变行为，仍是隐性放大。

方案 A 影响面精确等于两个接口本身。

### D2：DocumentWikiController 里的持久化就地改写，不下沉到 Service

该 Controller 现有 move 方法直接 `documentWikiService.updateById(...)`，本次改为 `documentWikiService.update(...)`（MyBatis-Plus IService 自带），保持改动最小，不新增 Service 方法。

### D3：folderId 的 SET 用 `targetFolderId`（可能为 null）一次性写入

跨空间与同空间共用同一条 UPDATE 语句，null 与非 null 都由 `.set(..., value)` 显式携带，不分支处理。

## Risks / Trade-offs

- [LambdaUpdateWrapper 绕过实体填充逻辑（如自动 updateTime）] → 本两处已手动 set editTime；updateTime 若有 DB `ON UPDATE CURRENT_TIMESTAMP` 会自动刷新，与现状一致。
- [set null 后树构建把孤儿节点当根] → `listFolderTree` 本就把 parentId 无效/为 null 的节点归入 roots，行为正确；无需额外处理。
- [文档跨空间移根后旧缓存未失效] → 现有代码已同时 clearDocument 旧空间与新空间，保持不变。

## Migration Plan

部署即生效，无数据迁移。回滚 = revert 两个文件的改动。

## Open Questions

无。
