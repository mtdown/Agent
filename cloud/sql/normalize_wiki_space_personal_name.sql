-- 一次性订正：把个人空间名统一为「个人区」
--
-- 背景：个人空间有两个历史命名来源，且互不一致：
--   1. 本文件同目录的 backfill_document_wiki_space.sql 曾用 CONCAT('个人区-', u.id)，
--      存量用户的空间因此带上了用户 ID 后缀（如 个人区-1971148507794014209）；
--   2. WikiSpaceServiceImpl.ensurePersonalSpaceForUser() 曾写死「个人文档」。
-- 现在两者已统一为常量 WikiSpaceService.PERSONAL_SPACE_DEFAULT_NAME = '个人区'，
-- 本脚本负责把库中已有的两种历史名字一并订正过来。
--
-- 幂等：可重复执行，重复执行不产生新变化。
--
-- 注意：全量 UPDATE 之所以安全，是因为个人空间名由系统持有、任何角色都不可改名
-- （见 WikiSpaceServiceImpl.renameSpace 对非团队空间的拒绝）。若将来重新开放
-- 个人空间重命名，本脚本必须收窄为只订正 name 匹配 '个人区-%' 或 '个人文档' 的行，
-- 否则会把用户自己改过的名字冲掉。

UPDATE wiki_space SET name = '个人区' WHERE type = 0 AND isDelete = 0;
