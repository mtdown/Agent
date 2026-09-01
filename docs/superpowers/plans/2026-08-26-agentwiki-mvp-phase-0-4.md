# AgentWiki MVP Phase 0-4 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有智能云图库改造成 AgentWiki 企业智能知识协作平台的第一版 MVP，完成项目定位、文档模型、文档上传解析、Wiki 编辑页面、个人 Wiki 与团队 Wiki 权限体系。

**Architecture:** 保留现有用户、空间、成员权限、图片上传、COS 存储、缓存和后台管理基础能力；新增文档主业务模型，让 `Document` 成为 Wiki 的核心对象，让 `Picture` 转型为 Wiki 素材资产。阶段 0-4 不实现 RAG、URL 抓取和 Agent 编排，只为后续阶段预留文档切片、解析状态和权限扩展点。

**Tech Stack:** Spring Boot 2.7.6, Java 11, MyBatis-Plus, MySQL, Redis, Caffeine, Tencent COS, Sa-Token, Knife4j, Vue3, TypeScript, Pinia, Vue Router, Ant Design Vue, Vite.

---

## 0. 固定工作流程

由于原项目已经较久没有维护，后续每个阶段开始前都必须先恢复项目上下文，再进入替换和实现。每次执行阶段任务时，按以下顺序推进：

1. **先说明要替换哪些内容**
   - 先列出本阶段涉及的旧模块、旧页面、旧接口、旧表、旧类名。
   - 明确哪些是保留、哪些是改名、哪些是新增、哪些是暂停主线。
   - 特别注意：图片模块不删除，转为 Wiki 图片素材能力。

2. **带用户熟悉一遍原操作流程**
   - 先从用户视角走一遍原功能怎么用。
   - 再从代码视角说明原功能经过哪些页面、接口、Service、数据库表。
   - 只有确认原流程理解清楚后，才进入新功能替换。

3. **说明要替换成什么具体内容**
   - 对照原流程，说明新流程如何变化。
   - 明确新增的数据结构、页面、接口和权限点。
   - 说明新旧模块之间的复用关系。

4. **说明具体操作方式后再动手**
   - 写清楚本次会改哪些文件。
   - 说明每个文件的改动目的。
   - 对风险较高的改动先做小步验证。

5. **完成后复核是否有问题**
   - 检查旧功能是否被误删或误伤。
   - 检查新流程是否能走通。
   - 检查权限、路由、接口、数据库字段是否一致。
   - 检查是否还有命名残留、页面入口不一致、前后端 API 不同步等问题。

这个流程优先级高于具体开发速度。后续所有阶段都以“先理解原项目，再替换升级，再复核”为默认节奏。

---

## 1. 项目定位

### 1.1 新项目名称

项目名称：**AgentWiki 企业智能知识协作平台**

一句话描述：

> AgentWiki 是一个基于 Spring Boot + Vue3 的企业 Wiki 知识协作平台，支持多格式文档上传、在线文档编辑、个人 Wiki、团队 Wiki、图片素材管理和权限隔离，并为后续企业文档 RAG 与 Agent 编排预留扩展基础。

### 1.2 项目主线变化

原项目主线：

> 图片上传、图片管理、公共图库、私有空间、团队空间、AI 扩图、协同编辑。

新项目主线：

> 企业文档上传、Wiki 文档编辑、个人知识库、团队知识库、文档权限管理、图片素材插入、后续 RAG 问答。

### 1.3 核心设计原则

- 不删除图片模块，图片继续作为 Wiki 文档中的插图、封面、附件图片和空间素材使用。
- 不直接重写整个项目，优先复用现有用户、空间、权限、COS、缓存、后台管理和接口生成能力。
- 阶段 0-4 先完成企业 Wiki 的基础业务闭环，RAG、URL 抓取、Agent 编排放到后续阶段。
- MVP 先支持 Markdown 编辑，不做 Word 在线协同编辑。
- 团队权限继续复用 Sa-Token + 空间成员角色模型。

---

## 2. 原模块处理策略

