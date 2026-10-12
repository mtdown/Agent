
/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
DROP TABLE IF EXISTS `document_wiki`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `document_wiki` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `title` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'document title',
  `content` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'document content',
  `summary` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'document summary',
  `category` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'category',
  `tags` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'tags JSON array',
  `userId` bigint NOT NULL COMMENT 'creator user id',
  `spaceId` bigint DEFAULT NULL COMMENT 'wiki space id',
  `folderId` bigint DEFAULT NULL COMMENT 'wiki folder id',
  `viewCount` bigint NOT NULL DEFAULT '0' COMMENT 'view count',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `editTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'edit time',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  `deleteTime` datetime DEFAULT NULL COMMENT 'delete time',
  `deleteBy` bigint DEFAULT NULL COMMENT 'delete user id',
  `isDelete` tinyint NOT NULL DEFAULT '0' COMMENT 'is deleted',
  `contentFormat` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT 'plain' COMMENT 'plain/markdown (RAG group 1)',
  `sourceType` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT 'NATIVE' COMMENT 'NATIVE/UPLOAD/IMPORT/URL (RAG group 2)',
  `sourceUrl` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'original source url (RAG group 2)',
  `contentHash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'content fingerprint (RAG group 1)',
  `contentVersion` int DEFAULT '1' COMMENT 'content version, +1 per edit (RAG group 1)',
  `visibility` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT 'SPACE' COMMENT 'PRIVATE/SPACE/PUBLIC (RAG group 6)',
  `metadataJson` varchar(2048) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'extensible metadata (RAG group 6)',
  PRIMARY KEY (`id`),
  KEY `idx_title` (`title`),
  KEY `idx_category` (`category`),
  KEY `idx_tags` (`tags`),
  KEY `idx_userId` (`userId`),
  KEY `idx_editTime` (`editTime`),
  KEY `idx_space_folder` (`spaceId`,`folderId`),
  KEY `idx_deleteTime` (`deleteTime`),
  KEY `idx_deleteBy` (`deleteBy`),
  FULLTEXT KEY `idx_document_wiki_title_content_fulltext` (`title`,`content`) /*!50100 WITH PARSER `ngram` */ 
) ENGINE=InnoDB AUTO_INCREMENT=2108865120607522819 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='wiki document';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `picture`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `picture` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `url` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '图片 url',
  `name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '图片名称',
  `introduction` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '简介',
  `category` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '分类',
  `tags` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '标签（JSON 数组）',
  `picSize` bigint DEFAULT NULL COMMENT '图片体积',
  `picWidth` int DEFAULT NULL COMMENT '图片宽度',
  `picHeight` int DEFAULT NULL COMMENT '图片高度',
  `picScale` double DEFAULT NULL COMMENT '图片宽高比例',
  `picFormat` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '图片格式',
  `userId` bigint NOT NULL COMMENT '创建用户 id',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `editTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '编辑时间',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `isDelete` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除',
  `reviewStatus` int NOT NULL DEFAULT '0' COMMENT '审核状态：0待审核，1审核通过，2审核拒绝',
  `reviewMessage` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审核信息',
  `reviewerId` bigint DEFAULT NULL COMMENT '审核人 ID',
  `reviewTime` datetime DEFAULT NULL COMMENT '审核时间',
  `spaceId` bigint DEFAULT NULL COMMENT '空间 id（为空表示公共空间）',
  `thumbnailUrl` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '缩略图 url',
  PRIMARY KEY (`id`),
  KEY `idx_name` (`name`),
  KEY `idx_introduction` (`introduction`),
  KEY `idx_category` (`category`),
  KEY `idx_tags` (`tags`),
  KEY `idx_userId` (`userId`),
  KEY `idx_reviewStatus` (`reviewStatus`),
  KEY `idx_spaceId` (`spaceId`)
) ENGINE=InnoDB AUTO_INCREMENT=2096516667165478914 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='图片';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `rag_api_key`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rag_api_key` (
  `id` bigint NOT NULL COMMENT 'id (ASSIGN_ID)',
  `userId` bigint NOT NULL COMMENT '属主 user.id',
  `keyName` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用途名, 如"本地 Agent"',
  `keyHash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SHA-256(明文key) hex, 认证唯一入口',
  `keyPrefix` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '明文前8位, 列表展示',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  `isDelete` tinyint NOT NULL DEFAULT '0' COMMENT 'soft delete flag (=1 即吊销)',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`userId`,`isDelete`),
  KEY `idx_key_hash` (`keyHash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='rag open api key';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `space`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `space` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `spaceName` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '空间名称',
  `spaceLevel` int DEFAULT '0' COMMENT '空间级别：0-普通版 1-专业版 2-旗舰版',
  `maxSize` bigint DEFAULT '0' COMMENT '空间图片的最大总大小',
  `maxCount` bigint DEFAULT '0' COMMENT '空间图片的最大数量',
  `totalSize` bigint DEFAULT '0' COMMENT '当前空间下图片的总大小',
  `totalCount` bigint DEFAULT '0' COMMENT '当前空间下的图片数量',
  `userId` bigint NOT NULL COMMENT '创建用户 id',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `editTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '编辑时间',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `isDelete` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除',
  `spaceType` int NOT NULL DEFAULT '0' COMMENT '空间类型：0-私有，1-公共',
  PRIMARY KEY (`id`),
  KEY `idx_userId` (`userId`),
  KEY `idx_spaceName` (`spaceName`),
  KEY `idx_spaceLevel` (`spaceLevel`),
  KEY `idx_spaceType` (`spaceType`)
) ENGINE=InnoDB AUTO_INCREMENT=2095009860920135682 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='空间';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `space_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `space_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `spaceId` bigint NOT NULL COMMENT '空间id',
  `userId` bigint NOT NULL COMMENT '用户id',
  `spaceRole` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT 'viewer' COMMENT '空间角色',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_spaceId_userId` (`spaceId`,`userId`),
  KEY `idx_spaceId` (`spaceId`),
  KEY `idx_userId` (`userId`)
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='空间用户关联';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `userAccount` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '账号',
  `userPassword` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码',
  `userName` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户昵称',
  `userAvatar` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户头像',
  `userProfile` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户简介',
  `userRole` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'user' COMMENT '用户角色：user/admin/VIP等等',
  `editTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '编辑时间',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `isDelete` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_userAccount` (`userAccount`),
  KEY `idx_userName` (`userName`)
) ENGINE=InnoDB AUTO_INCREMENT=2096516665374511106 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `wiki_attachment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wiki_attachment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `wikiSpaceId` bigint NOT NULL COMMENT 'wiki space id',
  `documentId` bigint DEFAULT NULL COMMENT 'document id, null means not linked yet',
  `fileName` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'file name',
  `url` varchar(1024) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'file url',
  `fileSize` bigint NOT NULL DEFAULT '0' COMMENT 'file size in bytes',
  `mimeType` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'mime type',
  `fileHash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'file content hash (md5 hex)',
  `userId` bigint NOT NULL COMMENT 'uploader user id',
  `deleteTime` datetime DEFAULT NULL COMMENT 'logical delete time',
  `deleteBy` bigint DEFAULT NULL COMMENT 'logical delete user',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  `isDelete` tinyint NOT NULL DEFAULT '0' COMMENT 'soft delete flag',
  PRIMARY KEY (`id`),
  KEY `idx_space_delete` (`wikiSpaceId`,`isDelete`),
  KEY `idx_document` (`documentId`),
  KEY `idx_user` (`userId`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='wiki attachment';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `wiki_chunk`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wiki_chunk` (
  `id` bigint NOT NULL COMMENT 'id (ASSIGN_ID)',
  `docId` bigint NOT NULL COMMENT 'document_wiki.id',
  `spaceId` bigint NOT NULL COMMENT 'wiki_space.id (冗余, 权限过滤主口)',
  `contentVersion` int NOT NULL DEFAULT '1' COMMENT '对应文档 contentVersion',
  `chunkIndex` int NOT NULL DEFAULT '0' COMMENT '文档内切片序号',
  `chunkHeading` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '所属标题路径',
  `chunkText` mediumtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '切片原文',
  `docTitle` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '文档标题(冗余, 检索免联表)',
  `docNumber` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '文号, 正则提取, 可空',
  `embedding` longblob COMMENT 'float32 little-endian 序列化向量',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / INVALID',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  `isDelete` tinyint NOT NULL DEFAULT '0' COMMENT 'soft delete flag',
  PRIMARY KEY (`id`),
  KEY `idx_space_status` (`spaceId`,`status`),
  KEY `idx_doc_version_status` (`docId`,`contentVersion`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='wiki rag chunk';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `wiki_folder`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wiki_folder` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `spaceId` bigint NOT NULL COMMENT 'wiki space id',
  `parentId` bigint DEFAULT NULL COMMENT 'parent folder id',
  `name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'folder name',
  `deleteTime` datetime DEFAULT NULL COMMENT 'delete time',
  `deleteBy` bigint DEFAULT NULL COMMENT 'delete user id',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `editTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'edit time',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  `isDelete` tinyint NOT NULL DEFAULT '0' COMMENT 'is deleted',
  PRIMARY KEY (`id`),
  KEY `idx_space_parent` (`spaceId`,`parentId`),
  KEY `idx_parentId` (`parentId`),
  KEY `idx_isDelete` (`isDelete`)
) ENGINE=InnoDB AUTO_INCREMENT=2095544465590915102 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='wiki folder';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `wiki_space`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wiki_space` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `type` tinyint NOT NULL COMMENT 'space type: 0-personal, 1-team, 2-public',
  `name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'space name',
  `ownerUserId` bigint DEFAULT NULL COMMENT 'owner user id',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  `isDelete` tinyint NOT NULL DEFAULT '0' COMMENT 'is deleted',
  `personalOwnerKey` bigint GENERATED ALWAYS AS (if((`type` = 0),`ownerUserId`,NULL)) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_personal_owner` (`personalOwnerKey`),
  KEY `idx_type` (`type`),
  KEY `idx_ownerUserId` (`ownerUserId`),
  KEY `idx_isDelete` (`isDelete`)
) ENGINE=InnoDB AUTO_INCREMENT=2095544464810774535 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='wiki space';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `wiki_space_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wiki_space_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `spaceId` bigint NOT NULL COMMENT 'wiki space id',
  `userId` bigint NOT NULL COMMENT 'user id',
  `spaceRole` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'editor' COMMENT 'space role',
  `createTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `updateTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  `isDelete` tinyint NOT NULL DEFAULT '0' COMMENT 'is deleted',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_space_user` (`spaceId`,`userId`),
  KEY `idx_userId` (`userId`),
  KEY `idx_spaceId` (`spaceId`),
  KEY `idx_isDelete` (`isDelete`)
) ENGINE=InnoDB AUTO_INCREMENT=2095544464928215047 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='wiki space user';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

