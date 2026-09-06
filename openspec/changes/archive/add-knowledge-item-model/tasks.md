# Tasks: KnowledgeItem 实施清单（最小可用 Stage 1+2）

> **当前状态**：方案已就绪、已切到 `feature/knowledge-item-fusion` 分支。**待用户授权后**正式启动 Stage 1 编码。
> **范围**：仅 TEXT + IMAGE 两种 block，先打通"图、文融合" + 列表卡片化 + 详情三栏。

## 0. 立项与方案确认

- [x] 用户确认实施切片（最小可用 Stage 1+2）
- [x] 切到 `feature/knowledge-item-fusion` 分支
- [x] 写入 OpenSpec：`proposal.md` / `design.md` / `specs/knowledge-item.md` / `tasks.md`
- [ ] 在 `IssueLog.xlsx` 登记本 Change 的 issue 行（按 AGENTS.md 第 3 节）
- [ ] 用户授权启动 Stage 1 编码

## 1. Stage 1 — 基础数据通路（仅 TEXT+IMAGE block）

- [ ] 创建 `knowledge_item` 表 DDL：`cloud/sql/create_table_knowledge_item.sql`
- [ ] 创建 `knowledge_block` 表 DDL：`cloud/sql/create_table_knowledge_block.sql`
- [ ] 新增 `KnowledgeItem` 实体（MyBatis-Plus 风格，复用 `Picture` 风格）
- [ ] 新增 `KnowledgeBlock` 实体（同上）
- [ ] 新增 `KnowledgeType` 枚举：`PICTURE` / `DOCUMENT` / `MIXED`
- [ ] 新增 `BlockType` 枚举：`TEXT` / `IMAGE`（其余 Stage 3 再扩展）
- [ ] 新增 `KnowledgeItemVis`（一次返回 item + blocks）
- [ ] 新增 DTO：`KnowledgeItemAddRequest` / `KnowledgeItemEditRequest` / `KnowledgeItemQueryRequest` / `BlockUpsertRequest`
- [ ] 新增 `KnowledgeItemMapper` / `KnowledgeBlockMapper`
- [ ] 新增 `KnowledgeItemService` 与 `KnowledgeBlockService`（同事务写入；`type` 由 blocks 回算）
- [ ] 新增 `DocumentWikiAdapter`：旧 `DocumentWiki` 数据按 `\n\n` 切 TEXT block 适配读取
- [ ] 新增 `KnowledgeItemController`：`add` / `get/vis` / `list/page/vis` / `edit` / `delete`
- [ ] 沿用 `wikiSpaceService.requireVisibleSpace` 做权限校验
- [ ] 新增 `KnowledgeCacheManager`（条目粒度失效，TTL 5 分钟 + 抖动）
- [ ] 单元测试：`KnowledgeItemServiceImplTest`（新增、type 回算、事务回滚、Adapter 读取）
- [ ] 验证：`mvn -Dtest=KnowledgeItemServiceImplTest test` 通过
- [ ] 验证：`mvn -DskipTests package` 通过
- [ ] 在 `IssueLog.xlsx` 记录本 Stage 全部问题
- [ ] 汇报：本轮改动概要 + 测试通过项 + 失败项 + 修复方案（按 AGENTS.md 第 4 节）

## 2. Stage 2 — 前端块编辑器与三栏布局

- [ ] 前端 `KnowledgeBlockRenderer.vue` 递归渲染（仅 TEXT / IMAGE）
- [ ] 前端 `BlockText.vue` / `BlockImage.vue` 两个组件
- [ ] 前端 `KnowledgeListPage.vue`：卡片网格，按 type 过滤
- [ ] 前端 `KnowledgeDetailPage.vue`：左目录 + 中央 BlockRenderer + 右操作面板
- [ ] 前端 `KnowledgeEditPage.vue`：浮动 + 按钮插入 block，IMAGE 块走"图库选择器"
- [ ] 复用 `vue-cropper` 已有能力做图片裁剪（嵌入 IMAGE 块）
- [ ] 路由 `/knowledge/list` / `/knowledge/detail/:id` / `/knowledge/edit/:id`
- [ ] 导航栏添加"知识库"入口
- [ ] 重新生成 OpenAPI 前端代码：`npm run openapi`
- [ ] 验证：`npm run build-only` 通过
- [ ] 在 `IssueLog.xlsx` 记录本 Stage 全部问题
- [ ] 汇报：本轮改动概要 + 测试通过项 + 失败项 + 修复方案

## 3. 收尾（Stage 1+2 都完成后）

- [ ] 更新本文件尾部的"Verification / Result"段
- [ ] 手测：旧 `DocumentWiki` 数据 → 知识库列表 → 详情三栏 → 编辑 → 删除 全链路
- [ ] 手测：图片库 → 选图 → 提升为条目 → 在知识库列表出现
- [ ] 用户授权后方可合并至 main
- [ ] 合并后在 `README.md` 增加"11. 知识库融合"小节，记录方案要点

## 4. Out of Scope（明确不做，避免范围蔓延）

- ❌ CODE / TABLE / QUOTE / LIST / DIVIDER / FILE 等其他 block 类型
- ❌ `POST /picture/promote`（Stage 3）
- ❌ 搜索接口全文检索（Stage 3）
- ❌ 协同编辑 / RAG / URL 抓取（后续 Change）