| 原模块 | 新定位 | 处理方式 |
| --- | --- | --- |
| 用户认证模块 | 用户认证模块 | 保留 |
| 管理员权限模块 | 平台后台权限模块 | 保留 |
| 公共图库模块 | 公共 Wiki 文档库 | 改造 |
| 图片上传模块 | Wiki 图片素材上传模块 | 保留并嵌入编辑器 |
| 图片管理模块 | 图片素材管理模块 | 保留并弱化首页入口 |
| 图片审核模块 | 公共素材审核 / 文档审核预留 | 部分保留 |
| 图片搜索模块 | 文档搜索 + 素材搜索 | 改造 |
| 图片分类标签模块 | 文档分类标签 + 素材分类标签 | 复用 |
| 私有空间模块 | 个人 Wiki | 改造 |
| 团队空间模块 | 团队 Wiki | 改造 |
| 空间成员权限模块 | Wiki 成员权限模块 | 改造 |
| 图片基础编辑模块 | Wiki 图片素材编辑 | 保留 |
| AI 扩图模块 | 后续 RAG / AI 功能参考 | 暂停主线 |
| WebSocket 协同编辑模块 | 后续文档协同编辑参考 | 暂停主线 |
| 缓存优化模块 | 文档列表缓存 / 热点内容缓存 | 复用 |
| 对象存储模块 | 文档原文件 + 图片素材存储 | 扩展 |
| 后台管理模块 | 用户、文档、空间、成员管理 | 改造 |
| API 自动生成模块 | 前后端接口同步 | 保留 |

---

## 3. 阶段 0：项目定位与基础命名

### 3.1 阶段目标

完成项目从“云图库”到“企业 Wiki 知识协作平台”的定位切换，让后续代码、页面、数据库和简历叙事围绕 AgentWiki 展开。

### 3.2 主要任务

- [ ] 修改 README 项目标题和项目介绍。
- [ ] 新增 AgentWiki 项目架构说明。
- [ ] 明确 `Picture` 不删除，定位为 Wiki 图片素材。
- [ ] 明确 `Document` 是新主业务对象。
- [ ] 梳理旧页面入口，把首页主入口从图片列表调整为文档列表。
- [ ] 保留原公共图库入口作为“素材库”或后台入口。

### 3.3 涉及文件

后端：

- `cloud/src/main/resources/application.yml`
- `cloud/src/main/java/com/et/cloud/CloudApplication.java`

前端：

- `cloud_front/src/layouts/BasicLayout.vue`
- `cloud_front/src/components/GlobalHeader.vue`
- `cloud_front/src/router/index.ts`
- `cloud_front/src/pages/HomePage.vue`

文档：

- `README.md`
- `docs/superpowers/plans/2026-08-26-agentwiki-mvp-phase-0-4.md`

### 3.4 验收标准

- 项目说明不再以“智能云图库”为唯一主线。
- 页面导航能体现“文档库 / 我的 Wiki / 团队 Wiki / 素材库 / 后台管理”。
- 后续开发人员能从 README 和本计划书理解项目改造方向。

---

## 4. 阶段 1：文档模型与数据库改造

### 4.1 阶段目标

新增 Wiki 文档主模型，同时保留图片模型，形成 `Space -> Document -> DocumentVersion / DocumentChunk` 与 `Space -> Picture` 并存的结构。

### 4.2 新增数据库表

#### document

用途：保存 Wiki 文档主信息。

关键字段：

- `id`
- `title`
- `summary`
- `content`
- `contentType`
- `sourceType`
- `fileUrl`
- `fileName`
- `fileType`
- `fileSize`
- `coverPictureId`
- `spaceId`
- `userId`
- `reviewStatus`
- `parseStatus`
- `parseMessage`
- `createTime`
- `editTime`
- `updateTime`
- `isDelete`

#### document_version

用途：保存文档历史版本。

关键字段：

- `id`
- `documentId`
- `versionNo`
- `title`
- `content`
- `summary`
- `userId`
- `createTime`

#### document_chunk

用途：保存文档切片，为后续 RAG 做准备。

关键字段：

- `id`
- `documentId`
- `spaceId`
- `chunkIndex`
- `content`
- `tokenCount`
- `embeddingStatus`
- `createTime`
- `updateTime`
- `isDelete`

### 4.3 新增后端包与类

实体：

- `cloud/src/main/java/com/et/cloud/model/entity/Document.java`
- `cloud/src/main/java/com/et/cloud/model/entity/DocumentVersion.java`
- `cloud/src/main/java/com/et/cloud/model/entity/DocumentChunk.java`

