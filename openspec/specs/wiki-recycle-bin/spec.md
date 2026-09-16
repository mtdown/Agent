# wiki-recycle-bin Specification

## Purpose
Defines the per-space recycle bin: logically deleted wiki documents and folders become visible only in their space's recycle view, deletions are recorded with time and user, and visible members may restore or permanently delete the deleted subtree.

## Requirements

### Requirement: Recycle bin scoped to each space

The system SHALL provide a recycle view per wiki space that only users who can see that space may access, listing the space's logically deleted documents and folders.

#### Scenario: Deleted item appears in the space's recycle bin

- **GIVEN** a document or folder in a space has been logically deleted
- **WHEN** a user who can see that space opens its recycle view
- **THEN** the deleted document or folder (with its subtree for folders) is listed there

#### Scenario: Deleted item hidden from normal listings

- **GIVEN** a logically deleted document or folder
- **WHEN** normal wiki listings of that space are viewed
- **THEN** the deleted item does not appear

### Requirement: Record deletion time and deleting user

The system SHALL record the deletion time and the deleting user for every logically deleted document and folder.

#### Scenario: Recycle entry shows who deleted and when

- **GIVEN** a document deleted by a user in a space
- **WHEN** the space's recycle view is inspected
- **THEN** the deletion time and the deleting user are shown for that document

### Requirement: Restore deleted subtree

The system SHALL let any user who can see a space restore its logically deleted items, restoring a folder together with its entire deleted subtree as a unit.

#### Scenario: Restore a document

- **GIVEN** a logically deleted document in a visible space
- **WHEN** a visible user restores it
- **THEN** the document reappears in its original location

#### Scenario: Restore a folder restores its subtree

- **GIVEN** a logically deleted folder together with its deleted documents and nested folders
- **WHEN** a visible user restores the folder
- **THEN** the folder and all of its deleted documents and nested folders reappear together in their original hierarchy

#### Scenario: Restore when parent folder is gone

- **GIVEN** a logically deleted item whose original parent folder was permanently deleted
- **WHEN** a visible user restores the item
- **THEN** the item reappears at the root of its space

### Requirement: Permanent delete after confirmation

The system SHALL require a secondary confirmation before permanently deleting an item from the recycle bin, after which the item is physically removed and cannot be restored.

#### Scenario: Permanent delete requires confirmation

- **GIVEN** a logically deleted document in the recycle bin
- **WHEN** a visible user requests permanent deletion without confirming
- **THEN** the item remains in the recycle bin

#### Scenario: Confirmed permanent delete removes item

- **GIVEN** a logically deleted document in the recycle bin
- **WHEN** a visible user confirms permanent deletion of it
- **THEN** the document is physically removed and no longer appears in the recycle bin or anywhere else

### Requirement: Recycle bin entries are read-only

The system SHALL restrict actions on recycle bin entries to restore, permanent delete, and viewing deletion metadata; editing, moving, or opening the content of a recycled item SHALL be unavailable.

#### Scenario: Recycled item cannot be edited or moved

- **GIVEN** a logically deleted document or folder in the recycle bin
- **WHEN** a user attempts to edit or move it
- **THEN** the request is rejected

#### Scenario: Recycled item content is not opened

- **GIVEN** a logically deleted document in the recycle bin
- **WHEN** a user attempts to open its content
- **THEN** no content is shown and only deletion metadata is available

### Requirement: 批量永久删除

系统 SHALL 允许对可见空间的回收站条目进行多选，并在二次确认后批量永久删除所选条目；单条恢复与单条永久删除行为保持不变。

#### Scenario: 多选后批量永久删除

- **GIVEN** 某可见空间的回收站中存在多条逻辑删除的文档或文件夹
- **WHEN** 可见用户勾选多条条目后确认批量永久删除
- **THEN** 所选条目全部物理删除，不再出现在回收站中
- **AND** 页面反馈成功与失败（如有）的数量

#### Scenario: 未选择条目时批量删除不生效

- **GIVEN** 回收站处于批量选择状态
- **WHEN** 用户未勾选任何条目即触发批量永久删除
- **THEN** 系统提示先选择条目，不发起任何删除

#### Scenario: 批量永久删除需二次确认

- **GIVEN** 用户已勾选多条回收站条目
- **WHEN** 用户点击批量永久删除
- **THEN** 系统先要求二次确认，确认前不删除任何条目

#### Scenario: 批量操作完成后列表刷新

- **GIVEN** 批量永久删除已执行完毕
- **WHEN** 用户查看当前空间的回收站
- **THEN** 已删除条目不再出现，未勾选的条目保持原样
