# wiki-attachment Specification

## Purpose
为 Wiki 提供独立于图片模块的附件（文件）上传通道与元数据存储能力。图片是第一种附件类型，供文档编辑器粘贴上传使用；权限校验基于 Wiki 空间可见性，不经过图片模块的空间权限体系。

## Requirements

### Requirement: 登录用户可向可见的 Wiki 空间上传图片附件
系统 SHALL 仅允许已登录用户向对其可见的 Wiki 空间上传图片附件，上传成功后返回可直接访问的文件地址。

#### Scenario: 上传成功
- **GIVEN** 已登录用户选中一个对其可见的 Wiki 空间
- **WHEN** 用户通过 Wiki 图片上传接口提交一张 jpg/png/gif/webp 图片且大小不超过 5MB
- **THEN** 系统存储该文件并返回可直接访问的 URL

#### Scenario: 未登录拒绝
- **WHEN** 未登录用户调用图片上传接口
- **THEN** 系统拒绝请求并返回未登录错误

#### Scenario: 空间不可见拒绝
- **GIVEN** 已登录用户
- **WHEN** 用户向其不可见的 Wiki 空间上传图片
- **THEN** 系统拒绝请求且不产生任何附件记录

### Requirement: 附件类型与大小校验
系统 MUST 校验附件的 MIME 类型与文件大小，不合规请求一律拒绝且不产生附件记录。

#### Scenario: 非图片类型拒绝
- **WHEN** 用户上传非 jpg/png/gif/webp 的文件（如 pdf 或可执行文件）
- **THEN** 系统拒绝并提示类型不允许

#### Scenario: 超过大小上限拒绝
- **WHEN** 用户上传大于 5MB 的图片
- **THEN** 系统拒绝并提示大小超限

### Requirement: 附件元数据记录
每次成功上传 MUST 生成一条附件记录，包含所属空间、关联文档（可空）、文件名、访问地址、文件大小、MIME 类型、内容指纹、上传人与逻辑删除标记。

#### Scenario: 上传即记录
- **WHEN** 一次上传成功
- **THEN** 附件记录可通过所属空间与关联文档查询到
- **AND** 尚未关联任何文档的附件，其文档字段为空

### Requirement: 编辑器粘贴图片自动上传
文档编辑器 MUST 支持在编辑正文时粘贴或拖拽图片：自动上传到当前空间并在光标位置插入图片引用。

#### Scenario: 粘贴截图
- **GIVEN** 已登录用户正在编辑一篇属于某可见空间的文档
- **WHEN** 用户在正文区粘贴一张截图
- **THEN** 图片自动上传成功后在光标处插入图片引用
- **AND** 文档保存后详情页能看到该图片

### Requirement: Attachment upload does not leave orphaned objects

The system SHALL NOT retain an object in COS when the corresponding `wiki_attachment` row fails to persist.

#### Scenario: Row save fails after COS write

- **GIVEN** a logged-in user uploads a valid image to a space they may edit
- **WHEN** `cosManager.putObject` succeeds but `save(wikiAttachment)` returns false
- **THEN** the system SHALL delete the just-written COS object
- **AND** SHALL surface `OPERATION_ERROR` to the caller
- **AND** SHALL leave no `wiki_attachment` row for that object

#### Scenario: Row save succeeds

- **GIVEN** a logged-in user uploads a valid image to a space they may edit
- **WHEN** both the COS write and the row save succeed
- **THEN** the system SHALL return the object URL
- **AND** SHALL record size, mime type, content hash, uploader, and acting space

### Requirement: `documentId` must belong to the acting space

The system SHALL reject an upload whose `documentId` refers to a document outside the space being uploaded to.

#### Scenario: Cross-space document id

- **GIVEN** document A belongs to space S1 and the caller may edit space S2
- **WHEN** the caller uploads an image with `spaceId=S2` and `documentId=A`
- **THEN** the system SHALL reject with `NO_AUTH_ERROR` or `PARAMS_ERROR`
- **AND** SHALL NOT create any attachment row or COS object

#### Scenario: Matching document id

- **GIVEN** document B belongs to space S2 and the caller may edit S2
- **WHEN** the caller uploads with `spaceId=S2` and `documentId=B`
- **THEN** the attachment SHALL be linked to document B

#### Scenario: No document id

- **GIVEN** the caller uploads before the document is saved
- **WHEN** `documentId` is omitted
- **THEN** the attachment SHALL be created with a null `documentId` and linked later on save

### Requirement: Uploading requires edit access, not mere visibility

The system SHALL allow an image upload only when the caller holds edit rights on the target wiki space.

#### Scenario: Viewer attempts upload

- **GIVEN** a user who can see space S but holds view-only rights
- **WHEN** that user calls `POST /documentWiki/image/upload` with `spaceId=S`
- **THEN** the system SHALL reject with `NO_AUTH_ERROR`
- **AND** SHALL NOT write anything to COS

#### Scenario: Editor uploads

- **GIVEN** a user with edit rights on space S
- **WHEN** that user uploads a valid image
- **THEN** the upload SHALL succeed

### Requirement: File content is verified, not just its suffix

The system SHALL verify that an uploaded file's real content is an allowed image type, independent of its filename suffix.

#### Scenario: Disguised file

- **GIVEN** a non-image payload renamed to `evil.png`
- **WHEN** the caller uploads it
- **THEN** the system SHALL reject with `PARAMS_ERROR`
- **AND** SHALL NOT write anything to COS

#### Scenario: Genuine image

- **GIVEN** a genuine PNG whose content header matches its suffix
- **WHEN** the caller uploads it
- **THEN** the upload SHALL succeed

### Requirement: Object paths use normalized suffixes

The system SHALL build COS object paths with a lower-cased suffix regardless of the casing used in the uploaded filename.

#### Scenario: Upper-case suffix

- **GIVEN** a caller uploads `PHOTO.JPG`
- **WHEN** the object path is generated
- **THEN** the path SHALL end in `.jpg`