请求对象：

- `cloud/src/main/java/com/et/cloud/dto/document/DocumentAddRequest.java`
- `cloud/src/main/java/com/et/cloud/dto/document/DocumentEditRequest.java`
- `cloud/src/main/java/com/et/cloud/dto/document/DocumentUpdateRequest.java`
- `cloud/src/main/java/com/et/cloud/dto/document/DocumentQueryRequest.java`
- `cloud/src/main/java/com/et/cloud/dto/document/DocumentUploadRequest.java`

视图对象：

- `cloud/src/main/java/com/et/cloud/model/vis/DocumentVis.java`
- `cloud/src/main/java/com/et/cloud/model/vis/DocumentVersionVis.java`

Mapper：

- `cloud/src/main/java/com/et/cloud/mapper/DocumentMapper.java`
- `cloud/src/main/java/com/et/cloud/mapper/DocumentVersionMapper.java`
- `cloud/src/main/java/com/et/cloud/mapper/DocumentChunkMapper.java`

Service：

- `cloud/src/main/java/com/et/cloud/service/DocumentService.java`
- `cloud/src/main/java/com/et/cloud/service/DocumentVersionService.java`
- `cloud/src/main/java/com/et/cloud/service/DocumentChunkService.java`
- `cloud/src/main/java/com/et/cloud/service/impl/DocumentServiceImpl.java`
- `cloud/src/main/java/com/et/cloud/service/impl/DocumentVersionServiceImpl.java`
- `cloud/src/main/java/com/et/cloud/service/impl/DocumentChunkServiceImpl.java`

Controller：

- `cloud/src/main/java/com/et/cloud/controller/DocumentController.java`

SQL：

- `cloud/sql/create_table_document.sql`

### 4.4 需要保留的图片能力

- `Picture`
- `PictureController`
- `PictureService`
- `PictureUploadTemplate`
- `CosManager`
- 图片缩略图字段 `thumbnailUrl`
- 图片 `spaceId`

图片模块的新用途：

- 文档封面图
- 文档正文插图
- Wiki 空间素材库
- 文档附件图片

### 4.5 验收标准

- 数据库可以创建文档、文档版本、文档切片表。
- 后端可以完成文档新增、编辑、删除、详情、分页查询。
- 文档可以绑定 `spaceId`。
- 图片功能没有被破坏，仍可上传并归属到空间。

---

## 5. 阶段 2：文档上传与解析

### 5.1 阶段目标

把原来的图片上传能力扩展为文档上传能力，支持企业 Wiki 常见文档格式上传，并能解析出正文文本。

### 5.2 MVP 支持格式

- PDF
- DOCX
- TXT
- Markdown

HTML 和 URL 抓取放到阶段 5。

### 5.3 文档上传流程

1. 用户选择文档文件。
2. 前端调用文档上传接口。
3. 后端校验文件大小、扩展名和 MIME 类型。
4. 后端将原文件上传到 COS。
5. 后端创建 `document` 记录。
6. 后端根据文件类型提取正文文本。
7. 后端生成简单摘要。
8. 后端保存正文到 `document.content`。
9. 后端按段落生成 `document_chunk`。
10. 后端更新 `parseStatus` 为成功或失败。

### 5.4 新增后端解析组件

统一接口：

- `cloud/src/main/java/com/et/cloud/manager/document/DocumentParser.java`

解析实现：

- `cloud/src/main/java/com/et/cloud/manager/document/PdfDocumentParser.java`
- `cloud/src/main/java/com/et/cloud/manager/document/DocxDocumentParser.java`
- `cloud/src/main/java/com/et/cloud/manager/document/TextDocumentParser.java`
- `cloud/src/main/java/com/et/cloud/manager/document/MarkdownDocumentParser.java`

解析调度：

- `cloud/src/main/java/com/et/cloud/manager/document/DocumentParseManager.java`

文本切片：

- `cloud/src/main/java/com/et/cloud/manager/document/DocumentChunkManager.java`

上传结果：

- `cloud/src/main/java/com/et/cloud/dto/file/UploadDocumentResult.java`

### 5.5 推荐依赖

后端新增依赖：

- Apache PDFBox：解析 PDF。
- Apache POI：解析 DOCX。

Markdown 和 TXT 可以先用普通文本读取。

