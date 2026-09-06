## Context

见 `proposal.md` 的 Why。`PictureServiceImpl` 内有两个额度更新点：上传路径（约 184-196 行）已用 `if (finalSpaceId != null)` 守卫，删除路径（约 542-554 行）没有——这是同一文件内的不对称，也是本缺陷的直接根源。SQL 语义上 `WHERE id = NULL` 恒为 UNKNOWN，`update()` 必然返回 false 并触发 `ThrowUtils.throwIf(!update, ..., "额度更新失败")`，事务回滚。

测试基础设施：本地 MySQL(3307)/Redis(6379) 由 Docker 提供，`start-dev.ps1` 可一键拉起后端(8123, local profile)。公共图库现存 2 张 72B 残留测试图（`2096509129564987394`、`2096509302319980545`）可直接作为公共图库删除的真实回归用例；admin 自有空间 `1987063349924573185` 可作为空间额度路径的回归用例。

## Goals / Non-Goals

**Goals:**

- 公共图库图片（spaceId=null）删除返回 code 0 且记录被删除。
- 空间图片删除的额度释放行为保持与现状完全一致。
- 修复面最小：一个判空分支，不动接口签名、不动前端、不动其他额度更新点。

**Non-Goals:**

- 不修公共图库首页 5 分钟缓存不失效问题（已知行为，另行决策）。
- 不做删除时的对象存储文件清理（现状也不做，维持一致）。
- 不引入软删除/回收站等新机制。

## Decisions

### Decision 1: 判空跳过额度更新（而非伪造一个"公共空间"行）

在 `deletePicture` 的事务内，仅当 `oldPicture.getSpaceId() != null` 时执行现有的 `lambdaUpdate().eq(Space::getId, spaceId)...` 额度更新；为空时直接跳过该分支。

理由：与上传路径（`finalSpaceId != null` 守卫）形成对称，改动最小、语义直白。

备选方案：为公共图库造一条哨兵 Space 记录承接额度更新。否决——引入假数据，污染 space 表，且公共图库本无额度概念。

### Decision 2: 复用现有事务结构，不拆分事务

保留 `transactionTemplate.execute` 单事务包裹 removeById + 额度更新。公共图库路径事务内只剩 removeById，行为天然正确；空间路径事务语义与现状一致。

备选方案：按 spaceId 是否为空拆成两个事务方法。否决——增加分支复杂度，无收益。

### Decision 3: 回归验证走 API 冒烟而非新增单测

项目现状后端无单测体系（模块以手测+脚本验证为主）。回归采用：修复前先记录 admin 空间的 totalSize/totalCount → 空间内上传+删除 → 断言额度回落；公共图库删除 2 张残留测试图 → 断言 code 0 且再查询 404。

理由：与 fix-frontend-typecheck 的验证方式一致，成本低。

## Risks / Trade-offs

- **风险：跳过额度更新后公共图库删除"静默成功"，掩盖其他潜在失败** —— 可接受：公共图库本无额度可更新，唯一失败点 removeById 的结果仍被校验。
- **风险：遗漏同类判空的其他调用点** —— 已排查：全项目仅上传与删除两处额度更新，上传已有守卫；编辑路径不涉及额度。
- **风险：历史脏数据**（此前删除失败回滚不留脏数据）—— 无需数据修复，事务回滚保证了历史一致性。