### 5.6 接口设计

- `POST /document/upload`：上传并解析文档。
- `POST /document/add`：创建 Markdown 文档。
- `POST /document/edit`：编辑文档。
- `POST /document/delete`：删除文档。
- `GET /document/get/vis`：获取文档详情。
- `POST /document/list/page/vis`：分页查询文档。
- `GET /document/list/version`：查询文档版本。

### 5.7 验收标准

- 上传 PDF 后能生成一条文档记录，并能查看解析出的正文。
- 上传 DOCX 后能生成一条文档记录，并能查看解析出的正文。
- 上传 TXT 后能生成一条文档记录，并能查看解析出的正文。
- 上传 Markdown 后能生成一条文档记录，并保留 Markdown 正文。
- 解析失败时页面能显示失败状态和原因。
- 文档切片能写入 `document_chunk`。

---

## 6. 阶段 3：Wiki 页面与文档编辑

### 6.1 阶段目标

让系统具备可用的 Wiki 文档浏览、创建、编辑和版本保存能力。

### 6.2 前端页面改造

新增页面：

- `cloud_front/src/pages/DocumentDetailPage.vue`
- `cloud_front/src/pages/AddDocumentPage.vue`
- `cloud_front/src/pages/EditDocumentPage.vue`
- `cloud_front/src/pages/DocumentVersionPage.vue`

改造页面：

- `cloud_front/src/pages/HomePage.vue`
- `cloud_front/src/pages/MySpacePage.vue`
- `cloud_front/src/pages/SpaceDetailPage.vue`
- `cloud_front/src/pages/admin/PictureManagePage.vue`

新增组件：

- `cloud_front/src/components/DocumentList.vue`
- `cloud_front/src/components/DocumentEditor.vue`
- `cloud_front/src/components/DocumentUpload.vue`
- `cloud_front/src/components/InsertPictureUpload.vue`
- `cloud_front/src/components/DocumentVersionList.vue`

### 6.3 编辑器设计

MVP 使用 Markdown 编辑器。

能力范围：

- 编辑标题。
- 编辑 Markdown 正文。
- 设置分类。
- 设置标签。
- 插入图片。
- 保存文档。
- 保存时自动生成版本。
- 详情页渲染 Markdown 内容。

图片插入流程：

1. 用户在编辑器中点击插入图片。
2. 前端调用现有图片上传接口。
3. 后端上传图片到 COS。
4. 返回图片 URL。
5. 前端将 Markdown 图片语法插入正文。

示例正文格式：

```markdown
# 项目部署说明

下面是部署架构图：

![部署架构](https://example.com/wiki-image.png)
```

### 6.4 文档版本设计

保存文档时：

- 更新 `document` 当前内容。
- 向 `document_version` 插入一条新版本。
- `versionNo` 从 1 开始递增。

MVP 支持：

- 查看版本列表。
- 查看某个历史版本内容。

MVP 不支持：

- 多人实时协同编辑。
- Word 在线编辑。
- 版本差异对比。

### 6.5 验收标准

- 用户可以创建 Markdown Wiki 文档。
- 用户可以编辑已有文档。
- 用户可以上传图片并插入文档正文。
- 用户可以查看文档详情。
- 用户可以查看文档版本列表。
- 首页展示文档而不是图片。

---

## 7. 阶段 4：个人 Wiki 与团队 Wiki 权限体系

### 7.1 阶段目标

将原有私有空间和团队空间升级为个人 Wiki 与团队 Wiki，复用空间成员体系，实现文档级操作权限隔离。

### 7.2 空间概念改造

| 原概念 | 新概念 |
| --- | --- |
| 私有空间 | 个人 Wiki |
| 团队空间 | 团队 Wiki |
| 空间图片 | Wiki 文档 + Wiki 图片素材 |
| 空间成员 | Wiki 成员 |
| 空间角色 | Wiki 角色 |

### 7.3 权限配置改造

修改文件：

- `cloud/src/main/resources/biz/spaceUserAuthConfig.json`
- `cloud_front/src/constants/space.ts`

新增权限：

- `document:view`
- `document:create`
- `document:edit`
- `document:delete`
- `document:upload`
- `picture:upload`
- `picture:view`
- `wiki:member:manage`

角色权限建议：

| 角色 | 权限 |
| --- | --- |
| 浏览者 | `document:view`, `picture:view` |
| 编辑者 | `document:view`, `document:create`, `document:edit`, `document:upload`, `picture:view`, `picture:upload` |
| 管理员 | `document:view`, `document:create`, `document:edit`, `document:delete`, `document:upload`, `picture:view`, `picture:upload`, `wiki:member:manage` |

### 7.4 后端权限校验

文档接口使用空间权限注解：

- 查看文档：`document:view`
- 创建文档：`document:create`
- 上传文档：`document:upload`
- 编辑文档：`document:edit`
- 删除文档：`document:delete`
- 成员管理：`wiki:member:manage`

需要确认 `StpInterfaceImpl` 可以从以下请求参数中解析空间上下文：

- `spaceId`
- `documentId`
- `id`

当接口只传 `documentId` 或 `id` 时，后端应查询 `Document` 获取 `spaceId`，再判断当前用户在该 Wiki 空间中的权限。

### 7.5 前端权限控制

前端根据 `DocumentVis.permissionList` 控制按钮展示：

- 有 `document:edit` 才显示编辑按钮。
- 有 `document:delete` 才显示删除按钮。
- 有 `document:create` 才显示新建文档按钮。
- 有 `document:upload` 才显示上传文档按钮。
- 有 `wiki:member:manage` 才显示成员管理入口。

### 7.6 页面入口改造

导航建议：

- 文档库
- 我的 Wiki
- 团队 Wiki
- 素材库
- 后台管理

空间详情页：

- 默认展示该空间下的文档列表。
- 提供文档上传入口。
- 提供文档新建入口。
- 提供素材库入口。
- 团队管理员可进入成员管理。

### 7.7 验收标准

- 用户可以创建个人 Wiki。
- 用户可以创建团队 Wiki。
- 团队 Wiki 管理员可以添加成员。
- 浏览者只能查看文档。
- 编辑者可以创建、上传、编辑文档。
- 管理员可以删除文档和管理成员。
- 前端按钮展示与后端权限校验一致。

---

## 8. 阶段 0-4 总体交付标准

完成阶段 0-4 后，系统应达到以下状态：

- 项目定位已经从云图库切换为企业 Wiki 知识协作平台。
- 系统保留图片能力，图片作为 Wiki 素材继续服务文档编辑。
- 系统具备文档主模型、文档版本、文档切片基础表。
- 系统支持 PDF、DOCX、TXT、Markdown 上传解析。
- 系统支持在线创建和编辑 Markdown Wiki 文档。
- 系统支持个人 Wiki 和团队 Wiki。
- 系统支持团队成员角色权限控制。
- 首页主业务展示 Wiki 文档。
- 后台管理可以管理用户、文档、空间和成员。
- RAG、URL 抓取、Agent 编排尚未进入实现，但数据库和模块边界已经为后续扩展留好入口。

---

## 9. 后续阶段预留

阶段 5：URL 文本采集。

阶段 6：企业文档 RAG。

阶段 7：Agent 任务编排。

这三个阶段不进入当前 MVP 0-4 的交付范围，但阶段 1 的 `document_chunk`、阶段 2 的解析流程、阶段 4 的空间权限会为它们提供基础。

---

## 10. 推荐开发顺序

### Task 1：项目定位与导航改造

**目标：** 完成 AgentWiki 命名、README 描述和前端导航入口调整。

**主要文件：**

- `README.md`
- `cloud_front/src/components/GlobalHeader.vue`
- `cloud_front/src/router/index.ts`

- [ ] 更新项目名称和介绍。
- [ ] 更新导航菜单。
- [ ] 保留素材库入口。
- [ ] 手动检查页面跳转。
- [ ] 提交阶段 0 变更。

### Task 2：文档数据库与后端基础 CRUD

**目标：** 新增文档、文档版本、文档切片表和基础后端接口。

**主要文件：**

- `cloud/sql/create_table_document.sql`
- `cloud/src/main/java/com/et/cloud/model/entity/Document.java`
- `cloud/src/main/java/com/et/cloud/controller/DocumentController.java`
- `cloud/src/main/java/com/et/cloud/service/DocumentService.java`
- `cloud/src/main/java/com/et/cloud/service/impl/DocumentServiceImpl.java`

- [ ] 编写 SQL。
- [ ] 新增实体、DTO、VO、Mapper、Service、Controller。
- [ ] 实现文档新增、编辑、删除、详情、分页查询。
- [ ] 运行后端测试。
- [ ] 使用 Knife4j 或 HTTP 文件验证接口。
- [ ] 提交阶段 1 变更。

### Task 3：文档上传与解析

**目标：** 支持 PDF、DOCX、TXT、Markdown 上传并解析为正文。

**主要文件：**

- `cloud/pom.xml`
- `cloud/src/main/java/com/et/cloud/manager/document/DocumentParser.java`
- `cloud/src/main/java/com/et/cloud/manager/document/DocumentParseManager.java`
- `cloud/src/main/java/com/et/cloud/manager/document/DocumentChunkManager.java`
- `cloud/src/main/java/com/et/cloud/controller/DocumentController.java`

- [ ] 添加 PDFBox 和 POI 依赖。
- [ ] 实现统一解析接口。
- [ ] 实现四种格式解析器。
- [ ] 实现文档上传接口。
- [ ] 实现文档切片保存。
- [ ] 准备四类样本文档测试。
- [ ] 提交阶段 2 变更。

### Task 4：Wiki 文档页面

**目标：** 完成文档列表、详情、新建、编辑和版本页面。

**主要文件：**

- `cloud_front/src/pages/HomePage.vue`
- `cloud_front/src/pages/DocumentDetailPage.vue`
- `cloud_front/src/pages/AddDocumentPage.vue`
- `cloud_front/src/pages/EditDocumentPage.vue`
- `cloud_front/src/components/DocumentEditor.vue`
- `cloud_front/src/components/DocumentUpload.vue`

- [ ] 重新生成前端 API。
- [ ] 首页切换为文档列表。
- [ ] 新增文档详情页。
- [ ] 新增文档创建页。
- [ ] 新增文档编辑页。
- [ ] 接入图片上传作为插图能力。
- [ ] 验证版本保存。
- [ ] 提交阶段 3 变更。

### Task 5：个人 Wiki 与团队 Wiki 权限

**目标：** 将空间体系改造成 Wiki 空间，并完成文档权限控制。

**主要文件：**

- `cloud/src/main/resources/biz/spaceUserAuthConfig.json`
- `cloud/src/main/java/com/et/cloud/manager/auth/StpInterfaceImpl.java`
- `cloud/src/main/java/com/et/cloud/controller/DocumentController.java`
- `cloud_front/src/constants/space.ts`
- `cloud_front/src/pages/MySpacePage.vue`
- `cloud_front/src/pages/SpaceDetailPage.vue`

- [ ] 更新权限配置。
- [ ] 更新前端权限常量。
- [ ] 在文档接口添加权限校验。
- [ ] 确保权限上下文支持通过文档 ID 反查空间。
- [ ] 改造我的空间页面为我的 Wiki。
- [ ] 改造空间详情页为 Wiki 文档列表。
- [ ] 验证浏览者、编辑者、管理员三类角色。
- [ ] 提交阶段 4 变更。

---

## 11. 风险与约束

- PDF 与 DOCX 解析质量可能受文件格式影响，MVP 只保证常规文本型文档可解析。
- 扫描版 PDF 不做 OCR。
- Markdown 编辑器先满足正文编辑与图片插入，不做实时多人编辑。
- 图片模块必须保留，避免破坏原项目已完成的 COS 上传、缩略图和素材能力。
- RAG 不进入阶段 0-4，但文档切片表必须提前设计，避免后续大改数据库。
- 团队权限以后端校验为准，前端按钮隐藏只作为体验优化。

---

## 12. 简历阶段性描述

阶段 0-4 完成后，简历可写为：

> AgentWiki 企业智能知识协作平台：基于 Spring Boot + Vue3 构建的企业 Wiki 系统，支持多格式文档上传解析、Markdown 在线编辑、个人 Wiki、团队 Wiki、图片素材插入、文档版本管理和基于 Sa-Token 的空间级 RBAC 权限隔离。项目在原云图库的用户、空间、COS 存储、缓存和权限基础上进行业务升级，将图片管理能力沉淀为 Wiki 素材库，并新增文档模型、解析流程和团队知识协作能力，为后续企业文档 RAG 与 Agent 任务编排预留扩展基础。
